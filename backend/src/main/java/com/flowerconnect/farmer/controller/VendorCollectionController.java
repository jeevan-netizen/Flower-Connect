package com.flowerconnect.farmer.controller;

import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.farmer.domain.FarmerPayment;
import com.flowerconnect.farmer.dto.FarmerCollectionResponse;
import com.flowerconnect.farmer.dto.PassbookResponse;
import com.flowerconnect.farmer.service.FarmerCollectionService;
import com.flowerconnect.repository.VendorProfileRepository;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Vendor-side collection decisions and passbook (Stage 3 of the farmer module).
 *
 * <p>Every route requires the {@code FLORIST} role and acts only on links
 * belonging to the authenticated florist's vendor profile, resolved from the
 * JWT subject.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/vendor")
@RequiredArgsConstructor
public class VendorCollectionController {

    private final FarmerCollectionService farmerCollectionService;
    private final VendorProfileRepository vendorProfileRepository;

    /**
     * Returns the passbook for a link belonging to the caller's vendor.
     */
    @Operation(summary = "Get farmer-link passbook (vendor view)",
            description = "FLORIST only. Returns the passbook for a farmer-vendor link belonging to "
                    + "the authenticated vendor: link summary, balance, and combined deposit/collection history.")
    @GetMapping("/farmer-links/{linkId}/passbook")
    public ResponseEntity<PassbookResponse> passbook(Authentication authentication,
                                                     @PathVariable Long linkId) {
        Long vendorId = vendorId(authentication);
        return ResponseEntity.ok(farmerCollectionService.getPassbook(linkId, vendorId, "FLORIST"));
    }

    /**
     * Lists collections on the caller's vendor's links, narrowed by status
     * (defaults to {@code PENDING}).
     */
    @Operation(summary = "List collections on the vendor's links",
            description = "FLORIST only. Returns collection requests on the authenticated vendor's farmer links, "
                    + "filtered by status (defaults to PENDING).")
    @GetMapping("/farmer-collections")
    public ResponseEntity<List<FarmerCollectionResponse>> listCollections(Authentication authentication,
                                                                          @RequestParam(required = false)
                                                                          FarmerPayment.Status status) {
        Long vendorId = vendorId(authentication);
        FarmerPayment.Status filter = status == null ? FarmerPayment.Status.PENDING : status;
        return ResponseEntity.ok(farmerCollectionService.listCollectionsByStatus(vendorId, filter));
    }

    /**
     * Confirms a pending collection belonging to the caller's vendor.
     */
    @Operation(summary = "Confirm a pending collection",
            description = "FLORIST only. Marks a PENDING collection as CONFIRMED on a link belonging to "
                    + "the authenticated vendor.")
    @PostMapping("/farmer-collections/{paymentId}/confirm")
    public ResponseEntity<FarmerCollectionResponse> confirm(Authentication authentication,
                                                            @PathVariable Long paymentId) {
        Long vendorId = vendorId(authentication);
        return ResponseEntity.ok(farmerCollectionService.confirmCollection(vendorId, paymentId));
    }

    /**
     * Rejects a pending collection belonging to the caller's vendor.
     */
    @Operation(summary = "Reject a pending collection",
            description = "FLORIST only. Marks a PENDING collection as REJECTED on a link belonging to "
                    + "the authenticated vendor.")
    @PostMapping("/farmer-collections/{paymentId}/reject")
    public ResponseEntity<FarmerCollectionResponse> reject(Authentication authentication,
                                                           @PathVariable Long paymentId) {
        Long vendorId = vendorId(authentication);
        return ResponseEntity.ok(farmerCollectionService.rejectCollection(vendorId, paymentId));
    }

    /* ------------------------------------------------------------- private */

    private Long vendorId(Authentication authentication) {
        String email = authentication.getName();
        return vendorProfileRepository.findByUserEmail(email)
                .map(VendorProfile::getId)
                .orElseThrow(() -> new IllegalStateException("Authenticated vendor not found"));
    }
}
