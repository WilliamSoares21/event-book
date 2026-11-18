package com.eventbook.backend.orders;

import com.eventbook.backend.cards.CardStatus;
import com.eventbook.backend.cards.SimulatedCard;
import com.eventbook.backend.cards.SimulatedCardRepository;
import com.eventbook.backend.events.Event;
import com.eventbook.backend.events.EventRepository;
import com.eventbook.backend.events.EventStatus;
import com.eventbook.backend.lots.LotStatus;
import com.eventbook.backend.lots.TicketLot;
import com.eventbook.backend.lots.TicketLotRepository;
import com.eventbook.backend.payments.PaymentTransactionRepository;
import com.eventbook.backend.users.Role;
import com.eventbook.backend.users.User;
import com.eventbook.backend.users.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Prova, com PostgreSQL real e sem mocks, que uma falha após o início das alterações
 * faz rollback integral. A falha é induzida por uma constraint CHECK temporária que
 * rejeita o UPDATE do pedido para PAID — depois de o lote já ter sido convertido e os
 * tickets já terem sido inseridos na mesma transação.
 */
@Testcontainers
@SpringBootTest
class OrderCheckoutRollbackTest {

    private static final String CONSTRAINT = "test_block_paid";

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
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private TicketLotRepository ticketLotRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SimulatedCardRepository simulatedCardRepository;

    @Autowired
    private PaymentTransactionRepository paymentTransactionRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final String commonPassword = "password123";

    @BeforeEach
    void setup() {
        jdbcTemplate.update("DELETE FROM payment_transactions");
        jdbcTemplate.update("DELETE FROM tickets");
        orderRepository.deleteAll();
        simulatedCardRepository.deleteAll();
        ticketLotRepository.deleteAll();
        eventRepository.deleteAll();
        userRepository.deleteAll();
    }

    @AfterEach
    void dropConstraint() {
        jdbcTemplate.execute("ALTER TABLE orders DROP CONSTRAINT IF EXISTS " + CONSTRAINT);
    }

    @Test
    void deveFazerRollbackIntegralQuandoFalhaAposInicioDasAlteracoes() {
        User organizer = userRepository.save(User.builder()
                .name("Organizer").email("organizer@test.com")
                .passwordHash(passwordEncoder.encode(commonPassword))
                .role(Role.ORGANIZADOR).build());

        User buyer = userRepository.save(User.builder()
                .name("Buyer").email("buyer@test.com")
                .passwordHash(passwordEncoder.encode(commonPassword))
                .role(Role.COMPRADOR).build());

        Event event = eventRepository.save(Event.builder()
                .title("Rollback IT Event").description("d")
                .startsAt(OffsetDateTime.now().plus(1, ChronoUnit.DAYS))
                .endsAt(OffsetDateTime.now().plus(2, ChronoUnit.DAYS))
                .location("loc").organizer(organizer).status(EventStatus.PUBLISHED).build());

        TicketLot lot = ticketLotRepository.save(TicketLot.builder()
                .event(event).name("Lote 1").unitPrice(new BigDecimal("50.00"))
                .capacity(100).sold(0).reserved(2)
                .salesStart(Instant.now().minus(1, ChronoUnit.HOURS))
                .salesEnd(Instant.now().plus(1, ChronoUnit.HOURS))
                .status(LotStatus.ACTIVE).build());

        Order order = orderRepository.save(Order.builder()
                .buyer(buyer).ticketLot(lot).quantity(2)
                .unitPriceSnapshot(new BigDecimal("50.00"))
                .status(OrderStatus.PENDING)
                .expiresAt(Instant.now().plus(10, ChronoUnit.MINUTES))
                .build());

        SimulatedCard card = simulatedCardRepository.save(SimulatedCard.builder()
                .user(buyer).lastFourDigits("1234").brand("VISA")
                .expiryMonth(12).expiryYear(2999)
                .totalLimit(new BigDecimal("1000.00"))
                .availableLimit(new BigDecimal("1000.00"))
                .status(CardStatus.ACTIVE).build());

        // Falha real no Postgres: impede persistir o pedido como PAID.
        // Ocorre no UPDATE final, depois da conversão do lote e da inserção dos tickets.
        jdbcTemplate.execute(
                "ALTER TABLE orders ADD CONSTRAINT " + CONSTRAINT + " CHECK (status <> 'PAID')");

        assertThatThrownBy(() -> orderService.checkout(order.getId(), buyer.getId(), card.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);

        // Rollback integral comprovado no banco real.
        Order persistedOrder = orderRepository.findById(order.getId()).orElseThrow();
        assertThat(persistedOrder.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(persistedOrder.getPaidAt()).isNull();

        TicketLot persistedLot = ticketLotRepository.findById(lot.getId()).orElseThrow();
        assertThat(persistedLot.getReserved()).isEqualTo(2);
        assertThat(persistedLot.getSold()).isZero();

        Integer ticketCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tickets WHERE order_id = ?", Integer.class, order.getId());
        assertThat(ticketCount).isZero();

        // O débito do cartão e a transação APPROVED também foram revertidos.
        assertThat(simulatedCardRepository.findById(card.getId()).orElseThrow().getAvailableLimit())
                .isEqualByComparingTo("1000.00");
        assertThat(paymentTransactionRepository.count()).isZero();
    }
}
