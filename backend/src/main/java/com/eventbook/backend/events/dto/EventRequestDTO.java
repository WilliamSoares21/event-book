package com.eventbook.backend.events.dto;

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

    @Schema(description = "Local do evento", example = "São Paulo", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank
    @Size(max = 255)
    private String location;
}