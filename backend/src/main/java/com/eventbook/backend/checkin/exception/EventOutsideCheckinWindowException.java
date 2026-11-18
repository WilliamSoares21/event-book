package com.eventbook.backend.checkin.exception;

public class EventOutsideCheckinWindowException extends RuntimeException {

    public EventOutsideCheckinWindowException(String message) {
        super(message);
    }
}