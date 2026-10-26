package com.flowerconnect.pos.repository;

import com.flowerconnect.pos.domain.PosItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Persistence for {@link PosItem}. Items are loaded through their parent
 * transaction; this repository exists for direct save and per-transaction
 * reads used by the receipt and report paths.
 */
public interface PosItemRepository extends JpaRepository<PosItem, Long> {

    List<PosItem> findByTransactionIdOrderByIdAsc(Long transactionId);
}
