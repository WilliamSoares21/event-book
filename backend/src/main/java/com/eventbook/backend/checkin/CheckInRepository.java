package com.eventbook.backend.checkin;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CheckInRepository extends JpaRepository <CheckIn, UUID> {

    // CORREÇÃO: Tipo de retorno alterado para CheckIn e final do método para 'Id'
    Optional<CheckIn> findByTicketId(UUID ticketId);
}
