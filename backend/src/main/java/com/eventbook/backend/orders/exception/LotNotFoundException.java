package com.eventbook.backend.orders.exception;

import org.springframework.http.HttpStatus;

import com.eventbook.backend.common.exception.BusinessException;

public class LotNotFoundException extends BusinessException {
  public LotNotFoundException(String message) {
    super(message, HttpStatus.NOT_FOUND);
  }

}
