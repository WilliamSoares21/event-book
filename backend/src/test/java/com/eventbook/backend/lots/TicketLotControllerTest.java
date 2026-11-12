package com.eventbook.backend.lots;

import com.eventbook.backend.common.exception.ApiExceptionHandler;
import com.eventbook.backend.common.exception.BusinessException;
import com.eventbook.backend.lots.dto.TicketLotResponseDTO;
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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class TicketLotControllerTest {

    @Mock
    private TicketLotService ticketLotService;

    @InjectMocks
    private TicketLotController ticketLotController;

    private MockMvc mockMvc;
    private UUID organizerId;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(ticketLotController)
                .setControllerAdvice(new ApiExceptionHandler())
                .setCustomArgumentResolvers(
                        new AuthenticationPrincipalArgumentResolver()
                )
                .build();

        organizerId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(
                organizerId,
                "organizer@example.com",
                "Organizer",
                null,
                List.of()
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities())
        );
    }

    @Test
    void deveRetornar201AoCriarLote() throws Exception {
        UUID eventId = UUID.randomUUID();
        TicketLotResponseDTO response = TicketLotResponseDTO.builder()
                .id(UUID.randomUUID())
                .eventId(eventId)
                .name("Lote promocional")
                .unitPrice(BigDecimal.valueOf(50))
                .capacity(100)
                .sold(0)
                .reserved(0)
                .salesStart(Instant.parse("2026-10-20T00:00:00Z"))
                .salesEnd(Instant.parse("2026-11-20T18:00:00Z"))
                .status(LotStatus.ACTIVE)
                .version(0L)
                .build();

        when(ticketLotService.create(eq(eventId), any(), eq(organizerId)))
                .thenReturn(response);

        mockMvc.perform(post("/api/events/{eventId}/ticket-lots", eventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Lote promocional",
                                  "unitPrice": 50.00,
                                  "capacity": 100,
                                  "salesStart": "2026-10-20T00:00:00Z",
                                  "salesEnd": "2026-11-20T18:00:00Z"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(response.getId().toString()))
                .andExpect(jsonPath("$.eventId").value(eventId.toString()))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.sold").value(0))
                .andExpect(jsonPath("$.reserved").value(0));

        verify(ticketLotService).create(eq(eventId), any(), eq(organizerId));
    }

    @Test
    void deveRetornar400AoCriarLoteComPayloadEstruturalInvalido() throws Exception {
        UUID eventId = UUID.randomUUID();

        mockMvc.perform(post("/api/events/{eventId}/ticket-lots", eventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "",
                                  "salesStart": null,
                                  "salesEnd": null
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields").exists());

        verify(ticketLotService, never()).create(any(), any(), any());
    }

    @Test
    void deveRetornar404AoCriarLoteParaEventoInexistente() throws Exception {
        UUID eventId = UUID.randomUUID();

        when(ticketLotService.create(eq(eventId), any(), eq(organizerId)))
                .thenThrow(new BusinessException("Evento não encontrado", HttpStatus.NOT_FOUND));

        mockMvc.perform(post("/api/events/{eventId}/ticket-lots", eventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Lote",
                                  "unitPrice": 10.00,
                                  "capacity": 10,
                                  "salesStart": "2026-10-20T00:00:00Z",
                                  "salesEnd": "2026-11-20T18:00:00Z"
                                }
                                """))
                .andExpect(status().isNotFound());

        verify(ticketLotService).create(eq(eventId), any(), eq(organizerId));
    }

    @Test
    void deveRetornar422AoCriarLoteComRegraDeNegocioInvalida() throws Exception {
        UUID eventId = UUID.randomUUID();

        when(ticketLotService.create(eq(eventId), any(), eq(organizerId)))
                .thenThrow(new BusinessException(
                        "A capacidade deve ser maior que zero",
                        HttpStatus.UNPROCESSABLE_CONTENT
                ));

        mockMvc.perform(post("/api/events/{eventId}/ticket-lots", eventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Lote",
                                  "unitPrice": 10.00,
                                  "capacity": 0,
                                  "salesStart": "2026-10-20T00:00:00Z",
                                  "salesEnd": "2026-11-20T18:00:00Z"
                                }
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("A capacidade deve ser maior que zero"));

        verify(ticketLotService).create(eq(eventId), any(), eq(organizerId));
    }

    @Test
    void deveRetornar200AoAtualizarLote() throws Exception {
        UUID lotId = UUID.randomUUID();
        TicketLotResponseDTO response = TicketLotResponseDTO.builder()
                .id(lotId)
                .eventId(UUID.randomUUID())
                .name("Lote Atualizado")
                .unitPrice(BigDecimal.valueOf(60))
                .capacity(150)
                .sold(5)
                .reserved(10)
                .salesStart(Instant.parse("2026-10-20T00:00:00Z"))
                .salesEnd(Instant.parse("2026-11-20T18:00:00Z"))
                .status(LotStatus.ACTIVE)
                .version(1L)
                .build();

        when(ticketLotService.update(eq(lotId), any(), eq(organizerId)))
                .thenReturn(response);

        mockMvc.perform(patch("/api/ticket-lots/{lotId}", lotId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Lote Atualizado",
                                  "capacity": 150
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(lotId.toString()))
                .andExpect(jsonPath("$.name").value("Lote Atualizado"))
                .andExpect(jsonPath("$.capacity").value(150));

        verify(ticketLotService).update(eq(lotId), any(), eq(organizerId));
    }

    @Test
    void deveRetornar404AoAtualizarLoteInexistente() throws Exception {
        UUID lotId = UUID.randomUUID();

        when(ticketLotService.update(eq(lotId), any(), eq(organizerId)))
                .thenThrow(new BusinessException("Lote não encontrado", HttpStatus.NOT_FOUND));

        mockMvc.perform(patch("/api/ticket-lots/{lotId}", lotId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "capacity": 200
                                }
                                """))
                .andExpect(status().isNotFound());

        verify(ticketLotService).update(eq(lotId), any(), eq(organizerId));
    }

    @Test
    void deveRetornar409AoAtualizarLoteComCapacidadeAbaixoDaOcupacao() throws Exception {
        UUID lotId = UUID.randomUUID();

        when(ticketLotService.update(eq(lotId), any(), eq(organizerId)))
                .thenThrow(new BusinessException(
                        "A capacidade não pode ser menor que sold + reserved",
                        HttpStatus.CONFLICT
                ));

        mockMvc.perform(patch("/api/ticket-lots/{lotId}", lotId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "capacity": 15
                                }
                                """))
                .andExpect(status().isConflict());

        verify(ticketLotService).update(eq(lotId), any(), eq(organizerId));
    }

    @Test
    void deveRetornar422AoAtualizarLoteComJanelaDeVendasInvalida() throws Exception {
        UUID lotId = UUID.randomUUID();

        when(ticketLotService.update(eq(lotId), any(), eq(organizerId)))
                .thenThrow(new BusinessException(
                        "salesEnd deve ser posterior a salesStart",
                        HttpStatus.UNPROCESSABLE_CONTENT
                ));

        mockMvc.perform(patch("/api/ticket-lots/{lotId}", lotId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "salesStart": "2026-11-20T00:00:00Z",
                                  "salesEnd": "2026-11-19T00:00:00Z"
                                }
                                """))
                .andExpect(status().isUnprocessableEntity());

        verify(ticketLotService).update(eq(lotId), any(), eq(organizerId));
    }
}