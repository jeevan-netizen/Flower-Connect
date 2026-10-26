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

import com.flowerconnect.billing.domain.Commission;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CommissionRepository extends JpaRepository<Commission, Long> {

    /** Idempotent read path: the commission row is created exactly once per order. */
    boolean existsByOrderId(Long orderId);

    Optional<Commission> findByOrderId(Long orderId);

    Page<Commission> findByVendorIdOrderByCreatedAtDesc(Long vendorId, Pageable pageable);

    Page<Commission> findByVendorIdAndCreatedAtBetweenOrderByCreatedAtDesc(
            Long vendorId, LocalDateTime from, LocalDateTime to, Pageable pageable);

    @Query("SELECT c FROM Commission c WHERE c.createdAt >= :from AND c.createdAt < :to ORDER BY c.createdAt ASC")
    List<Commission> findAllInDateRange(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("SELECT COALESCE(SUM(c.commissionAmount), 0) FROM Commission c WHERE c.vendor.id = :vendorId")
    BigDecimal sumCommissionAmountByVendorId(@Param("vendorId") Long vendorId);

    @Query("SELECT COALESCE(SUM(c.commissionAmount), 0) FROM Commission c WHERE c.createdAt >= :from AND c.createdAt < :to")
    BigDecimal sumCommissionAmountInDateRange(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
