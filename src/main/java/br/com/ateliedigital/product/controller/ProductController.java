package br.com.ateliedigital.product.controller;

import br.com.ateliedigital.accounts.AccountManagement;
import br.com.ateliedigital.product.dto.requests.ProductRequests;
import br.com.ateliedigital.product.dto.responses.ProductResponses;
import br.com.ateliedigital.product.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @PostMapping("/stores/{storeId}/products")
    @PreAuthorize("hasRole('ARTISAN')")
    public ResponseEntity<ProductResponses.ProductResponse> create(
            JwtAuthenticationToken authentication,
            @PathVariable UUID storeId,
            @Valid @RequestBody ProductRequests.CreateProductRequest request
    ) {
        UUID artisanId = AccountManagement.keycloakId(authentication);

        ProductResponses.ProductResponse response =
                productService.createProduct(
                        artisanId,
                        storeId,
                        request
                );

        URI uri = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/products/{productId}")
                .buildAndExpand(response.id())
                .toUri();

        return ResponseEntity.created(uri).body(response);
    }

    @PostMapping(
            value = "/stores/{storeId}/products",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    @PreAuthorize("hasRole('ARTISAN')")
    public ResponseEntity<ProductResponses.ProductResponse> createWithImages(
            JwtAuthenticationToken authentication,
            @PathVariable UUID storeId,
            @Valid @RequestPart("request")
            ProductRequests.CreateProductRequest request,
            @RequestPart(value = "images", required = false)
            List<MultipartFile> images
    ) {
        UUID artisanId = AccountManagement.keycloakId(authentication);

        ProductResponses.ProductResponse response =
                productService.createProduct(
                        artisanId,
                        storeId,
                        request,
                        images
                );

        URI uri = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/products/{productId}")
                .buildAndExpand(response.id())
                .toUri();

        return ResponseEntity.created(uri).body(response);
    }

    @GetMapping("/products/{productId}")
    public ResponseEntity<ProductResponses.ProductResponse> getById(
            @PathVariable UUID productId
    ) {
        return ResponseEntity.ok(
                productService.getProductById(productId)
        );
    }

    @GetMapping("/stores/{storeId}/products")
    public ResponseEntity<Page<ProductResponses.ProductResponse>> getByStore(
            @PathVariable UUID storeId,
            @PageableDefault(
                    size = 20,
                    sort = "createdAt",
                    direction = Sort.Direction.DESC
            ) Pageable pageable
    ) {
        return ResponseEntity.ok(
                productService.getProductsByStore(storeId, pageable)
        );
    }

    @PatchMapping("/products/{productId}")
    @PreAuthorize("hasRole('ARTISAN')")
    public ResponseEntity<ProductResponses.ProductResponse> update(
            JwtAuthenticationToken authentication,
            @PathVariable UUID productId,
            @Valid @RequestBody ProductRequests.UpdateProductRequest request
    ) {
        UUID artisanId = AccountManagement.keycloakId(authentication);

        return ResponseEntity.ok(
                productService.updateProduct(
                        artisanId,
                        productId,
                        request
                )
        );
    }

    @PatchMapping(
            "/products/{productId}/variations/{variationId}"
    )
    @PreAuthorize("hasRole('ARTISAN')")
    public ResponseEntity<ProductResponses.ProductResponse> updateVariation(
            JwtAuthenticationToken authentication,
            @PathVariable UUID productId,
            @PathVariable UUID variationId,
            @Valid @RequestBody
            ProductRequests.UpdateProductVariationRequest request
    ) {
        UUID artisanId = AccountManagement.keycloakId(authentication);

        return ResponseEntity.ok(
                productService.updateVariation(
                        artisanId,
                        productId,
                        variationId,
                        request
                )
        );
    }

    @DeleteMapping("/products/{productId}")
    @PreAuthorize("hasRole('ARTISAN')")
    public ResponseEntity<Void> delete(
            JwtAuthenticationToken authentication,
            @PathVariable UUID productId
    ) {
        UUID artisanId = AccountManagement.keycloakId(authentication);

        productService.deleteProduct(artisanId, productId);

        return ResponseEntity.noContent().build();
    }

    @PostMapping(
            value = "/products/{productId}/images",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    @PreAuthorize("hasRole('ARTISAN')")
    public ResponseEntity<Void> uploadImage(
            JwtAuthenticationToken authentication,
            @PathVariable UUID productId,
            @RequestParam MultipartFile image
    ) {
        UUID artisanId = AccountManagement.keycloakId(authentication);

        productService.uploadImage(artisanId, productId, image);

        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/products/{productId}/images")
    @PreAuthorize("hasRole('ARTISAN')")
    public ResponseEntity<Void> deleteImage(
            JwtAuthenticationToken authentication,
            @PathVariable UUID productId,
            @RequestParam List<UUID> imageIds
    ) {
        UUID artisanId = AccountManagement.keycloakId(authentication);

        productService.deleteImage(artisanId, productId, imageIds);

        return ResponseEntity.noContent().build();
    }

    @PostMapping(
            value = "/products/{productId}/variations/{variationId}/images",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    @PreAuthorize("hasRole('ARTISAN')")
    public ResponseEntity<List<ProductResponses.ProductImageResponse>> addImages(
            JwtAuthenticationToken authentication,
            @PathVariable UUID productId,
            @PathVariable UUID variationId,
            @RequestPart("images") List<MultipartFile> images
    ) {
        UUID artisanId = AccountManagement.keycloakId(authentication);

        return ResponseEntity.ok(
                productService.addImages(
                        artisanId,
                        productId,
                        variationId,
                        images
                )
        );
    }

    @DeleteMapping(
            "/products/{productId}/variations/{variationId}/images/{imageId}"
    )
    @PreAuthorize("hasRole('ARTISAN')")
    public ResponseEntity<Void> deleteImage(
            JwtAuthenticationToken authentication,
            @PathVariable UUID productId,
            @PathVariable UUID variationId,
            @PathVariable UUID imageId
    ) {
        UUID artisanId = AccountManagement.keycloakId(authentication);

        productService.deleteImage(
                artisanId,
                productId,
                variationId,
                imageId
        );

        return ResponseEntity.noContent().build();
    }
}
