package com.eventbook.backend.payments;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, UUID> {

    @Query("""
            SELECT pt FROM PaymentTransaction pt
            WHERE pt.order.id = :orderId AND pt.status = :status
            """)
    Optional<PaymentTransaction> findByOrderIdAndStatus(
            @Param("orderId") UUID orderId,
            @Param("status") PaymentStatus status);

    @Query("""
            SELECT COUNT(pt)
            FROM PaymentTransaction pt
            WHERE pt.order.id = :orderId AND pt.status = :status
            """)
    long countByOrderIdAndStatus(
            @Param("orderId") UUID orderId,
            @Param("status") PaymentStatus status);
}
