package com.flowerconnect.vendor.controller;

import com.flowerconnect.vendor.dto.VendorProfileResponse;
import com.flowerconnect.vendor.dto.VendorProfileUpdateRequest;
import com.flowerconnect.vendor.dto.VendorRegisterRequest;
import com.flowerconnect.vendor.service.VendorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * Vendor self-service API (plan task 2.5).
 *
 * <p>{@code POST /register} is public — it is the vendor's entry point and
 * creates the account. Every other route requires {@code ROLE_FLORIST} and acts
 * only on the caller's own profile, resolved from the JWT subject; no route
 * accepts a vendor identifier, so cross-vendor access is not expressible.
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/vendors")
@RequiredArgsConstructor
public class VendorController {

    private final VendorService vendorService;

    /**
     * Creates the FLORIST account and the PENDING_APPROVAL profile in one
     * transaction. Returns the created profile; the client then signs in through
     * {@code POST /api/v1/auth/login}.
     */
    @PostMapping("/register")
    public ResponseEntity<VendorProfileResponse> register(
            @Valid @RequestBody VendorRegisterRequest request) {
        VendorProfileResponse created = vendorService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/profile")
    public ResponseEntity<VendorProfileResponse> getOwnProfile(Authentication authentication) {
        return ResponseEntity.ok(vendorService.getOwnProfile(authentication.getName()));
    }

    @PutMapping("/profile")
    public ResponseEntity<VendorProfileResponse> updateOwnProfile(
            Authentication authentication,
            @Valid @RequestBody VendorProfileUpdateRequest request) {
        return ResponseEntity.ok(vendorService.updateOwnProfile(authentication.getName(), request));
    }
}
