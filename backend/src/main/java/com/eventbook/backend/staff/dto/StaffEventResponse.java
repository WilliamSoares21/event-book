package com.eventbook.backend.staff.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record StaffEventResponse(UUID id, String title, OffsetDateTime startsAt, String location) {
}
