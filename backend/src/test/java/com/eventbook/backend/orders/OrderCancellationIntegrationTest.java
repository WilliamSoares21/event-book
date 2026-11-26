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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testes de integração reais do cancelamento (RN-06) contra PostgreSQL via Testcontainers.
 * Sem mocks: exercita JPA, transação e constraints de verdade.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class OrderCancellationIntegrationTest {

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

    // ===== Helpers =====

    private User createBuyer(String email) {
        return userRepository.save(User.builder()
                .name("Buyer " + email)
                .email(email)
                .passwordHash(passwordEncoder.encode(commonPassword))
                .role(Role.COMPRADOR)
                .build());
    }

    private User createOrganizer(String email) {
        return userRepository.save(User.builder()
                .name("Organizer " + email)
                .email(email)
                .passwordHash(passwordEncoder.encode(commonPassword))
                .role(Role.ORGANIZADOR)
                .build());
    }

    private TicketLot createActiveLot(User organizer, int capacity, BigDecimal price, OffsetDateTime eventStartsAt) {
        Event event = eventRepository.save(Event.builder()
                .title("Cancellation Test Event")
                .description("Test Description")
                .startsAt(eventStartsAt)
                .endsAt(eventStartsAt.plus(1, ChronoUnit.DAYS))
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
            {
                "items": [
                    { "ticketLotId": "%s", "quantity": %d }
                ]
            }
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

    private UUID createCard(String token) throws Exception {
        String body = mockMvc.perform(post("/api/cards")
                        .header("Authorization", "Bearer " + token))
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

    private ResultActions performCancel(String token, UUID orderId) throws Exception {
        return mockMvc.perform(delete("/api/orders/{orderId}", orderId)
                .header("Authorization", "Bearer " + token));
    }

    // ===== Functional Tests =====

    @Test
    void compradorDeveCancelarPedidoPendingDentroDoPrazo() throws Exception {
        User organizer = createOrganizer("org@test.com");
        User buyer = createBuyer("buyer@test.com");
        // Event starts in 2 days (well beyond 12h deadline)
        TicketLot lot = createActiveLot(organizer, 100, new BigDecimal("50.00"),
                OffsetDateTime.now().plus(2, ChronoUnit.DAYS));
        String buyerToken = obtainToken(buyer.getEmail());

        UUID orderId = createOrder(buyerToken, lot.getId(), 2);

        // Cancel PENDING order
        performCancel(buyerToken, orderId).andExpect(status().isNoContent());

        // Verify order is cancelled
        Order order = orderRepository.findById(orderId).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getCancelledAt()).isNotNull();

        // Verify reserved released
        TicketLot updatedLot = ticketLotRepository.findById(lot.getId()).orElseThrow();
        assertThat(updatedLot.getReserved()).isZero();
        assertThat(updatedLot.getSold()).isZero();

        // No financial transaction created
        assertThat(paymentTransactionRepository.findByOrderIdAndStatus(orderId, PaymentStatus.REFUNDED)).isEmpty();
    }

    @Test
    void compradorDeveCancelarPedidoPaidDentroDoPrazoComEstorno() throws Exception {
        User organizer = createOrganizer("org@test.com");
        User buyer = createBuyer("buyer@test.com");
        TicketLot lot = createActiveLot(organizer, 100, new BigDecimal("50.00"),
                OffsetDateTime.now().plus(2, ChronoUnit.DAYS));
        String buyerToken = obtainToken(buyer.getEmail());

        UUID orderId = createOrder(buyerToken, lot.getId(), 2);
        UUID cardId = createCard(buyerToken);

        // Checkout to make it PAID
        performCheckout(buyerToken, orderId, cardId).andExpect(status().isOk());

        // Capture card state after checkout
        SimulatedCard cardAfterCheckout = simulatedCardRepository.findById(cardId).orElseThrow();
        BigDecimal availableLimitAfterCheckout = cardAfterCheckout.getAvailableLimit();

        // Cancel PAID order
        performCancel(buyerToken, orderId).andExpect(status().isNoContent());

        // Verify order is cancelled
        Order order = orderRepository.findById(orderId).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getCancelledAt()).isNotNull();

        // Verify sold released
        TicketLot updatedLot = ticketLotRepository.findById(lot.getId()).orElseThrow();
        assertThat(updatedLot.getReserved()).isZero();
        assertThat(updatedLot.getSold()).isZero();

        // Verify tickets cancelled
        List<Ticket> tickets = ticketRepository.findByOrderId(orderId);
        assertThat(tickets).hasSize(2);
        assertThat(tickets).allMatch(t -> t.getStatus() == TicketStatus.CANCELLED);

        // Verify financial: REFUNDED transaction created
        var refundedTx = paymentTransactionRepository.findByOrderIdAndStatus(orderId, PaymentStatus.REFUNDED);
        assertThat(refundedTx).isPresent();
        assertThat(refundedTx.get().getAmount()).isEqualByComparingTo("100.00");

        // Original APPROVED transaction preserved
        var approvedTx = paymentTransactionRepository.findByOrderIdAndStatus(orderId, PaymentStatus.APPROVED);
        assertThat(approvedTx).isPresent();
        assertThat(approvedTx.get().getAmount()).isEqualByComparingTo("100.00");

        // Card limit restored
        SimulatedCard cardAfterCancel = simulatedCardRepository.findById(cardId).orElseThrow();
        BigDecimal totalLimit = cardAfterCancel.getTotalLimit();
        assertThat(cardAfterCancel.getAvailableLimit()).isEqualByComparingTo(totalLimit);
        assertThat(cardAfterCancel.getAvailableLimit()).isEqualByComparingTo(availableLimitAfterCheckout.add(new BigDecimal("100.00")));
    }

    @Test
    void organizadorDeveCancelarPedidoForaDoPrazo() throws Exception {
        User organizer = createOrganizer("org@test.com");
        User buyer = createBuyer("buyer@test.com");
        // Event starts in 1 hour (within 12h deadline for buyer, but organizer can cancel anytime)
        TicketLot lot = createActiveLot(organizer, 100, new BigDecimal("50.00"),
                OffsetDateTime.now().plus(1, ChronoUnit.HOURS));
        String buyerToken = obtainToken(buyer.getEmail());
        String organizerToken = obtainToken(organizer.getEmail());

        UUID orderId = createOrder(buyerToken, lot.getId(), 2);
        UUID cardId = createCard(buyerToken);
        performCheckout(buyerToken, orderId, cardId).andExpect(status().isOk());

        // Organizer cancels (even though within 12h)
        performCancel(organizerToken, orderId).andExpect(status().isNoContent());

        Order order = orderRepository.findById(orderId).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getCancelledAt()).isNotNull();
    }

    @Test
    void compradorNaoDeveCancelarForaDoPrazoDe12Horas() throws Exception {
        User organizer = createOrganizer("org@test.com");
        User buyer = createBuyer("buyer@test.com");
        // Event starts in 1 hour (within 12h deadline)
        TicketLot lot = createActiveLot(organizer, 100, new BigDecimal("50.00"),
                OffsetDateTime.now().plus(1, ChronoUnit.HOURS));
        String buyerToken = obtainToken(buyer.getEmail());

        UUID orderId = createOrder(buyerToken, lot.getId(), 2);
        UUID cardId = createCard(buyerToken);
        performCheckout(buyerToken, orderId, cardId).andExpect(status().isOk());

        // Buyer tries to cancel within 12h - should fail
        performCancel(buyerToken, orderId).andExpect(status().isForbidden());

        // Order should remain PAID
        Order order = orderRepository.findById(orderId).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    void usuarioNaoAutorizadoNaoDeveCancelar() throws Exception {
        User organizer = createOrganizer("org@test.com");
        User buyer = createBuyer("buyer@test.com");
        User other = createBuyer("other@test.com");
        TicketLot lot = createActiveLot(organizer, 100, new BigDecimal("50.00"),
                OffsetDateTime.now().plus(2, ChronoUnit.DAYS));
        String buyerToken = obtainToken(buyer.getEmail());
        String otherToken = obtainToken(other.getEmail());

        UUID orderId = createOrder(buyerToken, lot.getId(), 2);

        performCancel(otherToken, orderId).andExpect(status().isForbidden());

        Order order = orderRepository.findById(orderId).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void pedidoExpiradoNaoPodeSerCancelado() throws Exception {
        User organizer = createOrganizer("org@test.com");
        User buyer = createBuyer("buyer@test.com");
        TicketLot lot = createActiveLot(organizer, 100, new BigDecimal("50.00"),
                OffsetDateTime.now().plus(2, ChronoUnit.DAYS));
        String buyerToken = obtainToken(buyer.getEmail());

        UUID orderId = createOrder(buyerToken, lot.getId(), 2);

        // Manually expire the order (expiresAt in the past, but status still PENDING)
        Order order = orderRepository.findById(orderId).orElseThrow();
        order.setExpiresAt(Instant.now().minusSeconds(60));
        orderRepository.save(order);

        performCancel(buyerToken, orderId).andExpect(status().isUnprocessableEntity());

        // Estado inalterado: scheduler é responsável por marcar como EXPIRED
        Order expiredOrder = orderRepository.findById(orderId).orElseThrow();
        assertThat(expiredOrder.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(expiredOrder.getExpiresAt()).isBefore(Instant.now());
    }

    @Test
    void pedidoJaCanceladoNaoPodeSerCanceladoNovamente() throws Exception {
        User organizer = createOrganizer("org@test.com");
        User buyer = createBuyer("buyer@test.com");
        TicketLot lot = createActiveLot(organizer, 100, new BigDecimal("50.00"),
                OffsetDateTime.now().plus(2, ChronoUnit.DAYS));
        String buyerToken = obtainToken(buyer.getEmail());

        UUID orderId = createOrder(buyerToken, lot.getId(), 2);

        // First cancellation
        performCancel(buyerToken, orderId).andExpect(status().isNoContent());

        // Second cancellation attempt
        performCancel(buyerToken, orderId).andExpect(status().isConflict());

        Order order = orderRepository.findById(orderId).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void cancelamentoBloqueadoQuandoExisteIngressoComCheckIn() throws Exception {
        User organizer = createOrganizer("org@test.com");
        User buyer = createBuyer("buyer@test.com");
        TicketLot lot = createActiveLot(organizer, 100, new BigDecimal("50.00"),
                OffsetDateTime.now().plus(2, ChronoUnit.DAYS));
        String buyerToken = obtainToken(buyer.getEmail());

        UUID orderId = createOrder(buyerToken, lot.getId(), 2);
        UUID cardId = createCard(buyerToken);
        performCheckout(buyerToken, orderId, cardId).andExpect(status().isOk());

        // Mark one ticket as CHECKED_IN
        List<Ticket> tickets = ticketRepository.findByOrderId(orderId);
        Ticket checkedInTicket = tickets.get(0);
        checkedInTicket.setStatus(TicketStatus.CHECKED_IN);
        ticketRepository.save(checkedInTicket);

        // Try to cancel - should fail
        performCancel(buyerToken, orderId).andExpect(status().isUnprocessableEntity());

        // Order should remain PAID
        Order order = orderRepository.findById(orderId).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    void cancelamentoDePendingLiberaReserved() throws Exception {
        User organizer = createOrganizer("org@test.com");
        User buyer = createBuyer("buyer@test.com");
        TicketLot lot = createActiveLot(organizer, 10, new BigDecimal("50.00"),
                OffsetDateTime.now().plus(2, ChronoUnit.DAYS));
        String buyerToken = obtainToken(buyer.getEmail());

        UUID orderId = createOrder(buyerToken, lot.getId(), 3);

        // Verify reserved before cancel
        TicketLot lotBefore = ticketLotRepository.findById(lot.getId()).orElseThrow();
        assertThat(lotBefore.getReserved()).isEqualTo(3);

        performCancel(buyerToken, orderId).andExpect(status().isNoContent());

        TicketLot lotAfter = ticketLotRepository.findById(lot.getId()).orElseThrow();
        assertThat(lotAfter.getReserved()).isZero();
        assertThat(lotAfter.getSold()).isZero();
    }

    @Test
    void cancelamentoDePaidLiberaSold() throws Exception {
        User organizer = createOrganizer("org@test.com");
        User buyer = createBuyer("buyer@test.com");
        TicketLot lot = createActiveLot(organizer, 10, new BigDecimal("50.00"),
                OffsetDateTime.now().plus(2, ChronoUnit.DAYS));
        String buyerToken = obtainToken(buyer.getEmail());

        UUID orderId = createOrder(buyerToken, lot.getId(), 3);
        UUID cardId = createCard(buyerToken);
        performCheckout(buyerToken, orderId, cardId).andExpect(status().isOk());

        // Verify sold before cancel
        TicketLot lotBefore = ticketLotRepository.findById(lot.getId()).orElseThrow();
        assertThat(lotBefore.getSold()).isEqualTo(3);

        performCancel(buyerToken, orderId).andExpect(status().isNoContent());

        TicketLot lotAfter = ticketLotRepository.findById(lot.getId()).orElseThrow();
        assertThat(lotAfter.getSold()).isZero();
        assertThat(lotAfter.getReserved()).isZero();
    }

    @Test
    void cancelamentoDePaidDevolveLimiteDisponivel() throws Exception {
        User organizer = createOrganizer("org@test.com");
        User buyer = createBuyer("buyer@test.com");
        TicketLot lot = createActiveLot(organizer, 100, new BigDecimal("50.00"),
                OffsetDateTime.now().plus(2, ChronoUnit.DAYS));
        String buyerToken = obtainToken(buyer.getEmail());

        UUID orderId = createOrder(buyerToken, lot.getId(), 2);
        UUID cardId = createCard(buyerToken);

        SimulatedCard cardBefore = simulatedCardRepository.findById(cardId).orElseThrow();
        BigDecimal initialLimit = cardBefore.getAvailableLimit();

        performCheckout(buyerToken, orderId, cardId).andExpect(status().isOk());

        SimulatedCard cardAfterCheckout = simulatedCardRepository.findById(cardId).orElseThrow();
        BigDecimal afterCheckoutLimit = cardAfterCheckout.getAvailableLimit();
        assertThat(afterCheckoutLimit).isEqualByComparingTo(initialLimit.subtract(new BigDecimal("100.00")));

        performCancel(buyerToken, orderId).andExpect(status().isNoContent());

        SimulatedCard cardAfterCancel = simulatedCardRepository.findById(cardId).orElseThrow();
        assertThat(cardAfterCancel.getAvailableLimit()).isEqualByComparingTo(initialLimit);
    }

    @Test
    void cancelamentoDePaidRegistraRefunded() throws Exception {
        User organizer = createOrganizer("org@test.com");
        User buyer = createBuyer("buyer@test.com");
        TicketLot lot = createActiveLot(organizer, 100, new BigDecimal("50.00"),
                OffsetDateTime.now().plus(2, ChronoUnit.DAYS));
        String buyerToken = obtainToken(buyer.getEmail());

        UUID orderId = createOrder(buyerToken, lot.getId(), 2);
        UUID cardId = createCard(buyerToken);
        performCheckout(buyerToken, orderId, cardId).andExpect(status().isOk());

        performCancel(buyerToken, orderId).andExpect(status().isNoContent());

        var refundedTx = paymentTransactionRepository.findByOrderIdAndStatus(orderId, PaymentStatus.REFUNDED);
        assertThat(refundedTx).isPresent();
        assertThat(refundedTx.get().getAmount()).isEqualByComparingTo("100.00");
    }

    @Test
    void transacaoApprovedOriginalPermaneceIntacta() throws Exception {
        User organizer = createOrganizer("org@test.com");
        User buyer = createBuyer("buyer@test.com");
        TicketLot lot = createActiveLot(organizer, 100, new BigDecimal("50.00"),
                OffsetDateTime.now().plus(2, ChronoUnit.DAYS));
        String buyerToken = obtainToken(buyer.getEmail());

        UUID orderId = createOrder(buyerToken, lot.getId(), 2);
        UUID cardId = createCard(buyerToken);
        performCheckout(buyerToken, orderId, cardId).andExpect(status().isOk());

        var approvedTxBefore = paymentTransactionRepository.findByOrderIdAndStatus(orderId, PaymentStatus.APPROVED);
        UUID approvedTxId = approvedTxBefore.get().getId();
        Instant approvedTxTime = approvedTxBefore.get().getAttemptedAt();

        performCancel(buyerToken, orderId).andExpect(status().isNoContent());

        var approvedTxAfter = paymentTransactionRepository.findByOrderIdAndStatus(orderId, PaymentStatus.APPROVED);
        assertThat(approvedTxAfter).isPresent();
        assertThat(approvedTxAfter.get().getId()).isEqualTo(approvedTxId);
        assertThat(approvedTxAfter.get().getAttemptedAt()).isEqualTo(approvedTxTime);
        assertThat(approvedTxAfter.get().getAmount()).isEqualByComparingTo("100.00");
    }

    @Test
    void naoDeveExistirEstornoDuplicado() throws Exception {
        User organizer = createOrganizer("org@test.com");
        User buyer = createBuyer("buyer@test.com");
        TicketLot lot = createActiveLot(organizer, 100, new BigDecimal("50.00"),
                OffsetDateTime.now().plus(2, ChronoUnit.DAYS));
        String buyerToken = obtainToken(buyer.getEmail());

        UUID orderId = createOrder(buyerToken, lot.getId(), 2);
        UUID cardId = createCard(buyerToken);
        performCheckout(buyerToken, orderId, cardId).andExpect(status().isOk());

        performCancel(buyerToken, orderId).andExpect(status().isNoContent());

        // Try to cancel again - should fail
        performCancel(buyerToken, orderId).andExpect(status().isConflict());

        // Verify only one REFUNDED transaction
        long refundedCount = paymentTransactionRepository.countByOrderIdAndStatus(orderId, PaymentStatus.REFUNDED);
        assertThat(refundedCount).isEqualTo(1);
    }

    // ===== Concurrency Tests =====

    @Test
    void duasRequisicoesSimultaneasDeCancelamentoNaoDuplicamEstorno() throws Exception {
        User organizer = createOrganizer("org@test.com");
        User buyer = createBuyer("buyer@test.com");
        TicketLot lot = createActiveLot(organizer, 100, new BigDecimal("50.00"),
                OffsetDateTime.now().plus(2, ChronoUnit.DAYS));
        String buyerToken = obtainToken(buyer.getEmail());

        UUID orderId = createOrder(buyerToken, lot.getId(), 2);
        UUID cardId = createCard(buyerToken);
        performCheckout(buyerToken, orderId, cardId).andExpect(status().isOk());

        // Execute two concurrent cancellation requests
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(2);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger forbiddenCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            for (int i = 0; i < 2; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        var result = mockMvc.perform(delete("/api/orders/{orderId}", orderId)
                                .header("Authorization", "Bearer " + buyerToken));
                        int status = result.andReturn().getResponse().getStatus();
                        if (status == 204) successCount.incrementAndGet();
                        else if (status == 403) forbiddenCount.incrementAndGet();
                        else if (status == 409) conflictCount.incrementAndGet();
                    } catch (Exception e) {
                        // Ignore
                    } finally {
                        endLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            endLatch.await(30, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        // Exactly one should succeed (204), the other should get 409 (already cancelled)
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(conflictCount.get()).isEqualTo(1);

        // Verify no duplicate refund
        long refundedCount = paymentTransactionRepository.countByOrderIdAndStatus(orderId, PaymentStatus.REFUNDED);
        assertThat(refundedCount).isEqualTo(1);

        // Verify order is cancelled
        Order order = orderRepository.findById(orderId).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void checkoutConcorrenteComCancelamento() throws Exception {
        User organizer = createOrganizer("org@test.com");
        User buyer = createBuyer("buyer@test.com");
        TicketLot lot = createActiveLot(organizer, 5, new BigDecimal("50.00"),
                OffsetDateTime.now().plus(2, ChronoUnit.DAYS));
        String buyerToken = obtainToken(buyer.getEmail());

        UUID orderId = createOrder(buyerToken, lot.getId(), 2);
        UUID cardId = createCard(buyerToken);

        // Execute checkout and cancel concurrently
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(2);
        AtomicReference<Integer> checkoutStatus = new AtomicReference<>();
        AtomicReference<Integer> cancelStatus = new AtomicReference<>();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    var result = performCheckout(buyerToken, orderId, cardId);
                    checkoutStatus.set(result.andReturn().getResponse().getStatus());
                } catch (Exception e) {
                    checkoutStatus.set(500);
                } finally {
                    endLatch.countDown();
                }
            });

            executor.submit(() -> {
                try {
                    startLatch.await();
                    var result = performCancel(buyerToken, orderId);
                    cancelStatus.set(result.andReturn().getResponse().getStatus());
                } catch (Exception e) {
                    cancelStatus.set(500);
                } finally {
                    endLatch.countDown();
                }
            });

            startLatch.countDown();
            endLatch.await(30, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        // One should succeed, the other should fail appropriately
        // The important thing is that final state is consistent
        Order finalOrder = orderRepository.findById(orderId).orElseThrow();
        TicketLot finalLot = ticketLotRepository.findById(lot.getId()).orElseThrow();

        if (finalOrder.getStatus() == OrderStatus.PAID) {
            // Checkout won
            assertThat(finalOrder.getPaidAt()).isNotNull();
            assertThat(finalLot.getSold()).isEqualTo(2);
            assertThat(finalLot.getReserved()).isZero();
            assertThat(ticketRepository.findByOrderId(orderId)).hasSize(2);
        } else if (finalOrder.getStatus() == OrderStatus.CANCELLED) {
            // Cancel won
            assertThat(finalOrder.getCancelledAt()).isNotNull();
            assertThat(finalLot.getSold()).isZero();
            assertThat(finalLot.getReserved()).isZero();
            // If checkout started but cancel won, tickets should not exist or be cancelled
            List<Ticket> tickets = ticketRepository.findByOrderId(orderId);
            assertThat(tickets).allMatch(t -> t.getStatus() == TicketStatus.CANCELLED);
        } else {
            // Should not happen - order must be either PAID or CANCELLED
            throw new AssertionError("Estado inesperado: " + finalOrder.getStatus());
        }
    }

    @Test
    void constraintUnicaBancoImpedeEstornoDuplicado() throws Exception {
        // Este teste valida que o índice único parcial no banco de dados
        // impede a criação de duas transações REFUNDED para o mesmo pedido.
        User organizer = createOrganizer("org@test.com");
        User buyer = createBuyer("buyer@test.com");
        TicketLot lot = createActiveLot(organizer, 100, new BigDecimal("50.00"),
                OffsetDateTime.now().plus(2, ChronoUnit.DAYS));
        String buyerToken = obtainToken(buyer.getEmail());

        UUID orderId = createOrder(buyerToken, lot.getId(), 2);
        UUID cardId = createCard(buyerToken);
        performCheckout(buyerToken, orderId, cardId).andExpect(status().isOk());

        // Primeira transação REFUNDED via service (deve funcionar)
        performCancel(buyerToken, orderId).andExpect(status().isNoContent());

        // Verifica que a primeira REFUNDED existe
        var refundedTx1 = paymentTransactionRepository.findByOrderIdAndStatus(orderId, PaymentStatus.REFUNDED);
        assertThat(refundedTx1).isPresent();

        // Tenta inserir segunda REFUNDED diretamente no banco (bypass do service)
        // Deve falhar com DataIntegrityViolationException devido ao índice único
        PaymentTransaction duplicateRefund = PaymentTransaction.builder()
                .order(orderRepository.findById(orderId).orElseThrow())
                .card(simulatedCardRepository.findById(cardId).orElseThrow())
                .amount(new BigDecimal("100.00"))
                .status(PaymentStatus.REFUNDED)
                .build();

        assertThatThrownBy(() -> paymentTransactionRepository.saveAndFlush(duplicateRefund))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);

        // Verifica que ainda existe apenas uma REFUNDED
        long refundedCount = paymentTransactionRepository.countByOrderIdAndStatus(orderId, PaymentStatus.REFUNDED);
        assertThat(refundedCount).isEqualTo(1);

        // Verifica que a transação original APPROVED permanece intacta
        var approvedTx = paymentTransactionRepository.findByOrderIdAndStatus(orderId, PaymentStatus.APPROVED);
        assertThat(approvedTx).isPresent();
        assertThat(approvedTx.get().getAmount()).isEqualByComparingTo("100.00");
    }
}