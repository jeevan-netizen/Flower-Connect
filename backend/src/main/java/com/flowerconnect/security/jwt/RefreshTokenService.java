package com.flowerconnect.security.jwt;

import com.flowerconnect.config.JwtProperties;
import com.flowerconnect.domain.RefreshToken;
import com.flowerconnect.domain.User;
import com.flowerconnect.exception.AccountSuspendedException;
import com.flowerconnect.exception.TokenRefreshException;
import com.flowerconnect.repository.RefreshTokenRepository;
import com.flowerconnect.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            UserRepository userRepository,
            JwtService jwtService,
            JwtProperties jwtProperties) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
    }

    public String createRefreshToken(Long userId) {
        return doCreateRefreshToken(userId, UUID.randomUUID().toString()).rawToken;
    }

    private TokenCreationResult doCreateRefreshToken(Long userId, String familyId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new TokenRefreshException("User not found"));
        String rawToken = jwtService.generateRefreshToken();
        String tokenHash = hashToken(rawToken);
        LocalDateTime expiresAt = LocalDateTime.now().plus(Duration.ofMillis(jwtProperties.getRefreshTtlMs()));
        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .tokenHash(tokenHash)
                .expiresAt(expiresAt)
                .familyId(familyId)
                .build();
        RefreshToken saved = refreshTokenRepository.save(refreshToken);
        return new TokenCreationResult(rawToken, saved);
    }

    private static class TokenCreationResult {
        final String rawToken;
        final RefreshToken entity;

        TokenCreationResult(String rawToken, RefreshToken entity) {
            this.rawToken = rawToken;
            this.entity = entity;
        }
    }

    @Transactional
    public String rotateRefreshToken(String rawToken) {
        String tokenHash = hashToken(rawToken);
        RefreshToken storedToken = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new TokenRefreshException("Invalid refresh token"));

        if (storedToken.isRevoked()) {
            throw new TokenRefreshException("Refresh token has been revoked");
        }

        if (storedToken.isExpired()) {
            refreshTokenRepository.delete(storedToken);
            throw new TokenRefreshException("Refresh token has expired");
        }

        User user = storedToken.getUser();
        if (user.getStatus() == User.Status.SUSPENDED) {
            throw new AccountSuspendedException("Account suspended");
        }
        if (user.getStatus() == User.Status.DISABLED) {
            throw new TokenRefreshException("Invalid refresh token");
        }

        TokenCreationResult result = doCreateRefreshToken(user.getId(), storedToken.getFamilyId());

        storedToken.setRevokedAt(LocalDateTime.now());
        storedToken.setReplacedById(result.entity.getId());
        refreshTokenRepository.save(storedToken);

        log.debug("Rotated refresh token for user id={}", user.getId());
        return result.rawToken;
    }

    public User validateAndReturnUser(String rawToken) {
        String tokenHash = hashToken(rawToken);
        RefreshToken storedToken = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new TokenRefreshException("Invalid refresh token"));

        if (storedToken.isRevoked()) {
            throw new TokenRefreshException("Refresh token has been revoked");
        }

        if (storedToken.isExpired()) {
            refreshTokenRepository.delete(storedToken);
            throw new TokenRefreshException("Refresh token has expired");
        }

        User user = storedToken.getUser();
        if (user.getStatus() == User.Status.SUSPENDED) {
            throw new AccountSuspendedException("Account suspended");
        }
        if (user.getStatus() == User.Status.DISABLED) {
            throw new TokenRefreshException("Invalid refresh token");
        }

        return user;
    }

    public void revokeRefreshToken(String rawToken) {
        String tokenHash = hashToken(rawToken);
        Optional<RefreshToken> opt = refreshTokenRepository.findByTokenHash(tokenHash);
        if (opt.isPresent()) {
            RefreshToken token = opt.get();
            token.setRevokedAt(LocalDateTime.now());
            refreshTokenRepository.save(token);
            log.debug("Revoked refresh token for user id={}", token.getUser().getId());
        }
    }

    public void revokeAllRefreshTokensForUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new TokenRefreshException("User not found"));
        refreshTokenRepository.revokeAllActiveTokensForUser(user);
    }

    public String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public void cleanupExpiredTokens() {
        int deleted = refreshTokenRepository.deleteExpiredAndRevokedBefore(LocalDateTime.now());
        if (deleted > 0) {
            log.info("Cleaned up {} expired/revoked refresh tokens", deleted);
        }
    }
}
