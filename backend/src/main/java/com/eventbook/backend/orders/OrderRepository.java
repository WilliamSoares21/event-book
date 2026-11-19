package com.eventbook.backend.orders;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    @Query("""
            SELECT o FROM Order o
            JOIN FETCH o.ticketLot lot
            WHERE o.buyer.id = :buyerId
            ORDER BY o.createdAt DESC
            """)
    Page<Order> findByBuyerId(@Param("buyerId") UUID buyerId, Pageable pageable);

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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") UUID id);
}