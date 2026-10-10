package com.flowerconnect.discovery.service;

import com.flowerconnect.discovery.dto.DiscoveryResponse;
import com.flowerconnect.domain.ServiceLocation;
import com.flowerconnect.domain.User;
import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.geo.dto.PageResponse;
import com.flowerconnect.repository.ServiceLocationRepository;
import com.flowerconnect.repository.VendorProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.flowerconnect.exception.BusinessException;
import com.flowerconnect.exception.ErrorCode;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DiscoveryServiceTest {

    @Mock
    private VendorProfileRepository vendorProfileRepository;

    @Mock
    private ServiceLocationRepository serviceLocationRepository;

    private DiscoveryService discoveryService;

    private ServiceLocation originLocation;

    @BeforeEach
    void setUp() {
        discoveryService = new DiscoveryService(vendorProfileRepository, serviceLocationRepository);

        originLocation = ServiceLocation.builder()
                .id(1L)
                .city("Bengaluru")
                .area("Koramangala")
                .pincode("560034")
                .latitude(new BigDecimal("12.9352"))
                .longitude(new BigDecimal("77.6145"))
                .build();
    }

    @Test
    void discoverThrowsWhenLocationUnknown() {
        when(serviceLocationRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> discoveryService.discover(999L, 0, 20))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Unknown service location")
                .matches(ex -> ((BusinessException) ex).getErrorCode() == ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void discoverReturnsEmptyPageWhenNoApprovedVendors() {
        when(serviceLocationRepository.findById(1L)).thenReturn(Optional.of(originLocation));
        when(vendorProfileRepository.findMaxDeliveryRadiusForApproved()).thenReturn(null);

        PageResponse<DiscoveryResponse> response = discoveryService.discover(1L, 0, 20);

        assertThat(response.getContent()).isEmpty();
        assertThat(response.getTotalElements()).isZero();
        assertThat(response.getTotalPages()).isZero();
        assertThat(response.isEmpty()).isTrue();
    }

    @Test
    void discoverReturnsEmptyPageWhenNoCandidatesInBoundingBox() {
        when(serviceLocationRepository.findById(1L)).thenReturn(Optional.of(originLocation));
        when(vendorProfileRepository.findMaxDeliveryRadiusForApproved())
                .thenReturn(new BigDecimal("5.00"));
        when(vendorProfileRepository.findApprovedAcceptingInBoundingBox(any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        PageResponse<DiscoveryResponse> response = discoveryService.discover(1L, 0, 20);

        assertThat(response.getContent()).isEmpty();
        assertThat(response.getTotalElements()).isZero();
    }

    @Test
    void discoverFiltersByDeliveryRadiusAndSortsByDistance() {
        when(serviceLocationRepository.findById(1L)).thenReturn(Optional.of(originLocation));
        when(vendorProfileRepository.findMaxDeliveryRadiusForApproved())
                .thenReturn(new BigDecimal("10.00"));

        VendorProfile nearVendor = createVendor(1L, "Near Florist",
                new BigDecimal("12.9400"), new BigDecimal("77.6200"),
                new BigDecimal("5.00"), new BigDecimal("10.00"), new BigDecimal("5.00"));
        VendorProfile farVendor = createVendor(2L, "Far Florist",
                new BigDecimal("12.9500"), new BigDecimal("77.6300"),
                new BigDecimal("5.00"), new BigDecimal("10.00"), new BigDecimal("5.00"));
        VendorProfile outOfRangeVendor = createVendor(3L, "Out of Range Florist",
                new BigDecimal("12.9500"), new BigDecimal("77.6300"),
                new BigDecimal("1.00"), new BigDecimal("10.00"), new BigDecimal("5.00"));

        when(vendorProfileRepository.findApprovedAcceptingInBoundingBox(any(), any(), any(), any()))
                .thenReturn(List.of(nearVendor, farVendor, outOfRangeVendor));

        PageResponse<DiscoveryResponse> response = discoveryService.discover(1L, 0, 20);

        assertThat(response.getContent()).hasSize(2);
        assertThat(response.getContent().get(0).getBusinessName()).isEqualTo("Near Florist");
        assertThat(response.getContent().get(1).getBusinessName()).isEqualTo("Far Florist");
        assertThat(response.getContent().get(0).getDistanceKm())
                .isLessThan(response.getContent().get(1).getDistanceKm());
    }

    @Test
    void discoverPaginatesCorrectly() {
        when(serviceLocationRepository.findById(1L)).thenReturn(Optional.of(originLocation));
        when(vendorProfileRepository.findMaxDeliveryRadiusForApproved())
                .thenReturn(new BigDecimal("10.00"));

        List<VendorProfile> vendors = List.of(
                createVendor(1L, "Vendor 1", new BigDecimal("12.9400"), new BigDecimal("77.6200"),
                        new BigDecimal("5.00"), new BigDecimal("10.00"), new BigDecimal("5.00")),
                createVendor(2L, "Vendor 2", new BigDecimal("12.9450"), new BigDecimal("77.6250"),
                        new BigDecimal("5.00"), new BigDecimal("10.00"), new BigDecimal("5.00")),
                createVendor(3L, "Vendor 3", new BigDecimal("12.9500"), new BigDecimal("77.6300"),
                        new BigDecimal("5.00"), new BigDecimal("10.00"), new BigDecimal("5.00")),
                createVendor(4L, "Vendor 4", new BigDecimal("12.9550"), new BigDecimal("77.6350"),
                        new BigDecimal("5.00"), new BigDecimal("10.00"), new BigDecimal("5.00")),
                createVendor(5L, "Vendor 5", new BigDecimal("12.9600"), new BigDecimal("77.6400"),
                        new BigDecimal("5.00"), new BigDecimal("10.00"), new BigDecimal("5.00"))
        );

        when(vendorProfileRepository.findApprovedAcceptingInBoundingBox(any(), any(), any(), any()))
                .thenReturn(vendors);

        PageResponse<DiscoveryResponse> page0 = discoveryService.discover(1L, 0, 2);
        assertThat(page0.getContent()).hasSize(2);
        assertThat(page0.getTotalElements()).isEqualTo(5);
        assertThat(page0.getTotalPages()).isEqualTo(3);
        assertThat(page0.isFirst()).isTrue();
        assertThat(page0.isLast()).isFalse();

        PageResponse<DiscoveryResponse> page1 = discoveryService.discover(1L, 1, 2);
        assertThat(page1.getContent()).hasSize(2);
        assertThat(page1.isFirst()).isFalse();
        assertThat(page1.isLast()).isFalse();

        PageResponse<DiscoveryResponse> page2 = discoveryService.discover(1L, 2, 2);
        assertThat(page2.getContent()).hasSize(1);
        assertThat(page2.isLast()).isTrue();
    }

    @Test
    void discoverClampsPageAndSize() {
        when(serviceLocationRepository.findById(1L)).thenReturn(Optional.of(originLocation));
        when(vendorProfileRepository.findMaxDeliveryRadiusForApproved())
                .thenReturn(new BigDecimal("10.00"));
        when(vendorProfileRepository.findApprovedAcceptingInBoundingBox(any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        PageResponse<DiscoveryResponse> response = discoveryService.discover(1L, -1, 200);

        assertThat(response.getPage()).isZero();
        assertThat(response.getSize()).isEqualTo(100);
    }

    @Test
    void discoverComputesEstimatedDeliveryFeeCorrectly() {
        when(serviceLocationRepository.findById(1L)).thenReturn(Optional.of(originLocation));
        when(vendorProfileRepository.findMaxDeliveryRadiusForApproved())
                .thenReturn(new BigDecimal("10.00"));

        VendorProfile vendor = createVendor(1L, "Test Florist",
                new BigDecimal("12.9400"), new BigDecimal("77.6200"),
                new BigDecimal("5.00"),
                new BigDecimal("30.00"),  // baseDeliveryFee
                new BigDecimal("10.00")); // perKmFee

        when(vendorProfileRepository.findApprovedAcceptingInBoundingBox(any(), any(), any(), any()))
                .thenReturn(List.of(vendor));

        PageResponse<DiscoveryResponse> response = discoveryService.discover(1L, 0, 20);

        assertThat(response.getContent()).hasSize(1);
        DiscoveryResponse dto = response.getContent().get(0);
        BigDecimal expectedFee = new BigDecimal("30.00").add(dto.getDistanceKm().multiply(new BigDecimal("10.00")))
                .setScale(2, java.math.RoundingMode.HALF_UP);
        assertThat(dto.getEstimatedDeliveryFee()).isEqualByComparingTo(expectedFee);
    }

    @Test
    void discoverIncludesFreeDeliveryAboveAsInformational() {
        when(serviceLocationRepository.findById(1L)).thenReturn(Optional.of(originLocation));
        when(vendorProfileRepository.findMaxDeliveryRadiusForApproved())
                .thenReturn(new BigDecimal("10.00"));

        VendorProfile vendor = createVendor(1L, "Test Florist",
                new BigDecimal("12.9400"), new BigDecimal("77.6200"),
                new BigDecimal("5.00"),
                new BigDecimal("30.00"),
                new BigDecimal("10.00"));
        vendor.setFreeDeliveryAbove(new BigDecimal("500.00"));

        when(vendorProfileRepository.findApprovedAcceptingInBoundingBox(any(), any(), any(), any()))
                .thenReturn(List.of(vendor));

        PageResponse<DiscoveryResponse> response = discoveryService.discover(1L, 0, 20);

        assertThat(response.getContent().get(0).getFreeDeliveryAbove())
                .isEqualByComparingTo(new BigDecimal("500.00"));
    }

    @Test
    void discoverTieBreaksByVendorId() {
        when(serviceLocationRepository.findById(1L)).thenReturn(Optional.of(originLocation));
        when(vendorProfileRepository.findMaxDeliveryRadiusForApproved())
                .thenReturn(new BigDecimal("10.00"));

        // Two vendors at exactly the same coordinates (same distance)
        VendorProfile vendor1 = createVendor(1L, "Vendor A",
                new BigDecimal("12.9400"), new BigDecimal("77.6200"),
                new BigDecimal("5.00"), new BigDecimal("10.00"), new BigDecimal("5.00"));
        VendorProfile vendor2 = createVendor(2L, "Vendor B",
                new BigDecimal("12.9400"), new BigDecimal("77.6200"),
                new BigDecimal("5.00"), new BigDecimal("10.00"), new BigDecimal("5.00"));

        when(vendorProfileRepository.findApprovedAcceptingInBoundingBox(any(), any(), any(), any()))
                .thenReturn(List.of(vendor2, vendor1)); // Reversed order

        PageResponse<DiscoveryResponse> response = discoveryService.discover(1L, 0, 20);

        assertThat(response.getContent()).hasSize(2);
        // Vendor 1 (lower id) should come first due to tie-breaker
        assertThat(response.getContent().get(0).getId()).isEqualTo(1L);
        assertThat(response.getContent().get(1).getId()).isEqualTo(2L);
    }

    @Test
    void discoverExcludesNonAcceptingVendors() {
        when(serviceLocationRepository.findById(1L)).thenReturn(Optional.of(originLocation));
        when(vendorProfileRepository.findMaxDeliveryRadiusForApproved())
                .thenReturn(new BigDecimal("10.00"));

        VendorProfile acceptingVendor = createVendor(1L, "Accepting Florist",
                new BigDecimal("12.9400"), new BigDecimal("77.6200"),
                new BigDecimal("5.00"), new BigDecimal("10.00"), new BigDecimal("5.00"));
        VendorProfile nonAcceptingVendor = createVendor(2L, "Closed Florist",
                new BigDecimal("12.9400"), new BigDecimal("77.6200"),
                new BigDecimal("5.00"), new BigDecimal("10.00"), new BigDecimal("5.00"));
        nonAcceptingVendor.setAcceptingOrders(false);

        // Repository query already filters by acceptingOrders = true,
        // but verify the service doesn't add non-accepting vendors
        when(vendorProfileRepository.findApprovedAcceptingInBoundingBox(any(), any(), any(), any()))
                .thenReturn(List.of(acceptingVendor));

        PageResponse<DiscoveryResponse> response = discoveryService.discover(1L, 0, 20);

        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().get(0).getBusinessName()).isEqualTo("Accepting Florist");
    }

    private VendorProfile createVendor(Long id, String name,
                                       BigDecimal lat, BigDecimal lng,
                                       BigDecimal radius, BigDecimal baseFee, BigDecimal perKmFee) {
        ServiceLocation location = ServiceLocation.builder()
                .id(10L + id)
                .city("Bengaluru")
                .area("Area " + id)
                .pincode("5600" + String.format("%02d", id))
                .latitude(lat)
                .longitude(lng)
                .build();

        User user = User.builder()
                .id(100L + id)
                .email("vendor" + id + "@test.com")
                .build();

        return VendorProfile.builder()
                .id(id)
                .businessName(name)
                .description("Description for " + name)
                .serviceLocation(location)
                .latitude(lat)
                .longitude(lng)
                .deliveryRadiusKm(radius)
                .minOrderAmount(new BigDecimal("100.00"))
                .baseDeliveryFee(baseFee)
                .perKmFee(perKmFee)
                .freeDeliveryAbove(new BigDecimal("500.00"))
                .prepTimeMinutes(30)
                .avgRating(new BigDecimal("4.50"))
                .reviewCount(10)
                .acceptingOrders(true)
                .user(user)
                .build();
    }
}
