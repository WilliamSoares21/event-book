package com.eventbook.backend.orders;

import com.eventbook.backend.cards.SimulatedCard;
import com.eventbook.backend.cards.SimulatedCardRepository;
import com.eventbook.backend.events.Event;
import com.eventbook.backend.events.EventRepository;
import com.eventbook.backend.events.EventStatus;
import com.eventbook.backend.lots.LotStatus;
import com.eventbook.backend.lots.TicketLot;
import com.eventbook.backend.lots.TicketLotRepository;
import com.eventbook.backend.payments.PaymentStatus;
import com.eventbook.backend.payments.PaymentTransaction;
import com.eventbook.backend.payments.PaymentTransactionRepository;
import com.eventbook.backend.tickets.Ticket;
import com.eventbook.backend.tickets.TicketRepository;
import com.eventbook.backend.tickets.TicketStatus;
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
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class CardCheckoutIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("eventbook")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("security.jwt.secret", () -> "9843575ce52ce3295ae06df9de4fd1f64f4c55243a02da9efc2c102c8555343e");
        registry.add("r2.endpoint", () -> "https://dummy.r2.cloudflarestorage.com");
        registry.add("r2.access-key", () -> "dummy");
        registry.add("r2.secret-key", () -> "dummy");
        registry.add("r2.bucket", () -> "dummy-bucket");
        registry.add("r2.public-base-url", () -> "https://dummy.r2.cloudflarestorage.com");
        registry.add("security.jwt.secret", () -> "9843575ce52ce3295ae06df9de4fd1f64f4c55243a02da9efc2c102c8555343e");
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private TicketLotRepository ticketLotRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private SimulatedCardRepository simulatedCardRepository;

    @Autowired
    private PaymentTransactionRepository paymentTransactionRepository;

    private final String commonPassword = "password123";

    @BeforeEach
    void setup() {
        paymentTransactionRepository.deleteAll();
        ticketRepository.deleteAll();
        orderRepository.deleteAll();
        simulatedCardRepository.deleteAll();
        ticketLotRepository.deleteAll();
        eventRepository.deleteAll();
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

    private User createOrganizer() {
        return userRepository.save(User.builder()
                .name("Organizer")
                .email("organizer@test.com")
                .passwordHash(passwordEncoder.encode(commonPassword))
                .role(Role.ORGANIZADOR)
                .build());
    }

    private TicketLot createActiveLot(User organizer, int capacity, BigDecimal price) {
        Event event = eventRepository.save(Event.builder()
                .title("Card Checkout Event")
                .description("Desc")
                .startsAt(OffsetDateTime.now().plus(1, ChronoUnit.DAYS))
                .endsAt(OffsetDateTime.now().plus(2, ChronoUnit.DAYS))
                .location("Location")
                .organizer(organizer)
                .status(EventStatus.PUBLISHED)
                .build());

        return ticketLotRepository.save(TicketLot.builder()
                .event(event)
                .name("Lot 1")
                .unitPrice(price)
                .capacity(capacity)
                .salesStart(Instant.now().minus(1, ChronoUnit.HOURS))
                .salesEnd(Instant.now().plus(1, ChronoUnit.HOURS))
                .status(LotStatus.ACTIVE)
                .build());
    }

    private String obtainToken(String email) throws Exception {
        var res = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, commonPassword)))
                .andExpect(status().isOk())
                .andReturn();
        return res.getResponse().getContentAsString().split("\"token\":\"")[1].split("\"")[0];
    }

    private UUID createCard(String token) throws Exception {
        String body = mockMvc.perform(post("/api/cards")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(body.split("\"id\":\"")[1].split("\"")[0]);
    }

    private UUID createOrder(String token, UUID lotId, int qty) throws Exception {
        String body = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{\"items\":[{\"ticketLotId\":\"%s\",\"quantity\":%d}]}".formatted(lotId, qty)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(body.split("\"id\":\"")[1].split("\"")[0]);
    }

    @Test
    void deveAprovarCheckoutComCartaoEDebitarLimite() throws Exception {
        User organizer = createOrganizer();
        User buyer = createBuyer("buyer@test.com");
        String token = obtainToken(buyer.getEmail());

        TicketLot lot = createActiveLot(organizer, 10, new BigDecimal("150.00"));
        UUID cardId = createCard(token);
        UUID orderId = createOrder(token, lot.getId(), 2); // Total 300.00

        mockMvc.perform(post("/api/orders/{orderId}/checkout", orderId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cardId\":\"%s\"}".formatted(cardId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.totalPrice").value(300.00));

        // Verificar limite debitado no cartão (1000 - 300 = 700)
        SimulatedCard card = simulatedCardRepository.findById(cardId).orElseThrow();
        assertThat(card.getAvailableLimit()).isEqualByComparingTo("700.00");

        // Transação APPROVED persistida
        List<PaymentTransaction> txs = paymentTransactionRepository.findAll();
        assertThat(txs).hasSize(1);
        assertThat(txs.get(0).getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(txs.get(0).getAmount()).isEqualByComparingTo("300.00");
    }

    @Test
    void deveRecusarCheckoutPorLimiteInsuficienteEGravarTransactionDeclined() throws Exception {
        User organizer = createOrganizer();
        User buyer = createBuyer("buyer@test.com");
        String token = obtainToken(buyer.getEmail());

        TicketLot lot = createActiveLot(organizer, 10, new BigDecimal("1200.00")); // Acima do limite de 1000
        UUID cardId = createCard(token);
        UUID orderId = createOrder(token, lot.getId(), 1); // Total 1200.00

        mockMvc.perform(post("/api/orders/{orderId}/checkout", orderId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cardId\":\"%s\"}".formatted(cardId)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_CARD_LIMIT"));

        // Pedido continua PENDING
        Order order = orderRepository.findById(orderId).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);

        // Cartão com limite intacto (1000.00)
        SimulatedCard card = simulatedCardRepository.findById(cardId).orElseThrow();
        assertThat(card.getAvailableLimit()).isEqualByComparingTo("1000.00");

        // Transação DECLINED persistida com motivo
        List<PaymentTransaction> txs = paymentTransactionRepository.findAll();
        assertThat(txs).hasSize(1);
        assertThat(txs.get(0).getStatus()).isEqualTo(PaymentStatus.DECLINED);
        assertThat(txs.get(0).getDeclineReason()).isEqualTo("INSUFFICIENT_CARD_LIMIT");
        assertThat(txs.get(0).getAmount()).isEqualByComparingTo("1200.00");
    }

    @Test
    void deveHerdarLimiteDisponivelAoSubstituirCartao() throws Exception {
        User buyer = createBuyer("buyer@test.com");
        String token = obtainToken(buyer.getEmail());

        UUID card1Id = createCard(token);
        
        // Simular consumo parcial do cartão 1 (deixar 400.00 disponíveis)
        SimulatedCard card1 = simulatedCardRepository.findById(card1Id).orElseThrow();
        card1.setAvailableLimit(new BigDecimal("400.00"));
        simulatedCardRepository.save(card1);

        // Desativar cartão 1
        mockMvc.perform(patch("/api/cards/{cardId}/deactivate", card1Id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        // Cadastrar cartão 2: deve herdar 400.00
        String body = mockMvc.perform(post("/api/cards")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID card2Id = UUID.fromString(body.split("\"id\":\"")[1].split("\"")[0]);
        SimulatedCard card2 = simulatedCardRepository.findById(card2Id).orElseThrow();
        assertThat(card2.getAvailableLimit()).isEqualByComparingTo("400.00");
    }

    private org.springframework.test.web.servlet.ResultActions checkout(String token, UUID orderId, UUID cardId) throws Exception {
        return mockMvc.perform(post("/api/orders/{orderId}/checkout", orderId)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + token)
                .content("{\"cardId\":\"" + cardId + "\"}"));
    }

    @Test
    void deveRecusarCheckoutComCartaoDeOutroUsuario() throws Exception {
        User organizer = createOrganizer();
        User owner = createBuyer("owner@test.com");
        User other = createBuyer("other@test.com");
        String ownerToken = obtainToken(owner.getEmail());
        String otherToken = obtainToken(other.getEmail());

        TicketLot lot = createActiveLot(organizer, 10, new BigDecimal("50.00"));
        UUID ownerCardId = createCard(ownerToken);
        UUID orderId = createOrder(otherToken, lot.getId(), 1);

        checkout(otherToken, orderId, ownerCardId).andExpect(status().isForbidden());
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void deveRecusarCheckoutComCartaoInativo() throws Exception {
        User organizer = createOrganizer();
        User buyer = createBuyer("buyer@test.com");
        String token = obtainToken(buyer.getEmail());

        TicketLot lot = createActiveLot(organizer, 10, new BigDecimal("50.00"));
        UUID cardId = createCard(token);
        mockMvc.perform(patch("/api/cards/{cardId}/deactivate", cardId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        UUID orderId = createOrder(token, lot.getId(), 1);

        checkout(token, orderId, cardId).andExpect(status().isUnprocessableEntity());
        assertThat(paymentTransactionRepository.count()).isZero();
    }

    @Test
    void deveRecusarCheckoutComCartaoVencido() throws Exception {
        User organizer = createOrganizer();
        User buyer = createBuyer("buyer@test.com");
        String token = obtainToken(buyer.getEmail());

        TicketLot lot = createActiveLot(organizer, 10, new BigDecimal("50.00"));
        UUID cardId = createCard(token);

        SimulatedCard card = simulatedCardRepository.findById(cardId).orElseThrow();
        YearMonth lastMonth = YearMonth.now().minusMonths(1);
        card.setExpiryMonth(lastMonth.getMonthValue());
        card.setExpiryYear(lastMonth.getYear());
        simulatedCardRepository.save(card);

        UUID orderId = createOrder(token, lot.getId(), 1);

        checkout(token, orderId, cardId).andExpect(status().isUnprocessableEntity());
        assertThat(paymentTransactionRepository.count()).isZero();
    }

    @Test
    void devePermitirNovaTentativaAposRecusaPorLimite() throws Exception {
        User organizer = createOrganizer();
        User buyer = createBuyer("buyer@test.com");
        String token = obtainToken(buyer.getEmail());

        TicketLot lot = createActiveLot(organizer, 10, new BigDecimal("600.00"));
        UUID cardId = createCard(token);
        UUID orderId = createOrder(token, lot.getId(), 2); // 1200.00 > 1000.00

        checkout(token, orderId, cardId)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_CARD_LIMIT"));

        // Amplia o limite e repete a tentativa no mesmo pedido.
        SimulatedCard card = simulatedCardRepository.findById(cardId).orElseThrow();
        card.setTotalLimit(new BigDecimal("2000.00"));
        card.setAvailableLimit(new BigDecimal("2000.00"));
        simulatedCardRepository.save(card);

        checkout(token, orderId, cardId).andExpect(status().isOk());

        Order order = orderRepository.findById(orderId).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(paymentTransactionRepository.findAll())
                .extracting(PaymentTransaction::getStatus)
                .containsExactlyInAnyOrder(PaymentStatus.DECLINED, PaymentStatus.APPROVED);
    }

    @Test
    void duasComprasSimultaneasComLimiteParaApenasUma() throws Exception {
        User organizer = createOrganizer();
        User buyer = createBuyer("buyer@test.com");
        String token = obtainToken(buyer.getEmail());

        TicketLot lot = createActiveLot(organizer, 10, new BigDecimal("400.00"));
        UUID cardId = createCard(token);
        UUID order1 = createOrder(token, lot.getId(), 2); // 800.00
        UUID order2 = createOrder(token, lot.getId(), 2); // 800.00 -> total 1600 > 1000

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch end = new CountDownLatch(2);
        AtomicInteger approved = new AtomicInteger();
        AtomicInteger declined = new AtomicInteger();

        for (UUID orderId : List.of(order1, order2)) {
            executor.submit(() -> {
                try {
                    start.await();
                    int status = checkout(token, orderId, cardId).andReturn().getResponse().getStatus();
                    if (status == 200) approved.incrementAndGet();
                    else if (status == 422) declined.incrementAndGet();
                } catch (Exception e) {
                    declined.incrementAndGet();
                } finally {
                    end.countDown();
                }
            });
        }
        start.countDown();
        end.await();
        executor.shutdown();

        assertThat(approved.get()).isEqualTo(1);
        assertThat(declined.get()).isEqualTo(1);

        // Limite nunca fica negativo e reflete exatamente uma compra.
        assertThat(simulatedCardRepository.findById(cardId).orElseThrow().getAvailableLimit())
                .isEqualByComparingTo("200.00");
    }
}
