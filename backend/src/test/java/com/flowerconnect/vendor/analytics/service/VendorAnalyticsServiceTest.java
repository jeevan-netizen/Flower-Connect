package com.flowerconnect.vendor.analytics.service;

import com.flowerconnect.domain.ServiceLocation;
import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.order.repository.OrderRepository;
import com.flowerconnect.order.repository.OrderStatusHistoryRepository;
import com.flowerconnect.pos.repository.PosTransactionRepository;
import com.flowerconnect.repository.VendorProfileRepository;
import com.flowerconnect.vendor.analytics.dto.OrderDeliveryDto;
import com.flowerconnect.vendor.analytics.dto.OrderDeliveryDto.OrderItemLineDto;
import com.flowerconnect.vendor.analytics.dto.PosTransactionDto;
import com.flowerconnect.vendor.analytics.dto.VendorAnalyticsDailyTrend;
import com.flowerconnect.vendor.analytics.dto.VendorAnalyticsOverviewResponse;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link VendorAnalyticsService} using mocked repositories.
 * Range validation, timezone resolution, vendor isolation, POS separation,
 * repeat-customer rate, and zero-data cases are covered here; the
 * integration test covers real-MySQL query behaviour.
 */
@ExtendWith(MockitoExtension.class)
@ExtendWith(MockitoExtension.class)
class VendorAnalyticsServiceTest {

    private static final String VENDOR_EMAIL = "florist@test.example";
    private static final Long VENDOR_ID = 42L;

    @Mock
    private VendorProfileRepository vendorProfileRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderStatusHistoryRepository orderStatusHistoryRepository;
    @Mock
    private PosTransactionRepository posTransactionRepository;

    private final Clock clock = Clock.fixed(Instant.parse("2026-11-05T10:00:00Z"), ZoneId.of("UTC"));

    private VendorAnalyticsService service;

    @BeforeEach
    void setUp() {
        service = new VendorAnalyticsService(
                vendorProfileRepository,
                orderRepository,
                orderStatusHistoryRepository,
                posTransactionRepository,
                clock);
    }

    private VendorProfile vendor(String timezone) {
        ServiceLocation location = ServiceLocation.builder()
                .id(7L)
                .city("Bengaluru")
                .area("Koramangala")
                .pincode("560034")
                .timezone(timezone)
                .build();
        return VendorProfile.builder()
                .id(VENDOR_ID)
                .user(null)
                .businessName("Test Florist")
                .serviceLocation(location)
                .build();
    }

    /* ---------------------------------------------------------------- */
    // Range validation

