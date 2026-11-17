package com.eventbook.backend.payments.exception;

/**
 * Recusa comercial de pagamento. Lançada fora da transação de checkout,
 * para que a transação DECLINED já persistida não seja revertida.
 */
public class PaymentDeclinedException extends RuntimeException {

    private final String code;

    public PaymentDeclinedException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
