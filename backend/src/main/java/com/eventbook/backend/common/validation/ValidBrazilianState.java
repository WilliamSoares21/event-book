package com.eventbook.backend.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = BrazilianStateValidator.class)
@Documented
public @interface ValidBrazilianState {
    String message() default "UF inválida. Use uma das 27 UFs brasileiras.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}