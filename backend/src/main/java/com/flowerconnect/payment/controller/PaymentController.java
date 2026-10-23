package com.flowerconnect.payment.controller;

import com.flowerconnect.payment.dto.PaymentGroupStateResponse;
import com.flowerconnect.payment.dto.PaymentResumeResponse;
import com.flowerconnect.payment.dto.PaymentStateResponse;
import com.flowerconnect.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.constraints.Positive;

/**
 * Customer-facing payment state and resume API (Phase 7, task 7.4).
 *
 * <p>The namespace is restricted to the {@code CUSTOMER} role in
 * {@code com.flowerconnect.config.SecurityConfig}; a vendor or admin account
 * is refused with 403 before this controller is reached. The caller is
 * resolved from the JWT subject (email) on every call.
 *
 * <p>Routes:
 * <ul>
 *   <li>{@code GET /api/v1/payments/{orderId}} — current payment state for
 *       one order (owning customer only)</li>
 *   <li>{@code GET /api/v1/payments/group/{paymentGroupId}} — current state
 *       of a payment group (simulated intent + linked orders, owning customer
 *       only)</li>
 *   <li>{@code POST /api/v1/payments/{orderId}/resume} — confirm payment for
 *       an unexpired {@code PENDING_PAYMENT} order (test-mode/simulated).
 *       Transitions the order to {@code PLACED} and the payment to
 *       {@code PAID}.</li>
 * </ul>
 *
 * <p>All reads are scoped to the owning customer: a missing resource is 404,
 * another customer's resource is 403, and a non-resumable payment is 409.
 * No provider secrets are ever returned.
 */
@Slf4j
@Validated
@Tag(name = "Customer payments", description = "Payment state and resume for the authenticated customer")
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @Operation(summary = "Get payment state for an order",
            description = "Returns the current payment and order status for " +
                    "the authenticated customer's own order. COD orders report " +
                    "their collected/pending state; ONLINE orders report the " +
                    "pending-payment state until resumed.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Payment state"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "403", description = "Not a CUSTOMER account, or order belongs to another customer"),
            @ApiResponse(responseCode = "404", description = "Order or payment not found")
    })
    @GetMapping("/{orderId}")
    public ResponseEntity<PaymentStateResponse> getPaymentState(
            Authentication authentication,
            @PathVariable @Positive(message = "Order id must be positive") Long orderId) {
        PaymentStateResponse state = paymentService.fetchPaymentState(orderId, authentication.getName());
        log.info("Customer {} fetched payment state for order {}", authentication.getName(), orderId);
        return ResponseEntity.ok(state);
    }

    @Operation(summary = "Get payment group state",
            description = "Returns the simulated payment intent state and all " +
                    "linked orders for the authenticated customer's own group.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Group state"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "403", description = "Not a CUSTOMER account, or group belongs to another customer"),
            @ApiResponse(responseCode = "404", description = "Payment group not found")
    })
    @GetMapping("/group/{paymentGroupId}")
    public ResponseEntity<PaymentGroupStateResponse> getGroupState(
            Authentication authentication,
            @Parameter(description = "The payment group id returned by checkout")
            @PathVariable String paymentGroupId) {
        PaymentGroupStateResponse state = paymentService.fetchGroupState(paymentGroupId, authentication.getName());
        log.info("Customer {} fetched payment group state for {}", authentication.getName(), paymentGroupId);
        return ResponseEntity.ok(state);
    }

    @Operation(summary = "Resume/pay a pending order",
            description = "Confirms payment for an unexpired {@code PENDING_PAYMENT} " +
                    "order (simulated/test-mode). Transitions the order to " +
                    "{@code PLACED} and the payment to {@code PAID}. Rejected " +
                    "with 409 if the order is not pending, already paid, or the " +
                    "intent has expired.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Payment confirmed"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "403", description = "Not a CUSTOMER account, or order belongs to another customer"),
            @ApiResponse(responseCode = "404", description = "Order or payment not found"),
            @ApiResponse(responseCode = "409", description = "Not pending, already paid, or intent expired")
    })
    @PostMapping("/{orderId}/resume")
    public ResponseEntity<PaymentResumeResponse> resumePayment(
            Authentication authentication,
            @PathVariable @Positive(message = "Order id must be positive") Long orderId) {
        PaymentResumeResponse result = paymentService.resumePayment(orderId, authentication.getName());
        log.info("Customer {} resumed payment for order {}", authentication.getName(), orderId);
        return ResponseEntity.ok(result);
    }
}
