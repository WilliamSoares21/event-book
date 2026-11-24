package com.eventbook.backend.events;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventImageRepository extends JpaRepository<EventImage, UUID> {

    Optional<EventImage> findByEventId(UUID eventId);

    List<EventImage> findByEventIdIn(List<UUID> eventIds);

    @Modifying
    @Transactional
    @Query("DELETE FROM EventImage ei WHERE ei.event.id = :eventId")
    void deleteByEventId(@Param("eventId") UUID eventId);

    boolean existsByEventId(UUID eventId);
}