package com.eventbook.backend.events;

import com.eventbook.backend.common.exception.ApiExceptionHandler;
import com.eventbook.backend.events.dto.EventResponseDTO;
import com.eventbook.backend.security.CustomUserDetailsService;
import com.eventbook.backend.security.AuthenticatedUser;
import com.eventbook.backend.security.JwtAuthenticationFilter;
import com.eventbook.backend.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EventController.class)
@Import({SecurityConfig.class, ApiExceptionHandler.class})
class EventSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EventService eventService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @Test
    void deveRetornar401AoCriarEventoSemAutenticacao() throws Exception {
        mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPayload()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deveRetornar403AoCriarEventoComComprador() throws Exception {
        mockMvc.perform(post("/api/events")
                        .with(user("buyer").roles("COMPRADOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPayload()))
                .andExpect(status().isForbidden());
    }

    @Test
    void devePermitirCriacaoParaOrganizadorAutenticado() throws Exception {
        UUID organizerId = UUID.randomUUID();
        EventResponseDTO response = EventResponseDTO.builder()
                .id(UUID.randomUUID())
                .organizerId(organizerId)
                .title("Festival")
                .startsAt(OffsetDateTime.parse("2026-11-20T19:00:00-03:00"))
                .endsAt(OffsetDateTime.parse("2026-11-20T23:00:00-03:00"))
                .location("São Paulo")
                .status(EventStatus.DRAFT)
                .build();

        when(eventService.create(any(), eq(organizerId)))
                .thenReturn(response);

        AuthenticatedUser principal = new AuthenticatedUser(
                organizerId,
                "organizer@example.com",
                "Organizer",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_ORGANIZADOR"))
        );

        mockMvc.perform(post("/api/events")
                        .with(authentication(
                                new UsernamePasswordAuthenticationToken(
                                        principal,
                                        null,
                                        principal.getAuthorities()
                                )
                        ))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPayload()))
                .andExpect(status().isCreated());
    }

    private String validPayload() {
        return """
                {
                  "title": "Festival EventBook",
                  "description": "Evento de demonstração",
                  "startsAt": "2026-11-20T19:00:00-03:00",
                  "endsAt": "2026-11-20T23:00:00-03:00",
                  "location": "São Paulo"
                }
                """;
    }
}
