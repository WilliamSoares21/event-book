package com.eventbook.backend.events.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Ocupação por lote individual do evento")
public class TicketLotOccupancyDTO {

    @Schema(description = "ID do lote", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID ticketLotId;

    @Schema(description = "Nome do lote", example = "Lote Promocional")
    private String name;

    @Schema(description = "Capacidade do lote", example = "1000")
    private Long capacity;

    @Schema(description = "Ingressos vendidos neste lote", example = "800")
    private Long sold;

    @Schema(description = "Ingressos reservados neste lote", example = "100")
    private Long reserved;

    @Schema(description = "Disponibilidade do lote (capacity - sold - reserved)", example = "100")
    private Long available;

    @Schema(description = "Check-ins neste lote", example = "750")
    private Long checkedIn;

    @Schema(description = "Taxa de ocupação comercial do lote (%)", example = "80.00")
    private BigDecimal commercialOccupancyRate;

    @Schema(description = "Taxa de comprometimento do lote (%)", example = "90.00")
    private BigDecimal committedOccupancyRate;

    @Schema(description = "Taxa de ocupação física do lote (%)", example = "75.00")
    private BigDecimal physicalOccupancyRate;
}