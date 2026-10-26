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

package com.flowerconnect.billing.repository;

import com.flowerconnect.billing.domain.SettlementEntry;
import com.flowerconnect.billing.domain.SettlementEntryType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SettlementEntryRepository extends JpaRepository<SettlementEntry, Long>, JpaSpecificationExecutor<SettlementEntry> {

    boolean existsByOrderId(Long orderId);

    Optional<SettlementEntry> findByOrderId(Long orderId);

    boolean existsByDisputeId(Long disputeId);

    Optional<SettlementEntry> findByDisputeId(Long disputeId);

    Page<SettlementEntry> findByVendorIdOrderByCreatedAtDesc(Long vendorId, Pageable pageable);

    Page<SettlementEntry> findByVendorIdAndTypeOrderByCreatedAtDesc(
            Long vendorId, SettlementEntryType type, Pageable pageable);

    Page<SettlementEntry> findByVendorIdAndCreatedAtBetweenOrderByCreatedAtDesc(
            Long vendorId, LocalDateTime from, LocalDateTime to, Pageable pageable);

    List<SettlementEntry> findBySettlementIdOrderByCreatedAtAsc(Long settlementId);

    /**
     * Calculates vendor outstanding balance:
     * Sum of signed amounts for rows not attached to any settlement,
     * OR attached to a PENDING settlement.
     * Rows attached to a SETTLED settlement are excluded.
     */
    @Query("SELECT COALESCE(SUM(se.amount), 0) FROM SettlementEntry se " +
           "LEFT JOIN se.settlement s " +
           "WHERE se.vendor.id = :vendorId " +
           "AND (s IS NULL OR s.status = com.flowerconnect.billing.domain.SettlementStatus.PENDING)")
    BigDecimal calculateOutstandingBalance(@Param("vendorId") Long vendorId);

    /**
     * Finds unattached entries eligible for settlement run up to period cutoff.
     */
    @Query("SELECT se FROM SettlementEntry se " +
           "WHERE se.vendor.id = :vendorId AND se.settlement IS NULL AND se.createdAt < :periodEnd " +
           "ORDER BY se.createdAt ASC")
    List<SettlementEntry> findEligibleUnattachedForSettlement(
            @Param("vendorId") Long vendorId, @Param("periodEnd") LocalDateTime periodEnd);

    /**
     * Finds entries for statement generation in a date range.
     */
    @Query("SELECT se FROM SettlementEntry se " +
           "WHERE se.vendor.id = :vendorId AND se.createdAt >= :from AND se.createdAt < :to " +
           "ORDER BY se.createdAt ASC")
    List<SettlementEntry> findByVendorIdAndDateRange(
            @Param("vendorId") Long vendorId, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
