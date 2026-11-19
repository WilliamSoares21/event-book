package com.eventbook.backend.tickets;

import com.eventbook.backend.common.dto.PageResponse;
import com.eventbook.backend.security.AuthenticatedUser;
import com.eventbook.backend.tickets.dto.BuyerTicketResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/tickets")
public class TicketController {

    private final TicketService ticketService;

    @GetMapping("/me")
    @PreAuthorize("hasRole('COMPRADOR')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Lista os ingressos do comprador autenticado (paginado)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista paginada de ingressos do comprador"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Usuário não é comprador")
    })
    public ResponseEntity<PageResponse<BuyerTicketResponse>> listOwnTickets(
            @Parameter(description = "Número da página (base 0)", example = "0")
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Tamanho da página (máx 50)", example = "10")
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size,
            @AuthenticationPrincipal AuthenticatedUser authUser) {

        PageResponse<BuyerTicketResponse> tickets = ticketService.findOwnTickets(authUser.id(), page, size);
        return ResponseEntity.ok(tickets);
    }
}