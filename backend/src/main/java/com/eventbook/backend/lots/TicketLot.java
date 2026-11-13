package com.eventbook.backend.lots;

import com.eventbook.backend.events.Event;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ticket_lots")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketLot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Package-private setter for test/Hibernate
    void setId(UUID id) {
        this.id = id;
    }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Column(nullable = false)
    private String name;

    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false)
    private Integer capacity;

    @Column(nullable = false)
    @Builder.Default
    private Integer sold = 0;

    @Column(nullable = false)
    @Builder.Default
    private Integer reserved = 0;

    @Column(name = "sales_start", nullable = false)
    private Instant salesStart;

    @Column(name = "sales_end", nullable = false)
    private Instant salesEnd;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LotStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;


    @Version
    private Long version;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (status == null) {
            status = LotStatus.ACTIVE;
        }
    }

    @Transient
    public int availableCapacity() {
        return capacity - sold - reserved;
    }

    // Package-private setters for fields updated by TicketLotService
    void setName(String name) {
        this.name = name;
    }

    void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    void setCapacity(Integer capacity) {
        this.capacity = capacity;
    }

    void setSalesStart(Instant salesStart) {
        this.salesStart = salesStart;
    }

    void setSalesEnd(Instant salesEnd) {
        this.salesEnd = salesEnd;
    }

    void setStatus(LotStatus status) {
        this.status = status;
    }

    // Domain methods for controlled modification of sold/reserved
    public void incrementReserved(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("A quantidade a reservar deve ser positiva");
        }
        if (this.sold + this.reserved + quantity > this.capacity) {
            throw new IllegalStateException("Capacidade insuficiente no lote");
        }
        this.reserved += quantity;
    }

    public void incrementSold(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("A quantidade a vender deve ser positiva");
        }
        if (this.sold + this.reserved + quantity > this.capacity) {
            throw new IllegalStateException("Capacidade insuficiente no lote");
        }
        this.sold += quantity;
    }

    public void decrementReserved(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("A quantidade a liberar deve ser positiva");
        }
        if (this.reserved < quantity) {
            throw new IllegalStateException("Reserva insuficiente para liberar");
        }
        this.reserved -= quantity;
    }
}