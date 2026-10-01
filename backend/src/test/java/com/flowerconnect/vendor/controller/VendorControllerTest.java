package com.flowerconnect.vendor.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowerconnect.config.TestClockConfig;
import com.flowerconnect.exception.BusinessException;
import com.flowerconnect.vendor.dto.VendorHoursRequest;
import com.flowerconnect.vendor.dto.VendorProfileResponse;
import com.flowerconnect.vendor.dto.VendorProfileUpdateRequest;
import com.flowerconnect.vendor.dto.VendorRegisterRequest;
import com.flowerconnect.vendor.service.VendorService;
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
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Web-layer coverage for the vendor API (plan task 2.5): the authorization rules
 * declared in the production {@code SecurityConfig}, HTTP status codes, and the
 * error response shape produced by the shared {@code GlobalExceptionHandler}.
 */
@WebMvcTest(controllers = VendorController.class)
@Import({VendorControllerTest.TestSecurityConfig.class, TestClockConfig.class})
class VendorControllerTest {

    @TestConfiguration
    static class TestSecurityConfig {
        /**
         * Mirrors the production rules exactly: registration is public, every
         * other vendor route requires the vendor role.
         */
        @Bean
        SecurityFilterChain testFilterChain(HttpSecurity http) throws Exception {
            http
                    .csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers(HttpMethod.POST, "/api/v1/vendors/register").permitAll()
                            .requestMatchers("/api/v1/vendors/**").hasRole("FLORIST")
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
    private VendorService vendorService;

    // ------------------------------------------------------------- authorization

    @Test
    void registrationIsReachableWithoutAuthentication() throws Exception {
        when(vendorService.register(any())).thenReturn(sampleProfile());

        mockMvc.perform(post("/api/v1/vendors/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRegisterRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"));
    }

    @Test
    void readingOwnProfileRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/vendors/profile"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(vendorService);
    }

