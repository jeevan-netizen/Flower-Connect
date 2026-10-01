package com.flowerconnect.vendor.service;

import com.flowerconnect.domain.Role;
import com.flowerconnect.domain.ServiceLocation;
import com.flowerconnect.domain.User;
import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.exception.BusinessException;
import com.flowerconnect.exception.ErrorCode;
import com.flowerconnect.repository.RoleRepository;
import com.flowerconnect.repository.ServiceLocationRepository;
import com.flowerconnect.repository.UserRepository;
import com.flowerconnect.repository.VendorHoursRepository;
import com.flowerconnect.repository.VendorProfileRepository;
import com.flowerconnect.vendor.dto.VendorHoursRequest;
import com.flowerconnect.vendor.dto.VendorProfileResponse;
import com.flowerconnect.vendor.dto.VendorProfileUpdateRequest;
import com.flowerconnect.vendor.dto.VendorRegisterRequest;
import com.flowerconnect.vendor.mapper.VendorMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit coverage for the service-layer validation rules of plan task 2.5.
 *
 * <p>Field-level constraints ({@code @NotBlank}, {@code @Min}, {@code @Digits})
 * live on the request DTOs and are exercised by the controller slice test and
 * the integration test. This class covers what the service alone owns: the
 * cross-field operating-hours contract, referential rules that need the
 * database, and the registration/update semantics.
 */
@ExtendWith(MockitoExtension.class)
class VendorServiceTest {

    @Mock
    private VendorProfileRepository vendorProfileRepository;
    @Mock
    private VendorHoursRepository vendorHoursRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private ServiceLocationRepository serviceLocationRepository;
    @Mock
    private VendorMapper mapper;
    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private VendorService vendorService;

    private ServiceLocation location;

    @BeforeEach
    void setUp() {
        location = ServiceLocation.builder()
                .id(7L)
                .city("Bengaluru")
                .area("Koramangala")
                .pincode("560034")
                .latitude(new BigDecimal("12.93520000"))
                .longitude(new BigDecimal("77.62450000"))
                .build();
    }

    // ---------------------------------------------------------------- registration

    @Test
    void registerAppliesSchemaDefaultsWhenOptionalFieldsOmitted() {
        stubRegistration();

        vendorService.register(registerRequest(null, null, null, null, null, null, null, null, null));

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertEquals("FLORIST", userCaptor.getValue().getRole().getName());

        ArgumentCaptor<VendorProfile> profileCaptor = ArgumentCaptor.forClass(VendorProfile.class);
        verify(vendorProfileRepository).save(profileCaptor.capture());
        VendorProfile saved = profileCaptor.getValue();

        assertEquals(VendorProfile.Status.PENDING_APPROVAL, saved.getStatus());
        assertEquals(new BigDecimal("5.00"), saved.getDeliveryRadiusKm());
        assertEquals(new BigDecimal("0.00"), saved.getMinOrderAmount());
        assertEquals(new BigDecimal("0.00"), saved.getBaseDeliveryFee());
        assertEquals(new BigDecimal("0.00"), saved.getPerKmFee());
        assertEquals(30, saved.getPrepTimeMinutes());
        assertEquals(60, saved.getSlotDurationMinutes());
        assertEquals(10, saved.getMaxOrdersPerSlot());
        assertTrue(saved.isAcceptingOrders());
        assertEquals(0, saved.getReviewCount());
    }

    @Test
    void registerCopiesCoordinatesFromServiceLocationCentroid() {
        stubRegistration();

        vendorService.register(registerRequest(null, null, null, null, null, null, null, null, null));

        ArgumentCaptor<VendorProfile> profileCaptor = ArgumentCaptor.forClass(VendorProfile.class);
        verify(vendorProfileRepository).save(profileCaptor.capture());
        VendorProfile saved = profileCaptor.getValue();
        assertEquals(location.getLatitude(), saved.getLatitude());
        assertEquals(location.getLongitude(), saved.getLongitude());
        assertEquals(location, saved.getServiceLocation());
    }

