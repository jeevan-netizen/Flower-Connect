/*
 * Copyright 2026 FlowerConnect
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.flowerconnect.scheduler;

import com.flowerconnect.billing.service.SettlementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

/**
 * Triggers the weekly settlement cycle on a schedule (plan Phase 10, task 10.4).
 *
 * <p>Thin scheduler delegating to {@link SettlementService}. It computes the previous
 * calendar week (Monday to Sunday) using the injected {@link Clock} and runs
 * settlements across all active vendors.
 *
 * <p>The schedule is {@code app.settlement-cron} (default weekly Sunday midnight UTC)
 * rather than a literal. Set to {@code -} (Spring's {@code CRON_DISABLED}) to turn
 * the job off (e.g. in test profile).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementScheduler {

    private final SettlementService settlementService;
    private final Clock clock;

    @Scheduled(cron = "#{@appProperties.settlementCron}")
    public void runWeeklySettlement() {
        LocalDate today = LocalDate.now(clock);
        LocalDate previousSunday = today.minusDays(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
        LocalDate previousMonday = previousSunday.minusDays(6);

        log.info("Starting scheduled weekly settlement run for period {} to {}", previousMonday, previousSunday);
        settlementService.runSettlementForAllVendors(previousMonday, previousSunday);
        log.info("Scheduled weekly settlement run finished for period {} to {}", previousMonday, previousSunday);
    }
}
