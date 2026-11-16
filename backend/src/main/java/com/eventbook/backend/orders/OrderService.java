package com.eventbook.backend.orders;

import com.eventbook.backend.lots.LotStatus;
import com.eventbook.backend.lots.TicketLot;
import com.eventbook.backend.lots.TicketLotRepository;
import com.eventbook.backend.orders.dto.CheckoutResponseDTO;
import com.eventbook.backend.orders.exception.InsufficientCapacityException;
import com.eventbook.backend.orders.exception.LotNotActiveException;
import com.eventbook.backend.orders.exception.LotNotFoundException;
import com.eventbook.backend.orders.exception.OutsideSalesWindowException;
import com.eventbook.backend.orders.exception.OrderAccessDeniedException;
import com.eventbook.backend.orders.exception.OrderExpiredException;
import com.eventbook.backend.orders.exception.OrderInvalidStatusException;
import com.eventbook.backend.orders.exception.OrderNotFoundException;
import com.eventbook.backend.tickets.Ticket;
import com.eventbook.backend.tickets.TicketRepository;
import com.eventbook.backend.tickets.TicketStatus;
import com.eventbook.backend.users.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderService {

  private static final int RESERVATION_MINUTES = 15;

  private final OrderRepository orderRepository;
  private final TicketLotRepository ticketLotRepository;
  private final TicketRepository ticketRepository;

  @Transactional
  public Order reserve(User buyer, UUID ticketLotId, int quantity) {
    TicketLot lot = ticketLotRepository.findByIdForUpdate(ticketLotId)
        .orElseThrow(() -> new LotNotFoundException("Lote não encontrado: " + ticketLotId));

    if (lot.getStatus() != LotStatus.ACTIVE) {
      throw new LotNotActiveException("Lote não está ativo para venda");
    }

    Instant now = Instant.now();
    if (now.isBefore(lot.getSalesStart()) || now.isAfter(lot.getSalesEnd())) {
      throw new OutsideSalesWindowException("Fora da janela de vendas do lote");
    }

    int occupied = lot.getSold() + lot.getReserved();
    if (occupied + quantity > lot.getCapacity()) {
      throw new InsufficientCapacityException("Capacidade insuficiente no lote");
    }

    lot.incrementReserved(quantity);
    ticketLotRepository.save(lot);

    Order order = Order.builder()
        .buyer(buyer)
        .ticketLot(lot)
        .quantity(quantity)
        .unitPriceSnapshot(lot.getUnitPrice())
        .status(OrderStatus.PENDING)
        .expiresAt(now.plus(RESERVATION_MINUTES, ChronoUnit.MINUTES))
        .build();

    return orderRepository.save(order);
  }

  @Transactional
  public CheckoutResponseDTO checkout(UUID orderId, UUID buyerId) {
    // 1. Lock the Order (PESSIMISTIC_WRITE)
    Order order = orderRepository.findByIdForUpdate(orderId)
        .orElseThrow(() -> new OrderNotFoundException("Pedido não encontrado: " + orderId));

    // 2. Validate ownership
    if (!order.getBuyer().getId().equals(buyerId)) {
      throw new OrderAccessDeniedException("Acesso negado: pedido não pertence ao comprador");
    }

    // 3. Validate expiration FIRST (returns 422 for expired orders)
    Instant now = Instant.now();
    if (order.getExpiresAt() == null || order.getExpiresAt().isBefore(now)) {
      throw new OrderExpiredException("Pedido expirado: não é possível realizar checkout");
    }

    // 4. Validate status == PENDING (returns 409 for other invalid statuses like PAID, CANCELLED)
    if (order.getStatus() != OrderStatus.PENDING) {
      throw new OrderInvalidStatusException("Pedido não está pendente para checkout", order.getStatus());
    }

    // 5. Lock the TicketLot (PESSIMISTIC_WRITE) - Order → TicketLot order to prevent deadlock
    TicketLot lot = ticketLotRepository.findByIdForUpdate(order.getTicketLot().getId())
        .orElseThrow(() -> new LotNotFoundException("Lote não encontrado: " + order.getTicketLot().getId()));

    // 6. Convert reservation to sale
    lot.decrementReserved(order.getQuantity());
    lot.incrementSold(order.getQuantity());
    ticketLotRepository.save(lot);

    // 7. Create tickets
    List<Ticket> tickets = new ArrayList<>();
    for (int i = 0; i < order.getQuantity(); i++) {
        String uniqueCode;
        do {
            uniqueCode = generateUniqueCode();
        } while (ticketRepository.existsByUniqueCode(uniqueCode));
        
        Ticket ticket = Ticket.builder()
            .order(order)
            .uniqueCode(uniqueCode)
            .status(TicketStatus.VALID)
            .build();
        tickets.add(ticket);
    }
    List<Ticket> savedTickets = ticketRepository.saveAll(tickets);

    // 8. Update Order to PAID
    order.setStatus(OrderStatus.PAID);
    order.setPaidAt(now);
    orderRepository.save(order);

    return CheckoutResponseDTO.from(order, savedTickets);
  }

  private String generateUniqueCode() {
    return UUID.randomUUID().toString().replace("-", "").toUpperCase();
  }
}
