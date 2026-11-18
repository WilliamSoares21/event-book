package com.eventbook.backend.checkin.exception;

public class StaffNotAuthorizedException extends RuntimeException {

    public StaffNotAuthorizedException(String message) {
        super(message);
    }
}