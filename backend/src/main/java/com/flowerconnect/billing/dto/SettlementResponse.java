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

package com.flowerconnect.billing.dto;

import com.flowerconnect.billing.domain.Settlement;
import com.flowerconnect.billing.domain.SettlementDirection;
import com.flowerconnect.billing.domain.SettlementStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
public class SettlementResponse {
    private Long id;
    private Long vendorId;
    private String vendorBusinessName;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private BigDecimal totalCredits;
    private BigDecimal totalDues;
    private BigDecimal netAmount;
    private SettlementDirection direction;
    private SettlementStatus status;
    private String adminReferenceNote;
    private Long settledByUserId;
    private String settledByEmail;
    private LocalDateTime settledAt;
    private LocalDateTime createdAt;

    public static SettlementResponse fromEntity(Settlement settlement) {
        if (settlement == null) {
            return null;
        }
        return SettlementResponse.builder()
                .id(settlement.getId())
                .vendorId(settlement.getVendor() != null ? settlement.getVendor().getId() : null)
                .vendorBusinessName(settlement.getVendor() != null ? settlement.getVendor().getBusinessName() : null)
                .periodStart(settlement.getPeriodStart())
                .periodEnd(settlement.getPeriodEnd())
                .totalCredits(settlement.getTotalCredits())
                .totalDues(settlement.getTotalDues())
                .netAmount(settlement.getNetAmount())
                .direction(settlement.getDirection())
                .status(settlement.getStatus())
                .adminReferenceNote(settlement.getAdminReferenceNote())
                .settledByUserId(settlement.getSettledBy() != null ? settlement.getSettledBy().getId() : null)
                .settledByEmail(settlement.getSettledBy() != null ? settlement.getSettledBy().getEmail() : null)
                .settledAt(settlement.getSettledAt())
                .createdAt(settlement.getCreatedAt())
                .build();
    }
}
