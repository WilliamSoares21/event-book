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
import com.eventbook.backend.orders.exception.OrderAlreadyCancelledException;
import com.eventbook.backend.orders.exception.OrderExpiredException;
import com.eventbook.backend.orders.exception.OrderHasCheckedInTicketException;
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

  @Transactional
  public void cancel(UUID orderId, UUID userId) {
    // 1. Lock the Order (PESSIMISTIC_WRITE)
    Order order = orderRepository.findByIdForUpdate(orderId)
        .orElseThrow(() -> new OrderNotFoundException("Pedido não encontrado: " + orderId));

    // 2. Validate authorization: buyer or event organizer
    boolean isBuyer = order.getBuyer().getId().equals(userId);
    boolean isOrganizer = order.getTicketLot()
        .getEvent()
        .getOrganizer()
        .getId()
        .equals(userId);

    if (!isBuyer && !isOrganizer) {
      throw new OrderAccessDeniedException("Você não tem permissão para cancelar este pedido");
    }

    // 3. Validate order status
    if (order.getStatus() == OrderStatus.CANCELLED) {
      throw new OrderAlreadyCancelledException("Este pedido já foi cancelado");
    }

    if (order.getStatus() == OrderStatus.EXPIRED) {
      throw new OrderExpiredException("Pedidos expirados não podem ser cancelados");
    }

    if (order.getStatus() != OrderStatus.PENDING && order.getStatus() != OrderStatus.PAID) {
      throw new OrderInvalidStatusException("Estado do pedido não permite cancelamento", order.getStatus());
    }

    // 4. Validate cancellation deadline for buyer (not organizer)
    // isAfter() retorna false no instante exato do deadline -> cancelamento permitido no limite de 12h
    Instant now = Instant.now();
    if (isBuyer && !isOrganizer) {
      Instant cancellationDeadline = order.getTicketLot()
          .getEvent()
          .getStartsAt()
          .toInstant()
          .minus(12, ChronoUnit.HOURS);

      if (now.isAfter(cancellationDeadline)) {
        throw new OrderAccessDeniedException("O prazo para cancelamento pelo comprador foi encerrado");
      }
    }

    // 5. Lock the TicketLot (PESSIMISTIC_WRITE) - Order → TicketLot lock order
    TicketLot lot = ticketLotRepository.findByIdForUpdate(order.getTicketLot().getId())
        .orElseThrow(() -> new LotNotFoundException("Lote não encontrado: " + order.getTicketLot().getId()));

    // 6. Re-validate status after acquiring locks
    if (order.getStatus() == OrderStatus.CANCELLED) {
      throw new OrderAlreadyCancelledException("Este pedido já foi cancelado");
    }

    if (order.getStatus() == OrderStatus.EXPIRED) {
      throw new OrderExpiredException("Pedidos expirados não podem ser cancelados");
    }

    // Re-validate expiration for PENDING orders (authoritative check inside transaction)
    if (order.getStatus() == OrderStatus.PENDING
            && order.getExpiresAt() != null
            && order.getExpiresAt().isBefore(Instant.now())) {
      throw new OrderExpiredException("Pedido expirado: não é possível cancelar");
    }

    // 7. Process based on order status
    if (order.getStatus() == OrderStatus.PENDING) {
      processPendingCancellation(order, lot);
    } else if (order.getStatus() == OrderStatus.PAID) {
      processPaidCancellation(order, lot, now);
    }

    // 8. Update order to CANCELLED
    order.setStatus(OrderStatus.CANCELLED);
    order.setCancelledAt(now);
    orderRepository.save(order);
  }

  private void processPendingCancellation(Order order, TicketLot lot) {
    // Release reserved capacity
    lot.decrementReserved(order.getQuantity());
    ticketLotRepository.save(lot);
    // No financial transaction for PENDING orders
    // Tickets are not created for PENDING orders (created only at checkout)
  }

  private void processPaidCancellation(Order order, TicketLot lot, Instant now) {
    // 1. Find the original APPROVED payment transaction
    PaymentTransaction approvedTransaction = paymentTransactionRepository
        .findByOrderIdAndStatus(order.getId(), PaymentStatus.APPROVED)
        .orElseThrow(() -> new IllegalStateException("Transação aprovada não encontrada para o pedido: " + order.getId()));

    // 2. Check for existing REFUNDED transaction (idempotency protection)
    if (paymentTransactionRepository.countByOrderIdAndStatus(order.getId(), PaymentStatus.REFUNDED) > 0) {
      throw new IllegalStateException("Estorno já processado para este pedido: " + order.getId());
    }

    // 3. Lock the SimulatedCard (PESSIMISTIC_WRITE) - Order → TicketLot → SimulatedCard
    SimulatedCard card = simulatedCardRepository.findByIdForUpdate(approvedTransaction.getCard().getId())
        .orElseThrow(() -> new IllegalStateException("Cartão não encontrado: " + approvedTransaction.getCard().getId()));

    // 4. Restore available limit
    BigDecimal refundAmount = approvedTransaction.getAmount();
    card.setAvailableLimit(card.getAvailableLimit().add(refundAmount));
    simulatedCardRepository.save(card);

    // 5. Create REFUNDED payment transaction (append-only history)
    paymentTransactionRepository.save(PaymentTransaction.builder()
        .order(order)
        .card(card)
        .amount(refundAmount)
        .status(PaymentStatus.REFUNDED)
        .build());

    // 6. Release sold capacity
    lot.decrementSold(order.getQuantity());
    ticketLotRepository.save(lot);

    // 7. Cancel tickets
    List<Ticket> tickets = ticketRepository.findByOrderId(order.getId());
    for (Ticket ticket : tickets) {
      if (ticket.getStatus() == TicketStatus.CHECKED_IN) {
        throw new OrderHasCheckedInTicketException("Não é possível cancelar um pedido que possui ingresso já utilizado");
      }
      ticket.setStatus(TicketStatus.CANCELLED);
    }
    ticketRepository.saveAll(tickets);
  }
}
