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

import com.flowerconnect.config.PlatformConfig;
import com.flowerconnect.config.PlatformConfigRepository;
import com.flowerconnect.domain.AuditLog;
import com.flowerconnect.domain.User;
import com.flowerconnect.exception.BusinessException;
import com.flowerconnect.repository.AuditLogRepository;
import com.flowerconnect.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Admin-driven read + update for the global commission-rate default
 * ({@code platform_config.commission_rate_default}). Every change writes an
 * {@code audit_log} row with the previous and new percentage values.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PlatformConfigService {

    public static final String ACTION_PLATFORM_COMMISSION_RATE_CHANGED =
            "PLATFORM_COMMISSION_RATE_CHANGED";
    public static final String ENTITY_TYPE = "PLATFORM_CONFIG";

    private final PlatformConfigRepository platformConfigRepository;
    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;

    /**
     * Returns the global default commission rate as a percentage (0-100), or
     * {@code null} when no row is present.
     */
    @Transactional(readOnly = true)
    public BigDecimal getCommissionRateDefault() {
        return platformConfigRepository.findByConfigKey(CommissionRateService.COMMISSION_RATE_DEFAULT_KEY)
                .map(config -> parsePercentage(config.getConfigValue()))
                .orElse(null);
    }

    /**
     * ADMIN-only: set the global default commission rate and record an audit row.
     *
     * @param adminUserId the acting admin (authorisation is enforced by the security filter)
     * @param reason optional explanation for the change
     * @param newPercentage percentage 0.00-100.00 with at most 2 decimal places
     */
    @Transactional
    public void updateCommissionRateDefault(Long adminUserId, String reason, BigDecimal newPercentage) {
        if (newPercentage == null) {
            throw BusinessException.badRequest("Commission rate is required");
        }
        CommissionRateService.validatePercentage(newPercentage);

        PlatformConfig existing = platformConfigRepository.findByConfigKey(CommissionRateService.COMMISSION_RATE_DEFAULT_KEY).orElse(null);
        BigDecimal previous = existing != null ? parsePercentage(existing.getConfigValue()) : null;

        if (existing == null) {
            PlatformConfig row = PlatformConfig.builder()
                    .configKey(CommissionRateService.COMMISSION_RATE_DEFAULT_KEY)
                    .configValue(newPercentage.toPlainString())
                    .description("Global default commission rate percentage (0-100).")
                    .build();
            platformConfigRepository.saveAndFlush(row);
        } else {
            existing.setConfigValue(newPercentage.toPlainString());
            platformConfigRepository.saveAndFlush(existing);
        }

        String auditReason = (reason != null && !reason.isBlank()) ? reason.trim() : "Platform commission rate default updated by admin";
        writeAudit(adminUserId, ACTION_PLATFORM_COMMISSION_RATE_CHANGED, auditReason, previous, newPercentage);
    }

    private void writeAudit(Long adminUserId, String actionType, String reason,
                            BigDecimal previous, BigDecimal current) {
        User admin = userRepository.findById(adminUserId)
                .orElseThrow(() -> BusinessException.unauthorized("Authenticated admin not found for audit: " + adminUserId));

        auditLogRepository.save(AuditLog.builder()
                .actor(admin)
                .actionType(actionType)
                .entityType(ENTITY_TYPE)
                .entityId(0L)
                .reason(reason)
                .previousValue(previous != null ? previous.toPlainString() : null)
                .newValue(current != null ? current.toPlainString() : null)
                .build());
    }

    private static BigDecimal parsePercentage(String value) {
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            log.warn("Non-numeric commission_rate_default '{}'; using null", value);
            return null;
        }
    }
}
