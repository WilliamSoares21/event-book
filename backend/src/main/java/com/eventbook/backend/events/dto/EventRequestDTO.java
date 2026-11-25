package com.eventbook.backend.events.dto;

import com.eventbook.backend.common.deserializer.EmptyStringAsNullDeserializer;
import com.eventbook.backend.common.deserializer.UpperCaseTrimDeserializer;
import com.eventbook.backend.common.validation.ValidBrazilianState;
import tools.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.OffsetDateTime;

@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventRequestDTO {

    @Schema(description = "Título do evento", example = "Festival EventBook", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank
    @Size(max = 255)
    private String title;

    @Schema(description = "Descrição opcional do evento", example = "Evento de demonstração.")
    @Size(max = 2000)
    private String description;

    @Schema(description = "Data e hora de início", example = "2026-11-20T19:00:00-03:00", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    @Future
    private OffsetDateTime startsAt;   

    @Schema(description = "Data e hora de término", example = "2026-11-20T23:00:00-03:00", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull
    @Future
    private OffsetDateTime endsAt;    

    @Schema(description = "Nome do estabelecimento/local", example = "Arena Castelão", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank
    @Size(max = 255)
    private String venueName;

    @Schema(description = "Logradouro", example = "Av. Alberto Craveiro", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank
    @Size(max = 255)
    private String street;

    @Schema(description = "Número do endereço", example = "2901")
    @Size(max = 20)
    @JsonDeserialize(using = EmptyStringAsNullDeserializer.class)
    private String number;

    @Schema(description = "Complemento", example = "Bloco B")
    @Size(max = 100)
    @JsonDeserialize(using = EmptyStringAsNullDeserializer.class)
    private String complement;

    @Schema(description = "Bairro", example = "Castelão", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank
    @Size(max = 100)
    private String neighborhood;

    @Schema(description = "Cidade", example = "Fortaleza", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank
    @Size(max = 100)
    @JsonDeserialize(using = EmptyStringAsNullDeserializer.class)
    private String city;

    @Schema(description = "UF 2 letras maiúsculas", example = "CE", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank
    @ValidBrazilianState
    @JsonDeserialize(using = UpperCaseTrimDeserializer.class)
    private String state;

    @Schema(description = "CEP formato 00000-000 ou 00000000", example = "60000-000")
    @Pattern(regexp = "^\\d{5}-?\\d{3}$", message = "CEP deve estar no formato 00000-000 ou 00000000")
    @Size(max = 9)
    @JsonDeserialize(using = EmptyStringAsNullDeserializer.class)
    private String zipCode;

    @Schema(description = "Local do evento legado/computado (preenchido automaticamente)", example = "São Paulo")
    @Size(max = 255)
    private String location;

    public void setNumber(String number) {
        this.number = (number == null || number.isBlank()) ? null : number.trim();
    }

    public void setComplement(String complement) {
        this.complement = (complement == null || complement.isBlank()) ? null : complement.trim();
    }

    public void setCity(String city) {
        this.city = (city == null || city.isBlank()) ? null : city.trim();
    }

    public void setState(String state) {
        this.state = (state == null || state.isBlank()) ? null : state.trim().toUpperCase();
    }

    public void setZipCode(String zipCode) {
        this.zipCode = (zipCode == null || zipCode.isBlank()) ? null : zipCode.trim();
    }
}