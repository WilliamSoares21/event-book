package com.eventbook.backend.tickets;

import com.eventbook.backend.lots.TicketLot;
import com.eventbook.backend.tickets.dto.BuyerTicketResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
        TicketLot ticketLot = ticket.getTicketLot();
        return new BuyerTicketResponse(
                ticket.getId(),
                ticketLot.getEvent().getTitle(),
                ticketLot.getEvent().getStartsAt().toInstant(),
                ticket.getUniqueCode(),
                ticket.getStatus(),
                ticketLot.getName(),
                ticket.getOrderItem() != null 
                    ? ticket.getOrderItem().getUnitPriceSnapshot()
                    : ticket.getOrder().getUnitPriceSnapshot()
        );
    }
}