    @Test
    void registerHashesThePasswordRatherThanStoringItVerbatim() {
        stubRegistration();
        when(passwordEncoder.encode("Secret123")).thenReturn("$2a$10$hashed");

        vendorService.register(registerRequest(null, null, null, null, null, null, null, null, null));

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertEquals("$2a$10$hashed", userCaptor.getValue().getPasswordHash());
    }

    @Test
    void registerRejectsDuplicateEmail() {
        when(userRepository.existsByEmail("vendor@test.com")).thenReturn(true);

        assertEquals(ErrorCode.CONFLICT, assertThrows(BusinessException.class,
                () -> vendorService.register(
                        registerRequest(null, null, null, null, null, null, null, null, null)))
                .getErrorCode());
        verify(vendorProfileRepository, never()).save(any());
    }

    @Test
    void registerRejectsDuplicatePhone() {
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(userRepository.existsByPhone("+919999999999")).thenReturn(true);

        assertEquals(ErrorCode.CONFLICT, assertThrows(BusinessException.class,
                () -> vendorService.register(
                        registerRequest(null, "+919999999999", null, null, null, null, null, null, null)))
                .getErrorCode());
    }

    @Test
    void registerRejectsUnknownServiceLocation() {
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(roleRepository.findByName("FLORIST")).thenReturn(java.util.Optional.of(vendorRole()));
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(serviceLocationRepository.findById(9_999_999L)).thenReturn(java.util.Optional.empty());

        VendorRegisterRequest request =
                registerRequest(9_999_999L, null, null, null, null, null, null, null, null);

        assertEquals(ErrorCode.VALIDATION_FAILED, assertThrows(BusinessException.class,
                () -> vendorService.register(request)).getErrorCode());
        verify(vendorProfileRepository, never()).save(any());
    }

    // ------------------------------------------------------- operating-hours rules

    @Test
    void registerAcceptsOpenPeriodWithOrderedTimes() {
        stubRegistration();

        List<VendorHoursRequest> hours = List.of(openDay(DayOfWeek.MONDAY,
                LocalTime.of(9, 0), LocalTime.of(18, 0)));

        vendorService.register(registerRequest(null, null, null, null, null, null, null, null, hours));

        verify(vendorHoursRepository).saveAll(anyList());
    }

    @Test
    void registerAcceptsClosedPeriodWithoutTimes() {
        stubRegistration();

        List<VendorHoursRequest> hours = List.of(VendorHoursRequest.builder()
                .weekday(DayOfWeek.SUNDAY).closed(true).build());

        vendorService.register(registerRequest(null, null, null, null, null, null, null, null, hours));

        verify(vendorHoursRepository).saveAll(anyList());
    }

