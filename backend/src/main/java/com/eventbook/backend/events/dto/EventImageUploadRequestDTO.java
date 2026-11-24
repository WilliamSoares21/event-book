package com.eventbook.backend.events.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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
public class EventImageUploadRequestDTO {

    @NotNull
    @Schema(description = "Tipo MIME da imagem (image/webp, image/jpeg, image/png)", example = "image/webp", requiredMode = Schema.RequiredMode.REQUIRED)
    @Pattern(regexp = "^image/(webp|jpeg|png)$", message = "Tipo de arquivo não suportado. Use WebP, JPEG ou PNG.")
    private String contentType;

    @NotBlank
    @Schema(description = "SHA-256 do arquivo em Base64 (para validação de integridade)", requiredMode = Schema.RequiredMode.REQUIRED)
    private String sha256Base64;

    @NotBlank
    @Schema(description = "ID único da operação de escrita (UUID v4)", example = "550e8400-e29b-41d4-a716-446655440000", requiredMode = Schema.RequiredMode.REQUIRED)
    private String writeId;
}