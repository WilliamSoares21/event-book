package com.eventbook.backend.orders;

import com.eventbook.backend.cards.SimulatedCardRepository;
import com.eventbook.backend.events.Event;
import com.eventbook.backend.events.EventRepository;
import com.eventbook.backend.events.EventStatus;
import com.eventbook.backend.lots.LotStatus;
import com.eventbook.backend.lots.TicketLot;
import com.eventbook.backend.lots.TicketLotRepository;
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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
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
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testes de integração reais do checkout (RN-05) contra PostgreSQL via Testcontainers.
 * Sem mocks: exercita JPA, transação e constraints de verdade.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class OrderCheckoutIntegrationTest {

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
    private PaymentTransactionRepository paymentTransactionRepository;

    @Autowired
    private SimulatedCardRepository simulatedCardRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final String commonPassword = "password123";

    @BeforeEach
    void setup() {
        paymentTransactionRepository.deleteAll();
        simulatedCardRepository.deleteAll();
        ticketRepository.deleteAll();
        orderRepository.deleteAll();
        ticketLotRepository.deleteAll();
        eventRepository.deleteAll();
        userRepository.deleteAll();
    }

    // ===== Helpers =====

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
                .name("Test Organizer")
                .email("organizer@test.com")
                .passwordHash(passwordEncoder.encode(commonPassword))
                .role(Role.ORGANIZADOR)
                .build());
    }

    private TicketLot createActiveLot(User organizer, int capacity, BigDecimal price) {
        Event event = eventRepository.save(Event.builder()
                .title("Checkout IT Event")
                .description("Test Description")
                .startsAt(OffsetDateTime.now().plus(1, ChronoUnit.DAYS))
                .endsAt(OffsetDateTime.now().plus(2, ChronoUnit.DAYS))
                .location("Test Location")
                .organizer(organizer)
                .status(EventStatus.PUBLISHED)
                .build());

        return ticketLotRepository.save(TicketLot.builder()
                .event(event)
                .name("Lote 1")
                .unitPrice(price)
                .capacity(capacity)
                .salesStart(Instant.now().minus(1, ChronoUnit.HOURS))
                .salesEnd(Instant.now().plus(1, ChronoUnit.HOURS))
                .status(LotStatus.ACTIVE)
                .build());
    }

    private String createRequestBody(UUID lotId, int quantity) {
        return """
            { "ticketLotId": "%s", "quantity": %d }
            """.formatted(lotId, quantity);
    }

    private String obtainToken(String email) throws Exception {
        var result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            { "email": "%s", "password": "%s" }
                            """.formatted(email, commonPassword)))
                .andExpect(status().isOk())
                .andReturn();
        return result.getResponse().getContentAsString().split("\"token\":\"")[1].split("\"")[0];
    }

    private UUID createOrder(String token, UUID lotId, int quantity) throws Exception {
        String body = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(createRequestBody(lotId, quantity)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(body.split("\"id\":\"")[1].split("\"")[0]);
    }

    private ResultActions performCheckout(String token, UUID orderId, UUID cardId) throws Exception {
        return mockMvc.perform(post("/api/orders/{orderId}/checkout", orderId)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + token)
                .content("{\"cardId\":\"" + cardId + "\"}"));
    }

    private UUID createCard(String token) throws Exception {
        String body = mockMvc.perform(post("/api/cards")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(body.split("\"id\":\"")[1].split("\"")[0]);
    }

    // ===== Sucesso =====

    @Test
    void deveRealizarCheckoutPersistindoEstadoETickets() throws Exception {
        User organizer = createOrganizer();
        User buyer = createBuyer("buyer@test.com");
        TicketLot lot = createActiveLot(organizer, 100, new BigDecimal("50.00"));
        String token = obtainToken(buyer.getEmail());

        UUID orderId = createOrder(token, lot.getId(), 2);
        UUID cardId = createCard(token);

        // Altera o preço atual do lote: o checkout deve ignorá-lo e usar o snapshot da reserva.
        jdbcTemplate.update("UPDATE ticket_lots SET unit_price = 999.99 WHERE id = ?", lot.getId());

        var result = performCheckout(token, orderId, cardId)
                .andExpect(status().isOk())
                .andReturn();
        String body = result.getResponse().getContentAsString();

        // Order persistido como PAID, paidAt preenchido, snapshot preservado
        Order order = orderRepository.findById(orderId).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(order.getPaidAt()).isNotNull();
        assertThat(order.getUnitPriceSnapshot()).isEqualByComparingTo("50.00");
        assertThat(body).contains("\"status\":\"PAID\"");
        assertThat(body).contains("\"totalPrice\":100.00");

        // Tickets persistidos: quantidade exata, VALID, ids e códigos únicos
        List<Ticket> tickets = ticketRepository.findByOrderId(orderId);
        assertThat(tickets).hasSize(2);
        assertThat(tickets).allMatch(t -> t.getStatus() == TicketStatus.VALID);
        assertThat(tickets).allMatch(t -> t.getOrder().getId().equals(orderId));
        assertThat(tickets.stream().map(Ticket::getId).distinct().count()).isEqualTo(2);
        assertThat(tickets.stream().map(Ticket::getUniqueCode).distinct().count()).isEqualTo(2);

        // Conversão reserved -> sold
        TicketLot updatedLot = ticketLotRepository.findById(lot.getId()).orElseThrow();
        assertThat(updatedLot.getReserved()).isZero();
        assertThat(updatedLot.getSold()).isEqualTo(2);
    }

    // ===== Autorização e existência =====

    @Test
    void deveListarSomenteTicketsEmitidosParaOCompradorAutenticado() throws Exception {
        User organizer = createOrganizer();
        TicketLot lot = createActiveLot(organizer, 100, new BigDecimal("50.00"));
        String buyerToken = obtainToken(createBuyer("buyer@test.com").getEmail());
        String otherToken = obtainToken(createBuyer("other@test.com").getEmail());
        UUID orderId = createOrder(buyerToken, lot.getId(), 1);
        createOrder(otherToken, lot.getId(), 1);
        performCheckout(buyerToken, orderId, createCard(buyerToken)).andExpect(status().isOk());
        Ticket ticket = ticketRepository.findByOrderId(orderId).getFirst();

        mockMvc.perform(get("/api/tickets/me").header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(ticket.getId().toString()))
                .andExpect(jsonPath("$[0].uniqueCode").value(ticket.getUniqueCode()))
                .andExpect(jsonPath("$[0].status").value("VALID"));
        mockMvc.perform(get("/api/tickets/me").header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/tickets/me")
                        .header("Authorization", "Bearer " + obtainToken(organizer.getEmail())))
                .andExpect(status().isForbidden());
    }

    @Test
    void deveNegarCheckoutParaOutroComprador() throws Exception {
        User organizer = createOrganizer();
        User owner = createBuyer("owner@test.com");
        User other = createBuyer("other@test.com");
        TicketLot lot = createActiveLot(organizer, 100, new BigDecimal("50.00"));

        UUID orderId = createOrder(obtainToken(owner.getEmail()), lot.getId(), 2);
        String otherToken = obtainToken(other.getEmail());

        performCheckout(otherToken, orderId, UUID.randomUUID()).andExpect(status().isForbidden());

        // Estado intacto
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(ticketRepository.findByOrderId(orderId)).isEmpty();
        TicketLot untouched = ticketLotRepository.findById(lot.getId()).orElseThrow();
        assertThat(untouched.getReserved()).isEqualTo(2);
        assertThat(untouched.getSold()).isZero();
    }

    @Test
    void deveRetornar404ParaPedidoInexistente() throws Exception {
        User buyer = createBuyer("buyer@test.com");
        String token = obtainToken(buyer.getEmail());

        performCheckout(token, UUID.randomUUID(), UUID.randomUUID()).andExpect(status().isNotFound());
    }

    // ===== Contrato HTTP (Correção A) =====

    @Test
    void deveRetornar409ParaPedidoPagoMesmoComPrazoExpirado() throws Exception {
        User organizer = createOrganizer();
        User buyer = createBuyer("buyer@test.com");
        TicketLot lot = createActiveLot(organizer, 100, new BigDecimal("50.00"));
        String token = obtainToken(buyer.getEmail());

        UUID orderId = createOrder(token, lot.getId(), 1);
        Order order = orderRepository.findById(orderId).orElseThrow();
        order.setStatus(OrderStatus.PAID);
        order.setExpiresAt(Instant.now().minusSeconds(60));
        orderRepository.save(order);

        performCheckout(token, orderId, UUID.randomUUID()).andExpect(status().isConflict());

        // PAID não vira PAID de novo; nenhum ticket novo
        assertThat(ticketRepository.findByOrderId(orderId)).isEmpty();
    }

    @Test
    void deveRetornar422ParaPedidoCancelado() throws Exception {
        User organizer = createOrganizer();
        User buyer = createBuyer("buyer@test.com");
        TicketLot lot = createActiveLot(organizer, 100, new BigDecimal("50.00"));
        String token = obtainToken(buyer.getEmail());

        UUID orderId = createOrder(token, lot.getId(), 1);
        Order order = orderRepository.findById(orderId).orElseThrow();
        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);

        performCheckout(token, orderId, UUID.randomUUID()).andExpect(status().isUnprocessableEntity());
        assertThat(ticketRepository.findByOrderId(orderId)).isEmpty();
    }

    @Test
    void deveRetornar422ParaPedidoPendenteExpirado() throws Exception {
        User organizer = createOrganizer();
        User buyer = createBuyer("buyer@test.com");
        TicketLot lot = createActiveLot(organizer, 100, new BigDecimal("50.00"));
        String token = obtainToken(buyer.getEmail());

        UUID orderId = createOrder(token, lot.getId(), 2);
        Order order = orderRepository.findById(orderId).orElseThrow();
        order.setExpiresAt(Instant.now().minusSeconds(60));
        orderRepository.save(order);

        performCheckout(token, orderId, UUID.randomUUID()).andExpect(status().isUnprocessableEntity());

        // Estado inalterado
        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(ticketRepository.findByOrderId(orderId)).isEmpty();
        TicketLot untouched = ticketLotRepository.findById(lot.getId()).orElseThrow();
        assertThat(untouched.getReserved()).isEqualTo(2);
        assertThat(untouched.getSold()).isZero();
    }

    // ===== Unicidade real da constraint =====

    @Test
    void deveAplicarConstraintUnicaDeUniqueCodeNoPostgres() throws Exception {
        User organizer = createOrganizer();
        User buyer = createBuyer("buyer@test.com");
        TicketLot lot = createActiveLot(organizer, 100, new BigDecimal("50.00"));
        UUID orderId = createOrder(obtainToken(buyer.getEmail()), lot.getId(), 1);
        Order order = orderRepository.findById(orderId).orElseThrow();

        String code = "DUPLICATE-CODE-0123456789";
        ticketRepository.saveAndFlush(Ticket.builder()
                .order(order).uniqueCode(code).status(TicketStatus.VALID).build());

        assertThatThrownBy(() -> ticketRepository.saveAndFlush(Ticket.builder()
                .order(order).uniqueCode(code).status(TicketStatus.VALID).build()))
                .isInstanceOf(DataIntegrityViolationException.class);

        // Somente o primeiro ticket sobreviveu
        assertThat(ticketRepository.findByOrderId(orderId)).hasSize(1);
    }
}