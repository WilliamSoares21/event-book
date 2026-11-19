package com.eventbook.backend.events.projection;

import java.util.UUID;

public record TicketLotOccupancyProjection(
    UUID ticketLotId,
    String name,
    Long capacity,
    Long sold,
    Long reserved,
    Long checkedIn
) {}