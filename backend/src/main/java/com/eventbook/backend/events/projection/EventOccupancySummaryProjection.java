package com.eventbook.backend.events.projection;

import java.util.UUID;

public record EventOccupancySummaryProjection(
    UUID eventId,
    String eventName,
    Long capacity,
    Long sold,
    Long reserved,
    Long checkedIn
) {}