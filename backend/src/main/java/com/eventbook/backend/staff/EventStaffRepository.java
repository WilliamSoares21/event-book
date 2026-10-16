package com.eventbook.backend.staff;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface EventStaffRepository extends JpaRepository <EventStaff, UUID> {

    // CORREÇÃO: Adicionado o "And" para separar as propriedades de busca
    Optional<EventStaff> findByEventIdAndUserId(UUID eventId, UUID userId);

    // Este já estava correto!
    boolean existsByEventIdAndUserId(UUID eventId, UUID userId);
}
