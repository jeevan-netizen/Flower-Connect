package com.flowerconnect.scheduler;

import com.flowerconnect.config.AppProperties;
import com.flowerconnect.security.jwt.PasswordResetService;
import com.flowerconnect.security.jwt.RefreshTokenService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefreshTokenCleanupSchedulerTest {

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private PasswordResetService passwordResetService;

    @InjectMocks
    private RefreshTokenCleanupScheduler scheduler;

    @Test
    void cleanupExpiredTokensCallsBothServices() {
        scheduler.cleanupExpiredTokens();

        verify(refreshTokenService).cleanupExpiredTokens();
        verify(passwordResetService).cleanupExpiredTokens();
    }
}