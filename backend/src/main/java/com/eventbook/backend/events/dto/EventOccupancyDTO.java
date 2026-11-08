package com.eventbook.backend.events.dto;

import java.util.UUID;

import lombok.*;

@Getter 
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventOccupancyDTO {

    private UUID eventId;
    private Long capacity;        // total de ingressos do evento
    private Long reserved;        // ingressos reservados (status PENDING)
    private Long sold;            // tickets já confirmados
    private Long checkedIn;       // ingressos já validados
    private Long available;       // capacity - (reserved + sold)
}