package com.eventbook.backend.cards.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
public class InsufficientCardLimitException extends RuntimeException {
    public InsufficientCardLimitException(String message) {
        super(message);
    }
}
