package com.eventbook.backend.tickets;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
            SELECT t FROM Ticket t
            JOIN FETCH t.order o
            JOIN FETCH o.ticketLot lot
            JOIN FETCH lot.event e
            WHERE o.buyer.id = :buyerId
            ORDER BY t.createdAt DESC
            """)
    Page<Ticket> findByBuyerId(@Param("buyerId") UUID buyerId, Pageable pageable);
}
