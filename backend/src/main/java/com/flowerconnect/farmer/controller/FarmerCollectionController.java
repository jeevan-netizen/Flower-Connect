package com.flowerconnect.farmer.controller;

import com.flowerconnect.farmer.dto.FarmerCollectionRequest;
import com.flowerconnect.farmer.dto.FarmerCollectionResponse;
import com.flowerconnect.farmer.dto.PassbookResponse;
import com.flowerconnect.farmer.service.FarmerCollectionService;
import com.flowerconnect.repository.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * Farmer-side collection requests and passbook (Stage 3 of the farmer module).
 *
 * <p>Every route requires the {@code FARMER} role and acts on the caller's own
 * links, resolved from the JWT subject.
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/farmer/links")
@RequiredArgsConstructor
public class FarmerCollectionController {

    private final FarmerCollectionService farmerCollectionService;
    private final UserRepository userRepository;

    /**
     * Returns the passbook for the caller's own link: link summary, outstanding
     * balance and a combined deposit/collection entry list with running balance.
     */
    @GetMapping("/{linkId}/passbook")
    public ResponseEntity<PassbookResponse> passbook(Authentication authentication,
                                                     @PathVariable Long linkId) {
        Long farmerId = farmerId(authentication);
        return ResponseEntity.ok(farmerCollectionService.getPassbook(linkId, farmerId, "FARMER"));
    }

    /**
     * Requests a {@code PENDING} collection on the caller's own {@code ACTIVE}
     * link. At most one {@code PENDING} collection may exist per link.
     */
    @PostMapping("/{linkId}/collections")
    public ResponseEntity<FarmerCollectionResponse> requestCollection(Authentication authentication,
                                                                      @PathVariable Long linkId,
                                                                      @Valid @RequestBody FarmerCollectionRequest request) {
        Long farmerId = farmerId(authentication);
        FarmerCollectionResponse created = farmerCollectionService.requestCollection(farmerId, linkId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
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
