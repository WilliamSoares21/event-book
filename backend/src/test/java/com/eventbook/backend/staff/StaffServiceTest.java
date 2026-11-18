package com.eventbook.backend.staff;

import com.eventbook.backend.common.exception.BusinessException;
import com.eventbook.backend.events.Event;
import com.eventbook.backend.events.EventRepository;
import com.eventbook.backend.events.EventStatus;
import com.eventbook.backend.staff.dto.AddEventStaffRequest;
import com.eventbook.backend.staff.dto.EventStaffResponseDTO;
import com.eventbook.backend.users.Role;
import com.eventbook.backend.users.User;
import com.eventbook.backend.users.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StaffServiceTest {

    @Mock
    private EventStaffRepository eventStaffRepository;

    @Mock
    private EventRepository eventRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private StaffService staffService;

    private UUID eventId;
    private UUID organizerId;
    private UUID staffUserId;
    private Event event;
    private User organizer;
    private User staffUser;
    private AddEventStaffRequest request;

    @BeforeEach
    void setUp() {
        eventId = UUID.randomUUID();
        organizerId = UUID.randomUUID();
        staffUserId = UUID.randomUUID();

        organizer = User.builder()
                .id(organizerId)
                .name("Organizer")
                .email("organizer@test.com")
                .role(Role.ORGANIZADOR)
                .build();

        staffUser = User.builder()
                .id(staffUserId)
                .name("Staff User")
                .email("staff@test.com")
                .role(Role.STAFF)
                .build();

        event = Event.builder()
                .id(eventId)
                .organizer(organizer)
                .title("Test Event")
                .startsAt(OffsetDateTime.now().plusDays(10))
                .endsAt(OffsetDateTime.now().plusDays(10).plusHours(2))
                .location("São Paulo")
                .status(EventStatus.DRAFT)
                .build();

        request = AddEventStaffRequest.builder()
                .userId(staffUserId)
                .build();
    }

    @Test
    void deveAssociarStaffComSucesso() {
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(userRepository.findById(staffUserId)).thenReturn(Optional.of(staffUser));
        when(eventStaffRepository.saveAndFlush(any(EventStaff.class)))
                .thenAnswer(invocation -> {
                    EventStaff es = invocation.getArgument(0);
                    return EventStaff.builder()
                            .id(UUID.randomUUID())
                            .event(es.getEvent())
                            .user(es.getUser())
                            .createdAt(es.getCreatedAt())
                            .build();
                });

        EventStaffResponseDTO response = staffService.addStaffToEvent(eventId, request, organizerId);

        assertThat(response).isNotNull();
        assertThat(response.getEventId()).isEqualTo(eventId);
        assertThat(response.getUserId()).isEqualTo(staffUserId);
        assertThat(response.getUserName()).isEqualTo("Staff User");
        assertThat(response.getUserEmail()).isEqualTo("staff@test.com");

        verify(eventRepository).findById(eventId);
        verify(userRepository).findById(staffUserId);
        verify(eventStaffRepository).saveAndFlush(any(EventStaff.class));
    }

    @Test
    void deveRetornar404QuandoEventoNaoExiste() {
        when(eventRepository.findById(eventId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> staffService.addStaffToEvent(eventId, request, organizerId))
                .isInstanceOf(BusinessException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.NOT_FOUND);

        verify(eventRepository).findById(eventId);
        verify(userRepository, never()).findById(any());
        verify(eventStaffRepository, never()).save(any());
    }

    @Test
    void deveRetornar403QuandoOrganizadorNaoEProprietario() {
        UUID anotherOrganizerId = UUID.randomUUID();
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> staffService.addStaffToEvent(eventId, request, anotherOrganizerId))
                .isInstanceOf(BusinessException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.FORBIDDEN);

        verify(eventRepository).findById(eventId);
        verify(userRepository, never()).findById(any());
        verify(eventStaffRepository, never()).save(any());
    }

    @Test
    void deveRetornar404QuandoUsuarioNaoExiste() {
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(userRepository.findById(staffUserId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> staffService.addStaffToEvent(eventId, request, organizerId))
                .isInstanceOf(BusinessException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.NOT_FOUND);

        verify(eventRepository).findById(eventId);
        verify(userRepository).findById(staffUserId);
        verify(eventStaffRepository, never()).save(any());
    }

    @Test
    void deveRetornar422QuandoUsuarioNaoTemRoleStaff() {
        User buyer = User.builder()
                .id(staffUserId)
                .name("Buyer")
                .email("buyer@test.com")
                .role(Role.COMPRADOR)
                .build();

        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(userRepository.findById(staffUserId)).thenReturn(Optional.of(buyer));

        assertThatThrownBy(() -> staffService.addStaffToEvent(eventId, request, organizerId))
                .isInstanceOf(BusinessException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

        verify(eventRepository).findById(eventId);
        verify(userRepository).findById(staffUserId);
        verify(eventStaffRepository, never()).save(any());
    }

    @Test
    void deveRetornar409QuandoAssociacaoDuplicada() {
        when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        when(userRepository.findById(staffUserId)).thenReturn(Optional.of(staffUser));
        when(eventStaffRepository.saveAndFlush(any(EventStaff.class)))
                .thenThrow(new DataIntegrityViolationException("Duplicate key"));

        assertThatThrownBy(() -> staffService.addStaffToEvent(eventId, request, organizerId))
                .isInstanceOf(BusinessException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.CONFLICT);

        verify(eventRepository).findById(eventId);
        verify(userRepository).findById(staffUserId);
        verify(eventStaffRepository).saveAndFlush(any(EventStaff.class));
    }
}