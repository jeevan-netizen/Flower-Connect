package com.flowerconnect.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowerconnect.config.TestClockConfig;
import com.flowerconnect.domain.User;
import com.flowerconnect.exception.BusinessException;
import com.flowerconnect.security.AdminUserService;
import com.flowerconnect.security.UserStatusService;
import com.flowerconnect.security.dto.AdminUserPageResponse;
import com.flowerconnect.security.dto.AdminUserResponse;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Web-layer coverage for admin user status management (plan task 2.8).
 */
@WebMvcTest(controllers = AdminUserController.class)
@Import({AdminUserControllerTest.TestSecurityConfig.class, TestClockConfig.class})
class AdminUserControllerTest {

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
    private AdminUserService adminUserService;

    @MockBean
    private UserStatusService userStatusService;

    // ------------------------------------------------------------- authorization

    @Test
    void listingRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(adminUserService);
    }

    @Test
    void changingStatusRequiresAuthentication() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/users/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SUSPENDED\",\"reason\":\"abuse\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(userStatusService);
    }

    @Test
    @WithMockUser(username = "customer@test.com", roles = {"CUSTOMER"})
    void aCustomerCannotListUsers() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(adminUserService);
    }

    @Test
    @WithMockUser(username = "florist@test.com", roles = {"FLORIST"})
    void aVendorCannotListUsers() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(adminUserService);
    }

    @Test
    @WithMockUser(username = "customer@test.com", roles = {"CUSTOMER"})
    void aCustomerCannotChangeAUsersStatus() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/users/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SUSPENDED\",\"reason\":\"abuse\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(userStatusService);
    }

    @Test
    @WithMockUser(username = "florist@test.com", roles = {"FLORIST"})
    void aVendorCannotChangeAUsersStatus() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/users/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SUSPENDED\",\"reason\":\"abuse\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(userStatusService);
    }

    // -------------------------------------------------------------------- listing

    @Test
    @WithMockUser(username = "admin@test.com", roles = {"ADMIN"})
    void anAdminCanListUsers() throws Exception {
        when(adminUserService.listUsers(null, null, 0, 20)).thenReturn(samplePage());

        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].status").value("SUSPENDED"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = {"ADMIN"})
    void theListingCanBeFilteredByRoleAndStatus() throws Exception {
        when(adminUserService.listUsers("CUSTOMER", User.Status.ACTIVE, 0, 20))
                .thenReturn(samplePage());

        mockMvc.perform(get("/api/v1/admin/users")
                        .param("role", "CUSTOMER")
                        .param("status", "ACTIVE"))
                .andExpect(status().isOk());

        verify(adminUserService).listUsers("CUSTOMER", User.Status.ACTIVE, 0, 20);
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = {"ADMIN"})
    void theListingIsPaginated() throws Exception {
        when(adminUserService.listUsers(null, null, 2, 5)).thenReturn(samplePage());

        mockMvc.perform(get("/api/v1/admin/users")
                        .param("page", "2")
                        .param("size", "5"))
                .andExpect(status().isOk());

        verify(adminUserService).listUsers(null, null, 2, 5);
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = {"ADMIN"})
    void anUnknownStatusFilterIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users").param("status", "NOT_A_STATUS"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(adminUserService);
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = {"ADMIN"})
    void anOversizedPageIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users").param("size", "500"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(adminUserService);
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = {"ADMIN"})
    void anUnknownRoleFilterIsReportedAsABadRequest() throws Exception {
        when(adminUserService.listUsers(eq("GHOST"), isNull(), anyInt(), anyInt()))
                .thenThrow(BusinessException.badRequest("Unknown role: GHOST"));

        mockMvc.perform(get("/api/v1/admin/users").param("role", "GHOST"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("Unknown role: GHOST"));
    }

    // -------------------------------------------------------------------- status

    @Test
    @WithMockUser(username = "admin@test.com", roles = {"ADMIN"})
    void anAdminCanSuspendAUser() throws Exception {
        when(userStatusService.changeStatus(anyString(), eq(1L),
                eq(User.Status.SUSPENDED), anyString()))
                .thenReturn(sampleUser("SUSPENDED"));

        mockMvc.perform(patch("/api/v1/admin/users/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SUSPENDED\",\"reason\":\"Payment fraud\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));

        verify(userStatusService).changeStatus(
                "admin@test.com", 1L, User.Status.SUSPENDED, "Payment fraud");
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = {"ADMIN"})
    void anAdminCanDisableAUser() throws Exception {
        when(userStatusService.changeStatus(anyString(), eq(1L),
                eq(User.Status.DISABLED), anyString()))
                .thenReturn(sampleUser("DISABLED"));

        mockMvc.perform(patch("/api/v1/admin/users/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DISABLED\",\"reason\":\"Terms violated\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISABLED"));
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = {"ADMIN"})
    void anAdminCanReactivateAUser() throws Exception {
        when(userStatusService.changeStatus(anyString(), eq(1L),
                eq(User.Status.ACTIVE), anyString()))
                .thenReturn(sampleUser("ACTIVE"));

        mockMvc.perform(patch("/api/v1/admin/users/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACTIVE\",\"reason\":\"Appeal upheld\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = {"ADMIN"})
    void aMissingReasonIsRejected() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/users/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SUSPENDED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validation.reason").exists());

        verifyNoInteractions(userStatusService);
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = {"ADMIN"})
    void aBlankReasonIsRejected() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/users/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SUSPENDED\",\"reason\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validation.reason").exists());

        verifyNoInteractions(userStatusService);
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = {"ADMIN"})
    void aMissingStatusIsRejected() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/users/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"abuse\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validation.status").exists());

        verifyNoInteractions(userStatusService);
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = {"ADMIN"})
    void anUnknownStatusIsRejected() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/users/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"BANNED\",\"reason\":\"abuse\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(userStatusService);
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = {"ADMIN"})
    void anOverlongReasonIsRejected() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/users/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "status", "SUSPENDED",
                                "reason", "x".repeat(501)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validation.reason").exists());

        verifyNoInteractions(userStatusService);
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = {"ADMIN"})
    void aMissingBodyIsRejected() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/users/1/status")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(userStatusService);
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = {"ADMIN"})
    void aSelfStatusChangeIsReportedAsForbidden() throws Exception {
        when(userStatusService.changeStatus(anyString(), eq(1L), any(), anyString()))
                .thenThrow(BusinessException.forbidden(
                        "An administrator cannot change their own account status"));

        mockMvc.perform(patch("/api/v1/admin/users/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SUSPENDED\",\"reason\":\"self\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value(
                        "An administrator cannot change their own account status"));
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = {"ADMIN"})
    void anUnknownUserIsReportedAsNotFound() throws Exception {
        when(userStatusService.changeStatus(anyString(), eq(404L), any(), anyString()))
                .thenThrow(BusinessException.notFound("User not found"));

        mockMvc.perform(patch("/api/v1/admin/users/404/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SUSPENDED\",\"reason\":\"abuse\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = {"ADMIN"})
    void aNonPositiveUserIdIsRejected() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/users/0/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SUSPENDED\",\"reason\":\"abuse\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(userStatusService);
    }

    // ------------------------------------------------------------------ helpers

    private static AdminUserResponse sampleUser(String status) {
        return AdminUserResponse.builder()
                .id(1L)
                .email("target@test.com")
                .fullName("Test User")
                .phone("+919999999999")
                .role("CUSTOMER")
                .status(User.Status.valueOf(status))
                .build();
    }

    private static AdminUserPageResponse samplePage() {
        return AdminUserPageResponse.builder()
                .content(List.of(sampleUser("SUSPENDED")))
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