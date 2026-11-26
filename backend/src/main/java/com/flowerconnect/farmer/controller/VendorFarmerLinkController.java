package com.flowerconnect.farmer.controller;

import com.flowerconnect.farmer.dto.FarmerLinkResponse;
import com.flowerconnect.farmer.domain.FarmerVendorLink;
import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.farmer.service.FarmerLinkService;
import com.flowerconnect.repository.VendorProfileRepository;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Vendor (florist) side of farmer/vendor links (Stage 1 of the farmer module).
 *
 * <p>Every route requires the {@code FLORIST} role and acts only on links
 * belonging to the authenticated florist's vendor profile, resolved from the
 * JWT subject.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/vendor")
@RequiredArgsConstructor
public class VendorFarmerLinkController {

    private final FarmerLinkService farmerLinkService;
    private final VendorProfileRepository vendorProfileRepository;

    /**
     * Lists links belonging to the caller's vendor, optionally narrowed by
     * status ({@code PENDING}, {@code ACTIVE}, {@code REJECTED},
     * {@code SUSPENDED}).
     */
    @Operation(summary = "List the vendor's farmer links",
            description = "FLORIST only. Returns links belonging to the authenticated vendor's profile, "
                    + "optionally filtered by status (PENDING, ACTIVE, REJECTED, SUSPENDED).")
    @GetMapping("/farmer-links")
    public ResponseEntity<List<FarmerLinkResponse>> listVendorLinks(Authentication authentication,
                                                                    @RequestParam(required = false)
                                                                    FarmerVendorLink.Status status) {
        Long vendorId = vendorId(authentication);
        return ResponseEntity.ok(farmerLinkService.listVendorLinks(vendorId, status));
    }

    /**
     * Accepts a pending link: {@code PENDING -> ACTIVE}.
     */
    @Operation(summary = "Accept a pending farmer link",
            description = "FLORIST only. Transitions a PENDING farmer-vendor link to ACTIVE.")
    @PostMapping("/farmer-links/{linkId}/accept")
    public ResponseEntity<FarmerLinkResponse> acceptLink(Authentication authentication,
                                                         @PathVariable Long linkId) {
        Long vendorId = vendorId(authentication);
        return ResponseEntity.ok(farmerLinkService.acceptLink(vendorId, linkId));
    }

    /**
     * Rejects a pending link: {@code PENDING -> REJECTED}.
     */
    @Operation(summary = "Reject a pending farmer link",
            description = "FLORIST only. Transitions a PENDING farmer-vendor link to REJECTED.")
    @PostMapping("/farmer-links/{linkId}/reject")
    public ResponseEntity<FarmerLinkResponse> rejectLink(Authentication authentication,
                                                         @PathVariable Long linkId) {
        Long vendorId = vendorId(authentication);
        return ResponseEntity.ok(farmerLinkService.rejectLink(vendorId, linkId));
    }

    /**
     * Suspends an active link: {@code ACTIVE -> SUSPENDED}.
     */
    @Operation(summary = "Suspend an active farmer link",
            description = "FLORIST only. Transitions an ACTIVE farmer-vendor link to SUSPENDED.")
    @PostMapping("/farmer-links/{linkId}/suspend")
    public ResponseEntity<FarmerLinkResponse> suspendLink(Authentication authentication,
                                                          @PathVariable Long linkId) {
        Long vendorId = vendorId(authentication);
        return ResponseEntity.ok(farmerLinkService.suspendLink(vendorId, linkId));
    }

    /**
     * Reactivates a suspended link: {@code SUSPENDED -> ACTIVE}.
     */
    @Operation(summary = "Reactivate a suspended farmer link",
            description = "FLORIST only. Transitions a SUSPENDED farmer-vendor link back to ACTIVE.")
    @PostMapping("/farmer-links/{linkId}/reactivate")
    public ResponseEntity<FarmerLinkResponse> reactivateLink(Authentication authentication,
                                                             @PathVariable Long linkId) {
        Long vendorId = vendorId(authentication);
        return ResponseEntity.ok(farmerLinkService.reactivateLink(vendorId, linkId));
    }

    /* ------------------------------------------------------------- private */

    private Long vendorId(Authentication authentication) {
        String email = authentication.getName();
        return vendorProfileRepository.findByUserEmail(email)
                .map(VendorProfile::getId)
                .orElseThrow(() -> new IllegalStateException("Authenticated vendor not found"));
    }
}
