package com.eventbook.backend.storage;

import com.eventbook.backend.config.R2Properties;
import com.eventbook.backend.common.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
@Transactional
public class R2StorageService {

    private static final Logger log = LoggerFactory.getLogger(R2StorageService.class);

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final R2Properties properties;

    public R2StorageService(S3Client s3Client, S3Presigner s3Presigner, R2Properties properties) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.properties = properties;
    }

    /**
     * Gera uma presigned URL para upload direto no R2.
     * A URL expira em 15 minutos.
     */
    public PresignedUploadResult generatePresignedUploadUrl(String contentType, String sha256Base64, String writeId) {
        String objectKey = buildObjectKey(contentType);
        String publicUrl = buildPublicUrl(objectKey);

        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(properties.getBucket())
                .key(objectKey)
                .contentType(contentType)
                .contentLength(properties.getMaxFileSizeBytes())
                .checksumSHA256(sha256Base64)
                .metadata(java.util.Map.of("write-id", writeId))
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(15))
                .putObjectRequest(putRequest)
                .build();

        PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(presignRequest);

        return new PresignedUploadResult(
                presignedRequest.url().toString(),
                objectKey,
                publicUrl,
                presignedRequest.url().toString(),
                Instant.now().plusSeconds(900)
        );
    }

    /**
     * Valida se o objeto existe no R2 e corresponde ao writeId e checksum.
     */
    public ValidationResult validateUploadedObject(String objectKey, String expectedWriteId, String expectedSha256Base64) {
        try {
            HeadObjectRequest headRequest = HeadObjectRequest.builder()
                    .bucket(properties.getBucket())
                    .key(objectKey)
                    .build();

            HeadObjectResponse response = s3Client.headObject(headRequest);

            String actualWriteId = response.metadata().get("write-id");
            String actualSha256 = response.metadata().get("x-amz-checksum-sha256");
            if (actualSha256 == null) {
                actualSha256 = response.metadata().get("checksum-sha256");
            }

            boolean writeIdMatches = expectedWriteId != null && expectedWriteId.equals(actualWriteId);
            boolean sha256Matches = expectedSha256Base64 != null && expectedSha256Base64.equals(actualSha256);

            long contentLength = response.contentLength();
            String contentType = response.contentType();

            if (!writeIdMatches) {
                log.warn("WriteId mismatch for object {}: expected {}, got {}", objectKey, expectedWriteId, actualWriteId);
            }
            if (!sha256Matches) {
                log.warn("SHA256 mismatch for object {}: expected {}, got {}", objectKey, expectedSha256Base64, actualSha256);
            }

            return new ValidationResult(
                    true,
                    contentLength,
                    contentType,
                    writeIdMatches,
                    sha256Matches
            );

        } catch (NoSuchKeyException e) {
            log.warn("Object not found in R2: {}", objectKey);
            return new ValidationResult(false, 0, null, false, false);
        } catch (Exception e) {
            log.error("Error validating object in R2: {}", objectKey, e);
            throw new BusinessException(
                    "Falha ao validar objeto no storage",
                    HttpStatus.BAD_GATEWAY
            );
        }
    }

    /**
     * Deleta um objeto do R2.
     */
    public void deleteObject(String objectKey) {
        try {
            DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                    .bucket(properties.getBucket())
                    .key(objectKey)
                    .build();

            s3Client.deleteObject(deleteRequest);
            log.info("Deleted object from R2: {}", objectKey);

        } catch (Exception e) {
            log.error("Error deleting object from R2: {}", objectKey, e);
            throw new BusinessException(
                    "Falha ao deletar objeto do storage",
                    HttpStatus.BAD_GATEWAY
            );
        }
    }

    /**
     * Verifica se um objeto existe no R2 (HEAD request).
     */
    public boolean objectExists(String objectKey) {
        try {
            HeadObjectRequest headRequest = HeadObjectRequest.builder()
                    .bucket(properties.getBucket())
                    .key(objectKey)
                    .build();

            s3Client.headObject(headRequest);
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (Exception e) {
            log.error("Error checking object existence in R2: {}", objectKey, e);
            return false;
        }
    }

    private String buildObjectKey(String contentType) {
        String extension = switch (contentType) {
            case "image/webp" -> ".webp";
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            default -> "";
        };
        return "events/" + UUID.randomUUID() + extension;
    }

    private String buildPublicUrl(String objectKey) {
        String baseUrl = properties.getPublicBaseUrl().replaceAll("/$", "");
        return baseUrl + "/" + objectKey;
    }

    /**
     * Constrói a URL pública a partir do objectKey e do publicBaseUrl configurado.
     */
    public String buildPublicUrlFromProperties(String objectKey) {
        return buildPublicUrl(objectKey);
    }

    public record PresignedUploadResult(
            String uploadUrl,
            String objectKey,
            String publicUrl,
            String signedUrl,
            Instant expiresAt
    ) {}

    public record ValidationResult(
            boolean exists,
            long contentLength,
            String contentType,
            boolean writeIdMatches,
            boolean sha256Matches
    ) {}
}