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

import com.flowerconnect.billing.domain.Settlement;
import com.flowerconnect.billing.domain.SettlementStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface SettlementRepository extends JpaRepository<Settlement, Long>, JpaSpecificationExecutor<Settlement> {

    boolean existsByVendorIdAndPeriodStartAndPeriodEnd(Long vendorId, LocalDate periodStart, LocalDate periodEnd);

    Optional<Settlement> findByVendorIdAndPeriodStartAndPeriodEnd(Long vendorId, LocalDate periodStart, LocalDate periodEnd);

    Page<Settlement> findByVendorIdOrderByPeriodEndDesc(Long vendorId, Pageable pageable);

    Page<Settlement> findByStatusOrderByPeriodEndDesc(SettlementStatus status, Pageable pageable);

    Page<Settlement> findByVendorIdAndStatusOrderByPeriodEndDesc(
            Long vendorId, SettlementStatus status, Pageable pageable);
}
