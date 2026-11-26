package com.flowerconnect.vendor.controller;

import com.flowerconnect.vendor.dto.VendorProfileResponse;
import com.flowerconnect.vendor.dto.VendorProfileUpdateRequest;
import com.flowerconnect.vendor.dto.VendorRegisterRequest;
import com.flowerconnect.vendor.service.VendorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Vendor self-service", description = "Vendor registration and own-profile management")
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
    @Operation(summary = "Register a vendor account",
            description = "Public. Creates the FLORIST account and a PENDING_APPROVAL profile "
                    + "in one transaction. Returns the created profile; the client then signs in "
                    + "through POST /api/v1/auth/login.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Vendor registered"),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "409", description = "Email or phone already in use")
    })
    public ResponseEntity<VendorProfileResponse> register(
            @Valid @RequestBody VendorRegisterRequest request) {
        VendorProfileResponse created = vendorService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/profile")
    @Operation(summary = "Get the calling vendor's own profile",
            description = "FLORIST-only. Returns the authenticated vendor's own profile, "
                    + "resolved from the JWT subject.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Vendor profile"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "403", description = "Not a FLORIST account"),
            @ApiResponse(responseCode = "404", description = "No vendor profile")
    })
    public ResponseEntity<VendorProfileResponse> getOwnProfile(Authentication authentication) {
        return ResponseEntity.ok(vendorService.getOwnProfile(authentication.getName()));
    }

    @PutMapping("/profile")
    @Operation(summary = "Update the calling vendor's own profile",
            description = "FLORIST-only. Full replacement of the editable profile fields. "
                    + "Resolved from the JWT subject.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profile updated"),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "403", description = "Not a FLORIST account"),
            @ApiResponse(responseCode = "404", description = "No vendor profile")
    })
    public ResponseEntity<VendorProfileResponse> updateOwnProfile(
            Authentication authentication,
            @Valid @RequestBody VendorProfileUpdateRequest request) {
        return ResponseEntity.ok(vendorService.updateOwnProfile(authentication.getName(), request));
    }
}
