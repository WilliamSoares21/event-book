package com.eventbook.backend.orders.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record OrderRequestDTO(
        @Valid
        @NotNull
        @Size(min = 1, message = "Pelo menos um item é obrigatório")
        List<OrderItemRequest> items
) {
    public record OrderItemRequest(
            @NotNull
            UUID ticketLotId,

            @NotNull
            @Positive
            Integer quantity
    ) {}
}