package com.flowerconnect.pos.repository;

import com.flowerconnect.pos.domain.PosItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Persistence for {@link PosItem}. Items are loaded through their parent
 * transaction; this repository exists for direct save and per-transaction
 * reads used by the receipt and report paths.
 */
public interface PosItemRepository extends JpaRepository<PosItem, Long> {

    List<PosItem> findByTransactionIdOrderByIdAsc(Long transactionId);

    /**
     * All POS line items for a vendor's transactions in a half-open UTC
     * interval [from, to). Used by the report path to aggregate top-selling
     * products; the caller is responsible for the vendor scoping so a vendor
     * only ever sees its own items.
     */
    @Query("""
            SELECT i FROM PosItem i
            JOIN i.transaction t
            WHERE t.vendor.id = :vendorId
              AND t.createdAt >= :from
              AND t.createdAt < :to
            """)
    List<PosItem> findByVendorIdAndCreatedAtBetween(
            @Param("vendorId") Long vendorId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);
}
