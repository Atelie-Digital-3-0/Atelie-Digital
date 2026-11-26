
package br.com.ateliedigital.product.service;

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
import br.com.ateliedigital.storage.event.StorageObjectDeletionHandler;
import br.com.ateliedigital.storage.event.StorageObjectDeletionPayload;
import br.com.ateliedigital.store.model.StoreModel;
import br.com.ateliedigital.store.repository.StoreRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductVariationRepository variationRepository;
    private final ProductImageRepository imageRepository;
    private final StoreRepository storeRepository;

    private final ProductMapper productMapper;
    private final ProductVariationMapper variationMapper;
    private final ProductImageMapper imageMapper;

    private final OutboxManagement outboxManagement;

    public ProductService(
            ProductRepository productRepository,
            ProductVariationRepository variationRepository,
            ProductImageRepository imageRepository,
            StoreRepository storeRepository,
            ProductMapper productMapper,
            ProductVariationMapper variationMapper,
            ProductImageMapper imageMapper,
            OutboxManagement outboxManagement
    ) {
        this.productRepository = productRepository;
        this.variationRepository = variationRepository;
        this.imageRepository = imageRepository;
        this.storeRepository = storeRepository;
        this.productMapper = productMapper;
        this.variationMapper = variationMapper;
        this.imageMapper = imageMapper;
        this.outboxManagement = outboxManagement;
    }

    /**
     * Creates a product and its variations.
     */
    @Transactional
    public ProductResponses.ProductResponse createProduct(
            UUID artisanId,
            UUID storeId,
            ProductRequests.CreateProductRequest request
    ) {
        StoreModel store = findActiveStore(storeId);
        validateOwnership(store, artisanId);

        validatePrimaryVariation(request.variations());

        request.variations().forEach(
                variation -> validateSku(variation.sku())
        );

        ProductModel product = productMapper.toEntity(request);
        product.setStore(store);

        ProductModel savedProduct = productRepository.save(product);

        List<ProductVariationModel> variations =
                savedProduct.getVariations();

        variations.forEach(variation ->
                variation.setProduct(savedProduct)
        );

        variationRepository.saveAll(variations);

        return toResponse(savedProduct);
    }

    /**
     * Retrieves an active product.
     */
    @Transactional(readOnly = true)
    public ProductResponses.ProductResponse getProductById(
            UUID productId
    ) {
        ProductModel product = findActiveProduct(productId);

        return toResponse(product);
    }

    /**
     * Lists active products from a store.
     */
    @Transactional(readOnly = true)
    public Page<ProductResponses.ProductResponse> getProductsByStore(
            UUID storeId,
            Pageable pageable
    ) {
        findActiveStore(storeId);

        return productRepository.findActiveByStoreId(storeId, pageable)
                .map(this::toResponse);
    }

    /**
     * Updates product information.
     */
    @Transactional
    public ProductResponses.ProductResponse updateProduct(
            UUID artisanId,
            UUID productId,
            ProductRequests.UpdateProductRequest request
    ) {
        ProductModel product = findActiveProduct(productId);

        validateOwnership(product.getStore(), artisanId);

        productMapper.updateEntity(request, product);

        ProductModel savedProduct = productRepository.save(product);

        return toResponse(savedProduct);
    }

    /**
     * Updates an existing product variation.
     */
    @Transactional
    public ProductResponses.ProductResponse updateVariation(
            UUID artisanId,
            UUID productId,
            UUID variationId,
            ProductRequests.UpdateProductVariationRequest request
    ) {
        ProductModel product = findActiveProduct(productId);

        validateOwnership(product.getStore(), artisanId);

        ProductVariationModel variation = variationRepository
                .findActiveById(variationId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Product variation not found"
                        )
                );

        if (!variation.getProduct().getId().equals(productId)) {
            throw new ResourceNotFoundException(
                    "Product variation not found"
            );
        }

        if (request.sku() != null
                && !request.sku().equals(variation.getSku())) {
            validateSku(request.sku(), variationId);
        }

        if (Boolean.TRUE.equals(request.primary())) {
            removePrimaryFromOtherVariations(product, variationId);
        }

        if (Boolean.FALSE.equals(request.primary())
                && variation.isPrimary()) {
            throw new VioletedRule(
                    "A product must have exactly one primary variation"
            );
        }

        variationMapper.updateEntity(request, variation);

        variationRepository.save(variation);

        return toResponse(product);
    }

    /**
     * Soft deletes a product, its variations and its images.
     */
    @Transactional
    public void deleteProduct(
            UUID artisanId,
            UUID productId
    ) {
        ProductModel product = findActiveProduct(productId);

        validateOwnership(product.getStore(), artisanId);

        List<ProductVariationModel> variations =
                variationRepository.findActiveByProductId(productId);

        for (ProductVariationModel variation : variations) {

            List<ProductImageModel> images =
                    imageRepository.findActiveByVariationId(
                            variation.getId()
                    );

            for (ProductImageModel image : images) {
                image.setDeleted(true);

                enqueueObjectDeletion(
                        image.getStorageKey(),
                        productId
                );
            }

            imageRepository.saveAll(images);

            variation.setDeleted(true);
        }

        variationRepository.saveAll(variations);

        product.setDeleted(true);

        productRepository.save(product);
    }

    private StoreModel findActiveStore(UUID storeId) {
        return storeRepository.findByIdAndDeletedAtIsNull(storeId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Store not found")
                );
    }

    private ProductModel findActiveProduct(UUID productId) {
        return productRepository.findActiveById(productId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Product not found")
                );
    }

    private void validateOwnership(
            StoreModel store,
            UUID artisanId
    ) {
        if (!store.getArtisanId().equals(artisanId.toString())) {
            throw new AccessDeniedException(
                    "You do not have permission to manage this product"
            );
        }
    }

    private void validatePrimaryVariation(
            List<ProductRequests.ProductVariationRequest> variations
    ) {
        long primaryCount = variations.stream()
                .filter(variation ->
                        Boolean.TRUE.equals(variation.primary())
                )
                .count();

        if (primaryCount != 1) {
            throw new VioletedRule(
                    "A product must have exactly one primary variation"
            );
        }
    }

    private void validateSku(String sku) {
        if (sku != null && !sku.isBlank()
                && variationRepository.existsBySkuIncludingDeleted(sku)) {
            throw new RuleConflit(
                    "This SKU is already registered"
            );
        }
    }

    private void validateSku(
            String sku,
            UUID variationId
    ) {
        if (sku != null && !sku.isBlank()
                && variationRepository
                .existsBySkuAndIdNotIncludingDeleted(sku, variationId)) {
            throw new RuleConflit(
                    "This SKU is already registered"
            );
        }
    }

    private void removePrimaryFromOtherVariations(
            ProductModel product,
            UUID currentVariationId
    ) {
        List<ProductVariationModel> variations =
                variationRepository.findActiveByProductId(product.getId());

        for (ProductVariationModel variation : variations) {
            if (!variation.getId().equals(currentVariationId)
                    && variation.isPrimary()) {
                variation.setPrimary(false);
                variationRepository.save(variation);
            }
        }
    }

    private ProductResponses.ProductResponse toResponse(
            ProductModel product
    ) {
        ProductResponses.ProductResponse response =
                productMapper.toResponse(product);

        List<ProductResponses.ProductVariationResponse> variations =
                variationRepository.findActiveByProductId(product.getId())
                        .stream()
                        .map(variation -> {
                            ProductResponses.ProductVariationResponse
                                    variationResponse =
                                    variationMapper.toResponse(variation);

                            List<ProductResponses.ProductImageResponse>
                                    images =
                                    imageRepository.findActiveByVariationId(
                                                    variation.getId()
                                            )
                                            .stream()
                                            .map(imageMapper::toResponse)
                                            .toList();

                            return new ProductResponses.ProductVariationResponse(
                                    variationResponse.id(),
                                    variationResponse.price(),
                                    variationResponse.weight(),
                                    variationResponse.length(),
                                    variationResponse.width(),
                                    variationResponse.height(),
                                    variationResponse.sku(),
                                    variationResponse.stock(),
                                    variationResponse.color(),
                                    variationResponse.size(),
                                    variationResponse.primary(),
                                    images,
                                    variationResponse.createdAt(),
                                    variationResponse.updatedAt()
                            );
                        })
                        .toList();

        return new ProductResponses.ProductResponse(
                response.id(),
                response.storeId(),
                response.name(),
                response.description(),
                variations,
                response.createdAt(),
                response.updatedAt()
        );
    }

    private void enqueueObjectDeletion(
            String objectKey,
            UUID productId
    ) {
        if (objectKey == null || objectKey.isBlank()) {
            return;
        }

        StorageObjectDeletionPayload payload =
                new StorageObjectDeletionPayload(objectKey);

        outboxManagement.enqueue(
                "PRODUCT",
                productId,
                StorageObjectDeletionHandler.EVENT_TYPE,
                payload
        );
    }
}
