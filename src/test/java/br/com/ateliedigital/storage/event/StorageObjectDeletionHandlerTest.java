package br.com.ateliedigital.storage.event;

import br.com.ateliedigital.storage.service.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class StorageObjectDeletionHandlerTest {
    @Mock
    private StorageService storageService;

    private StorageObjectDeletionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new StorageObjectDeletionHandler(
                storageService,
                JsonMapper.builder().build()
        );
    }

    @Test
    void shouldReturnCorrectEventType() {
        assertEquals(
                StorageObjectDeletionHandler.EVENT_TYPE,
                handler.eventType()
        );
    }

    @Test
    void shouldConvertPayloadAndDeleteObject() {
        String objectKey = "stores/42/logo/image.jpg";

        Map<String, Object> payload = Map.of(
                "objectKey",
                objectKey
        );

        handler.handle(payload);

        verify(storageService).delete(objectKey);
    }

    @Test
    void shouldRejectNullPayload() {
        assertThrows(
                IllegalArgumentException.class,
                () -> handler.handle(null)
        );

        verifyNoInteractions(storageService);
    }

    @Test
    void shouldRejectNullObjectKey() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("objectKey", null);

        assertThrows(
                IllegalArgumentException.class,
                () -> handler.handle(payload)
        );

        verifyNoInteractions(storageService);
    }

    @Test
    void shouldRejectBlankObjectKey() {
        Map<String, Object> payload = Map.of(
                "objectKey",
                "   "
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> handler.handle(payload)
        );

        verifyNoInteractions(storageService);
    }

    @Test
    void shouldPropagateExceptionWhenStorageDeletionFails() {
        String objectKey = "stores/42/logo/image.jpg";

        Map<String, Object> payload = Map.of(
                "objectKey",
                objectKey
        );

        RuntimeException expectedException =
                new RuntimeException("MinIO unavailable");

        doThrow(expectedException)
                .when(storageService)
                .delete(objectKey);

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> handler.handle(payload)
        );

        assertSame(expectedException, exception);
        verify(storageService).delete(objectKey);
    }
}
