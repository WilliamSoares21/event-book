package com.eventbook.backend.checkin;

import com.eventbook.backend.checkin.dto.CheckInResponseDTO;
import com.eventbook.backend.checkin.exception.TicketAlreadyCheckedInException;
import com.eventbook.backend.events.Event;
import com.eventbook.backend.events.EventRepository;
import com.eventbook.backend.events.EventStatus;
import com.eventbook.backend.lots.LotStatus;
import com.eventbook.backend.lots.TicketLot;
import com.eventbook.backend.lots.TicketLotRepository;
import com.eventbook.backend.orders.Order;
import com.eventbook.backend.orders.OrderRepository;
import com.eventbook.backend.orders.OrderStatus;
import com.eventbook.backend.staff.EventStaff;
import com.eventbook.backend.staff.EventStaffRepository;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
class CheckinConcurrencyTest {

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
    private CheckInRepository checkInRepository;

    @Autowired
    private EventStaffRepository eventStaffRepository;

    @Autowired
    private CheckinService checkinService;

    private User organizer;
    private User staff1;
    private User staff2;
    private User buyer;
    private Event event;
    private TicketLot lot;
    private Order order;
    private Ticket ticket;

    private static final String COMMON_PASSWORD = "password123";

    @BeforeEach
    void setup() {
        checkInRepository.deleteAll();
        eventStaffRepository.deleteAll();
        ticketRepository.deleteAll();
        orderRepository.deleteAll();
        ticketLotRepository.deleteAll();
        eventRepository.deleteAll();
        userRepository.deleteAll();

        organizer = createUser("organizer@test.com", Role.ORGANIZADOR);
        staff1 = createUser("staff1@test.com", Role.STAFF);
        staff2 = createUser("staff2@test.com", Role.STAFF);
        buyer = createUser("buyer@test.com", Role.COMPRADOR);

        event = createPublishedEvent(organizer);
        associateStaffWithEvent(event, staff1);
        associateStaffWithEvent(event, staff2);

        lot = createActiveLot(event);
        order = createPaidOrder(buyer, lot);
        ticket = createValidTicket(order);
    }

    private User createUser(String email, Role role) {
        User user = User.builder()
                .name("Test " + role)
                .email(email)
                .passwordHash(passwordEncoder.encode(COMMON_PASSWORD))
                .role(role)
                .build();
        return userRepository.save(user);
    }

    private Event createPublishedEvent(User organizer) {
        Event event = Event.builder()
                .title("Test Event")
                .description("Test Description")
                .startsAt(OffsetDateTime.now(ZoneOffset.UTC).plusHours(1))
                .endsAt(OffsetDateTime.now(ZoneOffset.UTC).plusHours(5))
                .location("Test Location")
                .organizer(organizer)
                .status(EventStatus.PUBLISHED)
                .build();
        return eventRepository.save(event);
    }

    private void associateStaffWithEvent(Event event, User staff) {
        EventStaff eventStaff = EventStaff.builder()
                .event(event)
                .user(staff)
                .build();
        eventStaffRepository.save(eventStaff);
    }

    private TicketLot createActiveLot(Event event) {
        TicketLot lot = TicketLot.builder()
                .event(event)
                .name("Lote 1")
                .unitPrice(new BigDecimal("50.00"))
                .capacity(100)
                .sold(0)
                .reserved(0)
                .salesStart(OffsetDateTime.now(ZoneOffset.UTC).minusDays(1).toInstant())
                .salesEnd(OffsetDateTime.now(ZoneOffset.UTC).plusDays(30).toInstant())
                .status(LotStatus.ACTIVE)
                .build();
        return ticketLotRepository.save(lot);
    }

    private Order createPaidOrder(User buyer, TicketLot lot) {
        Order order = Order.builder()
                .buyer(buyer)
                .ticketLot(lot)
                .quantity(1)
                .unitPriceSnapshot(new BigDecimal("50.00"))
                .status(OrderStatus.PAID)
                .createdAt(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(30).toInstant())
                .paidAt(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(15).toInstant())
                .build();
        return orderRepository.save(order);
    }

    private Ticket createValidTicket(Order order) {
        Ticket ticket = Ticket.builder()
                .order(order)
                .uniqueCode("CONCURRENT_TICKET_123")
                .status(TicketStatus.VALID)
                .build();
        return ticketRepository.save(ticket);
    }

    @Test
    void checkInConcorrenteDevePermitirApenasUmSucesso() throws InterruptedException {
        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        AtomicReference<Instant> firstCheckInTime = new AtomicReference<>();
        AtomicReference<UUID> firstStaffId = new AtomicReference<>();

        for (int i = 0; i < threadCount; i++) {
            final UUID staffId = (i == 0) ? staff1.getId() : staff2.getId();
            
            executor.submit(() -> {
                try {
                    // Wait for all threads to be ready
                    startLatch.await();
                    
                    // Attempt check-in
                    CheckInResponseDTO response = checkinService.checkIn("CONCURRENT_TICKET_123", staffId);
                    
                    // Success
                    int currentSuccess = successCount.incrementAndGet();
                    if (currentSuccess == 1) {
                        firstCheckInTime.set(response.getCheckedInAt());
                        firstStaffId.set(response.getStaffId());
                    }
                } catch (TicketAlreadyCheckedInException e) {
                    conflictCount.incrementAndGet();
                    // Verify the conflict contains original data
                    assertThat(e.getExistingCheckIn()).isNotNull();
                    assertThat(e.getExistingCheckIn().getTicket().getUniqueCode()).isEqualTo("CONCURRENT_TICKET_123");
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                    e.printStackTrace();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        // Start all threads simultaneously
        startLatch.countDown();
        
        // Wait for all threads to complete
        boolean completed = endLatch.await(30, TimeUnit.SECONDS);
        assertThat(completed).isTrue();
        
        executor.shutdown();
        
        // Verify results
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(conflictCount.get()).isEqualTo(1);
        assertThat(errorCount.get()).isEqualTo(0);
        
        // Verify only one check-in record exists
        assertThat(checkInRepository.count()).isEqualTo(1);
        
        // Verify ticket status is CHECKED_IN
        Ticket updatedTicket = ticketRepository.findById(ticket.getId()).orElseThrow();
        assertThat(updatedTicket.getStatus()).isEqualTo(TicketStatus.CHECKED_IN);
        
        // Verify the check-in data matches the first successful request
        CheckIn checkIn = checkInRepository.findByTicketId(ticket.getId()).orElseThrow();
        // Compare truncated to seconds since DB has microsecond precision
        assertThat(checkIn.getCheckedInAt().truncatedTo(java.time.temporal.ChronoUnit.SECONDS))
                .isEqualTo(firstCheckInTime.get().truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
        assertThat(checkIn.getStaff().getId()).isEqualTo(firstStaffId.get());
    }
}