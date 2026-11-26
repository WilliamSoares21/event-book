package com.eventbook.backend.checkin;

import com.eventbook.backend.checkin.dto.CheckInConflictResponseDTO;
import com.eventbook.backend.checkin.dto.CheckInResponseDTO;
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
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class CheckinIntegrationTest {

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
    private CheckInRepository checkInRepository;

    @Autowired
    private EventStaffRepository eventStaffRepository;

    private String commonPassword = "password123";
    private User organizer;
    private User staff;
    private User buyer;
    private User otherStaff;
    private Event event;
    private TicketLot lot;
    private Order order;
    private Ticket ticket;

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
        staff = createUser("staff@test.com", Role.STAFF);
        buyer = createUser("buyer@test.com", Role.COMPRADOR);
        otherStaff = createUser("otherstaff@test.com", Role.STAFF);

        event = createPublishedEvent(organizer);
        associateStaffWithEvent(event, staff);

        lot = createActiveLot(event);
        order = createPaidOrder(buyer, lot);
        ticket = createValidTicket(order);
    }

    private User createUser(String email, Role role) {
        User user = User.builder()
                .name("Test " + role)
                .email(email)
                .passwordHash(passwordEncoder.encode(commonPassword))
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
        Instant now = Instant.now();
        TicketLot lot = TicketLot.builder()
                .event(event)
                .name("Lote 1")
                .unitPrice(new BigDecimal("50.00"))
                .capacity(100)
                .sold(0)
                .reserved(0)
                .salesStart(now.minusSeconds(86400)) // minus 1 day
                .salesEnd(now.plusSeconds(2592000)) // plus 30 days
                .status(LotStatus.ACTIVE)
                .build();
        return ticketLotRepository.save(lot);
    }

    private Order createPaidOrder(User buyer, TicketLot lot) {
        Instant now = Instant.now();
        Order order = Order.builder()
                .buyer(buyer)
                .ticketLot(lot)
                .quantity(1)
                .unitPriceSnapshot(new BigDecimal("50.00"))
                .status(OrderStatus.PAID)
                .createdAt(now.minusSeconds(1800)) // minus 30 minutes
                .paidAt(now.minusSeconds(900)) // minus 15 minutes
                .build();
        return orderRepository.save(order);
    }

    private Ticket createValidTicket(Order order) {
        Ticket ticket = Ticket.builder()
                .order(order)
                .uniqueCode("VALID_TICKET_123")
                .status(TicketStatus.VALID)
                .build();
        return ticketRepository.save(ticket);
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

        String responseBody = result.getResponse().getContentAsString();
        return responseBody.split("\"token\":\"")[1].split("\"")[0];
    }

    @Test
    void checkInValidoDeveRetornar200() throws Exception {
        String token = obtainToken(staff.getEmail());

        mockMvc.perform(post("/api/tickets/{code}/check-in", "VALID_TICKET_123")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticketCode").value("VALID_TICKET_123"))
                .andExpect(jsonPath("$.staffId").value(staff.getId().toString()))
                .andExpect(jsonPath("$.checkedInAt").exists());

        // Verify ticket status updated
        Ticket updated = ticketRepository.findById(ticket.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(TicketStatus.CHECKED_IN);

        // Verify check-in persisted
        var checkIn = checkInRepository.findByTicketId(ticket.getId()).orElseThrow();
        assertThat(checkIn.getStaff().getId()).isEqualTo(staff.getId());
    }

    @Test
    void ticketInexistenteDeveRetornar404() throws Exception {
        String token = obtainToken(staff.getEmail());

        mockMvc.perform(post("/api/tickets/{code}/check-in", "NONEXISTENT")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Ticket não encontrado"));
    }

    @Test
    void codigoVazioDeveRetornar400() throws Exception {
        String token = obtainToken(staff.getEmail());

        mockMvc.perform(post("/api/tickets/{code}/check-in", "")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void codigoMuitoLongoDeveRetornar400() throws Exception {
        String token = obtainToken(staff.getEmail());
        String longCode = "A".repeat(65);

        mockMvc.perform(post("/api/tickets/{code}/check-in", longCode)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void pedidoNaoPagoDeveRetornar422() throws Exception {
        Instant now = Instant.now();
        Order pendingOrder = Order.builder()
                .buyer(buyer)
                .ticketLot(lot)
                .quantity(1)
                .unitPriceSnapshot(new BigDecimal("50.00"))
                .status(OrderStatus.PENDING)
                .createdAt(now.minusSeconds(1800)) // minus 30 minutes
                .build();
        pendingOrder = orderRepository.save(pendingOrder);

        Ticket pendingTicket = Ticket.builder()
                .order(pendingOrder)
                .uniqueCode("PENDING_TICKET")
                .status(TicketStatus.VALID)
                .build();
        ticketRepository.save(pendingTicket);

        String token = obtainToken(staff.getEmail());

        mockMvc.perform(post("/api/tickets/{code}/check-in", "PENDING_TICKET")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("Pedido não está pago"));
    }

    @Test
    void staffNaoAssociadoAoEventoDeveRetornar403() throws Exception {
        String token = obtainToken(otherStaff.getEmail());

        mockMvc.perform(post("/api/tickets/{code}/check-in", "VALID_TICKET_123")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Staff não autorizado para este evento"));
    }

    @Test
    void compradorDeveRetornar403() throws Exception {
        String token = obtainToken(buyer.getEmail());

        mockMvc.perform(post("/api/tickets/{code}/check-in", "VALID_TICKET_123")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void organizadorDeveRetornar403() throws Exception {
        String token = obtainToken(organizer.getEmail());

        mockMvc.perform(post("/api/tickets/{code}/check-in", "VALID_TICKET_123")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void semTokenDeveRetornar401() throws Exception {
        mockMvc.perform(post("/api/tickets/{code}/check-in", "VALID_TICKET_123"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ticketCanceladoDeveRetornar422() throws Exception {
        Ticket cancelledTicket = Ticket.builder()
                .order(order)
                .uniqueCode("CANCELLED_TICKET")
                .status(TicketStatus.CANCELLED)
                .build();
        ticketRepository.save(cancelledTicket);

        String token = obtainToken(staff.getEmail());

        mockMvc.perform(post("/api/tickets/{code}/check-in", "CANCELLED_TICKET")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("Ticket não está em status válido para check-in"));
    }

    @Test
    void ticketJaCheckedInDeveRetornar409ComDadosOriginais() throws Exception {
        String token = obtainToken(staff.getEmail());

        // First check-in
        mockMvc.perform(post("/api/tickets/{code}/check-in", "VALID_TICKET_123")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // Second attempt - should return 409
        mockMvc.perform(post("/api/tickets/{code}/check-in", "VALID_TICKET_123")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.ticketCode").value("VALID_TICKET_123"))
                .andExpect(jsonPath("$.staffId").value(staff.getId().toString()))
                .andExpect(jsonPath("$.checkedInAt").exists());

        // Verify only one check-in exists
        assertThat(checkInRepository.count()).isEqualTo(1);
    }

    @Test
    void staffNaoAutorizadoEmTicketJaCheckedInDeveRetornar403SemVazarDados() throws Exception {
        String authorizedToken = obtainToken(staff.getEmail());

        // First: authorized staff checks in
        mockMvc.perform(post("/api/tickets/{code}/check-in", "VALID_TICKET_123")
                        .header("Authorization", "Bearer " + authorizedToken))
                .andExpect(status().isOk());

        // Second: unauthorized staff tries to check in same ticket
        // Should return 403, NOT 409 with check-in data
        String unauthorizedToken = obtainToken(otherStaff.getEmail());
        mockMvc.perform(post("/api/tickets/{code}/check-in", "VALID_TICKET_123")
                        .header("Authorization", "Bearer " + unauthorizedToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Staff não autorizado para este evento"))
                .andExpect(jsonPath("$.checkedInAt").doesNotExist())
                .andExpect(jsonPath("$.staffId").doesNotExist())
                .andExpect(jsonPath("$.ticketCode").doesNotExist());

        // Verify only one check-in exists
        assertThat(checkInRepository.count()).isEqualTo(1);
    }

    @Test
    void staffNaoAutorizadoEmTicketValidoDeveRetornar403() throws Exception {
        String unauthorizedToken = obtainToken(otherStaff.getEmail());

        // Unauthorized staff tries to check in valid ticket (not checked in yet)
        // Should return 403
        mockMvc.perform(post("/api/tickets/{code}/check-in", "VALID_TICKET_123")
                        .header("Authorization", "Bearer " + unauthorizedToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Staff não autorizado para este evento"));

        // Verify no check-in was created
        assertThat(checkInRepository.count()).isEqualTo(0);
    }
}