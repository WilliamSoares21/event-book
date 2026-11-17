package com.eventbook.backend.orders.dto;

import com.eventbook.backend.tickets.Ticket;
import com.eventbook.backend.tickets.TicketStatus;

import java.util.UUID;

public record TicketSummaryDTO(
        UUID id,
        String uniqueCode,
        TicketStatus status
) {
    public static TicketSummaryDTO from(Ticket ticket) {
        return new TicketSummaryDTO(
                ticket.getId(),
                ticket.getUniqueCode(),
                ticket.getStatus()
        );
    }
}