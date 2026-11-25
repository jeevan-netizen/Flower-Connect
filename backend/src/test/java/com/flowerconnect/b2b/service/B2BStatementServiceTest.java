package com.flowerconnect.b2b.service;

import com.flowerconnect.b2b.domain.B2BOrder;
import com.flowerconnect.b2b.domain.B2BOrder.PaymentStatus;
import com.flowerconnect.b2b.domain.B2BOrder.Status;
import com.flowerconnect.b2b.domain.B2BStatement;
import com.flowerconnect.b2b.domain.B2BStatementOrder;
import com.flowerconnect.b2b.dto.B2BStatementPreviewResponse;
import com.flowerconnect.b2b.dto.B2BStatementResponse;
import com.flowerconnect.b2b.dto.CreateB2BStatementRequest;
import com.flowerconnect.b2b.dto.RecordB2BStatementPaymentRequest;
import com.flowerconnect.b2b.repository.B2BOrderRepository;
import com.flowerconnect.b2b.repository.B2BStatementOrderRepository;
import com.flowerconnect.b2b.repository.B2BStatementRepository;
import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.exception.BusinessException;
import com.flowerconnect.exception.ErrorCode;
import com.flowerconnect.repository.VendorProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link B2BStatementService}.
 *
 * <p>Everything decidable without a database is asserted here: statement
 * creation with a server-computed total, rejection of empty/self/invalid-range
 * requests, preview totals, payment recording (statement + linked orders
 * marked {@code PAID}), rejection of double-payment, and statement access
 * ownership.
 *
 * <p>HTTP wiring, RBAC, the pessimistic lock and the unique-order constraint
 * are covered by {@code B2BStatementIntegrationTest} and
 * {@code B2BStatementControllerTest}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class B2BStatementServiceTest {

    private static final String SELLER_EMAIL = "seller@test.com";
    private static final String BUYER_EMAIL = "buyer@test.com";
    private static final String OTHER_EMAIL = "other@test.com";
    private static final Long SELLER_ID = 1L;
    private static final Long BUYER_ID = 2L;
    private static final Long OTHER_ID = 3L;
    private static final Long ORDER_ID = 42L;
    private static final Long STATEMENT_ID = 10L;

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-25T10:00:00Z"), ZoneOffset.UTC);
    private static final LocalDateTime NOW = LocalDateTime.now(CLOCK);

    @Mock
    private B2BOrderRepository orderRepository;
    @Mock
    private B2BStatementRepository statementRepository;
    @Mock
    private B2BStatementOrderRepository statementOrderRepository;
    @Mock
    private VendorProfileRepository vendorProfileRepository;

    private B2BStatementService service;

    private VendorProfile seller;
    private VendorProfile buyer;
    private VendorProfile other;
    private B2BOrder order;
    private B2BStatement statement;
    private B2BStatementOrder link;

    @BeforeEach
    void setUp() {
        service = new B2BStatementService(
                orderRepository, statementRepository, statementOrderRepository,
                vendorProfileRepository, CLOCK);

        seller = VendorProfile.builder().id(SELLER_ID).businessName("Seller Florist")
                .status(VendorProfile.Status.APPROVED).build();
        buyer = VendorProfile.builder().id(BUYER_ID).businessName("Buyer Florist")
                .status(VendorProfile.Status.APPROVED).build();
        other = VendorProfile.builder().id(OTHER_ID).businessName("Other Florist")
                .status(VendorProfile.Status.APPROVED).build();

        order = B2BOrder.builder()
                .id(ORDER_ID)
                .orderNo("B2B-42")
                .buyer(buyer)
                .seller(seller)
                .status(Status.DELIVERED)
                .paymentStatus(PaymentStatus.UNPAID)
                .total(new BigDecimal("199.00"))
                .deliveredAt(NOW.minusSeconds(1))
                .build();

        statement = B2BStatement.builder()
                .id(STATEMENT_ID)
                .statementNo("STM-10")
                .seller(seller)
                .buyer(buyer)
                .periodStart(LocalDateTime.parse("2026-11-10T00:00:00"))
                .periodEnd(LocalDateTime.parse("2026-11-24T23:59:59.999999999"))
                .totalAmount(new BigDecimal("199.00"))
                .status(B2BStatement.Status.OPEN)
                .createdAt(NOW)
                .updatedAt(NOW)
                .build();

        link = B2BStatementOrder.builder()
                .id(100L)
                .statement(statement)
                .order(order)
                .build();

        when(vendorProfileRepository.findByUserEmail(SELLER_EMAIL)).thenReturn(Optional.of(seller));
        when(vendorProfileRepository.findByUserEmail(BUYER_EMAIL)).thenReturn(Optional.of(buyer));
        when(vendorProfileRepository.findByUserEmail(OTHER_EMAIL)).thenReturn(Optional.of(other));
        when(vendorProfileRepository.findById(SELLER_ID)).thenReturn(Optional.of(seller));
        when(vendorProfileRepository.findById(BUYER_ID)).thenReturn(Optional.of(buyer));
        when(vendorProfileRepository.findById(OTHER_ID)).thenReturn(Optional.of(other));

        when(statementRepository.saveAndFlush(any(B2BStatement.class)))
                .thenAnswer(inv -> {
                    B2BStatement s = inv.getArgument(0);
                    s.setId(STATEMENT_ID);
                    return s;
                });
        when(statementOrderRepository.findByStatementId(STATEMENT_ID)).thenReturn(List.of(link));
    }

    // ------------------------------------------------------------------
    // Create statement
    // ------------------------------------------------------------------

    @Test
    void createStatementSucceedsAndTotalsMatchEligibleOrders() {
        when(orderRepository.findDeliveredUnpaidBySellerAndBuyerForUpdate(
                eq(SELLER_ID), eq(BUYER_ID), eq(Status.DELIVERED),
                eq(PaymentStatus.UNPAID), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(order));

        CreateB2BStatementRequest request = new CreateB2BStatementRequest();
        request.setBuyerId(BUYER_ID);
        request.setFrom(LocalDate.parse("2026-11-10"));
        request.setTo(LocalDate.parse("2026-11-24"));

        B2BStatementResponse response = service.createStatement(SELLER_EMAIL, request);

        assertThat(response.getStatus()).isEqualTo(B2BStatement.Status.OPEN);
        assertThat(response.getTotalAmount()).isEqualByComparingTo("199.00");
        assertThat(response.getSellerId()).isEqualTo(SELLER_ID);
        assertThat(response.getBuyerId()).isEqualTo(BUYER_ID);
        assertThat(response.getOrders()).hasSize(1);
        assertThat(response.getOrders().get(0).getTotal()).isEqualByComparingTo("199.00");

        verify(statementRepository).saveAndFlush(any(B2BStatement.class));
        verify(statementOrderRepository).save(any(B2BStatementOrder.class));
    }

    @Test
    void createStatementRejectsEmpty() {
        when(orderRepository.findDeliveredUnpaidBySellerAndBuyerForUpdate(
                eq(SELLER_ID), eq(BUYER_ID), eq(Status.DELIVERED),
                eq(PaymentStatus.UNPAID), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of());

        CreateB2BStatementRequest request = new CreateB2BStatementRequest();
        request.setBuyerId(BUYER_ID);

        assertThatThrownBy(() -> service.createStatement(SELLER_EMAIL, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void createStatementRejectsSelf() {
        CreateB2BStatementRequest request = new CreateB2BStatementRequest();
        request.setBuyerId(SELLER_ID);

        assertThatThrownBy(() -> service.createStatement(SELLER_EMAIL, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    void createStatementRejectsInvalidRange() {
        CreateB2BStatementRequest request = new CreateB2BStatementRequest();
        request.setBuyerId(BUYER_ID);
        request.setFrom(LocalDate.parse("2026-11-24"));
        request.setTo(LocalDate.parse("2026-11-10"));

        assertThatThrownBy(() -> service.createStatement(SELLER_EMAIL, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    // ------------------------------------------------------------------
    // Preview
    // ------------------------------------------------------------------

    @Test
    void previewReturnsEligibleOrdersAndTotal() {
        when(orderRepository.findDeliveredUnpaidBySellerAndBuyer(
                eq(SELLER_ID), eq(BUYER_ID), eq(Status.DELIVERED),
                eq(PaymentStatus.UNPAID), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(order));

        B2BStatementPreviewResponse preview = service.preview(SELLER_EMAIL, BUYER_ID,
                LocalDate.parse("2026-11-10"), LocalDate.parse("2026-11-24"));

        assertThat(preview.getOrders()).hasSize(1);
        assertThat(preview.getOrders().get(0).getOrderNo()).isEqualTo("B2B-42");
        assertThat(preview.getTotalAmount()).isEqualByComparingTo("199.00");
    }

    // ------------------------------------------------------------------
    // Record payment
    // ------------------------------------------------------------------

    @Test
    void recordPaymentSucceedsAndMarksOrdersPaid() {
        when(statementRepository.findById(STATEMENT_ID)).thenReturn(Optional.of(statement));
        when(statementRepository.saveAndFlush(any(B2BStatement.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(orderRepository.saveAndFlush(any(B2BOrder.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        RecordB2BStatementPaymentRequest request = new RecordB2BStatementPaymentRequest();
        request.setPaymentMode(B2BStatement.PaymentMode.UPI);
        request.setPaymentNote("Received via UPI");

        B2BStatementResponse response = service.recordPayment(SELLER_EMAIL, STATEMENT_ID, request);

        assertThat(response.getStatus()).isEqualTo(B2BStatement.Status.PAID);
        assertThat(response.getPaidAt()).isNotNull();
        assertThat(response.getPaymentMode()).isEqualTo(B2BStatement.PaymentMode.UPI);
        assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
    }

    @Test
    void recordPaymentRejectsAlreadyPaid() {
        B2BStatement paid = B2BStatement.builder()
                .id(STATEMENT_ID).seller(seller).buyer(buyer)
                .status(B2BStatement.Status.PAID).build();
        when(statementRepository.findById(STATEMENT_ID)).thenReturn(Optional.of(paid));

        RecordB2BStatementPaymentRequest request = new RecordB2BStatementPaymentRequest();
        request.setPaymentMode(B2BStatement.PaymentMode.CASH);

        assertThatThrownBy(() -> service.recordPayment(SELLER_EMAIL, STATEMENT_ID, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    void recordPaymentRejectsNonSeller() {
        when(statementRepository.findById(STATEMENT_ID)).thenReturn(Optional.of(statement));

        RecordB2BStatementPaymentRequest request = new RecordB2BStatementPaymentRequest();
        request.setPaymentMode(B2BStatement.PaymentMode.CASH);

        assertThatThrownBy(() -> service.recordPayment(BUYER_EMAIL, STATEMENT_ID, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void recordPaymentRejectsMissingStatement() {
        when(statementRepository.findById(STATEMENT_ID)).thenReturn(Optional.empty());

        RecordB2BStatementPaymentRequest request = new RecordB2BStatementPaymentRequest();
        request.setPaymentMode(B2BStatement.PaymentMode.CASH);

        assertThatThrownBy(() -> service.recordPayment(SELLER_EMAIL, STATEMENT_ID, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    // ------------------------------------------------------------------
    // Get statement
    // ------------------------------------------------------------------

    @Test
    void getStatementSucceedsForParty() {
        when(statementRepository.findById(STATEMENT_ID)).thenReturn(Optional.of(statement));

        B2BStatementResponse response = service.getStatement(SELLER_EMAIL, STATEMENT_ID);

        assertThat(response.getId()).isEqualTo(STATEMENT_ID);
        assertThat(response.getSellerId()).isEqualTo(SELLER_ID);
        assertThat(response.getBuyerId()).isEqualTo(BUYER_ID);
        assertThat(response.getOrders()).hasSize(1);
    }

    @Test
    void getStatementRejectsNonParty() {
        when(statementRepository.findById(STATEMENT_ID)).thenReturn(Optional.of(statement));

        assertThatThrownBy(() -> service.getStatement(OTHER_EMAIL, STATEMENT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void getStatementRejectsMissing() {
        when(statementRepository.findById(STATEMENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getStatement(SELLER_EMAIL, STATEMENT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    void recordPaymentDoesNotDoublePayWhenAlreadyPaid() {
        B2BStatement paid = B2BStatement.builder()
                .id(STATEMENT_ID).seller(seller).buyer(buyer)
                .status(B2BStatement.Status.PAID).build();
        when(statementRepository.findById(STATEMENT_ID)).thenReturn(Optional.of(paid));

        RecordB2BStatementPaymentRequest request = new RecordB2BStatementPaymentRequest();
        request.setPaymentMode(B2BStatement.PaymentMode.CASH);

        assertThatThrownBy(() -> service.recordPayment(SELLER_EMAIL, STATEMENT_ID, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CONFLICT);

        verify(orderRepository, never()).saveAndFlush(any(B2BOrder.class));
    }
}
