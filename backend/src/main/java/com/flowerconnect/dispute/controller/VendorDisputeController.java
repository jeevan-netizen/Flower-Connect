package com.flowerconnect.dispute.controller;

import com.flowerconnect.dispute.dto.DisputeDetailResponse;
import com.flowerconnect.dispute.dto.DisputePageResponse;
import com.flowerconnect.dispute.dto.VendorDisputeResponseRequest;
import com.flowerconnect.dispute.service.DisputeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Vendor dispute endpoints (plan Phase 8, task 8.4).
 *
 * <p>All endpoints require FLORIST role and ownership of the order/dispute.
 * A suspended vendor may still respond to an in-flight dispute (D-6).
 * Base path: {@code /api/v1/vendor/disputes}
 */
@RestController
@RequestMapping("/api/v1/vendor/disputes")
@RequiredArgsConstructor
public class VendorDisputeController {

    private final DisputeService disputeService;

    /**
     * Lists disputes on the authenticated vendor's orders, newest first.
     */
    @GetMapping
    public ResponseEntity<DisputePageResponse> listDisputes(
            Authentication authentication,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(disputeService.listVendorDisputes(authentication.getName(), pageable));
    }

    /**
     * Gets one dispute on the authenticated vendor's orders.
     */
    @GetMapping("/{disputeId}")
    public ResponseEntity<DisputeDetailResponse> getDispute(
            Authentication authentication,
            @PathVariable Long disputeId) {
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
    public ResponseEntity<DisputeDetailResponse> respond(
            Authentication authentication,
            @PathVariable Long disputeId,
            @Valid @RequestBody VendorDisputeResponseRequest request) {
        return ResponseEntity.ok(disputeService.respondToDispute(authentication.getName(), disputeId, request));
    }
}