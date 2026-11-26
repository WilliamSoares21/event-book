package com.eventbook.backend.events;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

public interface EventRepository extends JpaRepository<Event, UUID> {
    List<Event> findByStatus(EventStatus status);
    Page<Event> findByStatus(EventStatus status, Pageable pageable);

    List<Event> findByOrganizerIdOrderByStartsAtDesc(UUID organizerId);

    /**
     * Catálogo público de eventos.
     *
     * <p>Com {@code onlyAvailable = true}, devolve apenas eventos que ainda não
     * terminaram ({@code endsAt > now}) e que possuem ao menos um lote vendável
     * (ACTIVE, janela de vendas vigente e estoque livre). O filtro é aplicado no
     * banco, antes da paginação, para que {@code totalElements}/{@code totalPages}
     * reflitam o resultado filtrado, sem N+1.
     */
    @Query("""
        SELECT e FROM Event e
        WHERE e.status = 'PUBLISHED'
        AND (:q IS NULL OR :q = ''
             OR LOWER(e.title) LIKE LOWER(CONCAT('%', :q, '%')) ESCAPE '\\'
             OR LOWER(e.description) LIKE LOWER(CONCAT('%', :q, '%')) ESCAPE '\\'
             OR LOWER(e.venueName) LIKE LOWER(CONCAT('%', :q, '%')) ESCAPE '\\'
             OR LOWER(e.location) LIKE LOWER(CONCAT('%', :q, '%')) ESCAPE '\\')
        AND (:city IS NULL OR :city = '' OR LOWER(e.city) = LOWER(:city))
        AND (:state IS NULL OR :state = '' OR UPPER(e.state) = UPPER(:state))
        AND (CAST(:startDate AS java.time.OffsetDateTime) IS NULL OR e.startsAt >= :startDate)
        AND (CAST(:endDate AS java.time.OffsetDateTime) IS NULL OR e.startsAt < :endDate)
        AND (:onlyAvailable = false
             OR (e.endsAt > :now AND EXISTS (
                  SELECT 1 FROM TicketLot tl
                  WHERE tl.event.id = e.id
                    AND tl.status = 'ACTIVE'
                    AND tl.salesStart <= :lotNow
                    AND tl.salesEnd >= :lotNow
                    AND (tl.capacity - tl.sold - tl.reserved) > 0)))
        """)
    Page<Event> findPublishedWithFilters(
        @Param("q") String q,
        @Param("city") String city,
        @Param("state") String state,
        @Param("startDate") OffsetDateTime startDate,
        @Param("endDate") OffsetDateTime endDate,
        @Param("onlyAvailable") boolean onlyAvailable,
        @Param("now") OffsetDateTime now,
        @Param("lotNow") Instant lotNow,
        Pageable pageable
    );

    /**
     * Busca publicada sem o filtro de disponibilidade (uso interno).
     */
    default Page<Event> findPublishedWithFilters(
            String q,
            String city,
            String state,
            OffsetDateTime startDate,
            OffsetDateTime endDate,
            Pageable pageable) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        return findPublishedWithFilters(q, city, state, startDate, endDate, false, now, now.toInstant(), pageable);
    }
}
