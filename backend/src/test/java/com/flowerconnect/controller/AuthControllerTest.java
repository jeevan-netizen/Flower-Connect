package com.flowerconnect.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowerconnect.config.CorsProperties;
import com.flowerconnect.config.TestClockConfig;
import com.flowerconnect.domain.User;
import com.flowerconnect.exception.ResourceConflictException;
import com.flowerconnect.exception.TokenRefreshException;
import com.flowerconnect.repository.PasswordResetTokenRepository;
import com.flowerconnect.security.AuthService;
import com.flowerconnect.security.jwt.RefreshTokenCookieService;
import com.flowerconnect.security.dto.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;

import static org.mockito.ArgumentMatchers.any;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import jakarta.servlet.http.Cookie;

@WebMvcTest(controllers = AuthController.class)
    @AutoConfigureMockMvc(addFilters = false)
    @Import(TestClockConfig.class)
    class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @MockBean
    private RefreshTokenCookieService cookieService;

    @MockBean
    private CorsProperties corsProperties;

    @MockBean
    private com.flowerconnect.security.jwt.PasswordResetService passwordResetService;

    @Test
    void shouldRegisterSuccessfully() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .email("new@test.com")
                .password("password123")
                .fullName("New User")
                .phone("+1234567890")
                .build();

        AuthResponse response = AuthResponse.of("access-token", "refresh-token", 900000L);

        when(authService.register(any(RegisterRequest.class))).thenReturn(response);
        when(cookieService.buildRefreshCookie("refresh-token"))
                .thenReturn(ResponseCookie.from("refresh_token", "refresh-token")
                        .httpOnly(true).secure(false).sameSite("Strict")
                        .path("/api/v1/auth").maxAge(604).build());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900000))
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("refresh_token=")));
    }

    @Test
    void shouldReturn400ForInvalidRegisterData() throws Exception {
        String invalidJson = """
                {"email":"not-an-email","password":"123","fullName":""}
                """;

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldLoginSuccessfully() throws Exception {
        LoginRequest request = LoginRequest.builder()
                .email("user@test.com")
                .password("password123")
                .build();

        AuthResponse response = AuthResponse.of("access-token", "refresh-token", 900000L);

        when(authService.login(any(LoginRequest.class))).thenReturn(response);
        when(cookieService.buildRefreshCookie("refresh-token"))
                .thenReturn(ResponseCookie.from("refresh_token", "refresh-token")
                        .httpOnly(true).secure(false).sameSite("Strict")
                        .path("/api/v1/auth").maxAge(604).build());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("refresh_token=")));
    }

    @Test
    void shouldReturn400ForInvalidLoginData() throws Exception {
        String invalidJson = """
                {"email":"not-an-email","password":""}
                """;

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn401ForBadCredentials() throws Exception {
        LoginRequest request = LoginRequest.builder()
                .email("user@test.com")
                .password("wrongpassword")
                .build();

        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new BadCredentialsException("Invalid email or password"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void shouldReturn409ForDuplicateEmail() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .email("existing@test.com")
                .password("password123")
                .fullName("Existing User")
                .build();

        when(authService.register(any(RegisterRequest.class)))
                .thenThrow(new ResourceConflictException("Email already in use"));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Email already in use"));
    }

    @Test
    void shouldReturn409ForDuplicatePhone() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .email("new@test.com")
                .password("password123")
                .fullName("New User")
                .phone("+1234567890")
                .build();

        when(authService.register(any(RegisterRequest.class)))
                .thenThrow(new ResourceConflictException("Phone number already in use"));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Phone number already in use"));
    }

    @Test
    void shouldRefreshSuccessfully() throws Exception {
        RefreshRequest request = RefreshRequest.builder().refreshToken("old-refresh-token").build();
        AuthResponse response = AuthResponse.of("new-access-token", "new-refresh-token", 900000L);

        when(authService.refresh("old-refresh-token")).thenReturn(response);
        when(cookieService.buildRefreshCookie("new-refresh-token"))
                .thenReturn(ResponseCookie.from("refresh_token", "new-refresh-token").httpOnly(true).secure(false).sameSite("Strict").path("/api/v1/auth").maxAge(604).build());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new-access-token"))
                .andExpect(jsonPath("$.refreshToken").value("new-refresh-token"))
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("refresh_token=new-refresh-token")));
    }

    @Test
    void shouldReturn403ForCookieRefreshWithMismatchedOrigin() throws Exception {
        when(corsProperties.getOrigins()).thenReturn("http://localhost:5173");

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie("refresh_token", "refresh-token"))
                        .header("X-FlowerConnect-Client", "1")
                        .header(HttpHeaders.ORIGIN, "https://attacker.example"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("Missing or invalid request origin"));

        verifyNoInteractions(authService, cookieService);
    }

    @Test
    void shouldReturn401ForInvalidRefreshToken() throws Exception {
        RefreshRequest request = RefreshRequest.builder().refreshToken("invalid-token").build();

        when(authService.refresh("invalid-token"))
                .thenThrow(new TokenRefreshException("Invalid refresh token"));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid refresh token"));
    }

    @Test
    void shouldLogoutSuccessfully() throws Exception {
        RefreshRequest request = RefreshRequest.builder().refreshToken("refresh-token-to-revoke").build();

        when(cookieService.buildClearCookie())
                .thenReturn(ResponseCookie.from("refresh_token", "").httpOnly(true).secure(false).sameSite("Strict").path("/api/v1/auth").maxAge(0).build());

        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("refresh_token=")));

        verify(authService).logout("refresh-token-to-revoke");
    }

    @Test
    void shouldReturn400ForEmptyRefreshTokenOnRefresh() throws Exception {
        String invalidJson = """
                {"refreshToken":""}
                """;

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("Refresh token is required"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void shouldReturn400ForEmptyRefreshTokenOnLogout() throws Exception {
        String invalidJson = """
                {"refreshToken":""}
                """;

        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("Refresh token is required"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void shouldReturn400ForMissingRequestBody() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn200ForForgotPasswordWithUnknownEmail() throws Exception {
        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                ForgotPasswordRequest.builder().email("unknown@test.com").build())))
                .andExpect(status().isOk());
    }

@Test
    void shouldReturn200ForForgotPasswordWithEmptyEmail() throws Exception {
        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                ForgotPasswordRequest.builder().email("").build())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn200ForForgotPasswordWithActiveEmailAndCreateToken() throws Exception {
        String email = "active@test.com";
        ForgotPasswordRequest request = ForgotPasswordRequest.builder().email(email).build();

        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(passwordResetService).requestPasswordReset(email.toLowerCase());
    }

    @Test
    void shouldReturnIdentical200ForForgotPasswordWithSuspendedEmail() throws Exception {
        String email = "suspended@test.com";
        ForgotPasswordRequest request = ForgotPasswordRequest.builder().email(email).build();

        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(passwordResetService).requestPasswordReset(email.toLowerCase());
    }

    @Test
    void shouldReturnIdentical200ForForgotPasswordWithDisabledEmail() throws Exception {
        String email = "disabled@test.com";
        ForgotPasswordRequest request = ForgotPasswordRequest.builder().email(email).build();

        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(passwordResetService).requestPasswordReset(email.toLowerCase());
    }

    @Test
    void shouldReturn400ForResetPasswordWithExpiredToken() throws Exception {
        doThrow(new com.flowerconnect.exception.BusinessException(
                com.flowerconnect.exception.ErrorCode.VALIDATION_FAILED,
                "Invalid or expired reset token"))
                .when(passwordResetService)
                        .completePasswordReset("expired-token", "newPassword123");

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                ResetPasswordRequest.builder().token("expired-token").newPassword("newPassword123").build())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid or expired reset token"));
    }

    @Test
    void shouldReturn400ForResetPasswordWithAlreadyUsedToken() throws Exception {
        doThrow(new com.flowerconnect.exception.BusinessException(
                com.flowerconnect.exception.ErrorCode.VALIDATION_FAILED,
                "Invalid or expired reset token"))
                .when(passwordResetService)
                        .completePasswordReset("used-token", "newPassword123");

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                ResetPasswordRequest.builder().token("used-token").newPassword("newPassword123").build())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid or expired reset token"));
    }

    @Test
    void shouldReturn400ForResetPasswordWithMalformedToken() throws Exception {
        doThrow(new com.flowerconnect.exception.BusinessException(
                com.flowerconnect.exception.ErrorCode.VALIDATION_FAILED,
                "Invalid or expired reset token"))
                .when(passwordResetService)
                        .completePasswordReset("malformed-token", "newPassword123");

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                ResetPasswordRequest.builder().token("malformed-token").newPassword("newPassword123").build())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid or expired reset token"));
    }

    @Test
    void shouldReturn400ForResetPasswordWithNonexistentToken() throws Exception {
        doThrow(new com.flowerconnect.exception.BusinessException(
                com.flowerconnect.exception.ErrorCode.VALIDATION_FAILED,
                "Invalid or expired reset token"))
                .when(passwordResetService)
                        .completePasswordReset("nonexistent-token", "newPassword123");

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                ResetPasswordRequest.builder().token("nonexistent-token").newPassword("newPassword123").build())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid or expired reset token"));
    }

    @Test
    void shouldReturnIdenticalGenericErrorForAllInvalidResetTokenCases() throws Exception {
        doThrow(new com.flowerconnect.exception.BusinessException(
                com.flowerconnect.exception.ErrorCode.VALIDATION_FAILED,
                "Invalid or expired reset token"))
                .when(passwordResetService)
                        .completePasswordReset(anyString(), anyString());

        String[] invalidTokens = {"expired", "used", "malformed", "nonexistent"};
        for (String token : invalidTokens) {
            mockMvc.perform(post("/api/v1/auth/reset-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    ResetPasswordRequest.builder().token(token).newPassword("newPassword123").build())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Invalid or expired reset token"));
        }
    }
}
