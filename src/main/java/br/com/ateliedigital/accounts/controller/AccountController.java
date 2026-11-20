package br.com.ateliedigital.accounts.controller;

import br.com.ateliedigital.accounts.dto.AccountRequests.CompleteAccountRequest;
import br.com.ateliedigital.accounts.dto.AccountRequests.CreateAccountRequest;
import br.com.ateliedigital.accounts.dto.AccountRequests.UpdateAccountRequest;
import br.com.ateliedigital.accounts.dto.AccountResponses.AccountResponse;
import br.com.ateliedigital.accounts.mapper.AccountMapper;
import br.com.ateliedigital.accounts.service.AccountService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.Map;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {
    private final AccountService accountService;
    private final AccountMapper mapper;

    public AccountController(AccountService accountService, AccountMapper mapper) {
        this.accountService = accountService;
        this.mapper = mapper;
    }

    @PostMapping
    public ResponseEntity<AccountResponse> create(@Valid @RequestBody CreateAccountRequest request) {
        AccountResponse response = accountService.create(request);

        URI uri = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.id())
                .toUri();

        return ResponseEntity.created(uri).body(response);
    }

    @GetMapping("/me")
    @PreAuthorize("hasAnyRole('CLIENT', 'ARTISAN', 'ADMIN')")
    public ResponseEntity<AccountResponse> getCurrent(JwtAuthenticationToken authentication) {
        return ResponseEntity.ok(accountService.getCurrentResponse(authentication));
    }

    @PutMapping("/me/complete")
    @PreAuthorize("hasAnyRole('CLIENT', 'ARTISAN')")
    public ResponseEntity<AccountResponse> completeCurrent(
            JwtAuthenticationToken authentication,
            @Valid @RequestBody CompleteAccountRequest request
    ) {
        AccountResponse response = accountService.completeCurrent(authentication, request);

        return ResponseEntity.ok(response);
    }

    @PatchMapping("/me")
    @PreAuthorize("hasAnyRole('CLIENT', 'ARTISAN')")
    public ResponseEntity<AccountResponse> updateCurrent(
            JwtAuthenticationToken authentication,
            @Valid @RequestBody UpdateAccountRequest request
    ) {
        AccountResponse response = accountService.updateCurrent(authentication, request);

        return ResponseEntity.ok(response);
    }

}
