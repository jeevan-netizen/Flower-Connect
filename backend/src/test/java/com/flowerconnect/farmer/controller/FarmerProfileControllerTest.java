package com.flowerconnect.farmer.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowerconnect.config.TestClockConfig;
import com.flowerconnect.domain.Role;
import com.flowerconnect.domain.User;
import com.flowerconnect.farmer.dto.FarmerProfileResponse;
import com.flowerconnect.farmer.service.FarmerProfileService;
import com.flowerconnect.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Web-layer coverage for the farmer profile endpoint: the FARMER authorization
 * rule, HTTP status codes, and the shared error-response envelope.
 */
@WebMvcTest(controllers = FarmerProfileController.class)
@Import({FarmerProfileControllerTest.TestSecurityConfig.class, TestClockConfig.class})
class FarmerProfileControllerTest {

    @TestConfiguration
    static class TestSecurityConfig {
        @Bean
        SecurityFilterChain testFilterChain(HttpSecurity http) throws Exception {
            http
                    .csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers(HttpMethod.GET, "/api/v1/farmer/profile").hasRole("FARMER")
                            .anyRequest().authenticated())
                    .httpBasic(Customizer.withDefaults());
            return http.build();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private FarmerProfileService farmerProfileService;

    @MockBean
    private UserRepository userRepository;

    private User farmer;

    @BeforeEach
    void setUp() {
        farmer = User.builder()
                .id(7L)
                .email("farmer@test.com")
                .fullName("Demo Farmer")
                .phone("+919999999999")
                .address("12, Flower Market Road, Koramangala")
                .role(Role.builder().id(4L).name("FARMER").build())
                .build();
        when(userRepository.findByEmailWithRole("farmer@test.com"))
                .thenReturn(Optional.of(farmer));
    }

    // ------------------------------------------------------------- authorization

    @Test
    void profileRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/farmer/profile"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "customer@test.com", roles = {"CUSTOMER"})
    void aCustomerCannotReadTheFarmerProfile() throws Exception {
        mockMvc.perform(get("/api/v1/farmer/profile"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "florist@test.com", roles = {"FLORIST"})
    void aFloristCannotReadTheFarmerProfile() throws Exception {
        mockMvc.perform(get("/api/v1/farmer/profile"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = {"ADMIN"})
    void anAdminCannotReadTheFarmerProfile() throws Exception {
        mockMvc.perform(get("/api/v1/farmer/profile"))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------- profile display

    @Test
    @WithMockUser(username = "farmer@test.com", roles = {"FARMER"})
    void aFarmerCanReadTheirOwnProfile() throws Exception {
        when(farmerProfileService.profile(anyString())).thenReturn(FarmerProfileResponse.builder()
                .id(7L)
                .email("farmer@test.com")
                .fullName("Demo Farmer")
                .phone("+919999999999")
                .address("12, Flower Market Road, Koramangala")
                .role("FARMER")
                .build());

        mockMvc.perform(get("/api/v1/farmer/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Demo Farmer"))
                .andExpect(jsonPath("$.phone").value("+919999999999"))
                .andExpect(jsonPath("$.address").value("12, Flower Market Road, Koramangala"))
                .andExpect(jsonPath("$.role").value("FARMER"));
    }

    @Test
    @WithMockUser(username = "farmer@test.com", roles = {"FARMER"})
    void aMissingAddressIsReturnedAsNull() throws Exception {
        User farmerWithoutAddress = User.builder()
                .id(7L)
                .email("farmer@test.com")
                .fullName("Demo Farmer")
                .phone("+919999999999")
                .address(null)
                .role(Role.builder().id(4L).name("FARMER").build())
                .build();
        when(userRepository.findByEmailWithRole("farmer@test.com"))
                .thenReturn(Optional.of(farmerWithoutAddress));
        when(farmerProfileService.profile(anyString())).thenReturn(FarmerProfileResponse.builder()
                .id(7L)
                .email("farmer@test.com")
                .fullName("Demo Farmer")
                .phone("+919999999999")
                .address(null)
                .role("FARMER")
                .build());

        mockMvc.perform(get("/api/v1/farmer/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.address").isEmpty());
    }

    @Test
    @WithMockUser(username = "farmer@test.com", roles = {"FARMER"})
    void theErrorResponseCarriesTheSharedEnvelopeShape() throws Exception {
        when(farmerProfileService.profile(anyString()))
                .thenThrow(new com.flowerconnect.exception.BusinessException(
                        com.flowerconnect.exception.ErrorCode.NOT_FOUND, "Farmer not found"));

        mockMvc.perform(get("/api/v1/farmer/profile"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.status").value(404));
    }
}
