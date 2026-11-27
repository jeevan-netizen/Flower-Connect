package com.flowerconnect.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowerconnect.config.TestClockConfig;
import com.flowerconnect.domain.User;
import com.flowerconnect.exception.BusinessException;
import com.flowerconnect.farmer.dto.FarmerAdminDetail;
import com.flowerconnect.farmer.dto.FarmerAdminSummary;
import com.flowerconnect.farmer.service.AdminFarmerService;
import com.flowerconnect.security.dto.AdminUserResponse;
import com.flowerconnect.security.dto.UserStatusUpdateRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Web-layer coverage for admin farmer management (Admin Farmer Management):
 * the authorization rules declared in the production {@code SecurityConfig},
 * HTTP status codes, and the error response shape produced by the shared
 * {@code GlobalExceptionHandler}.
 */
@WebMvcTest(controllers = AdminFarmerController.class)
@Import({AdminFarmerControllerTest.TestSecurityConfig.class, TestClockConfig.class})
class AdminFarmerControllerTest {

    @TestConfiguration
    static class TestSecurityConfig {
        @Bean
        SecurityFilterChain testFilterChain(HttpSecurity http) throws Exception {
            http
                    .csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers("/api/v1/admin/farmers/**").hasRole("ADMIN")
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
    private AdminFarmerService adminFarmerService;

    // ------------------------------------------------------------- authorization

    @Test
    void listingRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/admin/farmers"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(adminFarmerService);
    }

