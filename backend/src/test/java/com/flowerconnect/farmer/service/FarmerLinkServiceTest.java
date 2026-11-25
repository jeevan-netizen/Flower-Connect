package com.flowerconnect.farmer.service;

import com.flowerconnect.domain.Role;
import com.flowerconnect.domain.User;
import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.exception.BusinessException;
import com.flowerconnect.exception.ErrorCode;
import com.flowerconnect.farmer.domain.FarmerVendorLink;
import com.flowerconnect.farmer.dto.FarmerLinkRequest;
import com.flowerconnect.farmer.dto.FarmerLinkResponse;
import com.flowerconnect.farmer.dto.FarmerRegisterRequest;
import com.flowerconnect.farmer.dto.FarmerRegisterResponse;
import com.flowerconnect.farmer.repository.FarmerVendorLinkRepository;
import com.flowerconnect.repository.RoleRepository;
import com.flowerconnect.repository.UserRepository;
import com.flowerconnect.repository.VendorProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit coverage for the farmer link service: registration, link requests,
 * listings and vendor transitions.
 */
@ExtendWith(MockitoExtension.class)
class FarmerLinkServiceTest {

    @Mock
    private FarmerVendorLinkRepository linkRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private VendorProfileRepository vendorProfileRepository;
    @Mock
    private com.flowerconnect.security.util.EmailNormalizer emailNormalizer;
    @Mock
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @InjectMocks
    private FarmerLinkService farmerLinkService;

    private User farmer;
    private Role farmerRole;
    private VendorProfile approvedVendor;
    private VendorProfile pendingVendor;
    private FarmerVendorLink pendingLink;

    @BeforeEach
    void setUp() {
        farmerRole = Role.builder().id(4L).name("FARMER").build();
        farmer = User.builder()
                .id(1L)
                .email("farmer@test.com")
                .role(farmerRole)
                .status(User.Status.ACTIVE)
                .build();

        approvedVendor = VendorProfile.builder()
                .id(10L)
                .businessName("Green Acres")
                .status(VendorProfile.Status.APPROVED)
                .build();
        pendingVendor = VendorProfile.builder()
                .id(11L)
                .businessName("Not Yet Approved")
                .status(VendorProfile.Status.PENDING_APPROVAL)
                .build();

        pendingLink = FarmerVendorLink.builder()
                .id(100L)
                .farmerId(1L)
                .vendorId(10L)
                .status(FarmerVendorLink.Status.PENDING)
                .createdAt(LocalDateTime.parse("2026-01-01T10:00:00"))
                .updatedAt(LocalDateTime.parse("2026-01-01T10:00:00"))
                .build();
    }

    // ----------------------------------------------------------- registration

    @Test
    void registerCreatesFarmerAccount() {
        FarmerRegisterRequest request = FarmerRegisterRequest.builder()
                .email("farmer@test.com")
                .password("Secret123")
                .fullName("Farm Owner")
                .phone("+919999999999")
                .build();
        when(userRepository.existsByEmail("farmer@test.com")).thenReturn(false);
        when(userRepository.existsByPhone("+919999999999")).thenReturn(false);
        when(roleRepository.findByName("FARMER")).thenReturn(Optional.of(farmerRole));
        when(passwordEncoder.encode("Secret123")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenReturn(User.builder()
                .id(1L).email("farmer@test.com").fullName("Farm Owner")
                .role(farmerRole).status(User.Status.ACTIVE).build());

        FarmerRegisterResponse response = farmerLinkService.register(request);

        assertNotNull(response);
        assertEquals(1L, response.getId());
        assertEquals("farmer@test.com", response.getEmail());
        assertEquals("Farm Owner", response.getFullName());
        assertEquals("FARMER", response.getRole());
        verify(userRepository).save(any(User.class));
    }

    @Test
    void registerRejectsDuplicateEmail() {
        FarmerRegisterRequest request = FarmerRegisterRequest.builder()
                .email("farmer@test.com")
                .password("Secret123")
                .fullName("Farm Owner")
                .build();
        when(userRepository.existsByEmail("farmer@test.com")).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> farmerLinkService.register(request));
        assertEquals(ErrorCode.CONFLICT, ex.getErrorCode());
    }

