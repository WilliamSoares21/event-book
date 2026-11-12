package com.eventbook.backend.orders.exception;

import com.eventbook.backend.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class InsufficientCapacityException extends BusinessException {

    public InsufficientCapacityException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}