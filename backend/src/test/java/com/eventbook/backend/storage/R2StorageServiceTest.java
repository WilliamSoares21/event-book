package com.eventbook.backend.storage;

import com.eventbook.backend.config.R2Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class R2StorageServiceTest {

    @Mock
    private S3Client s3Client;

    @Mock
    private S3Presigner s3Presigner;

    @Mock
    private R2Properties properties;

    private R2StorageService service;

    @BeforeEach
    void setUp() {
        lenient().when(properties.getBucket()).thenReturn("eventbook-images");
        lenient().when(properties.getPublicBaseUrl()).thenReturn("https://images.example.com");
        service = new R2StorageService(s3Client, s3Presigner, properties);
    }

    @Test
    void presignedRequestDeveAssinarOTamanhoRecebidoENaoOLimiteMaximo() throws Exception {
        PresignedPutObjectRequest presigned = mock(PresignedPutObjectRequest.class);
        when(presigned.url()).thenReturn(URI.create("https://bucket.example.com/key?X-Amz-Signature=deadbeef").toURL());
        when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(presigned);

        service.generatePresignedUploadUrl("image/webp", "c2hhMjU2", "write-id-1", 3564530L);

        ArgumentCaptor<PutObjectPresignRequest> captor = ArgumentCaptor.forClass(PutObjectPresignRequest.class);
        verify(s3Presigner).presignPutObject(captor.capture());
        PutObjectRequest putObjectRequest = captor.getValue().putObjectRequest();

        assertThat(putObjectRequest.contentLength()).isEqualTo(3564530L);
        assertThat(putObjectRequest.contentLength()).isNotEqualTo(5L * 1024 * 1024);
        assertThat(putObjectRequest.contentType()).isEqualTo("image/webp");
        assertThat(putObjectRequest.checksumSHA256()).isEqualTo("c2hhMjU2");
        assertThat(putObjectRequest.metadata()).containsEntry("write-id", "write-id-1");
    }

    @Test
    void contentLengthDeveEntrarNaAssinaturaComOValorInformado() {
        R2StorageService realPresignerService = new R2StorageService(
                mock(S3Client.class),
                S3Presigner.builder()
                        .endpointOverride(URI.create("https://fake-account.r2.cloudflarestorage.com"))
                        .region(Region.of("auto"))
                        .credentialsProvider(StaticCredentialsProvider.create(
                                AwsBasicCredentials.create("FAKEACCESSKEYID", "FAKESECRETACCESSKEY")))
                        .build(),
                properties);

        String url = realPresignerService
                .generatePresignedUploadUrl("image/webp", "c2hhMjU2", "write-id-1", 3564530L)
                .uploadUrl();
        String urlMenor = realPresignerService
                .generatePresignedUploadUrl("image/webp", "c2hhMjU2", "write-id-1", 1024L)
                .uploadUrl();

        assertThat(signedHeaders(url)).contains("content-length");
        assertThat(signature(url)).isNotEqualTo(signature(urlMenor));
    }

    @Test
    void deveLerChecksumDoCampoProprioDoHeadObject() {
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(HeadObjectResponse.builder()
                .contentLength(3564530L)
                .contentType("image/webp")
                .metadata(Map.of("write-id", "write-id-1"))
                .checksumSHA256("c2hhMjU2")
                .build());

        R2StorageService.ValidationResult result =
                service.validateUploadedObject("events/uuid.webp", "write-id-1", "c2hhMjU2");

        assertThat(result.sha256Matches()).isTrue();
        assertThat(result.writeIdMatches()).isTrue();
        assertThat(result.contentLength()).isEqualTo(3564530L);
    }

    @Test
    void deveRejeitarQuandoR2NaoRetornaChecksum() {
        when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(HeadObjectResponse.builder()
                .contentLength(3564530L)
                .contentType("image/webp")
                .metadata(Map.of("write-id", "write-id-1"))
                .build());

        R2StorageService.ValidationResult result =
                service.validateUploadedObject("events/uuid.webp", "write-id-1", "c2hhMjU2");

        assertThat(result.sha256Matches()).isFalse();
    }

    private static String signedHeaders(String presignedUrl) {
        return URLDecoder.decode(presignedUrl, StandardCharsets.UTF_8)
                .replaceAll(".*X-Amz-SignedHeaders=([^&]*).*", "$1");
    }

    private static String signature(String presignedUrl) {
        return presignedUrl.replaceAll(".*X-Amz-Signature=([0-9a-f]+).*", "$1");
    }
}
