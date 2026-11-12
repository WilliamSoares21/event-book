
package com.eventbook.backend.orders;

import com.eventbook.backend.lots.LotStatus;
import com.eventbook.backend.lots.TicketLot;
import com.eventbook.backend.lots.TicketLotRepository;
import com.eventbook.backend.orders.exception.InsufficientCapacityException;
import com.eventbook.backend.orders.exception.LotNotActiveException;
import com.eventbook.backend.orders.exception.OutsideSalesWindowException;
import com.eventbook.backend.users.User;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hibernate.validator.internal.util.Contracts.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private TicketLotRepository ticketLotRepository;

    @InjectMocks
    private OrderService orderService;


    @Test
    void deveReservarIngressosComSucesso() {


        UUID ticketLotId = UUID.randomUUID();

        User buyer = User.builder()
                .id(UUID.randomUUID())
                .build();

        Instant now = Instant.now();

        TicketLot lot = TicketLot.builder()
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



        assertThatThrownBy(() ->
                orderService.reserve(buyer, ticketLotId, 2)
        )
                .isInstanceOf(IllegalArgumentException.class)
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
                .id(ticketLotId)
                .status(LotStatus.INACTIVE)
                .build();

        when(ticketLotRepository.findByIdForUpdate(ticketLotId))
                .thenReturn(Optional.of(lot));



        assertThatThrownBy(() ->
                orderService.reserve(buyer, ticketLotId, 2)
        )
                .isInstanceOf(LotNotActiveException.class)
                .hasMessage("Lote não está ativo para venda");

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
                .id(ticketLotId)
                .status(LotStatus.ACTIVE)
                .salesStart(now.minus(1, ChronoUnit.HOURS))
                .salesEnd(now.plus(2, ChronoUnit.HOURS))
                .build();

        when(ticketLotRepository.findByIdForUpdate(ticketLotId))
                .thenReturn(Optional.of(lot));



        assertThatThrownBy(() ->
                orderService.reserve(buyer, ticketLotId, 2)
        )
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



        assertThatThrownBy(() ->
                orderService.reserve(buyer, ticketLotId, 10)
        )
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
    void deveRejeitarReservaQuandoVendasJaTiveremEncerrado (){

    }



}

