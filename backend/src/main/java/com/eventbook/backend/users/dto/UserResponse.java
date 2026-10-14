package com.eventbook.backend.users.dto;

import com.eventbook.backend.users.Role;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String name,
        String email,
        Role role,
        Instant createdAt
) {
}