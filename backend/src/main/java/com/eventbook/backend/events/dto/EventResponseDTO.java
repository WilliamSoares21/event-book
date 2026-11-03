package com.eventbook.backend.events.dto;

import com.eventbook.backend.events.EventStatus;
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
public class EventResponseDTO {

    private UUID id;
    private String title;
    private String description;
    private OffsetDateTime startsAt;
    private OffsetDateTime endsAt;
    private String location;
    private UUID organizerId;
    private EventStatus status;
    private Long version;
}