    @Test
    void updatingOwnProfileRequiresAuthentication() throws Exception {
        mockMvc.perform(put("/api/v1/vendors/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validUpdateRequest())))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(vendorService);
    }

    @Test
    @WithMockUser(username = "customer@test.com", roles = {"CUSTOMER"})
    void aCustomerCannotReadTheVendorProfile() throws Exception {
        mockMvc.perform(get("/api/v1/vendors/profile"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(vendorService);
    }

    @Test
    @WithMockUser(username = "customer@test.com", roles = {"CUSTOMER"})
    void aCustomerCannotUpdateTheVendorProfile() throws Exception {
        mockMvc.perform(put("/api/v1/vendors/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validUpdateRequest())))
                .andExpect(status().isForbidden());
        verifyNoInteractions(vendorService);
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = {"ADMIN"})
    void anAdminIsNotAWhitelistedVendor() throws Exception {
        mockMvc.perform(get("/api/v1/vendors/profile"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(vendorService);
    }

    @Test
    @WithMockUser(username = "florist@test.com", roles = {"FLORIST"})
    void aVendorCanReadTheirOwnProfile() throws Exception {
        when(vendorService.getOwnProfile(anyString())).thenReturn(sampleProfile());

        mockMvc.perform(get("/api/v1/vendors/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessName").value("Test Blossoms"))
                .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"));
    }

    @Test
    @WithMockUser(username = "florist@test.com", roles = {"FLORIST"})
    void aVendorCanUpdateTheirOwnProfile() throws Exception {
        when(vendorService.updateOwnProfile(anyString(), any())).thenReturn(sampleProfile());

        mockMvc.perform(put("/api/v1/vendors/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validUpdateRequest())))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "florist@test.com", roles = {"FLORIST"})
    void theProfileIsResolvedFromTheJwtSubject() throws Exception {
        when(vendorService.getOwnProfile(anyString())).thenReturn(sampleProfile());

        mockMvc.perform(get("/api/v1/vendors/profile"))
                .andExpect(status().isOk());

        verify(vendorService).getOwnProfile("florist@test.com");
    }

    @Test
    @WithMockUser(username = "florist@test.com", roles = {"FLORIST"})
    void theUpdateRouteNeverAcceptsAVendorIdentifier() throws Exception {
        when(vendorService.updateOwnProfile(anyString(), any())).thenReturn(sampleProfile());

        mockMvc.perform(put("/api/v1/vendors/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .param("profileId", "99")
                        .param("id", "99")
                        .content(json(validUpdateRequest())))
                .andExpect(status().isOk());

        // Ownership cannot be redirected: the only argument carrying identity is
        // the authenticated email.
        verify(vendorService).updateOwnProfile(org.mockito.ArgumentMatchers.eq("florist@test.com"),
                any(VendorProfileUpdateRequest.class));
    }

    // ------------------------------------------------------ DTO-level validation

    @Test
    void registrationRejectsAMissingBusinessName() throws Exception {
        VendorRegisterRequest request = validRegisterRequest();
        request.setBusinessName("  ");

        mockMvc.perform(post("/api/v1/vendors/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.validation.businessName").exists());
    }

    @Test
    void registrationRejectsAMissingServiceLocation() throws Exception {
        VendorRegisterRequest request = validRegisterRequest();
        request.setServiceLocationId(null);

        mockMvc.perform(post("/api/v1/vendors/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validation.serviceLocationId").exists());
    }

    @Test
    void registrationRejectsAnInvalidEmail() throws Exception {
        VendorRegisterRequest request = validRegisterRequest();
        request.setEmail("not-an-email");

        mockMvc.perform(post("/api/v1/vendors/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validation.email").exists());
    }

    @Test
    void registrationRejectsAShortPassword() throws Exception {
        VendorRegisterRequest request = validRegisterRequest();
        request.setPassword("short");

        mockMvc.perform(post("/api/v1/vendors/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validation.password").exists());
    }

    @Test
    void registrationRejectsZeroPrepTime() throws Exception {
        VendorRegisterRequest request = validRegisterRequest();
        request.setPrepTimeMinutes(0);

        mockMvc.perform(post("/api/v1/vendors/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validation.prepTimeMinutes").exists());
    }

    @Test
    void registrationRejectsNegativePrepTime() throws Exception {
        VendorRegisterRequest request = validRegisterRequest();
        request.setPrepTimeMinutes(-5);

        mockMvc.perform(post("/api/v1/vendors/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validation.prepTimeMinutes").exists());
    }

    @Test
    void registrationRejectsANegativeFee() throws Exception {
        VendorRegisterRequest request = validRegisterRequest();
        request.setPerKmFee(new BigDecimal("-1.00"));

        mockMvc.perform(post("/api/v1/vendors/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validation.perKmFee").exists());
    }

    @Test
    void registrationRejectsAZeroDeliveryRadius() throws Exception {
        VendorRegisterRequest request = validRegisterRequest();
        request.setDeliveryRadiusKm(BigDecimal.ZERO);

        mockMvc.perform(post("/api/v1/vendors/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validation.deliveryRadiusKm").exists());
    }

    @Test
    void registrationRejectsAnOmittedClosedFlag() throws Exception {
        VendorRegisterRequest request = validRegisterRequest();
        request.setHours(List.of(VendorHoursRequest.builder()
                .weekday(DayOfWeek.MONDAY).openTime(LocalTime.of(9, 0))
                .closeTime(LocalTime.of(18, 0)).build()));

        mockMvc.perform(post("/api/v1/vendors/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validation['hours[0].closed']").exists());
    }

    @Test
    @WithMockUser(username = "florist@test.com", roles = {"FLORIST"})
    void updateRejectsZeroMaxOrdersPerSlot() throws Exception {
        VendorProfileUpdateRequest request = validUpdateRequest();
        request.setMaxOrdersPerSlot(0);

        mockMvc.perform(put("/api/v1/vendors/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validation.maxOrdersPerSlot").exists());
    }

    // ------------------------------------------------- service-level error mapping

    @Test
    @WithMockUser(username = "florist@test.com", roles = {"FLORIST"})
    void hoursContractViolationsAreReportedAsBadRequest() throws Exception {
        doThrow(BusinessException.badRequest("Opening hours for MONDAY must close after they open"))
                .when(vendorService).updateOwnProfile(anyString(), any());

        mockMvc.perform(put("/api/v1/vendors/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validUpdateRequest())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value(
                        "Opening hours for MONDAY must close after they open"))
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @WithMockUser(username = "florist@test.com", roles = {"FLORIST"})
    void aMissingProfileIsReportedAsNotFound() throws Exception {
        when(vendorService.getOwnProfile(anyString()))
                .thenThrow(BusinessException.notFound("No vendor profile exists for this account"));

        mockMvc.perform(get("/api/v1/vendors/profile"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void aDuplicateEmailIsReportedAsConflict() throws Exception {
        doThrow(BusinessException.conflict("Email already in use"))
                .when(vendorService).register(any());

        mockMvc.perform(post("/api/v1/vendors/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRegisterRequest())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void anUnknownServiceLocationIsReportedAsBadRequest() throws Exception {
        doThrow(BusinessException.badRequest("Unknown service location"))
                .when(vendorService).register(any());

        mockMvc.perform(post("/api/v1/vendors/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRegisterRequest())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Unknown service location"));
    }

    @Test
    void aMalformedBodyIsReportedAsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/vendors/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @WithMockUser(username = "florist@test.com", roles = {"FLORIST"})
    void theErrorResponseCarriesTheSharedEnvelopeShape() throws Exception {
        when(vendorService.getOwnProfile(anyString()))
                .thenThrow(BusinessException.notFound("No vendor profile exists for this account"));

        mockMvc.perform(get("/api/v1/vendors/profile"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").isString());
    }

    // ------------------------------------------------------------------- helpers

    private String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    private static VendorProfileResponse sampleProfile() {
        return VendorProfileResponse.builder()
                .id(1L)
                .ownerEmail("florist@test.com")
                .businessName("Test Blossoms")
                .status("PENDING_APPROVAL")
                .city("Bengaluru")
                .area("Koramangala")
                .pincode("560034")
                .deliveryRadiusKm(new BigDecimal("5.00"))
                .prepTimeMinutes(30)
                .hours(List.of())
                .build();
    }

    private static VendorRegisterRequest validRegisterRequest() {
        VendorRegisterRequest request = new VendorRegisterRequest();
        request.setEmail("florist@test.com");
        request.setPassword("Secret123");
        request.setFullName("Vendor Owner");
        request.setPhone("+919999999999");
        request.setBusinessName("Test Blossoms");
        request.setAddressLine1("12 Test Street");
        request.setServiceLocationId(1L);
        request.setPrepTimeMinutes(30);
        request.setSlotDurationMinutes(60);
        request.setMaxOrdersPerSlot(10);
        return request;
    }

    private static VendorProfileUpdateRequest validUpdateRequest() {
        VendorProfileUpdateRequest request = new VendorProfileUpdateRequest();
        request.setBusinessName("Test Blossoms");
        request.setAddressLine1("12 Test Street");
        request.setServiceLocationId(1L);
        return request;
    }
}
