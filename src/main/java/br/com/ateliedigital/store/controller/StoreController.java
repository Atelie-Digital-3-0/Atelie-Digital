package br.com.ateliedigital.store.controller;

import br.com.ateliedigital.accounts.AccountManagement;
import br.com.ateliedigital.store.dto.requests.StoreRequests.CreateStoreRequest;
import br.com.ateliedigital.store.dto.requests.StoreRequests.UpdateStoreRequest;
import br.com.ateliedigital.store.dto.responses.StoreResponses.StorePublicResponse;
import br.com.ateliedigital.store.service.StoreService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;


@RestController
@RequestMapping("/api/stores")
public class StoreController {

    private final StoreService storeService;

    public StoreController(StoreService storeService) {
        this.storeService = storeService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ARTISAN')")
    public ResponseEntity<StorePublicResponse> create(
            JwtAuthenticationToken authentication,
            @Valid @RequestPart("request") CreateStoreRequest request,
            @RequestPart("image") MultipartFile image,
            @RequestPart("banner") MultipartFile banner
    ) {
        UUID artisanId = AccountManagement.keycloakId(authentication);

        StorePublicResponse response =
                storeService.createStore(
                        artisanId,
                        request,
                        image,
                        banner
                );

        URI uri = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.id())
                .toUri();

        return ResponseEntity.created(uri).body(response);
    }

    @GetMapping("/{storeId}")
    public ResponseEntity<StorePublicResponse> getById(
            @PathVariable UUID storeId
    ) {
        return ResponseEntity.ok(storeService.getStoreById(storeId));
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('ARTISAN')")
    public ResponseEntity<StorePublicResponse> getMyStore(
            JwtAuthenticationToken authentication
    ) {
        UUID artisanId = AccountManagement.keycloakId(authentication);

        return ResponseEntity.ok(storeService.getMyStore(artisanId));
    }

    @PatchMapping(
            value = "/{storeId}",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    @PreAuthorize("hasRole('ARTISAN')")
    public ResponseEntity<StorePublicResponse> update(
            JwtAuthenticationToken authentication,
            @PathVariable UUID storeId,
            @Valid @RequestPart("request") UpdateStoreRequest request,
            @RequestPart(value = "image", required = false) MultipartFile image,
            @RequestPart(value = "banner", required = false) MultipartFile banner
    ) {
        UUID artisanId = AccountManagement.keycloakId(authentication);

        StorePublicResponse response =
                storeService.updateStore(
                        storeId,
                        artisanId,
                        request,
                        image,
                        banner
                );

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{storeId}")
    @PreAuthorize("hasRole('ARTISAN')")
    public ResponseEntity<Void> delete(
            JwtAuthenticationToken authentication,
            @PathVariable UUID storeId
    ) {
        UUID artisanId = AccountManagement.keycloakId(authentication);

        storeService.deleteStore(storeId, artisanId);

        return ResponseEntity.noContent().build();
    }
}
