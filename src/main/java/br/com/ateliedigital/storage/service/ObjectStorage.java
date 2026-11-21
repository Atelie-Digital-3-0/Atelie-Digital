package br.com.ateliedigital.storage.service;
import br.com.ateliedigital.storage.dto.StoredObject;
import java.io.InputStream;

public interface ObjectStorage {
    void upload(
            String objectKey,
            InputStream inputStream,
            long size,
            String contentType
    );

    StoredObject download(String objectKey);

    void delete(String objectKey);

    String getPublicUrl(String objectKey);

    boolean exists(String objectKey);
}
