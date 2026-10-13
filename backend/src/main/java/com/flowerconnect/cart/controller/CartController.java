package com.flowerconnect.cart.controller;

import com.flowerconnect.cart.dto.*;
import com.flowerconnect.cart.service.CartService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * Customer cart API (plan task 5.2), at {@code /api/v1/cart}.
 *
 * <p>The namespace is restricted to the {@code CUSTOMER} role in
 * {@code SecurityConfig}; a vendor or admin account is refused with 403 before
 * this controller is reached. The caller is resolved from the JWT subject
 * (the principal's name / email) on every call, so no route accepts a customer
 * identifier and cross-customer access is not expressible in the request.
 *
 * <p>Routes:
 * <ul>
 *   <li>{@code GET /api/v1/cart} — retrieve the caller's cart (empty if none yet)</li>
 *   <li>{@code POST /api/v1/cart/items} — add or increment a line item</li>
 *   <li>{@code PATCH /api/v1/cart/items/{id}} — change quantity (0 deletes)</li>
 *   <li>{@code DELETE /api/v1/cart/items/{id}} — remove one line item</li>
 *   <li>{@code DELETE /api/v1/cart} — clear the entire cart</li>
 * </ul>
 *
 * <p>The cart is created lazily on the first successful add-to-cart; an empty
 * {@code GET} before any add returns a 200 with zero items.
 */
@Slf4j
@Validated
@Tag(name = "Customer cart", description = "Cart retrieval and line-item management for the authenticated customer")
@RestController
@RequestMapping("/api/v1/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @Operation(summary = "Retrieve the caller's cart",
            description = "Returns the authenticated customer's cart with per-item eligibility " +
                    "flags (purchasable / notPurchasableReason) and per-vendor subtotals. " +
                    "An empty cart is returned if the customer has not added anything yet.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cart retrieved"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "403", description = "Not a CUSTOMER account")
    })
    @GetMapping
    public ResponseEntity<CartResponse> getCart(Authentication authentication) {
        return ResponseEntity.ok(cartService.getCart(authentication.getName()));
    }

    @Operation(summary = "Add or increment a line item",
            description = "Adds a product to the caller's cart with the requested quantity. " +
                    "If the product is already in the cart, the quantity is incremented. " +
                    "The price snapshot is copied from the product's current base_price. " +
                    "Sufficient available stock is required; no reservation is made.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Item added or incremented"),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "403", description = "Not a CUSTOMER account, or vendor is not approved"),
            @ApiResponse(responseCode = "404", description = "Product or vendor not found"),
            @ApiResponse(responseCode = "409", description = "Vendor not accepting orders, or insufficient stock"),
            @ApiResponse(responseCode = "413", description = "Insufficient stock")
    })
    @PostMapping("/items")
    public ResponseEntity<CartItemResponse> addItem(
            Authentication authentication,
            @Valid @RequestBody AddCartItemRequest request) {
        CartItemResponse response = cartService.addItem(authentication.getName(), request);
        log.info("Customer {} added product {} (qty {}) to cart", authentication.getName(),
                request.getProductId(), request.getQuantity());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Update a line item's quantity",
            description = "Updates the quantity of one of the caller's cart items. " +
                    "A quantity of 0 deletes the line item. Increases are re-checked " +
                    "against current available stock.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Quantity updated"),
            @ApiResponse(responseCode = "204", description = "Item deleted (quantity was 0)"),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "403", description = "Not a CUSTOMER account"),
            @ApiResponse(responseCode = "404", description = "Cart item not found, or belongs to another customer"),
            @ApiResponse(responseCode = "409", description = "Insufficient stock")
    })
    @PatchMapping("/items/{id}")
    public ResponseEntity<CartItemResponse> updateItem(
            Authentication authentication,
            @PathVariable @Positive(message = "Cart item id must be positive") Long id,
            @Valid @RequestBody UpdateCartItemRequest request) {
        CartItemResponse response = cartService.updateItem(authentication.getName(), id, request);
        if (response == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Remove a line item",
            description = "Deletes one line item from the caller's cart. The cart itself " +
                    "is retained for future adds.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Item deleted"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "403", description = "Not a CUSTOMER account"),
            @ApiResponse(responseCode = "404", description = "Cart item not found, or belongs to another customer")
    })
    @DeleteMapping("/items/{id}")
    public ResponseEntity<Void> removeItem(
            Authentication authentication,
            @PathVariable @Positive(message = "Cart item id must be positive") Long id) {
        cartService.removeItem(authentication.getName(), id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Clear the cart",
            description = "Deletes every line item from the caller's cart. The cart row " +
                    "itself is retained (empty) for future adds.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Cart cleared"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "403", description = "Not a CUSTOMER account")
    })
    @DeleteMapping
    public ResponseEntity<Void> clearCart(Authentication authentication) {
        cartService.clearCart(authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
