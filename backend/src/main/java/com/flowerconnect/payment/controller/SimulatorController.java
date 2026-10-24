package com.flowerconnect.payment.controller;

import com.flowerconnect.exception.BusinessException;
import com.flowerconnect.order.domain.Order;
import com.flowerconnect.order.repository.OrderRepository;
import com.flowerconnect.payment.dto.WebhookResponse;
import com.flowerconnect.payment.provider.SimulatedPaymentProvider;
import com.flowerconnect.payment.repository.PaymentRepository;
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
@RequiredArgsConstructor
@Profile("!prod")
@RestController
@RequestMapping("/api/v1/payments")
public class SimulatorController {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final SimulatedPaymentProvider provider;

    @PostMapping("/simulator/{orderId}/pay")
    @Transactional(readOnly = true)
    public ResponseEntity<WebhookResponse> pay(Authentication authentication,
                                               @PathVariable @Positive(message = "Order id must be positive") Long orderId) {
        return simulate(authentication, orderId, "pay");
    }

    @PostMapping("/simulator/{orderId}/fail")
    @Transactional(readOnly = true)
    public ResponseEntity<WebhookResponse> fail(Authentication authentication,
                                                @PathVariable @Positive(message = "Order id must be positive") Long orderId) {
        return simulate(authentication, orderId, "fail");
    }

    private ResponseEntity<WebhookResponse> simulate(Authentication authentication, Long orderId, String action) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> BusinessException.notFound("Order not found: " + orderId));
        if (!order.getUser().getEmail().equals(authentication.getName())) {
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
