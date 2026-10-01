package com.flowerconnect.vendor.service;

import com.flowerconnect.domain.AuditLog;
import com.flowerconnect.domain.Role;
import com.flowerconnect.domain.ServiceLocation;
import com.flowerconnect.domain.User;
import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.exception.BusinessException;
import com.flowerconnect.exception.ErrorCode;
import com.flowerconnect.repository.AuditLogRepository;
import com.flowerconnect.repository.UserRepository;
import com.flowerconnect.repository.VendorHoursRepository;
import com.flowerconnect.repository.VendorProfileRepository;
import com.flowerconnect.vendor.dto.VendorProfilePageResponse;
import com.flowerconnect.vendor.dto.VendorProfileResponse;
import com.flowerconnect.vendor.mapper.VendorMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit coverage for the admin state machine and audit trail of plan task 2.6.
 */
@ExtendWith(MockitoExtension.class)
class VendorAdminServiceTest {

    private static final String ADMIN_EMAIL = "admin@test.com";

    @Mock
    private VendorProfileRepository vendorProfileRepository;
    @Mock
    private VendorHoursRepository vendorHoursRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AuditLogRepository auditLogRepository;
    @Mock
    private VendorMapper mapper;

    @InjectMocks
    private VendorAdminService vendorAdminService;

    @BeforeEach
    void setUp() {
        // Deliberately empty: the shared stubs below are only applied by the
        // tests that actually reach them, so Mockito's strict-stubs check stays
        // meaningful.
    }

    /**
     * Stubs the response mapping used by the successful-action and listing paths.
     */
    private void stubResponseMapping() {
        when(vendorHoursRepository.findByVendorProfileIdOrderByWeekdayAsc(anyLong()))
                .thenReturn(List.of());
        when(mapper.toResponse(any(), anyList()))
                .thenReturn(VendorProfileResponse.builder().build());
    }

    // ------------------------------------------------------------------ approve

    @Test
    void approveMovesPendingProfileToApproved() {
        stubResponseMapping();
        VendorProfile profile = stubProfile(VendorProfile.Status.PENDING_APPROVAL);

        vendorAdminService.approve(ADMIN_EMAIL, 5L);

        assertEquals(VendorProfile.Status.APPROVED, profile.getStatus());
        verify(vendorProfileRepository).saveAndFlush(profile);
    }

    @Test
    void approveWritesAnAuditRow() {
        stubResponseMapping();
        stubProfile(VendorProfile.Status.PENDING_APPROVAL);

        vendorAdminService.approve(ADMIN_EMAIL, 5L);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        AuditLog entry = captor.getValue();
        assertEquals(VendorAdminService.ACTION_APPROVED, entry.getActionType());
        assertEquals(VendorAdminService.ENTITY_TYPE, entry.getEntityType());
        assertEquals(5L, entry.getEntityId());
        assertEquals(1L, entry.getActor().getId());
        assertNull(entry.getReason());
    }

    // ------------------------------------------------------------------ reject

    @Test
    void rejectMovesPendingProfileToRejectedAndRecordsTheReason() {
        stubResponseMapping();
        VendorProfile profile = stubProfile(VendorProfile.Status.PENDING_APPROVAL);

        vendorAdminService.reject(ADMIN_EMAIL, 5L, "Incomplete business licence");

        assertEquals(VendorProfile.Status.REJECTED, profile.getStatus());
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        assertEquals(VendorAdminService.ACTION_REJECTED, captor.getValue().getActionType());
        assertEquals("Incomplete business licence", captor.getValue().getReason());
    }

    // ----------------------------------------------------------------- suspend

    @Test
    void suspendMovesApprovedProfileToSuspended() {
        stubResponseMapping();
        VendorProfile profile = stubProfile(VendorProfile.Status.APPROVED);

        vendorAdminService.suspend(ADMIN_EMAIL, 5L, "Repeated late deliveries");

        assertEquals(VendorProfile.Status.SUSPENDED, profile.getStatus());
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        assertEquals(VendorAdminService.ACTION_SUSPENDED, captor.getValue().getActionType());
        assertEquals("Repeated late deliveries", captor.getValue().getReason());
    }

    // --------------------------------------------------------------- reinstate

    @Test
    void reinstateReturnsSuspendedProfileToApproved() {
        stubResponseMapping();
        VendorProfile profile = stubProfile(VendorProfile.Status.SUSPENDED);

        vendorAdminService.reinstate(ADMIN_EMAIL, 5L);

        assertEquals(VendorProfile.Status.APPROVED, profile.getStatus());
        verify(auditLogRepository).save(any(AuditLog.class));
    }

    // ------------------------------------------------- illegal state transitions

