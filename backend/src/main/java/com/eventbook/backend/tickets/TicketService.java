package com.eventbook.backend.tickets;

import com.eventbook.backend.common.dto.PageResponse;
import com.eventbook.backend.tickets.dto.BuyerTicketResponse;
import com.eventbook.backend.users.User;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TicketService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt");

    private final TicketRepository ticketRepository;

    @Transactional(readOnly = true)
    public PageResponse<BuyerTicketResponse> findOwnTickets(UUID buyerId, int page, int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE), DEFAULT_SORT);
        Page<Ticket> ticketsPage = ticketRepository.findByBuyerId(buyerId, pageable);
        return PageResponse.from(ticketsPage.map(this::toBuyerTicketResponse));
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