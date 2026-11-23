package com.eventbook.backend.events;

import com.eventbook.backend.events.dto.EventUpdateDTO;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class EventUpdateDTOValidationTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void stringVaziaNaoFalhaValidacao() {
        EventUpdateDTO dto = new EventUpdateDTO();
        dto.setZipCode("");

        Set<ConstraintViolation<EventUpdateDTO>> violations = validator.validate(dto);
        assertThat(violations).isEmpty();
    }

    @Test
    void espacosNaoFalhamValidacao() {
        EventUpdateDTO dto = new EventUpdateDTO();
        dto.setZipCode("   ");

        Set<ConstraintViolation<EventUpdateDTO>> violations = validator.validate(dto);
        assertThat(violations).isEmpty();
    }

    @Test
    void cepValidoPassaValidacao() {
        EventUpdateDTO dto = new EventUpdateDTO();
        dto.setZipCode("60000-000");

        Set<ConstraintViolation<EventUpdateDTO>> violations = validator.validate(dto);
        assertThat(violations).isEmpty();
    }

    @Test
    void cepValidoSemHifenPassaValidacao() {
        EventUpdateDTO dto = new EventUpdateDTO();
        dto.setZipCode("60000000");

        Set<ConstraintViolation<EventUpdateDTO>> violations = validator.validate(dto);
        assertThat(violations).isEmpty();
    }

    @Test
    void cepInvalidoNaoVazioFalhaValidacao() {
        EventUpdateDTO dto = new EventUpdateDTO();
        dto.setZipCode("abc");

        Set<ConstraintViolation<EventUpdateDTO>> violations = validator.validate(dto);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("zipCode");
    }

    @Test
    void cepMuitoLongoFalhaValidacao() {
        EventUpdateDTO dto = new EventUpdateDTO();
        dto.setZipCode("1234567890");

        Set<ConstraintViolation<EventUpdateDTO>> violations = validator.validate(dto);
        assertThat(violations).hasSizeGreaterThanOrEqualTo(1);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("zipCode"));
    }
}