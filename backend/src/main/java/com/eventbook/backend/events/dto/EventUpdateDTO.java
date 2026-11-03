package com.eventbook.backend.events.dto;

import jakarta.validation.constraints.*;
import lombok.*;

import java.time.OffsetDateTime;

@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventUpdateDTO {

    @Size(max = 255)
    private String title;

    @Size(max = 2000)
    private String description;

    @Future
    private OffsetDateTime startsAt;

    @Future
    private OffsetDateTime endsAt;

    @Size(max = 255)
    private String location;
}