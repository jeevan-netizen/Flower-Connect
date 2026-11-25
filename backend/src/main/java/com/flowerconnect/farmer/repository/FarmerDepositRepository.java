package com.flowerconnect.farmer.repository;

import com.flowerconnect.farmer.domain.FarmerDeposit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FarmerDepositRepository extends JpaRepository<FarmerDeposit, Long> {

    List<FarmerDeposit> findByLinkId(Long linkId);
}
