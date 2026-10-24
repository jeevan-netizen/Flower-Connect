package com.flowerconnect.scheduler;

import com.flowerconnect.order.service.PaymentExpiryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Triggers the payment-expiry job on a schedule (plan Phase 7, task 7.5).
 *
 * <p>Thin on purpose, exactly like {@link AutoRejectScheduler}: the component
 * owns the cron and nothing else, so the rule that decides what is "stale" and
 * what gets cancelled lives in {@link PaymentExpiryService} and can be
 * exercised with a fake clock without waiting for a schedule.
 *
 * <p>The schedule is {@code app.payment-expiry-cron} rather than a literal, so
 * a deployment can move it without a rebuild. Set to {@code -} (Spring's
 * {@code CRON_DISABLED}) to turn the job off.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentExpiryScheduler {

    private final PaymentExpiryService paymentExpiryService;

    @Scheduled(cron = "#{@appProperties.paymentExpiryCron}")
    public void runPaymentExpiry() {
        log.debug("Starting scheduled payment-expiry job");
        paymentExpiryService.runPaymentExpiry();
        log.debug("Scheduled payment-expiry job finished");
    }
}
