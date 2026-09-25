package com.flowerconnect.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowerconnect.config.TestClockConfig;
import com.flowerconnect.exception.ResourceConflictException;
import com.flowerconnect.exception.TokenRefreshException;
import com.flowerconnect.security.AuthService;
import com.flowerconnect.security.dto.*;
import com.flowerconnect.security.jwt.RefreshTokenCookieService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.*;

import static org.mockito.ArgumentMatchers.any;
import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

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
}
