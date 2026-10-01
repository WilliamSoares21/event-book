package com.eventbook.backend.checkin;

import com.eventbook.backend.tickets.Ticket;
import com.eventbook.backend.users.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "checkins")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CheckIn {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false, unique = true)
    private Ticket ticket;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "staff_id", nullable = false)
    private User staff;

    @Column(name = "checked_in_at", nullable = false)
    private Instant checkedInAt;

    @PrePersist
    void onCreate() {
        if (checkedInAt == null) {
            checkedInAt = Instant.now();
        }
    }
}
