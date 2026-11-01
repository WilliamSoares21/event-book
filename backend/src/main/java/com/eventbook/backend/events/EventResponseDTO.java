package com.eventbook.backend.events;

public record EventResponseDTO(
        String id,
        String title,
        String description,
        String date,
        String time,
        String location,
        String organizerId,
        String status
) {}