package br.com.ateliedigital.store.controller;

import br.com.ateliedigital.store.dto.responses.StoreAddressResponses.ZipCodeLookupResponse;
import br.com.ateliedigital.store.service.StoreAddressService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/stores/addresses")
public class StoreAddressController {

    private final StoreAddressService addressService;

    public StoreAddressController(StoreAddressService addressService) {
        this.addressService = addressService;
    }

    @GetMapping("/zip-code/{zipCode}")
    public ResponseEntity<ZipCodeLookupResponse> findAddressByZipCode(
            @PathVariable String zipCode) {

        ZipCodeLookupResponse response =
                addressService.findAddressByZipCode(zipCode);

        return ResponseEntity.ok(response);
    }
}
