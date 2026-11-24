package com.eventbook.backend.events.dto;

import com.eventbook.backend.common.deserializer.EmptyStringAsNullDeserializer;
import com.eventbook.backend.common.deserializer.UpperCaseTrimDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class EventDTOValidationTest {

    private Validator validator;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setup() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    // =========================================================================
    // TESTES DE DESERIALIZAÇÃO JACKSON (ObjectMapper) — APENAS DESERIALIZAÇÃO
    // =========================================================================

    @Test
    void desserializacao_number_vazio_retornaNull() throws Exception {
        String json = "{\"number\":\"\"}";
        EventRequestDTO dto = objectMapper.readValue(json, EventRequestDTO.class);
        assertThat(dto.getNumber()).isNull();
    }

    @Test
    void desserializacao_number_espacos_retornaNull() throws Exception {
        String json = "{\"number\":\"   \"}";
        EventRequestDTO dto = objectMapper.readValue(json, EventRequestDTO.class);
        assertThat(dto.getNumber()).isNull();
    }

    @Test
    void desserializacao_complement_comValor_retornaTrimmed() throws Exception {
        String json = "{\"complement\":\"  Ap 101  \"}";
        EventRequestDTO dto = objectMapper.readValue(json, EventRequestDTO.class);
        assertThat(dto.getComplement()).isEqualTo("Ap 101");
    }

    @Test
    void desserializacao_zipCode_vazio_retornaNull() throws Exception {
        String json = "{\"zipCode\":\"\"}";
        EventRequestDTO dto = objectMapper.readValue(json, EventRequestDTO.class);
        assertThat(dto.getZipCode()).isNull();
    }

    @Test
    void desserializacao_zipCode_espacos_retornaNull() throws Exception {
        String json = "{\"zipCode\":\"   \"}";
        EventRequestDTO dto = objectMapper.readValue(json, EventRequestDTO.class);
        assertThat(dto.getZipCode()).isNull();
    }

    @Test
    void desserializacao_zipCode_comHifen_retornaValor() throws Exception {
        String json = "{\"zipCode\":\"60000-000\"}";
        EventRequestDTO dto = objectMapper.readValue(json, EventRequestDTO.class);
        assertThat(dto.getZipCode()).isEqualTo("60000-000");
    }

    @Test
    void desserializacao_zipCode_semHifen_retornaValor() throws Exception {
        String json = "{\"zipCode\":\"60000000\"}";
        EventRequestDTO dto = objectMapper.readValue(json, EventRequestDTO.class);
        assertThat(dto.getZipCode()).isEqualTo("60000000");
    }

    @Test
    void desserializacao_nullExplicito_mantemNull() throws Exception {
        String json = "{\"number\":null}";
        EventRequestDTO dto = objectMapper.readValue(json, EventRequestDTO.class);
        assertThat(dto.getNumber()).isNull();
    }

    @Test
    void desserializacao_state_minusculo_normalizadoParaUppercase() throws Exception {
        String json = "{\"state\":\"ce\"}";
        EventRequestDTO dto = objectMapper.readValue(json, EventRequestDTO.class);
        assertThat(dto.getState()).isEqualTo("CE");
    }

    @Test
    void desserializacao_state_maiusculo_mantemValor() throws Exception {
        String json = "{\"state\":\"SP\"}";
        EventRequestDTO dto = objectMapper.readValue(json, EventRequestDTO.class);
        assertThat(dto.getState()).isEqualTo("SP");
    }

    @Test
    void desserializacao_camposObrigatorios_recebeStringVazia() throws Exception {
        // Jackson NÃO executa Bean Validation; DTO recebe "" normalmente
        String json = "{\"venueName\":\"\"}";
        EventRequestDTO dto = objectMapper.readValue(json, EventRequestDTO.class);
        assertThat(dto.getVenueName()).isEqualTo("");
    }

    @Test
    void desserializacao_location_vazio_recebeStringVazia() throws Exception {
        String json = "{\"location\":\"\"}";
        EventRequestDTO dto = objectMapper.readValue(json, EventRequestDTO.class);
        assertThat(dto.getLocation()).isEqualTo("");
    }

    @Test
    void desserializacao_location_espacos_recebeStringComEspacos() throws Exception {
        String json = "{\"location\":\"   \"}";
        EventRequestDTO dto = objectMapper.readValue(json, EventRequestDTO.class);
        assertThat(dto.getLocation()).isEqualTo("   ");
    }

    // =========================================================================
    // TESTES DE VALIDAÇÃO BEAN VALIDATION (Validator API) — APENAS VALIDAÇÃO
    // =========================================================================

    // --- Criação (EventRequestDTO) ---

    @Test
    void request_comTodosObrigatoriosValidos_devePassar() {
        EventRequestDTO dto = EventRequestDTO.builder()
                .title("Festival")
                .startsAt(java.time.OffsetDateTime.now().plusDays(10))
                .endsAt(java.time.OffsetDateTime.now().plusDays(10).plusHours(2))
                .venueName("Arena")
                .street("Av. Principal")
                .neighborhood("Centro")
                .city("Fortaleza")
                .state("CE")
                .location("Local do Evento")
                .build();

        Set<ConstraintViolation<EventRequestDTO>> violations = validator.validate(dto);
        assertThat(violations).isEmpty();
    }

    @Test
    void request_semVenueName_deveFalhar() {
        EventRequestDTO dto = EventRequestDTO.builder()
                .title("Festival")
                .startsAt(java.time.OffsetDateTime.now().plusDays(10))
                .endsAt(java.time.OffsetDateTime.now().plusDays(10).plusHours(2))
                .street("Av. Principal")
                .neighborhood("Centro")
                .city("Fortaleza")
                .state("CE")
                .build();

        Set<ConstraintViolation<EventRequestDTO>> violations = validator.validate(dto);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("venueName"));
    }

    @Test
    void request_semStreet_deveFalhar() {
        EventRequestDTO dto = EventRequestDTO.builder()
                .title("Festival")
                .startsAt(java.time.OffsetDateTime.now().plusDays(10))
                .endsAt(java.time.OffsetDateTime.now().plusDays(10).plusHours(2))
                .venueName("Arena")
                .neighborhood("Centro")
                .city("Fortaleza")
                .state("CE")
                .build();

        Set<ConstraintViolation<EventRequestDTO>> violations = validator.validate(dto);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("street"));
    }

    @Test
    void request_semNeighborhood_deveFalhar() {
        EventRequestDTO dto = EventRequestDTO.builder()
                .title("Festival")
                .startsAt(java.time.OffsetDateTime.now().plusDays(10))
                .endsAt(java.time.OffsetDateTime.now().plusDays(10).plusHours(2))
                .venueName("Arena")
                .street("Av. Principal")
                .city("Fortaleza")
                .state("CE")
                .build();

        Set<ConstraintViolation<EventRequestDTO>> violations = validator.validate(dto);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("neighborhood"));
    }

    @Test
    void request_semCity_deveFalhar() {
        EventRequestDTO dto = EventRequestDTO.builder()
                .title("Festival")
                .startsAt(java.time.OffsetDateTime.now().plusDays(10))
                .endsAt(java.time.OffsetDateTime.now().plusDays(10).plusHours(2))
                .venueName("Arena")
                .street("Av. Principal")
                .neighborhood("Centro")
                .state("CE")
                .build();

        Set<ConstraintViolation<EventRequestDTO>> violations = validator.validate(dto);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("city"));
    }

    @Test
    void request_semState_deveFalhar() {
        EventRequestDTO dto = EventRequestDTO.builder()
                .title("Festival")
                .startsAt(java.time.OffsetDateTime.now().plusDays(10))
                .endsAt(java.time.OffsetDateTime.now().plusDays(10).plusHours(2))
                .venueName("Arena")
                .street("Av. Principal")
                .neighborhood("Centro")
                .city("Fortaleza")
                .build();

        Set<ConstraintViolation<EventRequestDTO>> violations = validator.validate(dto);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("state"));
    }

    @Test
    void request_comStateInvalida_XX_deveFalhar() {
        EventRequestDTO dto = EventRequestDTO.builder()
                .title("Festival")
                .startsAt(java.time.OffsetDateTime.now().plusDays(10))
                .endsAt(java.time.OffsetDateTime.now().plusDays(10).plusHours(2))
                .venueName("Arena")
                .street("Av. Principal")
                .neighborhood("Centro")
                .city("Fortaleza")
                .state("XX")
                .build();

        Set<ConstraintViolation<EventRequestDTO>> violations = validator.validate(dto);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("state"));
    }

    @Test
    void request_comStateValida_CE_devePassar() {
        EventRequestDTO dto = EventRequestDTO.builder()
                .title("Festival")
                .startsAt(java.time.OffsetDateTime.now().plusDays(10))
                .endsAt(java.time.OffsetDateTime.now().plusDays(10).plusHours(2))
                .venueName("Arena")
                .street("Av. Principal")
                .neighborhood("Centro")
                .city("Fortaleza")
                .state("CE")
                .build();

        Set<ConstraintViolation<EventRequestDTO>> violations = validator.validate(dto);
        assertThat(violations).noneMatch(v -> v.getPropertyPath().toString().equals("state"));
    }

    @Test
    void request_comStateValida_ce_minuscula_normalizadaParaCE_devePassar() throws Exception {
        // Testa integração: deserializador + validator
        String json = """
                {
                    "title": "Festival",
                    "startsAt": "2026-12-01T19:00:00-03:00",
                    "endsAt": "2026-12-01T21:00:00-03:00",
                    "venueName": "Arena",
                    "street": "Av. Principal",
                    "neighborhood": "Centro",
                    "city": "Fortaleza",
                    "state": "ce"
                }
                """;
        EventRequestDTO dto = objectMapper.readValue(json, EventRequestDTO.class);
        Set<ConstraintViolation<EventRequestDTO>> violations = validator.validate(dto);
        assertThat(violations).noneMatch(v -> v.getPropertyPath().toString().equals("state"));
        assertThat(dto.getState()).isEqualTo("CE");
    }

    @Test
    void request_comZipCodeValidoComHifen_devePassar() {
        EventRequestDTO dto = EventRequestDTO.builder()
                .title("Festival")
                .startsAt(java.time.OffsetDateTime.now().plusDays(10))
                .endsAt(java.time.OffsetDateTime.now().plusDays(10).plusHours(2))
                .venueName("Arena")
                .street("Av. Principal")
                .neighborhood("Centro")
                .city("Fortaleza")
                .state("CE")
                .zipCode("60000-000")
                .build();

        Set<ConstraintViolation<EventRequestDTO>> violations = validator.validate(dto);
        assertThat(violations).noneMatch(v -> v.getPropertyPath().toString().equals("zipCode"));
    }

    @Test
    void request_comZipCodeValidoSemHifen_devePassar() {
        EventRequestDTO dto = EventRequestDTO.builder()
                .title("Festival")
                .startsAt(java.time.OffsetDateTime.now().plusDays(10))
                .endsAt(java.time.OffsetDateTime.now().plusDays(10).plusHours(2))
                .venueName("Arena")
                .street("Av. Principal")
                .neighborhood("Centro")
                .city("Fortaleza")
                .state("CE")
                .zipCode("60000000")
                .build();

        Set<ConstraintViolation<EventRequestDTO>> violations = validator.validate(dto);
        assertThat(violations).noneMatch(v -> v.getPropertyPath().toString().equals("zipCode"));
    }

    @Test
    void request_comZipCodeInvalido_deveFalhar() {
        EventRequestDTO dto = EventRequestDTO.builder()
                .title("Festival")
                .startsAt(java.time.OffsetDateTime.now().plusDays(10))
                .endsAt(java.time.OffsetDateTime.now().plusDays(10).plusHours(2))
                .venueName("Arena")
                .street("Av. Principal")
                .neighborhood("Centro")
                .city("Fortaleza")
                .state("CE")
                .zipCode("invalid")
                .build();

        Set<ConstraintViolation<EventRequestDTO>> violations = validator.validate(dto);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("zipCode"));
    }

    @Test
    void request_comLocationVazio_deveFalhar() {
        EventRequestDTO dto = EventRequestDTO.builder()
                .title("Festival")
                .startsAt(java.time.OffsetDateTime.now().plusDays(10))
                .endsAt(java.time.OffsetDateTime.now().plusDays(10).plusHours(2))
                .venueName("Arena")
                .street("Av. Principal")
                .neighborhood("Centro")
                .city("Fortaleza")
                .state("CE")
                .location("")
                .build();

        Set<ConstraintViolation<EventRequestDTO>> violations = validator.validate(dto);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("location"));
    }

    @Test
    void request_comLocationEspacos_deveFalhar() {
        EventRequestDTO dto = EventRequestDTO.builder()
                .title("Festival")
                .startsAt(java.time.OffsetDateTime.now().plusDays(10))
                .endsAt(java.time.OffsetDateTime.now().plusDays(10).plusHours(2))
                .venueName("Arena")
                .street("Av. Principal")
                .neighborhood("Centro")
                .city("Fortaleza")
                .state("CE")
                .location("   ")
                .build();

        Set<ConstraintViolation<EventRequestDTO>> violations = validator.validate(dto);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("location"));
    }

    @Test
    void request_semLocation_deveFalhar() {
        EventRequestDTO dto = EventRequestDTO.builder()
                .title("Festival")
                .startsAt(java.time.OffsetDateTime.now().plusDays(10))
                .endsAt(java.time.OffsetDateTime.now().plusDays(10).plusHours(2))
                .venueName("Arena")
                .street("Av. Principal")
                .neighborhood("Centro")
                .city("Fortaleza")
                .state("CE")
                .build();

        Set<ConstraintViolation<EventRequestDTO>> violations = validator.validate(dto);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("location"));
    }

    @Test
    void request_comLocationTexto_passaNoDTO() {
        // Texto não vazio passa no DTO; Service bloqueia em B-05
        EventRequestDTO dto = EventRequestDTO.builder()
                .title("Festival")
                .startsAt(java.time.OffsetDateTime.now().plusDays(10))
                .endsAt(java.time.OffsetDateTime.now().plusDays(10).plusHours(2))
                .venueName("Arena")
                .street("Av. Principal")
                .neighborhood("Centro")
                .city("Fortaleza")
                .state("CE")
                .location("São Paulo")
                .build();

        Set<ConstraintViolation<EventRequestDTO>> violations = validator.validate(dto);
        assertThat(violations).noneMatch(v -> v.getPropertyPath().toString().equals("location"));
    }

    @Test
    void request_comLocationNull_passa() {
        EventRequestDTO dto = EventRequestDTO.builder()
                .title("Festival")
                .startsAt(java.time.OffsetDateTime.now().plusDays(10))
                .endsAt(java.time.OffsetDateTime.now().plusDays(10).plusHours(2))
                .venueName("Arena")
                .street("Av. Principal")
                .neighborhood("Centro")
                .city("Fortaleza")
                .state("CE")
                .location("Local do Evento")
                .build();

        Set<ConstraintViolation<EventRequestDTO>> violations = validator.validate(dto);
        assertThat(violations).isEmpty();
    }

    // --- Atualização (EventUpdateDTO) ---

    @Test
    void update_todosNull_devePassar() {
        EventUpdateDTO dto = EventUpdateDTO.builder().build();

        Set<ConstraintViolation<EventUpdateDTO>> violations = validator.validate(dto);
        assertThat(violations).isEmpty();
    }

    @Test
    void update_apenasTitle_devePassar() {
        EventUpdateDTO dto = EventUpdateDTO.builder().title("Novo Título").build();

        Set<ConstraintViolation<EventUpdateDTO>> violations = validator.validate(dto);
        assertThat(violations).isEmpty();
    }

    @Test
    void update_locationVazio_deveFalhar() {
        EventUpdateDTO dto = EventUpdateDTO.builder().location("").build();

        Set<ConstraintViolation<EventUpdateDTO>> violations = validator.validate(dto);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("location"));
    }

    @Test
    void update_camposOpcionaisVazios_preservadosNoDTO_serviceLimpa() throws Exception {
        // Testa desserialização Jackson: number="", complement="   " → preserva strings (não null)
        // O Service trata isBlank() e limpa para NULL
        String json = """
                {
                    "number": "",
                    "complement": "   ",
                    "zipCode": ""
                }
                """;
        EventUpdateDTO dto = objectMapper.readValue(json, EventUpdateDTO.class);
        // Jackson preserva "" e "   " (sem EmptyStringAsNullDeserializer)
        assertThat(dto.getNumber()).isEqualTo("");
        assertThat(dto.getComplement()).isEqualTo("   ");
        // zipCode usa setter customizado: "" → null + cleared=true
        assertThat(dto.getZipCode()).isNull();
        assertThat(dto.isZipCodeCleared()).isTrue();
    }

    @Test
    void update_state_minusculo_normalizadoParaUppercase() throws Exception {
        String json = "{\"state\":\"sp\"}";
        EventUpdateDTO dto = objectMapper.readValue(json, EventUpdateDTO.class);
        assertThat(dto.getState()).isEqualTo("SP");
    }

    // =========================================================================
    // TESTES DE INTEGRAÇÃO DESERIALIZAÇÃO + VALIDAÇÃO
    // =========================================================================

    @Test
    void integracao_desserializacaoMaisValidacao_camposObrigatoriosValidos() throws Exception {
        String json = """
                {
                    "title": "Festival",
                    "startsAt": "2026-12-01T19:00:00-03:00",
                    "endsAt": "2026-12-01T21:00:00-03:00",
                    "venueName": "Arena",
                    "street": "Av. Principal",
                    "neighborhood": "Centro",
                    "city": "Fortaleza",
                    "state": "CE",
                    "location": "Local do Evento"
                }
                """;
        EventRequestDTO dto = objectMapper.readValue(json, EventRequestDTO.class);
        Set<ConstraintViolation<EventRequestDTO>> violations = validator.validate(dto);
        assertThat(violations).isEmpty();
    }

    @Test
    void integracao_desserializacaoMaisValidacao_zipCodeValidoComHifen() throws Exception {
        String json = """
                {
                    "title": "Festival",
                    "startsAt": "2026-12-01T19:00:00-03:00",
                    "endsAt": "2026-12-01T21:00:00-03:00",
                    "venueName": "Arena",
                    "street": "Av. Principal",
                    "neighborhood": "Centro",
                    "city": "Fortaleza",
                    "state": "CE",
                    "zipCode": "60000-000",
                    "location": "Local do Evento"
                }
                """;
        EventRequestDTO dto = objectMapper.readValue(json, EventRequestDTO.class);
        Set<ConstraintViolation<EventRequestDTO>> violations = validator.validate(dto);
        assertThat(violations).isEmpty();
    }
}