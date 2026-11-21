package br.com.ateliedigital.storage.service;

import br.com.ateliedigital.exceptions.VioletedRule;
import br.com.ateliedigital.storage.dto.StoredObject;
import br.com.ateliedigital.storage.config.StorageProperties;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class StorageService {

    private static final Map<String, String> ALLOWED_CONTENT_TYPES = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp"
    );

    private final ObjectStorage objectStorage;
    private final StorageProperties properties;

    public StorageService(
            ObjectStorage objectStorage,
            StorageProperties properties
    ) {
        this.objectStorage = objectStorage;
        this.properties = properties;
    }

    /**
     * Validates and uploads an image to the configured storage.
     *
     * @param file   image received from the client
     * @param prefix logical storage prefix (e.g. stores/123/logo)
     * @return generated object key
     */
    public String upload(MultipartFile file, String prefix) {
        validateFile(file);

        String contentType = file.getContentType()
                .toLowerCase(Locale.ROOT)
                .split(";")[0]
                .trim();

        String extension = ALLOWED_CONTENT_TYPES.get(contentType);

        if (extension == null) {
            throw new VioletedRule(
                    "Unsupported file type. Allowed types: JPEG, PNG and WebP"
            );
        }

        validatePrefix(prefix);

        String objectKey = prefix + "/" + UUID.randomUUID() + extension;

        try (InputStream inputStream = file.getInputStream()) {

            objectStorage.upload(
                    objectKey,
                    inputStream,
                    file.getSize(),
                    contentType
            );

            return objectKey;

        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Failed to read uploaded file",
                    exception
            );
        }
    }

    /**
     * Downloads an object from storage.
     */
    public StoredObject download(String objectKey) {
        return objectStorage.download(objectKey);
    }

    /**
     * Deletes an object from storage.
     */
    public void delete(String objectKey) {
        objectStorage.delete(objectKey);
    }

    /**
     * Checks whether an object exists.
     */
    public boolean exists(String objectKey) {
        return objectStorage.exists(objectKey);
    }

    /**
     * Returns the public URL of an object.
     */
    public String getPublicUrl(String objectKey) {
        return objectStorage.getPublicUrl(objectKey);
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new VioletedRule("File cannot be empty");
        }

        if (file.getSize() > properties.maxFileSize().toBytes()) {
            throw new VioletedRule(
                    "File exceeds the maximum allowed size of "
                            + properties.maxFileSize().toMegabytes()
                            + " MB"
            );
        }

        if (file.getContentType() == null
                || file.getContentType().isBlank()) {
            throw new VioletedRule("File content type cannot be blank");
        }
    }

    private void validatePrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            throw new VioletedRule("Storage prefix cannot be blank");
        }

        boolean invalidPrefix = prefix.startsWith("/")
                || prefix.endsWith("/")
                || prefix.contains("\\")
                || prefix.contains("//")
                || java.util.Arrays.stream(prefix.split("/", -1))
                .anyMatch(segment ->
                        segment.isBlank()
                                || segment.equals(".")
                                || segment.equals("..")
                );

        if (invalidPrefix) {
            throw new VioletedRule("Invalid storage prefix");
        }
    }
}
