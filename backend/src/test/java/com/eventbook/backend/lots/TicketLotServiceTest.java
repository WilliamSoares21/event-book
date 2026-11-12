package com.eventbook.backend.lots;

import com.eventbook.backend.common.exception.BusinessException;
import com.eventbook.backend.events.Event;
import com.eventbook.backend.events.EventRepository;
import com.eventbook.backend.events.EventStatus;
import com.eventbook.backend.lots.dto.TicketLotRequestDTO;
import com.eventbook.backend.lots.dto.TicketLotResponseDTO;
import com.eventbook.backend.lots.dto.TicketLotUpdateDTO;
import com.eventbook.backend.users.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketLotServiceTest {

    @Mock
    private TicketLotRepository ticketLotRepository;

    @Mock
    private EventRepository eventRepository;

    @InjectMocks
    private TicketLotService ticketLotService;

    @Test
    void deveCriarLoteAtivoComContadoresZerados() {
        UUID organizerId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        Event event = event(eventId, organizerId);
        TicketLotRequestDTO request = request();

        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(ticketLotRepository.save(any(TicketLot.class)))
                .thenAnswer(invocation -> {
                    TicketLot lot = invocation.getArgument(0);
                    lot.setId(UUID.randomUUID());
                    return lot;
                });

        TicketLotResponseDTO response =
                ticketLotService.create(eventId, request, organizerId);

        assertThat(response.getStatus()).isEqualTo(LotStatus.ACTIVE);
        assertThat(response.getSold()).isZero();
        assertThat(response.getReserved()).isZero();
        assertThat(response.getEventId()).isEqualTo(eventId);
        verify(ticketLotRepository).save(any(TicketLot.class));
    }

    @Test
    void deveRejeitarAtualizacaoDeLotePorOrganizadorSemPropriedade() {
        UUID ownerId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        TicketLot lot = TicketLot.builder()
                .id(UUID.randomUUID())
                .event(event(UUID.randomUUID(), ownerId))
                .name("Lote")
                .unitPrice(BigDecimal.TEN)
                .capacity(10)
                .sold(2)
                .reserved(1)
                .salesStart(Instant.now())
                .salesEnd(Instant.now().plusSeconds(3600))
                .status(LotStatus.ACTIVE)
                .build();

        when(ticketLotRepository.findByIdForUpdate(lot.getId()))
                .thenReturn(Optional.of(lot));

        assertThatThrownBy(() -> ticketLotService.update(
                lot.getId(),
                TicketLotUpdateDTO.builder().capacity(20).build(),
                requesterId
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("status")
                .isEqualTo(org.springframework.http.HttpStatus.FORBIDDEN);
    }

    @Test
    void naoDevePermitirCapacidadeAbaixoDaOcupacao() {
        UUID organizerId = UUID.randomUUID();
        TicketLot lot = TicketLot.builder()
                .id(UUID.randomUUID())
                .event(event(UUID.randomUUID(), organizerId))
                .name("Lote")
                .unitPrice(BigDecimal.TEN)
                .capacity(10)
                .sold(6)
                .reserved(3)
                .salesStart(Instant.now())
                .salesEnd(Instant.now().plusSeconds(3600))
                .status(LotStatus.ACTIVE)
                .build();

        when(ticketLotRepository.findByIdForUpdate(lot.getId()))
                .thenReturn(Optional.of(lot));

        assertThatThrownBy(() -> ticketLotService.update(
                lot.getId(),
                TicketLotUpdateDTO.builder().capacity(8).build(),
                organizerId
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("status")
                .isEqualTo(org.springframework.http.HttpStatus.CONFLICT);
    }

    @Test
    void devePreservarCamposNaoEnviadosNoPatch() {
        UUID organizerId = UUID.randomUUID();
        TicketLot lot = TicketLot.builder()
                .id(UUID.randomUUID())
                .event(event(UUID.randomUUID(), organizerId))
                .name("Nome original")
                .unitPrice(BigDecimal.TEN)
                .capacity(10)
                .sold(1)
                .reserved(2)
                .salesStart(Instant.now())
                .salesEnd(Instant.now().plusSeconds(3600))
                .status(LotStatus.ACTIVE)
                .build();

        when(ticketLotRepository.findByIdForUpdate(lot.getId()))
                .thenReturn(Optional.of(lot));
        when(ticketLotRepository.save(any(TicketLot.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ticketLotService.update(
                lot.getId(),
                TicketLotUpdateDTO.builder().name("Novo nome").build(),
                organizerId
        );

        assertThat(lot.getName()).isEqualTo("Novo nome");
        assertThat(lot.getUnitPrice()).isEqualByComparingTo(BigDecimal.TEN);
        assertThat(lot.getCapacity()).isEqualTo(10);
        assertThat(lot.getSold()).isEqualTo(1);
        assertThat(lot.getReserved()).isEqualTo(2);
    }

    @Test
    void deveRejeitarPrecoNegativoAoAtualizarLote() {
        UUID organizerId = UUID.randomUUID();
        TicketLot lot = lot(organizerId, 10, 0, 0);

        when(ticketLotRepository.findByIdForUpdate(lot.getId()))
                .thenReturn(Optional.of(lot));

        assertThatThrownBy(() -> ticketLotService.update(
                lot.getId(),
                TicketLotUpdateDTO.builder().unitPrice(BigDecimal.valueOf(-1)).build(),
                organizerId
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("status")
                .isEqualTo(org.springframework.http.HttpStatus.UNPROCESSABLE_CONTENT);
    }

    @Test
    void deveRejeitarCapacidadeZeroAoAtualizarLote() {
        UUID organizerId = UUID.randomUUID();
        TicketLot lot = lot(organizerId, 10, 0, 0);

        when(ticketLotRepository.findByIdForUpdate(lot.getId()))
                .thenReturn(Optional.of(lot));

        assertThatThrownBy(() -> ticketLotService.update(
                lot.getId(),
                TicketLotUpdateDTO.builder().capacity(0).build(),
                organizerId
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("status")
                .isEqualTo(org.springframework.http.HttpStatus.UNPROCESSABLE_CONTENT);
    }

    @Test
    void deveAtualizarStatusDoLote() {
        UUID organizerId = UUID.randomUUID();
        TicketLot lot = lot(organizerId, 10, 0, 0);

        when(ticketLotRepository.findByIdForUpdate(lot.getId()))
                .thenReturn(Optional.of(lot));
        when(ticketLotRepository.save(any(TicketLot.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TicketLotResponseDTO response = ticketLotService.update(
                lot.getId(),
                TicketLotUpdateDTO.builder().status(LotStatus.INACTIVE).build(),
                organizerId
        );

        assertThat(response.getStatus()).isEqualTo(LotStatus.INACTIVE);
        assertThat(lot.getStatus()).isEqualTo(LotStatus.INACTIVE);
    }

    @Test
    void deveRejeitarCriacaoComPrecoNegativo() {
        UUID organizerId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        Event event = event(eventId, organizerId);

        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> ticketLotService.create(
                eventId,
                TicketLotRequestDTO.builder()
                        .name("Lote")
                        .unitPrice(BigDecimal.valueOf(-1))
                        .capacity(10)
                        .salesStart(Instant.parse("2026-10-20T00:00:00Z"))
                        .salesEnd(Instant.parse("2026-11-20T18:00:00Z"))
                        .build(),
                organizerId
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("status")
                .isEqualTo(org.springframework.http.HttpStatus.UNPROCESSABLE_CONTENT);

        verify(ticketLotRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void deveRejeitarCriacaoComCapacidadeZero() {
        UUID organizerId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        Event event = event(eventId, organizerId);

        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> ticketLotService.create(
                eventId,
                TicketLotRequestDTO.builder()
                        .name("Lote")
                        .unitPrice(BigDecimal.TEN)
                        .capacity(0)
                        .salesStart(Instant.parse("2026-10-20T00:00:00Z"))
                        .salesEnd(Instant.parse("2026-11-20T18:00:00Z"))
                        .build(),
                organizerId
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("status")
                .isEqualTo(org.springframework.http.HttpStatus.UNPROCESSABLE_CONTENT);
    }

    @Test
    void deveRejeitarJanelaDeVendasInvalida() {
        UUID organizerId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        Event event = event(eventId, organizerId);

        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> ticketLotService.create(
                eventId,
                TicketLotRequestDTO.builder()
                        .name("Lote")
                        .unitPrice(BigDecimal.TEN)
                        .capacity(10)
                        .salesStart(Instant.parse("2026-11-20T00:00:00Z"))
                        .salesEnd(Instant.parse("2026-10-20T00:00:00Z"))
                        .build(),
                organizerId
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("status")
                .isEqualTo(org.springframework.http.HttpStatus.UNPROCESSABLE_CONTENT);
    }

    @Test
    void deveRetornar404QuandoEventoNaoExiste() {
        UUID organizerId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        when(eventRepository.findById(eventId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ticketLotService.create(eventId, request(), organizerId))
                .isInstanceOf(BusinessException.class)
                .extracting("status")
                .isEqualTo(org.springframework.http.HttpStatus.NOT_FOUND);
    }

    @Test
    void deveRetornar403QuandoOrganizadorNaoEDonoDoEventoNaCriacao() {
        UUID ownerId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        Event event = event(eventId, ownerId);

        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> ticketLotService.create(eventId, request(), requesterId))
                .isInstanceOf(BusinessException.class)
                .extracting("status")
                .isEqualTo(org.springframework.http.HttpStatus.FORBIDDEN);
    }

    private TicketLot lot(UUID organizerId, int capacity, int sold, int reserved) {
        return TicketLot.builder()
                .id(UUID.randomUUID())
                .event(event(UUID.randomUUID(), organizerId))
                .name("Lote")
                .unitPrice(BigDecimal.TEN)
                .capacity(capacity)
                .sold(sold)
                .reserved(reserved)
                .salesStart(Instant.now())
                .salesEnd(Instant.now().plusSeconds(3600))
                .status(LotStatus.ACTIVE)
                .build();
    }

    private TicketLotRequestDTO request() {
        return TicketLotRequestDTO.builder()
                .name("Lote promocional")
                .unitPrice(BigDecimal.valueOf(50))
                .capacity(100)
                .salesStart(Instant.parse("2026-10-20T00:00:00Z"))
                .salesEnd(Instant.parse("2026-11-20T18:00:00Z"))
                .build();
    }

    private Event event(UUID eventId, UUID organizerId) {
        return Event.builder()
                .id(eventId)
                .organizer(User.builder().id(organizerId).build())
                .title("Evento")
                .startsAt(java.time.OffsetDateTime.now().plusDays(1))
                .endsAt(java.time.OffsetDateTime.now().plusDays(1).plusHours(2))
                .location("Local")
                .status(EventStatus.DRAFT)
                .build();
    }
}
