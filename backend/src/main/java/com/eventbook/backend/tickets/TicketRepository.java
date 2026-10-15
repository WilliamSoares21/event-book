package com.eventbook.backend.tickets;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TicketRepository extends JpaRepository <Ticket, UUID> {

    Optional<Ticket> findByUniqueCode(String uniqueCode);

    List<Ticket> findByOrderId(UUID orderId);
}
