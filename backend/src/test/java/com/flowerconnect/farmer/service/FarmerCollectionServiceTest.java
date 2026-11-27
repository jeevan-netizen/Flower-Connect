package com.flowerconnect.farmer.service;

import com.flowerconnect.domain.User;
import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.exception.BusinessException;
import com.flowerconnect.exception.ErrorCode;
import com.flowerconnect.farmer.domain.FarmerDeposit;
import com.flowerconnect.farmer.domain.FarmerPayment;
import com.flowerconnect.farmer.domain.FarmerVendorLink;
import com.flowerconnect.farmer.dto.FarmerCollectionRequest;
import com.flowerconnect.farmer.dto.FarmerCollectionResponse;
import com.flowerconnect.farmer.dto.PassbookEntryResponse;
import com.flowerconnect.farmer.dto.PassbookResponse;
import com.flowerconnect.farmer.repository.FarmerDepositRepository;
import com.flowerconnect.farmer.repository.FarmerPaymentRepository;
import com.flowerconnect.farmer.repository.FarmerVendorLinkRepository;
import com.flowerconnect.repository.UserRepository;
import com.flowerconnect.repository.VendorProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Unit coverage for the farmer collection service: collection requests, vendor
 * confirm/reject decisions, passbook balances, suspension gating and ownership.
 */
class FarmerCollectionServiceTest {

    private FarmerVendorLinkRepository linkRepository;
    private FarmerPaymentRepository paymentRepository;
    private FarmerDepositRepository depositRepository;
    private UserRepository userRepository;
    private VendorProfileRepository vendorProfileRepository;
    private FarmerCollectionService service;

    private final Clock clock = Clock.fixed(Instant.parse("2026-01-15T10:00:00Z"), ZoneId.of("UTC"));

    private User farmer;
    private VendorProfile vendor;
    private FarmerVendorLink activeLink;
    private FarmerVendorLink suspendedLink;

    @BeforeEach
    void setUp() {
        linkRepository = mock(FarmerVendorLinkRepository.class);
        paymentRepository = mock(FarmerPaymentRepository.class);
        depositRepository = mock(FarmerDepositRepository.class);
        userRepository = mock(UserRepository.class);
        vendorProfileRepository = mock(VendorProfileRepository.class);
        service = new FarmerCollectionService(linkRepository, paymentRepository, depositRepository,
                userRepository, vendorProfileRepository, clock);

        farmer = User.builder().id(1L).email("farmer@test.com").fullName("Farm Owner").build();
        vendor = VendorProfile.builder().id(10L).businessName("Green Acres").build();

        activeLink = FarmerVendorLink.builder()
                .id(100L).farmerId(1L).vendorId(10L)
                .status(FarmerVendorLink.Status.ACTIVE).build();
        suspendedLink = FarmerVendorLink.builder()
                .id(101L).farmerId(1L).vendorId(10L)
                .status(FarmerVendorLink.Status.SUSPENDED).build();

        // Default stubs: vendor exists, active link owned by farmer 1 / vendor 10.
        when(vendorProfileRepository.existsById(10L)).thenReturn(true);
        when(vendorProfileRepository.findById(10L)).thenReturn(Optional.of(vendor));
        when(userRepository.findById(1L)).thenReturn(Optional.of(farmer));
        when(linkRepository.findByIdAndFarmerIdForUpdate(100L, 1L)).thenReturn(Optional.of(activeLink));
        when(linkRepository.findByIdAndFarmerIdForUpdate(101L, 1L)).thenReturn(Optional.of(suspendedLink));
        when(linkRepository.findByIdAndVendorIdForUpdate(100L, 10L)).thenReturn(Optional.of(activeLink));
        when(linkRepository.findByIdAndVendorIdForUpdate(101L, 10L)).thenReturn(Optional.of(suspendedLink));
        when(linkRepository.findById(anyLong())).thenAnswer(inv -> {
            Long id = inv.getArgument(0);
            return Optional.ofNullable(id.equals(100L) ? activeLink : id.equals(101L) ? suspendedLink : null);
        });
        when(linkRepository.findByVendorId(10L)).thenReturn(List.of(activeLink, suspendedLink));
    }

    // --------------------------------------------------------- request collection

