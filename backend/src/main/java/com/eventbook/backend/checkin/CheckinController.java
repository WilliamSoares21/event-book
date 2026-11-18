package com.eventbook.backend.checkin;

import com.eventbook.backend.checkin.dto.CheckInConflictResponseDTO;
import com.eventbook.backend.checkin.dto.CheckInResponseDTO;
import com.eventbook.backend.checkin.exception.EventOutsideCheckinWindowException;
import com.eventbook.backend.checkin.exception.InvalidTicketCodeException;
import com.eventbook.backend.checkin.exception.OrderNotPaidException;
import com.eventbook.backend.checkin.exception.StaffNotAuthorizedException;
import com.eventbook.backend.checkin.exception.TicketAlreadyCheckedInException;
import com.eventbook.backend.checkin.exception.TicketInvalidStatusException;
import com.eventbook.backend.checkin.exception.TicketNotFoundException;
import com.eventbook.backend.orders.OrderStatus;
import com.eventbook.backend.security.AuthenticatedUser;
import com.eventbook.backend.tickets.TicketStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/tickets")
public class CheckinController {

    private final CheckinService checkinService;

    @PreAuthorize("hasRole('STAFF')")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/{code}/check-in")
    @Operation(summary = "Realiza check-in de um ticket")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Check-in realizado com sucesso",
                    content = @Content(schema = @Schema(implementation = CheckInResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "Código ausente, vazio ou maior que 64 caracteres"),
            @ApiResponse(responseCode = "401", description = "JWT ausente ou inválido",
                    content = @Content(schema = @Schema(example = "{\"status\":401,\"error\":\"Autenticação necessária\",\"timestamp\":\"...\"}"))),
            @ApiResponse(responseCode = "403", description = "Usuário sem role STAFF ou não associado ao evento",
                    content = @Content(schema = @Schema(example = "{\"status\":403,\"error\":\"Acesso negado\",\"timestamp\":\"...\"}"))),
            @ApiResponse(responseCode = "404", description = "Ticket inexistente",
                    content = @Content(schema = @Schema(example = "{\"status\":404,\"error\":\"Ticket não encontrado\",\"timestamp\":\"...\"}"))),
            @ApiResponse(responseCode = "409", description = "Ticket já possui check-in; retorna dados do check-in original",
                    content = @Content(schema = @Schema(implementation = CheckInConflictResponseDTO.class))),
            @ApiResponse(responseCode = "422", description = "Pedido não pago, ticket inválido ou evento fora da janela",
                    content = @Content(schema = @Schema(example = "{\"status\":422,\"error\":\"Evento fora da janela de check-in\",\"timestamp\":\"...\"}")))
    })
    public ResponseEntity<Object> checkIn(
            @Parameter(description = "Código único do ticket (máx. 64 caracteres)", example = "A1B2C3D4E5F6")
            @PathVariable
            @NotBlank
            @Size(max = 64)
            String code,
            @AuthenticationPrincipal AuthenticatedUser authUser) {

        UUID staffId = authUser.id();
        CheckInResponseDTO response = checkinService.checkIn(code, staffId);
        return ResponseEntity.ok(response);
    }

    @ExceptionHandler(TicketNotFoundException.class)
    public ResponseEntity<Object> handleTicketNotFound(TicketNotFoundException ex) {
        return buildError(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(TicketAlreadyCheckedInException.class)
    public ResponseEntity<Object> handleTicketAlreadyCheckedIn(TicketAlreadyCheckedInException ex) {
        CheckInConflictResponseDTO conflict = CheckInConflictResponseDTO.from(ex.getExistingCheckIn());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(conflict);
    }

    @ExceptionHandler(OrderNotPaidException.class)
    public ResponseEntity<Object> handleOrderNotPaid(OrderNotPaidException ex) {
        return buildError(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
    }

    @ExceptionHandler(TicketInvalidStatusException.class)
    public ResponseEntity<Object> handleTicketInvalidStatus(TicketInvalidStatusException ex) {
        return buildError(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
    }

    @ExceptionHandler(EventOutsideCheckinWindowException.class)
    public ResponseEntity<Object> handleEventOutsideWindow(EventOutsideCheckinWindowException ex) {
        return buildError(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
    }

    @ExceptionHandler(StaffNotAuthorizedException.class)
    public ResponseEntity<Object> handleStaffNotAuthorized(StaffNotAuthorizedException ex) {
        return buildError(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(InvalidTicketCodeException.class)
    public ResponseEntity<Object> handleInvalidTicketCode(InvalidTicketCodeException ex) {
        return buildError(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    private ResponseEntity<Object> buildError(HttpStatus status, String message) {
        return ResponseEntity.status(status)
                .body(new ErrorResponse(status.value(), message));
    }

    private record ErrorResponse(int status, String error) {
        // timestamp is added by the global handler, but for controller-level handlers we include it manually if needed
    }
}