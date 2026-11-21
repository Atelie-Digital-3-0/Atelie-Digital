
package br.com.ateliedigital.storage.service;

import br.com.ateliedigital.exceptions.ResourceNotFoundException;
import br.com.ateliedigital.exceptions.VioletedRule;
import br.com.ateliedigital.storage.config.StorageProperties;
import br.com.ateliedigital.storage.dto.StoredObject;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.ErrorResponseException;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import java.util.stream.Collectors;
import br.com.ateliedigital.exceptions.StorageException;

@Service
public class MinioObjectStorage implements ObjectStorage {

    private final MinioClient minioClient;
    private final StorageProperties properties;

    public MinioObjectStorage(
            MinioClient minioClient,
            StorageProperties properties
    ) {
        this.minioClient = minioClient;
        this.properties = properties;
    }

    @Override
    public void upload(
            String objectKey,
            InputStream inputStream,
            long size,
            String contentType
    ) {
        validateObjectKey(objectKey);

        if (inputStream == null) {
            throw new VioletedRule("File input stream cannot be null");
        }

        if (size < 0) {
            throw new VioletedRule("File size cannot be negative");
        }

        if (contentType == null || contentType.isBlank()) {
            throw new VioletedRule("Content type cannot be blank");
        }

        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(properties.bucket())
                            .object(objectKey)
                            .stream(inputStream, size, -1L)
                            .contentType(contentType)
                            .build()
            );
        } catch (Exception exception) {
            throw new StorageException(
                    "Failed to upload object: " + objectKey,
                    exception
            );
        }
    }

    @Override
    public StoredObject download(String objectKey) {
        validateObjectKey(objectKey);

        try {
            StatObjectResponse metadata = minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(properties.bucket())
                            .object(objectKey)
                            .build()
            );

            InputStream inputStream = minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(properties.bucket())
                            .object(objectKey)
                            .build()
            );

            String contentType = metadata.contentType();

            return new StoredObject(
                    inputStream,
                    metadata.size(),
                    contentType != null
                            ? contentType
                            : "application/octet-stream"
            );

        } catch (ErrorResponseException exception) {
            if (isObjectNotFound(exception)) {
                throw new ResourceNotFoundException(
                        "Storage object not found: " + objectKey
                );
            }

            throw new StorageException(
                    "Failed to download object: " + objectKey,
                    exception
            );

        } catch (Exception exception) {
            throw new StorageException(
                    "Failed to download object: " + objectKey,
                    exception
            );
        }
    }

    @Override
    public void delete(String objectKey) {
        validateObjectKey(objectKey);

        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(properties.bucket())
                            .object(objectKey)
                            .build()
            );
        } catch (Exception exception) {
            throw new StorageException(
                    "Failed to delete object: " + objectKey,
                    exception
            );
        }
    }

    @Override
    public boolean exists(String objectKey) {
        validateObjectKey(objectKey);

        try {
            minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(properties.bucket())
                            .object(objectKey)
                            .build()
            );

            return true;

        } catch (ErrorResponseException exception) {
            if (isObjectNotFound(exception)) {
                return false;
            }

            throw new StorageException(
                    "Failed to check object existence: " + objectKey,
                    exception
            );

        } catch (Exception exception) {
            throw new StorageException(
                    "Failed to check object existence: " + objectKey,
                    exception
            );
        }
    }


    @Override
    public String getPublicUrl(String objectKey) {
        validateObjectKey(objectKey);

        String baseUrl = properties.publicUrl().replaceAll("/+$", "");

        String encodedKey = Arrays.stream(objectKey.split("/"))
                .map(segment -> URLEncoder.encode(
                        segment,
                        StandardCharsets.UTF_8
                ).replace("+", "%20"))
                .collect(Collectors.joining("/"));

        return baseUrl + "/" + properties.bucket() + "/" + encodedKey;
    }

    private void validateObjectKey(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            throw new VioletedRule("Object key cannot be blank");
        }

        boolean invalidKey = objectKey.startsWith("/")
                || objectKey.contains("\\")
                || Arrays.stream(objectKey.split("/", -1))
                .anyMatch(segment ->
                        segment.isBlank()
                                || segment.equals(".")
                                || segment.equals("..")
                );

        if (invalidKey) {
            throw new VioletedRule("Invalid object key");
        }
    }

    private boolean isObjectNotFound(ErrorResponseException exception) {
        String errorCode = exception.errorResponse().code();

        return "NoSuchKey".equals(errorCode)
                || "NoSuchObject".equals(errorCode);
    }
}
