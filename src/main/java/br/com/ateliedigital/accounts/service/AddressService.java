package br.com.ateliedigital.accounts.service;

import br.com.ateliedigital.accounts.dto.AddressRequests.AddressRequest;
import br.com.ateliedigital.accounts.dto.AddressResponses.AddressResponse;
import br.com.ateliedigital.accounts.mapper.AddressMapper;
import br.com.ateliedigital.accounts.model.Account;
import br.com.ateliedigital.accounts.model.Address;
import br.com.ateliedigital.accounts.repository.AddressRepository;
import br.com.ateliedigital.exceptions.ResourceNotFoundException;
import br.com.ateliedigital.exceptions.RuleConflit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        Page<Address> addressesPage = addressRepository.findAllByAccountId(account.getId(), pageable);

        return addressesPage.map(mapper::toResponse);
    }

    @Transactional
    public AddressResponse create(JwtAuthenticationToken authentication, AddressRequest request) {
        Account account = accountService.getCurrent(authentication);
        Address address = mapper.toEntity(request);
        address.setAccount(account);
        boolean duplicated = addressRepository.findAllByAccountId(account.getId()).stream()
                .anyMatch(address::sameLocationAs);
        if (duplicated) {
            throw new RuleConflit("Address is already registered");
        }
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
                .orElseThrow(() -> new ResourceNotFoundException("Address not found"));
        Address requested = mapper.toEntity(request);
        boolean duplicated = addressRepository.findAllByAccountId(account.getId()).stream()
                .anyMatch(other -> !other.getId().equals(addressId) && other.sameLocationAs(requested));
        if (duplicated) {
            throw new RuleConflit("Address is already registered");
        }
        if (request.main()) markOnlyMain(account.getId(), addressId);

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
                .orElseThrow(() -> new ResourceNotFoundException("Address not found"));
        boolean wasMain = address.isMain();
        addressRepository.delete(address);
        addressRepository.flush();
        if (wasMain) {
            // A conta com enderecos sempre mantem um principal: promove o mais antigo que restou.
            addressRepository.findFirstByAccountIdOrderByCreatedAtAscIdAsc(account.getId())
                    .ifPresent(next -> next.setMain(true));
        }
    }

    private void markOnlyMain(UUID accountId, UUID exceptId) {
        addressRepository.findAllByAccountId(accountId).stream()
                .filter(address -> address.isMain() && (exceptId == null || !address.getId().equals(exceptId)))
                .forEach(address -> address.setMain(false));
        // O indice unico parcial (uk_addresses_one_main_per_account) e checado a cada comando e o Hibernate executa
        // INSERTs antes de UPDATEs: persiste a remocao do status ANTES de gravar o novo principal.
        addressRepository.flush();
    }
}
