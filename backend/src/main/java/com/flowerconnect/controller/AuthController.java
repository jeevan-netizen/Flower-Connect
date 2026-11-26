package com.flowerconnect.controller;

import com.flowerconnect.config.CorsProperties;
import com.flowerconnect.exception.BusinessException;
import com.flowerconnect.security.AuthService;
import com.flowerconnect.security.dto.AuthResponse;
import com.flowerconnect.security.dto.ForgotPasswordRequest;
import com.flowerconnect.security.dto.LoginRequest;
import com.flowerconnect.security.dto.RefreshRequest;
import com.flowerconnect.security.dto.RegisterRequest;
import com.flowerconnect.security.dto.ResetPasswordRequest;
import com.flowerconnect.security.jwt.PasswordResetService;
import com.flowerconnect.security.jwt.RefreshTokenCookieService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;

@Slf4j
@Tag(name = "Authentication", description = "Register, login, token refresh and logout (public, some require cookie-based CSRF headers)")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final String REFRESH_COOKIE_NAME = "refresh_token";
    private static final String CLIENT_HEADER_NAME = "X-FlowerConnect-Client";
    private static final String CLIENT_HEADER_VALUE = "1";

    private final AuthService authService;
    private final RefreshTokenCookieService cookieService;
    private final CorsProperties corsProperties;
    private final PasswordResetService passwordResetService;

    public AuthController(
            AuthService authService,
            RefreshTokenCookieService cookieService,
            CorsProperties corsProperties,
            PasswordResetService passwordResetService) {
        this.authService = authService;
        this.cookieService = cookieService;
        this.corsProperties = corsProperties;
        this.passwordResetService = passwordResetService;
    }

    @PostMapping("/register")
    @Operation(summary = "Register a new customer account",
            description = "Creates a CUSTOMER account. Public endpoint. Returns the access token and "
                    + "sets the refresh token as an HttpOnly cookie. Duplicate email or phone yields 409.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Account created"),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "409", description = "Email or phone already in use")
    })
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthService.AuthTokens tokens = authService.register(request);
        return responseWithRefreshCookie(tokens.response(), tokens.refreshToken());
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate and issue tokens",
            description = "Validates credentials and returns an access token plus an HttpOnly refresh "
                    + "cookie. Public endpoint; rate-limited. Returns 401 for invalid credentials.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Authenticated"),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "401", description = "Invalid email or password"),
            @ApiResponse(responseCode = "429", description = "Rate limited")
    })
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthService.AuthTokens tokens = authService.login(request);
        return responseWithRefreshCookie(tokens.response(), tokens.refreshToken());
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh the access token",
            description = "Authenticated. Issues a new access token using the refresh token (HttpOnly cookie "
                    + "or body). Rotates the refresh token. Requires the X-FlowerConnect-Client header and "
                    + "an exact allowed Origin when the token arrives via cookie.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "New access token issued"),
            @ApiResponse(responseCode = "400", description = "Refresh token is required"),
            @ApiResponse(responseCode = "403", description = "Missing or invalid client header / origin")
    })
    public ResponseEntity<AuthResponse> refresh(
            HttpServletRequest request,
            @RequestBody(required = false) RefreshRequest body) {

        String rawToken = extractToken(request, body);
        if (rawToken == null || rawToken.isBlank()) {
            throw BusinessException.badRequest("Refresh token is required");
        }

        AuthService.AuthTokens tokens = authService.refresh(rawToken);
        ResponseCookie refreshCookie = cookieService.buildRefreshCookie(tokens.refreshToken());

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.SET_COOKIE, refreshCookie.toString());
        return ResponseEntity.ok()
                .headers(headers)
                .body(tokens.response());
    }

    @PostMapping("/logout")
    @Operation(summary = "Log out and revoke the refresh token",
            description = "Authenticated. Revokes the refresh token and clears its HttpOnly cookie. "
                    + "Requires the X-FlowerConnect-Client header and an exact allowed Origin.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Logged out"),
            @ApiResponse(responseCode = "400", description = "Refresh token is required"),
            @ApiResponse(responseCode = "403", description = "Missing or invalid client header / origin")
    })
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

    /**
     * Always returns 200 with an identical body, whether or not the email is
     * registered and whether or not the account is ACTIVE (rules.md 5.6 - no
     * user enumeration). Only ACTIVE accounts actually receive a token and an
     * email; SUSPENDED/DISABLED/nonexistent accounts are silently ignored.
     */
    @PostMapping("/forgot-password")
    @Operation(summary = "Request a password reset",
            description = "Public. Generates a password-reset token and delivers it via email (MailHog "
                    + "in dev). Always returns 200 with an identical body regardless of whether the "
                    + "email is registered, to avoid user enumeration.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reset requested (generic response)")
    })
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordResetService.requestPasswordReset(request.getEmail());
        return ResponseEntity.ok().build();
    }

    /**
     * Single-use token verification. Returns one generic 400 for not-found,
     * expired, or already-used tokens so the token state is not observable.
     * On success the password is re-hashed and all of the user's refresh
     * tokens are revoked.
     */
    @PostMapping("/reset-password")
    @Operation(summary = "Reset the password with a valid token",
            description = "Public. Validates the single-use reset token, re-hashes the new password and "
                    + "revokes all of the user's refresh tokens. Returns one generic 400 for not-found, "
                    + "expired or already-used tokens so the token state is not observable.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Password reset"),
            @ApiResponse(responseCode = "400", description = "Invalid, expired or already-used token")
    })
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.completePasswordReset(request.getToken(), request.getNewPassword());
        return ResponseEntity.ok().build();
    }

    private ResponseEntity<AuthResponse> responseWithRefreshCookie(AuthResponse response, String refreshToken) {
        ResponseCookie refreshCookie = cookieService.buildRefreshCookie(refreshToken);
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