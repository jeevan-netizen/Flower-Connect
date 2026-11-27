package com.flowerconnect.farmer.repository;

import com.flowerconnect.farmer.domain.FarmerPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface FarmerPaymentRepository extends JpaRepository<FarmerPayment, Long> {

    List<FarmerPayment> findByLinkId(Long linkId);

    List<FarmerPayment> findByLinkIdInAndStatus(List<Long> linkIds, FarmerPayment.Status status);

    boolean existsByLinkIdAndStatus(Long linkId, FarmerPayment.Status status);

    /* ------------------------------------------------------------- locking */

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM FarmerPayment p WHERE p.id = ?1")
    Optional<FarmerPayment> findByIdForUpdate(Long id);

    /* ------------------------------------------------------------- aggregates */

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM FarmerPayment p WHERE p.linkId = ?1 AND p.status = ?2")
    BigDecimal sumAmountByLinkIdAndStatus(Long linkId, FarmerPayment.Status status);
}
