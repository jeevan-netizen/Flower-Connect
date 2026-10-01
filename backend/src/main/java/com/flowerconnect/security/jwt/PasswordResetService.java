package com.flowerconnect.security.jwt;

import com.flowerconnect.config.AppProperties;
import com.flowerconnect.domain.PasswordResetToken;
import com.flowerconnect.domain.User;
import com.flowerconnect.exception.BusinessException;
import com.flowerconnect.notification.email.EmailSender;
import com.flowerconnect.repository.PasswordResetTokenRepository;
import com.flowerconnect.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;

/**
 * Password-reset lifecycle:
 * <ul>
 *   <li>{@link #requestPasswordReset(String)} — generate a single-use token
 *       (30-min TTL, SHA-256 hashed at rest) and email it. Only ACTIVE users
 *       receive a token; SUSPENDED/DISABLED/nonexistent users get the same
 *       silent 200 response (rules.md 5.6, no user enumeration).</li>
 *   <li>{@link #completePasswordReset(String, String)} — verify the token,
 *       hash the new password, mark the token used, and revoke all of the
 *       user's refresh tokens. Returns a generic 400 for not-found / expired /
 *       already-used tokens.</li>
 * </ul>
 */
@Slf4j
@Service
public class PasswordResetService {

    private static final String RESET_ERROR_MESSAGE = "Invalid or expired reset token";

    private final PasswordResetTokenRepository resetTokenRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final EmailSender emailSender;
    private final AppProperties appProperties;
    private final Clock clock;

    public PasswordResetService(
            PasswordResetTokenRepository resetTokenRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            RefreshTokenService refreshTokenService,
            EmailSender emailSender,
            AppProperties appProperties,
            Clock clock) {
        this.resetTokenRepository = resetTokenRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
        this.emailSender = emailSender;
        this.appProperties = appProperties;
        this.clock = clock;
    }

    /**
     * Request a password reset for the given email. Always returns identical
     * behaviour; the caller (controller) returns 200 either way.
     */
    public void requestPasswordReset(String email) {
        if (email == null || email.isBlank()) {
            return;
        }
        String normalizedEmail = email.trim().toLowerCase();
        userRepository.findByEmail(normalizedEmail).ifPresent(user -> {
            if (user.getStatus() != User.Status.ACTIVE) {
                log.debug("Password reset requested for non-ACTIVE user email={} status={}",
                        normalizedEmail, user.getStatus());
                return;
            }
            issueAndEmailResetToken(user);
        });
    }

    private void issueAndEmailResetToken(User user) {
        String rawToken = generateSecureToken();
        String tokenHash = hashToken(rawToken);
        LocalDateTime expiresAt = LocalDateTime.now(clock)
                .plus(Duration.ofMinutes(appProperties.getResetTokenTtlMinutes()));

        PasswordResetToken resetToken = PasswordResetToken.builder()
                .user(user)
                .tokenHash(tokenHash)
                .expiresAt(expiresAt)
                .build();
        resetTokenRepository.save(resetToken);

        String resetLink = buildResetLink(rawToken);
        emailSender.send(
                user.getEmail(),
                "FlowerConnect password reset",
                "Reset your password by visiting: " + resetLink
                        + "\n\nThis link expires in " + appProperties.getResetTokenTtlMinutes()
                        + " minutes. If you did not request this, you can ignore this email.");
    }

    /**
     * Complete the password reset. Throws a single generic 400-style
     * {@link BusinessException} for any invalid state so callers cannot
     * distinguish not-found / expired / already-used tokens.
     */
    @Transactional
    public void completePasswordReset(String rawToken, String newPassword) {
        if (rawToken == null || rawToken.isBlank()) {
            throw invalidToken();
        }
        String tokenHash = hashToken(rawToken);
        PasswordResetToken resetToken = resetTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> invalidToken());

        LocalDateTime now = LocalDateTime.now(clock);
        if (resetToken.isUsed() || resetToken.isExpired(now)) {
            throw invalidToken();
        }

        User user = resetToken.getUser();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        resetToken.markUsed(now);
        resetTokenRepository.save(resetToken);

        // A successful reset invalidates every existing session.
        refreshTokenService.revokeAllRefreshTokensForUser(user.getId());

        log.info("Password reset completed for user id={}", user.getId());
    }

    private static BusinessException invalidToken() {
        return BusinessException.badRequest(RESET_ERROR_MESSAGE);
    }

    private String buildResetLink(String rawToken) {
        String baseUrl = appProperties.getBaseUrl();
        if (!baseUrl.endsWith("/")) {
            baseUrl += "/";
        }
        return baseUrl + "reset-password?token=" + rawToken;
    }

    static String generateSecureToken() {
        return UUID.randomUUID() + "-" + UUID.randomUUID();
    }

    public static String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public void cleanupExpiredTokens() {
        int deleted = resetTokenRepository.deleteExpiredBefore(LocalDateTime.now(clock));
        if (deleted > 0) {
            log.info("Cleaned up {} expired password-reset tokens", deleted);
        }
    }
}
