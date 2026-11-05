package com.flowerconnect.payment.controller;

import com.flowerconnect.domain.User;
import com.flowerconnect.exception.BusinessException;
import com.flowerconnect.order.domain.Order;
import com.flowerconnect.order.repository.OrderRepository;
import com.flowerconnect.payment.dto.WebhookResponse;
import com.flowerconnect.payment.provider.SimulatedPaymentProvider;
import com.flowerconnect.payment.repository.PaymentRepository;
import com.flowerconnect.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.constraints.Positive;

/**
 * Simulated test-mode pay/fail endpoints (Phase 7, task 7.4 part 2).
 *
 * <p>Registered only outside the {@code prod} profile. Requires customer
 * authentication and ownership of the order; the generated event is delivered
 * through the same signed webhook handling path as
 * {@code POST /api/v1/payments/webhook}.
 */
@Slf4j
@Tag(name = "Payment simulator", description = "Test-mode pay/fail (simulated provider), !prod profile only, customer auth + ownership")
@RequiredArgsConstructor
@Profile("!prod")
@RestController
@RequestMapping("/api/v1/payments")
public class SimulatorController {

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final PaymentRepository paymentRepository;
    private final SimulatedPaymentProvider provider;

    @PostMapping("/simulator/{orderId}/pay")
    @Transactional
    @Operation(summary = "Simulate a successful payment",
            description = "Test-mode: delivers a successful provider event through the signed webhook path "
                    + "for the authenticated customer's own order. Requires customer authentication and "
                    + "order ownership. !prod profile only.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Payment simulated"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "403", description = "Order belongs to another customer"),
            @ApiResponse(responseCode = "404", description = "Order or payment not found")
    })
    public ResponseEntity<WebhookResponse> pay(Authentication authentication,
                                               @PathVariable @Positive(message = "Order id must be positive") Long orderId) {
        return simulate(authentication, orderId, "pay");
    }

    @PostMapping("/simulator/{orderId}/fail")
    @Transactional
    @Operation(summary = "Simulate a failed payment",
            description = "Test-mode: delivers a failed provider event through the signed webhook path "
                    + "for the authenticated customer's own order. Requires customer authentication and "
                    + "order ownership. !prod profile only.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Payment failure simulated"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "403", description = "Order belongs to another customer"),
            @ApiResponse(responseCode = "404", description = "Order or payment not found")
    })
    public ResponseEntity<WebhookResponse> fail(Authentication authentication,
                                                @PathVariable @Positive(message = "Order id must be positive") Long orderId) {
        return simulate(authentication, orderId, "fail");
    }

    private ResponseEntity<WebhookResponse> simulate(Authentication authentication, Long orderId, String action) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> BusinessException.notFound("Order not found: " + orderId));

        // Resolve the authenticated customer by email (JWT subject) and compare
        // user IDs via scalar queries, avoiding lazy-loading Order.user.
        User actor = userRepository.findByEmailWithRole(authentication.getName())
                .orElseThrow(() -> BusinessException.notFound("Authenticated user not found"));
        Long orderCustomerId = orderRepository.findCustomerIdByOrderId(orderId)
                .orElseThrow(() -> BusinessException.notFound("Order not found: " + orderId));
        if (!orderCustomerId.equals(actor.getId())) {
            throw BusinessException.forbidden("Order " + orderId + " belongs to another customer");
        }

        String paymentGroupId = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> BusinessException.notFound("Payment not found for order: " + orderId))
                .getPaymentGroupId();

        com.flowerconnect.payment.service.PaymentService.WebhookResult result =
                "pay".equals(action) ? provider.pay(paymentGroupId) : provider.fail(paymentGroupId);

        log.info("Customer {} simulated {} for order {} (group {})",
                authentication.getName(), action, orderId, paymentGroupId);
        return ResponseEntity.ok(WebhookResponse.from(result));
    }
}
