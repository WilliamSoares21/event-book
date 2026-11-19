package com.eventbook.backend.common.exception;

import com.eventbook.backend.cards.exception.CardAccessDeniedException;
import com.eventbook.backend.cards.exception.CardNotFoundException;
import com.eventbook.backend.checkin.exception.EventOutsideCheckinWindowException;
import com.eventbook.backend.checkin.exception.InvalidTicketCodeException;
import com.eventbook.backend.checkin.exception.OrderNotPaidException;
import com.eventbook.backend.checkin.exception.StaffNotAuthorizedException;
import com.eventbook.backend.checkin.exception.TicketAlreadyCheckedInException;
import com.eventbook.backend.checkin.exception.TicketInvalidStatusException;
import com.eventbook.backend.checkin.exception.TicketNotFoundException;
import com.eventbook.backend.orders.exception.OrderAccessDeniedException;
import com.eventbook.backend.orders.exception.OrderAlreadyCancelledException;
import com.eventbook.backend.orders.exception.OrderExpiredException;
import com.eventbook.backend.orders.exception.OrderHasCheckedInTicketException;
import com.eventbook.backend.orders.exception.OrderInvalidStatusException;
import com.eventbook.backend.orders.exception.OrderNotFoundException;
import com.eventbook.backend.orders.exception.OrderAlreadyCancelledException;
import com.eventbook.backend.orders.OrderStatus;
import com.eventbook.backend.payments.exception.PaymentDeclinedException;
import com.eventbook.backend.users.exception.DuplicateEmailException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(
            MethodArgumentNotValidException ex) {

        Map<String, String> fieldErrors = new HashMap<>();

        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(error.getField(), error.getDefaultMessage());
        }

        Map<String, Object> body = new HashMap<>();
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("error", "Validation Failed");
        body.put("fields", fieldErrors);
        body.put("timestamp", Instant.now().toString());

        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex) {

        return buildError(
                HttpStatus.BAD_REQUEST,
                "Parâmetro inválido: " + ex.getName()
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadableMessage(
            HttpMessageNotReadableException ex) {

        return buildError(
                HttpStatus.BAD_REQUEST,
                "Payload ou formato inválido"
        );
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrity(
            DataIntegrityViolationException ex) {

        return buildError(HttpStatus.CONFLICT, "Operação conflita com registros existentes");
    }

    @ExceptionHandler(DuplicateEmailException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicateEmail(
            DuplicateEmailException ex) {

        return buildError(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Map<String, Object>> handleBusiness(
            BusinessException ex) {

        return buildError(ex.getStatus(), ex.getMessage());
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleEntityNotFound(
            EntityNotFoundException ex) {

        return buildError(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleOrderNotFound(
            OrderNotFoundException ex) {

        return buildError(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(OrderAccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleOrderAccessDenied(
            OrderAccessDeniedException ex) {

        return buildError(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(OrderInvalidStatusException.class)
    public ResponseEntity<Map<String, Object>> handleOrderInvalidStatus(
            OrderInvalidStatusException ex) {

        // PAID = já processado (409); CANCELLED/EXPIRED = estado incompatível (422)
        HttpStatus status = ex.getCurrentStatus() == OrderStatus.PAID
                ? HttpStatus.CONFLICT
                : HttpStatus.UNPROCESSABLE_ENTITY;

        return buildError(status, ex.getMessage());
    }

    @ExceptionHandler(OrderExpiredException.class)
    public ResponseEntity<Map<String, Object>> handleOrderExpired(
            OrderExpiredException ex) {

        return buildError(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
    }

    @ExceptionHandler(OrderAlreadyCancelledException.class)
    public ResponseEntity<Map<String, Object>> handleOrderAlreadyCancelled(
            OrderAlreadyCancelledException ex) {

        return buildError(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(OrderHasCheckedInTicketException.class)
    public ResponseEntity<Map<String, Object>> handleOrderHasCheckedInTicket(
            OrderHasCheckedInTicketException ex) {

        return buildError(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
    }

    @ExceptionHandler(CardNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleCardNotFound(
            CardNotFoundException ex) {

        return buildError(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(CardAccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleCardAccessDenied(
            CardAccessDeniedException ex) {

        return buildError(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(PaymentDeclinedException.class)
    public ResponseEntity<Map<String, Object>> handlePaymentDeclined(
            PaymentDeclinedException ex) {

        Map<String, Object> body = new HashMap<>();
        body.put("status", HttpStatus.UNPROCESSABLE_ENTITY.value());
        body.put("code", ex.getCode());
        body.put("error", ex.getMessage());
        body.put("timestamp", Instant.now().toString());

        return ResponseEntity.unprocessableEntity().body(body);
    }

    // esse handler é uma proteção adicional caso alguma atualização futura não seja tratada dentro do service.
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, Object>> handleOptimisticLock(
            ObjectOptimisticLockingFailureException ex) {

        return buildError(
                HttpStatus.CONFLICT,
                "O recurso foi alterado por outro usuário"
        );
    }

    @ExceptionHandler(TicketNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleTicketNotFound(
            TicketNotFoundException ex) {

        return buildError(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(TicketAlreadyCheckedInException.class)
    public ResponseEntity<Map<String, Object>> handleTicketAlreadyCheckedIn(
            TicketAlreadyCheckedInException ex) {

        // This exception is handled at controller level to return the conflict DTO
        // but we keep a fallback here for safety
        return buildError(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(OrderNotPaidException.class)
    public ResponseEntity<Map<String, Object>> handleOrderNotPaid(
            OrderNotPaidException ex) {

        return buildError(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
    }

    @ExceptionHandler(TicketInvalidStatusException.class)
    public ResponseEntity<Map<String, Object>> handleTicketInvalidStatus(
            TicketInvalidStatusException ex) {

        return buildError(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
    }

    @ExceptionHandler(EventOutsideCheckinWindowException.class)
    public ResponseEntity<Map<String, Object>> handleEventOutsideWindow(
            EventOutsideCheckinWindowException ex) {

        return buildError(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
    }

    @ExceptionHandler(StaffNotAuthorizedException.class)
    public ResponseEntity<Map<String, Object>> handleStaffNotAuthorized(
            StaffNotAuthorizedException ex) {

        return buildError(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(InvalidTicketCodeException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidTicketCode(
            InvalidTicketCodeException ex) {

        return buildError(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    private ResponseEntity<Map<String, Object>> buildError(
            HttpStatus status,
            String message) {

        Map<String, Object> body = new HashMap<>();
        body.put("status", status.value());
        body.put("error", message);
        body.put("timestamp", Instant.now().toString());

        return ResponseEntity.status(status).body(body);
    }
}