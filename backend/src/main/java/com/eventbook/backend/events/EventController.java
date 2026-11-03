package com.eventbook.backend.events;

import com.eventbook.backend.events.dto.EventOccupancyDTO;
import com.eventbook.backend.events.dto.EventRequestDTO;
import com.eventbook.backend.events.dto.EventResponseDTO;
import com.eventbook.backend.events.dto.EventUpdateDTO;
import com.eventbook.backend.security.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/events")
public class EventController {

    private final EventService eventService;

    @GetMapping
    @Operation(summary = "Lista eventos publicados")
    @ApiResponse(responseCode = "200", description = "Eventos retornados com sucesso")
    public ResponseEntity<List<EventResponseDTO>> listEvents() {
        return ResponseEntity.ok(eventService.findAllPublished());
    }

    @GetMapping("/{eventId}")
    @Operation(summary = "Consulta um evento")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Evento encontrado"),
            @ApiResponse(responseCode = "404", description = "Evento não encontrado")
    })
    public ResponseEntity<EventResponseDTO> getEvent(
            @PathVariable UUID eventId,
            @AuthenticationPrincipal(errorOnInvalidType = false)
            AuthenticatedUser authUser) {

        UUID requesterId = authUser != null ? authUser.id() : null;

        return ResponseEntity.ok(
                eventService.getById(eventId, requesterId)
        );
    }

    @PreAuthorize("hasRole('ORGANIZADOR')")
    @PostMapping
    @Operation(summary = "Cria um evento")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Evento criado"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos"),
            @ApiResponse(responseCode = "401", description = "JWT ausente ou inválido"),
            @ApiResponse(responseCode = "403", description = "Usuário não é organizador"),
            @ApiResponse(responseCode = "422", description = "Regra de negócio inválida")
    })
    public ResponseEntity<EventResponseDTO> createEvent(
            @Valid @RequestBody EventRequestDTO dto,
            @AuthenticationPrincipal AuthenticatedUser authUser) {

        EventResponseDTO created = eventService.create(
                dto,
                authUser.id()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PreAuthorize("hasRole('ORGANIZADOR')")
    @PatchMapping("/{eventId}")
    @Operation(summary = "Atualiza parcialmente um evento")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Evento atualizado"),
        @ApiResponse(responseCode = "403", description = "Usuário não é proprietário"),
        @ApiResponse(responseCode = "404", description = "Evento não encontrado"),
        @ApiResponse(responseCode = "409", description = "Conflito de concorrência"),
        @ApiResponse(responseCode = "422", description = "Alteração inválida")
    })
    public ResponseEntity<EventResponseDTO> updateEvent(
            @PathVariable UUID eventId,
            @Valid @RequestBody EventUpdateDTO dto,
            @AuthenticationPrincipal AuthenticatedUser authUser) {

        return ResponseEntity.ok(
                eventService.update(
                        eventId,
                        dto,
                        authUser.id()
                )
        );
    }

    @PreAuthorize("hasRole('ORGANIZADOR')")
    @PostMapping("/{eventId}/publish")
    public ResponseEntity<Void> publishEvent(
            @PathVariable UUID eventId,
            @AuthenticationPrincipal AuthenticatedUser authUser) {

        eventService.publish(eventId, authUser.id());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{eventId}/occupancy")
    public ResponseEntity<EventOccupancyDTO> getOccupancy(
            @PathVariable UUID eventId) {

        return ResponseEntity.ok(
                eventService.getOccupancy(eventId)
        );
    }
}