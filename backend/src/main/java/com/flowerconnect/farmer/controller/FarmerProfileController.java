package com.flowerconnect.farmer.controller;

import com.flowerconnect.farmer.dto.FarmerProfileResponse;
import com.flowerconnect.farmer.service.FarmerProfileService;
import com.flowerconnect.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Farmer profile (Phase 14 farmer portal).
 *
 * <p>Returns the authenticated farmer's own profile: name, mobile number and
 * address. Every route requires the {@code FARMER} role and resolves the caller
 * from the JWT subject, so a farmer can only read their own profile.
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/farmer/profile")
@RequiredArgsConstructor
public class FarmerProfileController {

    private final FarmerProfileService farmerProfileService;

    /**
     * Returns the authenticated farmer's profile.
     */
    @Operation(summary = "Get the authenticated farmer's profile",
            description = "AUTHENTICATED FARMER only. Returns the caller's own name, mobile number and address.")
    @GetMapping
    public ResponseEntity<FarmerProfileResponse> profile(Authentication authentication) {
        String email = authentication.getName();
        return ResponseEntity.ok(farmerProfileService.profile(email));
    }
}
