package com.flowerconnect.dispute.controller;

import com.flowerconnect.dispute.dto.DisputeDetailResponse;
import com.flowerconnect.dispute.dto.DisputePageResponse;
import com.flowerconnect.dispute.dto.RaiseDisputeRequest;
import com.flowerconnect.dispute.service.DisputeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Customer dispute endpoints (plan Phase 8, task 8.4).
 *
 * <p>All endpoints require CUSTOMER role and ownership of the order/dispute.
 * Base path: {@code /api/v1/disputes}
 */
@RestController
@RequestMapping("/api/v1/disputes")
@RequiredArgsConstructor
public class CustomerDisputeController {

    private final DisputeService disputeService;

    /**
     * Raises a dispute for a delivered order.
     *
     * @param authentication the authenticated customer (principal is the email)
     * @param orderId        the order to dispute
     * @param request        reason code and description
     * @return the created dispute detail
     * @throws BusinessException 404 if order not found, 403 if not the
     *                           customer's order, 400 for invalid input,
     *                           409 if order not DELIVERED, window expired,
     *                           or dispute already exists
     */
    @PostMapping("/orders/{orderId}")
    public ResponseEntity<DisputeDetailResponse> raiseDispute(
            Authentication authentication,
            @PathVariable Long orderId,
            @Valid @RequestBody RaiseDisputeRequest request) {
        return ResponseEntity.ok(disputeService.raiseDispute(authentication.getName(), orderId, request));
    }

    /**
     * Lists the authenticated customer's own disputes, newest first.
     */
    @GetMapping
    public ResponseEntity<DisputePageResponse> listDisputes(
            Authentication authentication,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(disputeService.listCustomerDisputes(authentication.getName(), pageable));
    }

    /**
     * Gets one of the authenticated customer's disputes.
     */
    @GetMapping("/{disputeId}")
    public ResponseEntity<DisputeDetailResponse> getDispute(
            Authentication authentication,
            @PathVariable Long disputeId) {
        return ResponseEntity.ok(disputeService.getCustomerDispute(authentication.getName(), disputeId));
    }
}