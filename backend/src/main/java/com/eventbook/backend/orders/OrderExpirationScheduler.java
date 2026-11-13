package com.eventbook.backend.orders;

import com.eventbook.backend.lots.TicketLot;
import com.eventbook.backend.lots.TicketLotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderExpirationScheduler {

    private final OrderRepository orderRepository;
    private final TicketLotRepository ticketLotRepository;

    private static final int BATCH_SIZE = 50;

    @Scheduled(fixedDelayString = "${orders.expiration.check-interval-ms:60000}")
    @Transactional
    public void processExpiredOrders() {
        Instant now = Instant.now();
        List<Order> expiredOrders = orderRepository.findByStatusAndExpiresAtBefore(
                OrderStatus.PENDING, now);

        if (expiredOrders.isEmpty()) {
            return;
        }

        log.info("Processando {} pedidos expirados", expiredOrders.size());

        int processed = 0;
        for (Order order : expiredOrders) {
            if (processed >= BATCH_SIZE) {
                break;
            }

            try {
                processSingleExpiredOrder(order);
                processed++;
            } catch (Exception e) {
                log.error("Erro ao processar expiração do pedido {}: {}", order.getId(), e.getMessage());
            }
        }

        if (processed > 0) {
            log.info("{} pedidos expirados processados", processed);
        }
    }

    @Transactional
    public void processSingleExpiredOrder(Order order) {
        if (order.getStatus() != OrderStatus.PENDING) {
            return;
        }

        if (order.getExpiresAt() == null || order.getExpiresAt().isAfter(Instant.now())) {
            return;
        }

        UUID lotId = order.getTicketLot().getId();
        int quantity = order.getQuantity();

        TicketLot lot = ticketLotRepository.findByIdForUpdate(lotId)
                .orElse(null);

        if (lot == null) {
            log.warn("Lote {} não encontrado para pedido expirado {}", lotId, order.getId());
            return;
        }

        order.setStatus(OrderStatus.EXPIRED);
        lot.decrementReserved(quantity);

        orderRepository.save(order);
        ticketLotRepository.save(lot);

        log.debug("Pedido {} expirado, {} unidades liberadas do lote {}", order.getId(), quantity, lotId);
    }
}