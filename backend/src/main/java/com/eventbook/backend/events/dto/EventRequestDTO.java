package com.eventbook.backend.events.dto;

import jakarta.validation.constraints.*;
import lombok.*;

import java.time.OffsetDateTime;

@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventRequestDTO {

    @NotBlank
    @Size(max = 255)
    private String title;

    @NotBlank
    @Size(max = 2000)
    private String description;

    @NotNull
    @Future
    private OffsetDateTime startsAt;   

    @NotNull
    @Future
    private OffsetDateTime endsAt;    

    @NotBlank
    @Size(max = 255)
    private String location;
}