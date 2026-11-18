package com.eventbook.backend.checkin.dto;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.UUID;

@Value
@Builder
public class CheckInResponseDTO {

    String ticketCode;
    Instant checkedInAt;
    UUID staffId;

    public static CheckInResponseDTO from(com.eventbook.backend.checkin.CheckIn checkIn) {
        return CheckInResponseDTO.builder()
                .ticketCode(checkIn.getTicket().getUniqueCode())
                .checkedInAt(checkIn.getCheckedInAt())
                .staffId(checkIn.getStaff().getId())
                .build();
    }
}