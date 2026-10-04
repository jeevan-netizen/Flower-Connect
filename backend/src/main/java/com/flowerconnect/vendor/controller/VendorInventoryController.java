package com.flowerconnect.vendor.controller;

import com.flowerconnect.catalog.dto.InventorySummary;
import com.flowerconnect.inventory.dto.ExpiryDateRequest;
import com.flowerconnect.inventory.dto.LowStockPageResponse;
import com.flowerconnect.inventory.dto.LowStockThresholdRequest;
import com.flowerconnect.inventory.dto.StockAdjustmentRequest;
import com.flowerconnect.inventory.dto.StockInRequest;
import com.flowerconnect.inventory.dto.StockMovementPageResponse;
import com.flowerconnect.inventory.dto.StockOutRequest;
import com.flowerconnect.inventory.dto.StockWriteOffRequest;
import com.flowerconnect.inventory.service.InventoryService;
import com.flowerconnect.vendor.security.RequiresApprovedVendor;
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
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * Vendor-scoped stock control (plan task 3.6).
 *
 * <p>Every per-product route lives under
 * {@code /api/v1/vendors/products/{productId}/inventory}, nested in the
 * catalog's own path rather than behind a second {@code /vendor/}
 * prefix, so {@code SecurityConfig}'s {@code hasRole("FLORIST")} rule
 * covers it unchanged and the two authorization layers keep their
 * documented order (D-13): the namespace answers "is this a vendor
 * account?" and {@link RequiresApprovedVendor} answers "may this vendor
 * transact?". One product tree, one authorization story.
 *
 * <p>The one vendor-wide listing — the low-stock list — sits at
 * {@code /api/v1/vendors/inventory/low-stock}. It has no product to
 * hang off, and putting it under {@code /products/} would collide
 * structurally with {@code /products/{productId}/inventory}: both have
 * two segments after {@code products}, so the router would have to
 * break the tie on literal characters rather than on intent.
 *
 * <p>The vendor comes from the JWT subject on every call and the
 * movement's actor comes from the same identity — no route accepts
 * either, so neither is expressible in the request. A product belonging
 * to another vendor is a 403 and one that does not exist is a 404
 * (plan section 3).
 *
 * <p>The three mutations that move stock ({@code stock-in},
 * {@code stock-out}, {@code adjustments}, {@code write-offs}) answer
 * 409 with the {@code INSUFFICIENT_STOCK} code when the change would
 * leave {@code quantity} below {@code reserved_quantity}. They never
 * clamp the quantity to the floor: the vendor's books have to match the
 * movements that produced them.
 *
 * <p>{@code low-stock-threshold} and {@code expiry-date} are PUTs
 * because both are full replacements, and they write no stock movement —
 * they change an alert setting, not the stock.
 */
@Slf4j
@Validated
@Tag(name = "Vendor inventory", description = "Stock in/out, adjustments, write-offs and movement history")
@RequiresApprovedVendor
@RestController
@RequestMapping("/api/v1/vendors")
@RequiredArgsConstructor
public class VendorInventoryController {

    private final InventoryService inventoryService;

    // ------------------------------------------------------------------
    // Per-product stock control
    // ------------------------------------------------------------------

