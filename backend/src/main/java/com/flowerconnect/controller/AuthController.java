package com.flowerconnect.controller;

import com.flowerconnect.config.CorsProperties;
import com.flowerconnect.exception.BusinessException;
import com.flowerconnect.security.AuthService;
import com.flowerconnect.security.dto.AuthResponse;
import com.flowerconnect.security.dto.LoginRequest;
import com.flowerconnect.security.dto.RefreshRequest;
import com.flowerconnect.security.dto.RegisterRequest;
import com.flowerconnect.security.jwt.RefreshTokenCookieService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.Cookie;
import java.util.Arrays;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final String REFRESH_COOKIE_NAME = "refresh_token";
    private static final String CLIENT_HEADER_NAME = "X-FlowerConnect-Client";
    private static final String CLIENT_HEADER_VALUE = "1";

    private final AuthService authService;
    private final RefreshTokenCookieService cookieService;
    private final CorsProperties corsProperties;

    public AuthController(
            AuthService authService,
            RefreshTokenCookieService cookieService,
            CorsProperties corsProperties) {
        this.authService = authService;
        this.cookieService = cookieService;
        this.corsProperties = corsProperties;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return responseWithRefreshCookie(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return responseWithRefreshCookie(response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            HttpServletRequest request,
            @RequestBody(required = false) RefreshRequest body) {

        String rawToken = extractToken(request, body);
        if (rawToken == null || rawToken.isBlank()) {
            throw BusinessException.badRequest("Refresh token is required");
        }

        AuthResponse response = authService.refresh(rawToken);
        ResponseCookie refreshCookie = cookieService.buildRefreshCookie(response.getRefreshToken());

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.SET_COOKIE, refreshCookie.toString());
        return ResponseEntity.ok()
                .headers(headers)
                .body(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            HttpServletRequest request,
            @RequestBody(required = false) RefreshRequest body) {

        String rawToken = extractToken(request, body);
        if (rawToken == null || rawToken.isBlank()) {
            throw BusinessException.badRequest("Refresh token is required");
        }

        authService.logout(rawToken);
        ResponseCookie clearCookie = cookieService.buildClearCookie();

        HttpHeaders clearHeaders = new HttpHeaders();
        clearHeaders.add(HttpHeaders.SET_COOKIE, clearCookie.toString());
        return ResponseEntity.noContent()
                .headers(clearHeaders)
                .build();
    }

    private ResponseEntity<AuthResponse> responseWithRefreshCookie(AuthResponse response) {
        ResponseCookie refreshCookie = cookieService.buildRefreshCookie(response.getRefreshToken());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(response);
    }

    private String extractToken(HttpServletRequest request, RefreshRequest body) {
        String cookieToken = extractCookie(request, REFRESH_COOKIE_NAME);
        if (cookieToken != null && !cookieToken.isBlank()) {
            validateCookieAuthentication(request);
            return cookieToken;
        }
        if (body != null) {
            return body.getRefreshToken();
        }
        return null;
    }

    private void validateCookieAuthentication(HttpServletRequest request) {
        if (!CLIENT_HEADER_VALUE.equals(request.getHeader(CLIENT_HEADER_NAME))) {
            throw BusinessException.forbidden("Missing or invalid client header");
        }

        String origin = request.getHeader(HttpHeaders.ORIGIN);
        boolean allowedOrigin = Arrays.stream(corsProperties.getOrigins().split(","))
                .map(String::trim)
                .filter(allowed -> !allowed.isEmpty())
                .anyMatch(allowed -> allowed.equals(origin));
        if (!allowedOrigin) {
            throw BusinessException.forbidden("Missing or invalid request origin");
        }
    }

    private String extractCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            return Arrays.stream(cookies)
                    .filter(c -> c.getName().equals(name))
                    .findFirst()
                    .map(Cookie::getValue)
                    .orElse(null);
        }
        return null;
    }
}
