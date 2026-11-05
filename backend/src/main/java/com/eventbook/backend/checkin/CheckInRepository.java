package com.eventbook.backend.checkin;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
