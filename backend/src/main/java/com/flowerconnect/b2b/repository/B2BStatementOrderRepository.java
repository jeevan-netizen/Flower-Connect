package com.flowerconnect.b2b.repository;

import com.flowerconnect.b2b.domain.B2BStatementOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Persistence for the statement-order link table (Stage 3 of the B2B module).
 */
@Repository
public interface B2BStatementOrderRepository
        extends JpaRepository<B2BStatementOrder, Long> {

    /**
     * Returns the links for a statement, fetching each link's order (with its
     * buyer and seller) so the statement detail can be assembled without
     * N+1 lazy loads.
     */
    @Query("""
            SELECT so FROM B2BStatementOrder so
            JOIN FETCH so.order o
            LEFT JOIN FETCH o.buyer
            LEFT JOIN FETCH o.seller
            WHERE so.statement.id = :statementId
            ORDER BY so.id ASC
            """)
    List<B2BStatementOrder> findByStatementId(@Param("statementId") Long statementId);
}
