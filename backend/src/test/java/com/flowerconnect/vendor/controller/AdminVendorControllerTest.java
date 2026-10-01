package com.flowerconnect.vendor.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowerconnect.config.TestClockConfig;
import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.exception.BusinessException;
import com.flowerconnect.vendor.dto.VendorAdminReasonRequest;
import com.flowerconnect.vendor.dto.VendorProfilePageResponse;
import com.flowerconnect.vendor.dto.VendorProfileResponse;
import com.flowerconnect.vendor.service.VendorAdminService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Web-layer coverage for admin vendor management (plan task 2.6).
 */
@WebMvcTest(controllers = AdminVendorController.class)
@Import({AdminVendorControllerTest.TestSecurityConfig.class, TestClockConfig.class})
class AdminVendorControllerTest {

    @TestConfiguration
    static class TestSecurityConfig {
        /** Mirrors the production rule: admin routes require ROLE_ADMIN. */
        @Bean
        SecurityFilterChain testFilterChain(HttpSecurity http) throws Exception {
            http
                    .csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
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
    private VendorAdminService vendorAdminService;

    // ------------------------------------------------------------- authorization

    @Test
    void listingVendorsRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/admin/vendors"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(vendorAdminService);
    }

    @Test
    @WithMockUser(username = "customer@test.com", roles = {"CUSTOMER"})
    void aCustomerCannotListVendors() throws Exception {
        mockMvc.perform(get("/api/v1/admin/vendors"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(vendorAdminService);
    }

    @Test
    @WithMockUser(username = "florist@test.com", roles = {"FLORIST"})
    void aVendorCannotListVendors() throws Exception {
        mockMvc.perform(get("/api/v1/admin/vendors"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(vendorAdminService);
    }

    @Test
    @WithMockUser(username = "florist@test.com", roles = {"FLORIST"})
    void aVendorCannotApproveAVendor() throws Exception {
        mockMvc.perform(post("/api/v1/admin/vendors/1/approve"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(vendorAdminService);
    }

    @Test
    @WithMockUser(username = "florist@test.com", roles = {"FLORIST"})
    void aVendorCannotSuspendAVendor() throws Exception {
        mockMvc.perform(post("/api/v1/admin/vendors/1/suspend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reason("fraud")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(vendorAdminService);
    }

    // -------------------------------------------------------------------- listing

    @Test
    @WithMockUser(username = "admin@test.com", roles = {"ADMIN"})
    void anAdminCanListVendors() throws Exception {
        when(vendorAdminService.listProfiles(null, 0, 20)).thenReturn(samplePage());

        mockMvc.perform(get("/api/v1/admin/vendors"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = {"ADMIN"})
    void theListingCanBeFilteredByStatus() throws Exception {
        when(vendorAdminService.listProfiles(VendorProfile.Status.PENDING_APPROVAL, 0, 20))
                .thenReturn(samplePage());

        mockMvc.perform(get("/api/v1/admin/vendors")
                        .param("status", "PENDING_APPROVAL"))
                .andExpect(status().isOk());

        verify(vendorAdminService).listProfiles(VendorProfile.Status.PENDING_APPROVAL, 0, 20);
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = {"ADMIN"})
    void theListingIsPaginated() throws Exception {
        when(vendorAdminService.listProfiles(null, 2, 5)).thenReturn(samplePage());

        mockMvc.perform(get("/api/v1/admin/vendors")
                        .param("page", "2")
                        .param("size", "5"))
                .andExpect(status().isOk());

        verify(vendorAdminService).listProfiles(null, 2, 5);
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = {"ADMIN"})
    void anUnknownStatusFilterIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/admin/vendors")
                        .param("status", "NOT_A_STATUS"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(vendorAdminService);
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = {"ADMIN"})
    void anOversizedPageIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/admin/vendors")
                        .param("size", "500"))
                .andExpect(status().isBadRequest());
    }

    // -------------------------------------------------------------------- actions

    @Test
    @WithMockUser(username = "admin@test.com", roles = {"ADMIN"})
    void anAdminCanAppropendVendor() throws Exception {
        when(vendorAdminService.approve(anyString(), eq(1L))).thenReturn(sampleProfile("APPROVED"));

        mockMvc.perform(post("/api/v1/admin/vendors/1/approve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        verify(vendorAdminService).approve("admin@test.com", 1L);
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = {"ADMIN"})
    void anAdminCanRejectAVendorWithAReason() throws Exception {
        when(vendorAdminService.reject(anyString(), eq(1L), anyString()))
                .thenReturn(sampleProfile("REJECTED"));

        mockMvc.perform(post("/api/v1/admin/vendors/1/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reason("Incomplete licence")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        verify(vendorAdminService).reject("admin@test.com", 1L, "Incomplete licence");
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = {"ADMIN"})
    void rejectionWithoutAReasonIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/admin/vendors/1/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reason("   ")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validation.reason").exists());

        verifyNoInteractions(vendorAdminService);
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = {"ADMIN"})
    void rejectionWithoutABodyIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/admin/vendors/1/reject")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(vendorAdminService);
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = {"ADMIN"})
    void anAdminCanSuspendAVendor() throws Exception {
        when(vendorAdminService.suspend(anyString(), eq(1L), anyString()))
                .thenReturn(sampleProfile("SUSPENDED"));

        mockMvc.perform(post("/api/v1/admin/vendors/1/suspend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reason("Repeated late deliveries")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));

        verify(vendorAdminService).suspend("admin@test.com", 1L, "Repeated late deliveries");
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = {"ADMIN"})
    void anAdminCanReinstateAVendor() throws Exception {
        when(vendorAdminService.reinstate(anyString(), eq(1L)))
                .thenReturn(sampleProfile("APPROVED"));

        mockMvc.perform(post("/api/v1/admin/vendors/1/reinstate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        verify(vendorAdminService).reinstate("admin@test.com", 1L);
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = {"ADMIN"})
    void anIllegalTransitionIsReportedAsConflict() throws Exception {
        doThrow(BusinessException.conflict("Cannot approve a vendor profile in status APPROVED"))
                .when(vendorAdminService).approve(anyString(), any());

        mockMvc.perform(post("/api/v1/admin/vendors/1/approve"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = {"ADMIN"})
    void anUnknownVendorIsReportedAsNotFound() throws Exception {
        doThrow(BusinessException.notFound("Vendor profile not found"))
                .when(vendorAdminService).reinstate(anyString(), any());

        mockMvc.perform(post("/api/v1/admin/vendors/999/reinstate"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = {"ADMIN"})
    void aNonPositiveProfileIdIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/admin/vendors/0/approve"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(vendorAdminService);
    }

    private String reason(String text) throws Exception {
        return objectMapper.writeValueAsString(
                VendorAdminReasonRequest.builder().reason(text).build());
    }

    private static VendorProfileResponse sampleProfile() {
        return sampleProfile("PENDING_APPROVAL");
    }

    private static VendorProfileResponse sampleProfile(String status) {
        return VendorProfileResponse.builder()
                .id(1L)
                .businessName("Test Blossoms")
                .status(status)
                .hours(List.of())
                .build();
    }

    private static VendorProfilePageResponse samplePage() {
        return VendorProfilePageResponse.builder()
                .content(List.of(sampleProfile()))
                .page(0)
                .size(20)
                .totalElements(1)
                .totalPages(1)
                .first(true)
                .last(true)
                .empty(false)
                .build();
    }
}
