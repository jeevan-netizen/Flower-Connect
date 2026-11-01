package com.flowerconnect.notification.email;

import org.junit.jupiter.api.Test;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Coverage for {@link SmtpEmailSender}: errors are swallowed and never thrown,
 * so mail failures are fire-and-forget and never break the password-reset flow.
 */
class SmtpEmailSenderTest {

    @Test
    void sendSwallowsExceptionsAndNeverThrows() throws Exception {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        doThrow(new RuntimeException("SMTP connection refused"))
                .when(mailSender).send((SimpleMailMessage) any());

        SmtpEmailSender sender = new SmtpEmailSender(mailSender);

        // Must not throw: mail failures are fire-and-forget
        sender.send("customer@example.com", "Subject", "Body");

        verify(mailSender).send((SimpleMailMessage) any());
    }

    @Test
    void sendWithValidSenderSucceeds() throws Exception {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        SmtpEmailSender sender = new SmtpEmailSender(mailSender);

        sender.send("customer@example.com", "Subject", "Body");

        verify(mailSender).send((SimpleMailMessage) any());
    }
}
