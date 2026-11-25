package com.flowerconnect.b2b.controller;

import com.flowerconnect.b2b.dto.B2BStatementPageResponse;
import com.flowerconnect.b2b.dto.B2BStatementPreviewResponse;
import com.flowerconnect.b2b.dto.B2BStatementResponse;
import com.flowerconnect.b2b.dto.CreateB2BStatementRequest;
import com.flowerconnect.b2b.dto.RecordB2BStatementPaymentRequest;
import com.flowerconnect.b2b.service.B2BStatementService;
import com.flowerconnect.vendor.security.RequiresApprovedVendor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * B2B statements between approved vendors (Stage 3 of the B2B module).
 *
 * <p>Base path is {@code /api/v1/vendors/b2b/...}, inside the existing
 * {@code /api/v1/vendors/**} namespace so the {@code hasRole("FLORIST")}
 * rule in {@code SecurityConfig} applies first, and
 * {@link com.flowerconnect.vendor.security.RequiresApprovedVendor} adds
 * the stricter approval boundary (D-13).
 *
 * <p>The vendor is resolved from the JWT subject on every call; no route
 * accepts a vendor identifier for the caller. Statement parties
 * (seller/buyer) are checked in the service layer.
 */
@Slf4j
@Validated
@Tag(name = "B2B statements", description = "Statements and payment recording for delivered B2B orders")
@RequiresApprovedVendor
@RestController
@RequestMapping("/api/v1/vendors/b2b")
@RequiredArgsConstructor
public class B2BStatementController {

    private final B2BStatementService b2bStatementService;

    @Operation(summary = "Preview eligible orders for a statement",
            description = "Returns the caller's delivered, unpaid orders for the selected buyer "
                    + "within the optional inclusive date range, plus their combined total. "
                    + "When omitted, {@code to} defaults to today and {@code from} defaults to "
                    + "15 days before {@code to}.")
    @GetMapping("/statements/preview")
    public ResponseEntity<B2BStatementPreviewResponse> preview(
            Authentication authentication,
            @Parameter(description = "Buyer vendor profile id")
            @RequestParam @Positive(message = "Buyer id must be positive") Long buyerId,
            @Parameter(description = "Inclusive period start date (YYYY-MM-DD), optional")
            @RequestParam(required = false) LocalDate from,
            @Parameter(description = "Inclusive period end date (YYYY-MM-DD), optional")
            @RequestParam(required = false) LocalDate to) {
        return ResponseEntity.ok(b2bStatementService.preview(
                authentication.getName(), buyerId, from, to));
    }

    @Operation(summary = "Create a statement",
            description = "Creates an {@code OPEN} statement from the currently eligible orders "
                    + "for the caller (as seller) and the selected buyer. The total is computed "
                    + "server-side. Empty statements are rejected; concurrent creations cannot "
                    + "assign the same order to two statements.")
    @PostMapping("/statements")
    public ResponseEntity<B2BStatementResponse> createStatement(
            Authentication authentication,
            @Valid @RequestBody CreateB2BStatementRequest request) {
        B2BStatementResponse response = b2bStatementService.createStatement(
                authentication.getName(), request);
        log.info("Created statement {} by seller {}", response.getStatementNo(),
                authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "List the caller's statements",
            description = "Returns the caller's statements as {@code SELLER} or {@code BUYER}, "
                    + "newest first. Page size is clamped to 1..100.")
    @GetMapping("/statements")
    public ResponseEntity<B2BStatementPageResponse> listStatements(
            Authentication authentication,
            @Parameter(description = "SELLER (default) or BUYER")
            @RequestParam(required = false) String role,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "Page must be non-negative") int page,
            @RequestParam(defaultValue = "20") @Positive(message = "Size must be positive") int size) {
        return ResponseEntity.ok(b2bStatementService.listStatements(
                authentication.getName(), role, page, size));
    }

    @Operation(summary = "Read one statement",
            description = "Returns a single statement. Only the seller or the buyer on the "
                    + "statement may read it.")
    @GetMapping("/statements/{statementId}")
    public ResponseEntity<B2BStatementResponse> getStatement(
            Authentication authentication,
            @Parameter(description = "Statement id")
            @PathVariable @Positive(message = "Statement id must be positive") Long statementId) {
        return ResponseEntity.ok(b2bStatementService.getStatement(
                authentication.getName(), statementId));
    }

    @Operation(summary = "Record payment for a statement",
            description = "Seller only. Marks an {@code OPEN} statement {@code PAID} and marks "
                    + "every linked order's {@code payment_status} {@code PAID} in one "
                    + "transaction. Full payment only; paying twice is rejected.")
    @PostMapping("/statements/{statementId}/payment")
    public ResponseEntity<B2BStatementResponse> recordPayment(
            Authentication authentication,
            @Parameter(description = "Statement id")
            @PathVariable @Positive(message = "Statement id must be positive") Long statementId,
            @Valid @RequestBody RecordB2BStatementPaymentRequest request) {
        return ResponseEntity.ok(b2bStatementService.recordPayment(
                authentication.getName(), statementId, request));
    }
}
