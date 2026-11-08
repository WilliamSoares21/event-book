package com.eventbook.backend.tickets;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TicketRepository extends JpaRepository <Ticket, UUID> {

    Optional<Ticket> findByUniqueCode(String uniqueCode);

    @Query("""
            SELECT COUNT(t)
            FROM Ticket t
            WHERE t.order.ticketLot.event.id = :eventId
            """)
    Long countByEventId(@Param("eventId") UUID eventId);
    
    List<Ticket> findByOrderId(UUID orderId);
}
