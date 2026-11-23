package com.eventbook.backend.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Set;

public class BrazilianStateValidator implements ConstraintValidator<ValidBrazilianState, String> {

    private static final Set<String> UFS = Set.of(
            "AC", "AL", "AP", "AM", "BA", "CE", "DF", "ES", "GO", "MA",
            "MT", "MS", "MG", "PA", "PB", "PR", "PE", "PI", "RJ", "RN",
            "RS", "RO", "RR", "SC", "SP", "SE", "TO"
    );

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || UFS.contains(value);
    }
}