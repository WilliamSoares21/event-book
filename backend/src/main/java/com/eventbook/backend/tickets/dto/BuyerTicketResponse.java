package com.eventbook.backend.tickets.dto;

import com.eventbook.backend.tickets.TicketStatus;
import java.time.Instant;
import java.util.UUID;

public record BuyerTicketResponse(
        UUID id,
        String eventTitle,
        Instant eventStartsAt,
        String uniqueCode,
        TicketStatus status
) {
}