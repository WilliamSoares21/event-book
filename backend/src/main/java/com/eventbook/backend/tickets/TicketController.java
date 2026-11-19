package com.eventbook.backend.tickets;

import com.eventbook.backend.security.AuthenticatedUser;
import com.eventbook.backend.tickets.dto.BuyerTicketResponse;
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

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/tickets")
public class TicketController {

    private final TicketService ticketService;

    @GetMapping("/me")
    @PreAuthorize("hasRole('COMPRADOR')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Lista os ingressos do comprador autenticado")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de ingressos do comprador"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Usuário não é comprador")
    })
    public ResponseEntity<List<BuyerTicketResponse>> listOwnTickets(
            @AuthenticationPrincipal AuthenticatedUser authUser) {

        List<BuyerTicketResponse> tickets = ticketService.findOwnTickets(authUser.id());
        return ResponseEntity.ok(tickets);
    }
}