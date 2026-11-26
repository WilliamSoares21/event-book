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
        // Lock the Order first to ensure atomicity
        Order lockedOrder = orderRepository.findByIdWithItemsForUpdate(order.getId())
                .orElseThrow(() -> new IllegalStateException("Pedido não encontrado: " + order.getId()));

        // Handle both grouped and legacy orders
        UUID lotId;
        int quantity;
        if (!lockedOrder.getItems().isEmpty()) {
            OrderItem firstItem = lockedOrder.getItems().get(0);
            lotId = firstItem.getTicketLot().getId();
            quantity = firstItem.getQuantity();
        } else if (lockedOrder.isLegacyOrder() && lockedOrder.getTicketLot() != null) {
            lotId = lockedOrder.getTicketLot().getId();
            quantity = lockedOrder.getQuantity();
        } else {
            log.warn("Pedido {} sem lote associado", order.getId());
            return;
        }

        // Re-validate within the lock
        if (lockedOrder.getStatus() != OrderStatus.PENDING) {
            return;
        }

        if (lockedOrder.getExpiresAt() == null || lockedOrder.getExpiresAt().isAfter(Instant.now())) {
            return;
        }

        // Now lock the TicketLot
        TicketLot lot = ticketLotRepository.findByIdForUpdate(lotId)
                .orElseThrow(() -> new IllegalStateException("Lote não encontrado: " + lotId));

        // Both validations passed, process the expiration
        lockedOrder.setStatus(OrderStatus.EXPIRED);
        lot.decrementReserved(quantity);

        orderRepository.save(lockedOrder);
        ticketLotRepository.save(lot);

        log.debug("Pedido {} expirado, {} unidades liberadas do lote {}", order.getId(), quantity, lotId);
    }
}