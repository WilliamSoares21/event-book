package com.eventbook.backend.events;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface EventRepository extends JpaRepository<Event, UUID> {
    List<Event> findByStatus(EventStatus status);
    Page<Event> findByStatus(EventStatus status, Pageable pageable);

    List<Event> findByOrganizerIdOrderByStartsAtDesc(UUID organizerId);

    @Query("""
        SELECT e FROM Event e 
        WHERE e.status = 'PUBLISHED'
        AND (:q IS NULL OR :q = '' 
             OR LOWER(e.title) LIKE LOWER(CONCAT('%', :q, '%')) ESCAPE '\\' 
             OR LOWER(e.description) LIKE LOWER(CONCAT('%', :q, '%')) ESCAPE '\\'
             OR LOWER(e.venueName) LIKE LOWER(CONCAT('%', :q, '%')) ESCAPE '\\')
        AND (:city IS NULL OR :city = '' OR LOWER(e.city) = LOWER(:city))
        AND (:state IS NULL OR :state = '' OR UPPER(e.state) = UPPER(:state))
        AND (CAST(:startDate AS java.time.OffsetDateTime) IS NULL OR e.startsAt >= :startDate)
        AND (CAST(:endDate AS java.time.OffsetDateTime) IS NULL OR e.startsAt < :endDate)
    """)
    Page<Event> findPublishedWithFilters(
        @Param("q") String q,
        @Param("city") String city,
        @Param("state") String state,
        @Param("startDate") OffsetDateTime startDate,
        @Param("endDate") OffsetDateTime endDate,
        Pageable pageable
    );
}