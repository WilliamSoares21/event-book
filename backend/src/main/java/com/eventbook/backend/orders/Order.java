package com.eventbook.backend.orders;

import com.eventbook.backend.lots.TicketLot;
import com.eventbook.backend.users.User;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "buyer_id", nullable = false)
    private User buyer;

    // @Deprecated: Mantido para compatibilidade com pedidos legados (migração V12)
    // Será removido em versão futura após migração completa
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_lot_id")
    private TicketLot ticketLot;

    // @Deprecated: Mantido para compatibilidade com pedidos legados
    @Column
    private Integer quantity;

    // @Deprecated: Mantido para compatibilidade com pedidos legados
    @Column(name = "unit_price_snapshot", precision = 10, scale = 2)
    private BigDecimal unitPriceSnapshot;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<OrderItem> items = new ArrayList<>();

    @Column(name = "total_price_snapshot", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal totalPriceSnapshot = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (status == null) {
            status = OrderStatus.PENDING;
        }
        if (totalPriceSnapshot == null) {
            totalPriceSnapshot = BigDecimal.ZERO;
        }
    }

    // Métodos de conveniência para compatibilidade
    public boolean isLegacyOrder() {
        return ticketLot != null && quantity != null && unitPriceSnapshot != null;
    }

    public BigDecimal calculateTotal() {
        return items.stream()
            .map(OrderItem::getTotalPrice)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
