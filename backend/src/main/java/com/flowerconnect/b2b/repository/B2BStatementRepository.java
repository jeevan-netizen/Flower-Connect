package com.flowerconnect.b2b.repository;

import com.flowerconnect.b2b.domain.B2BStatement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Persistence for B2B statements (Stage 3 of the B2B module).
 *
 * <p>Queries are scoped to the calling vendor as seller or buyer; the service
 * layer enforces that only a party to a statement may read or act on it.
 */
@Repository
public interface B2BStatementRepository
        extends JpaRepository<B2BStatement, Long> {

    Optional<B2BStatement> findByStatementNo(String statementNo);

    List<B2BStatement> findBySellerId(Long sellerId);

    List<B2BStatement> findByBuyerId(Long buyerId);
}
