package br.com.ateliedigital.accounts.service;

import br.com.ateliedigital.accounts.dto.AddressRequests.AddressRequest;
import br.com.ateliedigital.accounts.dto.AddressResponses.AddressResponse;
import br.com.ateliedigital.accounts.mapper.AddressMapper;
import br.com.ateliedigital.accounts.model.*;
import br.com.ateliedigital.accounts.repository.AddressRepository;
import br.com.ateliedigital.exceptions.ResourceNotFoundException;
import br.com.ateliedigital.exceptions.RuleConflit;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AddressServiceTest {

    @Mock
    private AddressRepository addressRepository;
    @Mock
    private AccountService accountService;
    @Mock
    private AddressMapper mapper;

    @InjectMocks
    private AddressService service;

    private JwtAuthenticationToken authentication;
    private Account account;

    @BeforeEach
    void setUp() {
        UUID keycloakId = UUID.randomUUID();
        Jwt jwt = new Jwt("token", Instant.now(), Instant.now().plusSeconds(60),
                Map.of("alg", "none"), Map.of("sub", keycloakId.toString()));
        authentication = new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_CLIENT")));

        account = new Account();
        account.setId(UUID.randomUUID());
        account.setKeycloakId(keycloakId);
    }

    @AfterEach
    void tearDown() {
        // Os mocks sao recriados a cada teste pelo MockitoExtension; nada a limpar manualmente.
    }

    // ------------------------------------------------------------------ list

    @Test
    @DisplayName("Addresses listed with success")
    void list() {
        Pageable pageable = PageRequest.of(0, 10);
        Address address = address(UUID.randomUUID(), "Rua A", true);
        AddressResponse response = responseFor(address);

        when(accountService.getCurrent(authentication)).thenReturn(account);
        when(addressRepository.findAllByAccountId(account.getId(), pageable))
                .thenReturn(new PageImpl<>(List.of(address), pageable, 1));
        when(mapper.toResponse(address)).thenReturn(response);

        Page<AddressResponse> result = service.list(authentication, pageable);

        assertEquals(List.of(response), result.getContent());
        assertEquals(1, result.getTotalElements());
    }

    @Test
    @DisplayName("Addresses listed with success when account has no address")
    void listEmpty() {
        Pageable pageable = PageRequest.of(0, 10);

        when(accountService.getCurrent(authentication)).thenReturn(account);
        when(addressRepository.findAllByAccountId(account.getId(), pageable))
                .thenReturn(Page.empty(pageable));

        Page<AddressResponse> result = service.list(authentication, pageable);

        assertTrue(result.isEmpty());
        verifyNoInteractions(mapper);
    }

    @Test
    @DisplayName("Addresses not listed because account does not exist")
    void listFailsWhenAccountDoesNotExist() {
        when(accountService.getCurrent(authentication)).thenThrow(new ResourceNotFoundException("Account not found"));

        assertThrows(ResourceNotFoundException.class, () -> service.list(authentication, PageRequest.of(0, 10)));

        verifyNoInteractions(addressRepository, mapper);
    }

    // ---------------------------------------------------------------- create

    @Test
    @DisplayName("Address created with success")
    void create() {
        AddressRequest request = request("Rua A", false);
        Address address = address(null, "Rua A", false);
        Address saved = address(UUID.randomUUID(), "Rua A", false);
        AddressResponse response = responseFor(saved);

        when(accountService.getCurrent(authentication)).thenReturn(account);
        when(mapper.toEntity(request)).thenReturn(address);
        when(addressRepository.findAllByAccountId(account.getId())).thenReturn(List.of());
        when(addressRepository.save(address)).thenReturn(saved);
        when(mapper.toResponse(saved)).thenReturn(response);

        AddressResponse result = service.create(authentication, request);

        assertSame(response, result);
        assertSame(account, address.getAccount());
        verify(addressRepository, never()).flush();
    }

    @Test
    @DisplayName("Address created with success as main demoting current main")
    void createMainDemotesCurrentMain() {
        AddressRequest request = request("Rua B", true);
        Address address = address(null, "Rua B", true);
        Address currentMain = address(UUID.randomUUID(), "Rua A", true);
        Address saved = address(UUID.randomUUID(), "Rua B", true);
        AddressResponse response = responseFor(saved);

        when(accountService.getCurrent(authentication)).thenReturn(account);
        when(mapper.toEntity(request)).thenReturn(address);
        when(addressRepository.findAllByAccountId(account.getId())).thenReturn(List.of(currentMain));
        when(addressRepository.save(address)).thenReturn(saved);
        when(mapper.toResponse(saved)).thenReturn(response);

        AddressResponse result = service.create(authentication, request);

        assertSame(response, result);
        assertFalse(currentMain.isMain());
        verify(addressRepository).flush();
    }

    @Test
    @DisplayName("Address not created because address is registered")
    void createFailsWhenAddressIsAlreadyRegistered() {
        AddressRequest request = request("Rua A", false);
        Address address = address(null, "Rua A", false);
        Address existing = address(UUID.randomUUID(), " rua a ", false);

        when(accountService.getCurrent(authentication)).thenReturn(account);
        when(mapper.toEntity(request)).thenReturn(address);
        when(addressRepository.findAllByAccountId(account.getId())).thenReturn(List.of(existing));

        RuleConflit exception = assertThrows(RuleConflit.class, () -> service.create(authentication, request));

        assertEquals("Address is already registered", exception.getMessage());
        verify(addressRepository, never()).save(any());
        verify(addressRepository, never()).flush();
    }

    @Test
    @DisplayName("Address not created because account does not exist")
    void createFailsWhenAccountDoesNotExist() {
        when(accountService.getCurrent(authentication)).thenThrow(new ResourceNotFoundException("Account not found"));

        assertThrows(ResourceNotFoundException.class,
                () -> service.create(authentication, request("Rua A", false)));

        verifyNoInteractions(addressRepository, mapper);
    }

    // ---------------------------------------------------------------- update

    @Test
    @DisplayName("Address updated with success")
    void update() {
        UUID addressId = UUID.randomUUID();
        Address address = address(addressId, "Rua A", false);
        AddressRequest request = request("Rua Nova", false);
        Address requested = address(null, "Rua Nova", false);
        AddressResponse response = responseFor(address);

        when(accountService.getCurrent(authentication)).thenReturn(account);
        when(addressRepository.findByIdAndAccountId(addressId, account.getId())).thenReturn(Optional.of(address));
        when(mapper.toEntity(request)).thenReturn(requested);
        when(addressRepository.findAllByAccountId(account.getId())).thenReturn(List.of(address));
        when(addressRepository.save(address)).thenReturn(address);
        when(mapper.toResponse(address)).thenReturn(response);

        AddressResponse result = service.update(authentication, addressId, request);

        assertSame(response, result);
        assertEquals("Rua Nova", address.getStreet());
        assertEquals("20", address.getNumber());
        assertEquals("Apto 1", address.getComplement());
        assertEquals("Bairro", address.getNeighborhood());
        assertEquals("Campinas", address.getCity());
        assertEquals("SP", address.getState());
        assertEquals("13000000", address.getZipCode());
        assertFalse(address.isMain());
        verify(addressRepository, never()).flush();
    }

    @Test
    @DisplayName("Address updated with success as main demoting other main")
    void updateMainDemotesOtherMain() {
        UUID addressId = UUID.randomUUID();
        Address address = address(addressId, "Rua A", false);
        Address otherMain = address(UUID.randomUUID(), "Rua B", true);
        AddressRequest request = request("Rua A", true);
        Address requested = address(null, "Rua A", true);

        when(accountService.getCurrent(authentication)).thenReturn(account);
        when(addressRepository.findByIdAndAccountId(addressId, account.getId())).thenReturn(Optional.of(address));
        when(mapper.toEntity(request)).thenReturn(requested);
        when(addressRepository.findAllByAccountId(account.getId())).thenReturn(List.of(address, otherMain));
        when(addressRepository.save(address)).thenReturn(address);
        when(mapper.toResponse(address)).thenReturn(responseFor(address));

        service.update(authentication, addressId, request);

        assertTrue(address.isMain());
        assertFalse(otherMain.isMain());
        verify(addressRepository).flush();
    }

    @Test
    @DisplayName("Address not updated because address does not exist")
    void updateFailsWhenAddressDoesNotExist() {
        UUID addressId = UUID.randomUUID();

        when(accountService.getCurrent(authentication)).thenReturn(account);
        when(addressRepository.findByIdAndAccountId(addressId, account.getId())).thenReturn(Optional.empty());

        ResourceNotFoundException exception = assertThrows(ResourceNotFoundException.class,
                () -> service.update(authentication, addressId, request("Rua A", false)));

        assertEquals("Address not found", exception.getMessage());
        verify(addressRepository, never()).save(any());
        verifyNoInteractions(mapper);
    }

    @Test
    @DisplayName("Address not updated because address is registered")
    void updateFailsWhenAddressIsAlreadyRegistered() {
        UUID addressId = UUID.randomUUID();
        Address address = address(addressId, "Rua A", false);
        Address other = address(UUID.randomUUID(), "Rua B", false);
        AddressRequest request = request("Rua B", false);
        Address requested = address(null, "Rua B", false);

        when(accountService.getCurrent(authentication)).thenReturn(account);
        when(addressRepository.findByIdAndAccountId(addressId, account.getId())).thenReturn(Optional.of(address));
        when(mapper.toEntity(request)).thenReturn(requested);
        when(addressRepository.findAllByAccountId(account.getId())).thenReturn(List.of(address, other));

        RuleConflit exception = assertThrows(RuleConflit.class,
                () -> service.update(authentication, addressId, request));

        assertEquals("Address is already registered", exception.getMessage());
        assertEquals("Rua A", address.getStreet());
        verify(addressRepository, never()).save(any());
    }

    @Test
    @DisplayName("Address updated with success keeping its own location")
    void updateKeepingSameLocationIsNotADuplicate() {
        UUID addressId = UUID.randomUUID();
        Address address = address(addressId, "Rua A", false);
        AddressRequest request = request("Rua A", false);
        Address requested = address(null, "Rua A", false);

        when(accountService.getCurrent(authentication)).thenReturn(account);
        when(addressRepository.findByIdAndAccountId(addressId, account.getId())).thenReturn(Optional.of(address));
        when(mapper.toEntity(request)).thenReturn(requested);
        when(addressRepository.findAllByAccountId(account.getId())).thenReturn(List.of(address));
        when(addressRepository.save(address)).thenReturn(address);
        when(mapper.toResponse(address)).thenReturn(responseFor(address));

        assertDoesNotThrow(() -> service.update(authentication, addressId, request));

        verify(addressRepository).save(address);
    }

    // ---------------------------------------------------------------- delete

    @Test
    @DisplayName("Address deleted with success")
    void delete() {
        UUID addressId = UUID.randomUUID();
        Address address = address(addressId, "Rua A", false);

        when(accountService.getCurrent(authentication)).thenReturn(account);
        when(addressRepository.findByIdAndAccountId(addressId, account.getId())).thenReturn(Optional.of(address));

        service.delete(authentication, addressId);

        verify(addressRepository).delete(address);
        verify(addressRepository).flush();
        verify(addressRepository, never()).findFirstByAccountIdOrderByCreatedAtAscIdAsc(any());
    }

    @Test
    @DisplayName("Address deleted with success promoting the oldest address as main")
    void deleteMainPromotesOldestAddress() {
        UUID addressId = UUID.randomUUID();
        Address address = address(addressId, "Rua A", true);
        Address next = address(UUID.randomUUID(), "Rua B", false);

        when(accountService.getCurrent(authentication)).thenReturn(account);
        when(addressRepository.findByIdAndAccountId(addressId, account.getId())).thenReturn(Optional.of(address));
        when(addressRepository.findFirstByAccountIdOrderByCreatedAtAscIdAsc(account.getId()))
                .thenReturn(Optional.of(next));

        service.delete(authentication, addressId);

        verify(addressRepository).delete(address);
        verify(addressRepository).flush();
        assertTrue(next.isMain());
    }

    @Test
    @DisplayName("Address deleted with success when it was the last address")
    void deleteLastMainAddress() {
        UUID addressId = UUID.randomUUID();
        Address address = address(addressId, "Rua A", true);

        when(accountService.getCurrent(authentication)).thenReturn(account);
        when(addressRepository.findByIdAndAccountId(addressId, account.getId())).thenReturn(Optional.of(address));
        when(addressRepository.findFirstByAccountIdOrderByCreatedAtAscIdAsc(account.getId()))
                .thenReturn(Optional.empty());

        assertDoesNotThrow(() -> service.delete(authentication, addressId));

        verify(addressRepository).delete(address);
        verify(addressRepository).flush();
    }

    @Test
    @DisplayName("Address not deleted because address does not exist")
    void deleteFailsWhenAddressDoesNotExist() {
        UUID addressId = UUID.randomUUID();

        when(accountService.getCurrent(authentication)).thenReturn(account);
        when(addressRepository.findByIdAndAccountId(addressId, account.getId())).thenReturn(Optional.empty());

        ResourceNotFoundException exception =
                assertThrows(ResourceNotFoundException.class, () -> service.delete(authentication, addressId));

        assertEquals("Address not found", exception.getMessage());
        verify(addressRepository, never()).delete(any());
        verify(addressRepository, never()).flush();
    }

    // --------------------------------------------------------------- helpers

    private static AddressRequest request(String street, boolean main) {
        return new AddressRequest("13000000", street, "20", "Apto 1", "Bairro", "Campinas", "SP", main);
    }

    private static Address address(UUID id, String street, boolean main) {
        Address address = new Address();
        address.setId(id);
        address.setZipCode("13000000");
        address.setStreet(street);
        address.setNumber("20");
        address.setComplement("Apto 1");
        address.setNeighborhood("Bairro");
        address.setCity("Campinas");
        address.setState("SP");
        address.setMain(main);
        return address;
    }

    private static AddressResponse responseFor(Address address) {
        return new AddressResponse(address.getId(), address.getZipCode(), address.getStreet(), address.getNumber(),
                address.getComplement(), address.getNeighborhood(), address.getCity(), address.getState(),
                address.isMain());
    }
}
