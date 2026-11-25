package com.flowerconnect.farmer.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowerconnect.config.TestClockConfig;
import com.flowerconnect.domain.Role;
import com.flowerconnect.domain.User;
import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.farmer.dto.FarmerCollectionResponse;
import com.flowerconnect.farmer.dto.PassbookResponse;
import com.flowerconnect.farmer.service.FarmerCollectionService;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Web-layer coverage for the vendor collection API (Stage 3 of the farmer
 * module): authorization, HTTP status codes and the shared error envelope.
 */
@WebMvcTest(controllers = VendorCollectionController.class)
@Import({VendorCollectionControllerTest.TestSecurityConfig.class, TestClockConfig.class})
class VendorCollectionControllerTest {

    @TestConfiguration
    static class TestSecurityConfig {
        @Bean
        SecurityFilterChain testFilterChain(HttpSecurity http) throws Exception {
            http
                    .csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers("/api/v1/vendor/farmer-links/**").hasRole("FLORIST")
                            .requestMatchers("/api/v1/vendor/farmer-collections/**").hasRole("FLORIST")
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
    private FarmerCollectionService farmerCollectionService;

    @MockBean
    private VendorProfileRepository vendorProfileRepository;

    private User florist;
    private VendorProfile vendorProfile;

    @BeforeEach
    void setUp() {
        florist = User.builder().id(2L).email("florist@test.com")
                .role(Role.builder().name("FLORIST").build()).build();
        vendorProfile = VendorProfile.builder().id(10L).user(florist)
                .businessName("Green Acres").build();
        when(vendorProfileRepository.findByUserEmail("florist@test.com"))
                .thenReturn(Optional.of(vendorProfile));
    }

    // ------------------------------------------------------------- authorization

    @Test
    void passbookRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/vendor/farmer-links/100/passbook"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "customer@test.com", roles = {"CUSTOMER"})
    void aCustomerCannotReadVendorPassbook() throws Exception {
        mockMvc.perform(get("/api/v1/vendor/farmer-links/100/passbook"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = {"ADMIN"})
    void anAdminCannotReadVendorPassbook() throws Exception {
        mockMvc.perform(get("/api/v1/vendor/farmer-links/100/passbook"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "farmer@test.com", roles = {"FARMER"})
    void aFarmerCannotReadVendorPassbook() throws Exception {
        mockMvc.perform(get("/api/v1/vendor/farmer-links/100/passbook"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "florist@test.com", roles = {"FLORIST"})
    void aFloristCanReadVendorPassbook() throws Exception {
        when(farmerCollectionService.getPassbook(anyLong(), anyLong(), anyString()))
                .thenReturn(PassbookResponse.builder()
                        .linkId(100L).outstandingBalance(new BigDecimal("1300.00"))
                        .entries(List.of()).build());

        mockMvc.perform(get("/api/v1/vendor/farmer-links/100/passbook"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outstandingBalance").value(1300.0));
    }

    @Test
    @WithMockUser(username = "florist@test.com", roles = {"FLORIST"})
    void aFloristCanListPendingCollections() throws Exception {
        when(farmerCollectionService.listCollectionsByStatus(anyLong(), any()))
                .thenReturn(List.of(FarmerCollectionResponse.builder()
                        .id(500L).linkId(100L).amount(new BigDecimal("100.00"))
                        .status(FarmerCollectionResponse.Status.PENDING).build()));

        mockMvc.perform(get("/api/v1/vendor/farmer-collections?status=PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(500));
    }

    @Test
    void confirmRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/vendor/farmer-collections/500/confirm"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "customer@test.com", roles = {"CUSTOMER"})
    void aCustomerCannotConfirm() throws Exception {
        mockMvc.perform(post("/api/v1/vendor/farmer-collections/500/confirm"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "florist@test.com", roles = {"FLORIST"})
    void aFloristCanConfirm() throws Exception {
        when(farmerCollectionService.confirmCollection(anyLong(), anyLong()))
                .thenReturn(FarmerCollectionResponse.builder()
                        .id(500L).linkId(100L).amount(new BigDecimal("100.00"))
                        .status(FarmerCollectionResponse.Status.CONFIRMED)
                        .decidedAt(LocalDateTime.parse("2026-01-15T10:00:00")).build());

        mockMvc.perform(post("/api/v1/vendor/farmer-collections/500/confirm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    @WithMockUser(username = "florist@test.com", roles = {"FLORIST"})
    void aFloristCanReject() throws Exception {
        when(farmerCollectionService.rejectCollection(anyLong(), anyLong()))
                .thenReturn(FarmerCollectionResponse.builder()
                        .id(500L).linkId(100L).amount(new BigDecimal("100.00"))
                        .status(FarmerCollectionResponse.Status.REJECTED).build());

        mockMvc.perform(post("/api/v1/vendor/farmer-collections/500/reject"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }
}
