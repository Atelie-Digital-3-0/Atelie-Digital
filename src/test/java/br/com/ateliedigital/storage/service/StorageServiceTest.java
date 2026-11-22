package br.com.ateliedigital.storage.service;


import br.com.ateliedigital.exceptions.VioletedRule;
import br.com.ateliedigital.storage.config.StorageProperties;
import br.com.ateliedigital.storage.dto.StoredObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class StorageServiceTest {

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024;
    private static final byte[] JPEG_SIGNATURE = {
            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF
    };

    private static final byte[] PNG_SIGNATURE = {
            (byte) 0x89, 0x50, 0x4E, 0x47,
            0x0D, 0x0A, 0x1A, 0x0A
    };

    private static final byte[] WEBP_SIGNATURE = {
            'R', 'I', 'F', 'F', 0, 0, 0, 0,
            'W', 'E', 'B', 'P'
    };
    private byte[] fileContent(byte[] signature) {
        byte[] content = Arrays.copyOf(signature, signature.length + 3);

        content[signature.length] = 1;
        content[signature.length + 1] = 2;
        content[signature.length + 2] = 3;

        return content;
    }

    @Mock
    private ObjectStorage objectStorage;

    private StorageService storageService;

    @BeforeEach
    void setUp() {
        StorageProperties properties = new StorageProperties(
                "http://localhost:9000",
                "http://localhost:9000",
                "minioadmin",
                "minioadmin",
                "atelie-media",
                DataSize.ofMegabytes(5)
        );

        storageService = new StorageService(objectStorage, properties);
    }


    // UPLOAD

    @Test
    void shouldUploadJpegImageAndReturnGeneratedObjectKey() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "photo.jpg",
                "image/jpeg",
                fileContent(JPEG_SIGNATURE)
        );

        String result = storageService.upload(file, "stores/42/logo");

        assertNotNull(result);
        assertTrue(result.matches(
                "stores/42/logo/[0-9a-fA-F-]{36}\\.jpg"
        ));

        verify(objectStorage).upload(
                eq(result),
                any(InputStream.class),
                eq((long) fileContent(JPEG_SIGNATURE).length),
                eq("image/jpeg")
        );
    }

    @Test
    void shouldUploadPngImage() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "photo.png",
                "image/png",
                fileContent(PNG_SIGNATURE)
        );

        String result = storageService.upload(file, "products/10/images");

        assertTrue(result.endsWith(".png"));

        verify(objectStorage).upload(
                eq(result),
                any(InputStream.class),
                eq((long) fileContent(PNG_SIGNATURE).length),
                eq("image/png")
        );
    }

    @Test
    void shouldUploadWebpImage() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "photo.webp",
                "image/webp",
                fileContent(WEBP_SIGNATURE)
        );

        String result = storageService.upload(file, "products/10/images");

        assertTrue(result.endsWith(".webp"));

        verify(objectStorage).upload(
                eq(result),
                any(InputStream.class),
                eq((long) fileContent(WEBP_SIGNATURE).length),
                eq("image/webp")
        );
    }

    @Test
    void shouldNormalizeContentTypeBeforeUploading() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "photo.jpg",
                "image/jpeg; charset=binary",
                fileContent(JPEG_SIGNATURE)
        );

        String result = storageService.upload(file, "stores/42/logo");

        verify(objectStorage).upload(
                eq(result),
                any(InputStream.class),
                eq((long) fileContent(JPEG_SIGNATURE).length),
                eq("image/jpeg")
        );
    }

    @Test
    void shouldRejectNullFile() {
        assertThrows(
                VioletedRule.class,
                () -> storageService.upload(null, "stores/42/logo")
        );

        verifyNoInteractions(objectStorage);
    }

    @Test
    void shouldRejectEmptyFile() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "empty.jpg",
                "image/jpeg",
                new byte[0]
        );

        assertThrows(
                VioletedRule.class,
                () -> storageService.upload(file, "stores/42/logo")
        );

        verifyNoInteractions(objectStorage);
    }

    @Test
    void shouldRejectFileExceedingMaximumAllowedSize() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "large.jpg",
                "image/jpeg",
                new byte[(int) MAX_FILE_SIZE + 1]
        );

        assertThrows(
                VioletedRule.class,
                () -> storageService.upload(file, "stores/42/logo")
        );

        verifyNoInteractions(objectStorage);
    }

    @Test
    void shouldAcceptFileAtMaximumAllowedSize() {
        byte[] content = new byte[(int) MAX_FILE_SIZE];

        System.arraycopy(
                JPEG_SIGNATURE,
                0,
                content,
                0,
                JPEG_SIGNATURE.length
        );
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "maximum.jpg",
                "image/jpeg",
                content

        );

        String result = storageService.upload(file, "stores/42/logo");

        assertNotNull(result);

        verify(objectStorage).upload(
                eq(result),
                any(InputStream.class),
                eq(MAX_FILE_SIZE),
                eq("image/jpeg")
        );
    }

    @Test
    void shouldRejectUnsupportedContentType() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "document.pdf",
                "application/pdf",
                new byte[]{1, 2, 3}
        );

        assertThrows(
                VioletedRule.class,
                () -> storageService.upload(file, "stores/42/logo")
        );

        verifyNoInteractions(objectStorage);
    }

    @Test
    void shouldRejectMissingContentType() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "photo.jpg",
                null,
                new byte[]{1, 2, 3}
        );

        assertThrows(
                VioletedRule.class,
                () -> storageService.upload(file, "stores/42/logo")
        );

        verifyNoInteractions(objectStorage);
    }
    @ParameterizedTest
    @ValueSource(strings = {"", " "})
    void shouldRejectBlankContentType(String contentType) {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "photo.jpg",
                contentType,
                fileContent(JPEG_SIGNATURE)
        );

        assertThrows(
                VioletedRule.class,
                () -> storageService.upload(file, "stores/42/logo")
        );

        verifyNoInteractions(objectStorage);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            " ",
            "/stores/42/logo",
            "stores/42/logo/",
            "stores//logo",
            "stores/../logo",
            "stores/./logo",
            "stores\\42\\logo"
    })
    void shouldRejectInvalidStoragePrefix(String prefix) {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "photo.jpg",
                "image/jpeg",
                fileContent(JPEG_SIGNATURE)
        );

        assertThrows(
                VioletedRule.class,
                () -> storageService.upload(file, prefix)
        );

        verifyNoInteractions(objectStorage);
    }


    @Test
    void shouldRejectNullStoragePrefix() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "photo.jpg",
                "image/jpeg",
                fileContent(JPEG_SIGNATURE)
        );

        assertThrows(
                VioletedRule.class,
                () -> storageService.upload(file, null)
        );

        verifyNoInteractions(objectStorage);
    }

    @Test
    void shouldThrowIllegalStateExceptionWhenFileCannotBeRead()
            throws IOException {

        MultipartFile file = mock(MultipartFile.class);

        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(3L);
        when(file.getContentType()).thenReturn("image/jpeg");
        when(file.getInputStream())
                .thenThrow(new IOException("Read failure"));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> storageService.upload(file, "stores/42/logo")
        );

        assertEquals(
                "Failed to read uploaded file",
                exception.getMessage()
        );

        verifyNoInteractions(objectStorage);
    }

    // DOWNLOAD

    @Test
    void shouldDownloadObjectFromStorage() {
        StoredObject expected = new StoredObject(
                new ByteArrayInputStream(new byte[]{1, 2, 3}),
                3L,
                "image/jpeg"
        );

        when(objectStorage.download("stores/42/logo/image.jpg"))
                .thenReturn(expected);

        StoredObject result = storageService.download(
                "stores/42/logo/image.jpg"
        );

        assertSame(expected, result);

        verify(objectStorage).download("stores/42/logo/image.jpg");
    }


    // DELETE

    @Test
    void shouldDeleteObjectFromStorage() {
        String objectKey = "products/10/images/image.jpg";

        storageService.delete(objectKey);

        verify(objectStorage).delete(objectKey);
    }

    // EXISTS

    @Test
    void shouldReturnTrueWhenObjectExists() {
        String objectKey = "stores/42/logo/image.jpg";

        when(objectStorage.exists(objectKey)).thenReturn(true);

        boolean result = storageService.exists(objectKey);

        assertTrue(result);

        verify(objectStorage).exists(objectKey);
    }

    @Test
    void shouldReturnFalseWhenObjectDoesNotExist() {
        String objectKey = "stores/42/logo/image.jpg";

        when(objectStorage.exists(objectKey)).thenReturn(false);

        boolean result = storageService.exists(objectKey);

        assertFalse(result);

        verify(objectStorage).exists(objectKey);
    }


    // PUBLIC URL

    @Test
    void shouldReturnPublicUrlFromStorage() {
        String objectKey = "stores/42/logo/image.jpg";
        String expectedUrl =
                "http://localhost:9000/atelie-media/stores/42/logo/image.jpg";

        when(objectStorage.getPublicUrl(objectKey))
                .thenReturn(expectedUrl);

        String result = storageService.getPublicUrl(objectKey);

        assertEquals(expectedUrl, result);

        verify(objectStorage).getPublicUrl(objectKey);
    }


    @Test
    void shouldRejectFileWhenDeclaredContentTypeDoesNotMatchSignature() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "photo.jpg",
                "image/jpeg",
                fileContent(PNG_SIGNATURE)
        );

        assertThrows(
                VioletedRule.class,
                () -> storageService.upload(file, "stores/42/logo")
        );

        verifyNoInteractions(objectStorage);
    }

    @Test
    void shouldRejectFileWithInvalidSignature() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "photo.jpg",
                "image/jpeg",
                new byte[]{1, 2, 3, 4, 5}
        );

        assertThrows(
                VioletedRule.class,
                () -> storageService.upload(file, "stores/42/logo")
        );

        verifyNoInteractions(objectStorage);
    }

    @Test
    void shouldRejectFileSmallerThanRequiredSignature() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "photo.png",
                "image/png",
                new byte[]{(byte) 0x89, 0x50, 0x4E}
        );

        assertThrows(
                VioletedRule.class,
                () -> storageService.upload(file, "stores/42/logo")
        );

        verifyNoInteractions(objectStorage);
    }

    @Test
    void shouldUploadCompleteFileIncludingSignatureBytes() throws IOException {
        byte[] expectedContent = fileContent(JPEG_SIGNATURE);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "photo.jpg",
                "image/jpeg",
                expectedContent
        );

        byte[][] uploadedContent = new byte[1][];

        doAnswer(invocation -> {
            InputStream inputStream = invocation.getArgument(1);
            uploadedContent[0] = inputStream.readAllBytes();
            return null;
        }).when(objectStorage).upload(
                any(String.class),
                any(InputStream.class),
                eq((long) expectedContent.length),
                eq("image/jpeg")
        );

        storageService.upload(file, "stores/42/logo");

        assertArrayEquals(expectedContent, uploadedContent[0]);
    }

}
