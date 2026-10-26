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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SettlementSchedulerTest {

    @Mock
    private SettlementService settlementService;

    @Test
    @DisplayName("runWeeklySettlement computes previous calendar week Monday to Sunday when triggered on Sunday")
    void runWeeklySettlement_triggeredOnSunday() {
        // Sunday Oct 25, 2026: last completed Sunday was Oct 18, Monday was Oct 12
        Clock clock = Clock.fixed(Instant.parse("2026-10-25T00:00:00Z"), ZoneOffset.UTC);
        SettlementScheduler scheduler = new SettlementScheduler(settlementService, clock);

        scheduler.runWeeklySettlement();

        verify(settlementService).runSettlementForAllVendors(
                LocalDate.of(2026, 10, 12),
                LocalDate.of(2026, 10, 18));
    }

    @Test
    @DisplayName("runWeeklySettlement computes previous calendar week Monday to Sunday when triggered on Monday")
    void runWeeklySettlement_triggeredOnMonday() {
        // Monday Oct 26, 2026: last completed Sunday was Oct 25, Monday was Oct 19
        Clock clock = Clock.fixed(Instant.parse("2026-10-26T00:00:00Z"), ZoneOffset.UTC);
        SettlementScheduler scheduler = new SettlementScheduler(settlementService, clock);

        scheduler.runWeeklySettlement();

        verify(settlementService).runSettlementForAllVendors(
                LocalDate.of(2026, 10, 19),
                LocalDate.of(2026, 10, 25));
    }
}
