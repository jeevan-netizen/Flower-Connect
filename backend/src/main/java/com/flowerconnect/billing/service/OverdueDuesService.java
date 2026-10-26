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

package com.flowerconnect.billing.service;

import com.flowerconnect.billing.dto.OverdueVendorResponse;
import com.flowerconnect.billing.repository.SettlementEntryRepository;
import com.flowerconnect.config.AppProperties;
import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.repository.VendorProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service evaluating overdue billing dues for vendors (plan Phase 10, task 10.7).
 *
 * <p>A vendor is considered overdue when:
 * <ol>
 *   <li>Their unattached + PENDING net balance is negative (vendor owes platform money).</li>
 *   <li>The oldest unattached or PENDING negative entry timestamp is older than {@code app.overdue-days} (default 30 days).</li>
 * </ol>
 *
 * <p>In v1, overdue status is an advisory flag and admin report only; no automatic suspension is enforced.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OverdueDuesService {

    private final SettlementEntryRepository settlementEntryRepository;
    private final VendorProfileRepository vendorProfileRepository;
    private final AppProperties appProperties;
    private final Clock clock;

    /**
     * Finds all vendors with overdue dues, ordered by days overdue descending.
     */
    @Transactional(readOnly = true)
    public List<OverdueVendorResponse> getOverdueVendors() {
        List<Long> vendorIds = settlementEntryRepository.findDistinctVendorIdsWithOpenNegativeEntries();
        if (vendorIds.isEmpty()) {
            return List.of();
        }

        LocalDate today = LocalDate.now(clock);
        int overdueThresholdDays = appProperties.getOverdueDays();

        Map<Long, VendorProfile> vendorMap = vendorProfileRepository.findAllById(vendorIds).stream()
                .collect(Collectors.toMap(VendorProfile::getId, v -> v));

        List<OverdueVendorResponse> overdueList = new ArrayList<>();

        for (Long vendorId : vendorIds) {
            BigDecimal outstandingBalance = settlementEntryRepository.calculateOutstandingBalance(vendorId);
            if (outstandingBalance != null && outstandingBalance.compareTo(BigDecimal.ZERO) < 0) {
                Optional<LocalDateTime> oldestOpt = settlementEntryRepository.findOldestOpenNegativeEntryDate(vendorId);
                if (oldestOpt.isPresent()) {
                    LocalDateTime oldestDate = oldestOpt.get();
                    long daysOverdue = ChronoUnit.DAYS.between(oldestDate.toLocalDate(), today);
                    if (daysOverdue >= overdueThresholdDays) {
                        VendorProfile profile = vendorMap.get(vendorId);
                        String businessName = profile != null ? profile.getBusinessName() : "Vendor #" + vendorId;
                        overdueList.add(OverdueVendorResponse.builder()
                                .vendorId(vendorId)
                                .vendorBusinessName(businessName)
                                .outstandingBalance(outstandingBalance)
                                .outstandingDues(outstandingBalance.abs())
                                .daysOverdue(daysOverdue)
                                .oldestDuesDate(oldestDate)
                                .build());
                    }
                }
            }
        }

        overdueList.sort(Comparator.comparing(OverdueVendorResponse::getDaysOverdue).reversed()
                .thenComparing(OverdueVendorResponse::getVendorId));

        return overdueList;
    }
}
