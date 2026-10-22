package com.flowerconnect.scheduler;

import com.flowerconnect.order.service.AutoRejectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Triggers the auto-reject job on a schedule (plan Phase 6, task 6.5).
 *
 * <p>Thin on purpose, exactly like {@link
 * com.flowerconnect.scheduler.InventoryExpiryScheduler}: the component owns
 * the cron and nothing else, so the rule that decides what is "stale" and
 * what gets cancelled lives in {@link AutoRejectService} and can be exercised
 * with a fake clock without waiting for a schedule.
 *
 * <p>The schedule is {@code app.auto-reject-cron} (default hourly) rather than
 * a literal, so a deployment can move it without a rebuild. Set to {@code -}
 * (Spring's {@code CRON_DISABLED}) to turn the job off.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AutoRejectScheduler {

    private final AutoRejectService autoRejectService;

    @Scheduled(cron = "#{@appProperties.autoRejectCron}")
    public void runAutoReject() {
        log.debug("Starting scheduled auto-reject job");
        autoRejectService.runAutoReject();
        log.debug("Scheduled auto-reject job finished");
    }
}
