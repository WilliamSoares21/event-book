package com.eventbook.backend.checkin;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface CheckInRepository extends JpaRepository <CheckIn, UUID> {

    // Tipo de retorno alterado para CheckIn e final do método para 'Id'
    Optional<CheckIn> findByTicketId(UUID ticketId);

    @Query("""
            SELECT COUNT(c)
            FROM CheckIn c
            WHERE c.ticket.order.ticketLot.event.id = :eventId
            """)
    Long countByEventId(@Param("eventId") UUID eventId);

    @Query("""
            SELECT o.ticketLot.id, COUNT(c)
            FROM CheckIn c
            JOIN c.ticket t
            JOIN t.order o
            WHERE o.ticketLot.event.id = :eventId
            GROUP BY o.ticketLot.id
            """)
    List<Object[]> countCheckInsByEventIdGroupedByLotRaw(@Param("eventId") UUID eventId);

    default Map<UUID, Long> countCheckInsByEventIdGroupedByLot(UUID eventId) {
        return countCheckInsByEventIdGroupedByLotRaw(eventId).stream()
            .collect(java.util.stream.Collectors.toMap(
                row -> (UUID) row[0],
                row -> (Long) row[1]
            ));
    }
}
