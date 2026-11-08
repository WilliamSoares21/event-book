package com.eventbook.backend.events.dto;

import jakarta.validation.constraints.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.OffsetDateTime;

@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
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
    @Size(max = 255)
    private String location;
}