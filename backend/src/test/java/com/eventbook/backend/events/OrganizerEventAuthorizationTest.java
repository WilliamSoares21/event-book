package com.eventbook.backend.events;

import com.eventbook.backend.events.dto.OrganizerEventResponseDTO;
import com.eventbook.backend.security.AuthenticatedUser;
import com.eventbook.backend.security.CustomUserDetailsService;
import com.eventbook.backend.security.JwtService;
import com.eventbook.backend.security.SecurityConfig;
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

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifica a autorização do endpoint administrativo no nível do Spring Security.
 * Cobre 200/403/401 sem depender de banco de dados.
 */
@WebMvcTest(controllers = OrganizerEventController.class)
@Import(SecurityConfig.class)
@EnableWebSecurity
class OrganizerEventAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

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
    void organizadorRecebe200() throws Exception {
        UUID organizerId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(
                organizerId,
                "org@email.com",
                "Org",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_ORGANIZADOR")),
                0
        );

        when(eventService.findByOrganizer(eq(organizerId)))
                .thenReturn(List.of(OrganizerEventResponseDTO.builder()
                        .id(UUID.randomUUID())
                        .title("Evento")
                        .status(EventStatus.DRAFT)
                        .ticketLots(List.of())
                        .build()));

        mockMvc.perform(get("/api/organizer/events")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                user, null, user.getAuthorities()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Evento"))
                .andExpect(jsonPath("$[0].ticketLots.length()").value(0));
    }

    @Test
    void compradorRecebe403() throws Exception {
        mockMvc.perform(get("/api/organizer/events").with(principal("COMPRADOR")))
                .andExpect(status().isForbidden());
    }

    @Test
    void staffRecebe403() throws Exception {
        mockMvc.perform(get("/api/organizer/events").with(principal("STAFF")))
                .andExpect(status().isForbidden());
    }

    @Test
    void semAutenticacaoRecebe401() throws Exception {
        mockMvc.perform(get("/api/organizer/events"))
                .andExpect(status().isUnauthorized());
    }
}
