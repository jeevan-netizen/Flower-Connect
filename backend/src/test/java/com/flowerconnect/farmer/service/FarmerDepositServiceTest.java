package com.flowerconnect.farmer.service;

import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.exception.BusinessException;
import com.flowerconnect.exception.ErrorCode;
import com.flowerconnect.farmer.domain.FarmerDeposit;
import com.flowerconnect.farmer.domain.FarmerVendorLink;
import com.flowerconnect.farmer.dto.FarmerDepositRequest;
import com.flowerconnect.farmer.dto.FarmerDepositResponse;
import com.flowerconnect.farmer.repository.FarmerDepositRepository;
import com.flowerconnect.farmer.repository.FarmerVendorLinkRepository;
import com.flowerconnect.repository.VendorProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit coverage for the farmer deposit service: ownership, link-status gating,
 * and the deduction/net/amount calculation.
 */
class FarmerDepositServiceTest {

    private FarmerDepositRepository depositRepository;
    private FarmerVendorLinkRepository linkRepository;
    private VendorProfileRepository vendorProfileRepository;
    private FarmerDepositService service;

    private VendorProfile vendor;
    private FarmerVendorLink activeLink;

    @BeforeEach
    void setUp() {
        depositRepository = mock(FarmerDepositRepository.class);
        linkRepository = mock(FarmerVendorLinkRepository.class);
        vendorProfileRepository = mock(VendorProfileRepository.class);
        Clock clock = Clock.fixed(Instant.parse("2026-01-15T10:00:00Z"), ZoneId.of("UTC"));
        service = new FarmerDepositService(depositRepository, linkRepository, vendorProfileRepository, clock);

        vendor = VendorProfile.builder().id(10L).build();
        activeLink = FarmerVendorLink.builder()
                .id(100L)
                .farmerId(1L)
                .vendorId(10L)
                .status(FarmerVendorLink.Status.ACTIVE)
                .build();

        when(vendorProfileRepository.findById(10L)).thenReturn(Optional.of(vendor));
        when(linkRepository.findByIdAndVendorId(100L, 10L)).thenReturn(Optional.of(activeLink));
        when(depositRepository.save(any(FarmerDeposit.class)))
                .thenAnswer(inv -> {
                    FarmerDeposit d = inv.getArgument(0);
                    d.setId(500L);
                    return d;
                });
    }

    private FarmerDepositRequest request(BigDecimal grossKg, BigDecimal pricePerKg, LocalDateTime depositedAt) {
        FarmerDepositRequest r = new FarmerDepositRequest();
        r.setFlowerType("Roses");
        r.setGrossKg(grossKg);
        r.setPricePerKg(pricePerKg);
        r.setDepositedAt(depositedAt);
        return r;
    }

    // ------------------------------------------------------------ happy path

    @Test
    void recordDepositCalculatesDeductionNetAndAmount() {
        // gross 32 kg, price 100 -> deduction 3, net 29, amount 2900
        FarmerDepositResponse response = service.recordDeposit(10L, 100L,
                request(new BigDecimal("32"), new BigDecimal("100"), null));

        assertNotNull(response);
        assertEquals(500L, response.getId());
        assertEquals(100L, response.getLinkId());
        assertEquals("Roses", response.getFlowerType());
        assertEquals(new BigDecimal("32.00"), response.getGrossKg());
        assertEquals(new BigDecimal("3"), response.getDeductionKg());
        assertEquals(new BigDecimal("29.00"), response.getNetKg());
        assertEquals(new BigDecimal("100.00"), response.getPricePerKg());
        assertEquals(new BigDecimal("2900.00"), response.getAmount());
        assertEquals(LocalDateTime.parse("2026-01-15T10:00:00"), response.getDepositedAt());
    }

    @Test
    void recordDepositHonorsSuppliedDepositedAt() {
        // Supplied deposited-at must not be after the injected clock (2026-01-15T10:00:00Z).
        LocalDateTime depositedAt = LocalDateTime.parse("2026-01-15T09:30:00");
        FarmerDepositResponse response = service.recordDeposit(10L, 100L,
                request(new BigDecimal("10"), new BigDecimal("10"), depositedAt));

        assertEquals(depositedAt, response.getDepositedAt());
    }

