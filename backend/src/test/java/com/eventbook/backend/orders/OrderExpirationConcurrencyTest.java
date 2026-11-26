package com.eventbook.backend.orders;

import com.eventbook.backend.events.Event;
import com.eventbook.backend.events.EventRepository;
import com.eventbook.backend.events.EventStatus;
import com.eventbook.backend.lots.LotStatus;
import com.eventbook.backend.lots.TicketLot;
import com.eventbook.backend.lots.TicketLotRepository;
import com.eventbook.backend.users.Role;
import com.eventbook.backend.users.User;
import com.eventbook.backend.users.UserRepository;
import com.eventbook.backend.users.UserService;
import com.eventbook.backend.users.dto.RegisterUserRequest;
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
class OrderExpirationConcurrencyTest {

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
    private OrderExpirationScheduler expirationScheduler;

    private String commonPassword = "password123";

    @BeforeEach
    void setup() {
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

    @Test
    void deveProtegerUltimaUnidadeContraConcorrenciaExpiracao() throws Exception {
        User organizer = createOrganizer();
        TicketLot lot = createActiveLot(organizer, 1, new BigDecimal("50.00"));

        int threadCount = 10;
        List<String> tokens = java.util.stream.IntStream.range(0, threadCount)
                .mapToObj(i -> {
                    try {
                        User buyer = createBuyer("buyer" + i + "@test.com");
                        return obtainToken(buyer.getEmail());
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .toList();

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final String token = tokens.get(i);
            executor.submit(() -> {
                try {
                    startLatch.await();

                    var request = post("/api/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("Authorization", "Bearer " + token)
                            .content(createRequestBody(lot.getId(), 1));

                    var result = mockMvc.perform(request);
                    int status = result.andReturn().getResponse().getStatus();

                    if (status == 201) {
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

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(conflictCount.get()).isEqualTo(threadCount - 1);
        assertThat(errorCount.get()).isEqualTo(0);

        TicketLot finalLot = ticketLotRepository.findById(lot.getId()).orElseThrow();
        assertThat(finalLot.getReserved()).isEqualTo(1);
        assertThat(finalLot.getSold()).isEqualTo(0);

        var orders = orderRepository.findAll();
        long pendingOrders = orders.stream().filter(o -> o.getStatus() == OrderStatus.PENDING).count();
        assertThat(pendingOrders).isEqualTo(1);
    }

    @Test
    void deveProcessarExpiracaoConcorrenteSemLiberarDuplicada() throws Exception {
        User organizer = createOrganizer();
        TicketLot lot = createActiveLot(organizer, 1, new BigDecimal("50.00"));

        User buyer = createBuyer("buyer@test.com");
        String token = obtainToken(buyer.getEmail());
        performCreateOrder(token, lot.getId(), 1);

        assertThat(ticketLotRepository.findById(lot.getId()).orElseThrow().getReserved()).isEqualTo(1);

        Order order = orderRepository.findAll().get(0);
        order.setExpiresAt(Instant.now().minusSeconds(10));
        orderRepository.save(order);

        ExecutorService executor = Executors.newFixedThreadPool(5);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(5);
        AtomicInteger errorCount = new AtomicInteger(0);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < 5; i++) {
            final int threadIdx = i;
            final Order currentOrder = order;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    // Call the scheduler's single order expiration method directly
                    System.out.println("Thread " + threadIdx + " calling processSingleExpiredOrder for order " + currentOrder.getId());
                    expirationScheduler.processSingleExpiredOrder(currentOrder);
                    System.out.println("Thread " + threadIdx + " SUCCESS");
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    System.out.println("Thread " + threadIdx + " ERROR: " + e.getMessage());
                    e.printStackTrace();
                    errorCount.incrementAndGet();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        endLatch.await();
        executor.shutdown();

        assertThat(errorCount.get()).isEqualTo(0);
        TicketLot finalLot = ticketLotRepository.findById(lot.getId()).orElseThrow();
        assertThat(finalLot.getReserved()).isEqualTo(0);
        Order finalOrder = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(finalOrder.getStatus()).isEqualTo(OrderStatus.EXPIRED);
    }

    private String performCreateOrder(String token, UUID lotId, int quantity) throws Exception {
        return mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(createRequestBody(lotId, quantity)))
                .andReturn()
                .getResponse()
                .getContentAsString();
    }
}