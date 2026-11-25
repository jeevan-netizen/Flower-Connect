package com.flowerconnect.farmer.controller;

import com.flowerconnect.farmer.dto.FarmerLinkRequest;
import com.flowerconnect.farmer.dto.FarmerLinkResponse;
import com.flowerconnect.farmer.dto.FarmerRegisterRequest;
import com.flowerconnect.farmer.dto.FarmerRegisterResponse;
import com.flowerconnect.farmer.dto.FarmerVendorResponse;
import com.flowerconnect.farmer.service.FarmerLinkService;
import com.flowerconnect.repository.UserRepository;
import com.flowerconnect.repository.VendorProfileRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Farmer self-service API (Stage 1 of the farmer module).
 *
 * <p>{@code POST /register} is public and creates a {@code FARMER} account.
 * Every other route requires the {@code FARMER} role and acts on the caller's
 * own farmer identity, resolved from the JWT subject.
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/farmer")
@RequiredArgsConstructor
public class FarmerLinkController {

    private final FarmerLinkService farmerLinkService;
    private final UserRepository userRepository;

    /**
     * Registers a farmer account. Public entry point; the client then signs in
     * through {@code POST /api/v1/auth/login}.
     */
    @PostMapping("/register")
    public ResponseEntity<FarmerRegisterResponse> register(
            @Valid @RequestBody FarmerRegisterRequest request) {
        FarmerRegisterResponse created = farmerLinkService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Lists vendors eligible to receive a link request (approved vendors), with
     * the caller's link status when one exists.
     */
    @GetMapping("/vendors")
    public ResponseEntity<List<FarmerVendorResponse>> listEligibleVendors(Authentication authentication,
                                                                          @RequestParam(required = false) String query) {
        Long farmerId = farmerId(authentication);
        return ResponseEntity.ok(farmerLinkService.listEligibleVendors(farmerId, query));
    }

    /**
     * Creates a {@code PENDING} link request from the caller to a vendor.
     */
    @PostMapping("/links")
    public ResponseEntity<FarmerLinkResponse> requestLink(Authentication authentication,
                                                          @Valid @RequestBody FarmerLinkRequest request) {
        Long farmerId = farmerId(authentication);
        FarmerLinkResponse created = farmerLinkService.requestLink(farmerId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Lists only the authenticated farmer's own links.
     */
    @GetMapping("/links")
    public ResponseEntity<List<FarmerLinkResponse>> listFarmerLinks(Authentication authentication) {
        Long farmerId = farmerId(authentication);
        return ResponseEntity.ok(farmerLinkService.listFarmerLinks(farmerId));
    }

    /* ------------------------------------------------------------- private */

    private Long farmerId(Authentication authentication) {
        String email = authentication.getName();
        return userRepository.findByEmailWithRole(email)
                .map(user -> {
                    if (!"FARMER".equals(user.getRole().getName())) {
                        throw new IllegalStateException("Authenticated user is not a farmer");
                    }
                    return user.getId();
                })
                .orElseThrow(() -> new IllegalStateException("Authenticated farmer not found"));
    }
}
