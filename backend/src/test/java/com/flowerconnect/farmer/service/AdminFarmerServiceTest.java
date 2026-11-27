package com.flowerconnect.farmer.service;

import com.flowerconnect.domain.Role;
import com.flowerconnect.domain.User;
import com.flowerconnect.exception.BusinessException;
import com.flowerconnect.farmer.domain.FarmerVendorLink;
import com.flowerconnect.farmer.dto.FarmerAdminDetail;
import com.flowerconnect.farmer.dto.FarmerAdminSummary;
import com.flowerconnect.farmer.dto.FarmerVendorLedgerOverview;
import com.flowerconnect.farmer.repository.FarmerVendorLinkRepository;
import com.flowerconnect.repository.UserRepository;
import com.flowerconnect.security.UserStatusService;
import com.flowerconnect.security.dto.AdminUserResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit coverage for {@link AdminFarmerService}: farmer listing, farmer detail
 * (profile plus vendor-separated ledger), and farmer account status changes.
 */
@ExtendWith(MockitoExtension.class)
class AdminFarmerServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.parse("2026-11-27T10:00:00");

    @Mock
    private UserRepository userRepository;

    @Mock
    private FarmerVendorLinkRepository linkRepository;

    @Mock
    private FarmerCollectionService farmerCollectionService;

    @Mock
    private UserStatusService userStatusService;

    @InjectMocks
    private AdminFarmerService adminFarmerService;

    private User farmer(Long id, User.Status status) {
        return User.builder()
                .id(id)
                .email("farmer" + id + "@test.com")
                .fullName("Farm Owner " + id)
                .phone("+919999999999")
                .role(Role.builder().id(4L).name("FARMER").build())
                .status(status)
                .build();
    }

    // ---------------------------------------------------------------- listing

    @Test
    void listFarmersWithNoFiltersReturnsAPage() {
        User farmer = farmer(1L, User.Status.ACTIVE);
        Page<User> page = new PageImpl<>(List.of(farmer), PageRequest.of(0, 20), 1);
        when(userRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(page);

        Page<FarmerAdminSummary> result = adminFarmerService.listFarmers(null, null, 0, 20);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getEmail()).isEqualTo("farmer1@test.com");
        assertThat(result.getContent().get(0).getStatus()).isEqualTo(User.Status.ACTIVE);
        assertThat(result.getTotalElements()).isEqualTo(1);
    }

    @Test
    void listFarmersFiltersByStatus() {
        when(userRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(Page.empty());

        adminFarmerService.listFarmers(null, User.Status.SUSPENDED, 1, 5);

        verify(userRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void listFarmersSearchesNameEmailAndPhone() {
        when(userRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(Page.empty());

        adminFarmerService.listFarmers("rose", null, 0, 20);

        verify(userRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    // --------------------------------------------------------------- detail

    @Test
    void getFarmerDetailReturnsProfileAndVendorSeparatedLedger() {
        User farmer = farmer(1L, User.Status.ACTIVE);
        when(userRepository.findByIdWithRole(1L)).thenReturn(Optional.of(farmer));

        FarmerVendorLink link = FarmerVendorLink.builder()
                .id(10L)
                .farmerId(1L)
                .vendorId(20L)
                .status(FarmerVendorLink.Status.ACTIVE)
                .build();
        when(linkRepository.findByFarmerId(1L)).thenReturn(List.of(link));

        com.flowerconnect.farmer.dto.PassbookResponse passbook =
                com.flowerconnect.farmer.dto.PassbookResponse.builder()
                        .linkId(10L)
                        .farmerId(1L)
                        .farmerName("Farm Owner 1")
                        .vendorId(20L)
                        .vendorName("Green Acres")
                        .linkStatus("ACTIVE")
                        .outstandingBalance(new BigDecimal("1500.00"))
                        .entries(List.of())
                        .build();
        when(farmerCollectionService.getPassbookForAdmin(10L)).thenReturn(passbook);

        FarmerAdminDetail result = adminFarmerService.getFarmerDetail(1L);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getFullName()).isEqualTo("Farm Owner 1");
        assertThat(result.getAddress()).isNull();
        assertThat(result.getStatus()).isEqualTo(User.Status.ACTIVE);
        assertThat(result.getLinkedVendors()).hasSize(1);
        FarmerVendorLedgerOverview overview = result.getLinkedVendors().get(0);
        assertThat(overview.getVendorId()).isEqualTo(20L);
        assertThat(overview.getVendorName()).isEqualTo("Green Acres");
        assertThat(overview.getLinkStatus()).isEqualTo("ACTIVE");
        assertThat(overview.getOutstandingBalance()).isEqualByComparingTo("1500.00");
        assertThat(overview.getEntries()).isEmpty();
    }

    @Test
    void getFarmerDetailIncludesAddressWhenPresent() {
        User farmer = User.builder()
                .id(1L)
                .email("farmer1@test.com")
                .fullName("Farm Owner 1")
                .phone("+919999999999")
                .role(Role.builder().id(4L).name("FARMER").build())
                .status(User.Status.ACTIVE)
                .address("123 Garden Lane")
                .build();
        when(userRepository.findByIdWithRole(1L)).thenReturn(Optional.of(farmer));
        when(linkRepository.findByFarmerId(1L)).thenReturn(List.of());

        FarmerAdminDetail result = adminFarmerService.getFarmerDetail(1L);

        assertThat(result.getAddress()).isEqualTo("123 Garden Lane");
    }

    @Test
    void getFarmerDetailRejectsUnknownUser() {
        when(userRepository.findByIdWithRole(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminFarmerService.getFarmerDetail(404L))
                .isInstanceOf(BusinessException.class)
                .matches(ex -> ((BusinessException) ex).getErrorCode() == com.flowerconnect.exception.ErrorCode.NOT_FOUND);
    }

    @Test
    void getFarmerDetailRejectsNonFarmer() {
        User customer = User.builder()
                .id(1L)
                .email("customer@test.com")
                .role(Role.builder().id(1L).name("CUSTOMER").build())
                .status(User.Status.ACTIVE)
                .build();
        when(userRepository.findByIdWithRole(1L)).thenReturn(Optional.of(customer));

        assertThatThrownBy(() -> adminFarmerService.getFarmerDetail(1L))
                .isInstanceOf(BusinessException.class)
                .matches(ex -> ((BusinessException) ex).getErrorCode() == com.flowerconnect.exception.ErrorCode.NOT_FOUND);
    }

    // -------------------------------------------------------------- status

    @Test
    void changeFarmerStatusDelegatesToUserStatusService() {
        User farmer = farmer(1L, User.Status.ACTIVE);
        when(userRepository.findByIdWithRole(1L)).thenReturn(Optional.of(farmer));
        AdminUserResponse response = AdminUserResponse.builder()
                .id(1L).status(User.Status.SUSPENDED).build();
        when(userStatusService.changeStatus("admin@test.com", 1L,
                User.Status.SUSPENDED, "Payment fraud")).thenReturn(response);

        AdminUserResponse result = adminFarmerService.changeFarmerStatus(
                "admin@test.com", 1L, User.Status.SUSPENDED, "Payment fraud");

        assertThat(result.getStatus()).isEqualTo(User.Status.SUSPENDED);
        verify(userStatusService).changeStatus("admin@test.com", 1L,
                User.Status.SUSPENDED, "Payment fraud");
    }

    @Test
    void changeFarmerStatusRejectsNonFarmer() {
        User customer = User.builder()
                .id(1L)
                .email("customer@test.com")
                .role(Role.builder().id(1L).name("CUSTOMER").build())
                .status(User.Status.ACTIVE)
                .build();
        when(userRepository.findByIdWithRole(1L)).thenReturn(Optional.of(customer));

        assertThatThrownBy(() -> adminFarmerService.changeFarmerStatus(
                "admin@test.com", 1L, User.Status.SUSPENDED, "abuse"))
                .isInstanceOf(BusinessException.class)
                .matches(ex -> ((BusinessException) ex).getErrorCode() == com.flowerconnect.exception.ErrorCode.NOT_FOUND);
    }
}
