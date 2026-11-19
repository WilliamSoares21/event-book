package com.eventbook.backend.tickets;

import com.eventbook.backend.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
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

    private final TicketRepository ticketRepository;

    @GetMapping("/me")
    @PreAuthorize("hasRole('COMPRADOR')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Lista os tickets emitidos para o comprador autenticado")
    public List<BuyerTicketResponse> listOwnTickets(@AuthenticationPrincipal AuthenticatedUser user) {
        return ticketRepository.listByBuyerId(user.id());
    }
}
