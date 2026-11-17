package com.eventbook.backend.orders;

import com.eventbook.backend.orders.dto.CheckoutResponseDTO;

/**
 * Resultado de uma tentativa de checkout: aprovado (com a resposta da RN-05)
 * ou recusado por limite insuficiente (transação DECLINED já persistida).
 */
public record CheckoutResult(CheckoutResponseDTO response, String declineCode) {

    public static final String INSUFFICIENT_CARD_LIMIT = "INSUFFICIENT_CARD_LIMIT";

    public static CheckoutResult approved(CheckoutResponseDTO response) {
        return new CheckoutResult(response, null);
    }

    public static CheckoutResult declined(String code) {
        return new CheckoutResult(null, code);
    }

    public boolean isDeclined() {
        return declineCode != null;
    }
}
