package com.eventbook.backend.orders.exception;

public class OutsideSalesWindowException extends RuntimeException {
    public  OutsideSalesWindowException(String message) {
        super(message);
    }
}
