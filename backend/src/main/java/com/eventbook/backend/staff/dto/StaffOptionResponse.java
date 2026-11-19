package com.eventbook.backend.staff.dto;

import java.util.UUID;

public record StaffOptionResponse(UUID id, String name, boolean associated) {
}
