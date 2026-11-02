package br.com.ateliedigital.accounts.controller;

import br.com.ateliedigital.accounts.dto.AccountRequests.CreateAccountRequest;
import br.com.ateliedigital.accounts.dto.AccountRequests.UpdateAccountRequest;
import br.com.ateliedigital.accounts.dto.AccountResponses.AccountResponse;
import br.com.ateliedigital.accounts.mapper.AccountMapper;
import br.com.ateliedigital.accounts.service.AccountService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

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
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse create(@Valid @RequestBody CreateAccountRequest request) {
        return mapper.toResponse(accountService.create(request));
    }

    @GetMapping("/me")
    @PreAuthorize("hasAnyRole('CLIENT', 'ARTISAN', 'ADMIN')")
    public AccountResponse getCurrent(JwtAuthenticationToken authentication) {
        return mapper.toResponse(accountService.getCurrent(authentication));
    }

    @PutMapping("/me")
    @PreAuthorize("hasAnyRole('CLIENT', 'ARTISAN', 'ADMIN')")
    public AccountResponse updateCurrent(JwtAuthenticationToken authentication,
                                         @Valid @RequestBody UpdateAccountRequest request) {
        return mapper.toResponse(accountService.updateCurrent(authentication, request));
    }

    @GetMapping("/me/mercado-pago-token")
    @PreAuthorize("hasAnyRole('ARTISAN', 'ADMIN')")
    public Map<String, String> mercadoPagoToken(JwtAuthenticationToken authentication) {
        return Map.of("access_token", accountService.mercadoPagoToken(authentication));
    }
}
