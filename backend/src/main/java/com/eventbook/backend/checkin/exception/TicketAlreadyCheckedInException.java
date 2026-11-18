package com.eventbook.backend.checkin.exception;

import com.eventbook.backend.checkin.CheckIn;

public class TicketAlreadyCheckedInException extends RuntimeException {

    private final CheckIn existingCheckIn;

    public TicketAlreadyCheckedInException(String message, CheckIn existingCheckIn) {
        super(message);
        this.existingCheckIn = existingCheckIn;
    }

    public CheckIn getExistingCheckIn() {
        return existingCheckIn;
    }
}