package com.flowerconnect.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowerconnect.config.TestClockConfig;
import com.flowerconnect.domain.Role;
import com.flowerconnect.domain.User;
import com.flowerconnect.mapper.UserMapper;
import com.flowerconnect.repository.UserRepository;
import com.flowerconnect.security.dto.ChangePasswordRequest;
import com.flowerconnect.security.dto.ProfileUpdateRequest;
import com.flowerconnect.security.dto.UserResponse;
import com.flowerconnect.security.jwt.RefreshTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.*;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

    @WebMvcTest(controllers = UserController.class)
    @Import({UserControllerTest.TestSecurityConfig.class, TestClockConfig.class})
    class UserControllerTest {

    @TestConfiguration
    static class TestSecurityConfig {
        @Bean
        SecurityFilterChain testFilterChain(HttpSecurity http) throws Exception {
            http
                    .csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers("/api/v1/users/me").authenticated()
                            .requestMatchers("/api/v1/users/me/password").authenticated()
                            .anyRequest().permitAll())
                    .httpBasic(Customizer.withDefaults());
            return http.build();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private UserMapper userMapper;

    @MockBean
    private PasswordEncoder passwordEncoder;

    @MockBean
    private RefreshTokenService refreshTokenService;

    @Test
    @WithMockUser(username = "user@test.com", roles = {"CUSTOMER"})
    void shouldReturnCurrentUserWithValidToken() throws Exception {
        Role role = Role.builder().id(1L).name("CUSTOMER").build();
        User user = User.builder()
                .id(10L)
                .email("user@test.com")
                .passwordHash("$2a$10$hash")
                .fullName("Test User")
                .phone("+1234567890")
                .role(role)
                .status(com.flowerconnect.domain.User.Status.ACTIVE)
                .createdAt(LocalDateTime.of(2024, 1, 15, 10, 30))
                .build();

        UserResponse response = UserResponse.builder()
                .id(10L)
                .email("user@test.com")
                .fullName("Test User")
                .phone("+1234567890")
                .role("CUSTOMER")
                .createdAt(LocalDateTime.of(2024, 1, 15, 10, 30))
                .build();

        when(userRepository.findByEmailWithRole("user@test.com")).thenReturn(Optional.of(user));
        when(userMapper.toResponse(user)).thenReturn(response);

        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("user@test.com"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"));
    }

    @Test
    void shouldReturn401WithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "user@test.com", roles = {"CUSTOMER"})
    void shouldReturn500WhenUserNotFound() throws Exception {
        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().is5xxServerError())
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.stackTrace").doesNotExist());
    }

    @Test
    @WithMockUser(username = "user@test.com", roles = {"CUSTOMER"})
    void shouldUpdateProfileWithValidFullNameAndPhone() throws Exception {
        Role role = Role.builder().id(1L).name("CUSTOMER").build();
        User user = User.builder()
                .id(10L)
                .email("user@test.com")
                .passwordHash("$2a$10$hash")
                .fullName("Test User")
                .phone("+1234567890")
                .role(role)
                .status(com.flowerconnect.domain.User.Status.ACTIVE)
                .createdAt(LocalDateTime.of(2024, 1, 15, 10, 30))
                .build();

        UserResponse updatedResponse = UserResponse.builder()
                .id(10L)
                .email("user@test.com")
                .fullName("Updated Name")
                .phone("+19876543210")
                .role("CUSTOMER")
                .createdAt(LocalDateTime.of(2024, 1, 15, 10, 30))
                .build();

        when(userRepository.findByEmailWithRole("user@test.com")).thenReturn(Optional.of(user));
        when(userRepository.existsByPhone("+19876543210")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(userMapper.toResponse(user)).thenReturn(updatedResponse);

        ProfileUpdateRequest request = ProfileUpdateRequest.builder()
                .fullName("Updated Name")
                .phone("+19876543210")
                .build();

        mockMvc.perform(patch("/api/v1/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Updated Name"))
                .andExpect(jsonPath("$.phone").value("+19876543210"))
                .andExpect(jsonPath("$.email").value("user@test.com"));
    }

    @Test
    @WithMockUser(username = "user@test.com", roles = {"CUSTOMER"})
    void shouldUpdateProfileWithOnlyFullName() throws Exception {
        Role role = Role.builder().id(1L).name("CUSTOMER").build();
        User user = User.builder()
                .id(10L)
                .email("user@test.com")
                .passwordHash("$2a$10$hash")
                .fullName("Test User")
                .phone("+1234567890")
                .role(role)
                .status(com.flowerconnect.domain.User.Status.ACTIVE)
                .createdAt(LocalDateTime.of(2024, 1, 15, 10, 30))
                .build();

        UserResponse updatedResponse = UserResponse.builder()
                .id(10L)
                .email("user@test.com")
                .fullName("Only Name Changed")
                .phone("+1234567890")
                .role("CUSTOMER")
                .createdAt(LocalDateTime.of(2024, 1, 15, 10, 30))
                .build();

        when(userRepository.findByEmailWithRole("user@test.com")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(userMapper.toResponse(user)).thenReturn(updatedResponse);

        ProfileUpdateRequest request = ProfileUpdateRequest.builder()
                .fullName("Only Name Changed")
                .build();

        mockMvc.perform(patch("/api/v1/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Only Name Changed"))
                .andExpect(jsonPath("$.phone").value("+1234567890"));
    }

    @Test
    @WithMockUser(username = "user@test.com", roles = {"CUSTOMER"})
    void shouldUpdateProfileWithOnlyPhone() throws Exception {
        Role role = Role.builder().id(1L).name("CUSTOMER").build();
        User user = User.builder()
                .id(10L)
                .email("user@test.com")
                .passwordHash("$2a$10$hash")
                .fullName("Test User")
                .phone("+1234567890")
                .role(role)
                .status(com.flowerconnect.domain.User.Status.ACTIVE)
                .createdAt(LocalDateTime.of(2024, 1, 15, 10, 30))
                .build();

        UserResponse updatedResponse = UserResponse.builder()
                .id(10L)
                .email("user@test.com")
                .fullName("Test User")
                .phone("+19876543210")
                .role("CUSTOMER")
                .createdAt(LocalDateTime.of(2024, 1, 15, 10, 30))
                .build();

        when(userRepository.findByEmailWithRole("user@test.com")).thenReturn(Optional.of(user));
        when(userRepository.existsByPhone("+19876543210")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(userMapper.toResponse(user)).thenReturn(updatedResponse);

        ProfileUpdateRequest request = ProfileUpdateRequest.builder()
                .phone("+19876543210")
                .build();

        mockMvc.perform(patch("/api/v1/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Test User"))
                .andExpect(jsonPath("$.phone").value("+19876543210"));
    }

    @Test
    @WithMockUser(username = "user@test.com", roles = {"CUSTOMER"})
    void shouldRejectInvalidPhoneFormat() throws Exception {
        Role role = Role.builder().id(1L).name("CUSTOMER").build();
        User user = User.builder()
                .id(10L)
                .email("user@test.com")
                .passwordHash("$2a$10$hash")
                .fullName("Test User")
                .phone("+1234567890")
                .role(role)
                .status(com.flowerconnect.domain.User.Status.ACTIVE)
                .createdAt(LocalDateTime.of(2024, 1, 15, 10, 30))
                .build();

        when(userRepository.findByEmailWithRole("user@test.com")).thenReturn(Optional.of(user));

        ProfileUpdateRequest request = ProfileUpdateRequest.builder()
                .phone("invalid-phone")
                .build();

        mockMvc.perform(patch("/api/v1/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "user@test.com", roles = {"CUSTOMER"})
    void shouldRejectDuplicatePhone() throws Exception {
        Role role = Role.builder().id(1L).name("CUSTOMER").build();
        User user = User.builder()
                .id(10L)
                .email("user@test.com")
                .passwordHash("$2a$10$hash")
                .fullName("Test User")
                .phone("+1234567890")
                .role(role)
                .status(com.flowerconnect.domain.User.Status.ACTIVE)
                .createdAt(LocalDateTime.of(2024, 1, 15, 10, 30))
                .build();

        when(userRepository.findByEmailWithRole("user@test.com")).thenReturn(Optional.of(user));
        when(userRepository.existsByPhone("+19999999999")).thenReturn(true);

        ProfileUpdateRequest request = ProfileUpdateRequest.builder()
                .phone("+19999999999")
                .build();

        mockMvc.perform(patch("/api/v1/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Phone number already in use"));
    }

    @Test
    @WithMockUser(username = "user@test.com", roles = {"CUSTOMER"})
    void shouldIgnoreEmailFieldInProfileUpdate() throws Exception {
        Role role = Role.builder().id(1L).name("CUSTOMER").build();
        User user = User.builder()
                .id(10L)
                .email("user@test.com")
                .passwordHash("$2a$10$hash")
                .fullName("Test User")
                .phone("+1234567890")
                .role(role)
                .status(com.flowerconnect.domain.User.Status.ACTIVE)
                .createdAt(LocalDateTime.of(2024, 1, 15, 10, 30))
                .build();

        UserResponse response = UserResponse.builder()
                .id(10L)
                .email("user@test.com")
                .fullName("Test User")
                .phone("+1234567890")
                .role("CUSTOMER")
                .createdAt(LocalDateTime.of(2024, 1, 15, 10, 30))
                .build();

        when(userRepository.findByEmailWithRole("user@test.com")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(userMapper.toResponse(user)).thenReturn(response);

        // Send email field in request (should be ignored)
        String requestJson = """
                {"fullName":"Updated","phone":"+19876543210","email":"attacker@test.com"}
                """;

        mockMvc.perform(patch("/api/v1/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("user@test.com"));
    }

    @Test
    @WithMockUser(username = "user@test.com", roles = {"CUSTOMER"})
    void shouldIgnoreUnknownFieldsInProfileUpdate() throws Exception {
        Role role = Role.builder().id(1L).name("CUSTOMER").build();
        User user = User.builder()
                .id(10L)
                .email("user@test.com")
                .passwordHash("$2a$10$hash")
                .fullName("Test User")
                .phone("+1234567890")
                .role(role)
                .status(com.flowerconnect.domain.User.Status.ACTIVE)
                .createdAt(LocalDateTime.of(2024, 1, 15, 10, 30))
                .build();

        UserResponse response = UserResponse.builder()
                .id(10L)
                .email("user@test.com")
                .fullName("Test User")
                .phone("+1234567890")
                .role("CUSTOMER")
                .createdAt(LocalDateTime.of(2024, 1, 15, 10, 30))
                .build();

        when(userRepository.findByEmailWithRole("user@test.com")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(userMapper.toResponse(user)).thenReturn(response);

        // Send unknown field (should be ignored due to @JsonIgnoreProperties(ignoreUnknown = true))
        String requestJson = """
                {"fullName":"Updated","unknownField":"ignored"}
                """;

        mockMvc.perform(patch("/api/v1/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Test User"));
    }

    @Test
    @WithMockUser(username = "user@test.com", roles = {"CUSTOMER"})
    void shouldRejectChangePasswordWithWrongCurrentPassword() throws Exception {
        Role role = Role.builder().id(1L).name("CUSTOMER").build();
        User user = User.builder()
                .id(10L)
                .email("user@test.com")
                .passwordHash("$2a$10$hash")
                .fullName("Test User")
                .phone("+1234567890")
                .role(role)
                .status(com.flowerconnect.domain.User.Status.ACTIVE)
                .createdAt(LocalDateTime.of(2024, 1, 15, 10, 30))
                .build();

        when(userRepository.findByEmailWithRole("user@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrongpassword", "$2a$10$hash")).thenReturn(false);

        ChangePasswordRequest request = ChangePasswordRequest.builder()
                .currentPassword("wrongpassword")
                .newPassword("newPassword123")
                .build();

        mockMvc.perform(post("/api/v1/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Current password is incorrect"));
    }

    @Test
    @WithMockUser(username = "user@test.com", roles = {"CUSTOMER"})
    void shouldChangePasswordSuccessfullyWithCorrectCurrentPassword() throws Exception {
        Role role = Role.builder().id(1L).name("CUSTOMER").build();
        User user = User.builder()
                .id(10L)
                .email("user@test.com")
                .passwordHash("$2a$10$hash")
                .fullName("Test User")
                .phone("+1234567890")
                .role(role)
                .status(com.flowerconnect.domain.User.Status.ACTIVE)
                .createdAt(LocalDateTime.of(2024, 1, 15, 10, 30))
                .build();

        when(userRepository.findByEmailWithRole("user@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "$2a$10$hash")).thenReturn(true);
        when(passwordEncoder.encode("newPassword123")).thenReturn("$2a$10$newhash");
        when(userRepository.save(any(User.class))).thenReturn(user);
        doNothing().when(refreshTokenService).revokeAllRefreshTokensForUser(10L);

        ChangePasswordRequest request = ChangePasswordRequest.builder()
                .currentPassword("password123")
                .newPassword("newPassword123")
                .build();

        mockMvc.perform(post("/api/v1/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Password updated"));

        verify(refreshTokenService).revokeAllRefreshTokensForUser(10L);
    }

    @Test
    @WithMockUser(username = "user@test.com", roles = {"CUSTOMER"})
    void shouldRejectChangePasswordWithShortNewPassword() throws Exception {
        Role role = Role.builder().id(1L).name("CUSTOMER").build();
        User user = User.builder()
                .id(10L)
                .email("user@test.com")
                .passwordHash("$2a$10$hash")
                .fullName("Test User")
                .phone("+1234567890")
                .role(role)
                .status(com.flowerconnect.domain.User.Status.ACTIVE)
                .createdAt(LocalDateTime.of(2024, 1, 15, 10, 30))
                .build();

        when(userRepository.findByEmailWithRole("user@test.com")).thenReturn(Optional.of(user));

        ChangePasswordRequest request = ChangePasswordRequest.builder()
                .currentPassword("password123")
                .newPassword("short")
                .build();

        mockMvc.perform(post("/api/v1/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
