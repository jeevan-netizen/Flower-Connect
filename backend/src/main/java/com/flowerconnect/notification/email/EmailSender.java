package com.flowerconnect.notification.email;

/**
 * Outbound email port. Implementations are selected by profile:
 * <ul>
 *   <li>{@link SmtpEmailSender} when a real SMTP host is configured.</li>
 *   <li>{@link NoOpEmailSender} otherwise (unit tests, local dev without mail).</li>
 * </ul>
 */
public interface EmailSender {

    /**
     * Send an email. Implementations must swallow checked exceptions and log
     * rather than propagate, so a mail outage never breaks the auth flow.
     */
    void send(String to, String subject, String body);

    /** True when the sender will actually deliver mail (used for test assertions). */
    boolean isDelivering();
}