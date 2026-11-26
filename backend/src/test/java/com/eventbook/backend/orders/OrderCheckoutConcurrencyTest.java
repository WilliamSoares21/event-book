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
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class OrderCheckoutConcurrencyTest {

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
    private OrderExpirationScheduler expirationScheduler;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private LockProbe lockProbe;

    @TestConfiguration
    static class LockProbeConfig {
        @Bean
        static LockProbe lockProbe() {
            return new LockProbe();
        }

        @Bean
        static BeanPostProcessor orderRepositoryLockProbe(LockProbe lockProbe) {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String beanName) {
                    if (bean instanceof OrderRepository repository && !(repository instanceof LockProbeRepository)) {
                        return wrapWithLockProbe(repository, lockProbe);
                    }
                    return bean;
                }
            };
        }
    }

    static final class LockProbe {
        private final AtomicBoolean armed = new AtomicBoolean(false);
        private volatile CountDownLatch locked = new CountDownLatch(1);
        private volatile CountDownLatch release = new CountDownLatch(1);

        void reset() {
            armed.set(false);
            locked = new CountDownLatch(1);
            release = new CountDownLatch(1);
        }

        void arm() {
            armed.set(true);
        }

        boolean takeArmed() {
            return armed.getAndSet(false);
        }

        void signalLocked() {
            locked.countDown();
        }

        boolean awaitLocked(long seconds) throws InterruptedException {
            return locked.await(seconds, TimeUnit.SECONDS);
        }

        void awaitRelease() {
            try {
                if (!release.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timeout esperando liberação do LockProbe");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }

        void release() {
            release.countDown();
        }
    }

    interface LockProbeRepository extends OrderRepository {
    }

    @SuppressWarnings("unchecked")
    private static OrderRepository wrapWithLockProbe(OrderRepository delegate, LockProbe probe) {
        return (OrderRepository) Proxy.newProxyInstance(
                OrderRepository.class.getClassLoader(),
                new Class<?>[]{LockProbeRepository.class},
                (proxy, method, args) -> {
                    try {
                        if ("findByIdWithItemsForUpdate".equals(method.getName()) && probe.takeArmed()) {
                            Object result = method.invoke(delegate, args);
                            probe.signalLocked();
                            probe.awaitRelease();
                            return result;
                        }
                        return method.invoke(delegate, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
    }

    private void awaitLockWaiter() throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (System.nanoTime() < deadline) {
            Integer waiters = jdbcTemplate.queryForObject("""
                    SELECT count(*) FROM pg_stat_activity
                    WHERE wait_event_type = 'Lock'
                      AND datname = current_database()
                      AND pid <> pg_backend_pid()
                    """, Integer.class);
            if (waiters != null && waiters > 0) {
                return;
            }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(50));
        }
        fail("A segunda transação não ficou aguardando o lock da Order (timeout)");
    }

    private int performCheckoutStatus(String token, UUID orderId, UUID cardId) throws Exception {
        return mockMvc.perform(post("/api/orders/{orderId}/checkout", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(checkoutBody(cardId)))
                .andReturn().getResponse().getStatus();
    }

    private String commonPassword = "password123";

    @BeforeEach
    void setup() {
        lockProbe.reset();
        paymentTransactionRepository.deleteAll();
        ticketRepository.deleteAll();
        orderRepository.deleteAll();
        simulatedCardRepository.deleteAll();
        ticketLotRepository.deleteAll();
        eventRepository.deleteAll();
        userRepository.deleteAll();
    }

    private User createBuyer(String email) {
        User buyer = User.builder()
                .name("Buyer " + email)
                .email(email)
                .passwordHash(passwordEncoder.encode(commonPassword))
                .role(Role.COMPRADOR)
                .build();
        return userRepository.save(buyer);
    }

    private User createOrganizer() {
        User organizer = User.builder()
                .name("Test Organizer")
                .email("organizer@test.com")
                .passwordHash(passwordEncoder.encode(commonPassword))
                .role(Role.ORGANIZADOR)
                .build();
        return userRepository.save(organizer);
    }

    private TicketLot createActiveLot(User organizer, int capacity, BigDecimal price) {
        Event event = Event.builder()
                .title("Concurrency Test Event")
                .description("Test Description")
                .startsAt(OffsetDateTime.now().plus(1, ChronoUnit.DAYS))
                .endsAt(OffsetDateTime.now().plus(2, ChronoUnit.DAYS))
                .location("Test Location")
                .organizer(organizer)
                .status(EventStatus.PUBLISHED)
                .build();
        event = eventRepository.save(event);

        TicketLot lot = TicketLot.builder()
                .event(event)
                .name("Lote 1")
                .unitPrice(price)
                .capacity(capacity)
                .salesStart(Instant.now().minus(1, ChronoUnit.HOURS))
                .salesEnd(Instant.now().plus(1, ChronoUnit.HOURS))
                .status(LotStatus.ACTIVE)
                .build();
        return ticketLotRepository.save(lot);
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

    private UUID createCard(String token) throws Exception {
        String body = mockMvc.perform(post("/api/cards")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(body.split("\"id\":\"")[1].split("\"")[0]);
    }

    private String checkoutBody(UUID cardId) {
        return "{\"cardId\":\"" + cardId + "\"}";
    }

    private UUID createOrderAndGetId(String token, UUID lotId, int quantity) throws Exception {
        String response = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(createRequestBody(lotId, quantity)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return UUID.fromString(response.split("\"id\":\"")[1].split("\"")[0]);
    }

    // ===== Checkout Concurrency Tests =====

    @Test
    void devePermitirApenasUmCheckoutEmPedidoDuplicadoSimultaneo() throws Exception {
        User organizer = createOrganizer();
        TicketLot lot = createActiveLot(organizer, 10, new BigDecimal("50.00"));

        User buyer = createBuyer("buyer@test.com");
        String token = obtainToken(buyer.getEmail());

        UUID cardId = createCard(token);
        UUID orderId = createOrderAndGetId(token, lot.getId(), 2);

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();

                    var request = post("/api/orders/{orderId}/checkout", orderId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("Authorization", "Bearer " + token)
                            .content(checkoutBody(cardId));

                    var result = mockMvc.perform(request);
                    int status = result.andReturn().getResponse().getStatus();

                    if (status == 200) {
                        successCount.incrementAndGet();
                    } else if (status == 409) {
                        conflictCount.incrementAndGet();
                    } else {
                        errorCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        endLatch.await();
        executor.shutdown();

        // Exactly one checkout should succeed
        assertThat(successCount.get()).isEqualTo(1);
        // All others should get 409 Conflict
        assertThat(conflictCount.get()).isEqualTo(threadCount - 1);
        assertThat(errorCount.get()).isEqualTo(0);

        // Verify final state in database
        Order finalOrder = orderRepository.findById(orderId).orElseThrow();
        assertThat(finalOrder.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(finalOrder.getPaidAt()).isNotNull();

        List<Ticket> tickets = ticketRepository.findByOrderId(orderId);
        assertThat(tickets).hasSize(2);
        assertThat(tickets).allMatch(t -> t.getStatus() == TicketStatus.VALID);

        TicketLot finalLot = ticketLotRepository.findById(lot.getId()).orElseThrow();
        assertThat(finalLot.getReserved()).isEqualTo(0); // 2 - 2 = 0
        assertThat(finalLot.getSold()).isEqualTo(2); // 0 + 2 = 2
    }

    @Test
    void checkoutAdquireLockPrimeiroEImpedeExpiracao() throws Exception {
        User organizer = createOrganizer();
        TicketLot lot = createActiveLot(organizer, 5, new BigDecimal("50.00"));

        User buyer = createBuyer("buyer@test.com");
        String token = obtainToken(buyer.getEmail());
        UUID cardId = createCard(token);
        UUID orderId = createOrderAndGetId(token, lot.getId(), 2);

        // Pedido PENDING e ainda válido; o scheduler não deve expirá-lo.
        Order staleOrder = orderRepository.findById(orderId).orElseThrow();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch checkoutDone = new CountDownLatch(1);
        CountDownLatch schedulerDone = new CountDownLatch(1);
        AtomicInteger checkoutStatus = new AtomicInteger(-1);
        try {
            // 1. Checkout é liberado e o probe o pausa EXATAMENTE após o lock real da Order.
            lockProbe.arm();
            executor.submit(() -> {
                try {
                    checkoutStatus.set(performCheckoutStatus(token, orderId, cardId));
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                } finally {
                    checkoutDone.countDown();
                }
            });

            // 2. Evento real confirmado: o Checkout já detém o lock.
            assertThat(lockProbe.awaitLocked(10))
                    .as("checkout deve adquirir o lock da Order primeiro")
                    .isTrue();

            // 3. Scheduler disputa o mesmo lock.
            executor.submit(() -> {
                try {
                    expirationScheduler.processSingleExpiredOrder(staleOrder);
                } finally {
                    schedulerDone.countDown();
                }
            });

            // 4. Observação no PostgreSQL: o scheduler está bloqueado aguardando o lock.
            awaitLockWaiter();

            // 5. Checkout conclui (PAID, tickets emitidos) e libera o lock no commit.
            lockProbe.release();
            assertThat(checkoutDone.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(schedulerDone.await(10, TimeUnit.SECONDS)).isTrue();
        } finally {
            executor.shutdown();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }

        assertThat(checkoutStatus.get()).isEqualTo(200);

        Order finalOrder = orderRepository.findById(orderId).orElseThrow();
        assertThat(finalOrder.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(finalOrder.getPaidAt()).isNotNull();

        TicketLot finalLot = ticketLotRepository.findById(lot.getId()).orElseThrow();
        assertThat(finalLot.getReserved()).isZero();
        assertThat(finalLot.getSold()).isEqualTo(2);
        assertThat(ticketRepository.findByOrderId(orderId)).hasSize(2);
    }

    @Test
    void schedulerAdquireLockPrimeiroEExpiraAntesDoCheckout() throws Exception {
        User organizer = createOrganizer();
        TicketLot lot = createActiveLot(organizer, 5, new BigDecimal("50.00"));

        User buyer = createBuyer("buyer@test.com");
        String token = obtainToken(buyer.getEmail());
        UUID cardId = createCard(token);
        UUID orderId = createOrderAndGetId(token, lot.getId(), 2);

        // Pedido PENDING porém já expirado: elegível à expiração.
        Order staleOrder = orderRepository.findById(orderId).orElseThrow();
        staleOrder.setExpiresAt(Instant.now().minusSeconds(60));
        orderRepository.save(staleOrder);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch schedulerDone = new CountDownLatch(1);
        AtomicInteger checkoutStatus = new AtomicInteger(-1);
        try {
            // 1. Scheduler é liberado e o probe o pausa EXATAMENTE após o lock real da Order.
            lockProbe.arm();
            executor.submit(() -> {
                try {
                    expirationScheduler.processSingleExpiredOrder(staleOrder);
                } finally {
                    schedulerDone.countDown();
                }
            });

            // 2. Evento real confirmado: o Scheduler já detém o lock.
            assertThat(lockProbe.awaitLocked(10))
                    .as("scheduler deve adquirir o lock da Order primeiro")
                    .isTrue();

            // 3. Checkout disputa o mesmo lock.
            executor.submit(() -> {
                try {
                    checkoutStatus.set(performCheckoutStatus(token, orderId, cardId));
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            });

            // 4. Observação no PostgreSQL: o checkout está bloqueado aguardando o lock.
            awaitLockWaiter();

            // 5. Scheduler expira, libera a reserva e comita.
            lockProbe.release();
            assertThat(schedulerDone.await(10, TimeUnit.SECONDS)).isTrue();
        } finally {
            executor.shutdown();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }

        assertThat(checkoutStatus.get())
                .as("checkout só prossegue após o commit do scheduler e observa EXPIRED")
                .isEqualTo(422);

        Order finalOrder = orderRepository.findById(orderId).orElseThrow();
        assertThat(finalOrder.getStatus()).isEqualTo(OrderStatus.EXPIRED);
        assertThat(finalOrder.getPaidAt()).isNull();

        TicketLot finalLot = ticketLotRepository.findById(lot.getId()).orElseThrow();
        assertThat(finalLot.getReserved()).isZero();
        assertThat(finalLot.getSold()).isZero();
        assertThat(ticketRepository.findByOrderId(orderId)).isEmpty();
    }

    @Test
    void checkoutsParalelosPedidosDiferentesMesmoLoteDevemSerAtomicos() throws Exception {
        User organizer = createOrganizer();
        TicketLot lot = createActiveLot(organizer, 10, new BigDecimal("50.00"));

        // Create two orders for different buyers
        User buyer1 = createBuyer("buyer1@test.com");
        String token1 = obtainToken(buyer1.getEmail());
        UUID cardId1 = createCard(token1);
        UUID orderId1 = createOrderAndGetId(token1, lot.getId(), 3);

        User buyer2 = createBuyer("buyer2@test.com");
        String token2 = obtainToken(buyer2.getEmail());
        UUID cardId2 = createCard(token2);
        UUID orderId2 = createOrderAndGetId(token2, lot.getId(), 4);

        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        // Thread 1: Checkout order 1
        executor.submit(() -> {
            try {
                startLatch.await();
                var request = post("/api/orders/{orderId}/checkout", orderId1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token1)
                        .content(checkoutBody(cardId1));

                var result = mockMvc.perform(request);
                int status = result.andReturn().getResponse().getStatus();
                String responseBody = result.andReturn().getResponse().getContentAsString();
                System.out.println("Thread 1 (order 1): status=" + status + ", body=" + responseBody);

                if (status == 200) {
                    successCount.incrementAndGet();
                } else {
                    errorCount.incrementAndGet();
                }
            } catch (Exception e) {
                errorCount.incrementAndGet();
                System.out.println("Thread 1 exception: " + e.getMessage());
                e.printStackTrace();
            } finally {
                endLatch.countDown();
            }
        });

        // Thread 2: Checkout order 2
        executor.submit(() -> {
            try {
                startLatch.await();
                var request = post("/api/orders/{orderId}/checkout", orderId2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token2)
                        .content(checkoutBody(cardId2));

                var result = mockMvc.perform(request);
                int status = result.andReturn().getResponse().getStatus();
                String responseBody = result.andReturn().getResponse().getContentAsString();
                System.out.println("Thread 2 (order 2): status=" + status + ", body=" + responseBody);

                if (status == 200) {
                    successCount.incrementAndGet();
                } else {
                    errorCount.incrementAndGet();
                }
            } catch (Exception e) {
                errorCount.incrementAndGet();
                System.out.println("Thread 2 exception: " + e.getMessage());
                e.printStackTrace();
            } finally {
                endLatch.countDown();
            }
        });

        startLatch.countDown();
        endLatch.await();
        executor.shutdown();

        // Both checkouts should succeed
        assertThat(successCount.get()).isEqualTo(2);
        assertThat(errorCount.get()).isEqualTo(0);

        // Verify final state
        Order finalOrder1 = orderRepository.findById(orderId1).orElseThrow();
        Order finalOrder2 = orderRepository.findById(orderId2).orElseThrow();
        assertThat(finalOrder1.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(finalOrder2.getStatus()).isEqualTo(OrderStatus.PAID);

        List<Ticket> tickets1 = ticketRepository.findByOrderId(orderId1);
        List<Ticket> tickets2 = ticketRepository.findByOrderId(orderId2);
        assertThat(tickets1).hasSize(3);
        assertThat(tickets2).hasSize(4);

        TicketLot finalLot = ticketLotRepository.findById(lot.getId()).orElseThrow();
        assertThat(finalLot.getReserved()).isEqualTo(0); // 7 - 7 = 0
        assertThat(finalLot.getSold()).isEqualTo(7); // 0 + 7 = 7
    }

    @Test
    void checkoutAposExpiracaoDeveRetornar422SemAlterarEstado() throws Exception {
        User organizer = createOrganizer();
        TicketLot lot = createActiveLot(organizer, 5, new BigDecimal("50.00"));

        User buyer = createBuyer("buyer@test.com");
        String token = obtainToken(buyer.getEmail());

        UUID cardId = createCard(token);
        UUID orderId = createOrderAndGetId(token, lot.getId(), 2);

        // Expire the order first
        Order order = orderRepository.findById(orderId).orElseThrow();
        order.setExpiresAt(Instant.now().minusSeconds(10));
        orderRepository.save(order);

        expirationScheduler.processSingleExpiredOrder(order);

        // Verify expiration happened
        Order expiredOrder = orderRepository.findById(orderId).orElseThrow();
        assertThat(expiredOrder.getStatus()).isEqualTo(OrderStatus.EXPIRED);

        // Now try checkout - should return 422
        var request = post("/api/orders/{orderId}/checkout", orderId)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + token)
                .content(checkoutBody(cardId));

        mockMvc.perform(request)
                .andExpect(status().isUnprocessableEntity());

        // Verify state unchanged
        Order finalOrder = orderRepository.findById(orderId).orElseThrow();
        assertThat(finalOrder.getStatus()).isEqualTo(OrderStatus.EXPIRED);
        assertThat(finalOrder.getPaidAt()).isNull();

        TicketLot finalLot = ticketLotRepository.findById(lot.getId()).orElseThrow();
        assertThat(finalLot.getReserved()).isEqualTo(0); // Released on expiration
        assertThat(finalLot.getSold()).isEqualTo(0);

        assertThat(ticketRepository.findByOrderId(orderId)).isEmpty();
    }

    @Test
    void schedulerNaoDeveExpirarPedidoJaPago() throws Exception {
        User organizer = createOrganizer();
        TicketLot lot = createActiveLot(organizer, 5, new BigDecimal("50.00"));

        User buyer = createBuyer("buyer@test.com");
        String token = obtainToken(buyer.getEmail());

        UUID cardId = createCard(token);
        UUID orderId = createOrderAndGetId(token, lot.getId(), 2);

        // Checkout conclui primeiro: pedido vira PAID.
        mockMvc.perform(post("/api/orders/{orderId}/checkout", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(checkoutBody(cardId)))
                .andExpect(status().isOk());

        Order paidOrder = orderRepository.findById(orderId).orElseThrow();
        assertThat(paidOrder.getStatus()).isEqualTo(OrderStatus.PAID);

        // Mesmo com prazo já vencido, o scheduler deve sair no ramo status != PENDING (determinístico).
        paidOrder.setExpiresAt(Instant.now().minusSeconds(60));
        orderRepository.save(paidOrder);
        expirationScheduler.processSingleExpiredOrder(paidOrder);

        Order finalOrder = orderRepository.findById(orderId).orElseThrow();
        assertThat(finalOrder.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(finalOrder.getPaidAt()).isNotNull();

        TicketLot finalLot = ticketLotRepository.findById(lot.getId()).orElseThrow();
        assertThat(finalLot.getReserved()).isZero();
        assertThat(finalLot.getSold()).isEqualTo(2);
        assertThat(ticketRepository.findByOrderId(orderId)).hasSize(2);
    }
}