    // --------------------------------------------------------- eligible vendors

    @Test
    void listEligibleVendorsReturnsApprovedVendorsWithLinkStatus() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(farmer));
        when(vendorProfileRepository.findAllByStatus(VendorProfile.Status.APPROVED))
                .thenReturn(List.of(approvedVendor));
        when(linkRepository.findByFarmerId(1L)).thenReturn(List.of(pendingLink));

        List<com.flowerconnect.farmer.dto.FarmerVendorResponse> responses =
                farmerLinkService.listEligibleVendors(1L, null);

        assertEquals(1, responses.size());
        assertEquals(10L, responses.get(0).getId());
        assertEquals("Green Acres", responses.get(0).getBusinessName());
        assertEquals("APPROVED", responses.get(0).getVendorStatus());
        assertEquals("PENDING", responses.get(0).getLinkStatus());
    }

    @Test
    void listEligibleVendorsFiltersByNameQuery() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(farmer));
        when(vendorProfileRepository.findAllByStatus(VendorProfile.Status.APPROVED))
                .thenReturn(List.of(approvedVendor));
        when(linkRepository.findByFarmerId(1L)).thenReturn(List.of());

        List<com.flowerconnect.farmer.dto.FarmerVendorResponse> responses =
                farmerLinkService.listEligibleVendors(1L, "Green");

        assertEquals(1, responses.size());

        responses = farmerLinkService.listEligibleVendors(1L, "Acme");
        assertTrue(responses.isEmpty());
    }

    @Test
    void listEligibleVendorsRejectsNonFarmer() {
        User florist = User.builder().id(2L).role(Role.builder().name("FLORIST").build()).build();
        when(userRepository.findById(2L)).thenReturn(Optional.of(florist));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> farmerLinkService.listEligibleVendors(2L, null));
        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
    }

    // ----------------------------------------------------------- request link

    @Test
    void requestLinkCreatesPendingLink() {
        FarmerLinkRequest request = FarmerLinkRequest.builder().vendorId(10L).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(farmer));
        when(vendorProfileRepository.findById(10L)).thenReturn(Optional.of(approvedVendor));
        when(linkRepository.findByFarmerIdAndVendorId(1L, 10L)).thenReturn(Optional.empty());
        when(linkRepository.save(any(FarmerVendorLink.class))).thenAnswer(inv -> inv.getArgument(0));

        FarmerLinkResponse response = farmerLinkService.requestLink(1L, request);

        assertNotNull(response);
        assertEquals(10L, response.getVendorId());
        assertEquals("Green Acres", response.getVendorName());
        assertEquals("PENDING", response.getStatus());
    }

    @Test
    void requestLinkReusesRejectedLink() {
        FarmerLinkRequest request = FarmerLinkRequest.builder().vendorId(10L).build();
        FarmerVendorLink rejected = FarmerVendorLink.builder()
                .id(101L).farmerId(1L).vendorId(10L)
                .status(FarmerVendorLink.Status.REJECTED).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(farmer));
        when(vendorProfileRepository.findById(10L)).thenReturn(Optional.of(approvedVendor));
        when(linkRepository.findByFarmerIdAndVendorId(1L, 10L)).thenReturn(Optional.of(rejected));
        when(linkRepository.save(any(FarmerVendorLink.class))).thenAnswer(inv -> inv.getArgument(0));

        FarmerLinkResponse response = farmerLinkService.requestLink(1L, request);

        assertEquals("PENDING", response.getStatus());
        ArgumentCaptor<FarmerVendorLink> captor = ArgumentCaptor.forClass(FarmerVendorLink.class);
        verify(linkRepository).save(captor.capture());
        assertEquals(FarmerVendorLink.Status.PENDING, captor.getValue().getStatus());
    }

    @Test
    void requestLinkConflictsOnExistingPending() {
        FarmerLinkRequest request = FarmerLinkRequest.builder().vendorId(10L).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(farmer));
        when(vendorProfileRepository.findById(10L)).thenReturn(Optional.of(approvedVendor));
        when(linkRepository.findByFarmerIdAndVendorId(1L, 10L)).thenReturn(Optional.of(pendingLink));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> farmerLinkService.requestLink(1L, request));
        assertEquals(ErrorCode.CONFLICT, ex.getErrorCode());
    }

    @Test
    void requestLinkRejectsVendorNotApproved() {
        FarmerLinkRequest request = FarmerLinkRequest.builder().vendorId(11L).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(farmer));
        when(vendorProfileRepository.findById(11L)).thenReturn(Optional.of(pendingVendor));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> farmerLinkService.requestLink(1L, request));
        assertEquals(ErrorCode.VALIDATION_FAILED, ex.getErrorCode());
    }

    @Test
    void requestLinkRejectsUnknownVendor() {
        FarmerLinkRequest request = FarmerLinkRequest.builder().vendorId(999L).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(farmer));
        when(vendorProfileRepository.findById(999L)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> farmerLinkService.requestLink(1L, request));
        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
    }

    // ---------------------------------------------------------- list links

    @Test
    void listFarmerLinksReturnsOwnLinks() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(farmer));
        when(linkRepository.findByFarmerId(1L)).thenReturn(List.of(pendingLink));
        when(vendorProfileRepository.findAllById(List.of(10L))).thenReturn(List.of(approvedVendor));

        List<FarmerLinkResponse> responses = farmerLinkService.listFarmerLinks(1L);

        assertEquals(1, responses.size());
        assertEquals(100L, responses.get(0).getId());
        assertEquals("PENDING", responses.get(0).getStatus());
        assertEquals("Green Acres", responses.get(0).getVendorName());
    }

    @Test
    void listVendorLinksReturnsVendorLinks() {
        when(vendorProfileRepository.existsById(10L)).thenReturn(true);
        when(linkRepository.findByVendorId(10L)).thenReturn(List.of(pendingLink));
        when(vendorProfileRepository.findAllById(List.of(10L))).thenReturn(List.of(approvedVendor));

        List<FarmerLinkResponse> responses = farmerLinkService.listVendorLinks(10L, null);

        assertEquals(1, responses.size());
        assertEquals("PENDING", responses.get(0).getStatus());
    }

    @Test
    void listVendorLinksFiltersByStatus() {
        when(vendorProfileRepository.existsById(10L)).thenReturn(true);
        when(linkRepository.findByVendorIdAndStatus(10L, FarmerVendorLink.Status.ACTIVE))
                .thenReturn(List.of());

        List<FarmerLinkResponse> responses = farmerLinkService.listVendorLinks(10L,
                FarmerVendorLink.Status.ACTIVE);
        assertTrue(responses.isEmpty());
    }

    // --------------------------------------------------------- vendor transitions

    @Test
    void acceptLinkMovesPendingToActive() {
        when(linkRepository.findByIdAndVendorId(100L, 10L)).thenReturn(Optional.of(pendingLink));
        when(linkRepository.save(any(FarmerVendorLink.class))).thenAnswer(inv -> inv.getArgument(0));
        when(vendorProfileRepository.findById(10L)).thenReturn(Optional.of(approvedVendor));

        FarmerLinkResponse response = farmerLinkService.acceptLink(10L, 100L);

        assertEquals("ACTIVE", response.getStatus());
        assertEquals(FarmerVendorLink.Status.ACTIVE, pendingLink.getStatus());
    }

    @Test
    void acceptLinkRejectsNonPending() {
        FarmerVendorLink active = FarmerVendorLink.builder()
                .id(100L).farmerId(1L).vendorId(10L)
                .status(FarmerVendorLink.Status.ACTIVE).build();
        when(linkRepository.findByIdAndVendorId(100L, 10L)).thenReturn(Optional.of(active));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> farmerLinkService.acceptLink(10L, 100L));
        assertEquals(ErrorCode.CONFLICT, ex.getErrorCode());
    }

    @Test
    void rejectLinkMovesPendingToRejected() {
        when(linkRepository.findByIdAndVendorId(100L, 10L)).thenReturn(Optional.of(pendingLink));
        when(linkRepository.save(any(FarmerVendorLink.class))).thenAnswer(inv -> inv.getArgument(0));
        when(vendorProfileRepository.findById(10L)).thenReturn(Optional.of(approvedVendor));

        FarmerLinkResponse response = farmerLinkService.rejectLink(10L, 100L);

        assertEquals("REJECTED", response.getStatus());
    }

    @Test
    void suspendLinkMovesActiveToSuspended() {
        FarmerVendorLink active = FarmerVendorLink.builder()
                .id(100L).farmerId(1L).vendorId(10L)
                .status(FarmerVendorLink.Status.ACTIVE).build();
        when(linkRepository.findByIdAndVendorId(100L, 10L)).thenReturn(Optional.of(active));
        when(linkRepository.save(any(FarmerVendorLink.class))).thenAnswer(inv -> inv.getArgument(0));
        when(vendorProfileRepository.findById(10L)).thenReturn(Optional.of(approvedVendor));

        FarmerLinkResponse response = farmerLinkService.suspendLink(10L, 100L);

        assertEquals("SUSPENDED", response.getStatus());
    }

    @Test
    void reactivateLinkMovesSuspendedToActive() {
        FarmerVendorLink suspended = FarmerVendorLink.builder()
                .id(100L).farmerId(1L).vendorId(10L)
                .status(FarmerVendorLink.Status.SUSPENDED).build();
        when(linkRepository.findByIdAndVendorId(100L, 10L)).thenReturn(Optional.of(suspended));
        when(linkRepository.save(any(FarmerVendorLink.class))).thenAnswer(inv -> inv.getArgument(0));
        when(vendorProfileRepository.findById(10L)).thenReturn(Optional.of(approvedVendor));

        FarmerLinkResponse response = farmerLinkService.reactivateLink(10L, 100L);

        assertEquals("ACTIVE", response.getStatus());
    }

    @Test
    void transitionRejectsForeignVendor() {
        when(linkRepository.findByIdAndVendorId(100L, 99L)).thenReturn(Optional.empty());
        when(linkRepository.findById(100L)).thenReturn(Optional.of(pendingLink));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> farmerLinkService.acceptLink(99L, 100L));
        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
    }

    @Test
    void transitionRejectsMissingLink() {
        when(linkRepository.findByIdAndVendorId(100L, 10L)).thenReturn(Optional.empty());
        when(linkRepository.findById(100L)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> farmerLinkService.acceptLink(10L, 100L));
        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void invalidTransitionsAreRejected() {
        // REJECTED -> ACTIVE is invalid
        FarmerVendorLink rejected = FarmerVendorLink.builder()
                .id(100L).farmerId(1L).vendorId(10L)
                .status(FarmerVendorLink.Status.REJECTED).build();
        when(linkRepository.findByIdAndVendorId(100L, 10L)).thenReturn(Optional.of(rejected));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> farmerLinkService.acceptLink(10L, 100L));
        assertEquals(ErrorCode.CONFLICT, ex.getErrorCode());

        // SUSPENDED -> REJECTED is invalid
        FarmerVendorLink suspended = FarmerVendorLink.builder()
                .id(100L).farmerId(1L).vendorId(10L)
                .status(FarmerVendorLink.Status.SUSPENDED).build();
        when(linkRepository.findByIdAndVendorId(100L, 10L)).thenReturn(Optional.of(suspended));

        BusinessException ex2 = assertThrows(BusinessException.class,
                () -> farmerLinkService.rejectLink(10L, 100L));
        assertEquals(ErrorCode.CONFLICT, ex2.getErrorCode());
    }
}