    @Test
    void requestCollectionSavesPendingWhenBalanceSufficient() {
        when(paymentRepository.existsByLinkIdAndStatus(100L, FarmerPayment.Status.PENDING)).thenReturn(false);
        when(depositRepository.sumAmountByLinkId(100L)).thenReturn(new BigDecimal("1000.00"));
        when(paymentRepository.sumAmountByLinkIdAndStatus(100L, FarmerPayment.Status.CONFIRMED))
                .thenReturn(new BigDecimal("200.00"));
        when(paymentRepository.save(any(FarmerPayment.class))).thenAnswer(inv -> {
            FarmerPayment p = inv.getArgument(0);
            p.setId(500L);
            return p;
        });

        FarmerCollectionRequest request = new FarmerCollectionRequest();
        request.setAmount(new BigDecimal("300.00"));
        request.setMode(FarmerCollectionRequest.Mode.UPI);
        request.setNote("Weekly collection");

        FarmerCollectionResponse response = service.requestCollection(1L, 100L, request);

        assertNotNull(response);
        assertEquals(500L, response.getId());
        assertEquals(100L, response.getLinkId());
        assertEquals(new BigDecimal("300.00"), response.getAmount());
        assertEquals(FarmerCollectionResponse.Status.PENDING, response.getStatus());
        assertNull(response.getDecidedAt());
        verify(paymentRepository).save(any(FarmerPayment.class));
    }

