package com.flowerconnect.pos.controller;

import com.flowerconnect.pos.dto.PosSaleRequest;
import com.flowerconnect.pos.dto.PosSaleResponse;
import com.flowerconnect.pos.dto.PosSummaryResponse;
import com.flowerconnect.pos.service.PosService;
import com.flowerconnect.vendor.security.RequiresApprovedVendor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * POS walk-in sale API (plan Phase 11, tasks 11.2 and 11.3).
 *
 * <p>Base path: {@code /api/v1/pos}. Routes:
 * <ul>
 *   <li>{@code POST /sales} — quick sale with an {@code Idempotency-Key} header</li>
 *   <li>{@code GET /summary} — today's sale count, revenue and payment-method breakdown</li>
 * </ul>
 *
 * <p>The namespace is restricted to the {@code FLORIST} role in
 * {@code SecurityConfig}, and every handler carries
 * {@link com.flowerconnect.vendor.security.RequiresApprovedVendor} so that only
 * {@code APPROVED} vendors may ring up walk-in sales. Pending, rejected and
 * suspended vendors are refused with {@code 403 VENDOR_NOT_APPROVED}. POS is
 * deliberately <em>not</em> gated by {@code accepting_orders} (walk-in sales are
 * face-to-face and outside delivery capacity).
 *
 * <p>The caller is resolved from the JWT subject on every call. Payment methods
 * {@code CASH}, {@code CARD} and {@code UPI} are labels only; no gateway is
 * involved and no payment, order, commission or ledger row is created.
 */
@Slf4j
@Validated
@Tag(name = "POS", description = "Walk-in point-of-sale sales for approved vendors")
@RequiresApprovedVendor
@RestController
@RequestMapping("/api/v1/pos")
@RequiredArgsConstructor
public class PosController {

    private final PosService posService;

    /**
     * Records a POS walk-in sale. The client supplies product IDs and quantities
     * only; prices and totals are computed server-side. Requires an
     * {@code Idempotency-Key} header (canonical UUID) to prevent double-submit.
     */
    @Operation(summary = "Record a POS walk-in sale",
            description = "Deducts available stock for each line, writes one SALE_POS stock "
                    + "movement per line, and returns a receipt. POS is commission-free and "
                    + "creates no order, payment, commission or ledger row.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sale recorded"),
            @ApiResponse(responseCode = "400", description = "Validation failed (missing idempotency key, empty items, invalid quantity, line/quantity limits)"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "403", description = "Not a FLORIST account, or vendor profile is not APPROVED, or product belongs to another vendor"),
            @ApiResponse(responseCode = "404", description = "Product not found"),
            @ApiResponse(responseCode = "409", description = "Insufficient available stock, product not active or expired, idempotency key reused with a different body, or key already in progress")
    })
    @PostMapping("/sales")
    public ResponseEntity<PosSaleResponse> recordSale(
            Authentication authentication,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody PosSaleRequest request) {
        PosSaleResponse response = posService.recordSale(authentication.getName(), idempotencyKey, request);
        log.info("Vendor {} recorded POS sale {}", authentication.getName(), response.getTransactionId());
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves a past POS sale receipt. Foreign sales are 403, missing sales 404.
     */
    @Operation(summary = "Retrieve a POS sale receipt",
            description = "Returns the receipt for a past POS sale owned by the calling vendor.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Receipt found"),
            @ApiResponse(responseCode = "403", description = "Sale belongs to another vendor"),
            @ApiResponse(responseCode = "404", description = "No such sale")
    })
    @GetMapping("/sales/{id}")
    public ResponseEntity<PosSaleResponse> getSale(
            Authentication authentication,
            @PathVariable @Positive(message = "Sale id must be positive") Long id) {
        PosSaleResponse response = posService.getSale(authentication.getName(), id);
        return ResponseEntity.ok(response);
    }

    /**
     * Today's POS summary for the calling vendor. "Today" is the calendar day in
     * the vendor's service-location timezone; timestamps are stored in UTC and
     * day boundaries are converted at query time.
     */
    @Operation(summary = "Today's POS summary",
            description = "Returns today's transaction count, total revenue and a "
                    + "breakdown by payment-method label. POS revenue is commission-free.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Today's summary")
    })
    @GetMapping("/summary")
    public ResponseEntity<PosSummaryResponse> todaySummary(
            Authentication authentication,
            @Parameter(description = "Local date (ISO-8601); defaults to today")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate localDate = date != null ? date : java.time.LocalDate.now(java.time.Clock.systemUTC());
        PosSummaryResponse response = posService.getSummary(authentication.getName(), localDate);
        return ResponseEntity.ok(response);
    }
}
