package br.com.ateliedigital.accounts.service;

import br.com.ateliedigital.accounts.dto.AddressRequests.AddressRequest;
import br.com.ateliedigital.accounts.dto.AddressResponses.AddressResponse;
import br.com.ateliedigital.accounts.mapper.AddressMapper;
import br.com.ateliedigital.accounts.model.Account;
import br.com.ateliedigital.accounts.model.Address;
import br.com.ateliedigital.accounts.repository.AddressRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
    private final AddressMapper mapper;

    public AddressService(AddressRepository addressRepository, AccountService accountService,
                          AddressMapper mapper) {
        this.addressRepository = addressRepository;
        this.accountService = accountService;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public Page<AddressResponse> list(JwtAuthenticationToken authentication, Pageable pageable) {
        Account account = accountService.getCurrent(authentication);
        Page<Address> addressesPage = addressRepository.findAllByAccountId(account.getId());
        if(addressesPage.isEmpty()){
            throw new RuntimeException("Nenhum setor localizado");
        }

        return addressesPage.map(mapper::toResponse);
    }

    @Transactional
    public AddressResponse create(JwtAuthenticationToken authentication, AddressRequest request) {
        Account account = accountService.getCurrent(authentication);
        Address address = mapper.toEntity(request);
        address.setAccount(account);
        if (request.main()) markOnlyMain(account.getId(), null);

        return mapper.toResponse(addressRepository.save(address));
    }

    @Transactional
    public AddressResponse update(
            JwtAuthenticationToken authentication,
            UUID addressId,
            AddressRequest request
    ) {
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

        return mapper.toResponse(addressRepository.save(address));
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
