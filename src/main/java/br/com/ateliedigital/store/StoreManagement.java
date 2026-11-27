package br.com.ateliedigital.store;

import br.com.ateliedigital.store.service.StoreService;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class StoreManagement {

    private final StoreService storeService;

    public StoreManagement(StoreService storeService) {
        this.storeService = storeService;
    }

    public void validateActiveStore(UUID storeId) {
        storeService.validateActiveStore(storeId);
    }

    public void validateStoreOwnership(UUID storeId, UUID artisanId) {
        storeService.validateStoreOwnership(storeId, artisanId);
    }
}