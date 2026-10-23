package com.flowerconnect.vendor.controller;

import com.flowerconnect.vendor.dto.VendorOrderPageResponse;
import com.flowerconnect.vendor.dto.VendorOrderRejectRequest;
import com.flowerconnect.vendor.dto.VendorOrderResponse;
import com.flowerconnect.vendor.dto.VendorOrderSummaryResponse;
import com.flowerconnect.vendor.service.VendorOrderService;
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
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Vendor order API (plan Phase 6, tasks 6.2 and 6.4).
 *
 * <p>Base path: {@code /api/v1/vendor/orders}. Action routes live beneath
 * this base:
 * <ul>
 *   <li>{@code POST /{orderId}/accept} — {@code PLACED &#8594; CONFIRMED},
 *       commits the reserved stock</li>
 *   <li>{@code POST /{orderId}/reject} — {@code PLACED &#8594; REJECTED},
 *       reason required, releases the reservation</li>
 *   <li>{@code POST /{orderId}/prepare} — {@code CONFIRMED &#8594; PREPARING}</li>
 *   <li>{@code POST /{orderId}/out-for-delivery} — {@code PREPARING &#8594; OUT_FOR_DELIVERY}</li>
 *   <li>{@code POST /{orderId}/delivered} — {@code OUT_FOR_DELIVERY &#8594; DELIVERED},
 *       records COD collection</li>
 * </ul>
 * Plus {@code GET /} (paginated list, optional status and date-range
 * filters) and {@code GET /summary} (today's summary).
 *
 * <p>Every controller method is thin: it resolves the caller's vendor
 * profile, enforces ownership, and delegates the transition to
 * {@link VendorOrderService}, which in turn delegates to
 * {@link com.flowerconnect.order.service.OrderStateMachine}. No controller
 * method sets {@code Order#status} directly.
 *
 * <p>Authorization: the route is covered by the
 * {@code hasRole("FLORIST")} namespace rule. {@code @RequiresApprovedVendor}
 * is deliberately not applied, per D-6, so a suspended vendor can still
 * process their own existing in-flight orders to completion. Order
 * ownership is enforced per handler: a foreign order is 403, a missing one
 * 404.
 */
@Slf4j
@Validated
@Tag(name = "Vendor orders", description = "Vendor order actions, list and today's summary")
@RestController
@RequestMapping("/api/v1/vendor/orders")
@RequiredArgsConstructor
public class VendorOrderController {

    private final VendorOrderService vendorOrderService;

    @Operation(summary = "Accept an order",
            description = "Transitions {@code PLACED &#8594; CONFIRMED} and commits the "
                    + "reserved stock. Refused with 409 if any item is short of stock.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Order accepted"),
            @ApiResponse(responseCode = "400", description = "Missing or invalid request"),
            @ApiResponse(responseCode = "403", description = "Order belongs to another vendor"),
            @ApiResponse(responseCode = "404", description = "No such order"),
            @ApiResponse(responseCode = "409", description = "Illegal transition or insufficient stock")
    })
    @PostMapping("/{orderId}/accept")
    public ResponseEntity<VendorOrderResponse> accept(
            Authentication authentication,
            @PathVariable @Positive(message = "Order id must be positive") Long orderId) {
        return ResponseEntity.ok(vendorOrderService.accept(authentication.getName(), orderId));
    }

    @Operation(summary = "Reject an order",
            description = "Transitions {@code PLACED &#8594; REJECTED} and releases the "
                    + "reserved stock. A reason is mandatory.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Order rejected"),
            @ApiResponse(responseCode = "400", description = "Reason is missing or blank"),
            @ApiResponse(responseCode = "403", description = "Order belongs to another vendor"),
            @ApiResponse(responseCode = "404", description = "No such order"),
            @ApiResponse(responseCode = "409", description = "Illegal transition")
    })
    @PostMapping("/{orderId}/reject")
    public ResponseEntity<VendorOrderResponse> reject(
            Authentication authentication,
            @PathVariable @Positive(message = "Order id must be positive") Long orderId,
            @Valid @RequestBody VendorOrderRejectRequest request) {
        return ResponseEntity.ok(vendorOrderService.reject(authentication.getName(), orderId,
                request.getReason()));
    }

    @Operation(summary = "Mark an order preparing",
            description = "Transitions {@code CONFIRMED &#8594; PREPARING}.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Order marked preparing"),
            @ApiResponse(responseCode = "403", description = "Order belongs to another vendor"),
            @ApiResponse(responseCode = "404", description = "No such order"),
            @ApiResponse(responseCode = "409", description = "Illegal transition")
    })
    @PostMapping("/{orderId}/prepare")
    public ResponseEntity<VendorOrderResponse> prepare(
            Authentication authentication,
            @PathVariable @Positive(message = "Order id must be positive") Long orderId) {
        return ResponseEntity.ok(vendorOrderService.prepare(authentication.getName(), orderId));
    }

    @Operation(summary = "Mark an order out for delivery",
            description = "Transitions {@code PREPARING &#8594; OUT_FOR_DELIVERY}.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Order marked out for delivery"),
            @ApiResponse(responseCode = "403", description = "Order belongs to another vendor"),
            @ApiResponse(responseCode = "404", description = "No such order"),
            @ApiResponse(responseCode = "409", description = "Illegal transition")
    })
    @PostMapping("/{orderId}/out-for-delivery")
    public ResponseEntity<VendorOrderResponse> outForDelivery(
            Authentication authentication,
            @PathVariable @Positive(message = "Order id must be positive") Long orderId) {
        return ResponseEntity.ok(vendorOrderService.outForDelivery(authentication.getName(), orderId));
    }

    @Operation(summary = "Mark an order delivered",
            description = "Transitions {@code OUT_FOR_DELIVERY &#8594; DELIVERED} and records "
                    + "COD collection ({@code payment_status = COLLECTED}) for COD orders.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Order marked delivered"),
            @ApiResponse(responseCode = "403", description = "Order belongs to another vendor"),
            @ApiResponse(responseCode = "404", description = "No such order"),
            @ApiResponse(responseCode = "409", description = "Illegal transition")
    })
    @PostMapping("/{orderId}/delivered")
    public ResponseEntity<VendorOrderResponse> delivered(
            Authentication authentication,
            @PathVariable @Positive(message = "Order id must be positive") Long orderId) {
        return ResponseEntity.ok(vendorOrderService.delivered(authentication.getName(), orderId));
    }

    @Operation(summary = "List the vendor's orders",
            description = "One page of the authenticated vendor's own orders, newest first. "
                    + "Optional {@code status} and {@code fromDate}/{@code toDate} filters.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Order page"),
            @ApiResponse(responseCode = "400", description = "Invalid page/size or date parameters")
    })
    @GetMapping
    public ResponseEntity<VendorOrderPageResponse> listOrders(
            Authentication authentication,
            @Parameter(description = "Filter by order status")
            @RequestParam(required = false) OrderStatusFilter status,
            @Parameter(description = "From date (inclusive), ISO-8601 local date")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @Parameter(description = "To date (exclusive), ISO-8601 local date")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @Parameter(description = "Zero-based page index")
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Page size, clamped to 1..100")
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        LocalDateTime from = fromDate != null ? fromDate.atStartOfDay() : null;
        LocalDateTime to = toDate != null ? toDate.atStartOfDay().plusDays(1) : null;
        com.flowerconnect.order.domain.Order.OrderStatus orderStatus =
                status != null ? com.flowerconnect.order.domain.Order.OrderStatus.valueOf(status.name()) : null;
        return ResponseEntity.ok(vendorOrderService.listOrders(authentication.getName(),
                orderStatus, from, to, pageable));
    }

    @Operation(summary = "Today's order summary",
            description = "Counts by status, today's order count, today's total value excluding "
                    + "cancelled/rejected orders, and expected COD cash for delivered orders. "
                    + "Today is the local date in the vendor's service-location timezone.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Today's summary")
    })
    @GetMapping("/summary")
    public ResponseEntity<VendorOrderSummaryResponse> todaySummary(
            Authentication authentication,
            @Parameter(description = "Optional local date (ISO-8601); defaults to today")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(vendorOrderService.todaySummary(authentication.getName(), date));
    }

    /**
     * Accepts the order-status enum constants as query-param values, so the
     * controller method signature stays free of the domain enum type.
     */
    public enum OrderStatusFilter {
        PLACED, PENDING_PAYMENT, CONFIRMED, PREPARING, OUT_FOR_DELIVERY,
        DELIVERED, CANCELLED, REJECTED, REFUNDED
    }
}
