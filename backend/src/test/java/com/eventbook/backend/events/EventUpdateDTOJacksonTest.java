package com.eventbook.backend.events;

import com.eventbook.backend.events.dto.EventUpdateDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EventUpdateDTOJacksonTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void omitido() throws Exception {
        EventUpdateDTO dto = mapper.readValue("{}", EventUpdateDTO.class);
        assertThat(dto.getZipCode()).isNull();
        assertThat(dto.isZipCodeCleared()).isFalse();
    }

    @Test
    void nullExplicito() throws Exception {
        EventUpdateDTO dto = mapper.readValue("{\"zipCode\": null}", EventUpdateDTO.class);
        assertThat(dto.getZipCode()).isNull();
        assertThat(dto.isZipCodeCleared()).isFalse();
    }

    @Test
    void stringVazia() throws Exception {
        EventUpdateDTO dto = mapper.readValue("{\"zipCode\": \"\"}", EventUpdateDTO.class);
        assertThat(dto.getZipCode()).isNull();
        assertThat(dto.isZipCodeCleared()).isTrue();
    }

    @Test
    void espacosEmBranco() throws Exception {
        EventUpdateDTO dto = mapper.readValue("{\"zipCode\": \"   \"}", EventUpdateDTO.class);
        assertThat(dto.getZipCode()).isNull();
        assertThat(dto.isZipCodeCleared()).isTrue();
    }

    @Test
    void cepValido() throws Exception {
        EventUpdateDTO dto = mapper.readValue("{\"zipCode\": \"60000-000\"}", EventUpdateDTO.class);
        assertThat(dto.getZipCode()).isEqualTo("60000-000");
        assertThat(dto.isZipCodeCleared()).isFalse();
    }

    @Test
    void cepValidoSemHifen() throws Exception {
        EventUpdateDTO dto = mapper.readValue("{\"zipCode\": \"60000000\"}", EventUpdateDTO.class);
        assertThat(dto.getZipCode()).isEqualTo("60000000");
        assertThat(dto.isZipCodeCleared()).isFalse();
    }

    @Test
    void numberComplementPreservamEmptyString() throws Exception {
        // number e complement SEM deserializador; "" chega como "", não null
        EventUpdateDTO dto = mapper.readValue("{\"number\": \"\", \"complement\": \"\"}", EventUpdateDTO.class);
        assertThat(dto.getNumber()).isEqualTo("");
        assertThat(dto.getComplement()).isEqualTo("");
    }

    @Test
    void numberComplementEspacosPreservados() throws Exception {
        EventUpdateDTO dto = mapper.readValue("{\"number\": \"   \", \"complement\": \"  \"}", EventUpdateDTO.class);
        assertThat(dto.getNumber()).isEqualTo("   ");
        assertThat(dto.getComplement()).isEqualTo("  ");
    }

    @Test
    void numberComplementOmitidos() throws Exception {
        EventUpdateDTO dto = mapper.readValue("{}", EventUpdateDTO.class);
        assertThat(dto.getNumber()).isNull();
        assertThat(dto.getComplement()).isNull();
    }

    @Test
    void numberComplementNullExplicito() throws Exception {
        EventUpdateDTO dto = mapper.readValue("{\"number\": null, \"complement\": null}", EventUpdateDTO.class);
        assertThat(dto.getNumber()).isNull();
        assertThat(dto.getComplement()).isNull();
    }

    @Test
    void numberComplementValorPreenchido() throws Exception {
        EventUpdateDTO dto = mapper.readValue("{\"number\": \"2901\", \"complement\": \"Bloco B\"}", EventUpdateDTO.class);
        assertThat(dto.getNumber()).isEqualTo("2901");
        assertThat(dto.getComplement()).isEqualTo("Bloco B");
    }
}