package com.eventbook.backend.checkin.exception;

import com.eventbook.backend.orders.OrderStatus;

public class OrderNotPaidException extends RuntimeException {

    private final OrderStatus currentStatus;

    public OrderNotPaidException(String message, OrderStatus currentStatus) {
        super(message);
        this.currentStatus = currentStatus;
    }

    public OrderStatus getCurrentStatus() {
        return currentStatus;
    }
}