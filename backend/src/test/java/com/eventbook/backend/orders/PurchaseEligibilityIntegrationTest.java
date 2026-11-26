package com.eventbook.backend.orders;

import com.eventbook.backend.cards.SimulatedCardRepository;
import com.eventbook.backend.events.Event;
import com.eventbook.backend.events.EventRepository;
import com.eventbook.backend.events.EventStatus;
import com.eventbook.backend.lots.LotStatus;
import com.eventbook.backend.lots.TicketLot;
import com.eventbook.backend.lots.TicketLotRepository;
import com.eventbook.backend.payments.PaymentTransactionRepository;
import com.eventbook.backend.tickets.TicketRepository;
import com.eventbook.backend.users.Role;
import com.eventbook.backend.users.User;
import com.eventbook.backend.users.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Elegibilidade comercial de compra: evento publicado, lote ativo e janela de vendas.
 * PostgreSQL real via Testcontainers; janelas relativas a Instant.now() (sem relógio absoluto).
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class PurchaseEligibilityIntegrationTest {

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

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final String commonPassword = "password123";

    private User organizer;
    private String buyerToken;
    private User buyer;

    @BeforeEach
    void setup() {
        paymentTransactionRepository.deleteAll();
        ticketRepository.deleteAll();
        orderRepository.deleteAll();
        simulatedCardRepository.deleteAll();
        ticketLotRepository.deleteAll();
        eventRepository.deleteAll();
        userRepository.deleteAll();

        organizer = userRepository.save(User.builder()
                .name("Organizer")
                .email("organizer@test.com")
                .passwordHash(passwordEncoder.encode(commonPassword))
                .role(Role.ORGANIZADOR)
                .build());

        buyer = userRepository.save(User.builder()
                .name("Buyer")
                .email("buyer@test.com")
                .passwordHash(passwordEncoder.encode(commonPassword))
                .role(Role.COMPRADOR)
                .build());

        try {
            buyerToken = obtainToken(buyer.getEmail());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String obtainToken(String email) throws Exception {
        var res = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, commonPassword)))
                .andExpect(status().isOk())
                .andReturn();
        return res.getResponse().getContentAsString().split("\"token\":\"")[1].split("\"")[0];
    }

    private Event createEvent(EventStatus status) {
        return eventRepository.save(Event.builder()
                .title("Event " + status)
                .description("d")
                .startsAt(OffsetDateTime.now().plus(10, ChronoUnit.DAYS))
                .endsAt(OffsetDateTime.now().plus(11, ChronoUnit.DAYS))
                .location("Fortaleza")
                .organizer(organizer)
                .status(status)
                .build());
    }

    private TicketLot createLot(Event event, Instant salesStart, Instant salesEnd) {
        return ticketLotRepository.save(TicketLot.builder()
                .event(event)
                .name("Lote 1")
                .unitPrice(new BigDecimal("100.00"))
                .capacity(10)
                .salesStart(salesStart)
                .salesEnd(salesEnd)
                .status(LotStatus.ACTIVE)
                .build());
    }

    private TicketLot openLot(Event event) {
        return createLot(event,
                Instant.now().minus(1, ChronoUnit.HOURS),
                Instant.now().plus(1, ChronoUnit.HOURS));
    }

    private ResultActions reserve(UUID lotId, int quantity) throws Exception {
        return mockMvc.perform(post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + buyerToken)
                .content("{\"items\":[{\"ticketLotId\":\"%s\",\"quantity\":%d}]}".formatted(lotId, quantity)));
    }

    private UUID reserveOk(TicketLot lot, int quantity) throws Exception {
        String body = reserve(lot.getId(), quantity)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(body.split("\"id\":\"")[1].split("\"")[0]);
    }

    private ResultActions checkout(UUID orderId) throws Exception {
        UUID cardId = simulatedCardRepository.findAll().isEmpty()
                ? createCard()
                : simulatedCardRepository.findAll().get(0).getId();
        return mockMvc.perform(post("/api/orders/{orderId}/checkout", orderId)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + buyerToken)
                .content("{\"cardId\":\"" + cardId + "\"}"));
    }

    private UUID createCard() throws Exception {
        String body = mockMvc.perform(post("/api/cards")
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(body.split("\"id\":\"")[1].split("\"")[0]);
    }

    // ===== Criação de reserva =====

    @Test
    void naoDeveReservarQuandoEventoEstaDraft() throws Exception {
        TicketLot lot = openLot(createEvent(EventStatus.DRAFT));

        reserve(lot.getId(), 1)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("Evento não está publicado para venda"));

        assertThat(ticketLotRepository.findById(lot.getId()).orElseThrow().getReserved()).isZero();
        assertThat(orderRepository.count()).isZero();
    }

    @Test
    void naoDeveReservarQuandoEventoEstaCancelado() throws Exception {
        TicketLot lot = openLot(createEvent(EventStatus.CANCELLED));

        reserve(lot.getId(), 1).andExpect(status().isUnprocessableEntity());

        assertThat(orderRepository.count()).isZero();
    }

    @Test
    void deveReservarEventoPublicadoComLoteAtivoDentroDaJanela() throws Exception {
        TicketLot lot = openLot(createEvent(EventStatus.PUBLISHED));

        reserve(lot.getId(), 2).andExpect(status().isCreated());

        assertThat(ticketLotRepository.findById(lot.getId()).orElseThrow().getReserved()).isEqualTo(2);
    }

    @Test
    void naoDeveReservarAntesDoInicioDasVendas() throws Exception {
        Event event = createEvent(EventStatus.PUBLISHED);
        TicketLot lot = createLot(event,
                Instant.now().plus(1, ChronoUnit.HOURS),
                Instant.now().plus(2, ChronoUnit.HOURS));

        reserve(lot.getId(), 1)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("Vendas ainda não iniciadas para o lote: " + lot.getName()));

        assertThat(orderRepository.count()).isZero();
    }

    @Test
    void naoDeveReservarDepoisDoFimDasVendas() throws Exception {
        Event event = createEvent(EventStatus.PUBLISHED);
        TicketLot lot = createLot(event,
                Instant.now().minus(2, ChronoUnit.HOURS),
                Instant.now().minus(1, ChronoUnit.HOURS));

        reserve(lot.getId(), 1)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("Vendas encerradas para o lote: " + lot.getName()));

        assertThat(orderRepository.count()).isZero();
    }

    @Test
    void naoDeveReservarLoteInativo() throws Exception {
        Event event = createEvent(EventStatus.PUBLISHED);
        TicketLot lot = openLot(event);
        jdbcTemplate.update("UPDATE ticket_lots SET status = 'INACTIVE' WHERE id = ?", lot.getId());

        reserve(lot.getId(), 1)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("Lote não está ativo para venda: " + lot.getName()));

        assertThat(orderRepository.count()).isZero();
    }

    // ===== Checkout de reserva existente =====

    @Test
    void naoDeveFinalizarCheckoutQuandoEventoVoltaParaDraft() throws Exception {
        Event event = createEvent(EventStatus.PUBLISHED);
        TicketLot lot = openLot(event);
        UUID orderId = reserveOk(lot, 2);

        event.setStatus(EventStatus.DRAFT);
        eventRepository.save(event);

        checkout(orderId)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("Evento não está publicado para venda"));

        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(ticketRepository.findByOrderId(orderId)).isEmpty();
        assertThat(paymentTransactionRepository.count()).isZero();
        assertThat(ticketLotRepository.findById(lot.getId()).orElseThrow().getReserved()).isEqualTo(2);
    }

    @Test
    void deveFinalizarReservaValidaMesmoDepoisDeSalesEnd() throws Exception {
        Event event = createEvent(EventStatus.PUBLISHED);
        TicketLot lot = openLot(event);
        UUID orderId = reserveOk(lot, 2);

        // Vendas encerradas depois da reserva: o checkout da reserva legítima continua permitido.
        jdbcTemplate.update("UPDATE ticket_lots SET sales_end = ? WHERE id = ?",
                Timestamp.from(Instant.now().minus(1, ChronoUnit.MINUTES)), lot.getId());

        checkout(orderId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));

        assertThat(ticketRepository.findByOrderId(orderId)).hasSize(2);
        assertThat(ticketLotRepository.findById(lot.getId()).orElseThrow().getSold()).isEqualTo(2);
    }

    @Test
    void naoDeveFinalizarCheckoutQuandoLoteFoiDesativado() throws Exception {
        Event event = createEvent(EventStatus.PUBLISHED);
        TicketLot lot = openLot(event);
        UUID orderId = reserveOk(lot, 2);

        jdbcTemplate.update("UPDATE ticket_lots SET status = 'INACTIVE' WHERE id = ?", lot.getId());

        checkout(orderId)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("Lote não está ativo para venda: " + lot.getName()));

        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(ticketRepository.findByOrderId(orderId)).isEmpty();
        assertThat(paymentTransactionRepository.count()).isZero();
    }
}
