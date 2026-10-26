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

import com.flowerconnect.billing.domain.SettlementEntry;
import com.flowerconnect.billing.domain.SettlementEntryType;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class SettlementEntryResponse {
    private Long id;
    private Long vendorId;
    private Long orderId;
    private Long disputeId;
    private Long settlementId;
    private SettlementEntryType type;
    private BigDecimal amount;
    private String reference;
    private LocalDateTime createdAt;

    public static SettlementEntryResponse fromEntity(SettlementEntry entry) {
        if (entry == null) {
            return null;
        }
        return SettlementEntryResponse.builder()
                .id(entry.getId())
                .vendorId(entry.getVendor() != null ? entry.getVendor().getId() : null)
                .orderId(entry.getOrder() != null ? entry.getOrder().getId() : null)
                .disputeId(entry.getDispute() != null ? entry.getDispute().getId() : null)
                .settlementId(entry.getSettlement() != null ? entry.getSettlement().getId() : null)
                .type(entry.getType())
                .amount(entry.getAmount())
                .reference(entry.getReference())
                .createdAt(entry.getCreatedAt())
                .build();
    }
}
