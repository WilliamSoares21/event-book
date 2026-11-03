package com.eventbook.backend.events;

import com.eventbook.backend.checkin.CheckInRepository;
import com.eventbook.backend.common.exception.BusinessException;
import com.eventbook.backend.events.dto.EventOccupancyDTO;
import com.eventbook.backend.events.dto.EventRequestDTO;
import com.eventbook.backend.events.dto.EventResponseDTO;
import com.eventbook.backend.events.dto.EventUpdateDTO;
import com.eventbook.backend.lots.LotStatus;
import com.eventbook.backend.lots.TicketLotRepository;
import com.eventbook.backend.orders.OrderRepository;
import com.eventbook.backend.orders.OrderStatus;
import com.eventbook.backend.tickets.TicketRepository;
import com.eventbook.backend.users.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EventService {

    private final EventRepository eventRepository;
    private final TicketLotRepository ticketLotRepository;
    private final OrderRepository orderRepository;
    private final TicketRepository ticketRepository;
    private final CheckInRepository checkInRepository;
    private final UserRepository userRepository;

    // Cria um novo evento salvando-o no banco de dados
    @Transactional
    public EventResponseDTO create(EventRequestDTO dto, UUID organizerId) {
        // Valida o intervalo de datas antes de criar a entidade.
        validateDateRange(dto.getStartsAt(), dto.getEndsAt());

        // Obtém uma referência ao organizador sem precisar carregá-lo imediatamente.
        // O Hibernate consulta o banco caso os dados da entidade sejam realmente necessários.
        var organizer = userRepository.getReferenceById(organizerId);

        Event event = Event.builder()
                .title(dto.getTitle().trim())
                .description(normalizeOptionalText(dto.getDescription()))
                .startsAt(dto.getStartsAt())
                .endsAt(dto.getEndsAt())
                .location(dto.getLocation().trim())
                .organizer(organizer)
                .status(EventStatus.DRAFT) // Todo evento nasce como rascunho e precisa ser publicado posteriormente.
                .build();

        return toResponseDTO(eventRepository.save(event));
    }

    // Busca um evento específico pelo ID
    @Transactional(readOnly = true)
    public EventResponseDTO getById(UUID eventId, UUID requesterId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new BusinessException(
                        "Evento não encontrado",
                        HttpStatus.NOT_FOUND
                ));

        // Eventos não publicados só podem ser visualizados pelo próprio organizador.
        // Retornar NOT_FOUND também evita revelar a existência de eventos privados.
        if (event.getStatus() != EventStatus.PUBLISHED
                && !event.getOrganizer().getId().equals(requesterId)) {
            throw new BusinessException(
                    "Evento não encontrado",
                    HttpStatus.NOT_FOUND
            );
        }

        return toResponseDTO(event);
    }

    // Lista todos os eventos cadastrados
    @Transactional(readOnly = true)
    public List<EventResponseDTO> findAllPublished() {
        // A consulta já filtra os eventos no banco, evitando carregar rascunhos desnecessariamente.
        return eventRepository.findByStatus(EventStatus.PUBLISHED)
                .stream()
                .map(this::toResponseDTO)
                .toList();
    }

    @Transactional
    public EventResponseDTO update(
            UUID eventId,
            EventUpdateDTO dto,
            UUID requesterId) {

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new BusinessException(
                        "Evento não encontrado",
                        HttpStatus.NOT_FOUND
                ));

        // Garante que somente o organizador proprietário possa modificar o evento.
        if (!event.getOrganizer().getId().equals(requesterId)) {
            throw new BusinessException(
                    "Somente o organizador proprietário pode atualizar o evento",
                    HttpStatus.FORBIDDEN
            );
        }

        // Decisão arquitetural: eventos publicados não podem ser editados.
        // Isso evita alterações inesperadas nas informações de eventos já disponibilizados ao público.
        if (event.getStatus() != EventStatus.DRAFT) {
            throw new BusinessException(
                    "Somente eventos em DRAFT podem ser alterados",
                    HttpStatus.UNPROCESSABLE_CONTENT
            );
        }

        // Atualização parcial: somente os campos enviados no DTO são modificados.
        if (dto.getTitle() != null) {
            if (dto.getTitle().isBlank()) {
                throw new BusinessException(
                        "O título não pode ficar vazio",
                        HttpStatus.UNPROCESSABLE_CONTENT
                );
            }
            event.setTitle(dto.getTitle().trim());
        }

        if (dto.getDescription() != null) {
            event.setDescription(normalizeOptionalText(dto.getDescription()));
        }

        if (dto.getLocation() != null) {
            if (dto.getLocation().isBlank()) {
                throw new BusinessException(
                        "A localização não pode ficar vazia",
                        HttpStatus.UNPROCESSABLE_CONTENT
                );
            }
            event.setLocation(dto.getLocation().trim());
        }

        // Combina os valores recebidos com os já existentes para validar
        // o intervalo completo, mesmo quando apenas uma das datas é alterada.
        OffsetDateTime startsAt = dto.getStartsAt() != null
                ? dto.getStartsAt()
                : event.getStartsAt();

        OffsetDateTime endsAt = dto.getEndsAt() != null
                ? dto.getEndsAt()
                : event.getEndsAt();

        validateDateRange(startsAt, endsAt);

        if (dto.getStartsAt() != null) {
            event.setStartsAt(dto.getStartsAt());
        }

        if (dto.getEndsAt() != null) {
            event.setEndsAt(dto.getEndsAt());
        }

        try {
            return toResponseDTO(eventRepository.save(event));
        } catch (ObjectOptimisticLockingFailureException ex) {
            // O controle de versão (@Version) detecta alterações concorrentes.
            // Se outro usuário salvou uma versão mais recente, evitamos sobrescrever seus dados.
            throw new BusinessException(
                    "O evento foi alterado por outro usuário",
                    HttpStatus.CONFLICT
            );
        }
    }

    @Transactional
    public void publish(UUID eventId, UUID requesterId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Evento não encontrado"
                ));

        // Apenas o organizador responsável pelo evento pode publicá-lo.
        if (!event.getOrganizer().getId().equals(requesterId)) {
            throw new BusinessException(
                    "Somente o organizador pode publicar",
                    HttpStatus.FORBIDDEN
            );
        }

        // Impede a publicação de eventos que já começaram ou cuja data de início não é futura.
        if (!event.getStartsAt().isAfter(OffsetDateTime.now())) {
            throw new BusinessException(
                    "Data de início deve ser futura",
                    HttpStatus.UNPROCESSABLE_CONTENT
            );
        }

        // Um evento precisa ter pelo menos um lote ativo para disponibilizar ingressos.
        if (!ticketLotRepository.existsByEventIdAndStatus(
                eventId,
                LotStatus.ACTIVE)) {
            throw new BusinessException(
                    "Evento precisa ter ao menos um lote ativo",
                    HttpStatus.UNPROCESSABLE_CONTENT
            );
        }

        event.setStatus(EventStatus.PUBLISHED);
        eventRepository.save(event);
    }

    @Transactional(readOnly = true)
    public EventOccupancyDTO getOccupancy(UUID eventId) {
        // Verifica a existência do evento antes de consultar seus indicadores.
        eventRepository.findById(eventId)
                .orElseThrow(() -> new BusinessException(
                        "Evento não encontrado",
                        HttpStatus.NOT_FOUND
                ));

        // Capacidade total disponibilizada pelos lotes do evento.
        Long capacity = ticketLotRepository.sumCapacityByEvent(eventId);

        // Pedidos pendentes representam ingressos temporariamente reservados,
        // mas que ainda não foram efetivamente vendidos.
        Long reserved = orderRepository.countByEventIdAndStatus(
                eventId,
                OrderStatus.PENDING
        );

        // Quantidade de ingressos já emitidos/vendidos.
        Long sold = ticketRepository.countByEventId(eventId);

        // Quantidade de participantes que já realizaram o check-in.
        Long checkedIn = checkInRepository.countByEventId(eventId);

        // Calcula a capacidade restante descontando reservas e ingressos vendidos.
        // O Math.max impede que a disponibilidade seja apresentada como negativa.
        Long available = capacity - reserved - sold;

        return EventOccupancyDTO.builder()
                .eventId(eventId)
                .capacity(capacity)
                .reserved(reserved)
                .sold(sold)
                .checkedIn(checkedIn)
                .available(Math.max(available, 0L))
                .build();
    }

    private void validateDateRange(
            OffsetDateTime startsAt,
            OffsetDateTime endsAt) {

        // Permite que a validação seja reutilizada em cenários
        // nos quais uma das datas ainda não foi informada.
        if (startsAt == null || endsAt == null) {
            return;
        }

        // A data de término precisa ser estritamente posterior à de início.
        if (!endsAt.isAfter(startsAt)) {
            throw new BusinessException(
                    "endsAt deve ser posterior a startsAt",
                    HttpStatus.UNPROCESSABLE_CONTENT
            );
        }
    }

    private String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }

        // Remove espaços desnecessários e transforma textos vazios em null.
        // Isso evita armazenar descrições contendo apenas espaços em branco.
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private EventResponseDTO toResponseDTO(Event event) {
        // Converte a entidade para DTO, evitando expor diretamente
        // os detalhes internos da entidade JPA na resposta da API.
        return EventResponseDTO.builder()
                .id(event.getId())
                .title(event.getTitle())
                .description(event.getDescription())
                .startsAt(event.getStartsAt())
                .endsAt(event.getEndsAt())
                .location(event.getLocation())
                .organizerId(event.getOrganizer().getId())
                .status(event.getStatus())
                .version(event.getVersion())
                .build();
    }
}
