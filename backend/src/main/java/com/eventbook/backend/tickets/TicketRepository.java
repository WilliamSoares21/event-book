package com.eventbook.backend.tickets;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TicketRepository extends JpaRepository <Ticket, UUID> {

    Optional<Ticket> findByUniqueCode(String uniqueCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Ticket t where t.uniqueCode = :uniqueCode")
    Optional<Ticket> findByUniqueCodeForUpdate(@Param("uniqueCode") String uniqueCode);

    boolean existsByUniqueCode(String uniqueCode);

    @Query("""
            SELECT COUNT(t)
            FROM Ticket t
            WHERE t.order.ticketLot.event.id = :eventId
            """)
    Long countByEventId(@Param("eventId") UUID eventId);
    
    List<Ticket> findByOrderId(UUID orderId);

    @Query("""
            SELECT new com.eventbook.backend.tickets.BuyerTicketResponse(
                t.id, e.title, e.startsAt, t.uniqueCode, t.status
            )
            FROM Ticket t
            JOIN t.order o
            JOIN o.ticketLot l
            JOIN l.event e
            WHERE o.buyer.id = :buyerId
            ORDER BY t.createdAt DESC, t.id DESC
            """)
    List<BuyerTicketResponse> listByBuyerId(@Param("buyerId") UUID buyerId);
}
