package com.eventbook.backend.checkin.exception;

public class InvalidTicketCodeException extends RuntimeException {

    public InvalidTicketCodeException(String message) {
        super(message);
    }
}