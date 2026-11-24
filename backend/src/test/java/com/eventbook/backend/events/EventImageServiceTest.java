package com.eventbook.backend.events;

import com.eventbook.backend.common.exception.BusinessException;
import com.eventbook.backend.config.R2Properties;
import com.eventbook.backend.events.dto.EventImageConfirmRequestDTO;
import com.eventbook.backend.events.dto.EventImageResponseDTO;
import com.eventbook.backend.events.dto.EventImageUploadRequestDTO;
import com.eventbook.backend.storage.R2StorageService;
import com.eventbook.backend.users.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventImageServiceTest {

    @Mock
    private EventRepository eventRepository;

    @Mock
    private EventImageRepository eventImageRepository;

    @Mock
    private R2StorageService r2StorageService;

    @Mock
    private R2Properties r2Properties;

    @InjectMocks
    private EventImageService eventImageService;

    private UUID organizerId;
    private UUID eventId;
    private Event event;

    @BeforeEach
    void setUp() {
        organizerId = UUID.randomUUID();
        eventId = UUID.randomUUID();
        event = Event.builder()
                .id(eventId)
                .organizer(User.builder().id(organizerId).build())
                .title("Festival")
                .description("Descrição")
                .startsAt(OffsetDateTime.now().plusDays(10))
                .endsAt(OffsetDateTime.now().plusDays(10).plusHours(2))
                .location("São Paulo")
                .status(EventStatus.DRAFT)
                .build();

        lenient().when(r2Properties.getPublicBaseUrl()).thenReturn("https://images.eventbook.app");
    }

    @Test
    void deveGerarUploadUrlComSucesso() {
        lenient().when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        lenient().when(r2StorageService.generatePresignedUploadUrl(anyString(), anyString(), anyString()))
                .thenReturn(new R2StorageService.PresignedUploadResult(
                        "https://presigned-url.com",
                        "events/uuid.webp",
                        "https://images.eventbook.app/events/uuid.webp",
                        "https://presigned-url.com",
                        java.time.Instant.now().plusSeconds(900)
                ));

        EventImageUploadRequestDTO request = EventImageUploadRequestDTO.builder()
                .contentType("image/webp")
                .sha256Base64("sha256-base64")
                .writeId(UUID.randomUUID().toString())
                .build();

        var result = eventImageService.generateUploadUrl(eventId, organizerId,
                request.getContentType(), request.getSha256Base64(), request.getWriteId());

        assertThat(result.uploadUrl()).isEqualTo("https://presigned-url.com");
        assertThat(result.objectKey()).isEqualTo("events/uuid.webp");
        assertThat(result.publicUrl()).isEqualTo("https://images.eventbook.app/events/uuid.webp");
        verify(eventRepository).findById(eventId);
        verify(r2StorageService).generatePresignedUploadUrl(anyString(), anyString(), anyString());
    }

    @Test
    void deveRejeitarGerarUploadUrlParaEventoInexistente() {
        lenient().when(eventRepository.findById(eventId)).thenReturn(Optional.empty());
        lenient().when(r2StorageService.generatePresignedUploadUrl(anyString(), anyString(), anyString()))
                .thenReturn(new R2StorageService.PresignedUploadResult(
                        "https://presigned-url.com",
                        "events/uuid.webp",
                        "https://images.eventbook.app/events/uuid.webp",
                        "https://presigned-url.com",
                        java.time.Instant.now().plusSeconds(900)
                ));

        assertThatThrownBy(() ->
                eventImageService.generateUploadUrl(eventId, organizerId, "image/webp", "sha256", "writeId")
        ).isInstanceOf(BusinessException.class)
         .extracting("status").isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void deveRejeitarGerarUploadUrlParaOutroOrganizador() {
        UUID otherOrganizerId = UUID.randomUUID();
        lenient().when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        lenient().when(r2StorageService.generatePresignedUploadUrl(anyString(), anyString(), anyString()))
                .thenReturn(new R2StorageService.PresignedUploadResult(
                        "https://presigned-url.com",
                        "events/uuid.webp",
                        "https://images.eventbook.app/events/uuid.webp",
                        "https://presigned-url.com",
                        java.time.Instant.now().plusSeconds(900)
                ));

        assertThatThrownBy(() ->
                eventImageService.generateUploadUrl(eventId, otherOrganizerId, "image/webp", "sha256", "writeId")
        ).isInstanceOf(BusinessException.class)
         .extracting("status").isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void deveRejeitarContentTypeInvalidoNoUploadUrl() {
        lenient().when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        lenient().when(r2StorageService.generatePresignedUploadUrl(anyString(), anyString(), anyString()))
                .thenReturn(new R2StorageService.PresignedUploadResult(
                        "https://presigned-url.com",
                        "events/uuid.webp",
                        "https://images.eventbook.app/events/uuid.webp",
                        "https://presigned-url.com",
                        java.time.Instant.now().plusSeconds(900)
                ));

        assertThatThrownBy(() ->
                eventImageService.generateUploadUrl(eventId, organizerId, "image/gif", "sha256", "writeId")
        ).isInstanceOf(BusinessException.class)
         .extracting("status").isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    }

    @Test
    void deveConfirmarUploadComSucesso() {
        String objectKey = "events/uuid.webp";
        String writeId = UUID.randomUUID().toString();
        String sha256Base64 = "sha256-base64";

        lenient().when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        lenient().when(r2StorageService.validateUploadedObject(objectKey, writeId, sha256Base64))
                .thenReturn(new R2StorageService.ValidationResult(
                        true, 1024L, "image/webp", true, true
                ));
        lenient().when(eventImageRepository.findByEventId(eventId)).thenReturn(Optional.empty());
        lenient().when(eventImageRepository.save(any(EventImage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        EventImageConfirmRequestDTO request = EventImageConfirmRequestDTO.builder()
                .objectKey(objectKey)
                .writeId(writeId)
                .sha256Base64(sha256Base64)
                .build();

        EventImageResponseDTO response = eventImageService.confirmUpload(eventId, organizerId,
                objectKey, writeId, sha256Base64);

        assertThat(response.getObjectKey()).isEqualTo(objectKey);
        assertThat(response.getContentType()).isEqualTo("image/webp");
        assertThat(response.getSizeBytes()).isEqualTo(1024L);
        assertThat(response.getPublicUrl()).isEqualTo("https://images.eventbook.app/events/uuid.webp");
        verify(eventImageRepository).save(any(EventImage.class));
    }

    @Test
    void deveSubstituirImagemExistente() {
        String objectKey = "events/uuid-new.webp";
        String previousObjectKey = "events/uuid-old.webp";
        String writeId = UUID.randomUUID().toString();
        String sha256Base64 = "sha256-base64";

        EventImage existingImage = EventImage.builder()
                .id(UUID.randomUUID())
                .event(event)
                .objectKey(previousObjectKey)
                .publicUrl("https://images.eventbook.app/" + previousObjectKey)
                .contentType("image/webp")
                .sizeBytes(512L)
                .uploadedAt(OffsetDateTime.now().minusDays(1))
                .build();

        lenient().when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        lenient().when(r2StorageService.validateUploadedObject(objectKey, writeId, sha256Base64))
                .thenReturn(new R2StorageService.ValidationResult(
                        true, 1024L, "image/webp", true, true
                ));
        lenient().when(eventImageRepository.findByEventId(eventId)).thenReturn(Optional.of(existingImage));
        lenient().when(eventImageRepository.save(any(EventImage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        EventImageConfirmRequestDTO request = EventImageConfirmRequestDTO.builder()
                .objectKey(objectKey)
                .writeId(writeId)
                .sha256Base64(sha256Base64)
                .build();

        EventImageResponseDTO response = eventImageService.confirmUpload(eventId, organizerId,
                objectKey, writeId, sha256Base64);

        assertThat(response.getObjectKey()).isEqualTo(objectKey);
        verify(eventImageRepository).deleteByEventId(eventId);
        verify(eventImageRepository).save(any(EventImage.class));
        verify(r2StorageService).deleteObject(previousObjectKey);
    }

    @Test
    void deveRejeitarConfirmacaoSeObjetoNaoExiste() {
        lenient().when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        lenient().when(r2StorageService.validateUploadedObject(anyString(), anyString(), anyString()))
                .thenReturn(new R2StorageService.ValidationResult(
                        false, 0, null, false, false
                ));

        assertThatThrownBy(() ->
                eventImageService.confirmUpload(eventId, organizerId, "events/uuid.webp", "writeId", "sha256")
        ).isInstanceOf(BusinessException.class)
          .extracting("status").isEqualTo(HttpStatus.BAD_REQUEST);

        verify(eventImageRepository, never()).save(any(EventImage.class));
    }

    @Test
    void deveRejeitarConfirmacaoSeWriteIdNaoConfere() {
        lenient().when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        lenient().when(r2StorageService.validateUploadedObject(anyString(), anyString(), anyString()))
                .thenReturn(new R2StorageService.ValidationResult(
                        true, 1024L, "image/webp", false, true
                ));

        assertThatThrownBy(() ->
                eventImageService.confirmUpload(eventId, organizerId, "events/uuid.webp", "writeId", "sha256")
        ).isInstanceOf(BusinessException.class)
          .extracting("status").isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void deveRejeitarConfirmacaoSeChecksumNaoConfere() {
        lenient().when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        lenient().when(r2StorageService.validateUploadedObject(anyString(), anyString(), anyString()))
                .thenReturn(new R2StorageService.ValidationResult(
                        true, 1024L, "image/webp", true, false
                ));

        assertThatThrownBy(() ->
                eventImageService.confirmUpload(eventId, organizerId, "events/uuid.webp", "writeId", "sha256")
        ).isInstanceOf(BusinessException.class)
          .extracting("status").isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void deveRemoverImagemComSucesso() {
        String objectKey = "events/uuid.webp";
        EventImage existingImage = EventImage.builder()
                .id(UUID.randomUUID())
                .event(event)
                .objectKey(objectKey)
                .publicUrl("https://images.eventbook.app/" + objectKey)
                .contentType("image/webp")
                .sizeBytes(512L)
                .uploadedAt(OffsetDateTime.now().minusDays(1))
                .build();

        lenient().when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        lenient().when(eventImageRepository.findByEventId(eventId)).thenReturn(Optional.of(existingImage));

        eventImageService.removeImage(eventId, organizerId);

        verify(eventImageRepository).deleteByEventId(eventId);
        verify(r2StorageService).deleteObject(objectKey);
    }

    @Test
    void deveRejeitarRemocaoSeEventoNaoTemImagem() {
        lenient().when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        lenient().when(eventImageRepository.findByEventId(eventId)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                eventImageService.removeImage(eventId, organizerId)
        ).isInstanceOf(BusinessException.class)
          .extracting("status").isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void deveRetornarImagemParaEventoPublicado() {
        String objectKey = "events/uuid.webp";
        EventImage existingImage = EventImage.builder()
                .id(UUID.randomUUID())
                .event(event)
                .objectKey(objectKey)
                .publicUrl("https://images.eventbook.app/" + objectKey)
                .contentType("image/webp")
                .sizeBytes(512L)
                .uploadedAt(OffsetDateTime.now().minusDays(1))
                .build();

        event.setStatus(EventStatus.PUBLISHED);
        lenient().when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        lenient().when(eventImageRepository.findByEventId(eventId)).thenReturn(Optional.of(existingImage));

        Optional<EventImageResponseDTO> result = eventImageService.getEventImage(eventId, UUID.randomUUID());

        assertThat(result).isPresent();
        assertThat(result.get().getObjectKey()).isEqualTo(objectKey);
    }

    @Test
    void deveRetornarVazioParaEventoSemImagem() {
        event.setStatus(EventStatus.PUBLISHED);
        lenient().when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        lenient().when(eventImageRepository.findByEventId(eventId)).thenReturn(Optional.empty());

        Optional<EventImageResponseDTO> result = eventImageService.getEventImage(eventId, UUID.randomUUID());

        assertThat(result).isEmpty();
    }

    @Test
    void deveRejeitarAcessoADraftDeTerceiro() {
        event.setStatus(EventStatus.DRAFT);
        lenient().when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));

        assertThatThrownBy(() ->
                eventImageService.getEventImage(eventId, UUID.randomUUID())
        ).isInstanceOf(BusinessException.class)
          .extracting("status").isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void devePermitirAcessoADraftDoProprietario() {
        event.setStatus(EventStatus.DRAFT);
        lenient().when(eventRepository.findById(eventId)).thenReturn(Optional.of(event));
        lenient().when(eventImageRepository.findByEventId(eventId)).thenReturn(Optional.empty());

        Optional<EventImageResponseDTO> result = eventImageService.getEventImage(eventId, organizerId);

        assertThat(result).isEmpty();
    }
}