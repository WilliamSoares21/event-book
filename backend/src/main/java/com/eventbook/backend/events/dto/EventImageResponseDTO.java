package com.eventbook.backend.events.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventImageResponseDTO {

    @Schema(description = "Identificador da imagem", accessMode = Schema.AccessMode.READ_ONLY)
    private UUID id;

    @Schema(description = "Identificador do evento", accessMode = Schema.AccessMode.READ_ONLY)
    private UUID eventId;

    @Schema(description = "Chave do objeto no R2", accessMode = Schema.AccessMode.READ_ONLY)
    private String objectKey;

    @Schema(description = "URL pública da imagem", accessMode = Schema.AccessMode.READ_ONLY)
    private String publicUrl;

    @Schema(description = "Tipo MIME da imagem", accessMode = Schema.AccessMode.READ_ONLY)
    private String contentType;

    @Schema(description = "Tamanho do arquivo em bytes", accessMode = Schema.AccessMode.READ_ONLY)
    private Long sizeBytes;

    @Schema(description = "Data e hora do upload", accessMode = Schema.AccessMode.READ_ONLY)
    private OffsetDateTime uploadedAt;
}