    @Test
    @WithMockUser(username = "customer@test.com", roles = {"CUSTOMER"})
    void aCustomerCannotListFarmers() throws Exception {
        mockMvc.perform(get("/api/v1/admin/farmers"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(adminFarmerService);
    }

    @Test
    @WithMockUser(username = "florist@test.com", roles = {"FLORIST"})
    void aVendorCannotListFarmers() throws Exception {
        mockMvc.perform(get("/api/v1/admin/farmers"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(adminFarmerService);
    }

    @Test
    void detailRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/admin/farmers/1"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(adminFarmerService);
    }

    @Test
    @WithMockUser(username = "customer@test.com", roles = {"CUSTOMER"})
    void aCustomerCannotViewFarmerDetail() throws Exception {
        mockMvc.perform(get("/api/v1/admin/farmers/1"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(adminFarmerService);
    }

    @Test
    void statusChangeRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/admin/farmers/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SUSPENDED\",\"reason\":\"abuse\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(adminFarmerService);
    }

    @Test
    @WithMockUser(username = "customer@test.com", roles = {"CUSTOMER"})
    void aCustomerCannotChangeFarmerStatus() throws Exception {
        mockMvc.perform(post("/api/v1/admin/farmers/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SUSPENDED\",\"reason\":\"abuse\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(adminFarmerService);
    }

    // -------------------------------------------------------------------- listing

    @Test
    @WithMockUser(username = "admin@test.com", roles = {"ADMIN"})
    void anAdminCanListFarmers() throws Exception {
        when(adminFarmerService.listFarmers(null, null, 0, 20))
                .thenReturn(new PageImpl<>(List.of(sampleFarmerSummary())));

        mockMvc.perform(get("/api/v1/admin/farmers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = {"ADMIN"})
    void theListingCanBeFilteredByStatusAndSearched() throws Exception {
        when(adminFarmerService.listFarmers("rose", User.Status.SUSPENDED, 0, 20))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/v1/admin/farmers")
                        .param("search", "rose")
                        .param("status", "SUSPENDED"))
                .andExpect(status().isOk());

        verify(adminFarmerService).listFarmers("rose", User.Status.SUSPENDED, 0, 20);
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = {"ADMIN"})
    void theListingIsPaginated() throws Exception {
        when(adminFarmerService.listFarmers(null, null, 2, 5))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/v1/admin/farmers")
                        .param("page", "2")
                        .param("size", "5"))
                .andExpect(status().isOk());

        verify(adminFarmerService).listFarmers(null, null, 2, 5);
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = {"ADMIN"})
    void anUnknownStatusFilterIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/admin/farmers").param("status", "NOT_A_STATUS"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(adminFarmerService);
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = {"ADMIN"})
    void anOversizedPageIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/admin/farmers").param("size", "500"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(adminFarmerService);
    }

    // -------------------------------------------------------------------- detail

    @Test
    @WithMockUser(username = "admin@test.com", roles = {"ADMIN"})
    void anAdminCanViewFarmerDetail() throws Exception {
        when(adminFarmerService.getFarmerDetail(1L))
                .thenReturn(sampleFarmerDetail());

        mockMvc.perform(get("/api/v1/admin/farmers/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.fullName").value("Farm Owner"))
                .andExpect(jsonPath("$.email").value("farmer@test.com"))
                .andExpect(jsonPath("$.address").value("123 Garden Lane"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = {"ADMIN"})
    void anUnknownFarmerIsReportedAsNotFound() throws Exception {
        when(adminFarmerService.getFarmerDetail(404L))
                .thenThrow(BusinessException.notFound("Farmer not found"));

        mockMvc.perform(get("/api/v1/admin/farmers/404"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = {"ADMIN"})
    void aNonPositiveFarmerIdIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/admin/farmers/0"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(adminFarmerService);
    }

    // -------------------------------------------------------------------- status

    @Test
    @WithMockUser(username = "admin@test.com", roles = {"ADMIN"})
    void anAdminCanSuspendAFarmer() throws Exception {
        when(adminFarmerService.changeFarmerStatus(
                eq("admin@test.com"), eq(1L), eq(User.Status.SUSPENDED), anyString()))
                .thenReturn(sampleAdminUserResponse("SUSPENDED"));

        mockMvc.perform(post("/api/v1/admin/farmers/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SUSPENDED\",\"reason\":\"Payment fraud\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));

        verify(adminFarmerService).changeFarmerStatus(
                "admin@test.com", 1L, User.Status.SUSPENDED, "Payment fraud");
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = {"ADMIN"})
    void anAdminCanReactivateAFarmer() throws Exception {
        when(adminFarmerService.changeFarmerStatus(
                anyString(), eq(1L), eq(User.Status.ACTIVE), anyString()))
                .thenReturn(sampleAdminUserResponse("ACTIVE"));

        mockMvc.perform(post("/api/v1/admin/farmers/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACTIVE\",\"reason\":\"Appeal upheld\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        verify(adminFarmerService).changeFarmerStatus(
                "admin@test.com", 1L, User.Status.ACTIVE, "Appeal upheld");
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = {"ADMIN"})
    void aMissingReasonIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/admin/farmers/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SUSPENDED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validation.reason").exists());

        verifyNoInteractions(adminFarmerService);
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = {"ADMIN"})
    void aBlankReasonIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/admin/farmers/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SUSPENDED\",\"reason\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validation.reason").exists());

        verifyNoInteractions(adminFarmerService);
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = {"ADMIN"})
    void aMissingStatusIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/admin/farmers/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"abuse\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validation.status").exists());

        verifyNoInteractions(adminFarmerService);
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = {"ADMIN"})
    void aSelfStatusChangeIsReportedAsForbidden() throws Exception {
        when(adminFarmerService.changeFarmerStatus(anyString(), eq(1L), any(), anyString()))
                .thenThrow(BusinessException.forbidden(
                        "An administrator cannot change their own account status"));

        mockMvc.perform(post("/api/v1/admin/farmers/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SUSPENDED\",\"reason\":\"self\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value(
                        "An administrator cannot change their own account status"));
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = {"ADMIN"})
    void anUnknownFarmerStatusChangeIsReportedAsNotFound() throws Exception {
        when(adminFarmerService.changeFarmerStatus(anyString(), eq(404L), any(), anyString()))
                .thenThrow(BusinessException.notFound("Farmer not found"));

        mockMvc.perform(post("/api/v1/admin/farmers/404/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SUSPENDED\",\"reason\":\"abuse\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = {"ADMIN"})
    void aNonPositiveFarmerIdIsRejectedForStatus() throws Exception {
        mockMvc.perform(post("/api/v1/admin/farmers/0/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SUSPENDED\",\"reason\":\"abuse\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(adminFarmerService);
    }

    // ------------------------------------------------------------------ helpers

    private static FarmerAdminSummary sampleFarmerSummary() {
        return FarmerAdminSummary.builder()
                .id(1L)
                .email("farmer@test.com")
                .fullName("Farm Owner")
                .phone("+919999999999")
                .status(User.Status.ACTIVE)
                .build();
    }

    private static FarmerAdminDetail sampleFarmerDetail() {
        return FarmerAdminDetail.builder()
                .id(1L)
                .email("farmer@test.com")
                .fullName("Farm Owner")
                .phone("+919999999999")
                .address("123 Garden Lane")
                .status(User.Status.ACTIVE)
                .linkedVendors(List.of())
                .build();
    }

    private static AdminUserResponse sampleAdminUserResponse(String status) {
        return AdminUserResponse.builder()
                .id(1L)
                .email("farmer@test.com")
                .fullName("Farm Owner")
                .phone("+919999999999")
                .role("FARMER")
                .status(User.Status.valueOf(status))
                .build();
    }
}