    @Test
    void registerRejectsClosedPeriodThatCarriesTimes() {
        stubEmailAvailable();
        List<VendorHoursRequest> hours = List.of(VendorHoursRequest.builder()
                .weekday(DayOfWeek.SUNDAY)
                .openTime(LocalTime.of(9, 0))
                .closeTime(LocalTime.of(18, 0))
                .closed(true)
                .build());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> vendorService.register(
                        registerRequest(null, null, null, null, null, null, null, null, hours)));
        assertEquals(ErrorCode.VALIDATION_FAILED, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("SUNDAY"));
        assertTrue(ex.getMessage().contains("closed"));
    }

    @Test
    void registerRejectsOpenPeriodWithoutOpenTime() {
        stubEmailAvailable();
        List<VendorHoursRequest> hours = List.of(VendorHoursRequest.builder()
                .weekday(DayOfWeek.TUESDAY).closeTime(LocalTime.of(18, 0)).closed(false).build());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> vendorService.register(
                        registerRequest(null, null, null, null, null, null, null, null, hours)));
        assertTrue(ex.getMessage().contains("openTime"));
    }

    @Test
    void registerRejectsOpenPeriodWithoutCloseTime() {
        stubEmailAvailable();
        List<VendorHoursRequest> hours = List.of(VendorHoursRequest.builder()
                .weekday(DayOfWeek.TUESDAY).openTime(LocalTime.of(9, 0)).closed(false).build());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> vendorService.register(
                        registerRequest(null, null, null, null, null, null, null, null, hours)));
        assertTrue(ex.getMessage().contains("closeTime"));
    }

    @Test
    void registerRejectsCloseTimeEqualToOpenTime() {
        stubEmailAvailable();
        List<VendorHoursRequest> hours = List.of(openDay(DayOfWeek.WEDNESDAY,
                LocalTime.of(9, 0), LocalTime.of(9, 0)));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> vendorService.register(
                        registerRequest(null, null, null, null, null, null, null, null, hours)));
        assertTrue(ex.getMessage().contains("close after"));
    }

    @Test
    void registerRejectsCloseTimeBeforeOpenTime() {
        stubEmailAvailable();
        List<VendorHoursRequest> hours = List.of(openDay(DayOfWeek.THURSDAY,
                LocalTime.of(18, 0), LocalTime.of(9, 0)));

        assertThrows(BusinessException.class,
                () -> vendorService.register(
                        registerRequest(null, null, null, null, null, null, null, null, hours)));
    }

    @Test
    void registerRejectsDuplicateWeekday() {
        stubEmailAvailable();
        List<VendorHoursRequest> hours = List.of(
                openDay(DayOfWeek.FRIDAY, LocalTime.of(9, 0), LocalTime.of(18, 0)),
                openDay(DayOfWeek.FRIDAY, LocalTime.of(10, 0), LocalTime.of(19, 0)));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> vendorService.register(
                        registerRequest(null, null, null, null, null, null, null, null, hours)));
        assertTrue(ex.getMessage().contains("Duplicate weekday"));
    }

    // ------------------------------------------------------------ own-profile reads

    @Test
    void getOwnProfileReturnsTheCallersProfile() {
        stubOwnProfile();

        vendorService.getOwnProfile("vendor@test.com");

        verify(vendorProfileRepository).findByUserIdWithDetails(42L);
    }

    @Test
    void getOwnProfileIsNotFoundWhenAccountHasNoProfile() {
        when(userRepository.findByEmailWithRole("vendor@test.com"))
                .thenReturn(java.util.Optional.of(vendorUser(42L)));
        when(vendorProfileRepository.findByUserIdWithDetails(42L)).thenReturn(java.util.Optional.empty());
        assertEquals(ErrorCode.NOT_FOUND, assertThrows(BusinessException.class,
                () -> vendorService.getOwnProfile("vendor@test.com")).getErrorCode());
    }

    // ------------------------------------------------------- own-profile updates

    @Test
    void updateResetsOmittedOptionalFieldsToDefaults() {
        stubOwnProfile();
        stubValidLocation();

        vendorService.updateOwnProfile("vendor@test.com", updateRequest());

        ArgumentCaptor<VendorProfile> captor = ArgumentCaptor.forClass(VendorProfile.class);
        verify(vendorProfileRepository).saveAndFlush(captor.capture());
        VendorProfile saved = captor.getValue();
        assertEquals(new BigDecimal("5.00"), saved.getDeliveryRadiusKm());
        assertEquals(new BigDecimal("0.00"), saved.getPerKmFee());
        assertEquals(30, saved.getPrepTimeMinutes());
        assertEquals(10, saved.getMaxOrdersPerSlot());
        assertTrue(saved.isAcceptingOrders());
    }

    @Test
    void updatePreservesPlatformOwnedFields() {
        VendorProfile existing = existingProfile();
        existing.setAvgRating(new BigDecimal("4.50"));
        existing.setReviewCount(37);
        stubOwnProfile(existing);
        stubValidLocation();

        vendorService.updateOwnProfile("vendor@test.com", updateRequest());

        ArgumentCaptor<VendorProfile> captor = ArgumentCaptor.forClass(VendorProfile.class);
        verify(vendorProfileRepository).saveAndFlush(captor.capture());
        assertEquals(37, captor.getValue().getReviewCount());
        assertEquals(new BigDecimal("4.50"), captor.getValue().getAvgRating());
        assertEquals(VendorProfile.Status.PENDING_APPROVAL, captor.getValue().getStatus());
    }

    @Test
    void updateLeavesStoredWeekUntouchedWhenHoursAreAbsent() {
        stubOwnProfile();
        stubValidLocation();

        vendorService.updateOwnProfile("vendor@test.com", updateRequest());

        verify(vendorHoursRepository, never()).deleteAll(anyList());
        verify(vendorHoursRepository, never()).saveAll(anyList());
    }

    @Test
    void updateReplacesTheWholeWeekWhenHoursAreSupplied() {
        stubOwnProfile();
        stubValidLocation();

        vendorService.updateOwnProfile("vendor@test.com",
                updateRequest(7L, List.of(openDay(DayOfWeek.MONDAY,
                        LocalTime.of(8, 0), LocalTime.of(20, 0)))));

        verify(vendorHoursRepository).deleteAll(anyList());
        verify(vendorHoursRepository).saveAll(anyList());
    }

    @Test
    void updateRejectsUnknownServiceLocation() {
        stubProfileLookup(existingProfile());
        when(serviceLocationRepository.findById(9_999_999L)).thenReturn(java.util.Optional.empty());

        VendorProfileUpdateRequest request = updateRequest(9_999_999L, null);

        assertEquals(ErrorCode.VALIDATION_FAILED, assertThrows(BusinessException.class,
                () -> vendorService.updateOwnProfile("vendor@test.com", request)).getErrorCode());
        verify(vendorProfileRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateRejectsInvalidHoursBeforeTouchingTheProfile() {
        stubProfileLookup(existingProfile());

        VendorProfileUpdateRequest request = updateRequest(7L, List.of(VendorHoursRequest.builder()
                .weekday(DayOfWeek.MONDAY).closed(true)
                .openTime(LocalTime.of(9, 0)).build()));

        assertThrows(BusinessException.class,
                () -> vendorService.updateOwnProfile("vendor@test.com", request));
        verify(vendorProfileRepository, never()).saveAndFlush(any());
    }

    // ------------------------------------------------------------------- helpers

    private void stubRegistration() {
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        // Only consulted when the request carries a phone number.
        lenient().when(userRepository.existsByPhone(anyString())).thenReturn(false);
        when(roleRepository.findByName("FLORIST")).thenReturn(java.util.Optional.of(vendorRole()));
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(serviceLocationRepository.findById(7L)).thenReturn(java.util.Optional.of(location));
        when(vendorProfileRepository.save(any(VendorProfile.class)))
                .thenAnswer(inv -> {
                    VendorProfile profile = inv.getArgument(0);
                    profile.setId(99L);
                    return profile;
                });
        when(vendorHoursRepository.findByVendorProfileIdOrderByWeekdayAsc(99L))
                .thenReturn(List.of());
        when(mapper.toResponse(any(), anyList())).thenReturn(VendorProfileResponse.builder().build());
    }

    private void stubOwnProfile() {
        stubOwnProfile(existingProfile());
    }

    private void stubOwnProfile(VendorProfile profile) {
        stubProfileLookup(profile);
        when(vendorHoursRepository.findByVendorProfileIdOrderByWeekdayAsc(99L))
                .thenReturn(List.of());
        when(mapper.toResponse(any(), anyList())).thenReturn(VendorProfileResponse.builder().build());
    }

    /**
     * Stubs only the "which profile is mine" lookup, for the tests that must fail
     * before any response is produced.
     */
    private void stubProfileLookup(VendorProfile profile) {
        when(userRepository.findByEmailWithRole("vendor@test.com"))
                .thenReturn(java.util.Optional.of(vendorUser(42L)));
        when(vendorProfileRepository.findByUserIdWithDetails(42L))
                .thenReturn(java.util.Optional.of(profile));
    }

    /**
     * Stubs only the checks that run before the operating-hours contract is
     * evaluated, for the tests that must fail on invalid hours.
     */
    private void stubEmailAvailable() {
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
    }

    /**
     * Stubs the service-location lookup used by the update path, so the update
     * proceeds far enough to assert the field changes.
     */
    private void stubValidLocation() {
        when(serviceLocationRepository.findById(7L)).thenReturn(java.util.Optional.of(location));
    }

    private static Role vendorRole() {
        return Role.builder().id(2L).name("FLORIST").build();
    }

    private static User vendorUser(Long id) {
        return User.builder().id(id).email("vendor@test.com").role(vendorRole())
                .status(User.Status.ACTIVE).build();
    }

    private static VendorProfile existingProfile() {
        return VendorProfile.builder()
                .id(99L)
                .user(vendorUser(42L))
                .businessName("Old Name")
                .addressLine1("1 Old Street")
                .serviceLocation(ServiceLocation.builder().id(7L).city("Bengaluru")
                        .area("Koramangala").pincode("560034")
                        .latitude(new BigDecimal("12.93520000"))
                        .longitude(new BigDecimal("77.62450000")).build())
                .latitude(new BigDecimal("12.93520000"))
                .longitude(new BigDecimal("77.62450000"))
                .deliveryRadiusKm(new BigDecimal("9.00"))
                .status(VendorProfile.Status.PENDING_APPROVAL)
                .reviewCount(0)
                .minOrderAmount(new BigDecimal("10.00"))
                .baseDeliveryFee(new BigDecimal("20.00"))
                .perKmFee(new BigDecimal("5.00"))
                .prepTimeMinutes(45)
                .slotDurationMinutes(90)
                .maxOrdersPerSlot(7)
                .acceptingOrders(false)
                .build();
    }

    private static VendorHoursRequest openDay(DayOfWeek weekday, LocalTime open, LocalTime close) {
        return VendorHoursRequest.builder()
                .weekday(weekday).openTime(open).closeTime(close).closed(false).build();
    }

    private static VendorRegisterRequest registerRequest(Long serviceLocationId, String phone,
                                                        BigDecimal radius, BigDecimal minOrder,
                                                        BigDecimal baseFee, BigDecimal perKm,
                                                        Integer prepTime, Integer slotDuration,
                                                        List<VendorHoursRequest> hours) {
        return VendorRegisterRequest.builder()
                .email("vendor@test.com")
                .password("Secret123")
                .fullName("Vendor Owner")
                .phone(phone)
                .businessName("Test Blossoms")
                .addressLine1("12 Test Street")
                .serviceLocationId(serviceLocationId == null ? 7L : serviceLocationId)
                .deliveryRadiusKm(radius)
                .minOrderAmount(minOrder)
                .baseDeliveryFee(baseFee)
                .perKmFee(perKm)
                .prepTimeMinutes(prepTime)
                .slotDurationMinutes(slotDuration)
                .hours(hours)
                .build();
    }

    private static VendorProfileUpdateRequest updateRequest() {
        return updateRequest(7L, null);
    }

    private static VendorProfileUpdateRequest updateRequest(Long serviceLocationId,
                                                           List<VendorHoursRequest> hours) {
        return VendorProfileUpdateRequest.builder()
                .businessName("Updated Blossoms")
                .addressLine1("34 New Street")
                .serviceLocationId(serviceLocationId)
                .hours(hours)
                .build();
    }
}
