package com.eventbook.backend.common.exception;

import com.eventbook.backend.orders.OrderStatus;
import com.eventbook.backend.orders.exception.OrderAccessDeniedException;
import com.eventbook.backend.orders.exception.OrderExpiredException;
import com.eventbook.backend.orders.exception.OrderInvalidStatusException;
import com.eventbook.backend.orders.exception.OrderNotFoundException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void pedidoPagoDeveMapearPara409() {
        var response = handler.handleOrderInvalidStatus(
                new OrderInvalidStatusException("Pedido não está disponível", OrderStatus.PAID));

        assertThat(response.getStatusCode().value()).isEqualTo(409);
    }

    @Test
    void pedidoCanceladoDeveMapearPara422() {
        var response = handler.handleOrderInvalidStatus(
                new OrderInvalidStatusException("Pedido não está disponível", OrderStatus.CANCELLED));

        assertThat(response.getStatusCode().value()).isEqualTo(422);
    }

    @Test
    void pedidoExpiradoComStatusIncompativelDeveMapearPara422() {
        var response = handler.handleOrderInvalidStatus(
                new OrderInvalidStatusException("Pedido não está disponível", OrderStatus.EXPIRED));

        assertThat(response.getStatusCode().value()).isEqualTo(422);
    }

    @Test
    void pedidoPendenteExpiradoDeveMapearPara422() {
        var response = handler.handleOrderExpired(
                new OrderExpiredException("Pedido expirado"));

        assertThat(response.getStatusCode().value()).isEqualTo(422);
    }

    @Test
    void pedidoInexistenteDeveMapearPara404() {
        var response = handler.handleOrderNotFound(
                new OrderNotFoundException("Pedido não encontrado"));

        assertThat(response.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void acessoNegadoDeveMapearPara403() {
        var response = handler.handleOrderAccessDenied(
                new OrderAccessDeniedException("Acesso negado"));

        assertThat(response.getStatusCode().value()).isEqualTo(403);
    }
}
