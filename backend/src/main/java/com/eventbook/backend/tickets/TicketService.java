package com.eventbook.backend.tickets;

import com.eventbook.backend.tickets.dto.BuyerTicketResponse;
import com.eventbook.backend.users.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TicketService {

    private final TicketRepository ticketRepository;

    @Transactional(readOnly = true)
    public List<BuyerTicketResponse> findOwnTickets(UUID buyerId) {
        return ticketRepository.findByBuyerId(buyerId).stream()
                .map(this::toBuyerTicketResponse)
                .toList();
    }

    private BuyerTicketResponse toBuyerTicketResponse(Ticket ticket) {
        return new BuyerTicketResponse(
                ticket.getId(),
                ticket.getOrder().getTicketLot().getEvent().getTitle(),
                ticket.getOrder().getTicketLot().getEvent().getStartsAt().toInstant(),
                ticket.getUniqueCode(),
                ticket.getStatus()
        );
    }
}