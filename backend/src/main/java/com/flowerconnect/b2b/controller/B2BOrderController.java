package com.flowerconnect.b2b.controller;

import com.flowerconnect.b2b.dto.B2BOrderActionRequest;
import com.flowerconnect.b2b.dto.B2BOrderItemRequest;
import com.flowerconnect.b2b.dto.B2BOrderPageResponse;
import com.flowerconnect.b2b.dto.B2BOrderResponse;
import com.flowerconnect.b2b.dto.B2BProductSummary;
import com.flowerconnect.b2b.dto.PlaceB2BOrderRequest;
import com.flowerconnect.b2b.dto.SellerSummary;
import com.flowerconnect.b2b.service.B2BEbillService;
import com.flowerconnect.b2b.service.B2BOrderService;
import com.flowerconnect.b2b.domain.B2BOrder.Status;
import com.flowerconnect.vendor.security.RequiresApprovedVendor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * B2B orders between approved vendors (Stage 1 of the B2B module).
 *
 * <p>Base path is {@code /api/v1/vendors/b2b/...}, inside the existing
 * {@code /api/v1/vendors/**} namespace so the {@code hasRole("FLORIST")}
 * rule in {@code SecurityConfig} applies first, and
 * {@link com.flowerconnect.vendor.security.RequiresApprovedVendor} adds
 * the stricter approval boundary (D-13).
 *
 * <p>The vendor is resolved from the JWT subject on every call; no route
 * accepts a vendor identifier for the caller, so cross-vendor access is
 * not expressible in the request. Order participants (buyer/seller) are
 * checked in the service layer.
 */
@Slf4j
@Validated
@Tag(name = "B2B orders", description = "Wholesale orders between approved vendors")
@RequiresApprovedVendor
@RestController
@RequestMapping("/api/v1/vendors/b2b")
@RequiredArgsConstructor
public class B2BOrderController {

    private final B2BOrderService b2bOrderService;
    private final B2BEbillService b2bEbillService;

    @Operation(summary = "Search other approved vendors",
            description = "Returns approved vendors (excluding the caller) whose business name "
                    + "matches the optional query. Only approved vendors may be sellers in a B2B order.")
    @GetMapping("/sellers")
    public ResponseEntity<List<SellerSummary>> searchSellers(
            Authentication authentication,
            @Parameter(description = "Optional substring match on business name")
            @RequestParam(required = false) String query) {
        List<SellerSummary> sellers = b2bOrderService.searchSellers(authentication.getName(), query);
        return ResponseEntity.ok(sellers);
    }

    @Operation(summary = "List a seller's orderable products",
            description = "Returns the seller's ACTIVE products with catalog price and available "
                    + "stock, for building a B2B order.")
    @GetMapping("/sellers/{sellerId}/products")
    public ResponseEntity<List<B2BProductSummary>> listSellerProducts(
            Authentication authentication,
            @Parameter(description = "Seller vendor profile id")
            @PathVariable @Positive(message = "Seller id must be positive") Long sellerId) {
        List<B2BProductSummary> products = b2bOrderService.listSellerProducts(
                authentication.getName(), sellerId);
        return ResponseEntity.ok(products);
    }

    @Operation(summary = "Place a B2B order",
            description = "Creates a new PLACED order from the authenticated buyer to the selected "
                    + "seller. Validates that the buyer is not the seller, every product belongs to "
                    + "the seller and is active, and quantities are positive with sufficient stock. "
                    + "Prices and totals are computed server-side and snapshotted.")
    @PostMapping("/orders")
    public ResponseEntity<B2BOrderResponse> placeOrder(
            Authentication authentication,
            @Valid @RequestBody PlaceB2BOrderRequest request) {
        B2BOrderResponse response = b2bOrderService.placeOrder(authentication.getName(), request);
        log.info("Placed B2B order {} by buyer {}", response.getOrderNo(),
                authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "List the caller's B2B orders",
            description = "Returns the caller's orders as buyer or seller, newest first. "
                    + "Optional status and role filters. Page size is clamped to 1..100.")
    @GetMapping("/orders")
    public ResponseEntity<B2BOrderPageResponse> listOrders(
            Authentication authentication,
            @Parameter(description = "BUYER (default) or SELLER")
            @RequestParam(required = false) String role,
            @Parameter(description = "Filter by lifecycle status")
            @RequestParam(required = false) Status status,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "Page must be non-negative") int page,
            @RequestParam(defaultValue = "20") @Positive(message = "Size must be positive") int size) {
        return ResponseEntity.ok(b2bOrderService.listOrders(
                authentication.getName(), role, status, page, size));
    }

    @Operation(summary = "Read one B2B order",
            description = "Returns a single B2B order. Only the buyer or the seller may read it.")
    @GetMapping("/orders/{orderId}")
    public ResponseEntity<B2BOrderResponse> getOrder(
            Authentication authentication,
            @Parameter(description = "B2B order id")
            @PathVariable @Positive(message = "Order id must be positive") Long orderId) {
        return ResponseEntity.ok(b2bOrderService.getOrder(authentication.getName(), orderId));
    }

    @Operation(summary = "Accept a PLACED order",
            description = "Seller only. Moves the order to ACCEPTED and deducts seller stock "
                    + "through the inventory service. Rejected with INSUFFICIENT_STOCK if stock "
                    + "is no longer sufficient.")
    @PostMapping("/orders/{orderId}/accept")
    public ResponseEntity<B2BOrderResponse> accept(
            Authentication authentication,
            @Parameter(description = "B2B order id")
            @PathVariable @Positive(message = "Order id must be positive") Long orderId) {
        return ResponseEntity.ok(b2bOrderService.accept(authentication.getName(), orderId));
    }

    @Operation(summary = "Reject a PLACED order",
            description = "Seller only. Moves the order to REJECTED with an optional reason.")
    @PostMapping("/orders/{orderId}/reject")
    public ResponseEntity<B2BOrderResponse> reject(
            Authentication authentication,
            @Parameter(description = "B2B order id")
            @PathVariable @Positive(message = "Order id must be positive") Long orderId,
            @Valid @RequestBody B2BOrderActionRequest request) {
        return ResponseEntity.ok(b2bOrderService.reject(authentication.getName(), orderId,
                request.getReason()));
    }

    @Operation(summary = "Cancel a B2B order",
            description = "Buyer may cancel a PLACED order; either party may cancel an ACCEPTED "
                    + "order, in which case the seller's deducted stock is restored. No "
                    + "cancellation after SHIPPED.")
    @PostMapping("/orders/{orderId}/cancel")
    public ResponseEntity<B2BOrderResponse> cancel(
            Authentication authentication,
            @Parameter(description = "B2B order id")
            @PathVariable @Positive(message = "Order id must be positive") Long orderId,
            @Valid @RequestBody B2BOrderActionRequest request) {
        return ResponseEntity.ok(b2bOrderService.cancel(authentication.getName(), orderId,
                request.getReason()));
    }

    @Operation(summary = "Ship an ACCEPTED order",
            description = "Seller only. Moves the order to SHIPPED. Requires a shipment reference; "
                    + "accepts an optional shipped-date note.")
    @PostMapping("/orders/{orderId}/ship")
    public ResponseEntity<B2BOrderResponse> ship(
            Authentication authentication,
            @Parameter(description = "B2B order id")
            @PathVariable @Positive(message = "Order id must be positive") Long orderId,
            @Valid @RequestBody B2BOrderActionRequest request) {
        return ResponseEntity.ok(b2bOrderService.ship(authentication.getName(), orderId, request));
    }

    @Operation(summary = "Deliver a SHIPPED order",
            description = "Seller only. Moves the order to DELIVERED. The buyer's inventory is not "
                    + "increased; payment remains UNPAID in Stage 1.")
    @PostMapping("/orders/{orderId}/deliver")
    public ResponseEntity<B2BOrderResponse> deliver(
            Authentication authentication,
            @Parameter(description = "B2B order id")
            @PathVariable @Positive(message = "Order id must be positive") Long orderId) {
        return ResponseEntity.ok(b2bOrderService.deliver(authentication.getName(), orderId));
    }

    @Operation(summary = "Download the e-bill PDF",
            description = "Returns the e-bill PDF for a delivered B2B order. Only the buyer or the "
                    + "seller on the order may download it; only DELIVERED orders have an e-bill. "
                    + "The response is application/pdf with a download filename based on the bill "
                    + "number.")
    @GetMapping("/orders/{orderId}/e-bill")
    public ResponseEntity<byte[]> eBill(
            Authentication authentication,
            @Parameter(description = "B2B order id")
            @PathVariable @Positive(message = "Order id must be positive") Long orderId) {
        byte[] pdf = b2bEbillService.generateEbillPdf(authentication.getName(), orderId);
        B2BOrderResponse order = b2bOrderService.getOrder(authentication.getName(), orderId);
        String billNumber = order.getBillNumber();
        String filename = (billNumber != null ? billNumber : "order-" + orderId) + ".pdf";
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(filename)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(pdf);
    }
}
