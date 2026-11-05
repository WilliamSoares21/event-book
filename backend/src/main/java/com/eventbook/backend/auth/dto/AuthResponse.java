package com.eventbook.backend.auth.dto;

import com.eventbook.backend.users.dto.UserResponse; // já existe

public record AuthResponse(
        String token,
        String tokenType,
        long expiresIn,        // milissegundos
        UserResponse user      // informação mínima do usuário
) {}