    @Operation(summary = "Read a product's stock level",
            description = "Returns quantity, reserved quantity, availability (quantity − reserved), "
                    + "the low-stock threshold and flag, and the expiry date.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Inventory found"),
            @ApiResponse(responseCode = "403", description = "Product belongs to another vendor, or the profile is not APPROVED"),
            @ApiResponse(responseCode = "404", description = "No such product")
    })
    @GetMapping("/products/{productId}/inventory")
    public ResponseEntity<InventorySummary> getInventory(
            Authentication authentication,
            @PathVariable @Positive(message = "Product id must be positive") Long productId) {
        return ResponseEntity.ok(inventoryService.get(authentication.getName(), productId));
    }

    @Operation(summary = "Record stock received",
            description = "Adds units to the quantity and appends one STOCK_IN movement with a positive "
                    + "delta. The movement's actor is the authenticated vendor.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Stock added"),
            @ApiResponse(responseCode = "400", description = "Quantity is missing or not positive"),
            @ApiResponse(responseCode = "403", description = "Product belongs to another vendor, or the profile is not APPROVED"),
            @ApiResponse(responseCode = "404", description = "No such product"),
            @ApiResponse(responseCode = "409", description = "The change would leave quantity below reserved_quantity")
    })
    @PostMapping("/products/{productId}/inventory/stock-in")
    public ResponseEntity<InventorySummary> stockIn(
            Authentication authentication,
            @PathVariable @Positive(message = "Product id must be positive") Long productId,
            @Valid @RequestBody StockInRequest request) {
        return ResponseEntity.ok(inventoryService.stockIn(authentication.getName(), productId, request));
    }

    @Operation(summary = "Record stock removed",
            description = "Removes units from the quantity and appends one STOCK_OUT movement with a negative "
                    + "delta. Refused with 409 when the removal would consume units reserved for pending orders.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Stock removed"),
            @ApiResponse(responseCode = "400", description = "Quantity is missing or not positive"),
            @ApiResponse(responseCode = "403", description = "Product belongs to another vendor, or the profile is not APPROVED"),
            @ApiResponse(responseCode = "404", description = "No such product"),
            @ApiResponse(responseCode = "409", description = "The change would leave quantity below reserved_quantity")
    })
    @PostMapping("/products/{productId}/inventory/stock-out")
    public ResponseEntity<InventorySummary> stockOut(
            Authentication authentication,
            @PathVariable @Positive(message = "Product id must be positive") Long productId,
            @Valid @RequestBody StockOutRequest request) {
        return ResponseEntity.ok(inventoryService.stockOut(authentication.getName(), productId, request));
    }

    @Operation(summary = "Adjust the recorded quantity",
            description = "Applies a signed correction and appends one ADJUSTMENT movement with that delta. "
                    + "A reason is mandatory and is stored on the movement. A zero correction is refused.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Quantity adjusted"),
            @ApiResponse(responseCode = "400", description = "Quantity is missing or zero, or the reason is blank"),
            @ApiResponse(responseCode = "403", description = "Product belongs to another vendor, or the profile is not APPROVED"),
            @ApiResponse(responseCode = "404", description = "No such product"),
            @ApiResponse(responseCode = "409", description = "The correction would leave quantity below reserved_quantity")
    })
    @PostMapping("/products/{productId}/inventory/adjustments")
    public ResponseEntity<InventorySummary> adjust(
            Authentication authentication,
            @PathVariable @Positive(message = "Product id must be positive") Long productId,
            @Valid @RequestBody StockAdjustmentRequest request) {
        return ResponseEntity.ok(inventoryService.adjust(authentication.getName(), productId, request));
    }

    @Operation(summary = "Write off lost stock",
            description = "Records a physical loss as one WASTE movement with a negative delta, the same "
                    + "vocabulary the expiry scheduler uses. A reason is mandatory and is stored on the movement.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Stock written off"),
            @ApiResponse(responseCode = "400", description = "Quantity is missing or not positive, or the reason is blank"),
            @ApiResponse(responseCode = "403", description = "Product belongs to another vendor, or the profile is not APPROVED"),
            @ApiResponse(responseCode = "404", description = "No such product"),
            @ApiResponse(responseCode = "409", description = "The write-off would leave quantity below reserved_quantity")
    })
    @PostMapping("/products/{productId}/inventory/write-offs")
    public ResponseEntity<InventorySummary> writeOff(
            Authentication authentication,
            @PathVariable @Positive(message = "Product id must be positive") Long productId,
            @Valid @RequestBody StockWriteOffRequest request) {
        return ResponseEntity.ok(inventoryService.writeOff(authentication.getName(), productId, request));
    }

    @Operation(summary = "Replace the low-stock alert threshold",
            description = "Sets the number at or below which the product appears in the low-stock list. "
                    + "Not a stock change: no movement is written.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Threshold updated"),
            @ApiResponse(responseCode = "400", description = "Threshold is missing or negative"),
            @ApiResponse(responseCode = "403", description = "Product belongs to another vendor, or the profile is not APPROVED"),
            @ApiResponse(responseCode = "404", description = "No such product")
    })
    @PutMapping("/products/{productId}/inventory/low-stock-threshold")
    public ResponseEntity<InventorySummary> updateLowStockThreshold(
            Authentication authentication,
            @PathVariable @Positive(message = "Product id must be positive") Long productId,
            @Valid @RequestBody LowStockThresholdRequest request) {
        return ResponseEntity.ok(
                inventoryService.updateLowStockThreshold(authentication.getName(), productId, request));
    }

    @Operation(summary = "Replace or clear the expiry date",
            description = "Sets the optional expiry date; a null body value clears it. Not a stock change: the "
                    + "expiry scheduler is what turns a reached date into a WASTE movement.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Expiry date updated"),
            @ApiResponse(responseCode = "403", description = "Product belongs to another vendor, or the profile is not APPROVED"),
            @ApiResponse(responseCode = "404", description = "No such product")
    })
    @PutMapping("/products/{productId}/inventory/expiry-date")
    public ResponseEntity<InventorySummary> updateExpiryDate(
            Authentication authentication,
            @PathVariable @Positive(message = "Product id must be positive") Long productId,
            @Valid @RequestBody ExpiryDateRequest request) {
        return ResponseEntity.ok(inventoryService.updateExpiryDate(authentication.getName(), productId, request));
    }

    @Operation(summary = "Read a product's movement history",
            description = "One page of the append-only stock movement log for the product, newest first. "
                    + "Each row carries the signed delta, the reason and the actor who caused it.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "History page"),
            @ApiResponse(responseCode = "403", description = "Product belongs to another vendor, or the profile is not APPROVED"),
            @ApiResponse(responseCode = "404", description = "No such product")
    })
    @GetMapping("/products/{productId}/inventory/movements")
    public ResponseEntity<StockMovementPageResponse> listMovements(
            Authentication authentication,
            @PathVariable @Positive(message = "Product id must be positive") Long productId,
            @Parameter(description = "Zero-based page index")
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Page size, clamped by the service to 1..100")
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(
                inventoryService.listMovements(authentication.getName(), productId, page, size));
    }

    // ------------------------------------------------------------------
    // Vendor-wide listing
    // ------------------------------------------------------------------

    @Operation(summary = "List the vendor's low-stock products",
            description = "One page of the vendor's products whose availability (quantity − reserved) has "
                    + "reached or fallen below their own low-stock threshold. Never contains another "
                    + "vendor's products.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Low-stock page"),
            @ApiResponse(responseCode = "403", description = "The profile is not APPROVED")
    })
    @GetMapping("/inventory/low-stock")
    public ResponseEntity<LowStockPageResponse> listLowStock(
            Authentication authentication,
            @Parameter(description = "Zero-based page index")
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Page size, clamped by the service to 1..100")
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(inventoryService.listLowStock(authentication.getName(), page, size));
    }
}