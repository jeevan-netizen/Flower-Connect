package com.flowerconnect.payment.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowerconnect.config.AppProperties;
import com.flowerconnect.payment.domain.Payment;
import com.flowerconnect.payment.dto.WebhookEvent;
import com.flowerconnect.payment.repository.PaymentRepository;
import com.flowerconnect.payment.service.PaymentService;
import com.flowerconnect.payment.service.PaymentService.WebhookResult;
import com.flowerconnect.payment.service.WebhookSignatureService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Simulated/test-mode payment provider (Phase 7, task 7.4 part 2).
 *
 * <p>Requires no external account or network and exposes only {@link #pay}
 * and {@link #fail}. It generates a correctly signed webhook event and
 * delivers it through the same raw-body signature verification and webhook
 * handling path as {@code POST /api/v1/payments/webhook}. No card numbers,
 * CVV, card prefixes or other card-like fields are created or accepted.
 *
 * <p>Registered only outside the {@code prod} profile.
 */
@Slf4j
@Service
@Profile("!prod")
@RequiredArgsConstructor
public class SimulatedPaymentProvider {

    private final PaymentService paymentService;
    private final PaymentRepository paymentRepository;
    private final WebhookSignatureService signatureService;
    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    /**
     * Simulates a successful payment for the group owning {@code paymentGroupId}.
     */
    public WebhookResult pay(String paymentGroupId) {
        return deliver(paymentGroupId, "success", "payment.succeeded");
    }

    /**
     * Simulates a failed payment for the group owning {@code paymentGroupId}.
     */
    public WebhookResult fail(String paymentGroupId) {
        return deliver(paymentGroupId, "failure", "payment.failed");
    }

    private WebhookResult deliver(String paymentGroupId, String outcome, String eventType) {
        BigDecimal amount = paymentRepository.findByPaymentGroupId(paymentGroupId).stream()
                .map(Payment::getAmount)
                .min(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);

        String providerEventId = "sim_" + UUID.randomUUID();
        long now = Instant.now(clock).getEpochSecond();
        String timestamp = Long.toString(now);

        WebhookEvent event = WebhookEvent.builder()
                .providerEventId(providerEventId)
                .paymentGroupId(paymentGroupId)
                .eventType(eventType)
                .outcome(outcome)
                .amount(amount)
                .build();

        byte[] body;
        try {
            body = objectMapper.writeValueAsBytes(event);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Unable to encode simulated webhook event", e);
        }

        String signature = signatureService.sign(appProperties.getWebhookSecret(), timestamp, body);

        // Deliver through the same verification path as the public webhook.
        if (!signatureService.isValid(appProperties.getWebhookSecret(), timestamp, signature, body)) {
            throw new IllegalStateException("Simulated webhook signature verification failed");
        }

        log.info("Simulated {} event for group {} (event {})", outcome, paymentGroupId, providerEventId);
        return paymentService.processProviderEvent(paymentGroupId, providerEventId, eventType, outcome, amount);
    }
}
