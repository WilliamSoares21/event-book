package com.eventbook.backend.orders.exception;

public class LotNotActiveException extends RuntimeException {
    public LotNotActiveException(String message) {
        super(message);
    }
}
