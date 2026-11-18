package com.eventbook.backend.checkin;

import com.eventbook.backend.checkin.dto.CheckInResponseDTO;
import com.eventbook.backend.checkin.exception.EventOutsideCheckinWindowException;
import com.eventbook.backend.checkin.exception.InvalidTicketCodeException;
import com.eventbook.backend.checkin.exception.OrderNotPaidException;
import com.eventbook.backend.checkin.exception.StaffNotAuthorizedException;
import com.eventbook.backend.checkin.exception.TicketAlreadyCheckedInException;
import com.eventbook.backend.checkin.exception.TicketInvalidStatusException;
import com.eventbook.backend.checkin.exception.TicketNotFoundException;
import com.eventbook.backend.orders.OrderStatus;
import com.eventbook.backend.staff.EventStaffRepository;
import com.eventbook.backend.tickets.Ticket;
import com.eventbook.backend.tickets.TicketRepository;
import com.eventbook.backend.tickets.TicketStatus;
import com.eventbook.backend.users.User;
import com.eventbook.backend.users.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CheckinService {

    private final TicketRepository ticketRepository;
    private final CheckInRepository checkInRepository;
    private final EventStaffRepository eventStaffRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    @Transactional
    public CheckInResponseDTO checkIn(String uniqueCode, UUID staffId) {
        if (uniqueCode == null || uniqueCode.isBlank() || uniqueCode.length() > 64) {
            throw new InvalidTicketCodeException("Código do ticket inválido");
        }

        // 1. Lock the ticket by uniqueCode (PESSIMISTIC_WRITE)
        Ticket ticket = ticketRepository.findByUniqueCodeForUpdate(uniqueCode)
                .orElseThrow(() -> new TicketNotFoundException("Ticket não encontrado"));

        // 2. Validate order is PAID
        if (ticket.getOrder() == null || ticket.getOrder().getStatus() != OrderStatus.PAID) {
            OrderStatus currentStatus = ticket.getOrder() != null ? ticket.getOrder().getStatus() : null;
            throw new OrderNotPaidException("Pedido não está pago", currentStatus);
        }

        // 3. Validate event exists
        if (ticket.getOrder().getTicketLot() == null || ticket.getOrder().getTicketLot().getEvent() == null) {
            throw new TicketNotFoundException("Evento do ticket não encontrado");
        }

        var event = ticket.getOrder().getTicketLot().getEvent();

        // 4. Validate staff is associated with the event (MUST BE BEFORE check-in existence check)
        if (!eventStaffRepository.existsByEventIdAndUserId(event.getId(), staffId)) {
            throw new StaffNotAuthorizedException("Staff não autorizado para este evento");
        }

        // 5. Check if already checked in (only after authorization)
        Optional<CheckIn> existingCheckIn = checkInRepository.findByTicketId(ticket.getId());
        if (existingCheckIn.isPresent()) {
            throw new TicketAlreadyCheckedInException("Ticket já possui check-in", existingCheckIn.get());
        }

        // 6. Validate ticket status is VALID
        if (ticket.getStatus() != TicketStatus.VALID) {
            throw new TicketInvalidStatusException("Ticket não está em status válido para check-in", ticket.getStatus());
        }

        // 7. Validate check-in window: startsAt - 2 hours <= now <= endsAt
        Instant now = clock.instant();
        OffsetDateTime windowStart = event.getStartsAt().minusHours(2);
        OffsetDateTime windowEnd = event.getEndsAt();

        Instant windowStartInstant = windowStart.toInstant();
        Instant windowEndInstant = windowEnd.toInstant();

        if (now.isBefore(windowStartInstant) || now.isAfter(windowEndInstant)) {
            throw new EventOutsideCheckinWindowException("Evento fora da janela de check-in");
        }

        // 8. Fetch staff user and create check-in
        User staff = userRepository.findById(staffId)
                .orElseThrow(() -> new StaffNotAuthorizedException("Staff não encontrado"));

        CheckIn checkIn = CheckIn.builder()
                .ticket(ticket)
                .staff(staff)
                .checkedInAt(now)
                .build();

        checkInRepository.save(checkIn);

        // 9. Update ticket status to CHECKED_IN
        ticket.setStatus(TicketStatus.CHECKED_IN);
        ticketRepository.save(ticket);

        return CheckInResponseDTO.from(checkIn);
    }
}