package com.eventbook.backend.orders;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    List<Order> findByBuyerId(UUID buyerId);

    @Query("""
            SELECT COUNT(o)
            FROM Order o
            WHERE o.ticketLot.event.id = :eventId
              AND o.status = :status
            """)
    Long countByEventIdAndStatus(
        @Param("eventId") UUID eventId, 
        @Param("status") OrderStatus status);

    List<Order> findByStatusAndExpiresAtBefore(
            OrderStatus status,
            Instant instant
    );
}