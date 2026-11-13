package com.eventbook.backend.orders.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record OrderRequestDTO(

        @NotNull
        UUID ticketLotId,

        @NotNull
        @Positive
        Integer quantity

) {}