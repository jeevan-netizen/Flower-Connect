package com.flowerconnect.geo.controller;

import com.flowerconnect.domain.User;
import com.flowerconnect.geo.domain.ServiceLocationProposal;
import com.flowerconnect.geo.dto.ApproveServiceLocationProposalRequest;
import com.flowerconnect.geo.dto.ServiceLocationProposalRequest;
import com.flowerconnect.geo.dto.ServiceLocationProposalResponse;
import com.flowerconnect.geo.mapper.ServiceLocationProposalMapper;
import com.flowerconnect.geo.service.LocationProposalService;
import com.flowerconnect.repository.UserRepository;
import com.flowerconnect.vendor.dto.VendorAdminReasonRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Service-area proposals (vendor registration).
 *
 * A vendor whose shop area is not yet a seeded service location can propose it.
 * The proposal is recorded in PENDING state and reviewed by an administrator:
 * approval creates the real service location (with administrator-supplied
 * coordinates and timezone) and makes the area available in the customer
 * "Set your delivery location" picker; rejection records a reason.
 *
 * PENDING proposals are intentionally never returned by
 * {@code GET /api/v1/locations}, so a proposed area is not publicly
 * discoverable or deliverable until approved.
 */
@Validated
@Tag(name = "Service location proposals", description = "Propose and review new service areas")
@RestController
@RequestMapping("/api/v1/locations/proposals")
@RequiredArgsConstructor
public class LocationProposalController {

    private final LocationProposalService proposalService;
    private final UserRepository userRepository;

    /**
     * Submits a new service-area proposal. Idempotent: a PENDING proposal for
     * the same (city, area) is returned rather than duplicated.
     */
    @PostMapping
    @Operation(summary = "Propose a new service area",
            description = "Authenticated users may propose a service area that is not yet "
                    + "seeded. The proposal is recorded in PENDING state and reviewed by an "
                    + "administrator before it can appear in the customer location picker.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Proposal created"),
            @ApiResponse(responseCode = "200", description = "An identical PENDING proposal already exists"),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    public ResponseEntity<ServiceLocationProposalResponse> submitProposal(
            Authentication authentication,
            @Valid @RequestBody ServiceLocationProposalRequest request) {
        Long proposerId = userId(authentication);
        com.flowerconnect.geo.domain.ServiceLocationProposal proposal = proposalService.submitProposal(
                request.getCity(), request.getArea(), request.getPincode(),
                request.getAddressLine1(), request.getAddressLine2(),
                proposerId, authentication.getName());
        return ResponseEntity.ok(ServiceLocationProposalMapper.INSTANCE.toResponse(proposal));
    }

    /**
     * Lists PENDING proposals for administrator review.
     */
    @GetMapping
    @Operation(summary = "List pending service-area proposals",
            description = "ADMIN-only. Returns proposals awaiting review, oldest first.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pending proposals"),
            @ApiResponse(responseCode = "403", description = "Not an administrator")
    })
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<ServiceLocationProposalResponse>> listPending() {
        return ResponseEntity.ok(proposalService.listPending());
    }

    /**
     * Approves a PENDING proposal, creating the real service location with the
     * administrator-supplied coordinates and timezone.
     */
    @PostMapping("/{proposalId}/approve")
    @Operation(summary = "Approve a service-area proposal",
            description = "ADMIN-only. Flips the proposal to APPROVED and creates the real "
                    + "service location using the supplied coordinates and timezone, so the "
                    + "area appears in the customer location picker.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Proposal approved"),
            @ApiResponse(responseCode = "400", description = "Proposal is not PENDING"),
            @ApiResponse(responseCode = "403", description = "Not an administrator"),
            @ApiResponse(responseCode = "404", description = "Proposal not found")
    })
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ServiceLocationProposalResponse> approveProposal(
            @Parameter(description = "Proposal id") @Positive @PathVariable Long proposalId,
            Authentication authentication,
            @Valid @RequestBody ApproveServiceLocationProposalRequest request) {
        return ResponseEntity.ok(proposalService.approveProposal(
                proposalId, userId(authentication), request));
    }

    /**
     * Rejects a PENDING proposal with a required reason.
     */
    @PostMapping("/{proposalId}/reject")
    @Operation(summary = "Reject a service-area proposal",
            description = "ADMIN-only. Flips the proposal to REJECTED and records the reason.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Proposal rejected"),
            @ApiResponse(responseCode = "400", description = "Proposal is not PENDING or reason missing"),
            @ApiResponse(responseCode = "403", description = "Not an administrator"),
            @ApiResponse(responseCode = "404", description = "Proposal not found")
    })
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ServiceLocationProposalResponse> rejectProposal(
            @Parameter(description = "Proposal id") @Positive @PathVariable Long proposalId,
            Authentication authentication,
            @Valid @RequestBody VendorAdminReasonRequest request) {
        return ResponseEntity.ok(proposalService.rejectProposal(
                proposalId, userId(authentication), request.getReason()));
    }

    private Long userId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        return userRepository.findByEmailWithRole(authentication.getName())
                .map(User::getId)
                .orElse(null);
    }
}
