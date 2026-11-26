package com.eventbook.backend.cards;

import com.eventbook.backend.users.Role;
import com.eventbook.backend.users.User;
import com.eventbook.backend.users.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class CardIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("eventbook")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("security.jwt.secret", () -> "9843575ce52ce3295ae06df9de4fd1f64f4c55243a02da9efc2c102c8555343e");
        registry.add("r2.endpoint", () -> "https://dummy.r2.cloudflarestorage.com");
        registry.add("r2.access-key", () -> "dummy");
        registry.add("r2.secret-key", () -> "dummy");
        registry.add("r2.bucket", () -> "dummy");
        registry.add("r2.public-url", () -> "https://dummy.r2.cloudflarestorage.com");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private SimulatedCardRepository simulatedCardRepository;

    private final String commonPassword = "password123";

    @BeforeEach
    void setup() {
        simulatedCardRepository.deleteAll();
        userRepository.deleteAll();
    }

    private User createBuyer(String email) {
        return userRepository.save(User.builder()
                .name("Buyer " + email)
                .email(email)
                .passwordHash(passwordEncoder.encode(commonPassword))
                .role(Role.COMPRADOR)
                .build());
    }

    private String obtainToken(String email) throws Exception {
        var result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                                "email": "%s",
                                "password": "%s"
                            }
                            """.formatted(email, commonPassword)))
                .andExpect(status().isOk())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        return body.contains("token") ? body.split("\"token\":\"")[1].split("\"")[0] : "";
    }

    @Test
    void deveCadastrarListarEDesativarCartaoComSucesso() throws Exception {
        User buyer = createBuyer("buyer@test.com");
        String token = obtainToken(buyer.getEmail());

        // 1. Cadastrar cartão (201)
        var createResult = mockMvc.perform(post("/api/cards")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.lastFourDigits").isString())
                .andExpect(jsonPath("$.brand").isString())
                .andExpect(jsonPath("$.totalLimit").value(1000.00))
                .andExpect(jsonPath("$.availableLimit").value(1000.00))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn();

        String responseBody = createResult.getResponse().getContentAsString();
        UUID cardId = UUID.fromString(responseBody.split("\"id\":\"")[1].split("\"")[0]);

        // 2. Tentar cadastrar segundo cartão ativo (409 Conflict)
        mockMvc.perform(post("/api/cards")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());

        // 3. Listar cartões do usuário (200)
        mockMvc.perform(get("/api/cards/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(cardId.toString()))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"));

        // 4. Desativar cartão (204)
        mockMvc.perform(patch("/api/cards/{cardId}/deactivate", cardId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        // 5. Tentar desativar novamente (422 Unprocessable Entity)
        mockMvc.perform(patch("/api/cards/{cardId}/deactivate", cardId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnprocessableEntity());

        // 6. Cadastrar novo cartão após desativação (201)
        mockMvc.perform(post("/api/cards")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated());

        // 7. Listar agora deve mostrar dois cartões (1 INACTIVE, 1 ACTIVE)
        mockMvc.perform(get("/api/cards/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(2)));
    }

    @Test
    void deveNegarAcessoACartaoDeOutroUsuario() throws Exception {
        User buyer1 = createBuyer("buyer1@test.com");
        User buyer2 = createBuyer("buyer2@test.com");
        String token2 = obtainToken(buyer2.getEmail());

        // Buyer 1 cadastra um cartão
        String token1 = obtainToken(buyer1.getEmail());
        var createResult = mockMvc.perform(post("/api/cards")
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isCreated())
                .andReturn();
        UUID cardId1 = UUID.fromString(createResult.getResponse().getContentAsString().split("\"id\":\"")[1].split("\"")[0]);

        // Buyer 2 tenta desativar o cartão do Buyer 1 (403 Forbidden)
        mockMvc.perform(patch("/api/cards/{cardId}/deactivate", cardId1)
                        .header("Authorization", "Bearer " + token2))
                .andExpect(status().isForbidden());
    }

    @Test
    void deveGarantirUnicidadeDeCartaoAtivoSobConcorrencia() throws Exception {
        User buyer = createBuyer("concurrent@test.com");
        String token = obtainToken(buyer.getEmail());

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        AtomicInteger otherErrorCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    var result = mockMvc.perform(post("/api/cards")
                            .header("Authorization", "Bearer " + token));
                    int status = result.andReturn().getResponse().getStatus();
                    if (status == 201) {
                        successCount.incrementAndGet();
                    } else if (status == 409) {
                        conflictCount.incrementAndGet();
                    } else {
                        otherErrorCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    otherErrorCount.incrementAndGet();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        endLatch.await();
        executor.shutdown();

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(conflictCount.get()).isEqualTo(threadCount - 1);
        assertThat(otherErrorCount.get()).isZero();

        // Verificar na base que existe apenas 1 ACTIVE
        long activeCount = simulatedCardRepository.findAll().stream()
                .filter(c -> c.getStatus() == CardStatus.ACTIVE)
                .count();
        assertThat(activeCount).isEqualTo(1);
    }

    @Test
    void devePreservarLimiteAposMultiplasSubstituicoes() throws Exception {
        User buyer = createBuyer("buyer@test.com");
        String token = obtainToken(buyer.getEmail());

        UUID firstId = createCardAndGetId(token);

        // Consome 700 do primeiro cartão (restam 300).
        SimulatedCard first = simulatedCardRepository.findById(firstId).orElseThrow();
        first.setAvailableLimit(new BigDecimal("300.00"));
        simulatedCardRepository.save(first);

        mockMvc.perform(patch("/api/cards/{cardId}/deactivate", firstId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        UUID secondId = createCardAndGetId(token);
        assertThat(simulatedCardRepository.findById(secondId).orElseThrow().getAvailableLimit())
                .isEqualByComparingTo("300.00");

        mockMvc.perform(patch("/api/cards/{cardId}/deactivate", secondId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        UUID thirdId = createCardAndGetId(token);
        assertThat(simulatedCardRepository.findById(thirdId).orElseThrow().getAvailableLimit())
                .isEqualByComparingTo("300.00");

        // Histórico preservado: apenas o último está ACTIVE.
        assertThat(simulatedCardRepository.findAll().stream()
                .filter(c -> c.getStatus() == CardStatus.ACTIVE).count()).isEqualTo(1);
        assertThat(simulatedCardRepository.findAll().stream()
                .filter(c -> c.getStatus() == CardStatus.INACTIVE).count()).isEqualTo(2);
    }

    private UUID createCardAndGetId(String token) throws Exception {
        String body = mockMvc.perform(post("/api/cards")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(body.split("\"id\":\"")[1].split("\"")[0]);
    }
}
