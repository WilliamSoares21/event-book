package com.eventbook.backend.orders.exception;

import com.eventbook.backend.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class LotNotActiveException extends BusinessException {

    public LotNotActiveException(String message) {
        super(message, HttpStatus.UNPROCESSABLE_ENTITY);
    }
}