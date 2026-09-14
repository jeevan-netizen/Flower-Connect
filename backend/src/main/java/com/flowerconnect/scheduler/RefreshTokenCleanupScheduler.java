package com.flowerconnect.scheduler;

import com.flowerconnect.security.jwt.RefreshTokenService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class RefreshTokenCleanupScheduler {

    private final RefreshTokenService refreshTokenService;

    public RefreshTokenCleanupScheduler(RefreshTokenService refreshTokenService) {
        this.refreshTokenService = refreshTokenService;
    }

    @Scheduled(cron = "0 0 2 * * ?")
    public void cleanupExpiredTokens() {
        log.info("Starting scheduled cleanup of expired and revoked refresh tokens");
        refreshTokenService.cleanupExpiredTokens();
    }
}
