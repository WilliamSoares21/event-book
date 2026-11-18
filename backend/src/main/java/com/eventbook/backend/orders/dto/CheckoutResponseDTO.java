package com.eventbook.backend.orders.dto;

import com.eventbook.backend.orders.Order;
import com.eventbook.backend.orders.OrderStatus;
import com.eventbook.backend.tickets.Ticket;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CheckoutResponseDTO(
        UUID orderId,
        UUID ticketLotId,
        Integer quantity,
        BigDecimal unitPriceSnapshot,
        BigDecimal totalPrice,
        OrderStatus status,
        Instant paidAt,
        List<TicketSummaryDTO> tickets
) {
    public static CheckoutResponseDTO from(Order order, List<Ticket> tickets) {
        BigDecimal totalPrice = order.getUnitPriceSnapshot()
                .multiply(BigDecimal.valueOf(order.getQuantity()))
                .setScale(2, RoundingMode.HALF_UP);

        List<TicketSummaryDTO> ticketSummaries = tickets.stream()
                .map(TicketSummaryDTO::from)
                .toList();

        return new CheckoutResponseDTO(
                order.getId(),
                order.getTicketLot().getId(),
                order.getQuantity(),
                order.getUnitPriceSnapshot(),
                totalPrice,
                order.getStatus(),
                order.getPaidAt(),
                ticketSummaries
        );
    }
}