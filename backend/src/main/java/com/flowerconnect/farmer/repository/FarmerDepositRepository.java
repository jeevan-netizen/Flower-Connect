package com.flowerconnect.farmer.repository;

import com.flowerconnect.farmer.domain.FarmerDeposit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface FarmerDepositRepository extends JpaRepository<FarmerDeposit, Long> {

    List<FarmerDeposit> findByLinkId(Long linkId);

    @Query("SELECT COALESCE(SUM(d.amount), 0) FROM FarmerDeposit d WHERE d.linkId = ?1")
    BigDecimal sumAmountByLinkId(Long linkId);
}
