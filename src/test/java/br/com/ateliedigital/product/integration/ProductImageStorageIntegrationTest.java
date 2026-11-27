package br.com.ateliedigital.product.integration;

import br.com.ateliedigital.storage.config.StorageProperties;
import br.com.ateliedigital.storage.service.MinioObjectStorage;
import br.com.ateliedigital.storage.service.StorageService;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductImageStorageIntegrationTest {

    private static final String ACCESS_KEY = "test-access-key";
    private static final String SECRET_KEY = "test-secret-key-12345";
    private static final String BUCKET = "product-image-integration-tests";

    private static final GenericContainer<?> MINIO =
            new GenericContainer<>(
                    DockerImageName.parse("cgr.dev/chainguard/minio:latest")
            )
                    .withExposedPorts(9000)
                    .withEnv("MINIO_ROOT_USER", ACCESS_KEY)
                    .withEnv("MINIO_ROOT_PASSWORD", SECRET_KEY)
                    .withCommand("server", "/data", "--console-address", ":9001")
                    .waitingFor(
                            Wait.forHttp("/minio/health/ready")
                                    .forPort(9000)
                                    .forStatusCode(200)
                    );

    private static MinioClient minioClient;
    private static StorageService storageService;

    @BeforeAll
    static void setUp() throws Exception {
        MINIO.start();

        String endpoint = "http://" + MINIO.getHost()
                + ":" + MINIO.getMappedPort(9000);

        minioClient = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(ACCESS_KEY, SECRET_KEY)
                .build();

        StorageProperties properties = new StorageProperties(
                endpoint,
                endpoint,
                ACCESS_KEY,
                SECRET_KEY,
                BUCKET,
                DataSize.ofMegabytes(5)
        );

        storageService = new StorageService(
                new MinioObjectStorage(minioClient, properties),
                properties
        );

        if (!minioClient.bucketExists(
                BucketExistsArgs.builder().bucket(BUCKET).build()
        )) {
            minioClient.makeBucket(
                    MakeBucketArgs.builder().bucket(BUCKET).build()
            );
        }
    }

    @Test
    void shouldUploadAndDeleteProductVariationImage() {
        UUID productId = UUID.randomUUID();
        UUID variationId = UUID.randomUUID();

        byte[] pngContent = {
                (byte) 0x89, 0x50, 0x4E, 0x47,
                0x0D, 0x0A, 0x1A, 0x0A,
                0, 0, 0, 0
        };

        MockMultipartFile file = new MockMultipartFile(
                "images",
                "produto.png",
                "image/png",
                pngContent
        );

        String objectKey = storageService.upload(
                file,
                "products/" + productId
                        + "/variations/" + variationId
        );

        assertTrue(storageService.exists(objectKey));
        assertTrue(storageService.getPublicUrl(objectKey)
                .contains("/" + BUCKET + "/products/"));

        storageService.delete(objectKey);

        assertFalse(storageService.exists(objectKey));
    }
}
