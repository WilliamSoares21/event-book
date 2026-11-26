package com.eventbook.backend.orders;

import com.eventbook.backend.cards.CardStatus;
import com.eventbook.backend.cards.SimulatedCard;
import com.eventbook.backend.cards.SimulatedCardRepository;
import com.eventbook.backend.cards.exception.CardAccessDeniedException;
import com.eventbook.backend.cards.exception.CardNotFoundException;
import com.eventbook.backend.common.dto.PageResponse;
import com.eventbook.backend.common.exception.BusinessException;
import com.eventbook.backend.events.EventStatus;
import com.eventbook.backend.lots.LotStatus;
import com.eventbook.backend.lots.TicketLot;
import com.eventbook.backend.lots.TicketLotRepository;
import com.eventbook.backend.orders.dto.CheckoutResponseDTO;
import com.eventbook.backend.orders.dto.OrderRequestDTO;
import com.eventbook.backend.orders.dto.OrderResponseDTO;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderService {

  private static final int RESERVATION_MINUTES = 15;
  private static final int MAX_PAGE_SIZE = 50;
  private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt");

  private final OrderRepository orderRepository;
  private final TicketLotRepository ticketLotRepository;
  private final TicketRepository ticketRepository;
  private final SimulatedCardRepository simulatedCardRepository;
  private final PaymentTransactionRepository paymentTransactionRepository;

  @Transactional
  public Order reserve(User buyer, UUID ticketLotId, int quantity) {
    // Método legado para compatibilidade - delega para o novo método
    return reserveItems(buyer, List.of(new OrderRequestDTO.OrderItemRequest(ticketLotId, quantity)));
  }

  @Transactional
  public Order reserveItems(User buyer, List<OrderRequestDTO.OrderItemRequest> itemRequests) {
    // Validar itens duplicados
    Set<UUID> seenLotIds = new HashSet<>();
    for (OrderRequestDTO.OrderItemRequest item : itemRequests) {
      if (!seenLotIds.add(item.ticketLotId())) {
        throw new BusinessException("Lote duplicado na compra: " + item.ticketLotId(), HttpStatus.BAD_REQUEST);
      }
    }

    // 1. Lock all ticket lots first (ordenados por ID para evitar deadlock)
    List<TicketLot> lots = new ArrayList<>();
    List<OrderRequestDTO.OrderItemRequest> sortedItems = itemRequests.stream()
        .sorted((a, b) -> a.ticketLotId().compareTo(b.ticketLotId()))
        .toList();

    for (OrderRequestDTO.OrderItemRequest item : sortedItems) {
      TicketLot lot = ticketLotRepository.findByIdForUpdate(item.ticketLotId())
          .orElseThrow(() -> new LotNotFoundException("Lote não encontrado: " + item.ticketLotId()));
      lots.add(lot);
    }

    // 2. Validar todos os lotes antes de fazer qualquer reserva
    Instant now = Instant.now();
    Map<UUID, TicketLot> lotMap = new HashMap<>();
    for (int i = 0; i < sortedItems.size(); i++) {
      TicketLot lot = lots.get(i);
      OrderRequestDTO.OrderItemRequest item = sortedItems.get(i);
      lotMap.put(lot.getId(), lot);

      if (lot.getStatus() != LotStatus.ACTIVE) {
        throw new LotNotActiveException("Lote não está ativo para venda: " + lot.getName());
      }

      requirePublishedEvent(lot);

      if (now.isBefore(lot.getSalesStart())) {
        throw new OutsideSalesWindowException("Vendas ainda não iniciadas para o lote: " + lot.getName());
      }
      if (now.isAfter(lot.getSalesEnd())) {
        throw new OutsideSalesWindowException("Vendas encerradas para o lote: " + lot.getName());
      }

      int occupied = lot.getSold() + lot.getReserved();
      if (occupied + item.quantity() > lot.getCapacity()) {
        throw new InsufficientCapacityException("Capacidade insuficiente no lote: " + lot.getName());
      }
    }

    // 3. Fazer todas as reservas
    for (int i = 0; i < sortedItems.size(); i++) {
      TicketLot lot = lots.get(i);
      OrderRequestDTO.OrderItemRequest item = sortedItems.get(i);

      lot.incrementReserved(item.quantity());
      ticketLotRepository.save(lot);
    }

    // 4. Criar pedido com itens
    Order order = Order.builder()
        .buyer(buyer)
        .status(OrderStatus.PENDING)
        .expiresAt(now.plus(RESERVATION_MINUTES, ChronoUnit.MINUTES))
        .build();

    // Para compatibilidade com legado, preencher campos deprecated com o primeiro item
    OrderRequestDTO.OrderItemRequest firstItem = sortedItems.get(0);
    TicketLot firstLot = lotMap.get(firstItem.ticketLotId());
    order.setTicketLot(firstLot);
    order.setQuantity(firstItem.quantity());
    order.setUnitPriceSnapshot(firstLot.getUnitPrice());

    // Adicionar todos os itens
    for (OrderRequestDTO.OrderItemRequest item : sortedItems) {
      TicketLot lot = lotMap.get(item.ticketLotId());
      OrderItem orderItem = OrderItem.builder()
          .order(order)
          .ticketLot(lot)
          .quantity(item.quantity())
          .unitPriceSnapshot(lot.getUnitPrice())
          .build();
      order.getItems().add(orderItem);
    }

    // Calcular total
    order.setTotalPriceSnapshot(order.calculateTotal());

    return orderRepository.save(order);
  }

  @Transactional
  public CheckoutResult checkout(UUID orderId, UUID buyerId, UUID cardId) {
    // 1. Lock the Order with items (PESSIMISTIC_WRITE)
    Order order = orderRepository.findByIdWithItemsForUpdate(orderId)
        .orElseThrow(() -> new OrderNotFoundException("Pedido não encontrado: " + orderId));

    // 2. Validate ownership
    if (!order.getBuyer().getId().equals(buyerId)) {
      throw new OrderAccessDeniedException("Acesso negado: pedido não pertence ao comprador");
    }

    // 3. Validate status FIRST: PAID -> 409, CANCELLED/EXPIRED -> 422
    if (order.getStatus() != OrderStatus.PENDING) {
      throw new OrderInvalidStatusException("Pedido não está pendente para checkout", order.getStatus());
    }

    // 4. Validate expiration (only reachable for PENDING orders)
    Instant now = Instant.now();
    if (order.getExpiresAt() == null || order.getExpiresAt().isBefore(now)) {
      throw new OrderExpiredException("Pedido expirado: não é possível realizar checkout");
    }

    // 5. Lock all TicketLots (PESSIMISTIC_WRITE) - Order → TicketLot order to prevent deadlock
    // Ordenar por ID para evitar deadlock
    List<TicketLot> lots = new ArrayList<>();
    if (!order.getItems().isEmpty()) {
      lots = order.getItems().stream()
          .map(item -> item.getTicketLot())
          .sorted((a, b) -> a.getId().compareTo(b.getId()))
          .toList();
    } else if (order.isLegacyOrder() && order.getTicketLot() != null) {
      lots.add(order.getTicketLot());
    }

    Map<UUID, TicketLot> lotMap = new HashMap<>();
    for (TicketLot lot : lots) {
      // Re-lock para PESSIMISTIC_WRITE
      TicketLot lockedLot = ticketLotRepository.findByIdForUpdate(lot.getId())
          .orElseThrow(() -> new LotNotFoundException("Lote não encontrado: " + lot.getId()));
      lotMap.put(lockedLot.getId(), lockedLot);

      // 5.1 Revalidar condições comerciais: evento publicado e lote ativo.
      if (lockedLot.getStatus() != LotStatus.ACTIVE) {
        throw new LotNotActiveException("Lote não está ativo para venda: " + lockedLot.getName());
      }
      requirePublishedEvent(lockedLot);
    }

    // 5.2 Consistência das reservas
    if (!order.getItems().isEmpty()) {
      for (OrderItem item : order.getItems()) {
        TicketLot lockedLot = lotMap.get(item.getTicketLot().getId());
        if (lockedLot.getReserved() < item.getQuantity()) {
          throw new BusinessException("Reserva do lote inconsistente para o item: " + item.getTicketLot().getName(), HttpStatus.UNPROCESSABLE_ENTITY);
        }
      }
    } else if (order.isLegacyOrder() && order.getTicketLot() != null) {
      TicketLot lockedLot = lotMap.get(order.getTicketLot().getId());
      if (lockedLot.getReserved() < order.getQuantity()) {
        throw new BusinessException("Reserva do lote inconsistente para o pedido legado: " + lockedLot.getName(), HttpStatus.UNPROCESSABLE_ENTITY);
      }
    }

    // 6. Lock the card (PESSIMISTIC_WRITE) - global lock order: Order → TicketLot(s) → SimulatedCard
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

    BigDecimal amount = order.getTotalPriceSnapshot();

    // 7. Limite insuficiente
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

    // 8. Aprovação: débito, transação, lotes, ingressos e pedido na mesma transação.
    card.setAvailableLimit(card.getAvailableLimit().subtract(amount));
    simulatedCardRepository.save(card);

    paymentTransactionRepository.save(PaymentTransaction.builder()
        .order(order)
        .card(card)
        .amount(amount)
        .status(PaymentStatus.APPROVED)
        .build());

    // 9. Convert reservations to sales for ALL items
    List<Ticket> allTickets = new ArrayList<>();
    
    // Handle grouped orders (with items) and legacy orders (without items)
    if (!order.getItems().isEmpty()) {
      for (OrderItem item : order.getItems()) {
        TicketLot lockedLot = lotMap.get(item.getTicketLot().getId());
        
        lockedLot.decrementReserved(item.getQuantity());
        lockedLot.incrementSold(item.getQuantity());
        ticketLotRepository.save(lockedLot);

        // 10. Create tickets for this item
        for (int i = 0; i < item.getQuantity(); i++) {
          String uniqueCode;
          do {
            uniqueCode = generateUniqueCode();
          } while (ticketRepository.existsByUniqueCode(uniqueCode));
          
          Ticket ticket = Ticket.builder()
              .order(order)
              .orderItem(item)
              .uniqueCode(uniqueCode)
              .status(TicketStatus.VALID)
              .build();
          allTickets.add(ticket);
        }
      }
    } else if (order.isLegacyOrder() && order.getTicketLot() != null) {
      // Legacy order handling
      TicketLot lockedLot = lotMap.get(order.getTicketLot().getId());
      
      lockedLot.decrementReserved(order.getQuantity());
      lockedLot.incrementSold(order.getQuantity());
      ticketLotRepository.save(lockedLot);

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
        allTickets.add(ticket);
      }
    }
    
    List<Ticket> savedTickets = ticketRepository.saveAll(allTickets);

    // 11. Update Order to PAID
    order.setStatus(OrderStatus.PAID);
    order.setPaidAt(now);
    orderRepository.save(order);

    return CheckoutResult.approved(CheckoutResponseDTO.from(order, savedTickets));
  }

  private void requirePublishedEvent(TicketLot lot) {
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
    // 1. Lock the Order with items (PESSIMISTIC_WRITE)
    Order order = orderRepository.findByIdWithItemsForUpdate(orderId)
        .orElseThrow(() -> new OrderNotFoundException("Pedido não encontrado: " + orderId));

    // 2. Validate authorization: buyer or event organizer
    boolean isBuyer = order.getBuyer().getId().equals(userId);
    
    // Para compatibilidade com legacy, verificar organizador via primeiro lote
    UUID organizerId = null;
    if (!order.getItems().isEmpty()) {
      organizerId = order.getItems().get(0).getTicketLot().getEvent().getOrganizer().getId();
    } else if (order.getTicketLot() != null) {
      organizerId = order.getTicketLot().getEvent().getOrganizer().getId();
    }
    
    boolean isOrganizer = organizerId != null && organizerId.equals(userId);

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
    Instant now = Instant.now();
    if (isBuyer && !isOrganizer) {
      Instant cancellationDeadline = null;
      if (!order.getItems().isEmpty()) {
        cancellationDeadline = order.getItems().get(0).getTicketLot()
            .getEvent()
            .getStartsAt()
            .toInstant()
            .minus(12, ChronoUnit.HOURS);
      } else if (order.getTicketLot() != null) {
        cancellationDeadline = order.getTicketLot()
            .getEvent()
            .getStartsAt()
            .toInstant()
            .minus(12, ChronoUnit.HOURS);
      }
      
      if (cancellationDeadline != null && now.isAfter(cancellationDeadline)) {
        throw new OrderAccessDeniedException("O prazo para cancelamento pelo comprador foi encerrado");
      }
    }

    // 5. Lock all TicketLots (PESSIMISTIC_WRITE) - Order → TicketLot(s) lock order
    List<TicketLot> lots = new ArrayList<>();
    if (!order.getItems().isEmpty()) {
      lots = order.getItems().stream()
          .map(item -> item.getTicketLot())
          .sorted((a, b) -> a.getId().compareTo(b.getId()))
          .toList();
    } else if (order.getTicketLot() != null) {
      lots.add(order.getTicketLot());
    }

    Map<UUID, TicketLot> lotMap = new HashMap<>();
    for (TicketLot lot : lots) {
      TicketLot lockedLot = ticketLotRepository.findByIdForUpdate(lot.getId())
          .orElseThrow(() -> new LotNotFoundException("Lote não encontrado: " + lot.getId()));
      lotMap.put(lockedLot.getId(), lockedLot);
    }

    // 6. Re-validate status after acquiring locks
    if (order.getStatus() == OrderStatus.CANCELLED) {
      throw new OrderAlreadyCancelledException("Este pedido já foi cancelado");
    }

    if (order.getStatus() == OrderStatus.EXPIRED) {
      throw new OrderExpiredException("Pedidos expirados não podem ser cancelados");
    }

    // Re-validate expiration for PENDING orders
    if (order.getStatus() == OrderStatus.PENDING
            && order.getExpiresAt() != null
            && order.getExpiresAt().isBefore(Instant.now())) {
      throw new OrderExpiredException("Pedido expirado: não é possível cancelar");
    }

    // 7. Process based on order status
    if (order.getStatus() == OrderStatus.PENDING) {
      processPendingCancellation(order, lotMap);
    } else if (order.getStatus() == OrderStatus.PAID) {
      processPaidCancellation(order, lotMap, now);
    }

    // 8. Update order to CANCELLED
    order.setStatus(OrderStatus.CANCELLED);
    order.setCancelledAt(now);
    orderRepository.save(order);
  }

  private void processPendingCancellation(Order order, Map<UUID, TicketLot> lotMap) {
    // Release reserved capacity for all items
    // If order has items (grouped order), use them. Otherwise fall back to legacy fields.
    if (!order.getItems().isEmpty()) {
      for (OrderItem item : order.getItems()) {
        TicketLot lot = lotMap.get(item.getTicketLot().getId());
        if (lot != null) {
          lot.decrementReserved(item.getQuantity());
          ticketLotRepository.save(lot);
        }
      }
    } else if (order.isLegacyOrder() && order.getTicketLot() != null) {
      // Legacy order with no items
      TicketLot lot = lotMap.get(order.getTicketLot().getId());
      if (lot != null) {
        lot.decrementReserved(order.getQuantity());
        ticketLotRepository.save(lot);
      }
    }
  }

  private void processPaidCancellation(Order order, Map<UUID, TicketLot> lotMap, Instant now) {
    // 1. Find the original APPROVED payment transaction
    PaymentTransaction approvedTransaction = paymentTransactionRepository
        .findByOrderIdAndStatus(order.getId(), PaymentStatus.APPROVED)
        .orElseThrow(() -> new IllegalStateException("Transação aprovada não encontrada para o pedido: " + order.getId()));

    // 2. Check for existing REFUNDED transaction (idempotency protection)
    if (paymentTransactionRepository.countByOrderIdAndStatus(order.getId(), PaymentStatus.REFUNDED) > 0) {
      throw new IllegalStateException("Estorno já processado para este pedido: " + order.getId());
    }

    // 3. Lock the SimulatedCard (PESSIMISTIC_WRITE) - Order → TicketLot(s) → SimulatedCard
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

    // 6. Release sold capacity for all items
    // If order has items (grouped order), use them. Otherwise fall back to legacy fields.
    if (!order.getItems().isEmpty()) {
      for (OrderItem item : order.getItems()) {
        TicketLot lot = lotMap.get(item.getTicketLot().getId());
        if (lot != null) {
          lot.decrementSold(item.getQuantity());
          ticketLotRepository.save(lot);
        }
      }
    } else if (order.isLegacyOrder() && order.getTicketLot() != null) {
      // Legacy order with no items
      TicketLot lot = lotMap.get(order.getTicketLot().getId());
      if (lot != null) {
        lot.decrementSold(order.getQuantity());
        ticketLotRepository.save(lot);
      }
    }

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

  @Transactional(readOnly = true)
  public List<OrderResponseDTO> findOwnOrders(UUID buyerId) {
    return orderRepository.findByBuyerId(buyerId).stream()
            .map(order -> order.getItems().isEmpty() ? OrderResponseDTO.fromLegacy(order) : OrderResponseDTO.from(order))
            .toList();
  }
}