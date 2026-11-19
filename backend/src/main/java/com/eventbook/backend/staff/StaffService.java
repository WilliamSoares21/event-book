package com.eventbook.backend.staff;

import com.eventbook.backend.common.exception.BusinessException;
import com.eventbook.backend.events.Event;
import com.eventbook.backend.events.EventRepository;
import com.eventbook.backend.staff.dto.AddEventStaffRequest;
import com.eventbook.backend.staff.dto.EventStaffResponseDTO;
import com.eventbook.backend.users.User;
import com.eventbook.backend.users.UserRepository;
import com.eventbook.backend.users.Role;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StaffService {

    private final EventStaffRepository eventStaffRepository;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final EntityManager entityManager;

    @Transactional
    public EventStaffResponseDTO addStaffToEvent(UUID eventId, AddEventStaffRequest request, UUID organizerId) {
        // 1. Verifica se o evento existe
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new BusinessException("Evento não encontrado", HttpStatus.NOT_FOUND));

        // 2. Verifica se o organizador autenticado é o proprietário do evento
        if (!event.getOrganizer().getId().equals(organizerId)) {
            throw new BusinessException("Somente o organizador proprietário pode associar staff ao evento", HttpStatus.FORBIDDEN);
        }

        // 3. Verifica se o usuário indicado existe
        UUID userId = request.getUserId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("Usuário não encontrado", HttpStatus.NOT_FOUND));

        // 4. Verifica se o usuário possui role STAFF
        if (user.getRole() != Role.STAFF) {
            throw new BusinessException("O usuário indicado deve possuir role STAFF", HttpStatus.UNPROCESSABLE_ENTITY);
        }

        // 5. Cria a associação (a unicidade é garantida pela constraint do banco)
        EventStaff eventStaff = EventStaff.builder()
                .event(event)
                .user(user)
                .build();

        try {
            eventStaff = eventStaffRepository.saveAndFlush(eventStaff);
        } catch (DataIntegrityViolationException ex) {
            // Constraint única violada: associação duplicada
            throw new BusinessException("Usuário já associado a este evento", HttpStatus.CONFLICT);
        }

        return toResponseDTO(eventStaff);
    }

    private EventStaffResponseDTO toResponseDTO(EventStaff eventStaff) {
        return EventStaffResponseDTO.builder()
                .id(eventStaff.getId())
                .eventId(eventStaff.getEvent().getId())
                .userId(eventStaff.getUser().getId())
                .userName(eventStaff.getUser().getName())
                .userEmail(eventStaff.getUser().getEmail())
                .createdAt(eventStaff.getCreatedAt())
                .build();
    }
}