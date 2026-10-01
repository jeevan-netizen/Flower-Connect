package com.flowerconnect.repository;

import com.flowerconnect.domain.VendorProfile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

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
}
