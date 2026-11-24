package com.eventbook.backend.events.dto;

import com.eventbook.backend.events.EventStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventResponseDTO {

    @Schema(description = "Identificador do evento", accessMode = Schema.AccessMode.READ_ONLY)
    private UUID id;
    @Schema(description = "Título do evento")
    private String title;
    @Schema(description = "Descrição do evento")
    private String description;
    @Schema(description = "Data e hora de início")
    private OffsetDateTime startsAt;
    @Schema(description = "Data e hora de término")
    private OffsetDateTime endsAt;
    @Schema(description = "Local do evento")
    private String location;
    @Schema(description = "Identificador do organizador", accessMode = Schema.AccessMode.READ_ONLY)
    private UUID organizerId;
    @Schema(description = "Status controlado pelo backend", accessMode = Schema.AccessMode.READ_ONLY)
    private EventStatus status;
    @Schema(description = "Versão para controle otimista", accessMode = Schema.AccessMode.READ_ONLY)
    private Long version;
    @Schema(description = "URL da imagem de capa do evento", accessMode = Schema.AccessMode.READ_ONLY)
    private String coverImageUrl;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Lotes à venda com estoque, retornados na consulta de detalhes")
    private List<AvailableTicketLot> availableTicketLots;

    public record AvailableTicketLot(UUID id, String name, BigDecimal unitPrice, int availableQuantity) {}
}