    @Test
    void approveIsRejectedForAnAlreadyApprovedProfile() {
        stubProfileLookup(VendorProfile.Status.APPROVED);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> vendorAdminService.approve(ADMIN_EMAIL, 5L));
        assertEquals(ErrorCode.CONFLICT, ex.getErrorCode());
        verify(auditLogRepository, never()).save(any());
    }

    @Test
    void rejectIsRejectedForAnApprovedProfile() {
        stubProfileLookup(VendorProfile.Status.APPROVED);

        assertEquals(ErrorCode.CONFLICT, assertThrows(BusinessException.class,
                () -> vendorAdminService.reject(ADMIN_EMAIL, 5L, "too late")).getErrorCode());
    }

    @Test
    void suspendIsRejectedForAPendingProfile() {
        stubProfileLookup(VendorProfile.Status.PENDING_APPROVAL);

        assertEquals(ErrorCode.CONFLICT, assertThrows(BusinessException.class,
                () -> vendorAdminService.suspend(ADMIN_EMAIL, 5L, "not yet live")).getErrorCode());
    }

    @Test
    void reinstateIsRejectedForAnApprovedProfile() {
        stubProfileLookup(VendorProfile.Status.APPROVED);

        assertEquals(ErrorCode.CONFLICT, assertThrows(BusinessException.class,
                () -> vendorAdminService.reinstate(ADMIN_EMAIL, 5L)).getErrorCode());
    }

    @Test
    void reinstateIsRejectedForARejectedProfile() {
        stubProfileLookup(VendorProfile.Status.REJECTED);

        assertEquals(ErrorCode.CONFLICT, assertThrows(BusinessException.class,
                () -> vendorAdminService.reinstate(ADMIN_EMAIL, 5L)).getErrorCode());
    }

    @Test
    void actionsOnAnUnknownProfileAreNotFound() {
        when(vendorProfileRepository.findByIdWithDetails(404L)).thenReturn(Optional.empty());

        assertEquals(ErrorCode.NOT_FOUND, assertThrows(BusinessException.class,
                () -> vendorAdminService.approve(ADMIN_EMAIL, 404L)).getErrorCode());
        verify(auditLogRepository, never()).save(any());
    }

    // -------------------------------------------------------------------- list

    @Test
    void listWithoutStatusReturnsEveryProfile() {
        when(vendorProfileRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(profile(5L, VendorProfile.Status.APPROVED))));
        when(vendorHoursRepository.findByVendorProfileIdInOrderByWeekdayAsc(anyList()))
                .thenReturn(List.of());
        when(mapper.toResponse(any(), anyList()))
                .thenReturn(VendorProfileResponse.builder().build());

        VendorProfilePageResponse page = vendorAdminService.listProfiles(null, 0, 20);

        assertEquals(1, page.getContent().size());
        assertEquals(1L, page.getTotalElements());
        verify(vendorProfileRepository, never()).findPageByStatus(any(), any());
    }

    @Test
    void listWithStatusFiltersThroughTheStatusQuery() {
        when(vendorProfileRepository.findPageByStatus(eq(VendorProfile.Status.PENDING_APPROVAL), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(profile(6L, VendorProfile.Status.PENDING_APPROVAL))));
        when(vendorHoursRepository.findByVendorProfileIdInOrderByWeekdayAsc(anyList()))
                .thenReturn(List.of());
        when(mapper.toResponse(any(), anyList()))
                .thenReturn(VendorProfileResponse.builder().build());

        VendorProfilePageResponse page =
                vendorAdminService.listProfiles(VendorProfile.Status.PENDING_APPROVAL, 0, 20);

        assertEquals(1, page.getContent().size());
        verify(vendorProfileRepository).findPageByStatus(eq(VendorProfile.Status.PENDING_APPROVAL),
                any(Pageable.class));
    }

    @Test
    void listClampsPageSizeToTheMaximum() {
        when(vendorProfileRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        vendorAdminService.listProfiles(null, -5, 5000);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(vendorProfileRepository).findAll(captor.capture());
        assertEquals(0, captor.getValue().getPageNumber());
        assertEquals(100, captor.getValue().getPageSize());
    }

    @Test
    void listSkipsTheHoursQueryForAnEmptyPage() {
        when(vendorProfileRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        vendorAdminService.listProfiles(null, 0, 20);

        verify(vendorHoursRepository, never()).findByVendorProfileIdInOrderByWeekdayAsc(anyList());
    }

    // ------------------------------------------------------------------ helpers

    private VendorProfile stubProfile(VendorProfile.Status status) {
        VendorProfile profile = stubProfileLookup(status);
        stubAdminActor();
        return profile;
    }

    /**
     * Stubs only the profile lookup, for the tests that must fail before the
     * acting admin is ever resolved.
     */
    private VendorProfile stubProfileLookup(VendorProfile.Status status) {
        VendorProfile profile = profile(5L, status);
        when(vendorProfileRepository.findByIdWithDetails(5L)).thenReturn(Optional.of(profile));
        return profile;
    }

    private void stubAdminActor() {
        when(userRepository.findByEmailWithRole(ADMIN_EMAIL))
                .thenReturn(Optional.of(User.builder().id(1L).email(ADMIN_EMAIL)
                        .role(Role.builder().id(3L).name("ADMIN").build())
                        .status(User.Status.ACTIVE).build()));
    }

    private static VendorProfile profile(Long id, VendorProfile.Status status) {
        return VendorProfile.builder()
                .id(id)
                .user(User.builder().id(id + 100).email("v" + id + "@test.com").build())
                .businessName("Blossoms " + id)
                .addressLine1("1 Street")
                .serviceLocation(ServiceLocation.builder().id(7L).city("Bengaluru")
                        .area("Koramangala").pincode("560034")
                        .latitude(new BigDecimal("12.93520000"))
                        .longitude(new BigDecimal("77.62450000")).build())
                .latitude(new BigDecimal("12.93520000"))
                .longitude(new BigDecimal("77.62450000"))
                .deliveryRadiusKm(new BigDecimal("5.00"))
                .status(status)
                .reviewCount(0)
                .minOrderAmount(BigDecimal.ZERO)
                .baseDeliveryFee(BigDecimal.ZERO)
                .perKmFee(BigDecimal.ZERO)
                .prepTimeMinutes(30)
                .slotDurationMinutes(60)
                .maxOrdersPerSlot(10)
                .acceptingOrders(true)
                .build();
    }
}
