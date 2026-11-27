package com.flowerconnect.farmer.controller;

import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.farmer.dto.FarmerDepositRequest;
import com.flowerconnect.farmer.dto.FarmerDepositResponse;
import com.flowerconnect.farmer.service.FarmerDepositService;
import com.flowerconnect.repository.VendorProfileRepository;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * Vendor-entered flower deposits on farmer/vendor links (Stage 2 of the farmer
 * module).
 *
 * <p>Every route requires the {@code FLORIST} role and acts only on links
 * belonging to the authenticated florist's vendor profile, resolved from the
 * JWT subject.
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/vendor/farmer-links")
@RequiredArgsConstructor
public class FarmerDepositController {

    private final FarmerDepositService farmerDepositService;
    private final VendorProfileRepository vendorProfileRepository;

    /**
     * Records a flower deposit on the given link.
     *
     * <p>Requires an {@code ACTIVE} link owned by the caller's vendor.
     */
    @Operation(summary = "Record a flower deposit on a link",
            description = "FLORIST only. Records a flower deposit entry on the given ACTIVE farmer-vendor link. "
                    + "The link must belong to the authenticated vendor.")
    @PostMapping("/{linkId}/deposits")
    public ResponseEntity<FarmerDepositResponse> recordDeposit(Authentication authentication,
                                                               @PathVariable Long linkId,
                                                               @Valid @RequestBody FarmerDepositRequest request) {
        Long vendorId = vendorId(authentication);
        FarmerDepositResponse created = farmerDepositService.recordDeposit(vendorId, linkId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /* ------------------------------------------------------------- private */

    private Long vendorId(Authentication authentication) {
        String email = authentication.getName();
        return vendorProfileRepository.findByUserEmail(email)
                .map(VendorProfile::getId)
                .orElseThrow(() -> new IllegalStateException("Authenticated vendor not found"));
    }
}
