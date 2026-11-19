package com.eventbook.backend.lots;

import com.eventbook.backend.events.projection.EventOccupancySummaryProjection;
import com.eventbook.backend.events.projection.TicketLotOccupancyProjection;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TicketLotRepository extends JpaRepository<TicketLot, UUID> {

    List<TicketLot> findByEventId(UUID eventId);

    List<TicketLot> findByEventIdInOrderByNameAsc(Collection<UUID> eventIds);

    boolean existsByEventIdAndStatus(UUID eventId, LotStatus status);

    @Query("""
            SELECT COALESCE(SUM(t.capacity), 0)
            FROM TicketLot t
            WHERE t.event.id = :eventId
            """)
    Long sumCapacityByEvent(@Param("eventId") UUID eventId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TicketLot t where t.id = :id")
    Optional<TicketLot> findByIdForUpdate(@Param("id") UUID id);

    @Query("""
            SELECT new com.eventbook.backend.events.projection.EventOccupancySummaryProjection(
                e.id, e.title,
                COALESCE(SUM(tl.capacity), 0),
                COALESCE(SUM(tl.sold), 0),
                COALESCE(SUM(tl.reserved), 0),
                COALESCE((
                    SELECT COUNT(c)
                    FROM CheckIn c
                    JOIN c.ticket t
                    JOIN t.order o
                    JOIN o.ticketLot tl2
                    WHERE tl2.event.id = e.id
                ), 0L)
            )
            FROM Event e
            LEFT JOIN TicketLot tl ON tl.event.id = e.id
            WHERE e.id = :eventId
            GROUP BY e.id, e.title
            """)
    Optional<EventOccupancySummaryProjection> findOccupancySummaryByEventId(@Param("eventId") UUID eventId);

    @Query("""
            SELECT tl.id, tl.name, tl.capacity, tl.sold, tl.reserved
            FROM TicketLot tl
            WHERE tl.event.id = :eventId
            ORDER BY tl.name
            """)
    List<Object[]> findOccupancyByEventIdRaw(@Param("eventId") UUID eventId);

    default List<TicketLotOccupancyProjection> findOccupancyByEventId(UUID eventId) {
        return findOccupancyByEventIdRaw(eventId).stream()
            .map(row -> new com.eventbook.backend.events.projection.TicketLotOccupancyProjection(
                (UUID) row[0],
                (String) row[1],
                ((Number) row[2]).longValue(),
                ((Number) row[3]).longValue(),
                ((Number) row[4]).longValue(),
                0L
            ))
            .toList();
    }
}