package com.flowerconnect.payment.controller;

import com.flowerconnect.payment.dto.MarkRefundRequest;
import com.flowerconnect.payment.dto.RefundMarkedResponse;
import com.flowerconnect.payment.dto.RefundQueueItem;
import com.flowerconnect.payment.domain.Payment;
import com.flowerconnect.payment.service.PaymentService;
import com.flowerconnect.payment.domain.Payment.PaymentStatus;
import com.flowerconnect.order.domain.Order;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * Admin refund queue and mark-refunded endpoint (plan Phase 8, task 8.3).
 *
 * <p>The namespace is restricted to the {@code ADMIN} role in
 * {@link com.flowerconnect.config.SecurityConfig}; any other role is refused
 * with 403 before this controller is reached. The caller is resolved from the
 * JWT subject (email) on every call.
 *
 * <p>Routes:
 * <ul>
 *   <li>{@code GET /api/v1/admin/refunds} — paginated list of payments whose
 *       status is {@code REFUND_PENDING}, with optional vendor/date filters.</li>
 *   <li>{@code POST /api/v1/admin/refunds/{orderId}/mark} — marks a
 *       {@code REFUND_PENDING} payment as {@code REFUNDED}, requiring a
 *       reference note. Idempotent when already {@code REFUNDED}.</li>
 * </ul>
 */
@Slf4j
@Validated
@Tag(name = "Admin refund queue", description = "Admin-only refund queue and mark-refunded endpoint")
@RestController
@RequestMapping("/api/v1/admin/refunds")
@RequiredArgsConstructor
public class AdminRefundController {

    private final PaymentService paymentService;

    @Operation(summary = "List pending refunds",
            description = "Returns one page of payments whose status is "
                    + "{@code REFUND_PENDING}, ordered newest first. Optional "
                    + "vendorId, fromDate and toDate filters are supported.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Refund queue page"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "403", description = "Not an ADMIN account")
    })
    @GetMapping
    public ResponseEntity<org.springframework.data.domain.Page<RefundQueueItem>> listRefunds(
            @Parameter(description = "Payment status filter (defaults to REFUND_PENDING)")
            @RequestParam(required = false) PaymentStatus status,
            @Parameter(description = "Vendor profile id filter")
            @RequestParam(required = false) Long vendorId,
            @Parameter(description = "Lower bound on payment created_at (inclusive, ISO-8601)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fromDate,
            @Parameter(description = "Upper bound on payment created_at (exclusive, ISO-8601)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime toDate,
            @Parameter(description = "Zero-based page index")
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Page size, clamped to 1..100")
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {

        PaymentStatus effectiveStatus = status != null ? status : PaymentStatus.REFUND_PENDING;
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        org.springframework.data.domain.Page<RefundQueueItem> result =
                paymentService.listRefundPayments(effectiveStatus, vendorId, fromDate, toDate, pageable);
        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Mark a refund as complete",
            description = "Transitions a {@code REFUND_PENDING} payment to "
                    + "{@code REFUNDED}, updating both the payment and order "
                    + "payment status together. Requires a reference note. "
                    + "Idempotent when the payment is already {@code REFUNDED}.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Payment marked refunded"),
            @ApiResponse(responseCode = "400", description = "Missing or invalid reference note"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "403", description = "Not an ADMIN account"),
            @ApiResponse(responseCode = "404", description = "Order or payment not found"),
            @ApiResponse(responseCode = "409", description = "Payment is not REFUND_PENDING")
    })
    @PostMapping("/{orderId}/mark")
    public ResponseEntity<RefundMarkedResponse> markRefunded(
            Authentication authentication,
            @PathVariable @Positive(message = "Order id must be positive") Long orderId,
            @Valid @RequestBody MarkRefundRequest request) {
        Payment payment = paymentService.markRefunded(
                orderId, authentication.getName(), request.getReferenceNote());
        RefundMarkedResponse response = RefundMarkedResponse.builder()
                .orderId(payment.getOrder().getId())
                .paymentStatus(Order.PaymentStatus.valueOf(payment.getStatus().name()))
                .refundedAt(payment.getRefundedAt())
                .referenceNote(request.getReferenceNote())
                .build();
        log.info("Admin {} marked order {} refunded (reference: {})",
                authentication.getName(), orderId, request.getReferenceNote());
        return ResponseEntity.ok(response);
    }
}
