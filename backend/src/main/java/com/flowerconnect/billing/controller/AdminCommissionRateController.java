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

package com.flowerconnect.billing.controller;

import com.flowerconnect.billing.dto.AdminCommissionRateRequest;
import com.flowerconnect.billing.dto.AdminCommissionRateResponse;
import com.flowerconnect.billing.service.CommissionRateService;
import com.flowerconnect.billing.service.PlatformConfigService;
import com.flowerconnect.domain.AuditLog;
import com.flowerconnect.domain.User;
import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.exception.BusinessException;
import com.flowerconnect.repository.AuditLogRepository;
import com.flowerconnect.repository.UserRepository;
import com.flowerconnect.repository.VendorProfileRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/**
 * Commission-rate administration (Phase 10, Option A):
 * <ul>
 *   <li>read the platform global default ({@code GET /admin/platform-settings/commission-rate});</li>
 *   <li>update the platform global default ({@code PATCH /admin/platform-settings/commission-rate});</li>
 *   <li>read a vendor's override rate ({@code GET /admin/vendors/{id}/commission-rate});</li>
 *   <li>set a per-vendor override ({@code PATCH /admin/vendors/{id}/commission-rate});</li>
 *   <li>clear a per-vendor override ({@code DELETE /admin/vendors/{id}/commission-rate}).</li>
 * </ul>
 *
 * <p>Every change writes an {@code audit_log} row with before/after values.
 * Authorization is enforced by the {@code /api/v1/admin/**} rule in
 * {@code SecurityConfig} (ROLE_ADMIN).
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminCommissionRateController {

    public static final String ACTION_VENDOR_COMMISSION_RATE_CHANGED = "VENDOR_COMMISSION_RATE_CHANGED";
    public static final String ACTION_VENDOR_COMMISSION_RATE_CLEARED = "VENDOR_COMMISSION_RATE_CLEARED";
    public static final String ENTITY_TYPE_VENDOR_PROFILE = "VENDOR_PROFILE";

    private final VendorProfileRepository vendorProfileRepository;
    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;
    private final CommissionRateService commissionRateService;
    private final PlatformConfigService platformConfigService;

    /** Read the platform global commission-rate default (admin-only). */
    @GetMapping("/platform-settings/commission-rate")
    public ResponseEntity<AdminCommissionRateResponse> getPlatformRate() {
        BigDecimal rate = platformConfigService.getCommissionRateDefault();
        return ResponseEntity.ok(AdminCommissionRateResponse.builder().rate(rate).build());
    }

    /** Update the platform global commission-rate default (admin-only). */
    @PatchMapping("/platform-settings/commission-rate")
    public ResponseEntity<AdminCommissionRateResponse> updatePlatformRate(
            Authentication authentication,
            @Valid @RequestBody AdminCommissionRateRequest request) {

        User admin = userRepository.findByEmailWithRole(authentication.getName())
                .orElseThrow(() -> BusinessException.unauthorized("Authenticated admin not found"));

        platformConfigService.updateCommissionRateDefault(
                admin.getId(), request.getReason(), request.getRate());

        return ResponseEntity.ok(AdminCommissionRateResponse.builder()
                .rate(request.getRate())
                .build());
    }

    /** Read a vendor's commission-rate override (admin-only). */
    @GetMapping("/vendors/{id}/commission-rate")
    public ResponseEntity<AdminCommissionRateResponse> getVendorRate(
            @PathVariable @Positive(message = "Vendor profile id must be positive") Long id) {

        VendorProfile vendor = vendorProfileRepository.findByIdWithDetails(id)
                .orElseThrow(() -> BusinessException.notFound("Vendor profile not found: " + id));

        return ResponseEntity.ok(AdminCommissionRateResponse.builder()
                .rate(vendor.getCommissionRate())
                .build());
    }

    /** Set a per-vendor commission-rate override (admin-only). */
    @PatchMapping("/vendors/{id}/commission-rate")
    public ResponseEntity<AdminCommissionRateResponse> setVendorRate(
            Authentication authentication,
            @PathVariable @Positive(message = "Vendor profile id must be positive") Long id,
            @Valid @RequestBody AdminCommissionRateRequest request) {

        User admin = userRepository.findByEmailWithRole(authentication.getName())
                .orElseThrow(() -> BusinessException.unauthorized("Authenticated admin not found"));

        VendorProfile vendor = vendorProfileRepository.findByIdWithDetails(id)
                .orElseThrow(() -> BusinessException.notFound("Vendor profile not found: " + id));

        BigDecimal previous = vendor.getCommissionRate();
        BigDecimal newPercentage = request.getRate();
        CommissionRateService.validatePercentage(newPercentage);

        vendor.setCommissionRate(newPercentage);
        vendorProfileRepository.saveAndFlush(vendor);

        String auditReason = (request.getReason() != null && !request.getReason().isBlank())
                ? request.getReason().trim()
                : "Commission rate updated by admin";

        auditLogRepository.save(AuditLog.builder()
                .actor(admin)
                .actionType(ACTION_VENDOR_COMMISSION_RATE_CHANGED)
                .entityType(ENTITY_TYPE_VENDOR_PROFILE)
                .entityId(vendor.getId())
                .reason(auditReason)
                .previousValue(previous != null ? previous.toPlainString() : null)
                .newValue(newPercentage.toPlainString())
                .build());

        return ResponseEntity.ok(AdminCommissionRateResponse.builder()
                .rate(newPercentage)
                .build());
    }

    /** Clear a per-vendor commission-rate override (admin-only). */
    @DeleteMapping("/vendors/{id}/commission-rate")
    public ResponseEntity<Void> clearVendorRate(
            Authentication authentication,
            @PathVariable @Positive(message = "Vendor profile id must be positive") Long id) {

        User admin = userRepository.findByEmailWithRole(authentication.getName())
                .orElseThrow(() -> BusinessException.unauthorized("Authenticated admin not found"));

        VendorProfile vendor = vendorProfileRepository.findByIdWithDetails(id)
                .orElseThrow(() -> BusinessException.notFound("Vendor profile not found: " + id));

        BigDecimal previous = vendor.getCommissionRate();
        vendor.setCommissionRate(null);
        vendorProfileRepository.saveAndFlush(vendor);

        auditLogRepository.save(AuditLog.builder()
                .actor(admin)
                .actionType(ACTION_VENDOR_COMMISSION_RATE_CLEARED)
                .entityType(ENTITY_TYPE_VENDOR_PROFILE)
                .entityId(vendor.getId())
                .reason("Commission rate override cleared by admin")
                .previousValue(previous != null ? previous.toPlainString() : null)
                .newValue(null)
                .build());

        return ResponseEntity.noContent().build();
    }
}
