package com.eventbook.backend.orders;

import com.eventbook.backend.cards.SimulatedCard;
import com.eventbook.backend.cards.SimulatedCardRepository;
import com.eventbook.backend.events.Event;
import com.eventbook.backend.events.EventRepository;
import com.eventbook.backend.events.EventStatus;
import com.eventbook.backend.lots.LotStatus;
import com.eventbook.backend.lots.TicketLot;
import com.eventbook.backend.lots.TicketLotRepository;
import com.eventbook.backend.tickets.TicketRepository;
import com.eventbook.backend.users.Role;
import com.eventbook.backend.users.User;
import com.eventbook.backend.users.UserRepository;
import com.eventbook.backend.users.UserService;
import com.eventbook.backend.users.dto.RegisterUserRequest;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class OrderSnapshotIntegrationTest {

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
    private com.eventbook.backend.payments.PaymentTransactionRepository paymentTransactionRepository;

    @Autowired
    private OrderService orderService;

    @Autowired
    private SimulatedCardRepository simulatedCardRepository;

    @Autowired
    private com.eventbook.backend.lots.TicketLotService ticketLotService;

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

    private SimulatedCard createCard(User buyer, BigDecimal limit) {
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
    void snapshotDevePermanecerImutavelAposAlteracaoDePrecoDoLote() throws Exception {
        // Given: Lote com preço R$ 100,00
        User organizer = createOrganizer();
        User buyer = createBuyer("buyer@test.com");
        TicketLot lot = createActiveLot(organizer, 100, new BigDecimal("100.00"));

        // When: Pedido criado com 2 unidades
        Order order = orderService.reserve(buyer, lot.getId(), 2);
        BigDecimal snapshotOriginal = order.getUnitPriceSnapshot();
        BigDecimal totalOriginal = snapshotOriginal.multiply(BigDecimal.valueOf(2));

        // And: Preço do lote alterado para R$ 150,00 (via service, não setter direto)
        com.eventbook.backend.lots.dto.TicketLotUpdateDTO updateDTO = new com.eventbook.backend.lots.dto.TicketLotUpdateDTO();
        updateDTO.setUnitPrice(new BigDecimal("150.00"));
        ticketLotService.update(lot.getId(), updateDTO, organizer.getId());

        // Then: Pedido original mantém snapshot e total
        Order reloaded = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(reloaded.getUnitPriceSnapshot()).isEqualByComparingTo("100.00");

        // And: Checkout cobra R$ 200,00, não R$ 300,00
        SimulatedCard card = createCard(buyer, new BigDecimal("1000.00"));
        var checkoutResult = orderService.checkout(order.getId(), buyer.getId(), card.getId());

        assertThat(checkoutResult.response().totalPrice()).isEqualByComparingTo("200.00");
        assertThat(checkoutResult.response().unitPriceSnapshot()).isEqualByComparingTo("100.00");
    }

    @Test
    void checkoutDeveUsarSnapshotNaoPrecoAtualDoLote() throws Exception {
        User organizer = createOrganizer();
        User buyer = createBuyer("buyer2@test.com");
        TicketLot lot = createActiveLot(organizer, 100, new BigDecimal("50.00"));

        Order order = orderService.reserve(buyer, lot.getId(), 3);
        BigDecimal snapshot = order.getUnitPriceSnapshot();

        // Alterar preço do lote após reserva (via service)
        com.eventbook.backend.lots.dto.TicketLotUpdateDTO updateDTO = new com.eventbook.backend.lots.dto.TicketLotUpdateDTO();
        updateDTO.setUnitPrice(new BigDecimal("200.00"));
        ticketLotService.update(lot.getId(), updateDTO, organizer.getId());

        SimulatedCard card = createCard(buyer, new BigDecimal("1000.00"));
        var checkoutResult = orderService.checkout(order.getId(), buyer.getId(), card.getId());

        // Total deve ser 3 * 50.00 = 150.00, não 3 * 200.00 = 600.00
        assertThat(checkoutResult.response().totalPrice()).isEqualByComparingTo("150.00");
        assertThat(checkoutResult.response().unitPriceSnapshot()).isEqualByComparingTo("50.00");
    }

    @Test
    void pedidoMultiplasUnidadesDeveCalcularTotalCorretamente() throws Exception {
        User organizer = createOrganizer();
        User buyer = createBuyer("buyer3@test.com");
        TicketLot lot = createActiveLot(organizer, 100, new BigDecimal("33.33"));

        Order order = orderService.reserve(buyer, lot.getId(), 3);
        assertThat(order.getUnitPriceSnapshot()).isEqualByComparingTo("33.33");

        // Alterar preço do lote após reserva (via service)
        com.eventbook.backend.lots.dto.TicketLotUpdateDTO updateDTO = new com.eventbook.backend.lots.dto.TicketLotUpdateDTO();
        updateDTO.setUnitPrice(new BigDecimal("99.99"));
        ticketLotService.update(lot.getId(), updateDTO, organizer.getId());

        SimulatedCard card = createCard(buyer, new BigDecimal("1000.00"));
        var checkoutResult = orderService.checkout(order.getId(), buyer.getId(), card.getId());

        // 33.33 * 3 = 99.99 (matematicamente correto, não é divergência)
        assertThat(checkoutResult.response().totalPrice()).isEqualByComparingTo("99.99");
        assertThat(checkoutResult.response().unitPriceSnapshot()).isEqualByComparingTo("33.33");
    }

    @Test
    void reembolsoDeveUsarValorHistoricoDaTransacaoAprovada() throws Exception {
        User organizer = createOrganizer();
        User buyer = createBuyer("buyer4@test.com");
        TicketLot lot = createActiveLot(organizer, 100, new BigDecimal("75.00"));

        Order order = orderService.reserve(buyer, lot.getId(), 2);
        SimulatedCard card = createCard(buyer, new BigDecimal("1000.00"));
        var checkoutResult = orderService.checkout(order.getId(), buyer.getId(), card.getId());

        BigDecimal valorOriginal = checkoutResult.response().totalPrice(); // 150.00
        assertThat(valorOriginal).isEqualByComparingTo("150.00");

        // Cancelar pedido PAID - deve reembolsar o valor histórico
        orderService.cancel(order.getId(), buyer.getId());

        // Verificar se o estorno foi processado com o mesmo valor
        var refundedTransaction = paymentTransactionRepository
            .findByOrderIdAndStatus(order.getId(), com.eventbook.backend.payments.PaymentStatus.REFUNDED)
            .orElseThrow();

        assertThat(refundedTransaction.getAmount()).isEqualByComparingTo("150.00");
    }

    @Test
    void arredondamentoTotalDeveTerDuasCasasDecimaisHALF_UP() throws Exception {
        User organizer = createOrganizer();
        User buyer = createBuyer("buyer5@test.com");
        TicketLot lot = createActiveLot(organizer, 100, new BigDecimal("33.33"));

        Order order = orderService.reserve(buyer, lot.getId(), 3);
        SimulatedCard card = createCard(buyer, new BigDecimal("1000.00"));
        var checkoutResult = orderService.checkout(order.getId(), buyer.getId(), card.getId());

        // Total deve ter exatamente 2 casas decimais
        String totalStr = checkoutResult.response().totalPrice().toPlainString();
        assertThat(totalStr).matches("\\d+\\.\\d{2}");
        assertThat(checkoutResult.response().totalPrice()).isEqualByComparingTo("99.99");
    }
}