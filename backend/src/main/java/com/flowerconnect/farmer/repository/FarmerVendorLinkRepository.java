package com.flowerconnect.farmer.repository;

import com.flowerconnect.farmer.domain.FarmerVendorLink;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FarmerVendorLinkRepository
        extends JpaRepository<FarmerVendorLink, Long>, JpaSpecificationExecutor<FarmerVendorLink> {

    Optional<FarmerVendorLink> findByFarmerIdAndVendorId(Long farmerId, Long vendorId);

    List<FarmerVendorLink> findByFarmerId(Long farmerId);

    List<FarmerVendorLink> findByVendorId(Long vendorId);

    List<FarmerVendorLink> findByVendorIdAndStatus(Long vendorId, FarmerVendorLink.Status status);

    Optional<FarmerVendorLink> findByIdAndVendorId(Long id, Long vendorId);

    Optional<FarmerVendorLink> findByIdAndFarmerId(Long id, Long farmerId);

    boolean existsByFarmerIdAndVendorId(Long farmerId, Long vendorId);
}
