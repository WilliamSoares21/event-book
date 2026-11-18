package com.eventbook.backend.events;

import com.eventbook.backend.security.AuthenticatedUser;
import com.eventbook.backend.security.CustomUserDetailsService;
import com.eventbook.backend.security.JwtService;
import com.eventbook.backend.security.SecurityConfig;
import com.eventbook.backend.staff.StaffService;
import com.eventbook.backend.staff.dto.EventStaffResponseDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifica a autorização do endpoint de associação de staff no nível do Spring Security.
 * Cobre 201/403/401 sem depender de banco de dados.
 */
@WebMvcTest(controllers = EventController.class)
@Import(SecurityConfig.class)
@EnableWebSecurity
class EventStaffAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StaffService staffService;

    @MockitoBean
    private EventService eventService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private CustomUserDetailsService userDetailsService;

    private RequestPostProcessor principal(String role) {
        UUID id = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(
                id,
                "user@email.com",
                "User",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)),
                0
        );
        return authentication(new UsernamePasswordAuthenticationToken(
                user, null, user.getAuthorities()));
    }

    @Test
    void organizadorRecebe201() throws Exception {
        UUID organizerId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();

        AuthenticatedUser user = new AuthenticatedUser(
                organizerId,
                "org@email.com",
                "Org",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_ORGANIZADOR")),
                0
        );

        EventStaffResponseDTO response = EventStaffResponseDTO.builder()
                .id(UUID.randomUUID())
                .eventId(eventId)
                .userId(staffId)
                .userName("Staff")
                .userEmail("staff@test.com")
                .createdAt(Instant.now())
                .build();

        when(staffService.addStaffToEvent(eq(eventId), any(), eq(organizerId)))
                .thenReturn(response);

        mockMvc.perform(post("/api/events/{eventId}/staff", eventId)
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                user, null, user.getAuthorities())))
                        .contentType("application/json")
                        .content("""
                                {
                                  "userId": "%s"
                                }
                                """.formatted(staffId)))
                .andExpect(status().isCreated());
    }

    @Test
    void compradorRecebe403() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();

        mockMvc.perform(post("/api/events/{eventId}/staff", eventId)
                        .with(principal("COMPRADOR"))
                        .contentType("application/json")
                        .content("""
                                {
                                  "userId": "%s"
                                }
                                """.formatted(staffId)))
                .andExpect(status().isForbidden());
    }

    @Test
    void staffRecebe403() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();

        mockMvc.perform(post("/api/events/{eventId}/staff", eventId)
                        .with(principal("STAFF"))
                        .contentType("application/json")
                        .content("""
                                {
                                  "userId": "%s"
                                }
                                """.formatted(staffId)))
                .andExpect(status().isForbidden());
    }

    @Test
    void semAutenticacaoRecebe401() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID staffId = UUID.randomUUID();

        mockMvc.perform(post("/api/events/{eventId}/staff", eventId)
                        .contentType("application/json")
                        .content("""
                                {
                                  "userId": "%s"
                                }
                                """.formatted(staffId)))
                .andExpect(status().isUnauthorized());
    }
}