package com.eventbook.backend.events;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


import com.eventbook.backend.checkin.CheckInRepository;
import com.eventbook.backend.common.exception.BusinessException;
import com.eventbook.backend.events.dto.EventRequestDTO;
import com.eventbook.backend.events.dto.EventResponseDTO;
import com.eventbook.backend.events.dto.EventUpdateDTO;
import com.eventbook.backend.lots.TicketLotRepository;
import com.eventbook.backend.orders.OrderRepository;
import com.eventbook.backend.tickets.TicketRepository;
import com.eventbook.backend.users.User;
import com.eventbook.backend.users.UserRepository;

@ExtendWith(MockitoExtension.class)
public class EventServiceTest {
    
    @Mock
    private EventRepository eventRepository;

    @Mock
    private TicketLotRepository ticketLotRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private CheckInRepository checkInRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private EventService eventService;

    @Test
    void deveCriarEventoComoDraft() {
        UUID organizerId = UUID.randomUUID();
        User organizer = User.builder()
                .id(organizerId)
                .build();

        EventRequestDTO request = EventRequestDTO.builder()
                .title("Festival")
                .description("Descrição")
                .startsAt(OffsetDateTime.now().plusDays(10))
                .endsAt(OffsetDateTime.now().plusDays(10).plusHours(2))
                .location("São Paulo")
                .build();

        when(userRepository.getReferenceById(organizerId))
                .thenReturn(organizer);

        when(eventRepository.save(any(Event.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        EventResponseDTO response = eventService.create(request, organizerId);

        assertThat(response.getStatus()).isEqualTo(EventStatus.DRAFT);
        assertThat(response.getOrganizerId()).isEqualTo(organizerId);

        verify(eventRepository).save(any(Event.class));
    }

    @Test
    void deveRejeitarQuandoFimNaoForPosteriorAoInicio() {
        EventRequestDTO request = EventRequestDTO.builder()
                .title("Festival")
                .startsAt(OffsetDateTime.now().plusDays(10))
                .endsAt(OffsetDateTime.now().plusDays(9))
                .location("São Paulo")
                .build();

        assertThatThrownBy(() ->
                eventService.create(request, UUID.randomUUID())
        )
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue(
                        "status",
                        HttpStatus.UNPROCESSABLE_CONTENT
                );
    }

    @Test
    void deveRejeitarAtualizacaoDeOutroOrganizador() {
        UUID ownerId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();

        Event event = Event.builder()
                .id(UUID.randomUUID())
                .organizer(User.builder().id(ownerId).build())
                .status(EventStatus.DRAFT)
                .title("Festival")
                .startsAt(OffsetDateTime.now().plusDays(10))
                .endsAt(OffsetDateTime.now().plusDays(10).plusHours(2))
                .location("São Paulo")
                .build();

        when(eventRepository.findById(event.getId()))
                .thenReturn(java.util.Optional.of(event));

        EventUpdateDTO update = EventUpdateDTO.builder()
                .title("Novo título")
                .build();

        assertThatThrownBy(() ->
                eventService.update(event.getId(), update, requesterId)
        )
                .isInstanceOf(BusinessException.class)
                .extracting("status")
                .isEqualTo(org.springframework.http.HttpStatus.FORBIDDEN);
    }

    @Test
    void deveListarSomenteEventosPublicados() {
        Event published = event(EventStatus.PUBLISHED, UUID.randomUUID());

        when(eventRepository.findByStatus(EventStatus.PUBLISHED))
                .thenReturn(List.of(published));

        List<EventResponseDTO> response = eventService.findAllPublished();

        assertThat(response).hasSize(1);
        assertThat(response.get(0).getStatus()).isEqualTo(EventStatus.PUBLISHED);
        verify(eventRepository).findByStatus(EventStatus.PUBLISHED);
    }

    @Test
    void devePermitirConsultaDeDraftPeloProprietario() {
        UUID ownerId = UUID.randomUUID();
        Event draft = event(EventStatus.DRAFT, ownerId);

        when(eventRepository.findById(draft.getId()))
                .thenReturn(Optional.of(draft));

        EventResponseDTO response = eventService.getById(draft.getId(), ownerId);

        assertThat(response.getStatus()).isEqualTo(EventStatus.DRAFT);
        assertThat(response.getOrganizerId()).isEqualTo(ownerId);
    }

    @Test
    void deveOcultarDraftDeTerceiros() {
        UUID ownerId = UUID.randomUUID();
        Event draft = event(EventStatus.DRAFT, ownerId);

        when(eventRepository.findById(draft.getId()))
                .thenReturn(Optional.of(draft));

        assertThatThrownBy(() ->
                eventService.getById(draft.getId(), UUID.randomUUID())
        )
                .isInstanceOf(BusinessException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void deveRetornar404ParaEventoInexistente() {
        UUID eventId = UUID.randomUUID();

        when(eventRepository.findById(eventId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> eventService.getById(eventId, null))
                .isInstanceOf(BusinessException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void devePreservarCamposNaoEnviadosNoPatch() {
        UUID ownerId = UUID.randomUUID();

        Event event = Event.builder()
                .id(UUID.randomUUID())
                .organizer(User.builder().id(ownerId).build())
                .status(EventStatus.DRAFT)
                .title("Título antigo")
                .description("Descrição antiga")
                .startsAt(OffsetDateTime.now().plusDays(10))
                .endsAt(OffsetDateTime.now().plusDays(10).plusHours(2))
                .location("Local antigo")
                .build();

        when(eventRepository.findById(event.getId()))
                .thenReturn(java.util.Optional.of(event));

        when(eventRepository.save(any(Event.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        EventUpdateDTO update = EventUpdateDTO.builder()
                .title("Título novo")
                .build();

        eventService.update(event.getId(), update, ownerId);

        assertThat(event.getTitle()).isEqualTo("Título novo");
        assertThat(event.getDescription()).isEqualTo("Descrição antiga");
        assertThat(event.getLocation()).isEqualTo("Local antigo");
    }

    @Test
    void deveRejeitarAtualizacaoComDatasInvalidas() {
        UUID ownerId = UUID.randomUUID();
        Event event = event(EventStatus.DRAFT, ownerId);

        when(eventRepository.findById(event.getId()))
                .thenReturn(Optional.of(event));

        EventUpdateDTO update = EventUpdateDTO.builder()
                .endsAt(event.getStartsAt().minusMinutes(1))
                .build();

        assertThatThrownBy(() ->
                eventService.update(event.getId(), update, ownerId)
        )
                .isInstanceOf(BusinessException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    }

    @Test
    void deveConverterConflitoOtimistaPara409() {
        UUID ownerId = UUID.randomUUID();
        Event event = event(EventStatus.DRAFT, ownerId);

        when(eventRepository.findById(event.getId()))
                .thenReturn(Optional.of(event));
        when(eventRepository.save(any(Event.class)))
                .thenThrow(new ObjectOptimisticLockingFailureException(
                        Event.class,
                        event.getId()
                ));

        assertThatThrownBy(() ->
                eventService.update(
                        event.getId(),
                        EventUpdateDTO.builder().title("Novo título").build(),
                        ownerId
                )
        )
                .isInstanceOf(BusinessException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.CONFLICT);
    }

    private Event event(EventStatus status, UUID organizerId) {
        return Event.builder()
                .id(UUID.randomUUID())
                .organizer(User.builder().id(organizerId).build())
                .status(status)
                .title("Festival")
                .description("Descrição")
                .startsAt(OffsetDateTime.now().plusDays(10))
                .endsAt(OffsetDateTime.now().plusDays(10).plusHours(2))
                .location("São Paulo")
                .build();
    }
}
