package com.eventbook.backend.events;

import com.eventbook.backend.events.dto.OrganizerEventResponseDTO;
import com.eventbook.backend.lots.LotStatus;
import com.eventbook.backend.lots.dto.TicketLotResponseDTO;
import com.eventbook.backend.users.User;
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
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrganizerEventServiceTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-06-01T12:00:00Z"), ZoneOffset.UTC);

    @Mock
    private EventRepository eventRepository;

    @Mock
    private com.eventbook.backend.lots.TicketLotRepository ticketLotRepository;

    @Mock
    private com.eventbook.backend.orders.OrderRepository orderRepository;

    @Mock
    private com.eventbook.backend.tickets.TicketRepository ticketRepository;

    @Mock
    private com.eventbook.backend.checkin.CheckInRepository checkInRepository;

    @Mock
    private UserRepository userRepository;

    private EventService eventService;

    @BeforeEach
    void setUp() {
        eventService = new EventService(
                eventRepository,
                ticketLotRepository,
                orderRepository,
                ticketRepository,
                checkInRepository,
                userRepository,
                CLOCK
        );
    }

    @Test
    void deveRetornarEventosDoOrganizadorComLotes() {
        UUID organizerId = UUID.randomUUID();
        Event draft = event(EventStatus.DRAFT, organizerId);
        Event published = event(EventStatus.PUBLISHED, organizerId);

        when(eventRepository.findByOrganizerIdOrderByStartsAtDesc(organizerId))
                .thenReturn(List.of(draft, published));

        var activeLot = lot(draft, LotStatus.ACTIVE);
        var inactiveLot = lot(published, LotStatus.INACTIVE);
        when(ticketLotRepository.findByEventIdInOrderByNameAsc(anyCollection()))
                .thenReturn(List.of(activeLot, inactiveLot));

        List<OrganizerEventResponseDTO> result = eventService.findByOrganizer(organizerId);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getStatus()).isEqualTo(EventStatus.DRAFT);
        assertThat(result.get(0).getTicketLots()).hasSize(1);
        assertThat(result.get(0).getTicketLots().get(0).getStatus()).isEqualTo(LotStatus.ACTIVE);
        assertThat(result.get(1).getStatus()).isEqualTo(EventStatus.PUBLISHED);
        assertThat(result.get(1).getTicketLots()).hasSize(1);
        assertThat(result.get(1).getTicketLots().get(0).getStatus()).isEqualTo(LotStatus.INACTIVE);

        // Verifica consulta em lote, sem N+1 por evento.
        verify(ticketLotRepository).findByEventIdInOrderByNameAsc(anyCollection());
    }

    @Test
    void deveRetornarEventoSemLotesQuandoNaoHouverLotes() {
        UUID organizerId = UUID.randomUUID();
        Event draft = event(EventStatus.DRAFT, organizerId);

        when(eventRepository.findByOrganizerIdOrderByStartsAtDesc(organizerId)).thenReturn(List.of(draft));
        when(ticketLotRepository.findByEventIdInOrderByNameAsc(anyCollection())).thenReturn(List.of());

        List<OrganizerEventResponseDTO> result = eventService.findByOrganizer(organizerId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTicketLots()).isEmpty();
    }

    @Test
    void deveRetornarListaVaziaSemConsultarLotes() {
        UUID organizerId = UUID.randomUUID();
        when(eventRepository.findByOrganizerIdOrderByStartsAtDesc(organizerId)).thenReturn(List.of());

        List<OrganizerEventResponseDTO> result = eventService.findByOrganizer(organizerId);

        assertThat(result).isEmpty();
        verify(ticketLotRepository, never()).findByEventIdInOrderByNameAsc(any());
    }

    private Event event(EventStatus status, UUID organizerId) {
        return Event.builder()
                .id(UUID.randomUUID())
                .organizer(User.builder().id(organizerId).build())
                .title("Evento")
                .description("Descrição")
                .startsAt(OffsetDateTime.parse("2026-11-20T19:00:00-03:00"))
                .endsAt(OffsetDateTime.parse("2026-11-20T23:00:00-03:00"))
                .location("Local")
                .status(status)
                .version(0L)
                .build();
    }

    private com.eventbook.backend.lots.TicketLot lot(Event event, LotStatus status) {
        return com.eventbook.backend.lots.TicketLot.builder()
                .id(UUID.randomUUID())
                .event(event)
                .name("Lote")
                .unitPrice(BigDecimal.TEN)
                .capacity(100)
                .sold(1)
                .reserved(2)
                .salesStart(Instant.parse("2026-10-01T00:00:00Z"))
                .salesEnd(Instant.parse("2026-11-20T00:00:00Z"))
                .status(status)
                .version(0L)
                .build();
    }
}
