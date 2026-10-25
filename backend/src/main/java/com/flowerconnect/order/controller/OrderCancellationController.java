package com.flowerconnect.order.controller;

import com.flowerconnect.exception.BusinessException;
import com.flowerconnect.order.domain.ActorType;
import com.flowerconnect.order.dto.CancelOrderRequest;
import com.flowerconnect.order.dto.CancelOrderResponse;
import com.flowerconnect.order.service.CancellationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Slf4j
@Validated
@Tag(name = "Order cancellation", description = "Cancel an order (plan Phase 8, task 8.2)")
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderCancellationController {

    private final CancellationService cancellationService;

    /**
     * Cancel an order. A customer may cancel their own order, a vendor may
     * cancel (reject) their own order — including a suspended vendor's
     * in-flight orders per D-6 — and an admin may cancel any order. The
     * target status follows the section 6.1 transition table: a vendor
     * acting on a {@code PLACED} order rejects it; every other cancellation
     * targets {@code CANCELLED}.
     */
    @Operation(summary = "Cancel an order",
            description = "Transitions an order to {@code CANCELLED} (or {@code REJECTED} when a "
                    + "vendor acts on a {@code PLACED} order), releasing or restocking reserved "
                    + "stock per section 6.2. A paid online order is moved to {@code REFUND_PENDING}.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Order cancelled"),
            @ApiResponse(responseCode = "400", description = "Missing or invalid reason"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "403", description = "Not allowed to cancel this order"),
            @ApiResponse(responseCode = "404", description = "No such order"),
            @ApiResponse(responseCode = "409", description = "Illegal transition for the current state")
    })
    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<CancelOrderResponse> cancel(
            Authentication authentication,
            @PathVariable @Positive(message = "Order id must be positive") Long orderId,
            @RequestBody CancelOrderRequest request) {
        ActorType actorType = actorType(authentication);
        CancelOrderResponse response = cancellationService.cancel(
                authentication.getName(), actorType, orderId, request == null ? null : request.getReason());
        return ResponseEntity.ok(response);
    }

    private static ActorType actorType(Authentication authentication) {
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            String role = authority.getAuthority();
            if ("ROLE_ADMIN".equals(role)) {
                return ActorType.ADMIN;
            }
            if ("ROLE_FLORIST".equals(role)) {
                return ActorType.VENDOR;
            }
            if ("ROLE_CUSTOMER".equals(role)) {
                return ActorType.CUSTOMER;
            }
        }
        throw BusinessException.forbidden("No recognised role on the authenticated principal");
    }
}
