package com.flowerconnect.controller;

import com.flowerconnect.domain.Role;
import com.flowerconnect.domain.User;
import com.flowerconnect.mapper.UserMapper;
import com.flowerconnect.repository.UserRepository;
import com.flowerconnect.security.dto.UserResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.*;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.*;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = UserController.class)
@Import(UserControllerTest.TestSecurityConfig.class)
class UserControllerTest {

    @TestConfiguration
    static class TestSecurityConfig {
        @Bean
        SecurityFilterChain testFilterChain(HttpSecurity http) throws Exception {
            http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/users/me").authenticated()
                        .anyRequest().permitAll())
                .httpBasic(Customizer.withDefaults());
            return http.build();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private UserMapper userMapper;

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

        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
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
                .andExpect(status().is5xxServerError());
    }
}
