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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class OrderIntegrationTest {

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
                .title("Test Event")
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

    private String performCreateOrder(String token, UUID lotId, int quantity) throws Exception {
        return mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(createRequestBody(lotId, quantity)))
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private int performCreateOrderStatus(String token, UUID lotId, int quantity) throws Exception {
        return mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(createRequestBody(lotId, quantity)))
                .andReturn()
                .getResponse()
                .getStatus();
    }

    @Test
    void deveCriarPedidoEReservarIngressos() throws Exception {
        User organizer = createOrganizer();
        User buyer = createBuyer("buyer@test.com");
        TicketLot lot = createActiveLot(organizer, 100, new BigDecimal("50.00"));
        String token = obtainToken(buyer.getEmail());

        String responseBody = performCreateOrder(token, lot.getId(), 2);

        assertThat(responseBody).contains("\"quantity\":2");
        assertThat(responseBody).contains("\"unitPriceSnapshot\":50.00");
        assertThat(responseBody).contains("\"totalPrice\":100.00");
        assertThat(responseBody).contains("\"status\":\"PENDING\"");
        assertThat(responseBody).contains("expiresAt");

        TicketLot updatedLot = ticketLotRepository.findById(lot.getId()).orElseThrow();
        assertThat(updatedLot.getReserved()).isEqualTo(2);
        assertThat(updatedLot.getSold()).isEqualTo(0);
    }

    @Test
    void deveRetornar400ParaPayloadInvalido() throws Exception {
        User organizer = createOrganizer();
        User buyer = createBuyer("buyer@test.com");
        String token = obtainToken(buyer.getEmail());

        String requestBody = """
            {
                "ticketLotId": "invalid-uuid",
                "quantity": 2
            }
            """;

        int status = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(requestBody))
                .andReturn()
                .getResponse()
                .getStatus();

        assertThat(status).isEqualTo(400);
    }

    @Test
    void deveRetornar400ParaQuantidadeZero() throws Exception {
        User organizer = createOrganizer();
        User buyer = createBuyer("buyer@test.com");
        TicketLot lot = createActiveLot(organizer, 100, new BigDecimal("50.00"));
        String token = obtainToken(buyer.getEmail());

        int status = performCreateOrderStatus(token, lot.getId(), 0);
        assertThat(status).isEqualTo(400);
    }

    @Test
    void deveRetornar400ParaQuantidadeNegativa() throws Exception {
        User organizer = createOrganizer();
        User buyer = createBuyer("buyer@test.com");
        TicketLot lot = createActiveLot(organizer, 100, new BigDecimal("50.00"));
        String token = obtainToken(buyer.getEmail());

        int status = performCreateOrderStatus(token, lot.getId(), -1);
        assertThat(status).isEqualTo(400);
    }

    @Test
    void deveRetornar401SemAutenticacao() throws Exception {
        User organizer = createOrganizer();
        TicketLot lot = createActiveLot(organizer, 100, new BigDecimal("50.00"));

        int status = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestBody(lot.getId(), 2)))
                .andReturn()
                .getResponse()
                .getStatus();

        assertThat(status).isEqualTo(401);
    }

    @Test
    void deveRetornar403ParaPapelDiferenteDeComprador() throws Exception {
        User organizer = createOrganizer();
        TicketLot lot = createActiveLot(organizer, 100, new BigDecimal("50.00"));
        String token = obtainToken(organizer.getEmail()); // organizer token

        int status = performCreateOrderStatus(token, lot.getId(), 2);
        assertThat(status).isEqualTo(403);
    }

    @Test
    void deveRetornar404ParaLoteInexistente() throws Exception {
        User buyer = createBuyer("buyer@test.com");
        String token = obtainToken(buyer.getEmail());

        UUID nonExistentLotId = UUID.randomUUID();
        int status = performCreateOrderStatus(token, nonExistentLotId, 2);
        assertThat(status).isEqualTo(404);
    }

    @Test
    void deveRetornar409ParaEstoqueInsuficiente() throws Exception {
        User organizer = createOrganizer();
        User buyer = createBuyer("buyer@test.com");
        TicketLot lot = createActiveLot(organizer, 5, new BigDecimal("50.00"));
        String token = obtainToken(buyer.getEmail());

        int status = performCreateOrderStatus(token, lot.getId(), 10);
        assertThat(status).isEqualTo(409);
    }

    @Test
    void deveRetornar422ParaLoteInativo() throws Exception {
        User organizer = createOrganizer();
        User buyer = createBuyer("buyer@test.com");
        
        Event event = Event.builder()
                .title("Test Event")
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
                .name("Lote Inativo")
                .unitPrice(new BigDecimal("50.00"))
                .capacity(100)
                .salesStart(Instant.now().minus(1, ChronoUnit.HOURS))
                .salesEnd(Instant.now().plus(1, ChronoUnit.HOURS))
                .status(LotStatus.INACTIVE)
                .build();
        lot = ticketLotRepository.save(lot);

        String token = obtainToken(buyer.getEmail());

        int status = performCreateOrderStatus(token, lot.getId(), 2);
        assertThat(status).isEqualTo(422);
    }

    @Test
    void deveRetornar422ParaCompraForaDaJanela() throws Exception {
        User organizer = createOrganizer();
        User buyer = createBuyer("buyer@test.com");
        
        Event event = Event.builder()
                .title("Test Event")
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
                .unitPrice(new BigDecimal("50.00"))
                .capacity(100)
                .salesStart(Instant.now().plus(1, ChronoUnit.HOURS))
                .salesEnd(Instant.now().plus(2, ChronoUnit.HOURS))
                .status(LotStatus.ACTIVE)
                .build();
        lot = ticketLotRepository.save(lot);

        String token = obtainToken(buyer.getEmail());

        int status = performCreateOrderStatus(token, lot.getId(), 2);
        assertThat(status).isEqualTo(422);
    }

    @Test
    void deveCriarPedidoComExpiracaoDe15Minutos() throws Exception {
        User organizer = createOrganizer();
        User buyer = createBuyer("buyer@test.com");
        TicketLot lot = createActiveLot(organizer, 100, new BigDecimal("50.00"));
        String token = obtainToken(buyer.getEmail());

        String responseBody = performCreateOrder(token, lot.getId(), 2);

        assertThat(responseBody).contains("expiresAt");

        var orders = orderRepository.findAll();
        assertThat(orders).hasSize(1);
        Order order = orders.get(0);
        
        Instant now = Instant.now();
        assertThat(order.getExpiresAt()).isAfter(now.plusSeconds(14 * 60));
        assertThat(order.getExpiresAt()).isBefore(now.plusSeconds(16 * 60));
    }

    @Test
    void deveProcessarExpiracaoELiberarEstoque() throws Exception {
        User organizer = createOrganizer();
        User buyer = createBuyer("buyer@test.com");
        TicketLot lot = createActiveLot(organizer, 100, new BigDecimal("50.00"));
        String token = obtainToken(buyer.getEmail());

        performCreateOrder(token, lot.getId(), 3);

        assertThat(ticketLotRepository.findById(lot.getId()).orElseThrow().getReserved()).isEqualTo(3);

        var orders = orderRepository.findAll();
        assertThat(orders).hasSize(1);
        Order order = orders.get(0);

        order.setExpiresAt(Instant.now().minusSeconds(10));
        orderRepository.save(order);

        expirationScheduler.processExpiredOrders();

        Order expiredOrder = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(expiredOrder.getStatus()).isEqualTo(OrderStatus.EXPIRED);

        TicketLot updatedLot = ticketLotRepository.findById(lot.getId()).orElseThrow();
        assertThat(updatedLot.getReserved()).isEqualTo(0);
    }

    @Test
    void naoDeveProcessarPedidoNaoExpirado() throws Exception {
        User organizer = createOrganizer();
        User buyer = createBuyer("buyer@test.com");
        TicketLot lot = createActiveLot(organizer, 100, new BigDecimal("50.00"));
        String token = obtainToken(buyer.getEmail());

        performCreateOrder(token, lot.getId(), 3);

        expirationScheduler.processExpiredOrders();

        var orders = orderRepository.findAll();
        assertThat(orders).hasSize(1);
        Order order = orders.get(0);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);

        TicketLot updatedLot = ticketLotRepository.findById(lot.getId()).orElseThrow();
        assertThat(updatedLot.getReserved()).isEqualTo(3);
    }

    @Test
    void deveProcessarPedidoExpiradoApenasUmaVez() throws Exception {
        User organizer = createOrganizer();
        User buyer = createBuyer("buyer@test.com");
        TicketLot lot = createActiveLot(organizer, 100, new BigDecimal("50.00"));
        String token = obtainToken(buyer.getEmail());

        performCreateOrder(token, lot.getId(), 3);

        var orders = orderRepository.findAll();
        Order order = orders.get(0);
        order.setExpiresAt(Instant.now().minusSeconds(10));
        orderRepository.save(order);

        expirationScheduler.processExpiredOrders();
        expirationScheduler.processExpiredOrders();

        Order expiredOrder = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(expiredOrder.getStatus()).isEqualTo(OrderStatus.EXPIRED);

        TicketLot updatedLot = ticketLotRepository.findById(lot.getId()).orElseThrow();
        assertThat(updatedLot.getReserved()).isEqualTo(0);
    }
}