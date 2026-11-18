package com.eventbook.backend.orders.dto;

import com.eventbook.backend.orders.Order;
import com.eventbook.backend.orders.OrderStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

public record OrderResponseDTO(
        UUID id,
        UUID ticketLotId,
        Integer quantity,
        BigDecimal unitPriceSnapshot,
        BigDecimal totalPrice,
        OrderStatus status,
        Instant expiresAt,
        Instant createdAt
) {
    public static OrderResponseDTO from(Order order) {

        BigDecimal totalPrice = order.getUnitPriceSnapshot()
                .multiply(BigDecimal.valueOf(order.getQuantity()))
                .setScale(2, RoundingMode.HALF_UP);

        return new OrderResponseDTO(
                order.getId(),
                order.getTicketLot().getId(),
                order.getQuantity(),
                order.getUnitPriceSnapshot(),
                totalPrice,
                order.getStatus(),
                order.getExpiresAt(),
                order.getCreatedAt()
        );
    }
}