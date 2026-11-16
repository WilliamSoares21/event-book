package com.eventbook.backend.orders;

import com.eventbook.backend.events.Event;
import com.eventbook.backend.events.EventRepository;
import com.eventbook.backend.events.EventStatus;
import com.eventbook.backend.lots.LotStatus;
import com.eventbook.backend.lots.TicketLot;
import com.eventbook.backend.lots.TicketLotRepository;
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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
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
    private OrderExpirationScheduler expirationScheduler;

    private String commonPassword = "password123";

    @BeforeEach
    void setup() {
        ticketRepository.deleteAll();
        orderRepository.deleteAll();
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
                "ticketLotId": "%s",
                "quantity": %d
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
                            .content("{}");

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
    void checkoutDeveVencerExpiracaoQuandoGanhaLockPrimeiro() throws Exception {
        User organizer = createOrganizer();
        TicketLot lot = createActiveLot(organizer, 5, new BigDecimal("50.00"));

        User buyer = createBuyer("buyer@test.com");
        String token = obtainToken(buyer.getEmail());

        UUID orderId = createOrderAndGetId(token, lot.getId(), 1);

        // Set order to expire very soon
        Order order = orderRepository.findById(orderId).orElseThrow();
        order.setExpiresAt(Instant.now().minusSeconds(1)); // Already expired
        orderRepository.save(order);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(2);
        AtomicInteger checkoutSuccess = new AtomicInteger(0);
        AtomicInteger checkoutError = new AtomicInteger(0);
        AtomicInteger expirationSuccess = new AtomicInteger(0);
        AtomicInteger expirationError = new AtomicInteger(0);

        // Thread 1: Checkout
        executor.submit(() -> {
            try {
                startLatch.await();
                var request = post("/api/orders/{orderId}/checkout", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content("{}");

                var result = mockMvc.perform(request);
                int status = result.andReturn().getResponse().getStatus();

                if (status == 200) {
                    checkoutSuccess.incrementAndGet();
                } else {
                    checkoutError.incrementAndGet();
                }
            } catch (Exception e) {
                checkoutError.incrementAndGet();
            } finally {
                endLatch.countDown();
            }
        });

        // Thread 2: Expiration
        executor.submit(() -> {
            try {
                startLatch.await();
                expirationScheduler.processSingleExpiredOrder(order);
                expirationSuccess.incrementAndGet();
            } catch (Exception e) {
                expirationError.incrementAndGet();
            } finally {
                endLatch.countDown();
            }
        });

        startLatch.countDown();
        endLatch.await();
        executor.shutdown();

        // One of them should succeed, the other should fail gracefully
        // Since order is already expired, checkout should return 422
        // But if checkout gets the lock first, it might succeed before expiration runs
        int totalSuccess = checkoutSuccess.get() + expirationSuccess.get();
        int totalError = checkoutError.get() + expirationError.get();

        assertThat(totalSuccess).isEqualTo(1);
        assertThat(totalError).isEqualTo(1);

        Order finalOrder = orderRepository.findById(orderId).orElseThrow();
        // Final state should be either PAID (checkout won) or EXPIRED (expiration won)
        assertThat(finalOrder.getStatus()).isIn(OrderStatus.PAID, OrderStatus.EXPIRED);

        TicketLot finalLot = ticketLotRepository.findById(lot.getId()).orElseThrow();
        if (finalOrder.getStatus() == OrderStatus.PAID) {
            assertThat(finalLot.getReserved()).isEqualTo(0);
            assertThat(finalLot.getSold()).isEqualTo(1);
            assertThat(ticketRepository.findByOrderId(orderId)).hasSize(1);
        } else {
            assertThat(finalLot.getReserved()).isEqualTo(0);
            assertThat(finalLot.getSold()).isEqualTo(0);
            assertThat(ticketRepository.findByOrderId(orderId)).isEmpty();
        }
    }

    @Test
    void checkoutsParalelosPedidosDiferentesMesmoLoteDevemSerAtomicos() throws Exception {
        User organizer = createOrganizer();
        TicketLot lot = createActiveLot(organizer, 10, new BigDecimal("50.00"));

        // Create two orders for different buyers
        User buyer1 = createBuyer("buyer1@test.com");
        String token1 = obtainToken(buyer1.getEmail());
        UUID orderId1 = createOrderAndGetId(token1, lot.getId(), 3);

        User buyer2 = createBuyer("buyer2@test.com");
        String token2 = obtainToken(buyer2.getEmail());
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
                        .content("{}");

                var result = mockMvc.perform(request);
                int status = result.andReturn().getResponse().getStatus();

                if (status == 200) {
                    successCount.incrementAndGet();
                } else {
                    errorCount.incrementAndGet();
                }
            } catch (Exception e) {
                errorCount.incrementAndGet();
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
                        .content("{}");

                var result = mockMvc.perform(request);
                int status = result.andReturn().getResponse().getStatus();

                if (status == 200) {
                    successCount.incrementAndGet();
                } else {
                    errorCount.incrementAndGet();
                }
            } catch (Exception e) {
                errorCount.incrementAndGet();
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
                .content("{}");

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
}