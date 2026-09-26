package com.flowerconnect.notification.email;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Real SMTP sender, active only when {@code app.email.enabled=true}.
 * <p>
 * Errors are swallowed and logged: a mail outage must never break the
 * password-reset flow. Callers cannot distinguish delivery failure from success
 * by design (the forgot-password endpoint returns an identical response either
 * way, see rules.md section 5.6).
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "spring.mail.host")
public class SmtpEmailSender implements EmailSender {

    private final JavaMailSender javaMailSender;

    public SmtpEmailSender(JavaMailSender javaMailSender) {
        this.javaMailSender = javaMailSender;
    }

    @Override
    public void send(String to, String subject, String body) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            javaMailSender.send(message);
        } catch (Exception ex) {
            log.error("Failed to send email to {}: {}", to, ex.getMessage(), ex);
        }
    }

    @Override
    public boolean isDelivering() {
        return true;
    }
}