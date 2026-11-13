package com.eventbook.backend.events.dto;

import com.eventbook.backend.events.Event;
import com.eventbook.backend.events.EventStatus;
import com.eventbook.backend.lots.dto.TicketLotResponseDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Visão administrativa de um evento do organizador autenticado.
 * Reaproveita os campos do DTO público e adiciona os lotes (ativos e inativos).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrganizerEventResponseDTO {

    @Schema(description = "Identificador do evento", accessMode = Schema.AccessMode.READ_ONLY)
    private UUID id;
    private String title;
    private String description;
    private OffsetDateTime startsAt;
    private OffsetDateTime endsAt;
    private String location;
    private EventStatus status;
    private Long version;
    private List<TicketLotResponseDTO> ticketLots;

    public static OrganizerEventResponseDTO from(Event event, List<TicketLotResponseDTO> lots) {
        return OrganizerEventResponseDTO.builder()
                .id(event.getId())
                .title(event.getTitle())
                .description(event.getDescription())
                .startsAt(event.getStartsAt())
                .endsAt(event.getEndsAt())
                .location(event.getLocation())
                .status(event.getStatus())
                .version(event.getVersion())
                .ticketLots(lots)
                .build();
    }
}
