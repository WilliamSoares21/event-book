package com.eventbook.backend.orders;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    List<Order> findByBuyerId(UUID buyerId);

    List<Order> findByStatusAndExpiresAtBefore(
            OrderStatus status,
            Instant instant
    );
}