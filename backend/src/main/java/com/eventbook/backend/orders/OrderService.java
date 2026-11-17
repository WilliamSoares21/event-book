package com.eventbook.backend.orders;

import com.eventbook.backend.cards.CardStatus;
import com.eventbook.backend.cards.SimulatedCard;
import com.eventbook.backend.cards.SimulatedCardRepository;
import com.eventbook.backend.cards.exception.CardAccessDeniedException;
import com.eventbook.backend.cards.exception.CardNotFoundException;
import com.eventbook.backend.common.exception.BusinessException;
import com.eventbook.backend.events.EventStatus;
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
import com.eventbook.backend.payments.PaymentStatus;
import com.eventbook.backend.payments.PaymentTransaction;
import com.eventbook.backend.payments.PaymentTransactionRepository;
import com.eventbook.backend.tickets.Ticket;
import com.eventbook.backend.tickets.TicketRepository;
import com.eventbook.backend.tickets.TicketStatus;
import com.eventbook.backend.users.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
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
  private final SimulatedCardRepository simulatedCardRepository;
  private final PaymentTransactionRepository paymentTransactionRepository;

  @Transactional
  public Order reserve(User buyer, UUID ticketLotId, int quantity) {
    TicketLot lot = ticketLotRepository.findByIdForUpdate(ticketLotId)
        .orElseThrow(() -> new LotNotFoundException("Lote não encontrado: " + ticketLotId));

    if (lot.getStatus() != LotStatus.ACTIVE) {
      throw new LotNotActiveException("Lote não está ativo para venda");
    }

    requirePublishedEvent(lot);

    Instant now = Instant.now();
    if (now.isBefore(lot.getSalesStart())) {
      throw new OutsideSalesWindowException("Vendas ainda não iniciadas para este lote");
    }
    if (now.isAfter(lot.getSalesEnd())) {
      throw new OutsideSalesWindowException("Vendas encerradas para este lote");
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
  public CheckoutResult checkout(UUID orderId, UUID buyerId, UUID cardId) {
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

    // 5.1 Revalidar condições comerciais: evento publicado e lote ativo.
    // A janela de vendas NÃO é revalidada: reserva feita dentro do prazo pode ser finalizada.
    if (lot.getStatus() != LotStatus.ACTIVE) {
      throw new LotNotActiveException("Lote não está ativo para venda");
    }
    requirePublishedEvent(lot);

    // 5.2 Consistência da reserva: sem ela, decrementReserved lançaria IllegalStateException (500).
    if (lot.getReserved() < order.getQuantity()) {
      throw new BusinessException("Reserva do lote inconsistente para este pedido", HttpStatus.UNPROCESSABLE_ENTITY);
    }

    // 6. Lock the card (PESSIMISTIC_WRITE) - global lock order: Order → TicketLot → SimulatedCard
    SimulatedCard card = simulatedCardRepository.findByIdForUpdate(cardId)
        .orElseThrow(() -> new CardNotFoundException("Cartão não encontrado: " + cardId));

    if (!card.getUser().getId().equals(buyerId)) {
      throw new CardAccessDeniedException("Acesso negado: cartão não pertence ao comprador");
    }

    if (card.getStatus() != CardStatus.ACTIVE) {
      throw new BusinessException("Cartão não está ativo", HttpStatus.UNPROCESSABLE_ENTITY);
    }

    if (YearMonth.of(card.getExpiryYear(), card.getExpiryMonth()).isBefore(YearMonth.now())) {
      throw new BusinessException("Cartão vencido", HttpStatus.UNPROCESSABLE_ENTITY);
    }

    BigDecimal amount = order.getUnitPriceSnapshot().multiply(BigDecimal.valueOf(order.getQuantity()));

    // 7. Limite insuficiente: registra a recusa e devolve o pedido intacto (sem exceção, para não reverter o DECLINED).
    if (card.getAvailableLimit().compareTo(amount) < 0) {
      paymentTransactionRepository.save(PaymentTransaction.builder()
          .order(order)
          .card(card)
          .amount(amount)
          .status(PaymentStatus.DECLINED)
          .declineReason(CheckoutResult.INSUFFICIENT_CARD_LIMIT)
          .build());

      return CheckoutResult.declined(CheckoutResult.INSUFFICIENT_CARD_LIMIT);
    }

    // 8. Aprovação: débito, transação, lote, ingressos e pedido na mesma transação.
    card.setAvailableLimit(card.getAvailableLimit().subtract(amount));
    simulatedCardRepository.save(card);

    paymentTransactionRepository.save(PaymentTransaction.builder()
        .order(order)
        .card(card)
        .amount(amount)
        .status(PaymentStatus.APPROVED)
        .build());

    // 9. Convert reservation to sale
    lot.decrementReserved(order.getQuantity());
    lot.incrementSold(order.getQuantity());
    ticketLotRepository.save(lot);

    // 10. Create tickets
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

    // 11. Update Order to PAID
    order.setStatus(OrderStatus.PAID);
    order.setPaidAt(now);
    orderRepository.save(order);

    return CheckoutResult.approved(CheckoutResponseDTO.from(order, savedTickets));
  }

  private void requirePublishedEvent(TicketLot lot) {
    // Leitura sem lock: não introduz nova ordem de lock na cadeia Order → TicketLot → SimulatedCard.
    if (lot.getEvent().getStatus() != EventStatus.PUBLISHED) {
      throw new BusinessException(
          "Evento não está publicado para venda",
          HttpStatus.UNPROCESSABLE_ENTITY);
    }
  }

  private String generateUniqueCode() {
    return UUID.randomUUID().toString().replace("-", "").toUpperCase();
  }
}
