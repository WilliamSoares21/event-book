package com.eventbook.backend.cards.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.FORBIDDEN)
public class CardAccessDeniedException extends RuntimeException {
    public CardAccessDeniedException(String message) {
        super(message);
    }
}