package com.eventbook.backend.events;

import com.eventbook.backend.events.dto.EventUpdateDTO;
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
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.json.JsonMapper;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Percorre o fluxo completo HTTP -> Jackson -> Controller -> Bean Validation -> Service
 * usando o JsonMapper realmente configurado pelo Spring Boot.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class EventUpdatePatchIntegrationTest {

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

    private static final String PASSWORD = "password123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JsonMapper jsonMapper;

    private User organizer;
    private String token;

    @BeforeEach
    void setup() throws Exception {
        eventRepository.deleteAll();
        userRepository.deleteAll();

        organizer = userRepository.save(User.builder()
                .name("Organizer")
                .email("organizer@test.com")
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .role(Role.ORGANIZADOR)
                .build());

        token = obtainToken(organizer.getEmail());
    }

    private String obtainToken(String email) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return body.split("\"token\":\"")[1].split("\"")[0];
    }

    private Event draftEvent() {
        return eventRepository.save(Event.builder()
                .organizer(organizer)
                .title("Titulo original")
                .description("Descricao original")
                .startsAt(OffsetDateTime.parse("2030-01-10T19:00:00-03:00"))
                .endsAt(OffsetDateTime.parse("2030-01-10T23:00:00-03:00"))
                .location("Local original")
                .number("2901")
                .complement("Bloco B")
                .city("Fortaleza")
                .state("CE")
                .zipCode("60000-000")
                .build());
    }

    private ResultActions patchEvent(UUID eventId, String json) throws Exception {
        return mockMvc.perform(patch("/api/events/{eventId}", eventId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private Event reload(UUID eventId) {
        return eventRepository.findById(eventId).orElseThrow();
    }

    @Test
    void patchApenasTituloAtualizaEPreservaDemaisCampos() throws Exception {
        Event event = draftEvent();

        patchEvent(event.getId(), """
                {"title":"Titulo novo"}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Titulo novo"));

        Event atualizado = reload(event.getId());
        assertThat(atualizado.getTitle()).isEqualTo("Titulo novo");
        assertThat(atualizado.getDescription()).isEqualTo("Descricao original");
        assertThat(atualizado.getLocation()).isEqualTo("Local original");
        assertThat(atualizado.getNumber()).isEqualTo("2901");
        assertThat(atualizado.getComplement()).isEqualTo("Bloco B");
        assertThat(atualizado.getCity()).isEqualTo("Fortaleza");
        assertThat(atualizado.getState()).isEqualTo("CE");
        assertThat(atualizado.getZipCode()).isEqualTo("60000-000");
    }

    @Test
    void patchPayloadVazioNaoAlteraNada() throws Exception {
        Event event = draftEvent();

        patchEvent(event.getId(), "{}").andExpect(status().isOk());

        Event atualizado = reload(event.getId());
        assertThat(atualizado.getTitle()).isEqualTo("Titulo original");
        assertThat(atualizado.getZipCode()).isEqualTo("60000-000");
        assertThat(atualizado.getNumber()).isEqualTo("2901");
        assertThat(atualizado.getComplement()).isEqualTo("Bloco B");
    }

    @Test
    void patchZipCodeNullPreservaCepAtual() throws Exception {
        Event event = draftEvent();

        patchEvent(event.getId(), """
                {"zipCode":null}
                """).andExpect(status().isOk());

        assertThat(reload(event.getId()).getZipCode()).isEqualTo("60000-000");
    }

    @Test
    void patchZipCodeVazioLimpaCep() throws Exception {
        Event event = draftEvent();

        patchEvent(event.getId(), """
                {"zipCode":""}
                """).andExpect(status().isOk());

        assertThat(reload(event.getId()).getZipCode()).isNull();
    }

    @Test
    void patchNumberEComplementVaziosLimpamCampos() throws Exception {
        Event event = draftEvent();

        patchEvent(event.getId(), """
                {"number":"","complement":"   "}
                """).andExpect(status().isOk());

        Event atualizado = reload(event.getId());
        assertThat(atualizado.getNumber()).isNull();
        assertThat(atualizado.getComplement()).isNull();
    }

    @Test
    void patchNormalizaStateECity() throws Exception {
        Event event = draftEvent();

        patchEvent(event.getId(), """
                {"state":" ce ","city":" Fortaleza "}
                """).andExpect(status().isOk());

        Event atualizado = reload(event.getId());
        assertThat(atualizado.getState()).isEqualTo("CE");
        assertThat(atualizado.getCity()).isEqualTo("Fortaleza");
    }

    @Test
    void payloadInvalidoRetorna400ComMensagemPadrao() throws Exception {
        Event event = draftEvent();

        patchEvent(event.getId(), """
                {"startsAt":"nao-e-uma-data"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Payload ou formato inválido"));
    }

    // --- Desserialização com o JsonMapper real da aplicação (Fase 4) ---

    @Test
    void jsonMapperRealDesserializaPayloadsDoContrato() {
        assertThat(jsonMapper.readValue("{}", EventUpdateDTO.class).getTitle()).isNull();

        EventUpdateDTO somenteTitulo = jsonMapper.readValue("{\"title\":\"Novo título\"}", EventUpdateDTO.class);
        assertThat(somenteTitulo.getTitle()).isEqualTo("Novo título");
        assertThat(somenteTitulo.getZipCode()).isNull();

        EventUpdateDTO nulo = jsonMapper.readValue("{\"zipCode\":null}", EventUpdateDTO.class);
        assertThat(nulo.getZipCode()).isNull();

        EventUpdateDTO vazio = jsonMapper.readValue("{\"zipCode\":\"\"}", EventUpdateDTO.class);
        assertThat(vazio.getZipCode()).isNull();
        assertThat(vazio.isZipCodeCleared()).isTrue();

        EventUpdateDTO numero = jsonMapper.readValue("{\"number\":\"\",\"complement\":\"\"}", EventUpdateDTO.class);
        assertThat(numero.getNumber()).isNull();
        assertThat(numero.getComplement()).isNull();

        EventUpdateDTO normalizado = jsonMapper.readValue("{\"state\":\" ce \",\"city\":\" Fortaleza \"}", EventUpdateDTO.class);
        assertThat(normalizado.getState()).isEqualTo("CE");
        assertThat(normalizado.getCity()).isEqualTo("Fortaleza");
    }
}
