package com.eventbook.backend.events.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventImageConfirmRequestDTO {

    @NotBlank
    @Schema(description = "Chave do objeto no R2 retornada na geração da presigned URL", requiredMode = Schema.RequiredMode.REQUIRED)
    private String objectKey;

    @NotBlank
    @Schema(description = "ID único da operação de escrita (UUID v4)", requiredMode = Schema.RequiredMode.REQUIRED)
    private String writeId;

    @NotBlank
    @Schema(description = "SHA-256 do arquivo em Base64 (para validação de integridade)", requiredMode = Schema.RequiredMode.REQUIRED)
    private String sha256Base64;
}