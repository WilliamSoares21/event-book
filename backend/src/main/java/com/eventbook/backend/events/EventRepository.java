package com.eventbook.backend.events;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EventRepository extends JpaRepository<Event, UUID> {
    List<Event> findByStatus(EventStatus status);
    Page<Event> findByStatus(EventStatus status, Pageable pageable);

    List<Event> findByOrganizerIdOrderByStartsAtDesc(UUID organizerId);
}