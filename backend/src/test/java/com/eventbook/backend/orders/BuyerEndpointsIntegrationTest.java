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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class BuyerEndpointsIntegrationTest {

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
    private com.eventbook.backend.payments.PaymentTransactionRepository paymentTransactionRepository;

    @Autowired
    private com.eventbook.backend.cards.SimulatedCardRepository simulatedCardRepository;

    private String commonPassword = "password123";

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

    private String createOrderRequestBody(UUID lotId, int quantity) {
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
                        .content(createOrderRequestBody(lotId, quantity)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private void performCheckout(String token, UUID orderId, UUID cardId) throws Exception {
        String requestBody = """
            {
                "cardId": "%s"
            }
            """.formatted(cardId);
        mockMvc.perform(post("/api/orders/" + orderId + "/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(requestBody))
                .andExpect(status().isOk());
    }

    private UUID createCardForBuyer(String email) throws Exception {
        String token = obtainToken(email);
        String cardBody = """
            {
                "name": "Cartão Teste",
                "lastFourDigits": "1234",
                "expiryMonth": 12,
                "expiryYear": 2030,
                "availableLimit": 1000.00
            }
            """;
        var result = mockMvc.perform(post("/api/cards")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + token)
                        .content(cardBody))
                .andExpect(status().isCreated())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        return UUID.fromString(body.split("\"id\":\"")[1].split("\"")[0]);
    }

    // ========== GET /api/tickets/me ==========

    @Test
    void deveRetornarIngressosDoCompradorAutenticado() throws Exception {
        User organizer = createOrganizer();
        User buyer = createBuyer("buyer@test.com");
        TicketLot lot = createActiveLot(organizer, 100, new BigDecimal("50.00"));
        String token = obtainToken(buyer.getEmail());
        UUID cardId = createCardForBuyer(buyer.getEmail());

        // Criar pedido
        String orderResponse = performCreateOrder(token, lot.getId(), 2);
        UUID orderId = UUID.fromString(orderResponse.split("\"id\":\"")[1].split("\"")[0]);

        // Checkout para emitir tickets
        performCheckout(token, orderId, cardId);

        // Consultar tickets
        mockMvc.perform(get("/api/tickets/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].status").value("VALID"))
                .andExpect(jsonPath("$[0].eventTitle").value("Test Event"))
                .andExpect(jsonPath("$[0].uniqueCode").exists());
    }

    @Test
    void deveRetornarListaVaziaQuandoCompradorNaoTemIngressos() throws Exception {
        User buyer = createBuyer("buyer2@test.com");
        String token = obtainToken(buyer.getEmail());

        mockMvc.perform(get("/api/tickets/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void naoDeveRetornarIngressosDeOutroComprador() throws Exception {
        User organizer = createOrganizer();
        User buyer1 = createBuyer("buyer1@test.com");
        User buyer2 = createBuyer("buyer2@test.com");
        TicketLot lot = createActiveLot(organizer, 100, new BigDecimal("50.00"));
        String token1 = obtainToken(buyer1.getEmail());
        String token2 = obtainToken(buyer2.getEmail());
        UUID cardId1 = createCardForBuyer(buyer1.getEmail());

        // Buyer1 compra
        String orderResponse = performCreateOrder(token1, lot.getId(), 2);
        UUID orderId = UUID.fromString(orderResponse.split("\"id\":\"")[1].split("\"")[0]);
        performCheckout(token1, orderId, cardId1);

        // Buyer2 consulta seus tickets - deve ser vazio
        mockMvc.perform(get("/api/tickets/me")
                        .header("Authorization", "Bearer " + token2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void deveRetornarStatusCorretosDosIngressos() throws Exception {
        User organizer = createOrganizer();
        User buyer = createBuyer("buyer3@test.com");
        TicketLot lot = createActiveLot(organizer, 100, new BigDecimal("50.00"));
        String token = obtainToken(buyer.getEmail());
        UUID cardId = createCardForBuyer(buyer.getEmail());

        String orderResponse = performCreateOrder(token, lot.getId(), 3);
        UUID orderId = UUID.fromString(orderResponse.split("\"id\":\"")[1].split("\"")[0]);
        performCheckout(token, orderId, cardId);

        mockMvc.perform(get("/api/tickets/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[*].status").value(everyItem(is("VALID"))));
    }

    @Test
    void deveRetornar401SemAutenticacao() throws Exception {
        mockMvc.perform(get("/api/tickets/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deveRetornar403ParaOrganizador() throws Exception {
        User organizer = createOrganizer();
        String token = obtainToken(organizer.getEmail());

        mockMvc.perform(get("/api/tickets/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    // ========== GET /api/orders/me ==========

    @Test
    void deveRetornarPedidosDoCompradorAutenticado() throws Exception {
        User organizer = createOrganizer();
        User buyer = createBuyer("buyer4@test.com");
        TicketLot lot = createActiveLot(organizer, 100, new BigDecimal("50.00"));
        String token = obtainToken(buyer.getEmail());

        // Criar 2 pedidos
        performCreateOrder(token, lot.getId(), 1);
        performCreateOrder(token, lot.getId(), 2);

        mockMvc.perform(get("/api/orders/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].status").value("PENDING"))
                .andExpect(jsonPath("$[*].quantity").value(everyItem(greaterThan(0))));
    }

    @Test
    void deveRetornarListaVaziaQuandoCompradorNaoTemPedidos() throws Exception {
        User buyer = createBuyer("buyer5@test.com");
        String token = obtainToken(buyer.getEmail());

        mockMvc.perform(get("/api/orders/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void naoDeveRetornarPedidosDeOutroComprador() throws Exception {
        User organizer = createOrganizer();
        User buyer1 = createBuyer("buyer1@test.com");
        User buyer2 = createBuyer("buyer2@test.com");
        TicketLot lot = createActiveLot(organizer, 100, new BigDecimal("50.00"));
        String token1 = obtainToken(buyer1.getEmail());
        String token2 = obtainToken(buyer2.getEmail());

        // Buyer1 cria pedido
        performCreateOrder(token1, lot.getId(), 1);

        // Buyer2 consulta seus pedidos - deve ser vazio
        mockMvc.perform(get("/api/orders/me")
                        .header("Authorization", "Bearer " + token2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void deveRetornarPedidosComDiferentesStatus() throws Exception {
        User organizer = createOrganizer();
        User buyer = createBuyer("buyer6@test.com");
        TicketLot lot = createActiveLot(organizer, 100, new BigDecimal("50.00"));
        String token = obtainToken(buyer.getEmail());
        UUID cardId = createCardForBuyer(buyer.getEmail());

        // Pedido 1 - PENDING
        performCreateOrder(token, lot.getId(), 1);

        // Pedido 2 - PAID
        String orderResponse = performCreateOrder(token, lot.getId(), 1);
        UUID orderId = UUID.fromString(orderResponse.split("\"id\":\"")[1].split("\"")[0]);
        performCheckout(token, orderId, cardId);

        mockMvc.perform(get("/api/orders/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].status").value(containsInAnyOrder("PENDING", "PAID")));
    }

    @Test
    void deveRetornar401SemAutenticacaoParaPedidos() throws Exception {
        mockMvc.perform(get("/api/orders/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deveRetornar403ParaOrganizadorParaPedidos() throws Exception {
        User organizer = createOrganizer();
        String token = obtainToken(organizer.getEmail());

        mockMvc.perform(get("/api/orders/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }
}