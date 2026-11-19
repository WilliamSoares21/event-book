package com.eventbook.backend.checkin.exception;

import com.eventbook.backend.tickets.TicketStatus;

public class TicketInvalidStatusException extends RuntimeException {

    private final TicketStatus currentStatus;

    public TicketInvalidStatusException(String message, TicketStatus currentStatus) {
        super(message);
        this.currentStatus = currentStatus;
    }

    public TicketStatus getCurrentStatus() {
        return currentStatus;
    }
}