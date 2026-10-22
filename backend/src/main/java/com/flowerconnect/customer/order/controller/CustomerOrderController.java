package com.flowerconnect.customer.order.controller;

import com.flowerconnect.customer.order.dto.CustomerOrderListItem;
import com.flowerconnect.customer.order.dto.CustomerOrderPageResponse;
import com.flowerconnect.customer.order.dto.CustomerOrderResponse;
import com.flowerconnect.customer.order.service.CustomerOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Customer order history and detail (plan Phase 6, task 6.3), at
 * {@code /api/v1/orders}.
 *
 * <p>The namespace is restricted to the {@code CUSTOMER} role in
 * {@code SecurityConfig}: a florist or admin account is refused with
 * 403 before the request reaches this controller. The caller is
 * resolved from the JWT subject on every call, so no route accepts a
 * customer identifier and cross-customer access is not expressible in
 * the request at all — an order that belongs to another customer is a
 * 403, while one that does not exist is a 404.
 *
 * <p>These endpoints are read-only: they never change order status.
 * Every status change continues to go through {@code OrderStateMachine}.
 *
 * <p>The controller is thin: it resolves the caller, enforces ownership,
 * and delegates to {@link CustomerOrderService}, which maps entities to
 * response DTOs. No controller method sets {@code Order#status} directly.
 */
@Slf4j
@Validated
@Tag(name = "Customer orders", description = "Order history and detail for the authenticated customer")
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class CustomerOrderController {

    private final CustomerOrderService customerOrderService;

    @Operation(summary = "List the caller's orders",
            description = "Returns one page of the authenticated customer's own orders, newest "
                    + "first, with an optional status filter. Page size is clamped to 1..100.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The caller's order page"),
            @ApiResponse(responseCode = "400", description = "Invalid page or size"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "403", description = "Not a CUSTOMER account")
    })
    @GetMapping
    public ResponseEntity<CustomerOrderPageResponse> listOrders(
            Authentication authentication,
            @Parameter(description = "Filter by order status")
            @RequestParam(required = false) OrderStatusFilter status,
            @Parameter(description = "Zero-based page index")
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Page size, clamped to 1..100")
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        com.flowerconnect.order.domain.Order.OrderStatus orderStatus =
                status != null ? com.flowerconnect.order.domain.Order.OrderStatus.valueOf(status.name()) : null;
        return ResponseEntity.ok(customerOrderService.listOrders(authentication.getName(),
                orderStatus, pageable));
    }

    @Operation(summary = "Read one of the caller's orders",
            description = "Returns the full detail of one order owned by the authenticated "
                    + "customer, including items, fees and the status timeline.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Order found"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "403", description = "Not a CUSTOMER account"),
            @ApiResponse(responseCode = "404", description = "No such order, or it belongs to another customer")
    })
    @GetMapping("/{orderId}")
    public ResponseEntity<CustomerOrderResponse> getOrder(
            Authentication authentication,
            @PathVariable @Positive(message = "Order id must be positive") Long orderId) {
        return ResponseEntity.ok(customerOrderService.getOrder(authentication.getName(), orderId));
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
