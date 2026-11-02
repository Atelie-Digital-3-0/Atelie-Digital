package br.com.ateliedigital.accounts.service;

import br.com.ateliedigital.accounts.dto.AccountRequests.AddressRequest;
import br.com.ateliedigital.accounts.mapper.AccountMapper;
import br.com.ateliedigital.accounts.model.Account;
import br.com.ateliedigital.accounts.model.Address;
import br.com.ateliedigital.accounts.repository.AddressRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class AddressService {
    private final AddressRepository addressRepository;
    private final AccountService accountService;
    private final AccountMapper mapper;

    public AddressService(AddressRepository addressRepository, AccountService accountService,
                          AccountMapper mapper) {
        this.addressRepository = addressRepository;
        this.accountService = accountService;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<Address> list(JwtAuthenticationToken authentication) {
        Account account = accountService.getCurrent(authentication);
        return addressRepository.findAllByAccountId(account.getId());
    }

    @Transactional
    public Address create(JwtAuthenticationToken authentication, AddressRequest request) {
        Account account = accountService.getCurrent(authentication);
        Address address = mapper.toAddress(request);
        address.setAccount(account);
        if (request.main()) markOnlyMain(account.getId(), null);
        return addressRepository.save(address);
    }

    @Transactional
    public Address update(JwtAuthenticationToken authentication, UUID addressId, AddressRequest request) {
        Account account = accountService.getCurrent(authentication);
        Address address = addressRepository.findByIdAndAccountId(addressId, account.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Address not found"));
        if (request.main()) markOnlyMain(account.getId(), addressId);
        address.setCep(request.cep());
        address.setZipCode(request.zipCode());
        address.setStreet(request.street());
        address.setNumber(request.number());
        address.setComplement(request.complement());
        address.setNeighborhood(request.neighborhood());
        address.setCity(request.city());
        address.setState(request.state());
        address.setMain(request.main());
        return addressRepository.save(address);
    }

    @Transactional
    public void delete(JwtAuthenticationToken authentication, UUID addressId) {
        Account account = accountService.getCurrent(authentication);
        Address address = addressRepository.findByIdAndAccountId(addressId, account.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Address not found"));
        addressRepository.delete(address);
    }

    private void markOnlyMain(UUID accountId, UUID exceptId) {
        addressRepository.findAllByAccountId(accountId).stream()
                .filter(address -> exceptId == null || !address.getId().equals(exceptId))
                .forEach(address -> address.setMain(false));
    }
}
