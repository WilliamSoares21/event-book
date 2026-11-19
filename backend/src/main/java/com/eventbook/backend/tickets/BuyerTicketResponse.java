package com.eventbook.backend.tickets;

import java.time.OffsetDateTime;
import java.util.UUID;

public record BuyerTicketResponse(
        UUID id,
        String eventTitle,
        OffsetDateTime eventStartsAt,
        String uniqueCode,
        TicketStatus status
) {}
