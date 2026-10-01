package com.flowerconnect.vendor.service;

import com.flowerconnect.domain.Role;
import com.flowerconnect.domain.ServiceLocation;
import com.flowerconnect.domain.User;
import com.flowerconnect.domain.VendorHours;
import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.exception.BusinessException;
import com.flowerconnect.repository.RoleRepository;
import com.flowerconnect.repository.ServiceLocationRepository;
import com.flowerconnect.repository.UserRepository;
import com.flowerconnect.repository.VendorHoursRepository;
import com.flowerconnect.repository.VendorProfileRepository;
import com.flowerconnect.security.util.EmailNormalizer;
import com.flowerconnect.vendor.dto.VendorHoursRequest;
import com.flowerconnect.vendor.dto.VendorProfileResponse;
import com.flowerconnect.vendor.dto.VendorProfileUpdateRequest;
import com.flowerconnect.vendor.dto.VendorRegisterRequest;
import com.flowerconnect.vendor.mapper.VendorMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Vendor self-service API (plan task 2.5).
 *
 * <p>Every method here acts on the <em>authenticated</em> vendor's own profile.
 * No method accepts a vendor or profile identifier from the client, so a vendor
 * cannot address another vendor's row by tampering with a request body or path
 * variable.
 *
 * <p>Validation is split so that it is not duplicated:
 * <ul>
 *   <li>Field shape, length and range are declared once on the request DTOs
 *       with {@code jakarta.validation} and surface as a 400 with a per-field
 *       map.</li>
 *   <li>Cross-field business rules that span more than one property (the
 *       closed/open operating-hours contract) live here, once, and surface as a
 *       400 with a message naming the offending weekday.</li>
 *   <li>Referential rules that need the database (email/phone uniqueness, the
 *       service location existing) live here too.</li>
 *   <li>Invariants that must hold regardless of the caller are also enforced by
 *       CHECK constraints added in migration V6.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VendorService {

    /**
     * Application role for a vendor. The plan text calls this role "VENDOR";
     * the seeded role is FLORIST and is not renamed in this phase. See
     * docs/decisions.md (D-11).
     */
    public static final String VENDOR_ROLE = "FLORIST";

    static final BigDecimal DEFAULT_DELIVERY_RADIUS_KM = new BigDecimal("5.00");
    static final BigDecimal DEFAULT_MIN_ORDER_AMOUNT = new BigDecimal("0.00");
    static final BigDecimal DEFAULT_BASE_DELIVERY_FEE = new BigDecimal("0.00");
    static final BigDecimal DEFAULT_PER_KM_FEE = new BigDecimal("0.00");
    static final int DEFAULT_PREP_TIME_MINUTES = 30;
    static final int DEFAULT_SLOT_DURATION_MINUTES = 60;
    static final int DEFAULT_MAX_ORDERS_PER_SLOT = 10;
    static final boolean DEFAULT_ACCEPTING_ORDERS = true;
    static final int DEFAULT_REVIEW_COUNT = 0;

    private final VendorProfileRepository vendorProfileRepository;
    private final VendorHoursRepository vendorHoursRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final ServiceLocationRepository serviceLocationRepository;
    private final VendorMapper mapper;
    private final PasswordEncoder passwordEncoder;

    /**
     * Registers a vendor: creates the FLORIST account and the profile in one
     * transaction, with the profile in {@code PENDING_APPROVAL}.
     *
     * <p>The account is inserted before the service location is resolved so a
     * failure in profile creation rolls the account back with it; the
     * transaction guarantees no orphan user is left behind.
     */
    @Transactional
    public VendorProfileResponse register(VendorRegisterRequest request) {
        String normalizedEmail = EmailNormalizer.normalize(request.getEmail());
        String normalizedPhone = normalizePhone(request.getPhone());

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw BusinessException.conflict("Email already in use");
        }
        if (normalizedPhone != null && userRepository.existsByPhone(normalizedPhone)) {
            throw BusinessException.conflict("Phone number already in use");
        }

        validateHours(request.getHours());

        Role vendorRole = roleRepository.findByName(VENDOR_ROLE)
                .orElseThrow(() -> new IllegalStateException(VENDOR_ROLE + " role not found"));

        User vendor = userRepository.save(User.builder()
                .email(normalizedEmail)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .phone(normalizedPhone)
                .role(vendorRole)
                .status(User.Status.ACTIVE)
                .build());

        ServiceLocation location = resolveServiceLocation(request.getServiceLocationId());

        VendorProfile profile = VendorProfile.builder()
                .user(vendor)
                .businessName(request.getBusinessName())
                .description(request.getDescription())
                .addressLine1(request.getAddressLine1())
                .addressLine2(request.getAddressLine2())
                .serviceLocation(location)
                .latitude(location.getLatitude())
                .longitude(location.getLongitude())
                .deliveryRadiusKm(orDefault(request.getDeliveryRadiusKm(), DEFAULT_DELIVERY_RADIUS_KM))
                .logoUrl(request.getLogoUrl())
                .status(VendorProfile.Status.PENDING_APPROVAL)
                .reviewCount(DEFAULT_REVIEW_COUNT)
                .minOrderAmount(orDefault(request.getMinOrderAmount(), DEFAULT_MIN_ORDER_AMOUNT))
                .baseDeliveryFee(orDefault(request.getBaseDeliveryFee(), DEFAULT_BASE_DELIVERY_FEE))
                .perKmFee(orDefault(request.getPerKmFee(), DEFAULT_PER_KM_FEE))
                .freeDeliveryAbove(request.getFreeDeliveryAbove())
                .prepTimeMinutes(orDefault(request.getPrepTimeMinutes(), DEFAULT_PREP_TIME_MINUTES))
                .slotDurationMinutes(orDefault(request.getSlotDurationMinutes(), DEFAULT_SLOT_DURATION_MINUTES))
                .maxOrdersPerSlot(orDefault(request.getMaxOrdersPerSlot(), DEFAULT_MAX_ORDERS_PER_SLOT))
                .acceptingOrders(request.getAcceptingOrders() == null
                        ? DEFAULT_ACCEPTING_ORDERS
                        : request.getAcceptingOrders())
                .build();

        vendorProfileRepository.save(profile);
        replaceHours(profile, request.getHours());

        return mapper.toResponse(profile, vendorHoursRepository
                .findByVendorProfileIdOrderByWeekdayAsc(profile.getId()));
    }

    /**
     * Returns the authenticated vendor's own profile.
     *
     * @throws BusinessException NOT_FOUND if the caller holds the vendor role but
     *                             has no profile (for example an account created
     *                             before vendor registration existed)
     */
    @Transactional(readOnly = true)
    public VendorProfileResponse getOwnProfile(String authenticatedEmail) {
        VendorProfile profile = findOwnProfile(authenticatedEmail);
        return mapper.toResponse(profile,
                vendorHoursRepository.findByVendorProfileIdOrderByWeekdayAsc(profile.getId()));
    }

    /**
     * Replaces the authenticated vendor's own profile and delivery settings.
     * PUT semantics: omitted optional scalars fall back to the schema default.
     * A supplied {@code hours} list replaces the entire stored week; an absent
     * one leaves the stored week untouched.
     */
    @Transactional
    public VendorProfileResponse updateOwnProfile(String authenticatedEmail,
                                                  VendorProfileUpdateRequest request) {
        VendorProfile profile = findOwnProfile(authenticatedEmail);
        validateHours(request.getHours());

        ServiceLocation location = resolveServiceLocation(request.getServiceLocationId());

        profile.setBusinessName(request.getBusinessName());
        profile.setDescription(request.getDescription());
        profile.setAddressLine1(request.getAddressLine1());
        profile.setAddressLine2(request.getAddressLine2());
        profile.setServiceLocation(location);
        profile.setLatitude(location.getLatitude());
        profile.setLongitude(location.getLongitude());
        profile.setDeliveryRadiusKm(orDefault(request.getDeliveryRadiusKm(), DEFAULT_DELIVERY_RADIUS_KM));
        profile.setLogoUrl(request.getLogoUrl());
        profile.setMinOrderAmount(orDefault(request.getMinOrderAmount(), DEFAULT_MIN_ORDER_AMOUNT));
        profile.setBaseDeliveryFee(orDefault(request.getBaseDeliveryFee(), DEFAULT_BASE_DELIVERY_FEE));
        profile.setPerKmFee(orDefault(request.getPerKmFee(), DEFAULT_PER_KM_FEE));
        profile.setFreeDeliveryAbove(request.getFreeDeliveryAbove());
        profile.setPrepTimeMinutes(orDefault(request.getPrepTimeMinutes(), DEFAULT_PREP_TIME_MINUTES));
        profile.setSlotDurationMinutes(orDefault(request.getSlotDurationMinutes(), DEFAULT_SLOT_DURATION_MINUTES));
        profile.setMaxOrdersPerSlot(orDefault(request.getMaxOrdersPerSlot(), DEFAULT_MAX_ORDERS_PER_SLOT));
        profile.setAcceptingOrders(request.getAcceptingOrders() == null
                ? DEFAULT_ACCEPTING_ORDERS
                : request.getAcceptingOrders());

        vendorProfileRepository.saveAndFlush(profile);

        if (request.getHours() != null) {
            replaceHours(profile, request.getHours());
        }

        return mapper.toResponse(profile,
                vendorHoursRepository.findByVendorProfileIdOrderByWeekdayAsc(profile.getId()));
    }

    private VendorProfile findOwnProfile(String authenticatedEmail) {
        User user = userRepository.findByEmailWithRole(authenticatedEmail)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found"));
        return vendorProfileRepository.findByUserIdWithDetails(user.getId())
                .orElseThrow(() -> BusinessException.notFound("No vendor profile exists for this account"));
    }

    private ServiceLocation resolveServiceLocation(Long serviceLocationId) {
        return serviceLocationRepository.findById(serviceLocationId)
                .orElseThrow(() -> BusinessException.badRequest("Unknown service location"));
    }

    /**
     * Replaces the stored week with exactly the supplied days. Days that are not
     * supplied end up with no row, which downstream slot computation (Phase 5)
     * treats as closed.
     */
    private void replaceHours(VendorProfile profile, List<VendorHoursRequest> hours) {
        vendorHoursRepository.deleteAll(vendorHoursRepository.findByVendorProfileIdOrderByWeekdayAsc(profile.getId()));
        if (hours == null) {
            return;
        }
        List<VendorHours> entities = new ArrayList<>(hours.size());
        for (VendorHoursRequest request : hours) {
            entities.add(VendorHours.builder()
                    .vendorProfile(profile)
                    .weekday(request.getWeekday())
                    .openTime(request.getOpenTime())
                    .closeTime(request.getCloseTime())
                    .closed(Boolean.TRUE.equals(request.getClosed()))
                    .build());
        }
        vendorHoursRepository.saveAll(entities);
    }

    /**
     * Enforces the operating-hours contract (plan task 2.4 / 2.5):
     * <ul>
     *   <li>each weekday appears at most once;</li>
     *   <li>a closed day carries neither an open nor a close time;</li>
     *   <li>an open day carries both, and closes strictly after it opens.</li>
     * </ul>
     */
    private void validateHours(List<VendorHoursRequest> hours) {
        if (hours == null) {
            return;
        }
        Set<DayOfWeek> seen = EnumSet.noneOf(DayOfWeek.class);
        for (VendorHoursRequest day : hours) {
            DayOfWeek weekday = day.getWeekday();
            if (!seen.add(weekday)) {
                throw BusinessException.badRequest("Duplicate weekday in opening hours: " + weekday);
            }

            boolean closed = Boolean.TRUE.equals(day.getClosed());
            if (closed) {
                if (day.getOpenTime() != null || day.getCloseTime() != null) {
                    throw BusinessException.badRequest(
                            "Opening hours for " + weekday + " are marked closed but specify times");
                }
                continue;
            }

            if (day.getOpenTime() == null) {
                throw BusinessException.badRequest(
                        "Opening hours for " + weekday + " are open but openTime is missing");
            }
            if (day.getCloseTime() == null) {
                throw BusinessException.badRequest(
                        "Opening hours for " + weekday + " are open but closeTime is missing");
            }
            if (!day.getCloseTime().isAfter(day.getOpenTime())) {
                throw BusinessException.badRequest(
                        "Opening hours for " + weekday + " must close after they open");
            }
        }
    }

    private static String normalizePhone(String phone) {
        return phone == null ? null : phone.trim();
    }

    private static BigDecimal orDefault(BigDecimal value, BigDecimal fallback) {
        return value == null ? fallback : value;
    }

    private static int orDefault(Integer value, int fallback) {
        return value == null ? fallback : value;
    }
}
