package com.eventbook.backend.events;

public record EventUpdateDTO( 
        String title,
        String description,
        String date,
        String time,
        String location
) {}
