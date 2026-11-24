package com.eventbook.backend.events;

import com.eventbook.backend.events.dto.EventImageConfirmRequestDTO;
import com.eventbook.backend.events.dto.EventImageResponseDTO;
import com.eventbook.backend.events.dto.EventImageUploadRequestDTO;
import com.eventbook.backend.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/events/{eventId}/images")
public class EventImageController {

    private final EventImageService eventImageService;

    /**
     * Gera uma presigned URL para upload de imagem de capa.
     * O frontend faz o upload direto para o R2 usando esta URL.
     */
    @PreAuthorize("hasRole('ORGANIZADOR')")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/upload-url")
    @Operation(summary = "Gera URL pré-assinada para upload de imagem de capa")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "URL de upload gerada"),
            @ApiResponse(responseCode = "400", description = "Payload inválido"),
            @ApiResponse(responseCode = "401", description = "JWT ausente ou inválido"),
            @ApiResponse(responseCode = "403", description = "Usuário não é o organizador do evento"),
            @ApiResponse(responseCode = "404", description = "Evento não encontrado"),
            @ApiResponse(responseCode = "422", description = "Tipo de arquivo não suportado")
    })
    public ResponseEntity<EventImageUploadUrlResponse> getUploadUrl(
            @PathVariable UUID eventId,
            @Valid @RequestBody EventImageUploadRequestDTO request,
            @AuthenticationPrincipal AuthenticatedUser authUser) {

        var result = eventImageService.generateUploadUrl(
                eventId,
                authUser.id(),
                request.getContentType(),
                request.getSha256Base64(),
                request.getWriteId(),
                request.getSizeBytes()
        );

        return ResponseEntity.ok(new EventImageUploadUrlResponse(
                result.uploadUrl(),
                result.objectKey(),
                result.publicUrl(),
                result.expiresAt()
        ));
    }

    /**
     * Confirma o upload da imagem após o frontend fazer o PUT no R2.
     * Valida o objeto e persiste os metadados.
     */
    @PreAuthorize("hasRole('ORGANIZADOR')")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/confirm")
    @Operation(summary = "Confirma upload de imagem de capa")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Imagem confirmada e associada ao evento"),
            @ApiResponse(responseCode = "400", description = "Payload inválido ou objeto não encontrado no storage"),
            @ApiResponse(responseCode = "401", description = "JWT ausente ou inválido"),
            @ApiResponse(responseCode = "403", description = "Usuário não é o organizador do evento"),
            @ApiResponse(responseCode = "404", description = "Evento não encontrado"),
            @ApiResponse(responseCode = "422", description = "Validação de integridade falhou")
    })
    public ResponseEntity<EventImageResponseDTO> confirmUpload(
            @PathVariable UUID eventId,
            @Valid @RequestBody EventImageConfirmRequestDTO request,
            @AuthenticationPrincipal AuthenticatedUser authUser) {

        EventImageResponseDTO image = eventImageService.confirmUpload(
                eventId,
                authUser.id(),
                request.getObjectKey(),
                request.getWriteId(),
                request.getSha256Base64(),
                request.getSizeBytes()
        );

        return ResponseEntity.ok(image);
    }

    /**
     * Remove a imagem de capa do evento.
     */
    @PreAuthorize("hasRole('ORGANIZADOR')")
    @SecurityRequirement(name = "bearerAuth")
    @DeleteMapping
    @Operation(summary = "Remove a imagem de capa do evento")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Imagem removida com sucesso"),
            @ApiResponse(responseCode = "401", description = "JWT ausente ou inválido"),
            @ApiResponse(responseCode = "403", description = "Usuário não é o organizador do evento"),
            @ApiResponse(responseCode = "404", description = "Evento não encontrado ou não possui imagem")
    })
    public ResponseEntity<Void> removeImage(
            @PathVariable UUID eventId,
            @AuthenticationPrincipal AuthenticatedUser authUser) {

        eventImageService.removeImage(eventId, authUser.id());
        return ResponseEntity.noContent().build();
    }

    /**
     * Consulta a imagem de capa do evento (público para eventos publicados).
     */
    @GetMapping
    @Operation(summary = "Consulta a imagem de capa do evento")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Imagem encontrada"),
            @ApiResponse(responseCode = "404", description = "Evento não encontrado ou não possui imagem"),
            @ApiResponse(responseCode = "400", description = "UUID inválido")
    })
    public ResponseEntity<EventImageResponseDTO> getEventImage(
            @PathVariable UUID eventId,
            @AuthenticationPrincipal(errorOnInvalidType = false) AuthenticatedUser authUser) {

        UUID requesterId = authUser != null ? authUser.id() : null;
        return eventImageService.getEventImage(eventId, requesterId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    public record EventImageUploadUrlResponse(
            String uploadUrl,
            String objectKey,
            String publicUrl,
            java.time.Instant expiresAt
    ) {}
}