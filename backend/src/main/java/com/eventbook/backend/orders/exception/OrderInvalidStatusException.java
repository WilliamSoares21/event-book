package com.eventbook.backend.orders.exception;

import com.eventbook.backend.orders.OrderStatus;

public class OrderInvalidStatusException extends RuntimeException {

    private final OrderStatus currentStatus;

    public OrderInvalidStatusException(String message, OrderStatus currentStatus) {
        super(message);
        this.currentStatus = currentStatus;
    }

    public OrderStatus getCurrentStatus() {
        return currentStatus;
    }
}