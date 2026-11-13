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
class OrderConcurrencyTest {

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
    void deveProtegerUltimaUnidadeContraConcorrencia() throws Exception {
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
    void deveProtegerMultiplasUnidadesContraConcorrencia() throws Exception {
        User organizer = createOrganizer();
        TicketLot lot = createActiveLot(organizer, 5, new BigDecimal("50.00"));

        int threadCount = 10;
        int quantityPerRequest = 1;
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
                            .content(createRequestBody(lot.getId(), quantityPerRequest));

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

        assertThat(successCount.get()).isEqualTo(5);
        assertThat(conflictCount.get()).isEqualTo(threadCount - 5);
        assertThat(errorCount.get()).isEqualTo(0);

        TicketLot finalLot = ticketLotRepository.findById(lot.getId()).orElseThrow();
        assertThat(finalLot.getReserved()).isEqualTo(5);
        assertThat(finalLot.getSold()).isEqualTo(0);

        var orders = orderRepository.findAll();
        long pendingOrders = orders.stream().filter(o -> o.getStatus() == OrderStatus.PENDING).count();
        assertThat(pendingOrders).isEqualTo(5);
    }
}