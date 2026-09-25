package com.flowerconnect.security.jwt;

import com.flowerconnect.config.AppProperties;
import com.flowerconnect.config.JwtProperties;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

@Service
public class RefreshTokenCookieService {

    private static final String COOKIE_NAME = "refresh_token";
    private static final String COOKIE_PATH = "/api/v1/auth";

    private final AppProperties appProperties;
    private final JwtProperties jwtProperties;

    public RefreshTokenCookieService(AppProperties appProperties, JwtProperties jwtProperties) {
        this.appProperties = appProperties;
        this.jwtProperties = jwtProperties;
    }

    public ResponseCookie buildRefreshCookie(String refreshToken) {
        long maxAgeSeconds = jwtProperties.getRefreshTtlMs() / 1000;
        return ResponseCookie.from(COOKIE_NAME, refreshToken)
                .httpOnly(true)
                .secure(appProperties.isRefreshCookieSecure())
                .sameSite("Strict")
                .path(COOKIE_PATH)
                .maxAge(maxAgeSeconds)
                .build();
    }

    public ResponseCookie buildClearCookie() {
        return ResponseCookie.from(COOKIE_NAME, "")
                .httpOnly(true)
                .secure(appProperties.isRefreshCookieSecure())
                .sameSite("Strict")
                .path(COOKIE_PATH)
                .maxAge(0)
                .build();
    }
}
