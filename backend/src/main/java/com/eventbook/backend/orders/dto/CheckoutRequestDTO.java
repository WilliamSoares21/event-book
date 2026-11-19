package com.eventbook.backend.orders.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CheckoutRequestDTO(
        @NotNull(message = "O ID do cartão é obrigatório")
        UUID cardId
) {}
