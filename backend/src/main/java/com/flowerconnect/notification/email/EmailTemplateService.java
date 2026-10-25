package com.flowerconnect.notification.email;

import com.flowerconnect.order.domain.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Renders plain-text email templates for notification events.
 *
 * <p>No template engine dependency — plain Java strings keep the
 * dependency footprint minimal. Templates are deliberately plain text:
 * the {@link SmtpEmailSender} sends {@link org.springframework.mail.SimpleMailMessage}
 * which does not support HTML. If HTML mail is needed later, this
 * service can be swapped for a Thymeleaf-backed implementation without
 * changing the listener layer.
 */
@Component
public class EmailTemplateService {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT);

    /**
     * Renders the order-placed email for the customer.
     */
    public EmailContent orderPlacedCustomer(Long orderId, BigDecimal total,
            LocalDateTime slotStart) {
        String subject = "Your FlowerConnect order #" + orderId + " is confirmed";
        String body = """
                Hello,

                Thank you for your order!

                Order ID: %d
                Total: %s
                %s
                We'll notify you when the florist confirms and when it's out for delivery.

                Thank you for choosing FlowerConnect.
                """.formatted(orderId, formatMoney(total),
                slotStart != null ? "Delivery slot: " + DATE_TIME.format(slotStart) : "");

        return new EmailContent(subject, body.strip());
    }

    /**
     * Renders the new-order email for the vendor.
     */
    public EmailContent orderPlacedVendor(Long orderId, BigDecimal total,
            LocalDateTime slotStart, String customerName) {
        String subject = "New order #" + orderId + " received";
        String body = """
                Hello,

                You have a new order!

                Order ID: %d
                Customer: %s
                Total: %s
                %s
                Please review and accept or reject this order in your vendor dashboard.

                — FlowerConnect
                """.formatted(orderId, customerName != null ? customerName : "Customer",
                formatMoney(total),
                slotStart != null ? "Delivery slot: " + DATE_TIME.format(slotStart) : "");

        return new EmailContent(subject, body.strip());
    }

/**
     * Renders an order status change email for the customer.
     */
    public EmailContent orderStatusChangedCustomer(Long orderId, String fromStatus,
            String toStatus, String message) {
        String subject = "Update on your FlowerConnect order #" + orderId;
        String body = """
                Hello,

                Your order #%d status has changed:
                %s -> %s

                %s

                You can track your order in the FlowerConnect app.

                -- FlowerConnect
                """.formatted(orderId, fromStatus, toStatus, message);

        return new EmailContent(subject, body.strip());
    }

    /**
     * Renders a vendor status change email (approved/rejected/suspended/reinstated).
     */
    public EmailContent vendorStatusChange(Long profileId, String businessName,
            String status, String message) {
        String subject = "Your FlowerConnect vendor account: " + status;
        String body = """
                Hello %s,

                Your vendor account (ID: %d) has been %s.

                %s

                — FlowerConnect
                """.formatted(businessName, profileId, status.toLowerCase(), message);

        return new EmailContent(subject, body.strip());
    }

    /**
     * Renders a refund notification email for the customer.
     */
    public EmailContent refundCustomer(Long orderId, String status, BigDecimal amount) {
        String subject = "Refund " + status.toLowerCase() + " for order #" + orderId;
        String body = """
                Hello,

                The refund for order #%d has been %s.
                Amount: %s

                If you have any questions, please contact support.

                — FlowerConnect
                """.formatted(orderId, status.toLowerCase(), formatMoney(amount));

        return new EmailContent(subject, body.strip());
    }

    /**
     * Renders a dispute notification email for the vendor.
     */
    public EmailContent disputeOpenedVendor(Long disputeId, Long orderId, String reason) {
        String subject = "Dispute opened on order #" + orderId;
        String body = """
                Hello,

                A customer has raised a dispute on order #%d.

                Dispute ID: %d
                Reason: %s

                Please review and respond in your vendor dashboard.

                — FlowerConnect
                """.formatted(orderId, disputeId, reason);

        return new EmailContent(subject, body.strip());
    }

    /**
     * Renders a dispute resolution email for the customer.
     */
    public EmailContent disputeResolvedCustomer(Long disputeId, Long orderId, String status) {
        String subject = "Dispute resolved for order #" + orderId;
        String body = """
                Hello,

                The dispute on order #%d has been resolved.

                Dispute ID: %d
                Outcome: %s

                If you have any questions, please contact support.

                — FlowerConnect
                """.formatted(orderId, disputeId, status);

        return new EmailContent(subject, body.strip());
    }

    /**
     * Formats a {@link BigDecimal} as INR with two decimal places and comma grouping.
     */
    private String formatMoney(BigDecimal amount) {
        if (amount == null) {
            return "--";
        }
        java.text.DecimalFormat df = new java.text.DecimalFormat("#,##0.00");
        return "INR " + df.format(amount.setScale(2, java.math.RoundingMode.HALF_UP));
    }

    /**
     * Simple container for email subject and body.
     */
    public record EmailContent(String subject, String body) {}
}