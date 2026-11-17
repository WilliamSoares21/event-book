package com.eventbook.backend.cards;

import com.eventbook.backend.users.User;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "simulated_cards")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SimulatedCard {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "last_four_digits", nullable = false, length = 4)
    private String lastFourDigits;

    @Column(nullable = false, length = 20)
    private String brand;

    @Column(name = "expiry_month", nullable = false)
    private Integer expiryMonth;

    @Column(name = "expiry_year", nullable = false)
    private Integer expiryYear;

    @Column(name = "total_limit", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalLimit;

    @Column(name = "available_limit", nullable = false, precision = 12, scale = 2)
    private BigDecimal availableLimit;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CardStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Version
    private Long version;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
        if (status == null) status = CardStatus.ACTIVE;
        if (availableLimit == null) availableLimit = totalLimit;
    }
}