package com.eventbook.backend.orders;

import com.eventbook.backend.lots.LotStatus;
import com.eventbook.backend.lots.TicketLot;
import com.eventbook.backend.lots.TicketLotRepository;
import com.eventbook.backend.orders.exception.InsufficientCapacityException;
import com.eventbook.backend.orders.exception.LotNotActiveException;
import com.eventbook.backend.orders.exception.OutsideSalesWindowException;
import com.eventbook.backend.users.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderService {

    private static final int RESERVATION_MINUTES = 15;

    private final OrderRepository orderRepository;
    private final TicketLotRepository ticketLotRepository;

    @Transactional
    public Order reserve(User buyer, UUID ticketLotId, int quantity) {
        TicketLot lot = ticketLotRepository.findByIdForUpdate(ticketLotId)
                .orElseThrow(() -> new IllegalArgumentException("Lote não encontrado: " + ticketLotId));

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

        lot.setReserved(lot.getReserved() + quantity);
        ticketLotRepository.save(lot);

        Order order = Order.builder()
                .buyer(buyer)
                .ticketLot(lot)
                .quantity(quantity)
                .unitPriceSnapshot(lot.getUnitPrice()) // RN-10: preço congela aqui
                .status(OrderStatus.PENDING)
                .expiresAt(now.plus(RESERVATION_MINUTES, ChronoUnit.MINUTES))
                .build();

        return orderRepository.save(order);
    }
}

