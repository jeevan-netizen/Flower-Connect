package com.flowerconnect.farmer.service;

import com.flowerconnect.domain.Role;
import com.flowerconnect.domain.User;
import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.exception.BusinessException;
import com.flowerconnect.farmer.domain.FarmerVendorLink;
import com.flowerconnect.farmer.dto.FarmerLinkRequest;
import com.flowerconnect.farmer.dto.FarmerLinkResponse;
import com.flowerconnect.farmer.dto.FarmerRegisterResponse;
import com.flowerconnect.farmer.dto.FarmerVendorResponse;
import com.flowerconnect.farmer.repository.FarmerVendorLinkRepository;
import com.flowerconnect.repository.RoleRepository;
import com.flowerconnect.repository.UserRepository;
import com.flowerconnect.repository.VendorProfileRepository;
import com.flowerconnect.security.util.EmailNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Farmer/vendor link lifecycle (Stage 1 of the farmer module).
 *
 * <p>Ownership is always derived from the authenticated principal (the JWT
 * subject), never from a request parameter: a farmer can only touch their own
 * links and a florist can only touch links on their own vendor profile.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FarmerLinkService {

    public static final String FARMER_ROLE = "FARMER";

    private final FarmerVendorLinkRepository linkRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final VendorProfileRepository vendorProfileRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Registers a farmer account with the {@code FARMER} role, reusing the
     * existing user fields (email, password, fullName, phone).
     */
    @Transactional
    public FarmerRegisterResponse register(com.flowerconnect.farmer.dto.FarmerRegisterRequest request) {
        String normalizedEmail = EmailNormalizer.normalize(request.getEmail());
        String normalizedPhone = normalizePhone(request.getPhone());

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw BusinessException.conflict("Email already in use");
        }
        if (normalizedPhone != null && userRepository.existsByPhone(normalizedPhone)) {
            throw BusinessException.conflict("Phone number already in use");
        }

        Role farmerRole = roleRepository.findByName(FARMER_ROLE)
                .orElseThrow(() -> new IllegalStateException(FARMER_ROLE + " role not found"));

        User farmer = userRepository.save(User.builder()
                .email(normalizedEmail)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .phone(normalizedPhone)
                .role(farmerRole)
                .status(User.Status.ACTIVE)
                .build());

        return FarmerRegisterResponse.builder()
                .id(farmer.getId())
                .email(farmer.getEmail())
                .fullName(farmer.getFullName())
                .role(FARMER_ROLE)
                .build();
    }

    /**
     * Lists vendors eligible to receive a link request: approved vendors
     * (following the existing approved-vendor visibility rules), optionally
     * filtered by a name query. Each row carries this farmer's link status when
     * one exists.
     */
    @Transactional(readOnly = true)
    public List<FarmerVendorResponse> listEligibleVendors(Long farmerId, String query) {
        requireFarmer(farmerId);

        boolean hasQuery = query != null && !query.isBlank();
        String q = hasQuery ? query.toLowerCase() : null;

        List<VendorProfile> vendors = vendorProfileRepository.findAllByStatus(VendorProfile.Status.APPROVED);
        List<Long> vendorIds = vendors.stream()
                .map(VendorProfile::getId)
                .collect(Collectors.toList());

        Map<Long, FarmerVendorLink.Status> linkByVendor = linkRepository.findByFarmerId(farmerId).stream()
                .collect(Collectors.toMap(
                        FarmerVendorLink::getVendorId,
                        FarmerVendorLink::getStatus,
                        (existing, replacement) -> existing));

        return vendors.stream()
                .filter(v -> !hasQuery || v.getBusinessName().toLowerCase().contains(q))
                .map(v -> FarmerVendorResponse.builder()
                        .id(v.getId())
                        .businessName(v.getBusinessName())
                        .vendorStatus(v.getStatus().name())
                        .linkStatus(linkByVendor.get(v.getId()) != null
                                ? linkByVendor.get(v.getId()).name() : null)
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Creates a {@code PENDING} link request from the farmer to the vendor.
     *
     * <p>If a {@code REJECTED} row exists for the pair it is reused and set back
     * to {@code PENDING}. If a {@code PENDING}, {@code ACTIVE} or
     * {@code SUSPENDED} row already exists, a conflict is returned.
     */
    @Transactional
    public FarmerLinkResponse requestLink(Long farmerId, FarmerLinkRequest request) {
        requireFarmer(farmerId);

        VendorProfile vendor = vendorProfileRepository.findById(request.getVendorId())
                .orElseThrow(() -> BusinessException.notFound("Vendor not found"));

        // Vendor discovery visibility: only approved vendors can be linked.
        if (vendor.getStatus() != VendorProfile.Status.APPROVED) {
            throw BusinessException.badRequest("Vendor is not available for linking");
        }

        FarmerVendorLink existing = linkRepository.findByFarmerIdAndVendorId(farmerId, request.getVendorId())
                .orElse(null);

        if (existing != null) {
            if (existing.getStatus() == FarmerVendorLink.Status.REJECTED) {
                log.debug("Re-using rejected link {} for farmer {} and vendor {}", existing.getId(), farmerId,
                        request.getVendorId());
                existing.setStatus(FarmerVendorLink.Status.PENDING);
                linkRepository.save(existing);
                return toResponse(existing, vendor.getBusinessName());
            }
            throw BusinessException.conflict("A link already exists between this farmer and vendor");
        }

        FarmerVendorLink link = FarmerVendorLink.builder()
                .farmerId(farmerId)
                .vendorId(vendor.getId())
                .status(FarmerVendorLink.Status.PENDING)
                .build();
        linkRepository.save(link);
        return toResponse(link, vendor.getBusinessName());
    }

    /**
     * Lists only the authenticated farmer's own links, including status and
     * vendor name.
     */
    @Transactional(readOnly = true)
    public List<FarmerLinkResponse> listFarmerLinks(Long farmerId) {
        requireFarmer(farmerId);
        return toResponses(linkRepository.findByFarmerId(farmerId));
    }

    /**
     * Lists links belonging to the authenticated florist's vendor, optionally
     * narrowed by status.
     */
    @Transactional(readOnly = true)
    public List<FarmerLinkResponse> listVendorLinks(Long vendorId, FarmerVendorLink.Status status) {
        requireVendorExists(vendorId);
        List<FarmerVendorLink> links = status == null
                ? linkRepository.findByVendorId(vendorId)
                : linkRepository.findByVendorIdAndStatus(vendorId, status);
        return toResponses(links);
    }

    /**
     * Vendor accepts a pending link: {@code PENDING -> ACTIVE}.
     */
    @Transactional
    public FarmerLinkResponse acceptLink(Long vendorId, Long linkId) {
        FarmerVendorLink link = requireOwnLink(vendorId, linkId);
        requireTransition(link, FarmerVendorLink.Status.PENDING, FarmerVendorLink.Status.ACTIVE, "accept");
        link.setStatus(FarmerVendorLink.Status.ACTIVE);
        linkRepository.save(link);
        return toResponse(link, vendorName(link.getVendorId()));
    }

    /**
     * Vendor rejects a pending link: {@code PENDING -> REJECTED}.
     */
    @Transactional
    public FarmerLinkResponse rejectLink(Long vendorId, Long linkId) {
        FarmerVendorLink link = requireOwnLink(vendorId, linkId);
        requireTransition(link, FarmerVendorLink.Status.PENDING, FarmerVendorLink.Status.REJECTED, "reject");
        link.setStatus(FarmerVendorLink.Status.REJECTED);
        linkRepository.save(link);
        return toResponse(link, vendorName(link.getVendorId()));
    }

    /**
     * Vendor suspends an active link: {@code ACTIVE -> SUSPENDED}.
     */
    @Transactional
    public FarmerLinkResponse suspendLink(Long vendorId, Long linkId) {
        FarmerVendorLink link = requireOwnLink(vendorId, linkId);
        requireTransition(link, FarmerVendorLink.Status.ACTIVE, FarmerVendorLink.Status.SUSPENDED, "suspend");
        link.setStatus(FarmerVendorLink.Status.SUSPENDED);
        linkRepository.save(link);
        return toResponse(link, vendorName(link.getVendorId()));
    }

    /**
     * Vendor reactivates a suspended link: {@code SUSPENDED -> ACTIVE}.
     */
    @Transactional
    public FarmerLinkResponse reactivateLink(Long vendorId, Long linkId) {
        FarmerVendorLink link = requireOwnLink(vendorId, linkId);
        requireTransition(link, FarmerVendorLink.Status.SUSPENDED, FarmerVendorLink.Status.ACTIVE, "reactivate");
        link.setStatus(FarmerVendorLink.Status.ACTIVE);
        linkRepository.save(link);
        return toResponse(link, vendorName(link.getVendorId()));
    }

    /* ------------------------------------------------------------- private */

    private void requireFarmer(Long farmerId) {
        User user = userRepository.findById(farmerId)
                .orElseThrow(() -> BusinessException.notFound("Farmer not found"));
        if (!FARMER_ROLE.equals(user.getRole().getName())) {
            throw BusinessException.forbidden("Only a farmer may perform this action");
        }
    }

    private void requireVendorExists(Long vendorId) {
        if (!vendorProfileRepository.existsById(vendorId)) {
            throw BusinessException.notFound("Vendor not found");
        }
    }

    /**
     * Resolves and returns the link only when it exists and its vendor matches
     * the authenticated florist's vendor. A missing link is 404; a link owned
     * by another florist is 403, matching the existing ownership convention.
     */
    private FarmerVendorLink requireOwnLink(Long vendorId, Long linkId) {
        FarmerVendorLink link = linkRepository.findByIdAndVendorId(linkId, vendorId)
                .orElseGet(() -> {
                    // Distinguish "no such link" from "link belongs to another vendor".
                    if (linkRepository.findById(linkId).isPresent()) {
                        throw BusinessException.forbidden("This link belongs to another vendor");
                    }
                    throw BusinessException.notFound("Link not found");
                });
        return link;
    }

    private void requireTransition(FarmerVendorLink link, FarmerVendorLink.Status from,
                                 FarmerVendorLink.Status to, String action) {
        if (link.getStatus() != from) {
            throw BusinessException.conflict(
                    "Cannot " + action + " a link in status " + link.getStatus() + " (expected " + from + ")");
        }
    }

    private List<FarmerLinkResponse> toResponses(List<FarmerVendorLink> links) {
        if (links.isEmpty()) {
            return List.of();
        }
        List<Long> vendorIds = links.stream()
                .map(FarmerVendorLink::getVendorId)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, String> vendorNames = vendorProfileRepository.findAllById(vendorIds).stream()
                .collect(Collectors.toMap(
                        VendorProfile::getId,
                        VendorProfile::getBusinessName,
                        (existing, replacement) -> existing));
        return links.stream()
                .map(link -> toResponse(link, vendorNames.get(link.getVendorId())))
                .collect(Collectors.toList());
    }

    private FarmerLinkResponse toResponse(FarmerVendorLink link, String vendorName) {
        return FarmerLinkResponse.builder()
                .id(link.getId())
                .vendorId(link.getVendorId())
                .vendorName(vendorName)
                .status(link.getStatus().name())
                .createdAt(link.getCreatedAt())
                .updatedAt(link.getUpdatedAt())
                .build();
    }

    private String vendorName(Long vendorId) {
        return vendorProfileRepository.findById(vendorId)
                .map(VendorProfile::getBusinessName)
                .orElse("unknown");
    }

    private static String normalizePhone(String phone) {
        return phone == null ? null : phone.trim();
    }
}
