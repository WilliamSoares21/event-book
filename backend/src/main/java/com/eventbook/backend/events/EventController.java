package com.eventbook.backend.events;

import com.eventbook.backend.common.dto.PageResponse;
import com.eventbook.backend.events.dto.EventOccupancyDTO;
import com.eventbook.backend.events.dto.EventRequestDTO;
import com.eventbook.backend.events.dto.EventResponseDTO;
import com.eventbook.backend.events.dto.EventUpdateDTO;
import com.eventbook.backend.security.AuthenticatedUser;
import com.eventbook.backend.staff.StaffService;
import com.eventbook.backend.staff.dto.AddEventStaffRequest;
import com.eventbook.backend.staff.dto.EventStaffResponseDTO;
import com.eventbook.backend.staff.dto.StaffOptionResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import java.net.URI;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/events")
public class EventController {

    private final EventService eventService;
    private final StaffService staffService;

    @GetMapping
    @Operation(summary = "Lista eventos publicados (paginado)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Eventos retornados com sucesso"),
            @ApiResponse(responseCode = "400", description = "Parâmetros de paginação inválidos")
    })
    public ResponseEntity<PageResponse<EventResponseDTO>> listEvents(
            @Parameter(description = "Número da página (base 0)", example = "0")
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Tamanho da página (máx 50)", example = "10")
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("startsAt").ascending().and(Sort.by("id").ascending()));
        PageResponse<EventResponseDTO> events = eventService.findAllPublished(pageable);
        return ResponseEntity.ok(events);
    }

    @GetMapping("/{eventId}")
    @Operation(summary = "Consulta um evento")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Evento encontrado"),
            @ApiResponse(responseCode = "400", description = "UUID inválido"),
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
    @SecurityRequirement(name = "bearerAuth")
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

        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{eventId}")
                .buildAndExpand(created.getId())
                .toUri();

        return ResponseEntity.created(location).body(created);
    }

    @PreAuthorize("hasRole('ORGANIZADOR')")
    @SecurityRequirement(name = "bearerAuth")
    @PatchMapping("/{eventId}")
    @Operation(summary = "Atualiza parcialmente um evento")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Evento atualizado"),
        @ApiResponse(responseCode = "400", description = "UUID ou payload inválido"),
        @ApiResponse(responseCode = "401", description = "JWT ausente ou inválido"),
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
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/{eventId}/publication")
    @Operation(summary = "Publica um evento")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Evento publicado com sucesso"),
            @ApiResponse(responseCode = "401", description = "JWT ausente ou inválido"),
            @ApiResponse(responseCode = "403", description = "Somente o organizador proprietário pode publicar"),
            @ApiResponse(responseCode = "404", description = "Evento não encontrado"),
            @ApiResponse(responseCode = "422", description = "Data de início não é futura ou evento não possui lote ativo")
    })
    public ResponseEntity<Void> publishEvent(
            @PathVariable UUID eventId,
            @AuthenticationPrincipal AuthenticatedUser authUser) {

        eventService.publish(eventId, authUser.id());
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasRole('ORGANIZADOR')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Consulta ocupação do evento (apenas organizador proprietário)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Ocupação retornada"),
        @ApiResponse(responseCode = "401", description = "JWT ausente ou inválido"),
        @ApiResponse(responseCode = "403", description = "Usuário não é organizador"),
        @ApiResponse(responseCode = "404", description = "Evento não encontrado")
    })
    @GetMapping("/{eventId}/occupancy")
    public ResponseEntity<EventOccupancyDTO> getOccupancy(
            @PathVariable UUID eventId,
            @AuthenticationPrincipal AuthenticatedUser authUser) {

        return ResponseEntity.ok(
                eventService.getOccupancy(eventId, authUser.id())
        );
    }

    @PreAuthorize("hasRole('ORGANIZADOR')")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/{eventId}/staff")
    @Operation(summary = "Lista colaboradores cadastrados e indica se estão associados ao evento")
    public List<StaffOptionResponse> listEventStaff(
            @PathVariable UUID eventId,
            @AuthenticationPrincipal AuthenticatedUser authUser) {
        return staffService.listStaffForEvent(eventId, authUser.id());
    }

    @PreAuthorize("hasRole('ORGANIZADOR')")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/{eventId}/staff")
    @Operation(summary = "Associa um usuário STAFF ao evento")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Associação criada"),
            @ApiResponse(responseCode = "400", description = "UUID inválido ou payload inválido"),
            @ApiResponse(responseCode = "401", description = "JWT ausente ou inválido"),
            @ApiResponse(responseCode = "403", description = "Usuário sem role ORGANIZADOR ou sem propriedade sobre o evento"),
            @ApiResponse(responseCode = "404", description = "Evento ou usuário inexistente"),
            @ApiResponse(responseCode = "409", description = "Usuário já associado ao evento"),
            @ApiResponse(responseCode = "422", description = "Usuário existente sem role STAFF")
    })
    public ResponseEntity<EventStaffResponseDTO> addEventStaff(
            @PathVariable UUID eventId,
            @Valid @RequestBody AddEventStaffRequest request,
            @AuthenticationPrincipal AuthenticatedUser authUser) {

        EventStaffResponseDTO created = staffService.addStaffToEvent(eventId, request, authUser.id());

        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{staffId}")
                .buildAndExpand(created.getId())
                .toUri();

        return ResponseEntity.created(location).body(created);
    }
}