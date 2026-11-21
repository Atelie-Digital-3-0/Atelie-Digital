package br.com.ateliedigital.store.controller;

import br.com.ateliedigital.accounts.service.AccountService;
import br.com.ateliedigital.store.dto.requests.StoreRequests.CreateStoreRequest;
import br.com.ateliedigital.store.dto.requests.StoreRequests.UpdateStoreRequest;
import br.com.ateliedigital.store.dto.responses.StoreResponses.StorePublicResponse;
import br.com.ateliedigital.store.service.StoreService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
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

    @PostMapping
    @PreAuthorize("hasRole('ARTISAN')")
    public ResponseEntity<StorePublicResponse> create(
            JwtAuthenticationToken authentication,
            @Valid @RequestBody CreateStoreRequest request
    ) {
        UUID artisanId = AccountService.keycloakId(authentication);

        StorePublicResponse response =
                storeService.createStore(artisanId, request);

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
        UUID artisanId = AccountService.keycloakId(authentication);

        return ResponseEntity.ok(storeService.getMyStore(artisanId));
    }

    @PatchMapping("/{storeId}")
    @PreAuthorize("hasRole('ARTISAN')")
    public ResponseEntity<StorePublicResponse> update(
            JwtAuthenticationToken authentication,
            @PathVariable UUID storeId,
            @Valid @RequestBody UpdateStoreRequest request
    ) {
        UUID artisanId = AccountService.keycloakId(authentication);

        StorePublicResponse response =
                storeService.updateStore(storeId, artisanId, request);

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{storeId}")
    @PreAuthorize("hasRole('ARTISAN')")
    public ResponseEntity<Void> delete(
            JwtAuthenticationToken authentication,
            @PathVariable UUID storeId
    ) {
        UUID artisanId = AccountService.keycloakId(authentication);

        storeService.deleteStore(storeId, artisanId);

        return ResponseEntity.noContent().build();
    }
}
