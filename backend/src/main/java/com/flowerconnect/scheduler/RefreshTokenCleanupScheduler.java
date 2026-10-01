package com.flowerconnect.scheduler;

import com.flowerconnect.config.AppProperties;
import com.flowerconnect.security.jwt.PasswordResetService;
import com.flowerconnect.security.jwt.RefreshTokenService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class RefreshTokenCleanupScheduler {

    private final RefreshTokenService refreshTokenService;
    private final PasswordResetService passwordResetService;
    private final AppProperties appProperties;

    public RefreshTokenCleanupScheduler(
            RefreshTokenService refreshTokenService,
            PasswordResetService passwordResetService,
            AppProperties appProperties) {
        this.refreshTokenService = refreshTokenService;
        this.passwordResetService = passwordResetService;
        this.appProperties = appProperties;
    }

    @Scheduled(cron = "#{@appProperties.tokenCleanupCron}")
    public void cleanupExpiredTokens() {
        log.info("Starting scheduled cleanup of expired and revoked tokens");
        refreshTokenService.cleanupExpiredTokens();
        passwordResetService.cleanupExpiredTokens();
    }
}
