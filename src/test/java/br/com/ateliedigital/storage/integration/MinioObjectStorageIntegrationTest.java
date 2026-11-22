package br.com.ateliedigital.storage.integration;


import br.com.ateliedigital.exceptions.ResourceNotFoundException;
import br.com.ateliedigital.storage.config.StorageProperties;
import br.com.ateliedigital.storage.dto.StoredObject;
import br.com.ateliedigital.storage.service.MinioObjectStorage;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.springframework.util.unit.DataSize;

import java.io.ByteArrayInputStream;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class MinioObjectStorageIntegrationTest {
    private static final String ACCESS_KEY = "test-access-key";
    private static final String SECRET_KEY = "test-secret-key-12345";
    private static final String BUCKET = "storage-integration-tests";

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
    private static MinioObjectStorage objectStorage;

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

        objectStorage = new MinioObjectStorage(minioClient, properties);

        boolean bucketExists = minioClient.bucketExists(
                BucketExistsArgs.builder()
                        .bucket(BUCKET)
                        .build()
        );

        if (!bucketExists) {
            minioClient.makeBucket(
                    MakeBucketArgs.builder()
                            .bucket(BUCKET)
                            .build()
            );
        }
    }

    @Test
    void shouldUploadAndDownloadObject() throws Exception {
        String objectKey = "products/" + UUID.randomUUID() + ".png";
        byte[] content = "integration test image".getBytes();

        objectStorage.upload(
                objectKey,
                new ByteArrayInputStream(content),
                content.length,
                "image/png"
        );

        assertTrue(objectStorage.exists(objectKey));

        try (StoredObject storedObject = objectStorage.download(objectKey)) {
            assertArrayEquals(content, storedObject.inputStream().readAllBytes());
            assertEquals(content.length, storedObject.size());
            assertEquals("image/png", storedObject.contentType());
        }
    }

    @Test
    void shouldDeleteExistingObject() throws Exception {
        String objectKey = "products/" + UUID.randomUUID() + ".png";
        byte[] content = "image to delete".getBytes();

        objectStorage.upload(
                objectKey,
                new ByteArrayInputStream(content),
                content.length,
                "image/png"
        );

        assertTrue(objectStorage.exists(objectKey));

        objectStorage.delete(objectKey);

        assertFalse(objectStorage.exists(objectKey));
    }
    @Test
    void shouldDeleteNonExistingObjectWithoutThrowingException() {
        String objectKey = "products/" + UUID.randomUUID() + ".png";

        assertDoesNotThrow(() -> objectStorage.delete(objectKey));

        assertFalse(objectStorage.exists(objectKey));
    }

    @Test
    void shouldReturnFalseWhenObjectDoesNotExist() {
        String objectKey = "products/" + UUID.randomUUID() + ".png";

        assertFalse(objectStorage.exists(objectKey));
    }

    @Test
    void shouldThrowResourceNotFoundWhenDownloadingMissingObject() {
        String objectKey = "products/" + UUID.randomUUID() + ".png";

        assertThrows(
                ResourceNotFoundException.class,
                () -> objectStorage.download(objectKey)
        );
    }

    @Test
    void shouldGeneratePublicUrlForObject() {
        String objectKey = "products/example-image.png";

        String publicUrl = objectStorage.getPublicUrl(objectKey);

        assertEquals(
                "http://" + MINIO.getHost() + ":"
                        + MINIO.getMappedPort(9000)
                        + "/" + BUCKET + "/" + objectKey,
                publicUrl
        );
    }
}
