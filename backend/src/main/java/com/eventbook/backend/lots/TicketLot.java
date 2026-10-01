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
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TicketLot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

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
}
