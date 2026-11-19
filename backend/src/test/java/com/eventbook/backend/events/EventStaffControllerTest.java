package com.eventbook.backend.events;

import com.eventbook.backend.common.exception.ApiExceptionHandler;
import com.eventbook.backend.common.exception.BusinessException;
import com.eventbook.backend.security.AuthenticatedUser;
import com.eventbook.backend.staff.StaffService;
import com.eventbook.backend.staff.dto.AddEventStaffRequest;
import com.eventbook.backend.staff.dto.EventStaffResponseDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class EventStaffControllerTest {

    @Mock
    private StaffService staffService;

    @InjectMocks
    private EventController eventController;

    private MockMvc mockMvc;
    private UUID organizerId;
    private UUID eventId;
    private UUID staffId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(eventController)
                .setControllerAdvice(new ApiExceptionHandler())
                .setCustomArgumentResolvers(
                        new AuthenticationPrincipalArgumentResolver()
                )
                .build();

        organizerId = UUID.randomUUID();
        eventId = UUID.randomUUID();
        staffId = UUID.randomUUID();

        AuthenticatedUser authenticatedUser = new AuthenticatedUser(
                organizerId,
                "organizer@email.com",
                "Organizer",
                null,
                java.util.List.of(
                        new SimpleGrantedAuthority("ROLE_ORGANIZADOR")
                ),
                0
        );

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        authenticatedUser,
                        null,
                        authenticatedUser.getAuthorities()
                )
        );
    }

    @Test
    void deveRetornar201AoAssociarStaff() throws Exception {
        EventStaffResponseDTO response = EventStaffResponseDTO.builder()
                .id(UUID.randomUUID())
                .eventId(eventId)
                .userId(staffId)
                .userName("Staff User")
                .userEmail("staff@test.com")
                .createdAt(Instant.now())
                .build();

        when(staffService.addStaffToEvent(eq(eventId), any(AddEventStaffRequest.class), eq(organizerId)))
                .thenReturn(response);

        mockMvc.perform(post("/api/events/{eventId}/staff", eventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "%s"
                                }
                                """.formatted(staffId)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.eventId").value(eventId.toString()))
                .andExpect(jsonPath("$.userId").value(staffId.toString()))
                .andExpect(jsonPath("$.userName").value("Staff User"))
                .andExpect(jsonPath("$.userEmail").value("staff@test.com"));

        verify(staffService).addStaffToEvent(eq(eventId), any(AddEventStaffRequest.class), eq(organizerId));
    }

    @Test
    void deveRetornar400QuandoPayloadInvalido() throws Exception {
        mockMvc.perform(post("/api/events/{eventId}/staff", eventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": null
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fields.userId").exists());
    }

    @Test
    void deveRetornar400QuandoUserIdNaoEUuid() throws Exception {
        mockMvc.perform(post("/api/events/{eventId}/staff", eventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "not-a-uuid"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deveRetornar404QuandoEventoNaoExiste() throws Exception {
        UUID nonExistentEventId = UUID.randomUUID();

        doThrow(new BusinessException("Evento não encontrado", HttpStatus.NOT_FOUND))
                .when(staffService).addStaffToEvent(eq(nonExistentEventId), any(AddEventStaffRequest.class), eq(organizerId));

        mockMvc.perform(post("/api/events/{eventId}/staff", nonExistentEventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "%s"
                                }
                                """.formatted(staffId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Evento não encontrado"));

        verify(staffService).addStaffToEvent(eq(nonExistentEventId), any(AddEventStaffRequest.class), eq(organizerId));
    }

    @Test
    void deveRetornar403QuandoOrganizadorNaoEProprietario() throws Exception {
        doThrow(new BusinessException("Somente o organizador proprietário pode associar staff ao evento", HttpStatus.FORBIDDEN))
                .when(staffService).addStaffToEvent(eq(eventId), any(AddEventStaffRequest.class), eq(organizerId));

        mockMvc.perform(post("/api/events/{eventId}/staff", eventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "%s"
                                }
                                """.formatted(staffId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Somente o organizador proprietário pode associar staff ao evento"));

        verify(staffService).addStaffToEvent(eq(eventId), any(AddEventStaffRequest.class), eq(organizerId));
    }

    @Test
    void deveRetornar404QuandoUsuarioNaoExiste() throws Exception {
        doThrow(new BusinessException("Usuário não encontrado", HttpStatus.NOT_FOUND))
                .when(staffService).addStaffToEvent(eq(eventId), any(AddEventStaffRequest.class), eq(organizerId));

        mockMvc.perform(post("/api/events/{eventId}/staff", eventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "%s"
                                }
                                """.formatted(staffId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Usuário não encontrado"));

        verify(staffService).addStaffToEvent(eq(eventId), any(AddEventStaffRequest.class), eq(organizerId));
    }

    @Test
    void deveRetornar422QuandoUsuarioNaoTemRoleStaff() throws Exception {
        doThrow(new BusinessException("O usuário indicado deve possuir role STAFF", HttpStatus.UNPROCESSABLE_ENTITY))
                .when(staffService).addStaffToEvent(eq(eventId), any(AddEventStaffRequest.class), eq(organizerId));

        mockMvc.perform(post("/api/events/{eventId}/staff", eventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "%s"
                                }
                                """.formatted(staffId)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("O usuário indicado deve possuir role STAFF"));

        verify(staffService).addStaffToEvent(eq(eventId), any(AddEventStaffRequest.class), eq(organizerId));
    }

    @Test
    void deveRetornar409QuandoAssociacaoDuplicada() throws Exception {
        doThrow(new BusinessException("Usuário já associado a este evento", HttpStatus.CONFLICT))
                .when(staffService).addStaffToEvent(eq(eventId), any(AddEventStaffRequest.class), eq(organizerId));

        mockMvc.perform(post("/api/events/{eventId}/staff", eventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": "%s"
                                }
                                """.formatted(staffId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Usuário já associado a este evento"));

        verify(staffService).addStaffToEvent(eq(eventId), any(AddEventStaffRequest.class), eq(organizerId));
    }
}