package com.eventbook.backend.checkin;

import com.eventbook.backend.checkin.dto.CheckInResponseDTO;
import com.eventbook.backend.checkin.exception.EventOutsideCheckinWindowException;
import com.eventbook.backend.checkin.exception.InvalidTicketCodeException;
import com.eventbook.backend.checkin.exception.OrderNotPaidException;
import com.eventbook.backend.checkin.exception.StaffNotAuthorizedException;
import com.eventbook.backend.checkin.exception.TicketAlreadyCheckedInException;
import com.eventbook.backend.checkin.exception.TicketInvalidStatusException;
import com.eventbook.backend.checkin.exception.TicketNotFoundException;
import com.eventbook.backend.events.Event;
import com.eventbook.backend.events.EventStatus;
import com.eventbook.backend.lots.LotStatus;
import com.eventbook.backend.lots.TicketLot;
import com.eventbook.backend.orders.Order;
import com.eventbook.backend.orders.OrderStatus;
import com.eventbook.backend.staff.EventStaffRepository;
import com.eventbook.backend.tickets.Ticket;
import com.eventbook.backend.tickets.TicketRepository;
import com.eventbook.backend.tickets.TicketStatus;
import com.eventbook.backend.users.User;
import com.eventbook.backend.users.Role;
import com.eventbook.backend.users.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CheckinServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2025-06-15T12:00:00Z"), ZoneOffset.UTC);

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private CheckInRepository checkInRepository;

    @Mock
    private EventStaffRepository eventStaffRepository;

    @Mock
    private UserRepository userRepository;

    private CheckinService checkinService;

    private User organizer;
    private User staff;
    private User buyer;
    private Event event;
    private TicketLot lot;
    private Order order;
    private Ticket ticket;

    private static final UUID ORGANIZER_ID = UUID.randomUUID();
    private static final UUID STAFF_ID = UUID.randomUUID();
    private static final UUID BUYER_ID = UUID.randomUUID();
    private static final UUID EVENT_ID = UUID.randomUUID();
    private static final UUID LOT_ID = UUID.randomUUID();
    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final UUID TICKET_ID = UUID.randomUUID();

    @BeforeEach
    void setup() {
        checkinService = new CheckinService(
                ticketRepository,
                checkInRepository,
                eventStaffRepository,
                userRepository,
                CLOCK
        );

        // Setup test data
        organizer = User.builder().id(ORGANIZER_ID).name("Organizer").email("organizer@test.com").role(Role.ORGANIZADOR).build();
        staff = User.builder().id(STAFF_ID).name("Staff").email("staff@test.com").role(Role.STAFF).build();
        buyer = User.builder().id(BUYER_ID).name("Buyer").email("buyer@test.com").role(Role.COMPRADOR).build();

        event = Event.builder()
                .id(EVENT_ID)
                .title("Test Event")
                .description("Test Description")
                .startsAt(OffsetDateTime.of(2025, 6, 15, 14, 0, 0, 0, ZoneOffset.UTC))
                .endsAt(OffsetDateTime.of(2025, 6, 15, 18, 0, 0, 0, ZoneOffset.UTC))
                .location("Test Location")
                .organizer(organizer)
                .status(EventStatus.PUBLISHED)
                .build();

        lot = TicketLot.builder()
                .id(LOT_ID)
                .event(event)
                .name("Lote 1")
                .unitPrice(new BigDecimal("50.00"))
                .capacity(100)
                .sold(0)
                .reserved(0)
                .salesStart(Instant.parse("2025-01-01T00:00:00Z"))
                .salesEnd(Instant.parse("2025-12-31T23:59:00Z"))
                .status(LotStatus.ACTIVE)
                .build();

        order = Order.builder()
                .id(ORDER_ID)
                .buyer(buyer)
                .ticketLot(lot)
                .quantity(1)
                .unitPriceSnapshot(new BigDecimal("50.00"))
                .status(OrderStatus.PAID)
                .createdAt(Instant.parse("2025-06-15T11:00:00Z"))
                .paidAt(Instant.parse("2025-06-15T11:30:00Z"))
                .build();

        ticket = Ticket.builder()
                .id(TICKET_ID)
                .order(order)
                .uniqueCode("VALID_TICKET_123")
                .status(TicketStatus.VALID)
                .build();
    }

    private void setupValidTicketMocks() {
        lenient().when(ticketRepository.findByUniqueCodeForUpdate("VALID_TICKET_123")).thenReturn(Optional.of(ticket));
        lenient().when(checkInRepository.findByTicketId(TICKET_ID)).thenReturn(Optional.empty());
        lenient().when(eventStaffRepository.existsByEventIdAndUserId(EVENT_ID, STAFF_ID)).thenReturn(true);
        lenient().when(userRepository.findById(STAFF_ID)).thenReturn(Optional.of(staff));
    }

    @Test
    void checkInSucesso() {
        setupValidTicketMocks();

        CheckInResponseDTO response = checkinService.checkIn("VALID_TICKET_123", STAFF_ID);

        assertThat(response).isNotNull();
        assertThat(response.getTicketCode()).isEqualTo("VALID_TICKET_123");
        assertThat(response.getStaffId()).isEqualTo(STAFF_ID);
        assertThat(response.getCheckedInAt()).isEqualTo(Instant.parse("2025-06-15T12:00:00Z"));
    }

    @Test
    void ticketInexistenteDeveLancar404() {
        when(ticketRepository.findByUniqueCodeForUpdate("NONEXISTENT")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> checkinService.checkIn("NONEXISTENT", STAFF_ID))
                .isInstanceOf(TicketNotFoundException.class)
                .hasMessage("Ticket não encontrado");
    }

    @Test
    void codigoVazioDeveLancar400() {
        assertThatThrownBy(() -> checkinService.checkIn("", STAFF_ID))
                .isInstanceOf(InvalidTicketCodeException.class)
                .hasMessage("Código do ticket inválido");
    }

    @Test
    void codigoNuloDeveLancar400() {
        assertThatThrownBy(() -> checkinService.checkIn(null, STAFF_ID))
                .isInstanceOf(InvalidTicketCodeException.class)
                .hasMessage("Código do ticket inválido");
    }

    @Test
    void codigoApenasEspacosDeveLancar400() {
        assertThatThrownBy(() -> checkinService.checkIn("   ", STAFF_ID))
                .isInstanceOf(InvalidTicketCodeException.class)
                .hasMessage("Código do ticket inválido");
    }

    @Test
    void codigoMuitoLongoDeveLancar400() {
        String longCode = "A".repeat(65);
        assertThatThrownBy(() -> checkinService.checkIn(longCode, STAFF_ID))
                .isInstanceOf(InvalidTicketCodeException.class)
                .hasMessage("Código do ticket inválido");
    }

    @Test
    void codigoValidoCom64CaracteresNaoDeveLancarExcecaoDeValidacao() {
        String code64 = "A".repeat(64);
        when(ticketRepository.findByUniqueCodeForUpdate(code64)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> checkinService.checkIn(code64, STAFF_ID))
                .isInstanceOf(TicketNotFoundException.class)
                .hasMessage("Ticket não encontrado");
    }

    @Test
    void pedidoNaoPagoDeveLancar422() {
        Order pendingOrder = Order.builder()
                .id(UUID.randomUUID())
                .buyer(buyer)
                .ticketLot(lot)
                .quantity(1)
                .unitPriceSnapshot(new BigDecimal("50.00"))
                .status(OrderStatus.PENDING)
                .createdAt(Instant.parse("2025-06-15T11:00:00Z"))
                .build();

        Ticket pendingTicket = Ticket.builder()
                .id(UUID.randomUUID())
                .order(pendingOrder)
                .uniqueCode("PENDING_TICKET")
                .status(TicketStatus.VALID)
                .build();

        when(ticketRepository.findByUniqueCodeForUpdate("PENDING_TICKET")).thenReturn(Optional.of(pendingTicket));

        assertThatThrownBy(() -> checkinService.checkIn("PENDING_TICKET", STAFF_ID))
                .isInstanceOf(OrderNotPaidException.class)
                .hasMessage("Pedido não está pago");
    }

    @Test
    void pedidoCanceladoDeveLancar422() {
        Order cancelledOrder = Order.builder()
                .id(UUID.randomUUID())
                .buyer(buyer)
                .ticketLot(lot)
                .quantity(1)
                .unitPriceSnapshot(new BigDecimal("50.00"))
                .status(OrderStatus.CANCELLED)
                .createdAt(Instant.parse("2025-06-15T11:00:00Z"))
                .build();

        Ticket cancelledTicket = Ticket.builder()
                .id(UUID.randomUUID())
                .order(cancelledOrder)
                .uniqueCode("CANCELLED_TICKET")
                .status(TicketStatus.VALID)
                .build();

        when(ticketRepository.findByUniqueCodeForUpdate("CANCELLED_TICKET")).thenReturn(Optional.of(cancelledTicket));

        assertThatThrownBy(() -> checkinService.checkIn("CANCELLED_TICKET", STAFF_ID))
                .isInstanceOf(OrderNotPaidException.class)
                .hasMessage("Pedido não está pago");
    }

    @Test
    void staffNaoAssociadoAoEventoDeveLancar403() {
        setupValidTicketMocks();
        when(eventStaffRepository.existsByEventIdAndUserId(EVENT_ID, STAFF_ID)).thenReturn(false);

        assertThatThrownBy(() -> checkinService.checkIn("VALID_TICKET_123", STAFF_ID))
                .isInstanceOf(StaffNotAuthorizedException.class)
                .hasMessage("Staff não autorizado para este evento");
    }

    @Test
    void ticketCanceladoDeveLancar422() {
        Ticket cancelledTicket = Ticket.builder()
                .id(UUID.randomUUID())
                .order(order)
                .uniqueCode("CANCELLED_TICKET_2")
                .status(TicketStatus.CANCELLED)
                .build();

        when(ticketRepository.findByUniqueCodeForUpdate("CANCELLED_TICKET_2")).thenReturn(Optional.of(cancelledTicket));
        when(checkInRepository.findByTicketId(cancelledTicket.getId())).thenReturn(Optional.empty());
        when(eventStaffRepository.existsByEventIdAndUserId(EVENT_ID, STAFF_ID)).thenReturn(true);

        assertThatThrownBy(() -> checkinService.checkIn("CANCELLED_TICKET_2", STAFF_ID))
                .isInstanceOf(TicketInvalidStatusException.class)
                .hasMessage("Ticket não está em status válido para check-in");
    }

    @Test
    void ticketJaCheckedInDeveLancar409() {
        setupValidTicketMocks();
        CheckIn existingCheckIn = CheckIn.builder()
                .id(UUID.randomUUID())
                .ticket(ticket)
                .staff(staff)
                .checkedInAt(Instant.parse("2025-06-15T12:00:00Z"))
                .build();

        when(checkInRepository.findByTicketId(TICKET_ID)).thenReturn(Optional.of(existingCheckIn));

        assertThatThrownBy(() -> checkinService.checkIn("VALID_TICKET_123", STAFF_ID))
                .isInstanceOf(TicketAlreadyCheckedInException.class)
                .satisfies(ex -> {
                    TicketAlreadyCheckedInException e = (TicketAlreadyCheckedInException) ex;
                    assertThat(e.getExistingCheckIn()).isNotNull();
                    assertThat(e.getExistingCheckIn().getTicket().getUniqueCode()).isEqualTo("VALID_TICKET_123");
                    assertThat(e.getExistingCheckIn().getStaff().getId()).isEqualTo(STAFF_ID);
                    assertThat(e.getExistingCheckIn().getCheckedInAt()).isEqualTo(Instant.parse("2025-06-15T12:00:00Z"));
                });
    }

    @Test
    void antesDaJanelaDeCheckInDeveLancar422() {
        setupValidTicketMocks();
        Clock earlyClock = Clock.fixed(Instant.parse("2025-06-15T11:59:00Z"), ZoneOffset.UTC);
        CheckinService earlyService = new CheckinService(ticketRepository, checkInRepository, eventStaffRepository, userRepository, earlyClock);

        assertThatThrownBy(() -> earlyService.checkIn("VALID_TICKET_123", STAFF_ID))
                .isInstanceOf(EventOutsideCheckinWindowException.class)
                .hasMessage("Evento fora da janela de check-in");
    }

    @Test
    void exatamenteNoInicioDaJanelaDevePermitir() {
        setupValidTicketMocks();
        // CLOCK is at 12:00, window starts at 12:00 (startsAt - 2h = 14:00 - 2h = 12:00)
        CheckInResponseDTO response = checkinService.checkIn("VALID_TICKET_123", STAFF_ID);
        assertThat(response).isNotNull();
    }

    @Test
    void duranteAJanelaDevePermitir() {
        setupValidTicketMocks();
        Clock duringClock = Clock.fixed(Instant.parse("2025-06-15T15:00:00Z"), ZoneOffset.UTC);
        CheckinService duringService = new CheckinService(ticketRepository, checkInRepository, eventStaffRepository, userRepository, duringClock);

        CheckInResponseDTO response = duringService.checkIn("VALID_TICKET_123", STAFF_ID);
        assertThat(response).isNotNull();
    }

    @Test
    void exatamenteNoFimDaJanelaDevePermitir() {
        setupValidTicketMocks();
        Clock endClock = Clock.fixed(Instant.parse("2025-06-15T18:00:00Z"), ZoneOffset.UTC);
        CheckinService endService = new CheckinService(ticketRepository, checkInRepository, eventStaffRepository, userRepository, endClock);

        CheckInResponseDTO response = endService.checkIn("VALID_TICKET_123", STAFF_ID);
        assertThat(response).isNotNull();
    }

    @Test
    void depoisDaJanelaDeveLancar422() {
        setupValidTicketMocks();
        Clock lateClock = Clock.fixed(Instant.parse("2025-06-15T18:01:00Z"), ZoneOffset.UTC);
        CheckinService lateService = new CheckinService(ticketRepository, checkInRepository, eventStaffRepository, userRepository, lateClock);

        assertThatThrownBy(() -> lateService.checkIn("VALID_TICKET_123", STAFF_ID))
                .isInstanceOf(EventOutsideCheckinWindowException.class)
                .hasMessage("Evento fora da janela de check-in");
    }
}