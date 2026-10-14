package com.flowerconnect.checkout.controller;

import com.flowerconnect.checkout.dto.CheckoutQuoteRequest;
import com.flowerconnect.checkout.dto.CheckoutQuoteResponse;
import com.flowerconnect.checkout.service.CheckoutService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * Checkout API (plan task 5.3 — quote, task 5.4 — checkout).
 *
 * <p>The namespace is restricted to the {@code CUSTOMER} role in
 * {@code SecurityConfig}; a vendor or admin account is refused with 403 before
 * this controller is reached. The caller is resolved from the JWT subject
 * (the principal's name / email) on every call, so no route accepts a customer
 * identifier and cross-customer access is not expressible in the request.
 *
 * <p>Routes:
 * <ul>
 *   <li>{@code POST /api/v1/checkout/quote} — generate a read-only quote for the caller's cart</li>
 *   <li>{@code POST /api/v1/checkout} — place the order (task 5.4)</li>
 * </ul>
 */
@Slf4j
@Validated
@Tag(name = "Customer checkout", description = "Checkout quote and order placement for the authenticated customer")
@RestController
@RequestMapping("/api/v1/checkout")
@RequiredArgsConstructor
public class CheckoutController {

    private final CheckoutService checkoutService;

    @Operation(summary = "Generate a checkout quote",
            description = "Validates the caller's cart against the provided address and per-vendor " +
                    "slot selection. Returns a per-shop breakdown with subtotals, delivery fees, " +
                    "platform fee, and the grand total. No side effects — no stock reservation, " +
                    "no order creation, no cart clearing. Price drift warnings are included so " +
                    "the customer can confirm updated totals before placing the order.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Quote generated"),
            @ApiResponse(responseCode = "400", description = "Validation failed (empty cart, invalid address, missing slots, minimum order not met, address outside radius)"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "403", description = "Not a CUSTOMER account"),
            @ApiResponse(responseCode = "404", description = "Address not found"),
            @ApiResponse(responseCode = "409", description = "Slot full, or insufficient stock")
    })
    @PostMapping("/quote")
    public ResponseEntity<CheckoutQuoteResponse> quote(
            Authentication authentication,
            @Valid @RequestBody CheckoutQuoteRequest request) {
        CheckoutQuoteResponse response = checkoutService.generateQuote(authentication.getName(), request);
        log.info("Customer {} requested checkout quote for {} shop(s)", authentication.getName(),
                response.getShops().size());
        return ResponseEntity.ok(response);
    }
}