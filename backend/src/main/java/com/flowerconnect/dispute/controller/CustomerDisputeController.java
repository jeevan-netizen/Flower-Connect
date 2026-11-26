package com.flowerconnect.dispute.controller;

import com.flowerconnect.dispute.dto.DisputeDetailResponse;
import com.flowerconnect.dispute.dto.DisputePageResponse;
import com.flowerconnect.dispute.dto.RaiseDisputeRequest;
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
 * Customer dispute endpoints (plan Phase 8, task 8.4).
 *
 * <p>All endpoints require CUSTOMER role and ownership of the order/dispute.
 * Base path: {@code /api/v1/disputes}
 */
@Slf4j
@Validated
@Tag(name = "Customer disputes", description = "CUSTOMER-only: raise and view your own disputes")
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
    @Operation(summary = "Raise a dispute",
            description = "Raises a dispute for a DELIVERED order. Only the order's customer may submit, "
                    + "and only once per order. CUSTOMER-only.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Dispute created"),
            @ApiResponse(responseCode = "400", description = "Invalid input"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "403", description = "Not the order's customer"),
            @ApiResponse(responseCode = "404", description = "Order not found"),
            @ApiResponse(responseCode = "409", description = "Order not DELIVERED, window expired, or dispute already exists")
    })
    public ResponseEntity<DisputeDetailResponse> raiseDispute(
            Authentication authentication,
            @PathVariable @Positive(message = "Order id must be positive") Long orderId,
            @Valid @RequestBody RaiseDisputeRequest request) {
        return ResponseEntity.ok(disputeService.raiseDispute(authentication.getName(), orderId, request));
    }

    /**
     * Lists the authenticated customer's own disputes, newest first.
     */
    @GetMapping
    @Operation(summary = "List the customer's disputes",
            description = "Lists the authenticated customer's own disputes, newest first. CUSTOMER-only.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Dispute page"),
            @ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    public ResponseEntity<DisputePageResponse> listDisputes(
            Authentication authentication,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(disputeService.listCustomerDisputes(authentication.getName(), pageable));
    }

    /**
     * Gets one of the authenticated customer's disputes.
     */
    @GetMapping("/{disputeId}")
    @Operation(summary = "Get one of the customer's disputes",
            description = "Gets one of the authenticated customer's disputes. CUSTOMER-only.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Dispute detail"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "404", description = "Dispute not found")
    })
    public ResponseEntity<DisputeDetailResponse> getDispute(
            Authentication authentication,
            @PathVariable @Positive(message = "Dispute id must be positive") Long disputeId) {
        return ResponseEntity.ok(disputeService.getCustomerDispute(authentication.getName(), disputeId));
    }
}