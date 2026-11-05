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
import com.flowerconnect.domain.AuditActorType;
import com.flowerconnect.domain.AuditLog;
import com.flowerconnect.domain.User;
import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.exception.BusinessException;
import com.flowerconnect.repository.AuditLogRepository;
import com.flowerconnect.repository.UserRepository;
import com.flowerconnect.repository.VendorProfileRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Admin commission rates", description = "ADMIN-only: platform default and per-vendor commission rates")
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
    @Operation(summary = "Get the platform default commission rate",
            description = "Returns the platform default commission rate (percentage 0-100). ADMIN-only.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Default rate"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "403", description = "Not an ADMIN account")
    })
    public ResponseEntity<AdminCommissionRateResponse> getPlatformRate() {
        BigDecimal rate = platformConfigService.getCommissionRateDefault();
        return ResponseEntity.ok(AdminCommissionRateResponse.builder().rate(rate).build());
    }

    /** Update the platform global commission-rate default (admin-only). */
    @PatchMapping("/platform-settings/commission-rate")
    @Operation(summary = "Update the platform default commission rate",
            description = "Updates the platform default commission rate (percentage 0-100). Writes an "
                    + "audit-log row. ADMIN-only.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Default rate updated"),
            @ApiResponse(responseCode = "400", description = "Validation failed (rate out of range)"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "403", description = "Not an ADMIN account")
    })
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
    @Operation(summary = "Get a vendor's commission-rate override",
            description = "Returns a vendor's commission-rate override (percentage 0-100), or null if "
                    + "none is set. ADMIN-only.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Vendor rate"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "403", description = "Not an ADMIN account"),
            @ApiResponse(responseCode = "404", description = "Vendor profile not found")
    })
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
    @Operation(summary = "Set a per-vendor commission-rate override",
            description = "Sets a per-vendor commission-rate override (percentage 0-100). Writes an "
                    + "audit-log row. ADMIN-only.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Vendor rate set"),
            @ApiResponse(responseCode = "400", description = "Validation failed (rate out of range)"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "403", description = "Not an ADMIN account"),
            @ApiResponse(responseCode = "404", description = "Vendor profile not found")
    })
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
                .actorType(AuditActorType.USER)
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
    @Operation(summary = "Clear a per-vendor commission-rate override",
            description = "Clears a per-vendor commission-rate override so the platform default "
                    + "applies. Writes an audit-log row. ADMIN-only.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Vendor rate cleared"),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "403", description = "Not an ADMIN account"),
            @ApiResponse(responseCode = "404", description = "Vendor profile not found")
    })
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
                .actorType(AuditActorType.USER)
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
