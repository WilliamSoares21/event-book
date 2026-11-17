
package com.eventbook.backend.orders.exception;

public class OrderHasCheckedInTicketException extends RuntimeException {

    public OrderHasCheckedInTicketException(String message) {
        super(message);
    }
}
