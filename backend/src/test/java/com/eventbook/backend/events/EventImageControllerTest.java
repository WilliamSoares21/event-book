package com.eventbook.backend.events;

import com.eventbook.backend.common.exception.ApiExceptionHandler;
import com.eventbook.backend.common.exception.BusinessException;
import com.eventbook.backend.events.dto.EventImageConfirmRequestDTO;
import com.eventbook.backend.events.dto.EventImageResponseDTO;
import com.eventbook.backend.events.dto.EventImageUploadRequestDTO;
import com.eventbook.backend.security.AuthenticatedUser;
import com.eventbook.backend.storage.R2StorageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class EventImageControllerTest {

    @Mock
    private EventImageService eventImageService;

    @InjectMocks
    private EventImageController eventImageController;

    private MockMvc mockMvc;

    private UUID organizerId;
    private UUID eventId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(eventImageController)
                .setControllerAdvice(new ApiExceptionHandler())
                .setCustomArgumentResolvers(
                        new AuthenticationPrincipalArgumentResolver()
                )
                .build();

        organizerId = UUID.randomUUID();
        eventId = UUID.randomUUID();

        AuthenticatedUser authenticatedUser = new AuthenticatedUser(
                organizerId,
                "organizer@email.com",
                "Organizer",
                null,
                java.util.List.of(
                        new SimpleGrantedAuthority("ROLE_ORGANIZADOR")
                ),
                0
        );

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        authenticatedUser,
                        null,
                        authenticatedUser.getAuthorities()
                )
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void deveGerarUploadUrlComSucesso() throws Exception {
        var uploadResult = new R2StorageService.PresignedUploadResult(
                "https://presigned-url.com",
                "events/uuid.webp",
                "https://images.eventbook.app/events/uuid.webp",
                "https://presigned-url.com",
                Instant.now().plusSeconds(900)
        );

        when(eventImageService.generateUploadUrl(
                eq(eventId),
                eq(organizerId),
                eq("image/webp"),
                eq("sha256-base64"),
                eq("write-id-123"),
                eq(3564530L)
        )).thenReturn(uploadResult);

        mockMvc.perform(post("/api/events/{eventId}/images/upload-url", eventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "contentType": "image/webp",
                                  "sha256Base64": "sha256-base64",
                                  "writeId": "write-id-123",
                                  "sizeBytes": 3564530
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploadUrl").value("https://presigned-url.com"))
                .andExpect(jsonPath("$.objectKey").value("events/uuid.webp"))
                .andExpect(jsonPath("$.publicUrl").value("https://images.eventbook.app/events/uuid.webp"));

        verify(eventImageService).generateUploadUrl(
                eq(eventId),
                eq(organizerId),
                eq("image/webp"),
                eq("sha256-base64"),
                eq("write-id-123"),
                eq(3564530L)
        );
    }

    @Test
    void deveRejeitarUploadUrlPayloadInvalido() throws Exception {
        mockMvc.perform(post("/api/events/{eventId}/images/upload-url", eventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "contentType": "image/gif",
                                  "sha256Base64": "",
                                  "writeId": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields").exists());
    }

    @Test
    void deveRejeitarUploadUrlContentTypeInvalido() throws Exception {
        // A validação ocorre no DTO via @Pattern, retornando 400
        mockMvc.perform(post("/api/events/{eventId}/images/upload-url", eventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "contentType": "image/gif",
                                  "sha256Base64": "sha256-base64",
                                  "writeId": "write-id-123",
                                  "sizeBytes": 3564530
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields").exists());
    }

    @Test
    void deveConfirmarUploadComSucesso() throws Exception {
        EventImageResponseDTO response = EventImageResponseDTO.builder()
                .id(UUID.randomUUID())
                .eventId(eventId)
                .objectKey("events/uuid.webp")
                .publicUrl("https://images.eventbook.app/events/uuid.webp")
                .contentType("image/webp")
                .sizeBytes(1024L)
                .uploadedAt(OffsetDateTime.now())
                .build();

        when(eventImageService.confirmUpload(
                eq(eventId),
                eq(organizerId),
                eq("events/uuid.webp"),
                eq("write-id-123"),
                eq("sha256-base64"),
                eq(3564530L)
        )).thenReturn(response);

        mockMvc.perform(post("/api/events/{eventId}/images/confirm", eventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "objectKey": "events/uuid.webp",
                                  "writeId": "write-id-123",
                                  "sha256Base64": "sha256-base64",
                                  "sizeBytes": 3564530
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objectKey").value("events/uuid.webp"))
                .andExpect(jsonPath("$.publicUrl").value("https://images.eventbook.app/events/uuid.webp"))
                .andExpect(jsonPath("$.contentType").value("image/webp"))
                .andExpect(jsonPath("$.sizeBytes").value(1024));

        verify(eventImageService).confirmUpload(
                eq(eventId),
                eq(organizerId),
                eq("events/uuid.webp"),
                eq("write-id-123"),
                eq("sha256-base64"),
                eq(3564530L)
        );
    }

    @Test
    void deveRejeitarConfirmacaoPayloadInvalido() throws Exception {
        mockMvc.perform(post("/api/events/{eventId}/images/confirm", eventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "objectKey": "",
                                  "writeId": "",
                                  "sha256Base64": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields").exists());
    }

    @Test
    void deveRejeitarConfirmacaoObjetoNaoEncontrado() throws Exception {
        doThrow(new BusinessException(
                "Objeto não encontrado no storage. Upload pode ter falhado.",
                HttpStatus.BAD_REQUEST
        )).when(eventImageService).confirmUpload(any(), any(), any(), any(), any(), anyLong());

        mockMvc.perform(post("/api/events/{eventId}/images/confirm", eventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "objectKey": "events/uuid.webp",
                                  "writeId": "write-id-123",
                                  "sha256Base64": "sha256-base64",
                                  "sizeBytes": 3564530
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Objeto não encontrado no storage. Upload pode ter falhado."));
    }

    @Test
    void deveRemoverImagemComSucesso() throws Exception {
        mockMvc.perform(delete("/api/events/{eventId}/images", eventId))
                .andExpect(status().isNoContent());

        verify(eventImageService).removeImage(eq(eventId), eq(organizerId));
    }

    @Test
    void deveRejeitarRemocaoSeEventoNaoTemImagem() throws Exception {
        doThrow(new BusinessException(
                "Evento não possui imagem de capa",
                HttpStatus.NOT_FOUND
        )).when(eventImageService).removeImage(any(), any());

        mockMvc.perform(delete("/api/events/{eventId}/images", eventId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Evento não possui imagem de capa"));
    }

    @Test
    void deveConsultarImagemDeEventoPublicado() throws Exception {
        EventImageResponseDTO response = EventImageResponseDTO.builder()
                .id(UUID.randomUUID())
                .eventId(eventId)
                .objectKey("events/uuid.webp")
                .publicUrl("https://images.eventbook.app/events/uuid.webp")
                .contentType("image/webp")
                .sizeBytes(1024L)
                .uploadedAt(OffsetDateTime.now())
                .build();

        lenient().when(eventImageService.getEventImage(eq(eventId), any()))
                .thenReturn(java.util.Optional.of(response));

        mockMvc.perform(get("/api/events/{eventId}/images", eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objectKey").value("events/uuid.webp"))
                .andExpect(jsonPath("$.publicUrl").value("https://images.eventbook.app/events/uuid.webp"));

        verify(eventImageService).getEventImage(eq(eventId), any());
    }

    @Test
    void deveRetornar404SeEventoNaoTemImagem() throws Exception {
        lenient().when(eventImageService.getEventImage(eq(eventId), any()))
                .thenReturn(java.util.Optional.empty());

        mockMvc.perform(get("/api/events/{eventId}/images", eventId))
                .andExpect(status().isNotFound());
    }

    @Test
    void deveRejeitarAcessoADraftDeTerceiro() throws Exception {
        lenient().doThrow(new BusinessException(
                "Evento não encontrado",
                HttpStatus.NOT_FOUND
        )).when(eventImageService).getEventImage(any(), any());

        mockMvc.perform(get("/api/events/{eventId}/images", eventId))
                .andExpect(status().isNotFound());
    }
}