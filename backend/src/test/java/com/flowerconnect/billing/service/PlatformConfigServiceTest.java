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
import com.flowerconnect.domain.Role;
import com.flowerconnect.domain.User;
import com.flowerconnect.exception.BusinessException;
import com.flowerconnect.repository.AuditLogRepository;
import com.flowerconnect.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlatformConfigServiceTest {

    @Mock
    private PlatformConfigRepository platformConfigRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AuditLogRepository auditLogRepository;

    private PlatformConfigService platformConfigService;

    private User admin;

    @BeforeEach
    void setUp() {
        platformConfigService = new PlatformConfigService(platformConfigRepository, userRepository, auditLogRepository);
        admin = User.builder()
                .id(1L)
                .email("admin@flowerconnect.com")
                .role(Role.builder().name("ADMIN").build())
                .build();
    }

    @Test
    @DisplayName("Read default rate when row exists")
    void readDefaultRate() {
        when(platformConfigRepository.findByConfigKey(CommissionRateService.COMMISSION_RATE_DEFAULT_KEY))
                .thenReturn(Optional.of(PlatformConfig.builder()
                        .configKey(CommissionRateService.COMMISSION_RATE_DEFAULT_KEY)
                        .configValue("10.00")
                        .build()));

        BigDecimal rate = platformConfigService.getCommissionRateDefault();
        assertThat(rate).isEqualByComparingTo(new BigDecimal("10.00"));
    }

    @Test
    @DisplayName("Read default rate returns null when row missing")
    void readDefaultRateMissing() {
        when(platformConfigRepository.findByConfigKey(CommissionRateService.COMMISSION_RATE_DEFAULT_KEY))
                .thenReturn(Optional.empty());

        assertThat(platformConfigService.getCommissionRateDefault()).isNull();
    }

    @Test
    @DisplayName("Update platform default creates row when missing and writes audit log")
    void updateDefaultCreatesRow() {
        when(platformConfigRepository.findByConfigKey(CommissionRateService.COMMISSION_RATE_DEFAULT_KEY))
                .thenReturn(Optional.empty());
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));

        platformConfigService.updateCommissionRateDefault(1L, "Initial setup", new BigDecimal("12.50"));

        ArgumentCaptor<PlatformConfig> configCaptor = ArgumentCaptor.forClass(PlatformConfig.class);
        verify(platformConfigRepository).saveAndFlush(configCaptor.capture());
        assertThat(configCaptor.getValue().getConfigKey()).isEqualTo(CommissionRateService.COMMISSION_RATE_DEFAULT_KEY);
        assertThat(configCaptor.getValue().getConfigValue()).isEqualTo("12.50");

        ArgumentCaptor<AuditLog> auditCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(auditCaptor.capture());
        AuditLog audit = auditCaptor.getValue();
        assertThat(audit.getActor()).isEqualTo(admin);
        assertThat(audit.getActionType()).isEqualTo(PlatformConfigService.ACTION_PLATFORM_COMMISSION_RATE_CHANGED);
        assertThat(audit.getEntityType()).isEqualTo(PlatformConfigService.ENTITY_TYPE);
        assertThat(audit.getPreviousValue()).isNull();
        assertThat(audit.getNewValue()).isEqualTo("12.50");
        assertThat(audit.getReason()).isEqualTo("Initial setup");
    }

    @Test
    @DisplayName("Update platform default updates existing row and writes audit log with previous value")
    void updateDefaultUpdatesExistingRow() {
        PlatformConfig existing = PlatformConfig.builder()
                .id(5L)
                .configKey(CommissionRateService.COMMISSION_RATE_DEFAULT_KEY)
                .configValue("10.00")
                .build();
        when(platformConfigRepository.findByConfigKey(CommissionRateService.COMMISSION_RATE_DEFAULT_KEY))
                .thenReturn(Optional.of(existing));
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));

        platformConfigService.updateCommissionRateDefault(1L, "Promotional adjustment", new BigDecimal("8.00"));

        verify(platformConfigRepository).saveAndFlush(existing);
        assertThat(existing.getConfigValue()).isEqualTo("8.00");

        ArgumentCaptor<AuditLog> auditCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(auditCaptor.capture());
        AuditLog audit = auditCaptor.getValue();
        assertThat(audit.getPreviousValue()).isEqualTo("10.00");
        assertThat(audit.getNewValue()).isEqualTo("8.00");
        assertThat(audit.getReason()).isEqualTo("Promotional adjustment");
    }

    @Test
    @DisplayName("Update platform default rejects invalid rates")
    void updateRejectsInvalidRate() {
        assertThatThrownBy(() -> platformConfigService.updateCommissionRateDefault(1L, null, new BigDecimal("-1.00")))
                .isInstanceOf(BusinessException.class);

        assertThatThrownBy(() -> platformConfigService.updateCommissionRateDefault(1L, null, new BigDecimal("100.50")))
                .isInstanceOf(BusinessException.class);

        assertThatThrownBy(() -> platformConfigService.updateCommissionRateDefault(1L, null, null))
                .isInstanceOf(BusinessException.class);
    }
}
