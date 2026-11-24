package com.eventbook.backend.events.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class EventImageDTOValidationTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    private static EventImageUploadRequestDTO upload(Long sizeBytes) {
        return EventImageUploadRequestDTO.builder()
                .contentType("image/webp")
                .sha256Base64("c2hhMjU2")
                .writeId("write-id-1")
                .sizeBytes(sizeBytes)
                .build();
    }

    private static EventImageConfirmRequestDTO confirm(Long sizeBytes) {
        return EventImageConfirmRequestDTO.builder()
                .objectKey("events/uuid.webp")
                .writeId("write-id-1")
                .sha256Base64("c2hhMjU2")
                .sizeBytes(sizeBytes)
                .build();
    }

    private static Set<String> violatedFields(Set<? extends ConstraintViolation<?>> violations) {
        return violations.stream()
                .map(v -> v.getPropertyPath().toString())
                .collect(java.util.stream.Collectors.toSet());
    }

    @Test
    void uploadComTamanhoValidoNaoTemViolacoes() {
        assertThat(validator.validate(upload(3564530L))).isEmpty();
    }

    @Test
    void uploadSemTamanhoEhRejeitado() {
        assertThat(violatedFields(validator.validate(upload(null)))).contains("sizeBytes");
    }

    @Test
    void uploadComTamanhoZeroOuNegativoEhRejeitado() {
        assertThat(violatedFields(validator.validate(upload(0L)))).contains("sizeBytes");
        assertThat(violatedFields(validator.validate(upload(-1L)))).contains("sizeBytes");
    }

    @Test
    void confirmacaoComTamanhoValidoNaoTemViolacoes() {
        assertThat(validator.validate(confirm(3564530L))).isEmpty();
    }

    @Test
    void confirmacaoSemTamanhoEhRejeitada() {
        assertThat(violatedFields(validator.validate(confirm(null)))).contains("sizeBytes");
    }
}
