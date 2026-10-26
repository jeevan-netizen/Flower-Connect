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

import com.flowerconnect.billing.domain.Settlement;
import com.flowerconnect.billing.domain.SettlementDirection;
import com.flowerconnect.billing.domain.SettlementEntry;
import com.flowerconnect.billing.domain.SettlementStatus;
import com.flowerconnect.billing.repository.SettlementEntryRepository;
import com.flowerconnect.billing.repository.SettlementRepository;
import com.flowerconnect.domain.AuditLog;
import com.flowerconnect.domain.User;
import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.exception.BusinessException;
import com.flowerconnect.repository.AuditLogRepository;
import com.flowerconnect.repository.UserRepository;
import com.flowerconnect.repository.VendorProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Service managing weekly settlement cycles, netting calculations, entry attachment,
 * and admin finalization.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementService {

    private final SettlementRepository settlementRepository;
    private final SettlementEntryRepository settlementEntryRepository;
    private final VendorProfileRepository vendorProfileRepository;
    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;
    private final Clock clock;

    /**
     * Runs or retrieves a settlement cycle for a single vendor across the specified date period.
     * Idempotent: returns existing PENDING settlement if already created, or raises 409 Conflict if already SETTLED.
     *
     * @param vendorId the vendor ID
     * @param periodStart the start date of the period
     * @param periodEnd the end date of the period (inclusive)
     * @return the created or existing settlement
     */
    @Transactional
    public Settlement runSettlement(Long vendorId, LocalDate periodStart, LocalDate periodEnd) {
        if (vendorId == null) {
            throw BusinessException.badRequest("vendorId is required");
        }
        if (periodStart == null || periodEnd == null) {
            throw BusinessException.badRequest("periodStart and periodEnd are required");
        }
        if (periodStart.isAfter(periodEnd)) {
            throw BusinessException.badRequest("periodStart must be on or before periodEnd");
        }

        VendorProfile vendor = vendorProfileRepository.findById(vendorId)
                .orElseThrow(() -> BusinessException.notFound("Vendor not found: " + vendorId));

        Optional<Settlement> existingOpt = settlementRepository.findByVendorIdAndPeriodStartAndPeriodEnd(
                vendorId, periodStart, periodEnd);
        if (existingOpt.isPresent()) {
            Settlement existing = existingOpt.get();
            if (existing.getStatus() == SettlementStatus.SETTLED) {
                throw BusinessException.conflict("Settlement for period " + periodStart + " to " + periodEnd + " is already settled");
            }
            log.info("Returning existing PENDING settlement {} for vendor {} (period {} to {})",
                    existing.getId(), vendorId, periodStart, periodEnd);
            return existing;
        }

        LocalDateTime cutoff = periodEnd.plusDays(1).atStartOfDay();
        List<SettlementEntry> unattachedEntries = settlementEntryRepository.findEligibleUnattachedForSettlement(
                vendorId, cutoff);

        BigDecimal totalCredits = BigDecimal.ZERO;
        BigDecimal totalDues = BigDecimal.ZERO;

        for (SettlementEntry entry : unattachedEntries) {
            BigDecimal amount = entry.getAmount();
            if (amount.compareTo(BigDecimal.ZERO) > 0) {
                totalCredits = totalCredits.add(amount);
            } else if (amount.compareTo(BigDecimal.ZERO) < 0) {
                totalDues = totalDues.add(amount.abs());
            }
        }

        totalCredits = totalCredits.setScale(2, RoundingMode.HALF_UP);
        totalDues = totalDues.setScale(2, RoundingMode.HALF_UP);
        BigDecimal netAmount = totalCredits.subtract(totalDues).setScale(2, RoundingMode.HALF_UP);

        SettlementDirection direction;
        if (netAmount.compareTo(BigDecimal.ZERO) > 0) {
            direction = SettlementDirection.PLATFORM_PAYS_VENDOR;
        } else if (netAmount.compareTo(BigDecimal.ZERO) < 0) {
            direction = SettlementDirection.VENDOR_PAYS_PLATFORM;
        } else {
            direction = SettlementDirection.ZERO;
        }

        Settlement settlement = Settlement.builder()
                .vendor(vendor)
                .periodStart(periodStart)
                .periodEnd(periodEnd)
                .totalCredits(totalCredits)
                .totalDues(totalDues)
                .netAmount(netAmount)
                .direction(direction)
                .status(SettlementStatus.PENDING)
                .build();

        try {
            settlement = settlementRepository.save(settlement);
        } catch (DataIntegrityViolationException ex) {
            log.warn("Concurrent settlement creation for vendor {} (period {} to {}); checking existing",
                    vendorId, periodStart, periodEnd);
            Optional<Settlement> reloaded = settlementRepository.findByVendorIdAndPeriodStartAndPeriodEnd(
                    vendorId, periodStart, periodEnd);
            if (reloaded.isPresent()) {
                if (reloaded.get().getStatus() == SettlementStatus.SETTLED) {
                    throw BusinessException.conflict("Settlement for period " + periodStart + " to " + periodEnd + " is already settled");
                }
                return reloaded.get();
            }
            throw ex;
        }

        for (SettlementEntry entry : unattachedEntries) {
            entry.attachToSettlement(settlement);
        }
        settlementEntryRepository.saveAll(unattachedEntries);

        log.info("Created settlement {} for vendor {} (period {} to {}): credits={}, dues={}, net={}, entries={}",
                settlement.getId(), vendorId, periodStart, periodEnd, totalCredits, totalDues, netAmount, unattachedEntries.size());

        return settlement;
    }

    /**
     * Runs settlement generation for all vendors across the specified date period.
     *
     * @param periodStart the start date
     * @param periodEnd the end date
     * @return list of generated settlements
     */
    @Transactional
    public List<Settlement> runSettlementForAllVendors(LocalDate periodStart, LocalDate periodEnd) {
        List<VendorProfile> vendors = vendorProfileRepository.findAll();
        List<Settlement> results = new ArrayList<>();
        for (VendorProfile vendor : vendors) {
            try {
                results.add(runSettlement(vendor.getId(), periodStart, periodEnd));
            } catch (Exception ex) {
                log.error("Failed to run settlement for vendor {}: {}", vendor.getId(), ex.getMessage(), ex);
            }
        }
        return results;
    }

    /**
     * Finalizes a PENDING settlement, moving it to SETTLED status with an admin reference note and audit record.
     *
     * @param settlementId settlement ID
     * @param adminEmail email of the authenticating admin
     * @param adminReferenceNote non-blank administrative note/reference
     * @return the finalized settlement
     */
    @Transactional
    public Settlement finalizeSettlement(Long settlementId, String adminEmail, String adminReferenceNote) {
        if (adminReferenceNote == null || adminReferenceNote.trim().isEmpty()) {
            throw BusinessException.badRequest("Admin reference note is required");
        }

        Settlement settlement = settlementRepository.findById(settlementId)
                .orElseThrow(() -> BusinessException.notFound("Settlement not found: " + settlementId));

        if (settlement.getStatus() == SettlementStatus.SETTLED) {
            throw BusinessException.conflict("Settlement " + settlementId + " is already settled");
        }

        User admin = userRepository.findByEmailWithRole(adminEmail)
                .orElseThrow(() -> BusinessException.notFound("Admin user not found: " + adminEmail));

        settlement.setStatus(SettlementStatus.SETTLED);
        settlement.setSettledBy(admin);
        settlement.setSettledAt(LocalDateTime.now(clock));
        settlement.setAdminReferenceNote(adminReferenceNote.trim());
        Settlement saved = settlementRepository.save(settlement);

        auditLogRepository.save(AuditLog.builder()
                .actor(admin)
                .actionType("SETTLEMENT_FINALIZED")
                .entityType("SETTLEMENT")
                .entityId(saved.getId())
                .reason(adminReferenceNote.trim())
                .previousValue(SettlementStatus.PENDING.name())
                .newValue(SettlementStatus.SETTLED.name())
                .build());

        log.info("Finalized settlement {} by admin {}: status=SETTLED, note='{}'",
                saved.getId(), adminEmail, adminReferenceNote.trim());

        return saved;
    }

    /**
     * Retrieves a settlement by ID.
     */
    @Transactional(readOnly = true)
    public Settlement getSettlementById(Long settlementId) {
        return settlementRepository.findById(settlementId)
                .orElseThrow(() -> BusinessException.notFound("Settlement not found: " + settlementId));
    }

    /**
     * Admin view: lists paginated settlements with optional vendor and status filters.
     */
    @Transactional(readOnly = true)
    public Page<Settlement> listSettlements(Long vendorId, SettlementStatus status, Pageable pageable) {
        if (vendorId != null && status != null) {
            return settlementRepository.findByVendorIdAndStatusOrderByPeriodEndDesc(vendorId, status, pageable);
        } else if (vendorId != null) {
            return settlementRepository.findByVendorIdOrderByPeriodEndDesc(vendorId, pageable);
        } else if (status != null) {
            return settlementRepository.findByStatusOrderByPeriodEndDesc(status, pageable);
        } else {
            return settlementRepository.findAll(pageable);
        }
    }

    /**
     * Vendor view: lists paginated settlements for a specific vendor.
     */
    @Transactional(readOnly = true)
    public Page<Settlement> getVendorSettlements(Long vendorId, Pageable pageable) {
        return settlementRepository.findByVendorIdOrderByPeriodEndDesc(vendorId, pageable);
    }

    /**
     * Retrieves all ledger entries attached to a settlement.
     */
    @Transactional(readOnly = true)
    public List<SettlementEntry> getSettlementEntries(Long settlementId) {
        return settlementEntryRepository.findBySettlementIdOrderByCreatedAtAsc(settlementId);
    }
}
