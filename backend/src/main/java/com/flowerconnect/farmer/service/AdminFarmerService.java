package com.flowerconnect.farmer.service;

import com.flowerconnect.domain.User;
import com.flowerconnect.exception.BusinessException;
import com.flowerconnect.farmer.domain.FarmerVendorLink;
import com.flowerconnect.farmer.dto.FarmerAdminDetail;
import com.flowerconnect.farmer.dto.FarmerAdminSummary;
import com.flowerconnect.farmer.dto.FarmerVendorLedgerOverview;
import com.flowerconnect.farmer.repository.FarmerVendorLinkRepository;
import com.flowerconnect.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Administrator-facing farmer management (Admin Farmer Management).
 *
 * <p>Backs the admin farmer list and farmer detail. Every read is scoped to the
 * requested farmer and returns only fields the admin UI needs: no password hash,
 * no reset token, no credential material. Financial figures are reported exactly
 * as {@link FarmerCollectionService} computes them for the farmer and vendor
 * passbooks; the UI never recomputes balances.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminFarmerService {

    public static final String FARMER_ROLE = "FARMER";

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final UserRepository userRepository;
    private final FarmerVendorLinkRepository linkRepository;
    private final FarmerCollectionService farmerCollectionService;
    private final com.flowerconnect.security.UserStatusService userStatusService;

    /**
     * Lists farmer accounts, optionally narrowed to one account status and/or a
     * free-text search over name, email and mobile number.
     */
    @Transactional(readOnly = true)
    public Page<FarmerAdminSummary> listFarmers(String search, User.Status status,
                                                int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(safePage, safeSize,
                Sort.by("createdAt").descending().and(Sort.by("id").descending()));

        Specification<User> specification = buildSpecification(search, status);
        Page<User> result = specification == null
                ? userRepository.findAll(pageable)
                : userRepository.findAll(specification, pageable);

        return result.map(user -> FarmerAdminSummary.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .phone(user.getPhone())
                .status(user.getStatus())
                .createdAt(user.getCreatedAt())
                .build());
    }

    /**
     * Returns the detail view for one farmer: the account profile, the linked
     * florists, and a read-only ledger overview separated by vendor.
     *
     * @throws BusinessException {@code NOT_FOUND} when no farmer with the given
     *                           id exists, or the account is not a farmer
     */
    @Transactional(readOnly = true)
    public FarmerAdminDetail getFarmerDetail(Long farmerId) {
        User farmer = userRepository.findByIdWithRole(farmerId)
                .orElseThrow(() -> BusinessException.notFound("Farmer not found"));
        if (!FARMER_ROLE.equals(farmer.getRole().getName())) {
            throw BusinessException.notFound("Farmer not found");
        }

        List<FarmerVendorLink> links = linkRepository.findByFarmerId(farmerId);
        List<FarmerVendorLedgerOverview> linkedVendors = links.stream()
                .map(link -> {
                    com.flowerconnect.farmer.dto.PassbookResponse passbook =
                            farmerCollectionService.getPassbookForAdmin(link.getId());
                    return FarmerVendorLedgerOverview.builder()
                            .vendorId(link.getVendorId())
                            .vendorName(passbook.getVendorName())
                            .linkStatus(link.getStatus().name())
                            .linkCreatedAt(link.getCreatedAt())
                            .outstandingBalance(passbook.getOutstandingBalance())
                            .entries(passbook.getEntries())
                            .build();
                })
                .collect(Collectors.toList());

        return FarmerAdminDetail.builder()
                .id(farmer.getId())
                .email(farmer.getEmail())
                .fullName(farmer.getFullName())
                .phone(farmer.getPhone())
                .address(farmer.getAddress())
                .status(farmer.getStatus())
                .createdAt(farmer.getCreatedAt())
                .linkedVendors(linkedVendors)
                .build();
    }

    /**
     * Suspends or reactivates a farmer account. Delegates to
     * {@link com.flowerconnect.security.UserStatusService}, which is the single
     * owner of the status-change rule: it revokes the target's refresh tokens and
     * writes one {@code audit_log} row (actor, timestamp, action, reason) in the
     * same transaction.
     */
    @Transactional
    public com.flowerconnect.security.dto.AdminUserResponse changeFarmerStatus(
            String actorEmail, Long farmerId, User.Status newStatus, String reason) {
        User farmer = userRepository.findByIdWithRole(farmerId)
                .orElseThrow(() -> BusinessException.notFound("Farmer not found"));
        if (!FARMER_ROLE.equals(farmer.getRole().getName())) {
            throw BusinessException.notFound("Farmer not found");
        }
        // Delegates to UserStatusService, the single owner of the status-change
        // rule: it revokes the target's refresh tokens and writes one audit_log
        // row (actor, timestamp, action, reason) in the same transaction. The
        // actor is resolved from the JWT subject by UserStatusService, which also
        // refuses a self-targeted change.
        return userStatusService.changeStatus(actorEmail, farmerId, newStatus, reason);
    }

    private Specification<User> buildSpecification(String search, User.Status status) {
        List<Specification<User>> filters = new ArrayList<>();
        filters.add((root, query, builder) ->
                builder.equal(root.get("role").get("name"), FARMER_ROLE));
        if (status != null) {
            filters.add((root, query, builder) -> builder.equal(root.get("status"), status));
        }
        if (search != null && !search.isBlank()) {
            String like = "%" + search.trim().toLowerCase() + "%";
            filters.add((root, query, builder) -> builder.or(
                    builder.like(builder.lower(root.get("fullName")), like),
                    builder.like(builder.lower(root.get("email")), like),
                    builder.like(builder.lower(root.get("phone")), like)
            ));
        }
        Specification<User> combined = filters.get(0);
        for (int i = 1; i < filters.size(); i++) {
            combined = combined.and(filters.get(i));
        }
        return combined;
    }
}
