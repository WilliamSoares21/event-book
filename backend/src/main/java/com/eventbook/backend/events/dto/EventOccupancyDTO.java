package com.eventbook.backend.events.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Ocupação consolidada do evento com métricas e breakdown por lote")
public class EventOccupancyDTO {

    @Schema(description = "ID do evento", example = "550e8400-e29b-41d4-a716-446655440000")
    private UUID eventId;

    @Schema(description = "Nome do evento", example = "Rock in Rio 2025")
    private String eventName;

    @Schema(description = "Capacidade total do evento (soma de todos os lotes)", example = "50000")
    private Long capacity;

    @Schema(description = "Ingressos vendidos (status PAID)", example = "35000")
    private Long sold;

    @Schema(description = "Ingressos reservados (status PENDING)", example = "5000")
    private Long reserved;

    @Schema(description = "Disponibilidade atual (capacity - sold - reserved). Pode ser negativo em caso de inconsistência de dados", example = "10000")
    private Long available;

    @Schema(description = "Check-ins realizados", example = "28000")
    private Long checkedIn;

    @Schema(description = "Taxa de ocupação comercial (%) = sold / capacity * 100", example = "70.00")
    private BigDecimal commercialOccupancyRate;

    @Schema(description = "Taxa de comprometimento (%) = (sold + reserved) / capacity * 100", example = "80.00")
    private BigDecimal committedOccupancyRate;

    @Schema(description = "Taxa de ocupação física (%) = checkedIn / capacity * 100", example = "56.00")
    private BigDecimal physicalOccupancyRate;

    @Schema(description = "Breakdown por lote do evento")
    private List<TicketLotOccupancyDTO> ticketLots;
}