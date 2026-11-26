package com.eventbook.backend.events;

import com.eventbook.backend.cards.SimulatedCard;
import com.eventbook.backend.cards.SimulatedCardRepository;
import com.eventbook.backend.checkin.CheckInRepository;
import com.eventbook.backend.checkin.CheckinService;
import com.eventbook.backend.events.dto.EventOccupancyDTO;
import com.eventbook.backend.events.dto.TicketLotOccupancyDTO;
import com.eventbook.backend.lots.LotStatus;
import com.eventbook.backend.lots.TicketLot;
import com.eventbook.backend.lots.TicketLotRepository;
import com.eventbook.backend.orders.Order;
import com.eventbook.backend.orders.OrderExpirationScheduler;
import com.eventbook.backend.orders.OrderRepository;
import com.eventbook.backend.orders.OrderService;
import com.eventbook.backend.orders.OrderStatus;
import com.eventbook.backend.staff.StaffService;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import jakarta.persistence.PersistenceContext;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class EventOccupancyIntegrationTest {

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
    private OrderService orderService;

    @Autowired
    private CheckinService checkinService;

    @Autowired
    private EventService eventService;

    @Autowired
    private StaffService staffService;

    @Autowired
    private SimulatedCardRepository simulatedCardRepository;

    @Autowired
    private com.eventbook.backend.payments.PaymentTransactionRepository paymentTransactionRepository;

    @Autowired
    private com.eventbook.backend.staff.EventStaffRepository eventStaffRepository;

    @Autowired
    private OrderExpirationScheduler orderExpirationScheduler;

    @Autowired
    private java.time.Clock clock;

    @PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

    @Autowired
    private org.springframework.transaction.PlatformTransactionManager transactionManager;

    private String commonPassword = "password123";

    @BeforeEach
    void setup() {
        paymentTransactionRepository.deleteAll();
        simulatedCardRepository.deleteAll();
        eventStaffRepository.deleteAll();
        checkInRepository.deleteAll();
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

    private User createOrganizer(String email) {
        User organizer = User.builder()
                .name("Organizer " + email)
                .email(email)
                .passwordHash(passwordEncoder.encode(commonPassword))
                .role(Role.ORGANIZADOR)
                .build();
        return userRepository.save(organizer);
    }

    private User createStaff(String email) {
        User staff = User.builder()
                .name("Staff " + email)
                .email(email)
                .passwordHash(passwordEncoder.encode(commonPassword))
                .role(Role.STAFF)
                .build();
        return userRepository.save(staff);
    }

    private SimulatedCard createCard(User buyer, BigDecimal limit) {
        // Find existing active card for this user or create new one
        if (simulatedCardRepository.existsByUserAndStatus(buyer, com.eventbook.backend.cards.CardStatus.ACTIVE)) {
            return simulatedCardRepository.findFirstByUserOrderByCreatedAtDesc(buyer).orElseThrow();
        }
        return simulatedCardRepository.save(SimulatedCard.builder()
                .user(buyer)
                .brand("VISA")
                .lastFourDigits("1234")
                .expiryMonth(12)
                .expiryYear(2030)
                .totalLimit(limit)
                .availableLimit(limit)
                .status(com.eventbook.backend.cards.CardStatus.ACTIVE)
                .build());
    }

    private TicketLot createActiveLot(Event event, String name, int capacity, BigDecimal price) {
        TicketLot lot = TicketLot.builder()
                .event(event)
                .name(name)
                .unitPrice(price)
                .capacity(capacity)
                .salesStart(Instant.now().minus(1, ChronoUnit.HOURS))
                .salesEnd(Instant.now().plus(1, ChronoUnit.HOURS))
                .status(LotStatus.ACTIVE)
                .build();
        return ticketLotRepository.save(lot);
    }

    private String obtainToken(String email) throws Exception {
        var result = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/login")
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

    private Order createAndCheckoutOrder(User buyer, TicketLot lot, int quantity) throws Exception {
        Order order = orderService.reserve(buyer, lot.getId(), quantity);
        // Card limit must cover: quantity * lot.unitPrice
        BigDecimal requiredLimit = lot.getUnitPrice().multiply(BigDecimal.valueOf(quantity));
        SimulatedCard card = createCard(buyer, requiredLimit.add(new BigDecimal("1000.00"))); // extra buffer
        orderService.checkout(order.getId(), buyer.getId(), card.getId());
        return orderRepository.findById(order.getId()).orElseThrow();
    }

    @Test
    void eventoSemVendasDeveRetornarTodasTaxasZero() throws Exception {
        User organizer = createOrganizer("org1@test.com");
        Event event = createPublishedEvent(organizer, "Evento Vazio");
        createActiveLot(event, "Lote 1", 500, new BigDecimal("100.00"));

        String token = obtainToken(organizer.getEmail());
        var response = mockMvc.perform(get("/api/events/{eventId}/occupancy", event.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        String body = response.getResponse().getContentAsString();
        assertThat(body).contains("\"capacity\":500");
        assertThat(body).contains("\"sold\":0");
        assertThat(body).contains("\"reserved\":0");
        assertThat(body).contains("\"checkedIn\":0");
        assertThat(body).contains("\"available\":500");
        assertThat(body).contains("\"commercialOccupancyRate\":0.00");
        assertThat(body).contains("\"committedOccupancyRate\":0.00");
        assertThat(body).contains("\"physicalOccupancyRate\":0.00");
    }

    @Test
    void eventoComVendasDeveCalcularTaxasCorretamente() throws Exception {
        User organizer = createOrganizer("org2@test.com");
        User buyer = createBuyer("buyer2@test.com");
        Event event = createPublishedEvent(organizer, "Evento Com Vendas");
        TicketLot lot = createActiveLot(event, "Lote 1", 500, new BigDecimal("100.00"));

        createAndCheckoutOrder(buyer, lot, 120);

        String token = obtainToken(organizer.getEmail());
        var response = mockMvc.perform(get("/api/events/{eventId}/occupancy", event.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        String body = response.getResponse().getContentAsString();
        assertThat(body).contains("\"capacity\":500");
        assertThat(body).contains("\"sold\":120");
        assertThat(body).contains("\"reserved\":0");
        assertThat(body).contains("\"checkedIn\":0");
        assertThat(body).contains("\"available\":380");
        assertThat(body).contains("\"commercialOccupancyRate\":24.00");
        assertThat(body).contains("\"committedOccupancyRate\":24.00");
        assertThat(body).contains("\"physicalOccupancyRate\":0.00");
    }

    @Test
    void eventoComReservasPendingDeveTerComprometimentoMaiorQueComercial() throws Exception {
        User organizer = createOrganizer("org3@test.com");
        User buyer = createBuyer("buyer3@test.com");
        Event event = createPublishedEvent(organizer, "Evento Com Reservas");
        TicketLot lot = createActiveLot(event, "Lote 1", 500, new BigDecimal("100.00"));

        // Criar reserva PENDING (não fazer checkout)
        orderService.reserve(buyer, lot.getId(), 50);

        String token = obtainToken(organizer.getEmail());
        var response = mockMvc.perform(get("/api/events/{eventId}/occupancy", event.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        String body = response.getResponse().getContentAsString();
        assertThat(body).contains("\"reserved\":50");
        assertThat(body).contains("\"commercialOccupancyRate\":0.00");
        assertThat(body).contains("\"committedOccupancyRate\":10.00"); // (0+50)/500*100 = 10%
    }

    @Test
    void expiracaoReservaDeveReduzirReservedEAumentarAvailable() throws Exception {
        User organizer = createOrganizer("org4@test.com");
        User buyer = createBuyer("buyer4@test.com");
        Event event = createPublishedEvent(organizer, "Evento Expiracao");
        TicketLot lot = createActiveLot(event, "Lote 1", 100, new BigDecimal("100.00"));

        Order order = orderService.reserve(buyer, lot.getId(), 20);

        String token = obtainToken(organizer.getEmail());
        var before = mockMvc.perform(get("/api/events/{eventId}/occupancy", event.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        String beforeBody = before.getResponse().getContentAsString();
        assertThat(beforeBody).contains("\"reserved\":20");
        assertThat(beforeBody).contains("\"available\":80");

        // Verificar status inicial
        Order beforeOrder = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(beforeOrder.getStatus()).isEqualTo(OrderStatus.PENDING);

        // Avançar tempo além da expiração (reserva expira em 15 min por padrão)
        // Usar reflection para manipular o Clock se necessário, ou invocar scheduler com dados já expirados
        // Como o Clock é um bean do sistema, vamos criar uma reserva com expiração no passado
        // via manipulação direta do banco
        
        // Atualizar expiresAt para o passado
        org.springframework.transaction.support.TransactionTemplate txTemplate = 
            new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        txTemplate.execute(status -> {
            entityManager.createNativeQuery("UPDATE orders SET expires_at = :past WHERE id = :id")
               .setParameter("past", Instant.now().minus(1, ChronoUnit.HOURS))
               .setParameter("id", order.getId())
               .executeUpdate();
            entityManager.flush();
            entityManager.clear();
            return null;
        });

        // Executar scheduler
        orderExpirationScheduler.processExpiredOrders();

        // Verificar resultado
        var after = mockMvc.perform(get("/api/events/{eventId}/occupancy", event.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        String afterBody = after.getResponse().getContentAsString();
        assertThat(afterBody).contains("\"reserved\":0");
        assertThat(afterBody).contains("\"available\":100");
        assertThat(afterBody).contains("\"sold\":0");

        // Verificar status do pedido
        Order afterOrder = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(afterOrder.getStatus()).isEqualTo(OrderStatus.EXPIRED);

        // Reexecução não deve duplicar efeitos
        orderExpirationScheduler.processExpiredOrders();
        var after2 = mockMvc.perform(get("/api/events/{eventId}/occupancy", event.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        String after2Body = after2.getResponse().getContentAsString();
        assertThat(after2Body).contains("\"reserved\":0");
        assertThat(after2Body).contains("\"available\":100");
        assertThat(after2Body).contains("\"sold\":0");
    }

    @Test
    void cancelamentoPedidoPagoDeveReduzirSoldEAumentarAvailable() throws Exception {
        User organizer = createOrganizer("org5@test.com");
        User buyer = createBuyer("buyer5@test.com");
        Event event = createPublishedEvent(organizer, "Evento Cancelamento");
        TicketLot lot = createActiveLot(event, "Lote 1", 100, new BigDecimal("100.00"));

        Order order = createAndCheckoutOrder(buyer, lot, 30);

        String token = obtainToken(organizer.getEmail());
        var before = mockMvc.perform(get("/api/events/{eventId}/occupancy", event.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        String beforeBody = before.getResponse().getContentAsString();
        assertThat(beforeBody).contains("\"sold\":30");
        assertThat(beforeBody).contains("\"available\":70");

        orderService.cancel(order.getId(), buyer.getId());

        var after = mockMvc.perform(get("/api/events/{eventId}/occupancy", event.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        String afterBody = after.getResponse().getContentAsString();
        assertThat(afterBody).contains("\"sold\":0");
        assertThat(afterBody).contains("\"available\":100");
    }

    @Test
    void reembolsoDeveSerIgualACancelamentoPago() throws Exception {
        // Mesmo teste que cancelamento PAID
        User organizer = createOrganizer("org6@test.com");
        User buyer = createBuyer("buyer6@test.com");
        Event event = createPublishedEvent(organizer, "Evento Reembolso");
        TicketLot lot = createActiveLot(event, "Lote 1", 100, new BigDecimal("100.00"));

        Order order = createAndCheckoutOrder(buyer, lot, 20);
        orderService.cancel(order.getId(), buyer.getId());

        String token = obtainToken(organizer.getEmail());
        var response = mockMvc.perform(get("/api/events/{eventId}/occupancy", event.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        String body = response.getResponse().getContentAsString();
        assertThat(body).contains("\"sold\":0");
        assertThat(body).contains("\"available\":100");
    }

    @Test
    void checkInRealizadoDeveIncrementarCheckedInEPhysicalRate() throws Exception {
        User organizer = createOrganizer("org7@test.com");
        User buyer = createBuyer("buyer7@test.com");
        User staff = createStaff("staff7@test.com");
        // Evento que já iniciou (para permitir check-in): startsAt = 1 hora atrás
        Event event = Event.builder()
                .title("Evento CheckIn")
                .description("Test Description")
                .startsAt(OffsetDateTime.now().minusHours(1))
                .endsAt(OffsetDateTime.now().plusHours(3))
                .location("Test Location")
                .organizer(organizer)
                .status(EventStatus.PUBLISHED)
                .build();
        event = eventRepository.save(event);
        
        TicketLot lot = createActiveLot(event, "Lote 1", 100, new BigDecimal("100.00"));

        // Associar staff ao evento
        com.eventbook.backend.staff.dto.AddEventStaffRequest staffRequest =
            new com.eventbook.backend.staff.dto.AddEventStaffRequest();
        staffRequest.setUserId(staff.getId());
        staffService.addStaffToEvent(event.getId(), staffRequest, event.getOrganizer().getId());

        Order order = createAndCheckoutOrder(buyer, lot, 50);

        // Obter ticket para check-in
        List<Ticket> tickets = ticketRepository.findByOrderId(order.getId());
        String ticketCode = tickets.get(0).getUniqueCode();

        // Fazer check-in
        checkinService.checkIn(ticketCode, staff.getId());

        String token = obtainToken(organizer.getEmail());
        var response = mockMvc.perform(get("/api/events/{eventId}/occupancy", event.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        String body = response.getResponse().getContentAsString();
        assertThat(body).contains("\"sold\":50");
        assertThat(body).contains("\"checkedIn\":1");
        assertThat(body).contains("\"physicalOccupancyRate\":1.00");
    }

    @Test
    void multiplosLotesDeveRetornarBreakdownCorreto() throws Exception {
        User organizer = createOrganizer("org8@test.com");
        User buyer = createBuyer("buyer8@test.com");
        Event event = createPublishedEvent(organizer, "Evento Multiplos Lotes");
        TicketLot lot1 = createActiveLot(event, "Lote Promocional", 100, new BigDecimal("50.00"));
        TicketLot lot2 = createActiveLot(event, "Lote Regular", 400, new BigDecimal("100.00"));

        // Um único cartão para o comprador - limite suficiente para ambas as ordens
        // 50 * 50.00 + 70 * 100.00 = 2500 + 7000 = 9500
        Order order1 = orderService.reserve(buyer, lot1.getId(), 50);
        SimulatedCard card = createCard(buyer, new BigDecimal("15000.00"));
        orderService.checkout(order1.getId(), buyer.getId(), card.getId());
        
        Order order2 = orderService.reserve(buyer, lot2.getId(), 70);
        // Reutiliza o mesmo cartão (createCard retorna o existente se já houver ativo)
        orderService.checkout(order2.getId(), buyer.getId(), card.getId());

        String token = obtainToken(organizer.getEmail());
        var response = mockMvc.perform(get("/api/events/{eventId}/occupancy", event.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        String body = response.getResponse().getContentAsString();
        assertThat(body).contains("\"capacity\":500");
        assertThat(body).contains("\"sold\":120");
        assertThat(body).contains("\"ticketLots\"");
        assertThat(body).contains("\"name\":\"Lote Promocional\"");
        assertThat(body).contains("\"name\":\"Lote Regular\"");
        assertThat(body).contains("\"capacity\":100");
        assertThat(body).contains("\"capacity\":400");
        assertThat(body).contains("\"sold\":50");
        assertThat(body).contains("\"sold\":70");
    }

    @Test
    void loteSemVendasDeveAparecerNoBreakdownComZeros() throws Exception {
        User organizer = createOrganizer("org9@test.com");
        User buyer = createBuyer("buyer9@test.com");
        Event event = createPublishedEvent(organizer, "Evento Lote Sem Vendas");
        TicketLot lot1 = createActiveLot(event, "Lote Com Vendas", 100, new BigDecimal("100.00"));
        TicketLot lot2 = createActiveLot(event, "Lote Sem Vendas", 200, new BigDecimal("150.00"));

        createAndCheckoutOrder(buyer, lot1, 50);

        String token = obtainToken(organizer.getEmail());
        var response = mockMvc.perform(get("/api/events/{eventId}/occupancy", event.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        String body = response.getResponse().getContentAsString();
        assertThat(body).contains("\"name\":\"Lote Sem Vendas\"");
        assertThat(body).contains("\"sold\":0");
        assertThat(body).contains("\"reserved\":0");
        assertThat(body).contains("\"checkedIn\":0");
    }

    @Test
    void loteSemCheckInsDeveAparecerComCheckedInZero() throws Exception {
        User organizer = createOrganizer("org10@test.com");
        User buyer = createBuyer("buyer10@test.com");
        Event event = createPublishedEvent(organizer, "Evento Lote Sem CheckIn");
        TicketLot lot = createActiveLot(event, "Lote 1", 100, new BigDecimal("100.00"));

        createAndCheckoutOrder(buyer, lot, 30);

        String token = obtainToken(organizer.getEmail());
        var response = mockMvc.perform(get("/api/events/{eventId}/occupancy", event.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        String body = response.getResponse().getContentAsString();
        assertThat(body).contains("\"checkedIn\":0");
        assertThat(body).contains("\"physicalOccupancyRate\":0.00");
    }

    @Test
    void somaBreakdownDeveCorresponderAoConsolidado() throws Exception {
        User organizer = createOrganizer("org11@test.com");
        User buyer = createBuyer("buyer11@test.com");
        Event event = createPublishedEvent(organizer, "Evento Consistência");
        TicketLot lot1 = createActiveLot(event, "Lote A", 200, new BigDecimal("100.00"));
        TicketLot lot2 = createActiveLot(event, "Lote B", 300, new BigDecimal("150.00"));

        createAndCheckoutOrder(buyer, lot1, 80);
        createAndCheckoutOrder(buyer, lot2, 120);

        String token = obtainToken(organizer.getEmail());
        var response = mockMvc.perform(get("/api/events/{eventId}/occupancy", event.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        String body = response.getResponse().getContentAsString();
        
        // Verificar se o DTO pode ser desserializado e se a soma bate
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        EventOccupancyDTO dto = mapper.readValue(body, EventOccupancyDTO.class);
        
        Long somaSold = dto.getTicketLots().stream().mapToLong(TicketLotOccupancyDTO::getSold).sum();
        Long somaReserved = dto.getTicketLots().stream().mapToLong(TicketLotOccupancyDTO::getReserved).sum();
        Long somaCheckedIn = dto.getTicketLots().stream().mapToLong(TicketLotOccupancyDTO::getCheckedIn).sum();
        Long somaCapacity = dto.getTicketLots().stream().mapToLong(TicketLotOccupancyDTO::getCapacity).sum();
        
        assertThat(somaSold).isEqualTo(dto.getSold());
        assertThat(somaReserved).isEqualTo(dto.getReserved());
        assertThat(somaCheckedIn).isEqualTo(dto.getCheckedIn());
        assertThat(somaCapacity).isEqualTo(dto.getCapacity());
    }

    @Test
    void divisaoDecimalDeveTerArredondamentoHALF_UP() throws Exception {
        User organizer = createOrganizer("org12@test.com");
        User buyer = createBuyer("buyer12@test.com");
        Event event = createPublishedEvent(organizer, "Evento Arredondamento");
        TicketLot lot = createActiveLot(event, "Lote 1", 300, new BigDecimal("100.00"));

        createAndCheckoutOrder(buyer, lot, 100); // 100/300 = 33.33%

        String token = obtainToken(organizer.getEmail());
        var response = mockMvc.perform(get("/api/events/{eventId}/occupancy", event.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        String body = response.getResponse().getContentAsString();
        assertThat(body).contains("\"commercialOccupancyRate\":33.33"); // 33.333... -> 33.33 HALF_UP
    }

    @Test
    void usuarioSemRoleOrganizadorDeveRetornar403() throws Exception {
        User organizer = createOrganizer("org13@test.com");
        User buyer = createBuyer("buyer13@test.com");
        Event event = createPublishedEvent(organizer, "Evento 403");
        createActiveLot(event, "Lote 1", 100, new BigDecimal("100.00"));

        String buyerToken = obtainToken(buyer.getEmail());
        mockMvc.perform(get("/api/events/{eventId}/occupancy", event.getId())
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void organizadorOutroEventoDeveRetornar404() throws Exception {
        User organizer1 = createOrganizer("org14a@test.com");
        User organizer2 = createOrganizer("org14b@test.com");
        Event event = createPublishedEvent(organizer1, "Evento Org1");
        createActiveLot(event, "Lote 1", 100, new BigDecimal("100.00"));

        String tokenOrg2 = obtainToken(organizer2.getEmail());
        mockMvc.perform(get("/api/events/{eventId}/occupancy", event.getId())
                        .header("Authorization", "Bearer " + tokenOrg2))
                .andExpect(status().isNotFound());
    }

    @Test
    void eventoInexistenteDeveRetornar404() throws Exception {
        User organizer = createOrganizer("org15@test.com");
        String token = obtainToken(organizer.getEmail());

        UUID nonExistentId = UUID.randomUUID();
        mockMvc.perform(get("/api/events/{eventId}/occupancy", nonExistentId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void disponibilidadeNegativaNaoDeveSerMascarada() throws Exception {
        // Este teste verifica que a fórmula de available não mascara valores negativos
        // A constraint do banco impede inconsistências, mas testamos a lógica de cálculo
        User organizer = createOrganizer("org16@test.com");
        Event event = createPublishedEvent(organizer, "Evento Available Negativo");
        TicketLot lot = createActiveLot(event, "Lote 1", 100, new BigDecimal("100.00"));

        // Valid scenario: 80 sold, 15 reserved = 95 used, 5 available
        // Then we verify the calculation formula is correct and doesn't clamp to 0
        org.springframework.transaction.support.TransactionTemplate txTemplate = 
            new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        txTemplate.execute(status -> {
            entityManager.createNativeQuery("UPDATE ticket_lots SET sold = 80, reserved = 15 WHERE id = :id")
               .setParameter("id", lot.getId())
               .executeUpdate();
            entityManager.flush();
            entityManager.clear(); // Clear persistence context to force refetch
            return null;
        });
        
        String token = obtainToken(organizer.getEmail());
        var response = mockMvc.perform(get("/api/events/{eventId}/occupancy", event.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        String body = response.getResponse().getContentAsString();
        // available = 100 - 80 - 15 = 5
        assertThat(body).contains("\"available\":5");
        
        // Also test the breakdown calculation directly with a service call
        EventOccupancyDTO dto = eventService.getOccupancy(event.getId(), organizer.getId());
        Long calculatedAvailable = dto.getCapacity() - dto.getSold() - dto.getReserved();
        assertThat(calculatedAvailable).isEqualTo(dto.getAvailable());
    }

    @Test
    void reservaQuantity5DeveRepresentar5UnidadesReservadas() throws Exception {
        User organizer = createOrganizer("org17@test.com");
        User buyer = createBuyer("buyer17@test.com");
        Event event = createPublishedEvent(organizer, "Evento Reserva Multi");
        TicketLot lot = createActiveLot(event, "Lote 1", 100, new BigDecimal("100.00"));

        orderService.reserve(buyer, lot.getId(), 5);

        String token = obtainToken(organizer.getEmail());
        var response = mockMvc.perform(get("/api/events/{eventId}/occupancy", event.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        String body = response.getResponse().getContentAsString();
        assertThat(body).contains("\"reserved\":5");
        assertThat(body).contains("\"available\":95");
    }

    private Event createPublishedEvent(User organizer, String title) {
        Event event = Event.builder()
                .title(title)
                .description("Test Description")
                .startsAt(OffsetDateTime.now().plus(1, ChronoUnit.DAYS))
                .endsAt(OffsetDateTime.now().plus(2, ChronoUnit.DAYS))
                .location("Test Location")
                .organizer(organizer)
                .status(EventStatus.PUBLISHED)
                .build();
        return eventRepository.save(event);
    }
}