package com.flowerconnect.pos.repository;

import com.flowerconnect.pos.domain.PosTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Persistence for {@link PosTransaction}.
 */
public interface PosTransactionRepository extends JpaRepository<PosTransaction, Long>,
        JpaSpecificationExecutor<PosTransaction> {

    Optional<PosTransaction> findByVendorIdAndIdempotencyKey(Long vendorId, String idempotencyKey);

    long countByVendorId(Long vendorId);

    /**
     * One page of a vendor's own sales, newest first. Used by the sale listing
     * and daily/weekly reports.
     */
    Page<PosTransaction> findByVendorIdOrderByCreatedAtDesc(Long vendorId, Pageable pageable);

    /**
     * A vendor's sales in a half-open UTC interval [from, to). Report queries
     * convert local day boundaries to this UTC interval using the vendor's
     * business time zone before calling.
     */
    @Query("""
            SELECT t FROM PosTransaction t
            WHERE t.vendor.id = :vendorId
              AND t.createdAt >= :from
              AND t.createdAt < :to
            ORDER BY t.createdAt DESC, t.id DESC
            """)
    Page<PosTransaction> findByVendorIdAndCreatedAtBetween(
            @Param("vendorId") Long vendorId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable);
}
