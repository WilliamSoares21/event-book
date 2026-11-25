package com.eventbook.backend.events;

import com.eventbook.backend.events.dto.EventImageConfirmRequestDTO;
import com.eventbook.backend.events.dto.EventImageResponseDTO;
import com.eventbook.backend.events.dto.EventImageUploadRequestDTO;
import com.eventbook.backend.storage.R2StorageService;
import com.eventbook.backend.users.Role;
import com.eventbook.backend.users.User;
import com.eventbook.backend.users.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class EventImageConfirmIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("eventbook")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("security.jwt.secret", () -> "9843575ce52ce3295ae06df9de4fd1f64f4c55243a02da9efc2c102c8555343e");
        registry.add("r2.endpoint", () -> "https://72b2db68128fb895dbbf2d1615003e7e.r2.cloudflarestorage.com");
        registry.add("r2.access-key", () -> "test-access-key");
        registry.add("r2.secret-key", () -> "test-secret-key");
        registry.add("r2.region", () -> "auto");
        registry.add("r2.bucket", () -> "test-bucket");
        registry.add("r2.public-base-url", () -> "https://images.nebula-labs.tech");
        registry.add("r2.max-file-size-mb", () -> "5");
    }

    private static final String PASSWORD = "password123";

    @Autowired
    private EventImageService eventImageService;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private EventImageRepository eventImageRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private R2StorageService r2StorageService;

    private User organizer;
    private Event event;

    @BeforeEach
    void setup() {
        when(r2StorageService.validateUploadedObject(anyString(), anyString(), anyString()))
                .thenReturn(new R2StorageService.ValidationResult(
                        true, 0, "image/webp", true, true
                ));
        eventImageRepository.deleteAll();
        eventRepository.deleteAll();
        userRepository.deleteAll();

        organizer = userRepository.save(User.builder()
                .name("Organizer")
                .email("organizer@test.com")
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .role(Role.ORGANIZADOR)
                .build());

        event = eventRepository.save(Event.builder()
                .organizer(organizer)
                .title("Evento com imagem")
                .description("Descrição")
                .startsAt(OffsetDateTime.parse("2030-01-10T19:00:00-03:00"))
                .endsAt(OffsetDateTime.parse("2030-01-10T23:00:00-03:00"))
                .location("Local")
                .number("2901")
                .complement("Bloco B")
                .city("Fortaleza")
                .state("CE")
                .zipCode("60000-000")
                .build());
    }

    @Test
    void confirmUploadDevePersistirEventImageComIdGerado() {
        // Arrange
        String objectKey = "events/" + UUID.randomUUID() + ".webp";
        String writeId = UUID.randomUUID().toString();
        String sha256Base64 = Base64.getEncoder().encodeToString("test-hash".getBytes());
        long sizeBytes = 1024L;

        when(r2StorageService.validateUploadedObject(anyString(), anyString(), anyString()))
                .thenReturn(new R2StorageService.ValidationResult(
                        true, sizeBytes, "image/webp", true, true
                ));

        // Act
        EventImageResponseDTO response = eventImageService.confirmUpload(
                event.getId(), organizer.getId(), objectKey, writeId, sha256Base64, sizeBytes
        );

        // Assert - Response DTO
        assertThat(response.getId()).isNotNull();
        assertThat(response.getEventId()).isEqualTo(event.getId());
        assertThat(response.getObjectKey()).isEqualTo(objectKey);
        assertThat(response.getContentType()).isEqualTo("image/webp");
        assertThat(response.getSizeBytes()).isEqualTo(sizeBytes);
        assertThat(response.getPublicUrl()).contains(objectKey);
        assertThat(response.getUploadedAt()).isNotNull();

        // Assert - Database persistence
        var persisted = eventImageRepository.findByEventId(event.getId());
        assertThat(persisted).isPresent();
        assertThat(persisted.get().getId()).isNotNull();
        assertThat(persisted.get().getId()).isEqualTo(response.getId());
        assertThat(persisted.get().getObjectKey()).isEqualTo(objectKey);
        assertThat(persisted.get().getEvent().getId()).isEqualTo(event.getId());
        assertThat(persisted.get().getUploadedAt()).isNotNull();
    }

    @Test
    void confirmUploadDeveSubstituirImagemExistente() {
        // Arrange - Create existing image
        String oldObjectKey = "events/old-image.webp";
        EventImage existingImage = EventImage.builder()
                .event(event)
                .objectKey(oldObjectKey)
                .publicUrl("https://images.nebula-labs.tech/" + oldObjectKey)
                .contentType("image/webp")
                .sizeBytes(512L)
                .uploadedAt(OffsetDateTime.now().minusDays(1))
                .build();
        eventImageRepository.save(existingImage);
        UUID oldImageId = existingImage.getId();

        // New image data
        String newObjectKey = "events/" + UUID.randomUUID() + ".webp";
        String writeId = UUID.randomUUID().toString();
        String sha256Base64 = Base64.getEncoder().encodeToString("test-hash".getBytes());
        long sizeBytes = 2048L;

        when(r2StorageService.validateUploadedObject(anyString(), anyString(), anyString()))
                .thenReturn(new R2StorageService.ValidationResult(
                        true, sizeBytes, "image/webp", true, true
                ));

        // Act
        EventImageResponseDTO response = eventImageService.confirmUpload(
                event.getId(), organizer.getId(), newObjectKey, writeId, sha256Base64, sizeBytes
        );

        // Assert - New image persisted with new ID
        assertThat(response.getId()).isNotNull();
        assertThat(response.getId()).isNotEqualTo(oldImageId);
        assertThat(response.getObjectKey()).isEqualTo(newObjectKey);
        assertThat(response.getSizeBytes()).isEqualTo(sizeBytes);

        // Assert - Only one image exists for the event
        var allImages = eventImageRepository.findAll();
        assertThat(allImages).hasSize(1);
        assertThat(allImages.get(0).getId()).isEqualTo(response.getId());
        assertThat(allImages.get(0).getObjectKey()).isEqualTo(newObjectKey);
    }

    @Test
    void getEventImageDeveRetornarImagemPersistida() {
        // Arrange - Create and persist image via confirm flow
        String objectKey = "events/" + UUID.randomUUID() + ".webp";
        String writeId = UUID.randomUUID().toString();
        String sha256Base64 = Base64.getEncoder().encodeToString("test-hash".getBytes());
        long sizeBytes = 1024L;

        when(r2StorageService.validateUploadedObject(anyString(), anyString(), anyString()))
                .thenReturn(new R2StorageService.ValidationResult(
                        true, sizeBytes, "image/webp", true, true
                ));

        EventImageResponseDTO confirmed = eventImageService.confirmUpload(
                event.getId(), organizer.getId(), objectKey, writeId, sha256Base64, sizeBytes
        );

        // Act - Use organizer ID since event is DRAFT
        var result = eventImageService.getEventImage(event.getId(), organizer.getId());

        // Assert
        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(confirmed.getId());
        assertThat(result.get().getObjectKey()).isEqualTo(objectKey);
    }

    @Test
    void removeImageDeveDeletarDoBanco() {
        // Arrange - Create image
        String objectKey = "events/" + UUID.randomUUID() + ".webp";
        String writeId = UUID.randomUUID().toString();
        String sha256Base64 = Base64.getEncoder().encodeToString("test-hash".getBytes());
        long sizeBytes = 1024L;

        when(r2StorageService.validateUploadedObject(anyString(), anyString(), anyString()))
                .thenReturn(new R2StorageService.ValidationResult(
                        true, sizeBytes, "image/webp", true, true
                ));

        EventImageResponseDTO confirmed = eventImageService.confirmUpload(
                event.getId(), organizer.getId(), objectKey, writeId, sha256Base64, sizeBytes
        );

        // Act
        eventImageService.removeImage(event.getId(), organizer.getId());

        // Assert
        var result = eventImageRepository.findByEventId(event.getId());
        assertThat(result).isEmpty();
    }
}