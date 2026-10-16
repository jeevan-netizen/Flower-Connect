package com.flowerconnect.vendor.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowerconnect.config.TestClockConfig;
import com.flowerconnect.controller.exception.GlobalExceptionHandler;
import com.flowerconnect.exception.BusinessException;
import com.flowerconnect.vendor.dto.SlotResponse;
import com.flowerconnect.vendor.service.SlotService;
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

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice tests for {@link SlotController} — HTTP status, RBAC boundary,
 * request validation, and delegation to {@code SlotService}. The service is
 * mocked; integration-level business logic (slot calculations, eligibility)
 * is covered by {@code SlotServiceTest}.
 */
@WebMvcTest(controllers = SlotController.class)
@Import({SlotControllerTest.TestSecurityConfig.class, TestClockConfig.class, GlobalExceptionHandler.class})
class SlotControllerTest {

    @TestConfiguration
    static class TestSecurityConfig {
        @Bean
        SecurityFilterChain testFilterChain(HttpSecurity http) throws Exception {
            http
                    .csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers("/api/v1/vendors/**/slots").hasRole("CUSTOMER")
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
    private SlotService slotService;

    // ------------------------------------------------------- authorization

    @Test
    void getAvailableSlotsRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/vendors/1/slots")
                        .param("addressId", "1"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(slotService);
    }

    @Test
    @WithMockUser(username = "vendor@test.com", roles = {"FLORIST"})
    void aFloristCannotAccessSlots() throws Exception {
        mockMvc.perform(get("/api/v1/vendors/1/slots")
                        .param("addressId", "1"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(slotService);
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = {"ADMIN"})
    void anAdminCannotAccessSlots() throws Exception {
        mockMvc.perform(get("/api/v1/vendors/1/slots")
                        .param("addressId", "1"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(slotService);
    }

    // ----------------------------------------------------------------- get

    @Test
    @WithMockUser(username = "customer@test.com", roles = {"CUSTOMER"})
    void getAvailableSlotsReturnsTheResponse() throws Exception {
        when(slotService.getAvailableSlots(anyLong(), anyLong(), any(LocalDate.class), anyString()))
                .thenReturn(List.of(
                        SlotResponse.builder()
                                .slotStart(LocalDate.of(2026, 10, 16).atStartOfDay())
                                .slotEnd(LocalDate.of(2026, 10, 16).atStartOfDay().plusHours(1))
                                .weekday(java.time.DayOfWeek.FRIDAY)
                                .available(true)
                                .build(),
                        SlotResponse.builder()
                                .slotStart(LocalDate.of(2026, 10, 16).atStartOfDay().plusHours(1))
                                .slotEnd(LocalDate.of(2026, 10, 16).atStartOfDay().plusHours(2))
                                .weekday(java.time.DayOfWeek.FRIDAY)
                                .available(false)
                                .build()
                ));

        mockMvc.perform(get("/api/v1/vendors/1/slots")
                        .param("addressId", "2")
                        .param("date", "2026-10-16"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].available").value(true))
                .andExpect(jsonPath("$[1].available").value(false));

        verify(slotService).getAvailableSlots(1L, 2L, LocalDate.of(2026, 10, 16), "customer@test.com");
    }

    @Test
    @WithMockUser(username = "customer@test.com", roles = {"CUSTOMER"})
    void getAvailableSlotsUsesTodayWhenDateNull() throws Exception {
        when(slotService.getAvailableSlots(anyLong(), anyLong(), isNull(LocalDate.class), anyString()))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/vendors/1/slots")
                        .param("addressId", "2"))
                .andExpect(status().isOk());

        // Verify service was called with null date (controller passes null when date parameter is absent)
        verify(slotService).getAvailableSlots(
                eq(1L), 
                eq(2L), 
                isNull(LocalDate.class), 
                eq("customer@test.com"));
    }

    // ----------------------------------------------------------- validation

    @Test
    @WithMockUser(username = "customer@test.com", roles = {"CUSTOMER"})
    void getAvailableSlotsRejectsNonPositiveVendorId() throws Exception {
        mockMvc.perform(get("/api/v1/vendors/0/slots")
                        .param("addressId", "1"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(slotService);
    }

    @Test
    @WithMockUser(username = "customer@test.com", roles = {"CUSTOMER"})
    void getAvailableSlotsRejectsNonPositiveAddressId() throws Exception {
        mockMvc.perform(get("/api/v1/vendors/1/slots")
                        .param("addressId", "0"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(slotService);
    }

    @Test
    @WithMockUser(username = "customer@test.com", roles = {"CUSTOMER"})
    void getAvailableSlotsRejectsInvalidIsoDate() throws Exception {
        mockMvc.perform(get("/api/v1/vendors/1/slots")
                        .param("addressId", "1")
                        .param("date", "2026-13-16")) // Invalid month
                .andExpect(status().isBadRequest());

        verifyNoInteractions(slotService);
    }

    // ----------------------------------------------------------- service layer exceptions

    @Test
    @WithMockUser(username = "customer@test.com", roles = {"CUSTOMER"})
    void getAvailableSlotsHandlesVendorNotFound() throws Exception {
        doThrow(BusinessException.notFound("Vendor not found"))
                .when(slotService).getAvailableSlots(anyLong(), anyLong(), isNull(LocalDate.class), anyString());

        mockMvc.perform(get("/api/v1/vendors/999/slots")
                        .param("addressId", "1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        verify(slotService).getAvailableSlots(999L, 1L, null, "customer@test.com");
    }

    @Test
    @WithMockUser(username = "customer@test.com", roles = {"CUSTOMER"})
    void getAvailableSlotsHandlesVendorNotApproved() throws Exception {
        doThrow(BusinessException.forbidden("Vendor is not approved"))
                .when(slotService).getAvailableSlots(anyLong(), anyLong(), isNull(LocalDate.class), anyString());

        mockMvc.perform(get("/api/v1/vendors/1/slots")
                        .param("addressId", "1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        verify(slotService).getAvailableSlots(1L, 1L, null, "customer@test.com");
    }

    @Test
    @WithMockUser(username = "customer@test.com", roles = {"CUSTOMER"})
    void getAvailableSlotsHandlesAddressNotFound() throws Exception {
        doThrow(BusinessException.notFound("Address not found"))
                .when(slotService).getAvailableSlots(anyLong(), anyLong(), isNull(LocalDate.class), anyString());

        mockMvc.perform(get("/api/v1/vendors/1/slots")
                        .param("addressId", "999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        verify(slotService).getAvailableSlots(1L, 999L, null, "customer@test.com");
    }
}