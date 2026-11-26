
package com.eventbook.backend.orders;

import com.eventbook.backend.common.exception.BusinessException;
import com.eventbook.backend.orders.dto.CheckoutRequestDTO;
import com.eventbook.backend.orders.dto.CheckoutResponseDTO;
import com.eventbook.backend.orders.dto.OrderRequestDTO;
import com.eventbook.backend.orders.dto.OrderResponseDTO;
import com.eventbook.backend.orders.exception.OrderHasCheckedInTicketException;
import com.eventbook.backend.payments.exception.PaymentDeclinedException;
import com.eventbook.backend.security.AuthenticatedUser;
import com.eventbook.backend.users.User;
import com.eventbook.backend.users.UserRepository;

import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;
    private final UserRepository userRepository;

    @PostMapping
    @PreAuthorize("hasRole('COMPRADOR')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Cria um pedido agrupado e reserva ingressos de múltiplos lotes")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Pedido criado"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Usuário não é comprador"),
            @ApiResponse(responseCode = "404", description = "Lote não encontrado"),
            @ApiResponse(responseCode = "409", description = "Estoque insuficiente"),
            @ApiResponse(responseCode = "422", description = "Lote inativo ou fora da janela de vendas")
    })
    public ResponseEntity<OrderResponseDTO> createOrder(
            @Valid @RequestBody OrderRequestDTO dto,
            @AuthenticationPrincipal AuthenticatedUser authUser) {

        User buyer = userRepository.findById(authUser.id())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Comprador não encontrado"
                ));

        Order order = orderService.reserveItems(
                buyer,
                dto.items()
        );

        OrderResponseDTO response = OrderResponseDTO.from(order);

        URI location = URI.create("/api/orders/" + order.getId());

        return ResponseEntity
                .created(location)
                .body(response);
    }

    @PostMapping("/{orderId}/checkout")
    @PreAuthorize("hasRole('COMPRADOR')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Realiza o checkout simulado de um pedido pendente com cartão fictício")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Checkout aprovado, tickets emitidos"),
            @ApiResponse(responseCode = "400", description = "UUID inválido"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Pedido ou cartão de outro usuário"),
            @ApiResponse(responseCode = "404", description = "Pedido ou cartão não encontrado"),
            @ApiResponse(responseCode = "409", description = "Pedido já pago ou conflito de concorrência"),
            @ApiResponse(responseCode = "422", description = "Pedido expirado/cancelado, cartão inativo ou vencido, limite insuficiente (INSUFFICIENT_CARD_LIMIT)")
    })
    public ResponseEntity<CheckoutResponseDTO> checkout(
            @PathVariable UUID orderId,
            @Valid @RequestBody CheckoutRequestDTO dto,
            @AuthenticationPrincipal AuthenticatedUser authUser) {

        User buyer = userRepository.findById(authUser.id())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Comprador não encontrado"
                ));

        CheckoutResult result = orderService.checkout(orderId, buyer.getId(), dto.cardId());

        // Recusa comercial: lançada após o commit da transação, para preservar o registro DECLINED.
        if (result.isDeclined()) {
            throw new PaymentDeclinedException(
                    result.declineCode(),
                    "Pagamento não aprovado por limite insuficiente. O pedido continua pendente.");
        }

        return ResponseEntity.ok(result.response());
    }

    @DeleteMapping("/{orderId}")
    @PreAuthorize("hasRole('COMPRADOR') or hasRole('ORGANIZADOR')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Cancela um pedido (comprador ou organizador do evento)")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Pedido cancelado com sucesso"),
            @ApiResponse(responseCode = "400", description = "UUID inválido"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Usuário não autorizado a cancelar este pedido"),
            @ApiResponse(responseCode = "404", description = "Pedido não encontrado"),
            @ApiResponse(responseCode = "409", description = "Pedido já cancelado"),
            @ApiResponse(responseCode = "422", description = "Pedido expirado, fora do prazo, ou possui ingresso com check-in")
    })
    public ResponseEntity<Void> cancelOrder(
            @PathVariable UUID orderId,
            @AuthenticationPrincipal AuthenticatedUser authUser) {

        User user = userRepository.findById(authUser.id())
                .orElseThrow(() -> new EntityNotFoundException("Usuário não encontrado"));

        orderService.cancel(orderId, user.getId());

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('COMPRADOR')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Lista os pedidos do comprador autenticado")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de pedidos do comprador"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Usuário não é comprador")
    })
    public ResponseEntity<List<OrderResponseDTO>> listOwnOrders(
            @AuthenticationPrincipal AuthenticatedUser authUser) {

        List<OrderResponseDTO> orders = orderService.findOwnOrders(authUser.id());
        return ResponseEntity.ok(orders);
    }
}
