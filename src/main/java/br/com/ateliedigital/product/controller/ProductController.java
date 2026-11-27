
package br.com.ateliedigital.product.controller;

import br.com.ateliedigital.accounts.service.AccountService;
import br.com.ateliedigital.product.dto.requests.ProductRequests;
import br.com.ateliedigital.product.dto.responses.ProductResponses;
import br.com.ateliedigital.product.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
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
        UUID artisanId = AccountService.keycloakId(authentication);

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
        UUID artisanId = AccountService.keycloakId(authentication);

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
        UUID artisanId = AccountService.keycloakId(authentication);

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
        UUID artisanId = AccountService.keycloakId(authentication);

        productService.deleteProduct(artisanId, productId);

        return ResponseEntity.noContent().build();
    }
}
