package com.flowerconnect.notification.email;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Unit tests for {@link EmailTemplateService}.
 */
class EmailTemplateServiceTest {

    private final EmailTemplateService templateService = new EmailTemplateService();

    @Test
    void orderPlacedCustomerRendersSubjectAndBody() {
        var content = templateService.orderPlacedCustomer(123L,
                new BigDecimal("1234.56"),
                LocalDateTime.of(2026, 10, 25, 14, 30));

        assertThat(content.subject()).isEqualTo("Your FlowerConnect order #123 is confirmed");
        assertThat(content.body()).contains("Order ID: 123");
        assertThat(content.body()).contains("INR 1,234.56");
        assertThat(content.body()).contains("Delivery slot: 2026-10-25 14:30");
        assertThat(content.body()).contains("FlowerConnect");
    }

    @Test
    void orderPlacedCustomerWithoutSlotRendersWithoutSlot() {
        var content = templateService.orderPlacedCustomer(123L,
                new BigDecimal("1234.56"), null);

        assertThat(content.body()).doesNotContain("Delivery slot");
    }

    @Test
    void orderPlacedVendorRendersSubjectAndBody() {
        var content = templateService.orderPlacedVendor(123L,
                new BigDecimal("1234.56"),
                LocalDateTime.of(2026, 10, 25, 14, 30), "John Doe");

        assertThat(content.subject()).isEqualTo("New order #123 received");
        assertThat(content.body()).contains("Order ID: 123");
        assertThat(content.body()).contains("Customer: John Doe");
        assertThat(content.body()).contains("INR 1,234.56");
        assertThat(content.body()).contains("Delivery slot: 2026-10-25 14:30");
    }

    @Test
    void orderStatusChangedCustomerRendersSubjectAndBody() {
        var content = templateService.orderStatusChangedCustomer(123L,
                "PLACED", "CONFIRMED", "Your order has been confirmed.");

        assertThat(content.subject()).isEqualTo("Update on your FlowerConnect order #123");
        assertThat(content.body()).contains("PLACED -> CONFIRMED");
        assertThat(content.body()).contains("Your order has been confirmed");
    }

    @Test
    void vendorStatusChangeRendersSubjectAndBody() {
        var content = templateService.vendorStatusChange(456L, "Bloom & Co",
                "approved", "Your vendor account has been approved.");

        assertThat(content.subject()).isEqualTo("Your FlowerConnect vendor account: approved");
        assertThat(content.body()).contains("Bloom & Co");
        assertThat(content.body()).contains("approved");
    }

    @Test
    void refundCustomerRendersSubjectAndBody() {
        var content = templateService.refundCustomer(789L, "COMPLETED",
                new BigDecimal("500.00"));

        assertThat(content.subject()).isEqualTo("Refund completed for order #789");
        assertThat(content.body()).contains("The refund for order #789 has been completed");
        assertThat(content.body()).contains("INR 500.00");
    }

    @Test
    void disputeOpenedVendorRendersSubjectAndBody() {
        var content = templateService.disputeOpenedVendor(10L, 123L, "Flowers wilted");

        assertThat(content.subject()).isEqualTo("Dispute opened on order #123");
        assertThat(content.body()).contains("Dispute ID: 10");
        assertThat(content.body()).contains("Reason: Flowers wilted");
    }

    @Test
    void disputeResolvedCustomerRendersSubjectAndBody() {
        var content = templateService.disputeResolvedCustomer(10L, 123L, "RESOLVED_REFUND");

        assertThat(content.subject()).isEqualTo("Dispute resolved for order #123");
        assertThat(content.body()).contains("Dispute ID: 10");
        assertThat(content.body()).contains("RESOLVED_REFUND");
    }

    @Test
    void formatMoneyHandlesNull() {
        // Private method test via public method
        var content = templateService.orderPlacedCustomer(1L, null, null);
        assertThat(content.body()).contains("--");
    }
}