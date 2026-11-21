package br.com.ateliedigital.store.service;

import br.com.ateliedigital.exceptions.ResourceNotFoundException;
import br.com.ateliedigital.store.dto.requests.StoreRequests;
import br.com.ateliedigital.store.dto.responses.StoreResponses;
import br.com.ateliedigital.store.event.StoreCreatedHandler;
import br.com.ateliedigital.store.event.StoreCreatedPayload;
import br.com.ateliedigital.store.mapper.StoreAddressMapper;
import br.com.ateliedigital.store.mapper.StoreMapper;
import br.com.ateliedigital.store.model.AddressModel;
import br.com.ateliedigital.store.model.CategoryModel;
import br.com.ateliedigital.store.model.StoreModel;
import br.com.ateliedigital.store.repository.CategoryRepository;
import br.com.ateliedigital.store.repository.StoreRepository;
import br.com.ateliedigital.outbox.OutboxManagement;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

import br.com.ateliedigital.exceptions.VioletedRule;

@Service
public class StoreService {
    private final StoreRepository storeRepository;
    private final CategoryRepository categoryRepository;
    private final StoreMapper storeMapper;
    private final StoreAddressMapper addressMapper;
    private final StoreAddressService addressService;
    private final OutboxManagement outboxManagement;

    public StoreService(
            StoreRepository storeRepository,
            CategoryRepository categoryRepository,
            StoreMapper storeMapper,
            StoreAddressMapper addressMapper,
            StoreAddressService addressService,
            OutboxManagement outboxManagement
    ) {
        this.storeRepository = storeRepository;
        this.categoryRepository = categoryRepository;
        this.storeMapper = storeMapper;
        this.addressMapper = addressMapper;
        this.addressService = addressService;
        this.outboxManagement = outboxManagement;
    }

    @Transactional
    public StoreResponses.StorePublicResponse createStore(
            UUID artisanId,
            StoreRequests.CreateStoreRequest request
    ) {
        if (storeRepository.existsByArtisanIdAndDeletedAtIsNull(
                artisanId.toString()
        )) {
            throw new VioletedRule(
                    "Artisan already has an active store"
            );
        }

        CategoryModel category = categoryRepository.findById(
                request.categoryId()
        ).orElseThrow(() ->
                new ResourceNotFoundException("Store category not found")
        );

        StoreModel store = storeMapper.toEntity(request);

        store.setArtisanId(artisanId.toString());
        store.setCategory(category);

        AddressModel address = store.getAddress();

        address.setZipCode(
                addressService.validateAndFormatZipCode(
                        address.getZipCode()
                )
        );

        StoreModel savedStore = storeRepository.save(store);

        StoreCreatedPayload payload = new StoreCreatedPayload(
                savedStore.getId(),
                artisanId
        );

        outboxManagement.enqueue(
                "STORE",
                savedStore.getId(),
                StoreCreatedHandler.EVENT_TYPE,
                payload
        );

        return storeMapper.toResponse(savedStore);
    }

    @Transactional(readOnly = true)
    public StoreResponses.StorePublicResponse getStoreById(UUID storeId) {
        StoreModel store = findActiveStore(storeId);

        return storeMapper.toResponse(store);
    }

    @Transactional(readOnly = true)
    public StoreResponses.StorePublicResponse getMyStore(UUID artisanId) {
        StoreModel store = storeRepository
                .findByArtisanIdAndDeletedAtIsNull(artisanId.toString())
                .orElseThrow(() ->
                        new ResourceNotFoundException("Store not found")
                );

        return storeMapper.toResponse(store);
    }

    @Transactional
    public StoreResponses.StorePublicResponse updateStore(
            UUID storeId,
            UUID artisanId,
            StoreRequests.UpdateStoreRequest request
    ) {
        StoreModel store = findActiveStore(storeId);

        validateOwnership(store, artisanId);

        storeMapper.updateEntity(request, store);

        if (request.address() != null) {
            AddressModel address = store.getAddress();

            if (address == null) {
                throw new IllegalStateException(
                        "Store address is missing"
                );
            }

            addressMapper.updateEntity(request.address(), address);

            if (request.address().zipCode() != null) {
                address.setZipCode(
                        addressService.validateAndFormatZipCode(
                                request.address().zipCode()
                        )
                );
            }
        }

        return storeMapper.toResponse(store);
    }

    @Transactional
    public void deleteStore(UUID storeId, UUID artisanId) {
        StoreModel store = findActiveStore(storeId);

        validateOwnership(store, artisanId);

        store.setDeletedAt(LocalDateTime.now());

        storeRepository.save(store);
    }

    private StoreModel findActiveStore(UUID storeId) {
        return storeRepository.findByIdAndDeletedAtIsNull(storeId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Store not found")
                );
    }

    private void validateOwnership(
            StoreModel store,
            UUID artisanId
    ) {
        if (!store.getArtisanId().equals(artisanId.toString())) {
            throw new AccessDeniedException(
                    "You do not have permission to manage this store"
            );
        }
    }
}
