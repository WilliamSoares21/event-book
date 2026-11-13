package com.eventbook.backend.events;

import com.eventbook.backend.events.dto.OrganizerEventResponseDTO;
import com.eventbook.backend.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Endpoints administrativos do painel do organizador.
 * Separados do {@link EventController} para não misturar o contrato público com o administrativo.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/organizer")
public class OrganizerEventController {

    private final EventService eventService;

    @GetMapping("/events")
    @PreAuthorize("hasRole('ORGANIZADOR')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Lista os eventos do organizador autenticado, com seus lotes")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Eventos retornados com sucesso"),
            @ApiResponse(responseCode = "401", description = "JWT ausente ou inválido"),
            @ApiResponse(responseCode = "403", description = "Usuário não é organizador")
    })
    public ResponseEntity<List<OrganizerEventResponseDTO>> listOwnEvents(
            @AuthenticationPrincipal AuthenticatedUser authUser) {

        return ResponseEntity.ok(eventService.findByOrganizer(authUser.id()));
    }
}
