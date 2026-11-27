
package br.com.ateliedigital.product.unit.service;

import br.com.ateliedigital.exceptions.ResourceNotFoundException;
import br.com.ateliedigital.exceptions.RuleConflit;
import br.com.ateliedigital.exceptions.VioletedRule;
import br.com.ateliedigital.outbox.OutboxManagement;
import br.com.ateliedigital.product.dto.requests.ProductRequests;
import br.com.ateliedigital.product.dto.responses.ProductResponses;
import br.com.ateliedigital.product.mapper.ProductImageMapper;
import br.com.ateliedigital.product.mapper.ProductMapper;
import br.com.ateliedigital.product.mapper.ProductVariationMapper;
import br.com.ateliedigital.product.model.ProductImageModel;
import br.com.ateliedigital.product.model.ProductModel;
import br.com.ateliedigital.product.model.ProductVariationModel;
import br.com.ateliedigital.product.repository.ProductImageRepository;
import br.com.ateliedigital.product.repository.ProductRepository;
import br.com.ateliedigital.product.repository.ProductVariationRepository;
import br.com.ateliedigital.product.service.ProductService;
import br.com.ateliedigital.storage.event.StorageObjectDeletionHandler;
import br.com.ateliedigital.storage.event.StorageObjectDeletionPayload;
import br.com.ateliedigital.store.model.StoreModel;
import br.com.ateliedigital.store.repository.StoreRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductVariationRepository variationRepository;

    @Mock
    private ProductImageRepository imageRepository;

    @Mock
    private StoreRepository storeRepository;

    @Mock
    private ProductMapper productMapper;

    @Mock
    private ProductVariationMapper variationMapper;

    @Mock
    private ProductImageMapper imageMapper;

    @Mock
    private OutboxManagement outboxManagement;

    @InjectMocks
    private ProductService productService;

    private UUID artisanId;
    private UUID anotherArtisanId;
    private UUID storeId;
    private UUID productId;
    private UUID variationId;

    private StoreModel store;
    private ProductModel product;
    private ProductVariationModel variation;

    private ProductResponses.ProductResponse response;
    private ProductResponses.ProductVariationResponse variationResponse;

    @BeforeEach
    void setUp() {
        artisanId = UUID.randomUUID();
        anotherArtisanId = UUID.randomUUID();
        storeId = UUID.randomUUID();
        productId = UUID.randomUUID();
        variationId = UUID.randomUUID();

        store = new StoreModel();
        store.setId(storeId);
        store.setArtisanId(artisanId.toString());
        store.setName("Ateliê das Flores");

        product = new ProductModel();
        product.setId(productId);
        product.setStore(store);
        product.setName("Vaso artesanal");
        product.setDescription("Vaso produzido manualmente");

        variation = new ProductVariationModel();
        variation.setId(variationId);
        variation.setProduct(product);
        variation.setPrice(new BigDecimal("50.00"));
        variation.setStock(10);
        variation.setSku("VASO-001");
        variation.setPrimary(true);

        product.setVariations(List.of(variation));

        variationResponse =
                new ProductResponses.ProductVariationResponse(
                        variationId,
                        new BigDecimal("50.00"),
                        null,
                        null,
                        null,
                        null,
                        "VASO-001",
                        10,
                        null,
                        null,
                        true,
                        List.of(),
                        null,
                        null
                );

        response = new ProductResponses.ProductResponse(
                productId,
                storeId,
                "Vaso artesanal",
                "Vaso produzido manualmente",
                List.of(),
                null,
                null
        );
    }

    // CREATE PRODUCT

    @Test
    void shouldCreateProductSuccessfully() {
        ProductRequests.CreateProductRequest request = createRequest();

        when(storeRepository.findByIdAndDeletedAtIsNull(storeId))
                .thenReturn(Optional.of(store));

        when(variationRepository.existsBySkuIncludingDeleted("VASO-001"))
                .thenReturn(false);

        when(productMapper.toEntity(request)).thenReturn(product);
        when(productRepository.save(product)).thenReturn(product);

        mockProductResponse();

        ProductResponses.ProductResponse result =
                productService.createProduct(artisanId, storeId, request);

        assertNotNull(result);
        assertEquals(productId, result.id());
        assertEquals(storeId, result.storeId());

        assertSame(store, product.getStore());
        assertSame(product, variation.getProduct());

        verify(productRepository).save(product);
        verify(variationRepository).saveAll(product.getVariations());
    }

    @Test
    void shouldThrowExceptionWhenStoreDoesNotExistDuringCreation() {
        ProductRequests.CreateProductRequest request = createRequest();

        when(storeRepository.findByIdAndDeletedAtIsNull(storeId))
                .thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> productService.createProduct(
                        artisanId, storeId, request
                )
        );

        verifyNoInteractions(productMapper);
        verifyNoInteractions(productRepository);
        verifyNoInteractions(variationRepository);
    }

    @Test
    void shouldRejectCreationWhenArtisanIsNotStoreOwner() {
        ProductRequests.CreateProductRequest request = createRequest();

        store.setArtisanId(anotherArtisanId.toString());

        when(storeRepository.findByIdAndDeletedAtIsNull(storeId))
                .thenReturn(Optional.of(store));

        assertThrows(
                AccessDeniedException.class,
                () -> productService.createProduct(
                        artisanId, storeId, request
                )
        );

        verifyNoInteractions(productMapper);
        verifyNoInteractions(productRepository);
        verifyNoInteractions(variationRepository);
    }

    @Test
    void shouldRejectCreationWhenProductHasNoPrimaryVariation() {
        ProductRequests.CreateProductRequest request =
                new ProductRequests.CreateProductRequest(
                        "Vaso artesanal",
                        "Descrição",
                        List.of(variationRequest(false))
                );

        when(storeRepository.findByIdAndDeletedAtIsNull(storeId))
                .thenReturn(Optional.of(store));

        assertThrows(
                VioletedRule.class,
                () -> productService.createProduct(
                        artisanId, storeId, request
                )
        );

        verifyNoInteractions(productMapper);
        verifyNoInteractions(productRepository);
    }

    @Test
    void shouldRejectCreationWhenProductHasMultiplePrimaryVariations() {
        ProductRequests.CreateProductRequest request =
                new ProductRequests.CreateProductRequest(
                        "Vaso artesanal",
                        "Descrição",
                        List.of(
                                variationRequest(true),
                                variationRequest(true)
                        )
                );

        when(storeRepository.findByIdAndDeletedAtIsNull(storeId))
                .thenReturn(Optional.of(store));

        assertThrows(
                VioletedRule.class,
                () -> productService.createProduct(
                        artisanId, storeId, request
                )
        );

        verifyNoInteractions(productMapper);
        verifyNoInteractions(productRepository);
    }

    @Test
    void shouldRejectCreationWhenSkuIsAlreadyRegistered() {
        ProductRequests.CreateProductRequest request = createRequest();

        when(storeRepository.findByIdAndDeletedAtIsNull(storeId))
                .thenReturn(Optional.of(store));

        when(variationRepository.existsBySkuIncludingDeleted("VASO-001"))
                .thenReturn(true);

        assertThrows(
                RuleConflit.class,
                () -> productService.createProduct(
                        artisanId, storeId, request
                )
        );

        verifyNoInteractions(productMapper);
        verifyNoInteractions(productRepository);
    }

    // GET PRODUCT

    @Test
    void shouldReturnProductWhenItExistsAndIsActive() {
        when(productRepository.findActiveById(productId))
                .thenReturn(Optional.of(product));

        mockProductResponse();

        ProductResponses.ProductResponse result =
                productService.getProductById(productId);

        assertNotNull(result);
        assertEquals(productId, result.id());

        verify(productRepository).findActiveById(productId);
        verify(variationRepository).findActiveByProductId(productId);
    }

    @Test
    void shouldThrowExceptionWhenProductDoesNotExist() {
        when(productRepository.findActiveById(productId))
                .thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> productService.getProductById(productId)
        );

        verifyNoInteractions(productMapper);
    }

    // LIST PRODUCTS

    @Test
    void shouldReturnActiveProductsFromStore() {
        PageRequest pageable = PageRequest.of(0, 10);

        Page<ProductModel> products =
                new PageImpl<>(List.of(product));

        when(storeRepository.findByIdAndDeletedAtIsNull(storeId))
                .thenReturn(Optional.of(store));

        when(productRepository.findActiveByStoreId(storeId, pageable))
                .thenReturn(products);

        mockProductResponse();

        Page<ProductResponses.ProductResponse> result =
                productService.getProductsByStore(storeId, pageable);

        assertEquals(1, result.getTotalElements());
        assertEquals(productId, result.getContent().getFirst().id());

        verify(productRepository)
                .findActiveByStoreId(storeId, pageable);
    }

    @Test
    void shouldThrowExceptionWhenListingProductsFromNonexistentStore() {
        PageRequest pageable = PageRequest.of(0, 10);

        when(storeRepository.findByIdAndDeletedAtIsNull(storeId))
                .thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> productService.getProductsByStore(storeId, pageable)
        );


        verifyNoInteractions(productRepository);
    }

    // UPDATE PRODUCT

    @Test
    void shouldUpdateProductWhenRequesterIsOwner() {
        ProductRequests.UpdateProductRequest request =
                new ProductRequests.UpdateProductRequest(
                        "Novo nome",
                        "Nova descrição"
                );

        when(productRepository.findActiveById(productId))
                .thenReturn(Optional.of(product));

        mockStoreOwnedBy(artisanId);

        when(productRepository.save(product)).thenReturn(product);

        mockProductResponse();

        ProductResponses.ProductResponse result =
                productService.updateProduct(
                        artisanId, productId, request
                );

        assertNotNull(result);

        verify(productMapper).updateEntity(request, product);
        verify(productRepository).save(product);
    }

    @Test
    void shouldRejectUpdateWhenRequesterIsNotOwner() {
        ProductRequests.UpdateProductRequest request =
                new ProductRequests.UpdateProductRequest(
                        "Novo nome",
                        null
                );

        when(productRepository.findActiveById(productId))
                .thenReturn(Optional.of(product));

        // A loja pertence a outro artesão.
        mockStoreOwnedBy(anotherArtisanId);

        assertThrows(
                AccessDeniedException.class,
                () -> productService.updateProduct(
                        artisanId, productId, request
                )
        );

        verifyNoInteractions(productMapper);
        verify(productRepository, never()).save(any());
    }

    // UPDATE VARIATION

    @Test
    void shouldUpdateVariationWhenRequesterIsOwner() {
        ProductRequests.UpdateProductVariationRequest request =
                new ProductRequests.UpdateProductVariationRequest(
                        new BigDecimal("75.00"),
                        null,
                        null,
                        null,
                        null,
                        null,
                        20,
                        null,
                        null,
                        null
                );

        when(productRepository.findActiveById(productId))
                .thenReturn(Optional.of(product));

        mockStoreOwnedBy(artisanId);

        when(variationRepository.findActiveById(variationId))
                .thenReturn(Optional.of(variation));

        when(variationRepository.findActiveByProductId(productId))
                .thenReturn(List.of(variation));

        mockProductResponse();

        ProductResponses.ProductResponse result =
                productService.updateVariation(
                        artisanId,
                        productId,
                        variationId,
                        request
                );

        assertNotNull(result);

        verify(variationMapper).updateEntity(request, variation);
        verify(variationRepository).save(variation);
    }

    @Test
    void shouldRejectVariationUpdateWhenVariationBelongsToAnotherProduct() {
        UUID anotherProductId = UUID.randomUUID();

        ProductModel anotherProduct = new ProductModel();
        anotherProduct.setId(anotherProductId);

        variation.setProduct(anotherProduct);

        when(productRepository.findActiveById(productId))
                .thenReturn(Optional.of(product));

        mockStoreOwnedBy(artisanId);

        when(variationRepository.findActiveById(variationId))
                .thenReturn(Optional.of(variation));

        ProductRequests.UpdateProductVariationRequest request =
                new ProductRequests.UpdateProductVariationRequest(
                        null, null, null, null, null,
                        null, null, null, null, null
                );

        assertThrows(
                ResourceNotFoundException.class,
                () -> productService.updateVariation(
                        artisanId,
                        productId,
                        variationId,
                        request
                )
        );

        verify(variationRepository, never()).save(any());
    }

    @Test
    void shouldNotRemoveTheOnlyPrimaryVariation() {
        ProductRequests.UpdateProductVariationRequest request =
                new ProductRequests.UpdateProductVariationRequest(
                        null, null, null, null, null,
                        null, null, null, null, false
                );

        when(productRepository.findActiveById(productId))
                .thenReturn(Optional.of(product));

        mockStoreOwnedBy(artisanId);

        when(variationRepository.findActiveById(variationId))
                .thenReturn(Optional.of(variation));

        assertThrows(
                VioletedRule.class,
                () -> productService.updateVariation(
                        artisanId,
                        productId,
                        variationId,
                        request
                )
        );

        verify(variationRepository, never()).save(any());
    }

    // DELETE PRODUCT

    @Test
    void shouldSoftDeleteProductAndItsVariations() {
        when(productRepository.findActiveById(productId))
                .thenReturn(Optional.of(product));

        mockStoreOwnedBy(artisanId);

        when(variationRepository.findActiveByProductId(productId))
                .thenReturn(List.of(variation));

        when(imageRepository.findActiveByVariationId(variationId))
                .thenReturn(List.of());

        productService.deleteProduct(artisanId, productId);

        assertTrue(product.isDeleted());
        assertTrue(variation.isDeleted());

        verify(variationRepository).saveAll(List.of(variation));
        verify(productRepository).save(product);
    }

    @Test
    void shouldRejectDeletionWhenRequesterIsNotOwner() {
        when(productRepository.findActiveById(productId))
                .thenReturn(Optional.of(product));

        // A loja pertence a outro artesão.
        mockStoreOwnedBy(anotherArtisanId);

        assertThrows(
                AccessDeniedException.class,
                () -> productService.deleteProduct(
                        artisanId, productId
                )
        );

        assertFalse(product.isDeleted());

        verify(variationRepository, never())
                .findActiveByProductId(any());

        verify(productRepository, never()).save(any());
    }

    @Test
    void shouldEnqueueImageDeletionWhenDeletingProduct() {
        ProductImageModel image = new ProductImageModel();
        image.setStorageKey("products/" + productId + "/image.webp");

        when(productRepository.findActiveById(productId))
                .thenReturn(Optional.of(product));

        mockStoreOwnedBy(artisanId);

        when(variationRepository.findActiveByProductId(productId))
                .thenReturn(List.of(variation));

        when(imageRepository.findActiveByVariationId(variationId))
                .thenReturn(List.of(image));

        productService.deleteProduct(artisanId, productId);

        assertTrue(image.isDeleted());

        verify(outboxManagement).enqueue(
                eq("PRODUCT"),
                eq(productId),
                eq(StorageObjectDeletionHandler.EVENT_TYPE),
                eq(new StorageObjectDeletionPayload(image.getStorageKey()))
        );

        verify(imageRepository).saveAll(List.of(image));
    }

    @Test
    void shouldAllowPublicListingOfProductsFromStore() {
        PageRequest pageable = PageRequest.of(0, 10);

        store.setArtisanId(anotherArtisanId.toString());

        Page<ProductModel> products =
                new PageImpl<>(List.of(product));

        when(storeRepository.findByIdAndDeletedAtIsNull(storeId))
                .thenReturn(Optional.of(store));

        when(productRepository.findActiveByStoreId(storeId, pageable))
                .thenReturn(products);

        mockProductResponse();

        Page<ProductResponses.ProductResponse> result =
                productService.getProductsByStore(storeId, pageable);

        assertEquals(1, result.getTotalElements());
    }

    // HELPERS

    private ProductRequests.CreateProductRequest createRequest() {
        return new ProductRequests.CreateProductRequest(
                "Vaso artesanal",
                "Vaso produzido manualmente",
                List.of(variationRequest(true))
        );
    }

    private ProductRequests.ProductVariationRequest variationRequest(
            boolean primary
    ) {
        return new ProductRequests.ProductVariationRequest(
                new BigDecimal("50.00"),
                null,
                null,
                null,
                null,
                "VASO-001",
                10,
                null,
                null,
                primary
        );
    }

    private void mockProductResponse() {
        when(productMapper.toResponse(product))
                .thenReturn(response);

        when(variationRepository.findActiveByProductId(productId))
                .thenReturn(List.of(variation));

        when(variationMapper.toResponse(variation))
                .thenReturn(variationResponse);

        when(imageRepository.findActiveByVariationId(variationId))
                .thenReturn(List.of());
    }
    private void mockStoreOwnedBy(UUID ownerId) {
        store.setArtisanId(ownerId.toString());

        when(storeRepository.findByIdAndDeletedAtIsNull(storeId))
                .thenReturn(Optional.of(store));
    }
}
