
package com.eventbook.backend.orders;

import com.eventbook.backend.common.exception.BusinessException;
import com.eventbook.backend.orders.dto.CheckoutResponseDTO;
import com.eventbook.backend.orders.dto.OrderRequestDTO;
import com.eventbook.backend.orders.dto.OrderResponseDTO;
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
    @Operation(summary = "Cria um pedido e reserva ingressos")
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

        Order order = orderService.reserve(
                buyer,
                dto.ticketLotId(),
                dto.quantity()
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
    @Operation(summary = "Realiza o checkout simulado de um pedido pendente")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Checkout aprovado, tickets emitidos"),
            @ApiResponse(responseCode = "400", description = "UUID inválido"),
            @ApiResponse(responseCode = "401", description = "Não autenticado"),
            @ApiResponse(responseCode = "403", description = "Usuário não é comprador ou pedido não pertence ao usuário"),
            @ApiResponse(responseCode = "404", description = "Pedido não encontrado"),
            @ApiResponse(responseCode = "409", description = "Pedido já pago ou conflito de concorrência"),
            @ApiResponse(responseCode = "422", description = "Pedido expirado ou status incompatível (ex.: cancelado)")
    })
    public ResponseEntity<CheckoutResponseDTO> checkout(
            @PathVariable UUID orderId,
            @AuthenticationPrincipal AuthenticatedUser authUser) {

        User buyer = userRepository.findById(authUser.id())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Comprador não encontrado"
                ));

        CheckoutResponseDTO response = orderService.checkout(orderId, buyer.getId());

        return ResponseEntity.ok(response);
    }
}
