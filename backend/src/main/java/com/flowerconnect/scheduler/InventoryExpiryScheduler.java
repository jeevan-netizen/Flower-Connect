package com.flowerconnect.scheduler;

import com.flowerconnect.inventory.service.InventoryExpiryService;
import com.flowerconnect.inventory.service.InventoryExpiryService.SweepResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Triggers the inventory expiry sweep on a schedule (plan task 3.7).
 *
 * <p>Thin on purpose, exactly like {@link RefreshTokenCleanupScheduler}: the component
 * owns the cron and nothing else, so the rule that decides what "expired" means and what
 * gets written lives in one place, {@link InventoryExpiryService}, and can be exercised
 * with a fake clock without waiting for a schedule.
 *
 * <p>The schedule is {@code app.expiry-sweep-cron} (default 03:00 daily) rather than a
 * literal, so a deployment can move it without a rebuild.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryExpiryScheduler {

    private final InventoryExpiryService inventoryExpiryService;

    @Scheduled(cron = "#{@appProperties.expirySweepCron}")
    public void sweepExpiredStock() {
        log.debug("Starting scheduled inventory expiry sweep");
        SweepResult result = inventoryExpiryService.sweepExpiredStock();
        if (result.candidates() == 0) {
            log.debug("Scheduled inventory expiry sweep found nothing to do");
        } else {
            log.info("Scheduled inventory expiry sweep examined {} row(s), wrote off {} product(s), delisted {} product(s)",
                    result.candidates(), result.productsWrittenOff(), result.productsDelisted());
        }
    }
}
