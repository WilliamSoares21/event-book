package com.eventbook.backend.cards.dto;

import com.eventbook.backend.cards.CardStatus;
import com.eventbook.backend.cards.SimulatedCard;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

public record CardResponseDTO(
        UUID id,
        String lastFourDigits,
        String brand,
        Integer expiryMonth,
        Integer expiryYear,
        BigDecimal totalLimit,
        BigDecimal availableLimit,
        CardStatus status,
        Instant createdAt,
        String displayName,
        String expiryDisplay,
        String formattedTotalLimit,
        String formattedAvailableLimit
) {
    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    public static CardResponseDTO from(SimulatedCard card) {
        return new CardResponseDTO(
                card.getId(),
                card.getLastFourDigits(),
                card.getBrand(),
                card.getExpiryMonth(),
                card.getExpiryYear(),
                card.getTotalLimit(),
                card.getAvailableLimit(),
                card.getStatus(),
                card.getCreatedAt(),
                card.getBrand() + " •••• " + card.getLastFourDigits(),
                String.format("%02d/%d", card.getExpiryMonth(), card.getExpiryYear()),
                formatCurrency(card.getTotalLimit()),
                formatCurrency(card.getAvailableLimit())
        );
    }

    private static String formatCurrency(BigDecimal amount) {
        NumberFormat format = NumberFormat.getCurrencyInstance(PT_BR);
        return format.format(amount);
    }
}