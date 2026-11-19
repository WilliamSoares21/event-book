package com.eventbook.backend.staff.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventStaffResponseDTO {

    @Schema(description = "Identificador da associação", accessMode = Schema.AccessMode.READ_ONLY)
    private UUID id;

    @Schema(description = "Identificador do evento", accessMode = Schema.AccessMode.READ_ONLY)
    private UUID eventId;

    @Schema(description = "Identificador do usuário staff", accessMode = Schema.AccessMode.READ_ONLY)
    private UUID userId;

    @Schema(description = "Nome do usuário staff", accessMode = Schema.AccessMode.READ_ONLY)
    private String userName;

    @Schema(description = "Email do usuário staff", accessMode = Schema.AccessMode.READ_ONLY)
    private String userEmail;

    @Schema(description = "Data de criação da associação", accessMode = Schema.AccessMode.READ_ONLY)
    private Instant createdAt;
}