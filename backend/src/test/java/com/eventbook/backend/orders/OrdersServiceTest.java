package com.eventbook.backend.orders;

import com.eventbook.backend.cards.CardStatus;
import com.eventbook.backend.events.Event;
import com.eventbook.backend.events.EventStatus;
import com.eventbook.backend.cards.SimulatedCard;
import com.eventbook.backend.cards.SimulatedCardRepository;
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
import com.eventbook.backend.payments.PaymentTransactionRepository;
import com.eventbook.backend.tickets.Ticket;
import com.eventbook.backend.tickets.TicketRepository;
import com.eventbook.backend.tickets.TicketStatus;
import com.eventbook.backend.users.User;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

  @Mock
  private OrderRepository orderRepository;

  @Mock
  private TicketLotRepository ticketLotRepository;

  @Mock
  private TicketRepository ticketRepository;

  @Mock
  private SimulatedCardRepository simulatedCardRepository;

  @Mock
  private PaymentTransactionRepository paymentTransactionRepository;

  @InjectMocks
  private OrderService orderService;

  private final UUID cardId = UUID.randomUUID();

  private static final Event PUBLISHED_EVENT = Event.builder()
      .status(EventStatus.PUBLISHED)
      .build();

  // Cartão ativo com limite suficiente para os cenários de aprovação.
  private void stubActiveCard(UUID buyerId) {
    SimulatedCard card = SimulatedCard.builder()
        .id(cardId)
        .user(User.builder().id(buyerId).build())
        .brand("VISA")
        .lastFourDigits("1234")
        .expiryMonth(12)
        .expiryYear(2999)
        .totalLimit(new BigDecimal("1000.00"))
        .availableLimit(new BigDecimal("1000.00"))
        .status(CardStatus.ACTIVE)
        .build();

    when(simulatedCardRepository.findByIdForUpdate(cardId)).thenReturn(Optional.of(card));
  }

  @Test
  void deveReservarIngressosComSucesso() {

    UUID ticketLotId = UUID.randomUUID();

    User buyer = User.builder()
        .id(UUID.randomUUID())
        .build();

    Instant now = Instant.now();

    TicketLot lot = TicketLot.builder()
        .event(PUBLISHED_EVENT)
        .id(ticketLotId)
        .status(LotStatus.ACTIVE)
        .salesStart(now.minus(1, ChronoUnit.HOURS))
        .salesEnd(now.plus(1, ChronoUnit.HOURS))
        .capacity(100)
        .sold(10)
        .reserved(5)
        .unitPrice(new java.math.BigDecimal("50.00"))
        .build();

    when(ticketLotRepository.findByIdForUpdate(ticketLotId))
        .thenReturn(Optional.of(lot));

    when(ticketLotRepository.save(any(TicketLot.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    when(orderRepository.save(any(Order.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    Order order = orderService.reserve(buyer, ticketLotId, 2);

    assertThat(order).isNotNull();

    assertThat(order.getStatus())
        .isEqualTo(OrderStatus.PENDING);

    assertThat(order.getQuantity())
        .isEqualTo(2);

    assertThat(order.getUnitPriceSnapshot())
        .isEqualByComparingTo("50.00");

    assertThat(order.getBuyer()).isEqualTo(buyer);

    assertThat(lot.getReserved()).isEqualTo(7);

    assertThat(order.getExpiresAt())
        .isAfterOrEqualTo(now.plusSeconds(14 * 60))
        .isBeforeOrEqualTo(now.plusSeconds(16 * 60));

    verify(ticketLotRepository).findByIdForUpdate(ticketLotId);
    verify(ticketLotRepository).save(lot);
    verify(orderRepository).save(any(Order.class));
  }

  @Test
  void deveRejeitarReservaQuandoLoteNaoExistir() {

    UUID ticketLotId = UUID.randomUUID();

    User buyer = User.builder()
        .id(UUID.randomUUID())
        .build();

    when(ticketLotRepository.findByIdForUpdate(ticketLotId))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> orderService.reserve(buyer, ticketLotId, 2))
        .isInstanceOf(LotNotFoundException.class)
        .hasMessageContaining("Lote não encontrado");

    verify(ticketLotRepository).findByIdForUpdate(ticketLotId);

    verify(ticketLotRepository, never()).save(any());

    verify(orderRepository, never()).save(any());
  }

  @Test
  void deveRejeitarReservaQuandoLoteEstiverInativo() {

    UUID ticketLotId = UUID.randomUUID();

    User buyer = User.builder()
        .id(UUID.randomUUID())
        .build();

    TicketLot lot = TicketLot.builder()
        .event(PUBLISHED_EVENT)
        .id(ticketLotId)
        .status(LotStatus.INACTIVE)
        .name("Lote Teste")
        .build();

    when(ticketLotRepository.findByIdForUpdate(ticketLotId))
        .thenReturn(Optional.of(lot));

    assertThatThrownBy(() -> orderService.reserve(buyer, ticketLotId, 2))
        .isInstanceOf(LotNotActiveException.class)
        .hasMessageContaining("Lote não está ativo para venda");

    verify(ticketLotRepository, never()).save(any());
    verify(orderRepository, never()).save(any());
  }

  @Test
  void deveRejeitarReservaAntesDoInicioDasVendas() {

    UUID ticketLotId = UUID.randomUUID();

    User buyer = User.builder()
        .id(UUID.randomUUID())
        .build();

    Instant now = Instant.now();

    TicketLot lot = TicketLot.builder()
        .event(PUBLISHED_EVENT)
        .id(ticketLotId)
        .status(LotStatus.ACTIVE)
        .capacity(100)
        .salesStart(now.plus(1, ChronoUnit.HOURS))
        .salesEnd(now.plus(3, ChronoUnit.HOURS))
        .build();

    when(ticketLotRepository.findByIdForUpdate(ticketLotId))
        .thenReturn(Optional.of(lot));

    assertThatThrownBy(() -> orderService.reserve(buyer, ticketLotId, 2))
        .isInstanceOf(OutsideSalesWindowException.class);

    verify(ticketLotRepository, never()).save(any());
    verify(orderRepository, never()).save(any());
  }

  @Test
  void deveRejeitarReservaQuandoNaoHouverCapacidade() {

    UUID ticketLotId = UUID.randomUUID();

    User buyer = User.builder()
        .id(UUID.randomUUID())
        .build();

    Instant now = Instant.now();

    TicketLot lot = TicketLot.builder()
        .event(PUBLISHED_EVENT)
        .id(ticketLotId)
        .status(LotStatus.ACTIVE)
        .salesStart(now.minus(1, ChronoUnit.HOURS))
        .salesEnd(now.plus(1, ChronoUnit.HOURS))
        .capacity(100)
        .sold(80)
        .reserved(15)
        .build();

    when(ticketLotRepository.findByIdForUpdate(ticketLotId))
        .thenReturn(Optional.of(lot));

    assertThatThrownBy(() -> orderService.reserve(buyer, ticketLotId, 10))
        .isInstanceOf(InsufficientCapacityException.class);

    verify(ticketLotRepository, never()).save(any());
    verify(orderRepository, never()).save(any());
  }

  @Test
  void devePermitirReservaQuandoCapacidadeForExatamenteAtingida() {
    // Arrange
    UUID ticketLotId = UUID.randomUUID();
    BigDecimal unitPrice = new BigDecimal("50.00");
    User buyer = User.builder().id(UUID.randomUUID()).build();
    Instant now = Instant.now();

    TicketLot lot = TicketLot.builder()
        .event(PUBLISHED_EVENT)
        .id(ticketLotId)
        .status(LotStatus.ACTIVE)
        .salesStart(now.minus(1, ChronoUnit.HOURS))
        .salesEnd(now.plus(1, ChronoUnit.HOURS))
        .capacity(100)
        .sold(80)
        .reserved(15)
        .unitPrice(unitPrice)
        .build();

    when(ticketLotRepository.findByIdForUpdate(ticketLotId)).thenReturn(Optional.of(lot));
    when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

    // Act
    Order order = orderService.reserve(buyer, ticketLotId, 5);

    // Assert
    assertThat(order).isNotNull();
    assertThat(order.getBuyer()).isEqualTo(buyer);
    assertThat(order.getTicketLot()).isEqualTo(lot);
    assertThat(order.getQuantity()).isEqualTo(5);
    assertThat(order.getUnitPriceSnapshot()).isEqualByComparingTo(unitPrice);
    assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
    assertThat(order.getExpiresAt()).isAfter(now);

    assertThat(lot.getReserved()).isEqualTo(20);
    verify(ticketLotRepository).save(lot);
  }

  @Test
  void deveRejeitarReservaQuandoVendasJaTiveremEncerrado() {

    UUID ticketLotId = UUID.randomUUID();

    User buyer = User.builder()
        .id(UUID.randomUUID())
        .build();

    Instant now = Instant.now();

    TicketLot lot = TicketLot.builder()
        .event(PUBLISHED_EVENT)
        .id(ticketLotId)
        .status(LotStatus.ACTIVE)
        .capacity(100)
        .salesStart(now.minus(3, ChronoUnit.HOURS))
        .salesEnd(now.minus(1, ChronoUnit.HOURS))
        .build();

    when(ticketLotRepository.findByIdForUpdate(ticketLotId))
        .thenReturn(Optional.of(lot));

    assertThatThrownBy(() -> orderService.reserve(buyer, ticketLotId, 2))
        .isInstanceOf(OutsideSalesWindowException.class);

    verify(ticketLotRepository, never()).save(any());
    verify(orderRepository, never()).save(any());
  }

  @Test
  void devePermitirReservaExatamenteNoInicioDaJanela() {
    UUID ticketLotId = UUID.randomUUID();
    User buyer = User.builder().id(UUID.randomUUID()).build();
    Instant now = Instant.now();

    TicketLot lot = TicketLot.builder()
        .event(PUBLISHED_EVENT)
        .id(ticketLotId)
        .status(LotStatus.ACTIVE)
        .capacity(100)
        .salesStart(now)
        .salesEnd(now.plus(1, ChronoUnit.HOURS))
        .unitPrice(new BigDecimal("50.00"))
        .build();

    when(ticketLotRepository.findByIdForUpdate(ticketLotId)).thenReturn(Optional.of(lot));
    when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

    Order order = orderService.reserve(buyer, ticketLotId, 1);

    assertThat(order).isNotNull();
    assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
  }

  @Test
  void devePermitirReservaExatamenteNoFimDaJanela() {
    UUID ticketLotId = UUID.randomUUID();
    User buyer = User.builder().id(UUID.randomUUID()).build();
    Instant now = Instant.now().plusSeconds(2);

    TicketLot lot = TicketLot.builder()
        .event(PUBLISHED_EVENT)
        .id(ticketLotId)
        .status(LotStatus.ACTIVE)
        .capacity(100)
        .salesStart(now.minus(1, ChronoUnit.HOURS))
        .salesEnd(now)
        .unitPrice(new BigDecimal("50.00"))
        .build();

    when(ticketLotRepository.findByIdForUpdate(ticketLotId)).thenReturn(Optional.of(lot));
    when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

    Order order = orderService.reserve(buyer, ticketLotId, 1);

    assertThat(order).isNotNull();
    assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
  }

  private Order createPendingOrder(UUID orderId, UUID buyerId, UUID lotId, int quantity, BigDecimal unitPrice, Instant expiresAt) {
    User buyer = User.builder().id(buyerId).build();
    TicketLot lot = TicketLot.builder()
        .event(PUBLISHED_EVENT)
        .id(lotId)
        .capacity(100)
        .unitPrice(unitPrice)
        .build();
    Order order = Order.builder()
        .id(orderId)
        .buyer(buyer)
        .ticketLot(lot)
        .quantity(quantity)
        .unitPriceSnapshot(unitPrice)
        .status(OrderStatus.PENDING)
        .expiresAt(expiresAt)
        .build();
    // Adicionar item para compatibilidade com novo método findByIdWithItemsForUpdate
    OrderItem item = OrderItem.builder()
        .order(order)
        .ticketLot(lot)
        .quantity(quantity)
        .unitPriceSnapshot(unitPrice)
        .build();
    order.getItems().add(item);
    return order;
  }

  private TicketLot createTicketLot(UUID lotId, int capacity, int sold, int reserved) {
    return TicketLot.builder()
        .event(PUBLISHED_EVENT)
        .status(LotStatus.ACTIVE)
        .id(lotId)
        .capacity(capacity)
        .sold(sold)
        .reserved(reserved)
        .build();
  }

  // ===== Checkout Tests =====

  @Test
  void deveRealizarCheckoutComSucesso() {
    UUID orderId = UUID.randomUUID();
    UUID buyerId = UUID.randomUUID();
    UUID lotId = UUID.randomUUID();
    Instant now = Instant.now();

    Order order = createPendingOrder(orderId, buyerId, lotId, 2, new BigDecimal("50.00"), now.plus(10, ChronoUnit.MINUTES));
    TicketLot lot = createTicketLot(lotId, 100, 10, 5);

    when(orderRepository.findByIdWithItemsForUpdate(orderId)).thenReturn(Optional.of(order));
    when(ticketLotRepository.findByIdForUpdate(lotId)).thenReturn(Optional.of(lot));
    when(ticketRepository.existsByUniqueCode(anyString())).thenReturn(false);
    when(ticketRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
    when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
    when(ticketLotRepository.save(any(TicketLot.class))).thenAnswer(inv -> inv.getArgument(0));

    stubActiveCard(buyerId);

    CheckoutResponseDTO response = orderService.checkout(orderId, buyerId, cardId).response();

    assertThat(response).isNotNull();
    assertThat(response.orderId()).isEqualTo(orderId);
    assertThat(response.status()).isEqualTo(OrderStatus.PAID);
    assertThat(response.paidAt()).isNotNull();
    assertThat(response.tickets()).hasSize(2);
    assertThat(response.tickets().get(0).status()).isEqualTo(TicketStatus.VALID);
    assertThat(response.tickets().get(0).uniqueCode()).isNotNull();
    assertThat(response.unitPriceSnapshot()).isEqualByComparingTo("50.00");
    assertThat(response.totalPrice()).isEqualByComparingTo("100.00");

    assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
    assertThat(order.getPaidAt()).isNotNull();
    assertThat(lot.getReserved()).isEqualTo(3); // 5 - 2
    assertThat(lot.getSold()).isEqualTo(12); // 10 + 2

    verify(orderRepository).findByIdWithItemsForUpdate(orderId);
    verify(ticketLotRepository).findByIdForUpdate(lotId);
    verify(ticketRepository).saveAll(anyList());
  }

  @Test
  void deveRejeitarCheckoutQuandoPedidoNaoExistir() {
    UUID orderId = UUID.randomUUID();
    UUID buyerId = UUID.randomUUID();

    when(orderRepository.findByIdWithItemsForUpdate(orderId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> orderService.checkout(orderId, buyerId, cardId))
        .isInstanceOf(OrderNotFoundException.class)
        .hasMessageContaining("Pedido não encontrado");

    verify(orderRepository).findByIdWithItemsForUpdate(orderId);
    verify(ticketLotRepository, never()).findByIdForUpdate(any());
    verify(ticketRepository, never()).saveAll(anyList());
  }

  @Test
  void deveRejeitarCheckoutQuandoPedidoNaoPertenceAoComprador() {
    UUID orderId = UUID.randomUUID();
    UUID buyerId = UUID.randomUUID();
    UUID otherBuyerId = UUID.randomUUID();
    UUID lotId = UUID.randomUUID();
    Instant now = Instant.now();

    Order order = createPendingOrder(orderId, otherBuyerId, lotId, 2, new BigDecimal("50.00"), now.plus(10, ChronoUnit.MINUTES));

    when(orderRepository.findByIdWithItemsForUpdate(orderId)).thenReturn(Optional.of(order));

    assertThatThrownBy(() -> orderService.checkout(orderId, buyerId, cardId))
        .isInstanceOf(OrderAccessDeniedException.class)
        .hasMessageContaining("Acesso negado");

    verify(orderRepository).findByIdWithItemsForUpdate(orderId);
    verify(ticketLotRepository, never()).findByIdForUpdate(any());
  }

  @Test
  void deveRejeitarCheckoutQuandoPedidoJaPago() {
    UUID orderId = UUID.randomUUID();
    UUID buyerId = UUID.randomUUID();
    UUID lotId = UUID.randomUUID();
    Instant now = Instant.now();

    Order order = createPendingOrder(orderId, buyerId, lotId, 2, new BigDecimal("50.00"), now.plus(10, ChronoUnit.MINUTES));
    order.setStatus(OrderStatus.PAID);

    when(orderRepository.findByIdWithItemsForUpdate(orderId)).thenReturn(Optional.of(order));

    assertThatThrownBy(() -> orderService.checkout(orderId, buyerId, cardId))
        .isInstanceOf(OrderInvalidStatusException.class)
        .hasMessageContaining("não está pendente");

    verify(orderRepository).findByIdWithItemsForUpdate(orderId);
    verify(ticketLotRepository, never()).findByIdForUpdate(any());
  }

  @Test
  void deveRejeitarCheckoutQuandoPedidoExpirado() {
    UUID orderId = UUID.randomUUID();
    UUID buyerId = UUID.randomUUID();
    UUID lotId = UUID.randomUUID();
    Instant now = Instant.now();

    Order order = createPendingOrder(orderId, buyerId, lotId, 2, new BigDecimal("50.00"), now.minus(10, ChronoUnit.MINUTES));

    when(orderRepository.findByIdWithItemsForUpdate(orderId)).thenReturn(Optional.of(order));

    assertThatThrownBy(() -> orderService.checkout(orderId, buyerId, cardId))
        .isInstanceOf(OrderExpiredException.class)
        .hasMessageContaining("expirado");

    verify(orderRepository).findByIdWithItemsForUpdate(orderId);
    verify(ticketLotRepository, never()).findByIdForUpdate(any());
  }

  @Test
  void deveRejeitarCheckoutQuandoPedidoCancelado() {
    UUID orderId = UUID.randomUUID();
    UUID buyerId = UUID.randomUUID();
    UUID lotId = UUID.randomUUID();
    Instant now = Instant.now();

    Order order = createPendingOrder(orderId, buyerId, lotId, 2, new BigDecimal("50.00"), now.plus(10, ChronoUnit.MINUTES));
    order.setStatus(OrderStatus.CANCELLED);

    when(orderRepository.findByIdWithItemsForUpdate(orderId)).thenReturn(Optional.of(order));

    assertThatThrownBy(() -> orderService.checkout(orderId, buyerId, cardId))
        .isInstanceOf(OrderInvalidStatusException.class)
        .hasMessageContaining("não está pendente");

    verify(orderRepository).findByIdWithItemsForUpdate(orderId);
  }

  @Test
  void deveGerarQuantidadeCorretaDeTickets() {
    UUID orderId = UUID.randomUUID();
    UUID buyerId = UUID.randomUUID();
    UUID lotId = UUID.randomUUID();
    Instant now = Instant.now();

    Order order = createPendingOrder(orderId, buyerId, lotId, 5, new BigDecimal("50.00"), now.plus(10, ChronoUnit.MINUTES));
    TicketLot lot = createTicketLot(lotId, 100, 10, 5);

    when(orderRepository.findByIdWithItemsForUpdate(orderId)).thenReturn(Optional.of(order));
    when(orderRepository.findByIdWithItemsForUpdate(orderId)).thenReturn(Optional.of(order));
    when(orderRepository.findByIdWithItemsForUpdate(orderId)).thenReturn(Optional.of(order));
    when(ticketLotRepository.findByIdForUpdate(lotId)).thenReturn(Optional.of(lot));
    when(ticketRepository.existsByUniqueCode(anyString())).thenReturn(false);
    when(ticketRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
    when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
    when(ticketLotRepository.save(any(TicketLot.class))).thenAnswer(inv -> inv.getArgument(0));

    stubActiveCard(buyerId);

    CheckoutResponseDTO response = orderService.checkout(orderId, buyerId, cardId).response();

    assertThat(response.tickets()).hasSize(5);
    response.tickets().forEach(ticket -> {
      assertThat(ticket.status()).isEqualTo(TicketStatus.VALID);
      assertThat(ticket.uniqueCode()).isNotNull();
    });
  }

  @Test
  void devePreservarUnitPriceSnapshot() {
    UUID orderId = UUID.randomUUID();
    UUID buyerId = UUID.randomUUID();
    UUID lotId = UUID.randomUUID();
    BigDecimal unitPriceSnapshot = new BigDecimal("75.50");
    BigDecimal lotCurrentPrice = new BigDecimal("99.99");
    Instant now = Instant.now();

    Order order = createPendingOrder(orderId, buyerId, lotId, 2, unitPriceSnapshot, now.plus(10, ChronoUnit.MINUTES));
    TicketLot lot = TicketLot.builder()
        .event(PUBLISHED_EVENT)
        .status(LotStatus.ACTIVE)
        .id(lotId)
        .capacity(100)
        .sold(10)
        .reserved(5)
        .unitPrice(lotCurrentPrice)
        .build();

    when(orderRepository.findByIdWithItemsForUpdate(orderId)).thenReturn(Optional.of(order));
    when(orderRepository.findByIdWithItemsForUpdate(orderId)).thenReturn(Optional.of(order));
    when(orderRepository.findByIdWithItemsForUpdate(orderId)).thenReturn(Optional.of(order));
    when(ticketLotRepository.findByIdForUpdate(lotId)).thenReturn(Optional.of(lot));
    when(ticketRepository.existsByUniqueCode(anyString())).thenReturn(false);
    when(ticketRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
    when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
    when(ticketLotRepository.save(any(TicketLot.class))).thenAnswer(inv -> inv.getArgument(0));

    stubActiveCard(buyerId);

    CheckoutResponseDTO response = orderService.checkout(orderId, buyerId, cardId).response();

    assertThat(response.unitPriceSnapshot()).isEqualByComparingTo("75.50");
    assertThat(response.totalPrice()).isEqualByComparingTo("151.00");
  }

  @Test
  void deveConverterReservaEmVendaCorretamente() {
    UUID orderId = UUID.randomUUID();
    UUID buyerId = UUID.randomUUID();
    UUID lotId = UUID.randomUUID();
    Instant now = Instant.now();

    Order order = createPendingOrder(orderId, buyerId, lotId, 3, new BigDecimal("50.00"), now.plus(10, ChronoUnit.MINUTES));
    TicketLot lot = createTicketLot(lotId, 100, 20, 10);

    when(orderRepository.findByIdWithItemsForUpdate(orderId)).thenReturn(Optional.of(order));
    when(orderRepository.findByIdWithItemsForUpdate(orderId)).thenReturn(Optional.of(order));
    when(ticketLotRepository.findByIdForUpdate(lotId)).thenReturn(Optional.of(lot));
    when(ticketRepository.existsByUniqueCode(anyString())).thenReturn(false);
    when(ticketRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
    when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
    when(ticketLotRepository.save(any(TicketLot.class))).thenAnswer(inv -> inv.getArgument(0));

    stubActiveCard(buyerId);

    orderService.checkout(orderId, buyerId, cardId);

    assertThat(lot.getReserved()).isEqualTo(7); // 10 - 3
    assertThat(lot.getSold()).isEqualTo(23); // 20 + 3
  }

  @Test
  void deveGerarTicketsComUUIDsEcodigosUnicos() {
    UUID orderId = UUID.randomUUID();
    UUID buyerId = UUID.randomUUID();
    UUID lotId = UUID.randomUUID();
    Instant now = Instant.now();

    Order order = createPendingOrder(orderId, buyerId, lotId, 3, new BigDecimal("50.00"), now.plus(10, ChronoUnit.MINUTES));
    TicketLot lot = createTicketLot(lotId, 100, 10, 5);

    when(orderRepository.findByIdWithItemsForUpdate(orderId)).thenReturn(Optional.of(order));
    when(orderRepository.findByIdWithItemsForUpdate(orderId)).thenReturn(Optional.of(order));
    when(ticketLotRepository.findByIdForUpdate(lotId)).thenReturn(Optional.of(lot));
    when(ticketRepository.existsByUniqueCode(anyString())).thenReturn(false);
    // Mock saveAll to return tickets with generated IDs and uniqueCodes
    when(ticketRepository.saveAll(anyList())).thenAnswer(inv -> {
        List<Ticket> tickets = inv.getArgument(0);
        return tickets.stream().map(t -> Ticket.builder()
                .id(UUID.randomUUID())
                .order(t.getOrder())
                .uniqueCode(t.getUniqueCode())
                .status(t.getStatus())
                .build()).toList();
    });
    when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
    when(ticketLotRepository.save(any(TicketLot.class))).thenAnswer(inv -> inv.getArgument(0));

    stubActiveCard(buyerId);

    CheckoutResponseDTO response = orderService.checkout(orderId, buyerId, cardId).response();

    assertThat(response.tickets()).hasSize(3);
    var uniqueCodes = response.tickets().stream().map(t -> t.uniqueCode()).toList();
    var ids = response.tickets().stream().map(t -> t.id()).toList();

    // All unique codes should be distinct
    assertThat(uniqueCodes).hasSize(3).doesNotHaveDuplicates();
    // All IDs should be distinct
    assertThat(ids).hasSize(3).doesNotHaveDuplicates();
  }

  @Test
  void devePreencherPaidAtNoCheckout() {
    UUID orderId = UUID.randomUUID();
    UUID buyerId = UUID.randomUUID();
    UUID lotId = UUID.randomUUID();
    Instant now = Instant.now();

    Order order = createPendingOrder(orderId, buyerId, lotId, 2, new BigDecimal("50.00"), now.plus(10, ChronoUnit.MINUTES));
    TicketLot lot = createTicketLot(lotId, 100, 10, 5);

    when(orderRepository.findByIdWithItemsForUpdate(orderId)).thenReturn(Optional.of(order));
    when(ticketLotRepository.findByIdForUpdate(lotId)).thenReturn(Optional.of(lot));
    when(ticketRepository.existsByUniqueCode(anyString())).thenReturn(false);
    when(ticketRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
    when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
    when(ticketLotRepository.save(any(TicketLot.class))).thenAnswer(inv -> inv.getArgument(0));

    stubActiveCard(buyerId);

    CheckoutResponseDTO response = orderService.checkout(orderId, buyerId, cardId).response();

    assertThat(response.paidAt()).isNotNull();
    assertThat(response.paidAt()).isAfterOrEqualTo(now);
  }

  @Test
  void naoDeveClassificarPedidoPagoComoExpiradoMesmoComPrazoJaPassado() {
    UUID orderId = UUID.randomUUID();
    UUID buyerId = UUID.randomUUID();
    UUID lotId = UUID.randomUUID();
    Instant now = Instant.now();

    // Pedido pago cujo prazo já passou: status deve prevalecer sobre expiração (409, não 422)
    Order order = createPendingOrder(orderId, buyerId, lotId, 2, new BigDecimal("50.00"), now.minus(10, ChronoUnit.MINUTES));
    order.setStatus(OrderStatus.PAID);

    when(orderRepository.findByIdWithItemsForUpdate(orderId)).thenReturn(Optional.of(order));

    assertThatThrownBy(() -> orderService.checkout(orderId, buyerId, cardId))
        .isInstanceOf(OrderInvalidStatusException.class)
        .hasMessageContaining("não está pendente")
        .satisfies(ex -> assertThat(((OrderInvalidStatusException) ex).getCurrentStatus())
            .isEqualTo(OrderStatus.PAID));

    verify(ticketLotRepository, never()).findByIdForUpdate(any());
    verify(ticketRepository, never()).saveAll(anyList());
  }

  @Test
  void deveExporStatusCanceladoNaExcecaoParaMapeamento422() {
    UUID orderId = UUID.randomUUID();
    UUID buyerId = UUID.randomUUID();
    UUID lotId = UUID.randomUUID();
    Instant now = Instant.now();

    Order order = createPendingOrder(orderId, buyerId, lotId, 2, new BigDecimal("50.00"), now.plus(10, ChronoUnit.MINUTES));
    order.setStatus(OrderStatus.CANCELLED);

    when(orderRepository.findByIdWithItemsForUpdate(orderId)).thenReturn(Optional.of(order));

    assertThatThrownBy(() -> orderService.checkout(orderId, buyerId, cardId))
        .isInstanceOf(OrderInvalidStatusException.class)
        .satisfies(ex -> assertThat(((OrderInvalidStatusException) ex).getCurrentStatus())
            .isEqualTo(OrderStatus.CANCELLED));

    verify(ticketLotRepository, never()).findByIdForUpdate(any());
  }
}
