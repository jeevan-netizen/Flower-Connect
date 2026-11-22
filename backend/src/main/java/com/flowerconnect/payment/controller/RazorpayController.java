package com.flowerconnect.payment.controller;

import com.flowerconnect.config.AppProperties;
import com.flowerconnect.exception.BusinessException;
import com.flowerconnect.payment.service.RazorpayService;
import com.flowerconnect.payment.service.RazorpayService.RazorpayWebhookResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Map;

/**
 * Razorpay test-mode checkout API.
 *
 * <p>Routes:
 * <ul>
 *   <li>{@code GET /api/v1/payments/razorpay/key} — returns the public Razorpay
 *       Key ID for initialising Razorpay Standard Checkout on the frontend.
 *       Never returns the Key Secret.</li>
 *   <li>{@code POST /api/v1/payments/razorpay/{orderId}/verify} — verifies a
 *       Razorpay checkout signature (HMAC-SHA256) and, if valid, marks the
 *       payment {@code PAID} and transitions the order {@code PENDING_PAYMENT
 *       -> PLACED}. CUSTOMER only, ownership enforced.</li>
 *   <li>{@code POST /api/v1/payments/razorpay/webhook} — Razorpay webhook
 *       endpoint. Verifies {@code X-Razorpay-Signature} against the raw body,
 *       handles {@code payment.captured} / {@code payment.failed} idempotently.
 *       No JWT required.</li>
 * </ul>
 */
@Slf4j
@Validated
@Tag(name = "Razorpay payments", description = "Razorpay Standard Checkout (test mode): key, verify, webhook")
@RestController
@RequestMapping("/api/v1/payments/razorpay")
@RequiredArgsConstructor
public class RazorpayController {

    private final RazorpayService razorpayService;
    private final AppProperties appProperties;

    /**
     * Returns the public Razorpay Key ID. The Key Secret is never exposed.
     */
    @GetMapping("/key")
    @Operation(summary = "Get Razorpay public key",
            description = "CUSTOMER. Returns the Razorpay Key ID for initialising Razorpay Standard Checkout. " +
                    "The Key Secret is never sent to the frontend.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Razorpay key ID"),
            @ApiResponse(responseCode = "404", description = "Razorpay not configured")
    })
    public ResponseEntity<RazorpayKeyResponse> getKey() {
        String keyId = appProperties.getRazorpayKeyId();
        if (keyId == null || keyId.isBlank()) {
            throw BusinessException.notFound("Razorpay is not configured");
        }
        return ResponseEntity.ok(new RazorpayKeyResponse(keyId));
    }

    /**
     * Verifies a Razorpay checkout signature and completes the payment.
     */
    @PostMapping("/{orderId}/verify")
    @Operation(summary = "Verify Razorpay checkout signature",
            description = "CUSTOMER. Verifies the Razorpay checkout signature (HMAC-SHA256) and, " +
                    "if valid, marks the payment PAID and transitions the order to PLACED. " +
                    "An unverified signature never changes payment or order state.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Payment verified and order placed"),
            @ApiResponse(responseCode = "400", description = "Invalid signature"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "403", description = "Order belongs to another customer"),
            @ApiResponse(responseCode = "404", description = "Order or Razorpay order not found"),
            @ApiResponse(responseCode = "409", description = "Order is not awaiting payment")
    })
    public ResponseEntity<RazorpayVerifyResponse> verify(
            Authentication authentication,
            @PathVariable @Positive(message = "Order id must be positive") Long orderId,
            @Valid @RequestBody RazorpayVerifyRequest request) {
        RazorpayService.RazorpayVerifyResult result = razorpayService.verifyCheckout(
                orderId,
                request.getRazorpayOrderId(),
                request.getRazorpayPaymentId(),
                request.getRazorpaySignature(),
                authentication.getName());
        log.info("Customer {} verified Razorpay payment for order {}: payment={}, order={}",
                authentication.getName(), orderId, result.paymentStatus(), result.orderStatus());
        return ResponseEntity.ok(new RazorpayVerifyResponse(
                result.orderId(),
                result.orderStatus(),
                result.paymentStatus(),
                result.paymentGroupId()));
    }

    /**
     * Receives a Razorpay webhook. Signature-authenticated, no JWT.
     */
    @PostMapping("/webhook")
    @Operation(summary = "Receive Razorpay webhook",
            description = "Signature-authenticated (HMAC-SHA256 over the raw body using the " +
                    "Razorpay webhook secret). Requires no JWT. Handles payment.captured and " +
                    "payment.failed idempotently.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Event accepted or duplicate acknowledged"),
            @ApiResponse(responseCode = "400", description = "Malformed payload"),
            @ApiResponse(responseCode = "401", description = "Invalid signature"),
            @ApiResponse(responseCode = "404", description = "Razorpay order not found")
    })
    public ResponseEntity<RazorpayWebhookResponse> handleWebhook(
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature,
            jakarta.servlet.http.HttpServletRequest request) throws java.io.IOException {
        byte[] body = readBody(request);
        RazorpayWebhookResult result = razorpayService.processWebhook(signature, body);
        return ResponseEntity.ok(new RazorpayWebhookResponse(
                result.isAccepted() ? "accepted" : result.isDuplicate() ? "duplicate" : "ignored",
                result.getProviderEventId()));
    }

    /**
     * Reads the raw request body, enforcing the configured size limit.
     */
    private byte[] readBody(jakarta.servlet.http.HttpServletRequest request) throws java.io.IOException {
        int max = appProperties.getWebhookMaxBodyBytes();
        java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream(max);
        byte[] chunk = new byte[8192];
        int read;
        long total = 0;
        while ((read = request.getInputStream().read(chunk)) != -1) {
            total += read;
            if (total > max) {
                throw BusinessException.badRequest("Webhook body exceeds the configured size limit");
            }
            buf.write(chunk, 0, read);
        }
        return buf.toByteArray();
    }

    /** Public Razorpay Key ID (never the secret). */
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class RazorpayKeyResponse {
        private String keyId;
    }

    /** Verify request body. */
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class RazorpayVerifyRequest {
        @NotBlank(message = "razorpayOrderId is required")
        private String razorpayOrderId;

        @NotBlank(message = "razorpayPaymentId is required")
        private String razorpayPaymentId;

        @NotBlank(message = "razorpaySignature is required")
        private String razorpaySignature;
    }

    /** Verify response body. */
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class RazorpayVerifyResponse {
        private Long orderId;
        private String orderStatus;
        private String paymentStatus;
        private String paymentGroupId;
    }

    /** Webhook response body. */
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class RazorpayWebhookResponse {
        private String outcome;
        private String providerEventId;
    }
}
