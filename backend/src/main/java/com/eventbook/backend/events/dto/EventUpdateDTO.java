package com.eventbook.backend.events.dto;

import com.eventbook.backend.common.validation.ValidBrazilianState;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;import java.time.OffsetDateTime;

@Getter @Setter
@NoArgsConstructor
public class EventUpdateDTO {

    @Schema(description = "Novo título do evento", example = "Festival EventBook Atualizado")
    @Size(max = 255)
    private String title;

    @Schema(description = "Nova descrição do evento", example = "Descrição atualizada.")
    @Size(max = 2000)
    private String description;

    @Schema(description = "Nova data e hora de início", example = "2026-11-20T19:00:00-03:00")
    @Future
    private OffsetDateTime startsAt;

    @Schema(description = "Nova data e hora de término", example = "2026-11-20T23:00:00-03:00")
    @Future
    private OffsetDateTime endsAt;

    @Schema(description = "Novo local do evento", example = "São Paulo")
    @Size(min = 1, max = 255)
    private String location;

    @Schema(description = "Número do endereço", example = "2901")
    @Size(max = 20)
    private String number;

    @Schema(description = "Complemento", example = "Bloco B")
    @Size(max = 100)
    private String complement;

    @Schema(description = "Cidade", example = "Fortaleza")
    @Size(max = 100)
    private String city;

    @Schema(description = "UF 2 letras maiúsculas", example = "CE")
    @ValidBrazilianState
    private String state;

    @Schema(description = "CEP formato 00000-000 ou 00000000", example = "60000-000")
    @Pattern(regexp = "^\\d{5}-?\\d{3}$", message = "CEP deve estar no formato 00000-000 ou 00000000")
    @Size(max = 9)
    private String zipCode;

    // PATCH: "" limpa o CEP; null/omitido não altera. Boolean (não boolean) porque o
    // Jackson 3 falha ao mapear null em primitivo.
    @Getter(lombok.AccessLevel.NONE)
    @Setter(lombok.AccessLevel.NONE)
    private Boolean zipCodeCleared;

    public boolean isZipCodeCleared() {
        return Boolean.TRUE.equals(zipCodeCleared);
    }

    public void setZipCode(String zipCode) {
        this.zipCodeCleared = zipCode != null && zipCode.isBlank();
        this.zipCode = this.zipCodeCleared ? null : zipCode;
    }

    public void setState(String state) {
        this.state = (state == null || state.isBlank()) ? null : state.trim().toUpperCase();
    }

    public void setCity(String city) {
        this.city = (city == null || city.isBlank()) ? null : city.trim();
    }

    // Builder sobre um construtor privado: se o all-args fosse público, o Jackson 3 o
    // escolheria como creator implícito e ignoraria os setters de normalização.
    @Builder
    private EventUpdateDTO(String title, String description, OffsetDateTime startsAt,
                           OffsetDateTime endsAt, String location, String number,
                           String complement, String city, String state, String zipCode,
                           Boolean zipCodeCleared) {
        this.title = title;
        this.description = description;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.location = location;
        this.number = number;
        this.complement = complement;
        this.city = city;
        this.state = state;
        this.zipCode = zipCode;
        this.zipCodeCleared = zipCodeCleared;
    }
}
