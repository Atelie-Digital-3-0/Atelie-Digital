package br.com.ateliedigital.accounts.controller;

import br.com.ateliedigital.accounts.dto.AccountRequests.AddressRequest;
import br.com.ateliedigital.accounts.dto.AccountResponses.AddressResponse;
import br.com.ateliedigital.accounts.dto.AccountResponses.CepResponse;
import br.com.ateliedigital.accounts.integration.viacep.ViaCepClient;
import br.com.ateliedigital.accounts.mapper.AccountMapper;
import br.com.ateliedigital.accounts.service.AddressService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/accounts/me/addresses")
@PreAuthorize("hasAnyRole('CLIENT', 'ARTISAN', 'ADMIN')")
public class AddressController {
    private final AddressService addressService;
    private final AccountMapper mapper;
    private final ViaCepClient viaCepClient;

    public AddressController(AddressService addressService, AccountMapper mapper, ViaCepClient viaCepClient) {
        this.addressService = addressService;
        this.mapper = mapper;
        this.viaCepClient = viaCepClient;
    }

    @GetMapping
    public List<AddressResponse> list(JwtAuthenticationToken authentication) {
        return addressService.list(authentication).stream().map(mapper::toResponse).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AddressResponse create(JwtAuthenticationToken authentication,
                                  @Valid @RequestBody AddressRequest request) {
        return mapper.toResponse(addressService.create(authentication, request));
    }

    @PutMapping("/{addressId}")
    public AddressResponse update(JwtAuthenticationToken authentication, @PathVariable UUID addressId,
                                  @Valid @RequestBody AddressRequest request) {
        return mapper.toResponse(addressService.update(authentication, addressId, request));
    }

    @DeleteMapping("/{addressId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(JwtAuthenticationToken authentication, @PathVariable UUID addressId) {
        addressService.delete(authentication, addressId);
    }

    @GetMapping("/cep/{cep}")
    public CepResponse lookupCep(@PathVariable String cep) {
        return viaCepClient.lookup(cep);
    }
}
