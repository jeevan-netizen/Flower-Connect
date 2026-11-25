package com.flowerconnect.farmer.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowerconnect.config.TestClockConfig;
import com.flowerconnect.domain.Role;
import com.flowerconnect.domain.User;
import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.farmer.dto.FarmerLinkResponse;
import com.flowerconnect.farmer.domain.FarmerVendorLink;
import com.flowerconnect.farmer.service.FarmerLinkService;
import com.flowerconnect.repository.VendorProfileRepository;
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

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Web-layer coverage for the vendor side of farmer/vendor links (Stage 1 of the
 * farmer module): the authorization rules declared in the production
 * {@code SecurityConfig}, HTTP status codes, and the error response shape.
 */
@WebMvcTest(controllers = VendorFarmerLinkController.class)
@Import({VendorFarmerLinkControllerTest.TestSecurityConfig.class, TestClockConfig.class})
class VendorFarmerLinkControllerTest {

    @TestConfiguration
    static class TestSecurityConfig {
        @Bean
        SecurityFilterChain testFilterChain(HttpSecurity http) throws Exception {
            http
                    .csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers("/api/v1/vendor/farmer-links/**").hasRole("FLORIST")
                            .requestMatchers("/api/v1/vendor/**").hasRole("FLORIST")
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
    private FarmerLinkService farmerLinkService;

    @MockBean
    private VendorProfileRepository vendorProfileRepository;

    private VendorProfile vendorProfile;

    @BeforeEach
    void setUp() {
        User florist = User.builder()
                .id(2L)
                .email("florist@test.com")
                .role(Role.builder().id(2L).name("FLORIST").build())
                .build();
        vendorProfile = VendorProfile.builder()
                .id(10L)
                .user(florist)
                .businessName("Green Acres")
                .status(VendorProfile.Status.APPROVED)
                .build();
        when(vendorProfileRepository.findByUserEmail("florist@test.com"))
                .thenReturn(Optional.of(vendorProfile));
    }

    // ------------------------------------------------------------- authorization

    @Test
    void listVendorLinksRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/vendor/farmer-links"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "customer@test.com", roles = {"CUSTOMER"})
    void aCustomerCannotListVendorLinks() throws Exception {
        mockMvc.perform(get("/api/v1/vendor/farmer-links"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = {"ADMIN"})
    void anAdminCannotListVendorLinks() throws Exception {
        mockMvc.perform(get("/api/v1/vendor/farmer-links"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "farmer@test.com", roles = {"FARMER"})
    void aFarmerCannotListVendorLinks() throws Exception {
        mockMvc.perform(get("/api/v1/vendor/farmer-links"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "florist@test.com", roles = {"FLORIST"})
    void aFloristCanListVendorLinks() throws Exception {
        when(farmerLinkService.listVendorLinks(anyLong(), any())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/vendor/farmer-links"))
                .andExpect(status().isOk());
    }

    @Test
    void acceptLinkRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/vendor/farmer-links/100/accept"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "customer@test.com", roles = {"CUSTOMER"})
    void aCustomerCannotAcceptLink() throws Exception {
        mockMvc.perform(post("/api/v1/vendor/farmer-links/100/accept"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "florist@test.com", roles = {"FLORIST"})
    void aFloristCanAcceptLink() throws Exception {
        when(farmerLinkService.acceptLink(anyLong(), anyLong()))
                .thenReturn(FarmerLinkResponse.builder()
                        .id(100L).vendorId(10L).vendorName("Green Acres")
                        .status("ACTIVE").build());

        mockMvc.perform(post("/api/v1/vendor/farmer-links/100/accept"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void rejectLinkRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/vendor/farmer-links/100/reject"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "florist@test.com", roles = {"FLORIST"})
    void aFloristCanRejectLink() throws Exception {
        when(farmerLinkService.rejectLink(anyLong(), anyLong()))
                .thenReturn(FarmerLinkResponse.builder()
                        .id(100L).vendorId(10L).vendorName("Green Acres")
                        .status("REJECTED").build());

        mockMvc.perform(post("/api/v1/vendor/farmer-links/100/reject"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test
    void suspendLinkRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/vendor/farmer-links/100/suspend"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "florist@test.com", roles = {"FLORIST"})
    void aFloristCanSuspendLink() throws Exception {
        when(farmerLinkService.suspendLink(anyLong(), anyLong()))
                .thenReturn(FarmerLinkResponse.builder()
                        .id(100L).vendorId(10L).vendorName("Green Acres")
                        .status("SUSPENDED").build());

        mockMvc.perform(post("/api/v1/vendor/farmer-links/100/suspend"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));
    }

    @Test
    void reactivateLinkRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/vendor/farmer-links/100/reactivate"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "florist@test.com", roles = {"FLORIST"})
    void aFloristCanReactivateLink() throws Exception {
        when(farmerLinkService.reactivateLink(anyLong(), anyLong()))
                .thenReturn(FarmerLinkResponse.builder()
                        .id(100L).vendorId(10L).vendorName("Green Acres")
                        .status("ACTIVE").build());

        mockMvc.perform(post("/api/v1/vendor/farmer-links/100/reactivate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @WithMockUser(username = "florist@test.com", roles = {"FLORIST"})
    void theErrorResponseCarriesTheSharedEnvelopeShape() throws Exception {
        when(farmerLinkService.acceptLink(anyLong(), anyLong()))
                .thenThrow(new com.flowerconnect.exception.BusinessException(
                        com.flowerconnect.exception.ErrorCode.CONFLICT,
                        "Cannot accept a link in status ACTIVE (expected PENDING)"));

        mockMvc.perform(post("/api/v1/vendor/farmer-links/100/accept"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.status").value(409));
    }
}
