package com.eventbook.backend.events;

import com.eventbook.backend.common.exception.ApiExceptionHandler;
import com.eventbook.backend.common.exception.BusinessException;
import com.eventbook.backend.events.dto.EventResponseDTO;
import com.eventbook.backend.events.dto.EventUpdateDTO;
import com.eventbook.backend.events.dto.EventRequestDTO;
import com.eventbook.backend.security.AuthenticatedUser;
import org.junit.jupiter.api.AfterEach;
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

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class EventControllerTest {

    @Mock
    private EventService eventService;

    @InjectMocks
    private EventController eventController;

    private MockMvc mockMvc;

    private UUID organizerId;

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

        AuthenticatedUser authenticatedUser = new AuthenticatedUser(
                organizerId,
                "organizer@email.com",
                "Organizer",
                null,
                java.util.List.of(
                        new SimpleGrantedAuthority("ROLE_ORGANIZADOR")
                )
        );

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        authenticatedUser,
                        null,
                        authenticatedUser.getAuthorities()
                )
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void deveRetornar201AoCriarEvento() throws Exception {
        UUID eventId = UUID.randomUUID();

        EventResponseDTO response = EventResponseDTO.builder()
                .id(eventId)
                .title("Festival EventBook")
                .description("Evento de demonstração")
                .startsAt(OffsetDateTime.parse("2026-11-20T19:00:00-03:00"))
                .endsAt(OffsetDateTime.parse("2026-11-20T23:00:00-03:00"))
                .location("São Paulo")
                .organizerId(organizerId)
                .status(EventStatus.DRAFT)
                .version(0L)
                .build();

        when(eventService.create(any(EventRequestDTO.class), eq(organizerId)))
                .thenReturn(response);

        mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Festival EventBook",
                                  "description": "Evento de demonstração",
                                  "startsAt": "2026-11-20T19:00:00-03:00",
                                  "endsAt": "2026-11-20T23:00:00-03:00",
                                  "location": "São Paulo"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string(
                        "Location",
                        "http://localhost/api/events/" + eventId
                ))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(eventId.toString()))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.organizerId").value(organizerId.toString()))
                .andExpect(jsonPath("$.location").value("São Paulo"));

        verify(eventService).create(any(EventRequestDTO.class), eq(organizerId));
    }

    @Test
    void deveRetornar400QuandoPayloadDeCriacaoForInvalido() throws Exception {
        mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "",
                                  "startsAt": null,
                                  "endsAt": null,
                                  "location": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fields").exists());
    }

    @Test
    void deveRetornar200AoListarEventosPublicados() throws Exception {
        when(eventService.findAllPublished())
                .thenReturn(java.util.List.of(
                        EventResponseDTO.builder()
                                .id(UUID.randomUUID())
                                .title("Evento publicado")
                                .startsAt(OffsetDateTime.parse(
                                        "2026-11-20T19:00:00-03:00"
                                ))
                                .endsAt(OffsetDateTime.parse(
                                        "2026-11-20T23:00:00-03:00"
                                ))
                                .location("São Paulo")
                                .status(EventStatus.PUBLISHED)
                                .build()
                ));

        mockMvc.perform(get("/api/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("PUBLISHED"))
                .andExpect(jsonPath("$[0].title").value("Evento publicado"));

        verify(eventService).findAllPublished();
    }

    @Test
    void deveRetornar200AoConsultarEvento() throws Exception {
        UUID eventId = UUID.randomUUID();

        EventResponseDTO response = EventResponseDTO.builder()
                .id(eventId)
                .title("Festival EventBook")
                .startsAt(OffsetDateTime.parse("2026-11-20T19:00:00-03:00"))
                .endsAt(OffsetDateTime.parse("2026-11-20T23:00:00-03:00"))
                .location("São Paulo")
                .organizerId(organizerId)
                .status(EventStatus.PUBLISHED)
                .build();

        when(eventService.getById(eventId, organizerId))
                .thenReturn(response);

        mockMvc.perform(get("/api/events/{eventId}", eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(eventId.toString()))
                .andExpect(jsonPath("$.status").value("PUBLISHED"));

        verify(eventService).getById(eventId, organizerId);
    }

    @Test
    void deveRetornar400QuandoUuidDoEventoForInvalido() throws Exception {
        mockMvc.perform(get("/api/events/not-a-uuid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deveRetornar200AoAtualizarEvento() throws Exception {
        UUID eventId = UUID.randomUUID();

        EventResponseDTO response = EventResponseDTO.builder()
                .id(eventId)
                .title("Festival Atualizado")
                .startsAt(OffsetDateTime.parse("2026-11-20T19:00:00-03:00"))
                .endsAt(OffsetDateTime.parse("2026-11-20T23:00:00-03:00"))
                .location("São Paulo")
                .organizerId(organizerId)
                .status(EventStatus.DRAFT)
                .version(1L)
                .build();

        when(eventService.update(
                eq(eventId),
                any(EventUpdateDTO.class),
                eq(organizerId)
        )).thenReturn(response);

        mockMvc.perform(patch("/api/events/{eventId}", eventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Festival Atualizado"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Festival Atualizado"))
                .andExpect(jsonPath("$.status").value("DRAFT"));

        verify(eventService).update(
                eq(eventId),
                any(EventUpdateDTO.class),
                eq(organizerId)
        );
    }

    @Test
    void deveRetornar204AoPublicarEvento() throws Exception {
        UUID eventId = UUID.randomUUID();

        mockMvc.perform(post("/api/events/{eventId}/publication", eventId))
                .andExpect(status().isNoContent());

        verify(eventService).publish(eq(eventId), eq(organizerId));
    }

    @Test
    void deveRetornar422QuandoPublicarEventoSemLoteAtivo() throws Exception {
        UUID eventId = UUID.randomUUID();

        doThrow(new BusinessException(
                "Evento precisa ter ao menos um lote ativo",
                HttpStatus.UNPROCESSABLE_CONTENT
        )).when(eventService).publish(eq(eventId), eq(organizerId));
        mockMvc.perform(post("/api/events/{eventId}/publication", eventId))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("Evento precisa ter ao menos um lote ativo"));

        verify(eventService).publish(eq(eventId), eq(organizerId));
    }

    @Test
    void deveRetornar422QuandoPublicarEventoComDataPassada() throws Exception {
        UUID eventId = UUID.randomUUID();

        doThrow(new BusinessException(
                "Data de início deve ser futura",
                HttpStatus.UNPROCESSABLE_CONTENT
        )).when(eventService).publish(eq(eventId), eq(organizerId));

        mockMvc.perform(post("/api/events/{eventId}/publication", eventId))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("Data de início deve ser futura"));

        verify(eventService).publish(eq(eventId), eq(organizerId));
    }
}