package com.eventbook.backend.orders.dto;

import com.eventbook.backend.orders.Order;
import com.eventbook.backend.orders.OrderItem;
import com.eventbook.backend.orders.OrderStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponseDTO(
        UUID id,
        List<OrderItemResponse> items,
        BigDecimal totalPrice,
        OrderStatus status,
        Instant expiresAt,
        Instant createdAt
) {
    public record OrderItemResponse(
            UUID id,
            UUID ticketLotId,
            String ticketLotName,
            Integer quantity,
            BigDecimal unitPriceSnapshot,
            BigDecimal totalPrice
    ) {}

    public static OrderResponseDTO from(Order order) {
        List<OrderItemResponse> itemResponses = order.getItems().stream()
            .map(item -> new OrderItemResponse(
                item.getId(),
                item.getTicketLot().getId(),
                item.getTicketLot().getName(),
                item.getQuantity(),
                item.getUnitPriceSnapshot(),
                item.getTotalPrice()
            ))
            .toList();

        BigDecimal totalPrice = itemResponses.stream()
            .map(OrderItemResponse::totalPrice)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new OrderResponseDTO(
                order.getId(),
                itemResponses,
                totalPrice,
                order.getStatus(),
                order.getExpiresAt(),
                order.getCreatedAt()
        );
    }
    
    // Factory para compatibilidade com pedidos legados (migração V12)
    public static OrderResponseDTO fromLegacy(Order order) {
        UUID ticketLotId = order.getTicketLot() != null ? order.getTicketLot().getId() : null;
        String ticketLotName = order.getTicketLot() != null ? order.getTicketLot().getName() : "Lote desconhecido";
        Integer quantity = order.getQuantity();
        BigDecimal unitPrice = order.getUnitPriceSnapshot();
        
        OrderItemResponse legacyItem = new OrderItemResponse(
            order.getId(), // usar orderId como ID temporário
            ticketLotId,
            ticketLotName,
            quantity,
            unitPrice,
            unitPrice != null && quantity != null ? unitPrice.multiply(BigDecimal.valueOf(quantity)) : BigDecimal.ZERO
        );
        
        BigDecimal totalPrice = legacyItem.totalPrice();
        
        return new OrderResponseDTO(
            order.getId(),
            List.of(legacyItem),
            totalPrice,
            order.getStatus(),
            order.getExpiresAt(),
            order.getCreatedAt()
        );
    }
}