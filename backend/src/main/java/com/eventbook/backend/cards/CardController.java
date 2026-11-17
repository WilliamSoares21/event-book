package com.eventbook.backend.cards;

import com.eventbook.backend.cards.dto.CardResponseDTO;
import com.eventbook.backend.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/cards")
public class CardController {

    private final SimulatedCardService simulatedCardService;

    public CardController(SimulatedCardService simulatedCardService) {
        this.simulatedCardService = simulatedCardService;
    }

    @PreAuthorize("hasRole('COMPRADOR')")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping
    @Operation(summary = "Cadastra um cartão fictício para o usuário autenticado")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Cartão cadastrado"),
            @ApiResponse(responseCode = "401", description = "JWT ausente ou inválido"),
            @ApiResponse(responseCode = "403", description = "Usuário não é comprador"),
            @ApiResponse(responseCode = "409", description = "Usuário já possui cartão ativo")
    })
    public ResponseEntity<CardResponseDTO> create(
            @AuthenticationPrincipal AuthenticatedUser authUser) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(simulatedCardService.createCard(authUser.id()));
    }

    @PreAuthorize("hasRole('COMPRADOR')")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/me")
    @Operation(summary = "Lista os cartões fictícios do usuário autenticado")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cartões do usuário"),
            @ApiResponse(responseCode = "401", description = "JWT ausente ou inválido"),
            @ApiResponse(responseCode = "403", description = "Usuário não é comprador")
    })
    public ResponseEntity<List<CardResponseDTO>> listMine(
            @AuthenticationPrincipal AuthenticatedUser authUser) {

        return ResponseEntity.ok(simulatedCardService.listMyCards(authUser.id()));
    }

    @PreAuthorize("hasRole('COMPRADOR')")
    @SecurityRequirement(name = "bearerAuth")
    @PatchMapping("/{cardId}/deactivate")
    @Operation(summary = "Desativa um cartão fictício do usuário autenticado")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Cartão desativado"),
            @ApiResponse(responseCode = "401", description = "JWT ausente ou inválido"),
            @ApiResponse(responseCode = "403", description = "Cartão de outro usuário"),
            @ApiResponse(responseCode = "404", description = "Cartão não encontrado"),
            @ApiResponse(responseCode = "422", description = "Cartão já está inativo")
    })
    public ResponseEntity<Void> deactivate(
            @PathVariable UUID cardId,
            @AuthenticationPrincipal AuthenticatedUser authUser) {

        simulatedCardService.deactivateCard(authUser.id(), cardId);
        return ResponseEntity.noContent().build();
    }
}