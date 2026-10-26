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

package com.flowerconnect.billing.runner;

import com.flowerconnect.billing.service.CommissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Disabled-by-default one-off backfill runner for already delivered orders.
 * Enabled via {@code app.billing.backfill.enabled=true} or {@code APP_BILLING_BACKFILL_ENABLED=true}.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.billing.backfill.enabled", havingValue = "true", matchIfMissing = false)
@RequiredArgsConstructor
public class BillingBackfillRunner implements ApplicationRunner {

    private final CommissionService commissionService;

    @Override
    public void run(ApplicationArguments args) {
        log.info("Starting billing backfill runner for delivered orders...");
        int created = commissionService.backfillDeliveredOrders();
        log.info("Billing backfill runner finished. Created {} commission records.", created);
    }
}