    @Test
    void recordDepositOmitsDepositedAtUsesInjectedClock() {
        FarmerDepositResponse response = service.recordDeposit(10L, 100L,
                request(new BigDecimal("10"), new BigDecimal("10"), null));

        assertEquals(LocalDateTime.parse("2026-01-15T10:00:00"), response.getDepositedAt());
    }

    @Test
    void recordDepositRejectsFutureDepositedAt() {
        LocalDateTime future = LocalDateTime.parse("2026-01-16T10:00:00");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.recordDeposit(10L, 100L,
                        request(new BigDecimal("10"), new BigDecimal("10"), future)));
        assertEquals(ErrorCode.VALIDATION_FAILED, ex.getErrorCode());
    }

    @Test
    void recordDepositBelowTenKgDeductsZero() {
        FarmerDepositResponse response = service.recordDeposit(10L, 100L,
                request(new BigDecimal("5"), new BigDecimal("10"), null));
        assertEquals(new BigDecimal("0"), response.getDeductionKg());
        assertEquals(new BigDecimal("5.00"), response.getNetKg());
        assertEquals(new BigDecimal("50.00"), response.getAmount());
    }

    @Test
    void recordDepositFiftyKgDeductsFive() {
        FarmerDepositResponse response = service.recordDeposit(10L, 100L,
                request(new BigDecimal("50"), new BigDecimal("10"), null));
        assertEquals(new BigDecimal("5"), response.getDeductionKg());
        assertEquals(new BigDecimal("45.00"), response.getNetKg());
        assertEquals(new BigDecimal("450.00"), response.getAmount());
    }

    // ----------------------------------------------------------- ownership

    @Test
    void recordDepositRejectsForeignLink() {
        // vendor 99 exists, but link 100 belongs to another vendor (vendor 10)
        when(vendorProfileRepository.findById(99L)).thenReturn(Optional.of(vendor));
        when(linkRepository.findByIdAndVendorId(100L, 99L)).thenReturn(Optional.empty());
        when(linkRepository.findById(100L)).thenReturn(Optional.of(activeLink));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.recordDeposit(99L, 100L, request(new BigDecimal("10"), new BigDecimal("10"), null)));
        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
    }

    @Test
    void recordDepositRejectsMissingLink() {
        when(linkRepository.findByIdAndVendorId(100L, 10L)).thenReturn(Optional.empty());
        when(linkRepository.findById(100L)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.recordDeposit(10L, 100L, request(new BigDecimal("10"), new BigDecimal("10"), null)));
        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void recordDepositRejectsMissingVendor() {
        when(vendorProfileRepository.findById(10L)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.recordDeposit(10L, 100L, request(new BigDecimal("10"), new BigDecimal("10"), null)));
        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
    }

    // -------------------------------------------------------- link status

    @Test
    void recordDepositRejectsNonActiveLink() {
        FarmerVendorLink pendingLink = FarmerVendorLink.builder()
                .id(100L).farmerId(1L).vendorId(10L)
                .status(FarmerVendorLink.Status.PENDING).build();
        when(linkRepository.findByIdAndVendorId(100L, 10L)).thenReturn(Optional.of(pendingLink));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.recordDeposit(10L, 100L, request(new BigDecimal("10"), new BigDecimal("10"), null)));
        assertEquals(ErrorCode.CONFLICT, ex.getErrorCode());
    }

    @Test
    void recordDepositRejectsRejectedLink() {
        FarmerVendorLink rejectedLink = FarmerVendorLink.builder()
                .id(100L).farmerId(1L).vendorId(10L)
                .status(FarmerVendorLink.Status.REJECTED).build();
        when(linkRepository.findByIdAndVendorId(100L, 10L)).thenReturn(Optional.of(rejectedLink));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.recordDeposit(10L, 100L, request(new BigDecimal("10"), new BigDecimal("10"), null)));
        assertEquals(ErrorCode.CONFLICT, ex.getErrorCode());
    }

    @Test
    void recordDepositRejectsSuspendedLink() {
        FarmerVendorLink suspendedLink = FarmerVendorLink.builder()
                .id(100L).farmerId(1L).vendorId(10L)
                .status(FarmerVendorLink.Status.SUSPENDED).build();
        when(linkRepository.findByIdAndVendorId(100L, 10L)).thenReturn(Optional.of(suspendedLink));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.recordDeposit(10L, 100L, request(new BigDecimal("10"), new BigDecimal("10"), null)));
        assertEquals(ErrorCode.CONFLICT, ex.getErrorCode());
    }
}
