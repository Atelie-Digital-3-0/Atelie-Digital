package br.com.ateliedigital.store.unit.service;

import br.com.ateliedigital.exceptions.ResourceNotFoundException;
import br.com.ateliedigital.exceptions.VioletedRule;
import br.com.ateliedigital.outbox.OutboxManagement;
import br.com.ateliedigital.store.dto.requests.StoreAddressRequests;
import br.com.ateliedigital.store.dto.requests.StoreRequests;
import br.com.ateliedigital.store.dto.responses.StoreResponses;
import br.com.ateliedigital.store.event.StoreCreatedHandler;
import br.com.ateliedigital.store.event.StoreCreatedPayload;
import br.com.ateliedigital.store.mapper.StoreAddressMapper;
import br.com.ateliedigital.store.mapper.StoreMapper;
import br.com.ateliedigital.store.model.AddressModel;
import br.com.ateliedigital.store.model.CategoryModel;
import br.com.ateliedigital.store.model.StoreModel;
import br.com.ateliedigital.store.repository.CategoryRepository;
import br.com.ateliedigital.store.repository.StoreRepository;
import br.com.ateliedigital.store.service.StoreAddressService;
import br.com.ateliedigital.store.service.StoreService;
import br.com.ateliedigital.storage.service.StorageService;
import org.springframework.web.multipart.MultipartFile;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class StoreServiceTest {
    @Mock
    private StorageService storageService;

    @Mock
    private MultipartFile image;

    @Mock
    private MultipartFile banner;

    @Mock
    private StoreRepository storeRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private StoreMapper storeMapper;

    @Mock
    private StoreAddressMapper addressMapper;

    @Mock
    private StoreAddressService addressService;

    @Mock
    private OutboxManagement outboxManagement;

    @InjectMocks
    private StoreService storeService;

    private UUID artisanId;
    private UUID storeId;
    private UUID categoryId;

    private CategoryModel category;
    private StoreModel store;
    private AddressModel address;

    private StoreResponses.StorePublicResponse response;

    @BeforeEach
    void setUp() {
        artisanId = UUID.randomUUID();
        storeId = UUID.randomUUID();
        categoryId = UUID.randomUUID();

        category = new CategoryModel();
        category.setId(categoryId);
        category.setName("Artesanato");

        address = new AddressModel();
        address.setStreet("Rua das Flores");
        address.setNumber("100");
        address.setNeighborhood("Centro");
        address.setCity("Natal");
        address.setState("RN");
        address.setZipCode("59000-000");

        store = new StoreModel();
        store.setId(storeId);
        store.setArtisanId(artisanId.toString());
        store.setName("Ateliê das Flores");
        store.setPhone("(84) 99999-9999");
        store.setEmail("contato@ateliedasflores.com");
        store.setDescription("Produtos artesanais");
        store.setCategory(category);
        store.setAddress(address);

        address.setStore(store);

        response = new StoreResponses.StorePublicResponse(
                storeId,
                artisanId,
                store.getName(),
                store.getPhone(),
                store.getEmail(),
                store.getDescription(),
                null,
                null,
                null,
                null,
                null,
                null
        );
    }


    // CREATE STORE


    @Test
    void shouldCreateStoreSuccessfullyAndEnqueueEvent() {
        StoreRequests.CreateStoreRequest request = createRequest();

        when(storeRepository.existsByArtisanIdAndDeletedAtIsNull(
                artisanId.toString()
        )).thenReturn(false);

        when(categoryRepository.findById(categoryId))
                .thenReturn(Optional.of(category));

        when(storeMapper.toEntity(request)).thenReturn(store);

        when(addressService.validateAndFormatZipCode("59000-000"))
                .thenReturn("59000-000");

        when(storeRepository.save(store)).thenReturn(store);

        String imageKey = "stores/" + storeId + "/image";
        String bannerKey = "stores/" + storeId + "/banner";

        when(storageService.upload(image, "stores/" + storeId + "/image"))
                .thenReturn(imageKey);

        when(storageService.upload(banner, "stores/" + storeId + "/banner"))
                .thenReturn(bannerKey);

        when(storageService.getPublicUrl(imageKey))
                .thenReturn("http://localhost:9000/atelie/" + imageKey);

        when(storageService.getPublicUrl(bannerKey))
                .thenReturn("http://localhost:9000/atelie/" + bannerKey);

        when(storeMapper.toResponse(store)).thenReturn(response);

        StoreResponses.StorePublicResponse result =
                storeService.createStore(artisanId, request, image, banner);

        assertNotNull(result);
        assertEquals(storeId, result.id());

        assertEquals(artisanId.toString(), store.getArtisanId());
        assertSame(category, store.getCategory());
        assertEquals("59000-000", address.getZipCode());
        assertSame(store, address.getStore());

        verify(storeRepository, times(2)).save(store);

        verify(outboxManagement).enqueue(
                eq("STORE"),
                eq(storeId),
                eq(StoreCreatedHandler.EVENT_TYPE),
                eq(new StoreCreatedPayload(storeId, artisanId))
        );
        verify(storageService).upload(
                image,
                "stores/" + storeId + "/image"
        );

        verify(storageService).upload(
                banner,
                "stores/" + storeId + "/banner"
        );

        assertEquals(imageKey, store.getImage());
        assertEquals(bannerKey, store.getBanner());
    }

    @Test
    void shouldRejectCreationWhenArtisanAlreadyHasActiveStore() {
        StoreRequests.CreateStoreRequest request = createRequest();

        when(storeRepository.existsByArtisanIdAndDeletedAtIsNull(
                artisanId.toString()
        )).thenReturn(true);

        assertThrows(
                VioletedRule.class,
                () -> storeService.createStore(artisanId, request, image, banner)
        );

        verifyNoInteractions(categoryRepository);
        verifyNoInteractions(storeMapper);
        verifyNoInteractions(addressService);
        verifyNoInteractions(outboxManagement);

        verify(storeRepository, never()).save(any());
    }

    @Test
    void shouldThrowExceptionWhenCategoryDoesNotExist() {
        StoreRequests.CreateStoreRequest request = createRequest();

        when(storeRepository.existsByArtisanIdAndDeletedAtIsNull(
                artisanId.toString()
        )).thenReturn(false);

        when(categoryRepository.findById(categoryId))
                .thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> storeService.createStore(artisanId, request, image, banner)
        );

        verifyNoInteractions(storeMapper);
        verifyNoInteractions(addressService);
        verifyNoInteractions(outboxManagement);

        verify(storeRepository, never()).save(any());
    }

    @Test
    void shouldNotSaveStoreOrEnqueueEventWhenZipCodeValidationFails() {
        StoreRequests.CreateStoreRequest request = createRequest();

        when(storeRepository.existsByArtisanIdAndDeletedAtIsNull(
                artisanId.toString()
        )).thenReturn(false);

        when(categoryRepository.findById(categoryId))
                .thenReturn(Optional.of(category));

        when(storeMapper.toEntity(request)).thenReturn(store);

        when(addressService.validateAndFormatZipCode("59000-000"))
                .thenThrow(new IllegalArgumentException("Invalid zip code"));

        assertThrows(
                IllegalArgumentException.class,
                () -> storeService.createStore(artisanId, request, image, banner)
        );

        verify(storeRepository, never()).save(any());
        verifyNoInteractions(outboxManagement);
    }


    // GET STORE


    @Test
    void shouldReturnStoreWhenIdExistsAndStoreIsActive() {
        when(storeRepository.findByIdAndDeletedAtIsNull(storeId))
                .thenReturn(Optional.of(store));

        when(storeMapper.toResponse(store)).thenReturn(response);

        StoreResponses.StorePublicResponse result =
                storeService.getStoreById(storeId);

        assertNotNull(result);
        assertEquals(storeId, result.id());

        verify(storeRepository).findByIdAndDeletedAtIsNull(storeId);
        verify(storeMapper).toResponse(store);
    }

    @Test
    void shouldThrowExceptionWhenStoreIdDoesNotExist() {
        when(storeRepository.findByIdAndDeletedAtIsNull(storeId))
                .thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> storeService.getStoreById(storeId)
        );

        verifyNoInteractions(storeMapper);
    }

    @Test
    void shouldReturnArtisanOwnStore() {
        when(storeRepository.findByArtisanIdAndDeletedAtIsNull(
                artisanId.toString()
        )).thenReturn(Optional.of(store));

        when(storeMapper.toResponse(store)).thenReturn(response);

        StoreResponses.StorePublicResponse result =
                storeService.getMyStore(artisanId);

        assertNotNull(result);
        assertEquals(storeId, result.id());

        verify(storeRepository)
                .findByArtisanIdAndDeletedAtIsNull(artisanId.toString());
    }

    @Test
    void shouldThrowExceptionWhenArtisanDoesNotHaveActiveStore() {
        when(storeRepository.findByArtisanIdAndDeletedAtIsNull(
                artisanId.toString()
        )).thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> storeService.getMyStore(artisanId)
        );

        verifyNoInteractions(storeMapper);
    }


    // UPDATE STORE


    @Test
    void shouldUpdateStoreWhenRequesterIsOwner() {
        StoreRequests.UpdateStoreRequest request =
                new StoreRequests.UpdateStoreRequest(
                        null,
                        null,
                        "Nova descrição",
                        null
                );

        when(storeRepository.findByIdAndDeletedAtIsNull(storeId))
                .thenReturn(Optional.of(store));

        when(storeMapper.toResponse(store)).thenReturn(response);

        StoreResponses.StorePublicResponse result =
                storeService.updateStore(storeId, artisanId, request);

        assertNotNull(result);

        verify(storeMapper).updateEntity(request, store);
        verify(addressMapper, never()).updateEntity(any(), any());
        verify(storeMapper).toResponse(store);
    }

    @Test
    void shouldRejectUpdateWhenRequesterIsNotOwner() {
        UUID anotherArtisanId = UUID.randomUUID();

        StoreRequests.UpdateStoreRequest request =
                new StoreRequests.UpdateStoreRequest(
                        null,
                        null,
                        "Nova descrição",
                        null
                );

        when(storeRepository.findByIdAndDeletedAtIsNull(storeId))
                .thenReturn(Optional.of(store));

        assertThrows(
                AccessDeniedException.class,
                () -> storeService.updateStore(
                        storeId,
                        anotherArtisanId,
                        request
                )
        );

        verifyNoInteractions(storeMapper);
        verifyNoInteractions(addressMapper);
        verifyNoInteractions(addressService);
    }

    @Test
    void shouldUpdateAddressAndValidateZipCodeWhenAddressIsProvided() {
        StoreAddressRequests.AddressUpdateRequest addressRequest =
                new StoreAddressRequests.AddressUpdateRequest(
                        "Nova Rua",
                        null,
                        null,
                        null,
                        null,
                        "12345-678",
                        null
                );

        StoreRequests.UpdateStoreRequest request =
                new StoreRequests.UpdateStoreRequest(
                        null,
                        null,
                        null,
                        addressRequest
                );

        when(storeRepository.findByIdAndDeletedAtIsNull(storeId))
                .thenReturn(Optional.of(store));

        when(addressService.validateAndFormatZipCode("12345-678"))
                .thenReturn("12345-678");

        when(storeMapper.toResponse(store)).thenReturn(response);

        storeService.updateStore(storeId, artisanId, request);

        verify(addressMapper).updateEntity(addressRequest, address);

        verify(addressService)
                .validateAndFormatZipCode("12345-678");

        assertEquals("12345-678", address.getZipCode());
    }

    @Test
    void shouldNotValidateZipCodeWhenAddressUpdateDoesNotProvideZipCode() {
        StoreAddressRequests.AddressUpdateRequest addressRequest =
                new StoreAddressRequests.AddressUpdateRequest(
                        "Nova Rua",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                );

        StoreRequests.UpdateStoreRequest request =
                new StoreRequests.UpdateStoreRequest(
                        null,
                        null,
                        null,
                        addressRequest
                );

        when(storeRepository.findByIdAndDeletedAtIsNull(storeId))
                .thenReturn(Optional.of(store));

        when(storeMapper.toResponse(store)).thenReturn(response);

        storeService.updateStore(storeId, artisanId, request);

        verify(addressMapper).updateEntity(addressRequest, address);

        verifyNoInteractions(addressService);
    }

    @Test
    void shouldThrowExceptionWhenStoreAddressIsMissingDuringUpdate() {
        store.setAddress(null);

        StoreRequests.UpdateStoreRequest request =
                new StoreRequests.UpdateStoreRequest(
                        null,
                        null,
                        null,
                        new StoreAddressRequests.AddressUpdateRequest(
                                "Nova Rua",
                                null,
                                null,
                                null,
                                null,
                                null,
                                null
                        )
                );

        when(storeRepository.findByIdAndDeletedAtIsNull(storeId))
                .thenReturn(Optional.of(store));

        assertThrows(
                IllegalStateException.class,
                () -> storeService.updateStore(storeId, artisanId, request)
        );

        verifyNoInteractions(addressMapper);
        verifyNoInteractions(addressService);
    }


    // DELETE STORE


    @Test
    void shouldSoftDeleteStoreWhenRequesterIsOwner() {
        when(storeRepository.findByIdAndDeletedAtIsNull(storeId))
                .thenReturn(Optional.of(store));

        storeService.deleteStore(storeId, artisanId);

        assertNotNull(store.getDeletedAt());

        verify(storeRepository).save(store);
    }

    @Test
    void shouldRejectDeletionWhenRequesterIsNotOwner() {
        UUID anotherArtisanId = UUID.randomUUID();

        when(storeRepository.findByIdAndDeletedAtIsNull(storeId))
                .thenReturn(Optional.of(store));

        assertThrows(
                AccessDeniedException.class,
                () -> storeService.deleteStore(storeId, anotherArtisanId)
        );

        assertNull(store.getDeletedAt());

        verify(storeRepository, never()).save(any());
    }

    @Test
    void shouldThrowExceptionWhenDeletingNonexistentStore() {
        when(storeRepository.findByIdAndDeletedAtIsNull(storeId))
                .thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> storeService.deleteStore(storeId, artisanId)
        );

        verify(storeRepository, never()).save(any());
    }


    // HELPERS


    private StoreRequests.CreateStoreRequest createRequest() {
        return new StoreRequests.CreateStoreRequest(
                "Ateliê das Flores",
                "(84) 99999-9999",
                "contato@ateliedasflores.com",
                "Produtos artesanais",
                categoryId,
                new StoreAddressRequests.AddressRequest(
                        "Rua das Flores",
                        "100",
                        "Centro",
                        "Natal",
                        "RN",
                        "59000-000",
                        null
                )
        );
    }
}
