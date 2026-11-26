package com.eventbook.backend.tickets.dto;

import com.eventbook.backend.tickets.TicketStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record BuyerTicketResponse(
        UUID id,
        String eventTitle,
        Instant eventStartsAt,
        String uniqueCode,
        TicketStatus status,
        String ticketLotName,
        BigDecimal unitPriceSnapshot
) {
}