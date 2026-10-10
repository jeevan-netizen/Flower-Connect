package com.flowerconnect.repository;

import com.flowerconnect.domain.VendorProfile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface VendorProfileRepository
        extends JpaRepository<VendorProfile, Long>, JpaSpecificationExecutor<VendorProfile> {

    @Query("SELECT vp FROM VendorProfile vp JOIN FETCH vp.user u WHERE u.id = :userId")
    Optional<VendorProfile> findByUserId(Long userId);

    /**
     * Resolves the profile of the authenticated principal from the JWT subject
     * (the account email). Used by {@code VendorApprovalGuard}, which must not
     * accept a vendor identifier from the request.
     */
    @Query("SELECT vp FROM VendorProfile vp JOIN FETCH vp.user u WHERE u.email = :email")
    Optional<VendorProfile> findByUserEmail(String email);

    @Query("SELECT vp FROM VendorProfile vp JOIN FETCH vp.user u JOIN FETCH vp.serviceLocation WHERE u.id = :userId")
    Optional<VendorProfile> findByUserIdWithDetails(Long userId);

    @Query("SELECT vp FROM VendorProfile vp JOIN FETCH vp.user u JOIN FETCH vp.serviceLocation WHERE vp.id = :id")
    Optional<VendorProfile> findByIdWithDetails(Long id);

    boolean existsByUserId(Long userId);

    List<VendorProfile> findAllByStatus(VendorProfile.Status status);

    Page<VendorProfile> findPageByStatus(VendorProfile.Status status, Pageable pageable);

    /**
     * Returns the maximum delivery radius among all approved vendors, or null
     * if none exist. Used to compute a bounding box that cannot exclude any
     * vendor that might be within range.
     */
    @Query("SELECT MAX(vp.deliveryRadiusKm) FROM VendorProfile vp WHERE vp.status = 'APPROVED'")
    BigDecimal findMaxDeliveryRadiusForApproved();

    /**
     * Finds approved, accepting vendors whose denormalized coordinates fall
     * within the given latitude/longitude bounding box. The Haversine distance
     * and radius check are applied in Java after this prefilter.
     */
    @Query("SELECT vp FROM VendorProfile vp JOIN FETCH vp.serviceLocation " +
            "WHERE vp.status = 'APPROVED' " +
            "AND vp.acceptingOrders = true " +
            "AND vp.latitude BETWEEN :minLat AND :maxLat " +
            "AND vp.longitude BETWEEN :minLng AND :maxLng")
    List<VendorProfile> findApprovedAcceptingInBoundingBox(
            BigDecimal minLat, BigDecimal maxLat,
            BigDecimal minLng, BigDecimal maxLng);
}
