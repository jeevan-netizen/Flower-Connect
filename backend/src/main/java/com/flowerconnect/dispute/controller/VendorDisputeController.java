package com.flowerconnect.dispute.controller;

import com.flowerconnect.dispute.dto.DisputeDetailResponse;
import com.flowerconnect.dispute.dto.DisputePageResponse;
import com.flowerconnect.dispute.dto.VendorDisputeResponseRequest;
import com.flowerconnect.dispute.service.DisputeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * Vendor dispute endpoints (plan Phase 8, task 8.4).
 *
 * <p>All endpoints require FLORIST role and ownership of the order/dispute.
 * A suspended vendor may still respond to an in-flight dispute (D-6).
 * Base path: {@code /api/v1/vendor/disputes}
 */
@Slf4j
@Validated
@Tag(name = "Vendor disputes", description = "FLORIST-only: view and respond to disputes on your orders")
@RestController
@RequestMapping("/api/v1/vendor/disputes")
@RequiredArgsConstructor
public class VendorDisputeController {

    private final DisputeService disputeService;

    /**
     * Lists disputes on the authenticated vendor's orders, newest first.
     */
    @GetMapping
    @Operation(summary = "List the vendor's disputes",
            description = "Lists disputes on the authenticated vendor's orders, newest first. FLORIST-only.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Dispute page"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "403", description = "Not a FLORIST account")
    })
    public ResponseEntity<DisputePageResponse> listDisputes(
            Authentication authentication,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(disputeService.listVendorDisputes(authentication.getName(), pageable));
    }

    /**
     * Gets one dispute on the authenticated vendor's orders.
     */
    @GetMapping("/{disputeId}")
    @Operation(summary = "Get one of the vendor's disputes",
            description = "Gets one dispute on the authenticated vendor's orders. FLORIST-only.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Dispute detail"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "403", description = "Not a FLORIST account"),
            @ApiResponse(responseCode = "404", description = "Dispute not found")
    })
    public ResponseEntity<DisputeDetailResponse> getDispute(
            Authentication authentication,
            @PathVariable @Positive(message = "Dispute id must be positive") Long disputeId) {
        return ResponseEntity.ok(disputeService.getVendorDispute(authentication.getName(), disputeId));
    }

    /**
     * Responds to a dispute on the vendor's order. Moves {@code OPEN} to
     * {@code UNDER_REVIEW}. One response per dispute.
     *
     * @param authentication the authenticated vendor (principal is the email)
     * @param disputeId      the dispute to respond to
     * @param request        vendor's response text (required, max 500 chars)
     * @return the updated dispute detail
     * @throws BusinessException 404 if dispute not found, 403 if not the
     *                           vendor's order, 400 if missing response,
     *                           409 if dispute not OPEN
     */
    @PostMapping("/{disputeId}/respond")
    @Operation(summary = "Respond to a dispute",
            description = "Adds a vendor reply to a dispute on the vendor's order, moving OPEN to "
                    + "UNDER_REVIEW. One reply per dispute. FLORIST-only; a suspended vendor may still "
                    + "respond (D-6).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reply added"),
            @ApiResponse(responseCode = "400", description = "Invalid reply"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "403", description = "Not the review's vendor"),
            @ApiResponse(responseCode = "404", description = "Dispute not found"),
            @ApiResponse(responseCode = "409", description = "Review already has a reply")
    })
    public ResponseEntity<DisputeDetailResponse> respond(
            Authentication authentication,
            @PathVariable @Positive(message = "Dispute id must be positive") Long disputeId,
            @Valid @RequestBody VendorDisputeResponseRequest request) {
        return ResponseEntity.ok(disputeService.respondToDispute(authentication.getName(), disputeId, request));
    }
}