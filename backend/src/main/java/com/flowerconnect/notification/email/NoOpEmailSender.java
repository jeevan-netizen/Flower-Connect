package com.flowerconnect.notification.email;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * No-op sender used when no SMTP host is configured (unit tests, local dev
 * without mail). Emails are silently dropped; {@link #isDelivering()} returns
 * false so tests can assert that no real mail was sent.
 */
@Slf4j
@Component
@ConditionalOnMissingBean(EmailSender.class)
public class NoOpEmailSender implements EmailSender {

    @Override
    public void send(String to, String subject, String body) {
        log.debug("No-op email send: to={}, subject={}", to, subject);
    }

    @Override
    public boolean isDelivering() {
        return false;
    }
}