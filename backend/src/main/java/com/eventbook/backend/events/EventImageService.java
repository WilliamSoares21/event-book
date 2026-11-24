package com.eventbook.backend.events;

import com.eventbook.backend.common.exception.BusinessException;
import com.eventbook.backend.config.R2Properties;
import com.eventbook.backend.events.dto.EventImageResponseDTO;
import com.eventbook.backend.storage.R2StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EventImageService {

    private final EventImageRepository eventImageRepository;
    private final EventRepository eventRepository;
    private final R2StorageService r2StorageService;
    private final R2Properties r2Properties;

    /**
     * Gera uma presigned URL para upload de nova imagem de capa.
     * Verifica se o usuário é o organizador do evento.
     */
    @Transactional(readOnly = true)
    public R2StorageService.PresignedUploadResult generateUploadUrl(
            UUID eventId, UUID organizerId, String contentType, String sha256Base64, String writeId, long sizeBytes) {

        // Valida se o evento existe e pertence ao organizador
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new BusinessException(
                        "Evento não encontrado",
                        HttpStatus.NOT_FOUND
                ));

        if (!event.getOrganizer().getId().equals(organizerId)) {
            throw new BusinessException(
                    "Somente o organizador pode gerenciar a imagem do evento",
                    HttpStatus.FORBIDDEN
                );
        }

        // Valida content type
        validateContentType(contentType);
        validateSize(sizeBytes);

        // Se já existe uma imagem, não impede o upload - a substituição é tratada na confirmação
        return r2StorageService.generatePresignedUploadUrl(contentType, sha256Base64, writeId, sizeBytes);
    }

    /**
     * Confirma o upload da imagem após o frontend fazer o PUT no R2.
     * Valida o objeto no R2, remove a imagem anterior se houver, e persiste os metadados.
     */
    @Transactional
    public EventImageResponseDTO confirmUpload(
            UUID eventId, UUID organizerId, String objectKey, String writeId, String sha256Base64, long sizeBytes) {

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new BusinessException(
                        "Evento não encontrado",
                        HttpStatus.NOT_FOUND
                ));

        if (!event.getOrganizer().getId().equals(organizerId)) {
            throw new BusinessException(
                    "Somente o organizador pode gerenciar a imagem do evento",
                    HttpStatus.FORBIDDEN
                );
        }

        // Valida o objeto no R2
        R2StorageService.ValidationResult validation = r2StorageService.validateUploadedObject(
                objectKey, writeId, sha256Base64
        );

        if (!validation.exists()) {
            throw new BusinessException(
                    "Objeto não encontrado no storage. Upload pode ter falhado.",
                    HttpStatus.BAD_REQUEST
            );
        }

        validateSize(sizeBytes);
        if (validation.contentLength() != sizeBytes) {
            throw new BusinessException(
                    "Tamanho do arquivo no storage não corresponde ao declarado.",
                    HttpStatus.BAD_REQUEST
            );
        }

        if (!validation.writeIdMatches()) {
            throw new BusinessException(
                    "Validação de integridade falhou (writeId).",
                    HttpStatus.BAD_REQUEST
            );
        }

        if (!validation.sha256Matches()) {
            throw new BusinessException(
                    "Validação de integridade falhou (checksum).",
                    HttpStatus.BAD_REQUEST
            );
        }

        // Busca imagem anterior para deletar depois do commit
        String previousObjectKey = null;
        Optional<EventImage> existingImage = eventImageRepository.findByEventId(eventId);
        if (existingImage.isPresent()) {
            previousObjectKey = existingImage.get().getObjectKey();
            // Remove a imagem anterior do banco (o objeto R2 será deletado após commit)
            eventImageRepository.deleteByEventId(eventId);
        }

        // Constrói a URL pública
        String publicUrl = buildPublicUrlFromProperties(objectKey, r2Properties.getPublicBaseUrl());

        // Persiste a nova imagem
        EventImage newImage = EventImage.builder()
                .event(event)
                .objectKey(objectKey)
                .publicUrl(publicUrl)
                .contentType(validation.contentType())
                .sizeBytes(validation.contentLength())
                .uploadedAt(java.time.OffsetDateTime.now())
                .build();

        eventImageRepository.save(newImage);

        // Deleta a imagem anterior do R2 após o commit bem-sucedido
        // Nota: Em produção ideal, isso seria feito via @TransactionalEventListener AFTER_COMMIT
        // Para simplificar o MVP, fazemos inline - se falhar, o job de limpeza reprocessa
        if (previousObjectKey != null) {
            try {
                r2StorageService.deleteObject(previousObjectKey);
            } catch (Exception e) {
                // Log e continua - o job de limpeza reprocessa
                // Em uma implementação mais robusta, registrar em pending_r2_deletions
            }
        }

        return toResponseDTO(newImage);
    }

    /**
     * Remove a imagem de capa do evento.
     */
    @Transactional
    public void removeImage(UUID eventId, UUID organizerId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new BusinessException(
                        "Evento não encontrado",
                        HttpStatus.NOT_FOUND
                ));

        if (!event.getOrganizer().getId().equals(organizerId)) {
            throw new BusinessException(
                    "Somente o organizador pode gerenciar a imagem do evento",
                    HttpStatus.FORBIDDEN
                );
        }

        Optional<EventImage> existingImage = eventImageRepository.findByEventId(eventId);
        if (existingImage.isEmpty()) {
            throw new BusinessException(
                    "Evento não possui imagem de capa",
                    HttpStatus.NOT_FOUND
            );
        }

        String objectKey = existingImage.get().getObjectKey();
        eventImageRepository.deleteByEventId(eventId);

        // Deleta do R2 - se falhar, o job de limpeza reprocessa
        try {
            r2StorageService.deleteObject(objectKey);
        } catch (Exception e) {
            // Log e continua - o job de limpeza reprocessa
        }
    }

    /**
     * Busca a imagem de capa do evento (público ou organizador).
     */
    @Transactional(readOnly = true)
    public Optional<EventImageResponseDTO> getEventImage(UUID eventId, UUID requesterId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new BusinessException(
                        "Evento não encontrado",
                        HttpStatus.NOT_FOUND
                ));

        // Eventos não publicados só podem ser visualizados pelo próprio organizador
        if (event.getStatus() != EventStatus.PUBLISHED
                && !event.getOrganizer().getId().equals(requesterId)) {
            throw new BusinessException(
                    "Evento não encontrado",
                    HttpStatus.NOT_FOUND
            );
        }

        return eventImageRepository.findByEventId(eventId)
                .map(this::toResponseDTO);
    }

    /**
     * Busca a imagem para o organizador (sempre permite se for o dono).
     */
    @Transactional(readOnly = true)
    public Optional<EventImageResponseDTO> getEventImageForOrganizer(UUID eventId, UUID organizerId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new BusinessException(
                        "Evento não encontrado",
                        HttpStatus.NOT_FOUND
                ));

        if (!event.getOrganizer().getId().equals(organizerId)) {
            throw new BusinessException(
                    "Somente o organizador pode acessar esta informação",
                    HttpStatus.FORBIDDEN
            );
        }

        return eventImageRepository.findByEventId(eventId)
                .map(this::toResponseDTO);
    }

    private void validateContentType(String contentType) {
        if (contentType == null ||
                (!contentType.equals("image/webp") &&
                 !contentType.equals("image/jpeg") &&
                 !contentType.equals("image/png"))) {
            throw new BusinessException(
                    "Tipo de arquivo não suportado. Use WebP, JPEG ou PNG.",
                    HttpStatus.UNPROCESSABLE_CONTENT
            );
        }
    }

    private void validateSize(long sizeBytes) {
        if (sizeBytes <= 0 || sizeBytes > r2Properties.getMaxFileSizeBytes()) {
            throw new BusinessException(
                    "Tamanho de arquivo inválido. Limite máximo: " + r2Properties.getMaxFileSizeMb() + " MB.",
                    HttpStatus.UNPROCESSABLE_CONTENT
            );
        }
    }

    private String buildPublicUrl(String objectKey) {
        // Delega para o R2StorageService que tem acesso às properties
        // Será chamado pelo R2StorageService após validação
        return null; // Será preenchido pelo R2StorageService.generatePresignedUploadUrl
    }

    public String buildPublicUrlFromProperties(String objectKey, String publicBaseUrl) {
        String baseUrl = publicBaseUrl.replaceAll("/$", "");
        return baseUrl + "/" + objectKey;
    }

    private EventImageResponseDTO toResponseDTO(EventImage image) {
        return EventImageResponseDTO.builder()
                .id(image.getId())
                .eventId(image.getEvent().getId())
                .objectKey(image.getObjectKey())
                .publicUrl(image.getPublicUrl())
                .contentType(image.getContentType())
                .sizeBytes(image.getSizeBytes())
                .uploadedAt(image.getUploadedAt())
                .build();
    }
}