    @Test
    void requestCollectionRejectsAmountAboveBalance() {
        when(paymentRepository.existsByLinkIdAndStatus(100L, FarmerPayment.Status.PENDING)).thenReturn(false);
        when(depositRepository.sumAmountByLinkId(100L)).thenReturn(new BigDecimal("100.00"));
        when(paymentRepository.sumAmountByLinkIdAndStatus(100L, FarmerPayment.Status.CONFIRMED))
                .thenReturn(BigDecimal.ZERO);

        FarmerCollectionRequest request = new FarmerCollectionRequest();
        request.setAmount(new BigDecimal("500.00"));
        request.setMode(FarmerCollectionRequest.Mode.CASH);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.requestCollection(1L, 100L, request));
        assertEquals(ErrorCode.CONFLICT, ex.getErrorCode());
        verify(paymentRepository, never()).save(any(FarmerPayment.class));
    }

    @Test
    void requestCollectionRejectsSecondPending() {
        when(paymentRepository.existsByLinkIdAndStatus(100L, FarmerPayment.Status.PENDING)).thenReturn(true);

        FarmerCollectionRequest request = new FarmerCollectionRequest();
        request.setAmount(new BigDecimal("10.00"));
        request.setMode(FarmerCollectionRequest.Mode.CASH);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.requestCollection(1L, 100L, request));
        assertEquals(ErrorCode.CONFLICT, ex.getErrorCode());
    }

    @Test
    void requestCollectionRejectsSuspendedLink() {
        when(paymentRepository.existsByLinkIdAndStatus(101L, FarmerPayment.Status.PENDING)).thenReturn(false);

        FarmerCollectionRequest request = new FarmerCollectionRequest();
        request.setAmount(new BigDecimal("10.00"));
        request.setMode(FarmerCollectionRequest.Mode.CASH);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.requestCollection(1L, 101L, request));
        assertEquals(ErrorCode.CONFLICT, ex.getErrorCode());
    }

    @Test
    void requestCollectionRejectsForeignLink() {
        // link 100 belongs to farmer 1; farmer 2 asks for it.
        when(linkRepository.findByIdAndFarmerIdForUpdate(100L, 2L)).thenReturn(Optional.empty());
        when(linkRepository.findById(100L)).thenReturn(Optional.of(activeLink));

        FarmerCollectionRequest request = new FarmerCollectionRequest();
        request.setAmount(new BigDecimal("10.00"));
        request.setMode(FarmerCollectionRequest.Mode.CASH);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.requestCollection(2L, 100L, request));
        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
    }

    @Test
    void requestCollectionRejectsMissingLink() {
        when(linkRepository.findByIdAndFarmerIdForUpdate(999L, 1L)).thenReturn(Optional.empty());
        when(linkRepository.findById(999L)).thenReturn(Optional.empty());

        FarmerCollectionRequest request = new FarmerCollectionRequest();
        request.setAmount(new BigDecimal("10.00"));
        request.setMode(FarmerCollectionRequest.Mode.CASH);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.requestCollection(1L, 999L, request));
        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
    }

    // ------------------------------------------------------------ confirm / reject

    @Test
    void confirmCollectionSucceedsAndSetsDecidedAt() {
        FarmerPayment pending = FarmerPayment.builder()
                .id(500L).linkId(100L).amount(new BigDecimal("300.00"))
                .mode(FarmerPayment.Mode.UPI).note("x")
                .status(FarmerPayment.Status.PENDING)
                .requestedAt(LocalDateTime.parse("2026-01-15T09:00:00")).build();
        when(paymentRepository.findById(500L)).thenReturn(Optional.of(pending));
        when(paymentRepository.findByIdForUpdate(500L)).thenReturn(Optional.of(pending));
        when(paymentRepository.save(any(FarmerPayment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(depositRepository.sumAmountByLinkId(100L)).thenReturn(new BigDecimal("1000.00"));
        when(paymentRepository.sumAmountByLinkIdAndStatus(100L, FarmerPayment.Status.CONFIRMED))
                .thenReturn(BigDecimal.ZERO);

        FarmerCollectionResponse response = service.confirmCollection(10L, 500L);

        assertEquals(FarmerCollectionResponse.Status.CONFIRMED, response.getStatus());
        assertEquals(LocalDateTime.parse("2026-01-15T10:00:00"), response.getDecidedAt());
        assertEquals(FarmerPayment.Status.CONFIRMED, pending.getStatus());
        assertEquals(LocalDateTime.parse("2026-01-15T10:00:00"), pending.getDecidedAt());
    }

    @Test
    void confirmCollectionRechecksBalanceAndLeavesPendingWhenTooLow() {
        FarmerPayment pending = FarmerPayment.builder()
                .id(500L).linkId(100L).amount(new BigDecimal("900.00"))
                .mode(FarmerPayment.Mode.CASH)
                .status(FarmerPayment.Status.PENDING).build();
        when(paymentRepository.findById(500L)).thenReturn(Optional.of(pending));
        when(paymentRepository.findByIdForUpdate(500L)).thenReturn(Optional.of(pending));
        // Balance has dropped below the requested amount since the request was made.
        when(depositRepository.sumAmountByLinkId(100L)).thenReturn(new BigDecimal("100.00"));
        when(paymentRepository.sumAmountByLinkIdAndStatus(100L, FarmerPayment.Status.CONFIRMED))
                .thenReturn(BigDecimal.ZERO);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.confirmCollection(10L, 500L));
        assertEquals(ErrorCode.CONFLICT, ex.getErrorCode());
        // Request remains PENDING: not confirmed.
        assertEquals(FarmerPayment.Status.PENDING, pending.getStatus());
        assertNull(pending.getDecidedAt());
        verify(paymentRepository, never()).save(any(FarmerPayment.class));
    }

    @Test
    void confirmCollectionRejectsNonPending() {
        FarmerPayment confirmed = FarmerPayment.builder()
                .id(500L).linkId(100L).amount(new BigDecimal("100.00"))
                .status(FarmerPayment.Status.CONFIRMED).build();
        when(paymentRepository.findById(500L)).thenReturn(Optional.of(confirmed));
        when(paymentRepository.findByIdForUpdate(500L)).thenReturn(Optional.of(confirmed));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.confirmCollection(10L, 500L));
        assertEquals(ErrorCode.CONFLICT, ex.getErrorCode());
    }

    @Test
    void rejectCollectionSucceedsAndDoesNotChangeBalance() {
        FarmerPayment pending = FarmerPayment.builder()
                .id(500L).linkId(100L).amount(new BigDecimal("300.00"))
                .mode(FarmerPayment.Mode.CASH)
                .status(FarmerPayment.Status.PENDING).build();
        when(paymentRepository.findById(500L)).thenReturn(Optional.of(pending));
        when(paymentRepository.findByIdForUpdate(500L)).thenReturn(Optional.of(pending));
        when(paymentRepository.save(any(FarmerPayment.class))).thenAnswer(inv -> inv.getArgument(0));

        FarmerCollectionResponse response = service.rejectCollection(10L, 500L);

        assertEquals(FarmerCollectionResponse.Status.REJECTED, response.getStatus());
        assertEquals(LocalDateTime.parse("2026-01-15T10:00:00"), response.getDecidedAt());
        assertEquals(FarmerPayment.Status.REJECTED, pending.getStatus());
    }

    @Test
    void confirmAndRejectWorkWhenLinkIsSuspended() {
        // A pre-existing PENDING request on a suspended link can still be decided.
        when(depositRepository.sumAmountByLinkId(101L)).thenReturn(new BigDecimal("1000.00"));
        when(paymentRepository.sumAmountByLinkIdAndStatus(101L, FarmerPayment.Status.CONFIRMED))
                .thenReturn(BigDecimal.ZERO);
        FarmerPayment pending = FarmerPayment.builder()
                .id(500L).linkId(101L).amount(new BigDecimal("50.00"))
                .mode(FarmerPayment.Mode.CASH)
                .status(FarmerPayment.Status.PENDING).build();
        when(paymentRepository.findById(500L)).thenReturn(Optional.of(pending));
        when(paymentRepository.findByIdForUpdate(500L)).thenReturn(Optional.of(pending));
        when(paymentRepository.save(any(FarmerPayment.class))).thenAnswer(inv -> inv.getArgument(0));

        service.confirmCollection(10L, 500L);
        assertEquals(FarmerPayment.Status.CONFIRMED, pending.getStatus());

        // Reject another pending on the same suspended link.
        FarmerPayment pending2 = FarmerPayment.builder()
                .id(501L).linkId(101L).amount(new BigDecimal("25.00"))
                .mode(FarmerPayment.Mode.CASH)
                .status(FarmerPayment.Status.PENDING).build();
        when(paymentRepository.findById(501L)).thenReturn(Optional.of(pending2));
        when(paymentRepository.findByIdForUpdate(501L)).thenReturn(Optional.of(pending2));
        service.rejectCollection(10L, 501L);
        assertEquals(FarmerPayment.Status.REJECTED, pending2.getStatus());
    }

    @Test
    void confirmRejectRejectForeignVendor() {
        // payment 500 on link 100 (vendor 10); vendor 99 asks to decide.
        FarmerPayment pending = FarmerPayment.builder()
                .id(500L).linkId(100L).amount(new BigDecimal("100.00"))
                .status(FarmerPayment.Status.PENDING).build();
        when(paymentRepository.findById(500L)).thenReturn(Optional.of(pending));
        when(linkRepository.findByIdAndVendorIdForUpdate(100L, 99L)).thenReturn(Optional.empty());
        when(linkRepository.findById(100L)).thenReturn(Optional.of(activeLink));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.confirmCollection(99L, 500L));
        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());

        BusinessException ex2 = assertThrows(BusinessException.class,
                () -> service.rejectCollection(99L, 500L));
        assertEquals(ErrorCode.FORBIDDEN, ex2.getErrorCode());
    }

    // ---------------------------------------------------------------- passbook

    @Test
    void passbookReturnsBalanceAndRunningBalance() {
        // Deposits: 1000 on 10:00, 500 on 11:00. Collections: confirmed 200 on 10:30,
        // pending 100 on 10:45 (does not reduce running balance), rejected 50 on 11:30.
        FarmerDeposit d1 = FarmerDeposit.builder().id(1L).linkId(100L)
                .amount(new BigDecimal("1000.00"))
                .depositedAt(LocalDateTime.parse("2026-01-15T10:00:00")).build();
        FarmerDeposit d2 = FarmerDeposit.builder().id(2L).linkId(100L)
                .amount(new BigDecimal("500.00"))
                .depositedAt(LocalDateTime.parse("2026-01-15T11:00:00")).build();
        FarmerPayment c1 = FarmerPayment.builder().id(10L).linkId(100L)
                .amount(new BigDecimal("200.00")).mode(FarmerPayment.Mode.CASH)
                .status(FarmerPayment.Status.CONFIRMED)
                .requestedAt(LocalDateTime.parse("2026-01-15T10:30:00")).build();
        FarmerPayment c2 = FarmerPayment.builder().id(11L).linkId(100L)
                .amount(new BigDecimal("100.00")).mode(FarmerPayment.Mode.UPI)
                .status(FarmerPayment.Status.PENDING)
                .requestedAt(LocalDateTime.parse("2026-01-15T10:45:00")).build();
        FarmerPayment c3 = FarmerPayment.builder().id(12L).linkId(100L)
                .amount(new BigDecimal("50.00")).mode(FarmerPayment.Mode.CASH)
                .status(FarmerPayment.Status.REJECTED)
                .requestedAt(LocalDateTime.parse("2026-01-15T11:30:00")).build();

        when(depositRepository.findByLinkId(100L)).thenReturn(List.of(d1, d2));
        when(paymentRepository.findByLinkId(100L)).thenReturn(List.of(c1, c2, c3));
        when(depositRepository.sumAmountByLinkId(100L)).thenReturn(new BigDecimal("1500.00"));
        when(paymentRepository.sumAmountByLinkIdAndStatus(100L, FarmerPayment.Status.CONFIRMED))
                .thenReturn(new BigDecimal("200.00"));
        when(linkRepository.findByIdAndFarmerId(100L, 1L)).thenReturn(Optional.of(activeLink));

        PassbookResponse passbook = service.getPassbook(100L, 1L, "FARMER");

        assertEquals(100L, passbook.getLinkId());
        assertEquals("Farm Owner", passbook.getFarmerName());
        assertEquals("Green Acres", passbook.getVendorName());
        assertEquals("ACTIVE", passbook.getLinkStatus());
        // 1500 deposits - 200 confirmed collections = 1300.
        assertEquals(new BigDecimal("1300.00"), passbook.getOutstandingBalance());

        List<PassbookEntryResponse> entries = passbook.getEntries();
        assertEquals(5, entries.size());
        // Order: 10:00 dep 1000, 10:30 conf 200, 10:45 pend 100, 11:00 dep 500, 11:30 rej 50.
        assertEquals(PassbookEntryResponse.Type.DEPOSIT, entries.get(0).getType());
        assertEquals(new BigDecimal("1000.00"), entries.get(0).getRunningBalance());
        assertEquals(PassbookEntryResponse.Type.COLLECTION, entries.get(1).getType());
        assertEquals(new BigDecimal("800.00"), entries.get(1).getRunningBalance());
        assertEquals(PassbookEntryResponse.Type.COLLECTION, entries.get(2).getType());
        assertEquals(PassbookEntryResponse.Status.PENDING, entries.get(2).getStatus());
        assertEquals(new BigDecimal("800.00"), entries.get(2).getRunningBalance()); // pending no change
        assertEquals(PassbookEntryResponse.Type.DEPOSIT, entries.get(3).getType());
        assertEquals(new BigDecimal("1300.00"), entries.get(3).getRunningBalance());
        assertEquals(PassbookEntryResponse.Type.COLLECTION, entries.get(4).getType());
        assertEquals(new BigDecimal("1300.00"), entries.get(4).getRunningBalance()); // rejected no change
    }

    @Test
    void passbookRejectsForeignFarmer() {
        // farmer 2 asks for link 100 owned by farmer 1.
        when(linkRepository.findByIdAndFarmerId(100L, 2L)).thenReturn(Optional.empty());
        when(linkRepository.findById(100L)).thenReturn(Optional.of(activeLink));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.getPassbook(100L, 2L, "FARMER"));
        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void passbookRejectsForeignFlorist() {
        // florist/vendor 99 asks for link 100 owned by vendor 10.
        when(linkRepository.findByIdAndVendorId(100L, 99L)).thenReturn(Optional.empty());
        when(linkRepository.findById(100L)).thenReturn(Optional.of(activeLink));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.getPassbook(100L, 99L, "FLORIST"));
        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void listCollectionsByStatusReturnsVendorPending() {
        when(paymentRepository.findByLinkIdInAndStatus(List.of(100L, 101L),
                FarmerPayment.Status.PENDING)).thenReturn(List.of(
                FarmerPayment.builder().id(500L).linkId(100L).amount(new BigDecimal("100.00"))
                        .mode(FarmerPayment.Mode.CASH)
                        .status(FarmerPayment.Status.PENDING).build()));

        List<FarmerCollectionResponse> responses = service.listCollectionsByStatus(10L,
                FarmerPayment.Status.PENDING);

        assertEquals(1, responses.size());
        assertEquals(500L, responses.get(0).getId());
    }
}
