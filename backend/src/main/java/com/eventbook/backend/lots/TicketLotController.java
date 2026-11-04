package com.eventbook.backend.lots;

import com.eventbook.backend.lots.dto.TicketLotRequestDTO;
import com.eventbook.backend.lots.dto.TicketLotResponseDTO;
import com.eventbook.backend.lots.dto.TicketLotUpdateDTO;
import com.eventbook.backend.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api")
public class TicketLotController {

    private final TicketLotService ticketLotService;

    public TicketLotController(TicketLotService ticketLotService) {
        this.ticketLotService = ticketLotService;
    }

    @PreAuthorize("hasRole('ORGANIZADOR')")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/events/{eventId}/ticket-lots")
    @Operation(summary = "Cria um lote de ingressos")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Lote criado"),
            @ApiResponse(responseCode = "403", description = "Organizador sem propriedade do evento"),
            @ApiResponse(responseCode = "404", description = "Evento não encontrado"),
            @ApiResponse(responseCode = "422", description = "Dados do lote inválidos")
    })
    public ResponseEntity<TicketLotResponseDTO> create(
            @PathVariable UUID eventId,
            @Valid @RequestBody TicketLotRequestDTO dto,
            @AuthenticationPrincipal AuthenticatedUser authUser) {

        return ResponseEntity.status(HttpStatus.CREATED).body(
                ticketLotService.create(eventId, dto, authUser.id())
        );
    }

    @PreAuthorize("hasRole('ORGANIZADOR')")
    @SecurityRequirement(name = "bearerAuth")
    @PatchMapping("/ticket-lots/{lotId}")
    @Operation(summary = "Atualiza parcialmente um lote de ingressos")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lote atualizado"),
            @ApiResponse(responseCode = "403", description = "Organizador sem propriedade"),
            @ApiResponse(responseCode = "404", description = "Lote não encontrado"),
            @ApiResponse(responseCode = "409", description = "Conflito de concorrência ou capacidade"),
            @ApiResponse(responseCode = "422", description = "Dados do lote inválidos")
    })
    public ResponseEntity<TicketLotResponseDTO> update(
            @PathVariable UUID lotId,
            @Valid @RequestBody TicketLotUpdateDTO dto,
            @AuthenticationPrincipal AuthenticatedUser authUser) {

        return ResponseEntity.ok(
                ticketLotService.update(lotId, dto, authUser.id())
        );
    }
}