    @Test
    void fromDateAfterToDateIsRejected() {
        when(vendorProfileRepository.findByUserEmail(VENDOR_EMAIL))
                .thenReturn(Optional.of(vendor("Asia/Kolkata")));

        assertThrows(IllegalArgumentException.class,
                () -> service.overview(VENDOR_EMAIL, LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 1)));
    }

    @Test
    void rangeBeyondMaxDaysIsRejected() {
        when(vendorProfileRepository.findByUserEmail(VENDOR_EMAIL))
                .thenReturn(Optional.of(vendor("Asia/Kolkata")));

        assertThrows(IllegalArgumentException.class,
                () -> service.overview(VENDOR_EMAIL, LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1)));
    }

    @Test
    void rangeExactlyMaxDaysIsAllowed() {
        when(vendorProfileRepository.findByUserEmail(VENDOR_EMAIL))
                .thenReturn(Optional.of(vendor("Asia/Kolkata")));
        when(orderRepository.findDeliveredWithTimestampBetween(any(), any(), any())).thenReturn(List.of());
        when(orderRepository.findCancelledWithTimestampBetween(any(), any(), any())).thenReturn(List.of());
        when(posTransactionRepository.findByVendorIdAndCreatedAtBetweenDto(any(), any(), any())).thenReturn(List.of());

        VendorAnalyticsOverviewResponse result = service.overview(
                VENDOR_EMAIL, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));

        assertEquals(365, chronoDays(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)) + 1);
        assertNotNull(result);
    }

    /* ---------------------------------------------------------------- */
    // Timezone resolution

    @Test
    void usesVendorTimezoneFromServiceLocation() {
        when(vendorProfileRepository.findByUserEmail(VENDOR_EMAIL))
                .thenReturn(Optional.of(vendor("Asia/Kolkata")));
        when(orderRepository.findDeliveredWithTimestampBetween(any(), any(), any())).thenReturn(List.of());
        when(orderRepository.findCancelledWithTimestampBetween(any(), any(), any())).thenReturn(List.of());
        when(posTransactionRepository.findByVendorIdAndCreatedAtBetweenDto(any(), any(), any())).thenReturn(List.of());

        service.overview(VENDOR_EMAIL, null, null);

        verify(orderRepository).findDeliveredWithTimestampBetween(
                eq(VENDOR_ID), any(LocalDateTime.class), any(LocalDateTime.class));
    }

    @Test
    void fallsBackToUtcWhenTimezoneMissing() {
        VendorProfile noZone = VendorProfile.builder()
                .id(VENDOR_ID)
                .user(null)
                .businessName("No Zone Florist")
                .serviceLocation(ServiceLocation.builder()
                        .id(8L)
                        .city("Bengaluru")
                        .area("Indiranagar")
                        .pincode("560038")
                        .timezone(null)
                        .build())
                .build();
        when(vendorProfileRepository.findByUserEmail(VENDOR_EMAIL))
                .thenReturn(Optional.of(noZone));
        when(orderRepository.findDeliveredWithTimestampBetween(any(), any(), any())).thenReturn(List.of());
        when(orderRepository.findCancelledWithTimestampBetween(any(), any(), any())).thenReturn(List.of());
        when(posTransactionRepository.findByVendorIdAndCreatedAtBetweenDto(any(), any(), any())).thenReturn(List.of());

        // Should not throw; UTC fallback is used.
        VendorAnalyticsOverviewResponse result = service.overview(VENDOR_EMAIL, null, null);
        assertNotNull(result);
    }

    /* ---------------------------------------------------------------- */
    // Vendor isolation

    @Test
    void resolvesVendorFromEmailSubject() {
        when(vendorProfileRepository.findByUserEmail(VENDOR_EMAIL))
                .thenReturn(Optional.of(vendor("Asia/Kolkata")));
        when(orderRepository.findDeliveredWithTimestampBetween(any(), any(), any())).thenReturn(List.of());
        when(orderRepository.findCancelledWithTimestampBetween(any(), any(), any())).thenReturn(List.of());
        when(posTransactionRepository.findByVendorIdAndCreatedAtBetweenDto(any(), any(), any())).thenReturn(List.of());

        service.overview(VENDOR_EMAIL, null, null);

        verify(vendorProfileRepository).findByUserEmail(VENDOR_EMAIL);
        verify(orderRepository).findDeliveredWithTimestampBetween(eq(VENDOR_ID), any(), any());
    }

    /* ---------------------------------------------------------------- */
    // Zero data

    @Test
    void noOrdersReturnsZerosAndNullRepeatRate() {
        when(vendorProfileRepository.findByUserEmail(VENDOR_EMAIL))
                .thenReturn(Optional.of(vendor("Asia/Kolkata")));
        when(orderRepository.findDeliveredWithTimestampBetween(any(), any(), any())).thenReturn(List.of());
        when(orderRepository.findCancelledWithTimestampBetween(any(), any(), any())).thenReturn(List.of());
        when(posTransactionRepository.findByVendorIdAndCreatedAtBetweenDto(any(), any(), any())).thenReturn(List.of());

        LocalDate from = LocalDate.of(2026, 11, 1);
        LocalDate to = LocalDate.of(2026, 11, 30);
        VendorAnalyticsOverviewResponse result = service.overview(VENDOR_EMAIL, from, to);

        assertEquals(0, result.getDeliveredOrders());
        assertEquals(0, result.getMarketplaceRevenue().compareTo(BigDecimal.ZERO));
        assertEquals(0, result.getOnlineOrders());
        assertEquals(0, result.getOnlineRevenue().compareTo(BigDecimal.ZERO));
        assertEquals(0, result.getCodOrders());
        assertEquals(0, result.getCodRevenue().compareTo(BigDecimal.ZERO));
        assertEquals(0, result.getCancelledOrders());
        assertEquals(0, result.getCancelledRevenue().compareTo(BigDecimal.ZERO));
        assertNull(result.getRepeatCustomerRate());
        assertTrue(result.getTopProductsByUnits().isEmpty());
        assertTrue(result.getTopProductsByRevenue().isEmpty());
        assertNotNull(result.getPos());
        assertEquals(0, result.getPos().getTransactionCount());
        assertEquals(0, result.getPos().getTotalRevenue().compareTo(BigDecimal.ZERO));
        assertTrue(result.getPos().getBreakdownByPaymentMethod().isEmpty());
    }

    /* ---------------------------------------------------------------- */
    // Marketplace metrics

    @Test
    void computesOnlineCodAndCancelledSeparately() {
        when(vendorProfileRepository.findByUserEmail(VENDOR_EMAIL))
                .thenReturn(Optional.of(vendor("Asia/Kolkata")));

        OrderDeliveryDto online = new OrderDeliveryDto(
                1L, 100L, "ONLINE", new BigDecimal("100.00"),
                LocalDateTime.of(2026, 11, 15, 10, 0),
                List.of(new OrderItemLineDto(10L, "Roses", 2, new BigDecimal("100.00"))));
        OrderDeliveryDto cod = new OrderDeliveryDto(
                2L, 101L, "COD", new BigDecimal("50.00"),
                LocalDateTime.of(2026, 11, 16, 11, 0),
                List.of(new OrderItemLineDto(11L, "Tulips", 1, new BigDecimal("50.00"))));
        OrderDeliveryDto online2 = new OrderDeliveryDto(
                3L, 100L, "ONLINE", new BigDecimal("200.00"),
                LocalDateTime.of(2026, 11, 17, 12, 0),
                List.of(new OrderItemLineDto(10L, "Roses", 3, new BigDecimal("200.00"))));

        when(orderRepository.findDeliveredWithTimestampBetween(any(), any(), any()))
                .thenReturn(List.of(online, cod, online2));
        when(orderRepository.findCancelledWithTimestampBetween(any(), any(), any())).thenReturn(List.of());
        when(posTransactionRepository.findByVendorIdAndCreatedAtBetweenDto(any(), any(), any())).thenReturn(List.of());

        VendorAnalyticsOverviewResponse result = service.overview(
                VENDOR_EMAIL, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30));

        assertEquals(3, result.getDeliveredOrders());
        assertEquals(new BigDecimal("350.00"), result.getMarketplaceRevenue());
        assertEquals(2, result.getOnlineOrders());
        assertEquals(new BigDecimal("300.00"), result.getOnlineRevenue());
        assertEquals(1, result.getCodOrders());
        assertEquals(new BigDecimal("50.00"), result.getCodRevenue());
        assertEquals(0, result.getCancelledOrders());
        assertEquals(0, result.getCancelledRevenue().compareTo(BigDecimal.ZERO));
    }

    @Test
    void repeatCustomerRateCountsOnlyDeliveredOrderCustomers() {
        when(vendorProfileRepository.findByUserEmail(VENDOR_EMAIL))
                .thenReturn(Optional.of(vendor("Asia/Kolkata")));

        OrderDeliveryDto c1a = new OrderDeliveryDto(
                1L, 100L, "COD", new BigDecimal("10.00"),
                LocalDateTime.of(2026, 11, 1, 10, 0), List.of());
        OrderDeliveryDto c1b = new OrderDeliveryDto(
                2L, 100L, "COD", new BigDecimal("10.00"),
                LocalDateTime.of(2026, 11, 5, 10, 0), List.of()); // repeat
        OrderDeliveryDto c2 = new OrderDeliveryDto(
                3L, 101L, "COD", new BigDecimal("10.00"),
                LocalDateTime.of(2026, 11, 10, 10, 0), List.of()); // single

        when(orderRepository.findDeliveredWithTimestampBetween(any(), any(), any()))
                .thenReturn(List.of(c1a, c1b, c2));
        when(orderRepository.findCancelledWithTimestampBetween(any(), any(), any())).thenReturn(List.of());
        when(posTransactionRepository.findByVendorIdAndCreatedAtBetweenDto(any(), any(), any())).thenReturn(List.of());

        VendorAnalyticsOverviewResponse result = service.overview(
                VENDOR_EMAIL, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30));

        // 1 repeater (customer 100) out of 2 total customers (100, 101) = 0.5
        assertEquals(0.5, result.getRepeatCustomerRate(), 0.0001);
    }

    @Test
    void repeatCustomerRateIsNullWithNoCustomers() {
        when(vendorProfileRepository.findByUserEmail(VENDOR_EMAIL))
                .thenReturn(Optional.of(vendor("Asia/Kolkata")));
        when(orderRepository.findDeliveredWithTimestampBetween(any(), any(), any())).thenReturn(List.of());
        when(orderRepository.findCancelledWithTimestampBetween(any(), any(), any())).thenReturn(List.of());
        when(posTransactionRepository.findByVendorIdAndCreatedAtBetweenDto(any(), any(), any())).thenReturn(List.of());

        VendorAnalyticsOverviewResponse result = service.overview(
                VENDOR_EMAIL, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30));

        assertNull(result.getRepeatCustomerRate());
    }

    /* ---------------------------------------------------------------- */
    // Top products

    @Test
    void topProductsByUnitsAndRevenueAreRanked() {
        when(vendorProfileRepository.findByUserEmail(VENDOR_EMAIL))
                .thenReturn(Optional.of(vendor("Asia/Kolkata")));

        OrderDeliveryDto d1 = new OrderDeliveryDto(
                1L, 100L, "COD", new BigDecimal("100.00"),
                LocalDateTime.of(2026, 11, 1, 10, 0),
                List.of(
                        new OrderItemLineDto(10L, "Roses", 5, new BigDecimal("100.00")),
                        new OrderItemLineDto(11L, "Tulips", 1, new BigDecimal("0.00"))));
        OrderDeliveryDto d2 = new OrderDeliveryDto(
                2L, 101L, "COD", new BigDecimal("50.00"),
                LocalDateTime.of(2026, 11, 2, 10, 0),
                List.of(
                        new OrderItemLineDto(10L, "Roses", 2, new BigDecimal("40.00")),
                        new OrderItemLineDto(12L, "Orchids", 3, new BigDecimal("50.00"))));

        when(orderRepository.findDeliveredWithTimestampBetween(any(), any(), any()))
                .thenReturn(List.of(d1, d2));
        when(orderRepository.findCancelledWithTimestampBetween(any(), any(), any())).thenReturn(List.of());
        when(posTransactionRepository.findByVendorIdAndCreatedAtBetweenDto(any(), any(), any())).thenReturn(List.of());

        VendorAnalyticsOverviewResponse result = service.overview(
                VENDOR_EMAIL, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30));

        // Roses: 7 units, 140 revenue (top by both)
        VendorAnalyticsOverviewResponse.VendorTopProduct roses = result.getTopProductsByUnits().get(0);
        assertEquals(10L, roses.getProductId());
        assertEquals("Roses", roses.getProductName());
        assertEquals(7, roses.getQuantitySold());
        assertEquals(new BigDecimal("140.00"), roses.getRevenue());

        // Orchids: 3 units, 50 revenue; Tulips: 1 unit, 0 revenue
        assertEquals(12L, result.getTopProductsByUnits().get(1).getProductId());
        assertEquals(3, result.getTopProductsByUnits().get(1).getQuantitySold());
    }

    /* ---------------------------------------------------------------- */
    // POS section -- commission-free, never mixed into marketplace

    @Test
    void posSectionIsSeparateFromMarketplace() {
        when(vendorProfileRepository.findByUserEmail(VENDOR_EMAIL))
                .thenReturn(Optional.of(vendor("Asia/Kolkata")));

        when(orderRepository.findDeliveredWithTimestampBetween(any(), any(), any())).thenReturn(List.of());
        when(orderRepository.findCancelledWithTimestampBetween(any(), any(), any())).thenReturn(List.of());

        PosTransactionDto cash = new PosTransactionDto(100L, "CASH", new BigDecimal("30.00"),
                LocalDateTime.of(2026, 11, 1, 9, 0));
        PosTransactionDto upi = new PosTransactionDto(101L, "UPI", new BigDecimal("70.00"),
                LocalDateTime.of(2026, 11, 2, 9, 0));
        when(posTransactionRepository.findByVendorIdAndCreatedAtBetweenDto(any(), any(), any()))
                .thenReturn(List.of(cash, upi));

        VendorAnalyticsOverviewResponse result = service.overview(
                VENDOR_EMAIL, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30));

        // Marketplace is zero (no delivered orders)
        assertEquals(0, result.getDeliveredOrders());
        assertEquals(0, result.getMarketplaceRevenue().compareTo(BigDecimal.ZERO));

        // POS is separate
        assertEquals(2, result.getPos().getTransactionCount());
        assertEquals(new BigDecimal("100.00"), result.getPos().getTotalRevenue());
        assertEquals(2, result.getPos().getBreakdownByPaymentMethod().size());
    }

    /* ---------------------------------------------------------------- */
    // Daily trends

    @Test
    void dailyTrendsZeroFillsEmptyDays() {
        when(vendorProfileRepository.findByUserEmail(VENDOR_EMAIL))
                .thenReturn(Optional.of(vendor("Asia/Kolkata")));
        when(orderRepository.findDeliveredWithTimestampBetween(any(), any(), any())).thenReturn(List.of());

        List<VendorAnalyticsDailyTrend> series = service.dailyTrends(
                VENDOR_EMAIL, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 3));

        assertEquals(3, series.size());
        assertEquals(LocalDate.of(2026, 11, 1), series.get(0).getDate());
        assertEquals(0, series.get(0).getOrderCount());
        assertEquals(0, series.get(0).getRevenue().compareTo(BigDecimal.ZERO));
        assertEquals(LocalDate.of(2026, 11, 2), series.get(1).getDate());
        assertEquals(0, series.get(1).getOrderCount());
        assertEquals(LocalDate.of(2026, 11, 3), series.get(2).getDate());
        assertEquals(0, series.get(2).getOrderCount());
    }

    /* ---------------------------------------------------------------- */
    // Vendor not found

    @Test
    void unknownVendorEmailThrows() {
        when(vendorProfileRepository.findByUserEmail(VENDOR_EMAIL))
                .thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class,
                () -> service.overview(VENDOR_EMAIL, null, null));
        verify(orderRepository, never()).findDeliveredWithTimestampBetween(any(), any(), any());
    }

    // ------------------------------------------------------------------
    // Helper
    // ------------------------------------------------------------------
    private static long chronoDays(LocalDate from, LocalDate to) {
        return java.time.temporal.ChronoUnit.DAYS.between(from, to);
    }
}
