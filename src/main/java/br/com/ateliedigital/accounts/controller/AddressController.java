package br.com.ateliedigital.accounts.controller;

import br.com.ateliedigital.accounts.dto.AddressRequests.AddressRequest;
import br.com.ateliedigital.accounts.dto.AddressResponses.AddressResponse;
import br.com.ateliedigital.accounts.dto.AddressResponses.ZipCodeResponse;
import br.com.ateliedigital.accounts.integration.viacep.ViaCepClient;
import br.com.ateliedigital.accounts.mapper.AddressMapper;
import br.com.ateliedigital.accounts.service.AddressService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/accounts/me/addresses")
@PreAuthorize("hasAnyRole('CLIENT', 'ARTISAN')")
public class AddressController {
    private final AddressService addressService;
    private final AddressMapper mapper;
    private final ViaCepClient viaCepClient;

    public AddressController(
            AddressService addressService,
            AddressMapper mapper,
            ViaCepClient viaCepClient
    ) {
        this.addressService = addressService;
        this.mapper = mapper;
        this.viaCepClient = viaCepClient;
    }

    @PostMapping
    public ResponseEntity<AddressResponse> create(JwtAuthenticationToken authentication,
                                                  @Valid @RequestBody AddressRequest request) {
        AddressResponse response = addressService.create(authentication, request);

        URI uri = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.id())
                .toUri();

        return ResponseEntity.created(uri).body(response);
    }

    @GetMapping
    public List<AddressResponse> list(JwtAuthenticationToken authentication) {
        return addressService.list(authentication).stream().map(mapper::toResponse).toList();
    }

    @PutMapping("/{addressId}")
    public ResponseEntity<AddressResponse> update(
            JwtAuthenticationToken authentication,
            @PathVariable UUID addressId,
            @Valid @RequestBody AddressRequest request
    ) {
        AddressResponse response = addressService.update(authentication, addressId, request);

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{addressId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> delete(JwtAuthenticationToken authentication, @PathVariable UUID addressId) {
        addressService.delete(authentication, addressId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/cep/{zipCode}")
    public ZipCodeResponse lookupCep(@PathVariable String zipCode) {
        return viaCepClient.lookup(zipCode);
    }
}
