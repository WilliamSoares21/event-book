package com.eventbook.backend.orders;

import com.eventbook.backend.lots.LotStatus;
import com.eventbook.backend.lots.TicketLot;
import com.eventbook.backend.lots.TicketLotRepository;
import com.eventbook.backend.orders.dto.CheckoutResponseDTO;
import com.eventbook.backend.orders.exception.*;
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

    // 3. Validate status FIRST: PAID -> 409, CANCELLED/EXPIRED -> 422 (decided by currentStatus in the handler)
    if (order.getStatus() != OrderStatus.PENDING) {
      throw new OrderInvalidStatusException("Pedido não está pendente para checkout", order.getStatus());
    }

    // 4. Validate expiration (only reachable for PENDING orders)
    Instant now = Instant.now();
    if (order.getExpiresAt() == null || order.getExpiresAt().isBefore(now)) {
      throw new OrderExpiredException("Pedido expirado: não é possível realizar checkout");
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

  @Transactional
  public void cancel(UUID orderId, UUID userId) {

    // 1. Bloqueia o pedido para evitar cancelamentos simultâneos
    Order order = orderRepository.findByIdForUpdate(orderId)
            .orElseThrow(() -> new OrderNotFoundException(
                    "Pedido não encontrado: " + orderId
            ));

    // 2. Verifica se o usuário é o comprador ou o organizador do evento
    boolean isBuyer = order.getBuyer().getId().equals(userId);

    boolean isOrganizer = order.getTicketLot()
            .getEvent()
            .getOrganizer()
            .getId()
            .equals(userId);

    if (!isBuyer && !isOrganizer) {
      throw new OrderAccessDeniedException(
              "Você não tem permissão para cancelar este pedido"
      );
    }

    // 3. Verifica o estado atual do pedido
    if (order.getStatus() == OrderStatus.CANCELLED) {
      throw new OrderAlreadyCancelledException(
              "Este pedido já foi cancelado"
      );
    }

    if (order.getStatus() == OrderStatus.EXPIRED) {
      throw new OrderExpiredException(
              "Pedidos expirados não podem ser cancelados"
      );
    }

    if (order.getStatus() != OrderStatus.PENDING
            && order.getStatus() != OrderStatus.PAID) {

      throw new OrderInvalidStatusException(
              "Estado do pedido não permite cancelamento",
              order.getStatus()
      );
    }


    // 4. O comprador só pode cancelar até 12 horas antes do evento.
    // O organizador proprietário pode cancelar independentemente do prazo.
    Instant now = Instant.now();

    if (isBuyer && !isOrganizer) {

      Instant cancellationDeadline = order.getTicketLot()
              .getEvent()
              .getStartsAt()
              .toInstant()
              .minus(12, ChronoUnit.HOURS);

      if (now.isAfter(cancellationDeadline)) {
        throw new OrderAccessDeniedException(
                "O prazo para cancelamento pelo comprador foi encerrado"
        );
      }
    }

    // 5. Bloqueia o lote, mantendo a mesma ordem de locks do checkout
    TicketLot lot = ticketLotRepository.findByIdForUpdate(
            order.getTicketLot().getId()
    ).orElseThrow(() -> new LotNotFoundException(
            "Lote não encontrado: " + order.getTicketLot().getId()
    ));

    // 6. Devolve a quantidade ao estoque conforme o estado do pedido
    if (order.getStatus() == OrderStatus.PENDING) {

      lot.decrementReserved(order.getQuantity());


    } else if (order.getStatus() == OrderStatus.PAID) {

      // 1. Busca os ingressos vinculados ao pedido
      List<Ticket> tickets = ticketRepository.findByOrderId(orderId);

      // 2. Verifica se algum ingresso já foi utilizado
      boolean hasCheckedInTicket = tickets.stream()
              .anyMatch(ticket ->
                      ticket.getStatus() == TicketStatus.CHECKED_IN
              );

      // 3. Impede o cancelamento se houver ingresso utilizado
      if (hasCheckedInTicket) {
        throw new OrderHasCheckedInTicketException(
                "Não é possível cancelar um pedido que possui ingresso já utilizado"
        );
      }

      // 4. Devolve a quantidade ao estoque
      lot.decrementSold(order.getQuantity());

      // 5. Cancela os ingressos
      for (Ticket ticket : tickets) {
        ticket.setStatus(TicketStatus.CANCELLED);
      }

      ticketRepository.saveAll(tickets);
    }


    // 7. Atualiza o lote e o pedido
    ticketLotRepository.save(lot);

    order.setStatus(OrderStatus.CANCELLED);
    order.setCancelledAt(now);

    orderRepository.save(order);
  }

  private String generateUniqueCode() {
    return UUID.randomUUID().toString().replace("-", "").toUpperCase();
  }
}
