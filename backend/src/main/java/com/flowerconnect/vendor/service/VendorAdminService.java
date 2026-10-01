package com.flowerconnect.vendor.service;

import com.flowerconnect.domain.AuditLog;
import com.flowerconnect.domain.User;
import com.flowerconnect.domain.VendorHours;
import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.exception.BusinessException;
import com.flowerconnect.repository.AuditLogRepository;
import com.flowerconnect.repository.UserRepository;
import com.flowerconnect.repository.VendorHoursRepository;
import com.flowerconnect.repository.VendorProfileRepository;
import com.flowerconnect.vendor.dto.VendorProfilePageResponse;
import com.flowerconnect.vendor.dto.VendorProfileResponse;
import com.flowerconnect.vendor.mapper.VendorMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Admin vendor management (plan task 2.6): list vendors by status and move a
 * profile between approval states, writing an {@code audit_log} row for every
 * action.
 *
 * <p>Authorization is enforced at the filter chain: these operations are only
 * reachable by a caller holding {@code ROLE_ADMIN} (see {@code SecurityConfig}).
 * The service still resolves the acting admin explicitly so the audit row always
 * records a real actor.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VendorAdminService {

    public static final String ENTITY_TYPE = "VENDOR_PROFILE";

    public static final String ACTION_APPROVED = "VENDOR_APPROVED";
    public static final String ACTION_REJECTED = "VENDOR_REJECTED";
    public static final String ACTION_SUSPENDED = "VENDOR_SUSPENDED";
    public static final String ACTION_REINSTATED = "VENDOR_REINSTATED";

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final VendorProfileRepository vendorProfileRepository;
    private final VendorHoursRepository vendorHoursRepository;
    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;
    private final VendorMapper mapper;

    /**
     * Lists vendor profiles, optionally narrowed to one status.
     */
    @Transactional(readOnly = true)
    public VendorProfilePageResponse listProfiles(VendorProfile.Status status, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(safePage, safeSize,
                Sort.by("businessName").and(Sort.by("id")));

        Page<VendorProfile> result = status == null
                ? vendorProfileRepository.findAll(pageable)
                : vendorProfileRepository.findPageByStatus(status, pageable);

        List<Long> profileIds = result.getContent().stream()
                .map(VendorProfile::getId)
                .toList();
        Map<Long, List<VendorHours>> hoursByProfile = loadHours(profileIds);

        List<VendorProfileResponse> content = result.getContent().stream()
                .map(profile -> mapper.toResponse(profile,
                        hoursByProfile.getOrDefault(profile.getId(), Collections.emptyList())))
                .toList();

        return VendorProfilePageResponse.builder()
                .content(content)
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .first(result.isFirst())
                .last(result.isLast())
                .empty(result.isEmpty())
                .build();
    }

    /**
     * {@code PENDING_APPROVAL -> APPROVED}. The vendor becomes visible to
     * discovery and may use gated endpoints.
     */
    @Transactional
    public VendorProfileResponse approve(String adminEmail, Long profileId) {
        VendorProfile profile = requireProfile(profileId);
        requireStatus(profile, VendorProfile.Status.PENDING_APPROVAL, "approve");

        profile.setStatus(VendorProfile.Status.APPROVED);
        vendorProfileRepository.saveAndFlush(profile);

        writeAudit(adminEmail, ACTION_APPROVED, profile, null);
        return toResponse(profile);
    }

    /**
     * {@code PENDING_APPROVAL -> REJECTED}. The reason is required by the
     * controller and stored on the audit row.
     */
    @Transactional
    public VendorProfileResponse reject(String adminEmail, Long profileId, String reason) {
        VendorProfile profile = requireProfile(profileId);
        requireStatus(profile, VendorProfile.Status.PENDING_APPROVAL, "reject");

        profile.setStatus(VendorProfile.Status.REJECTED);
        vendorProfileRepository.saveAndFlush(profile);

        writeAudit(adminEmail, ACTION_REJECTED, profile, reason);
        return toResponse(profile);
    }

    /**
     * {@code APPROVED -> SUSPENDED}. In-flight orders are unaffected (D-6).
     */
    @Transactional
    public VendorProfileResponse suspend(String adminEmail, Long profileId, String reason) {
        VendorProfile profile = requireProfile(profileId);
        requireStatus(profile, VendorProfile.Status.APPROVED, "suspend");

        profile.setStatus(VendorProfile.Status.SUSPENDED);
        vendorProfileRepository.saveAndFlush(profile);

        writeAudit(adminEmail, ACTION_SUSPENDED, profile, reason);
        return toResponse(profile);
    }

    /**
     * {@code SUSPENDED -> APPROVED}. Reinstatement does not go through a fresh
     * approval, which is why it is a distinct action from {@link #approve}.
     */
    @Transactional
    public VendorProfileResponse reinstate(String adminEmail, Long profileId) {
        VendorProfile profile = requireProfile(profileId);
        requireStatus(profile, VendorProfile.Status.SUSPENDED, "reinstate");

        profile.setStatus(VendorProfile.Status.APPROVED);
        vendorProfileRepository.saveAndFlush(profile);

        writeAudit(adminEmail, ACTION_REINSTATED, profile, null);
        return toResponse(profile);
    }

    private VendorProfile requireProfile(Long profileId) {
        return vendorProfileRepository.findByIdWithDetails(profileId)
                .orElseThrow(() -> BusinessException.notFound("Vendor profile not found"));
    }

    private void requireStatus(VendorProfile profile, VendorProfile.Status expected, String action) {
        if (profile.getStatus() != expected) {
            throw BusinessException.conflict(
                    "Cannot " + action + " a vendor profile in status " + profile.getStatus());
        }
    }

    private void writeAudit(String adminEmail, String action, VendorProfile profile, String reason) {
        User admin = userRepository.findByEmailWithRole(adminEmail)
                .orElseThrow(() -> new IllegalStateException("Authenticated admin not found"));
        auditLogRepository.save(AuditLog.builder()
                .actor(admin)
                .actionType(action)
                .entityType(ENTITY_TYPE)
                .entityId(profile.getId())
                .reason(reason)
                .build());
    }

    private VendorProfileResponse toResponse(VendorProfile profile) {
        return mapper.toResponse(profile,
                vendorHoursRepository.findByVendorProfileIdOrderByWeekdayAsc(profile.getId()));
    }

    private Map<Long, List<VendorHours>> loadHours(List<Long> profileIds) {
        if (profileIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return vendorHoursRepository.findByVendorProfileIdInOrderByWeekdayAsc(profileIds).stream()
                .collect(Collectors.groupingBy(hours -> hours.getVendorProfile().getId()));
    }
}
