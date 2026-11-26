package com.eventbook.backend.tickets;

import com.eventbook.backend.lots.TicketLot;
import com.eventbook.backend.orders.Order;
import com.eventbook.backend.orders.OrderItem;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tickets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;


    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    // Referência ao item do pedido (para pedidos agrupados)
    // Nullable para compatibilidade com pedidos legados
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_item_id")
    private OrderItem orderItem;

    @Column(name = "unique_code", nullable = false, unique = true, length = 64)
    private String uniqueCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TicketStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (status == null) {
            status = TicketStatus.VALID;
        }
    }

    // Método helper para obter o lote (compatibilidade legacy + agrupado)
    public TicketLot getTicketLot() {
        if (orderItem != null) {
            return orderItem.getTicketLot();
        }
        return order.getTicketLot();
    }
}
