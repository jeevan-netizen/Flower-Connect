package com.flowerconnect.farmer.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowerconnect.config.TestClockConfig;
import com.flowerconnect.domain.Role;
import com.flowerconnect.domain.User;
import com.flowerconnect.farmer.dto.FarmerLinkRequest;
import com.flowerconnect.farmer.dto.FarmerLinkResponse;
import com.flowerconnect.farmer.dto.FarmerRegisterRequest;
import com.flowerconnect.farmer.dto.FarmerRegisterResponse;
import com.flowerconnect.farmer.dto.FarmerVendorResponse;
import com.flowerconnect.farmer.service.FarmerLinkService;
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

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Web-layer coverage for the farmer API (Stage 1 of the farmer module): the
 * authorization rules declared in the production {@code SecurityConfig}, HTTP
 * status codes, and the error response shape produced by the shared
 * {@code GlobalExceptionHandler}.
 */
@WebMvcTest(controllers = FarmerLinkController.class)
@Import({FarmerLinkControllerTest.TestSecurityConfig.class, TestClockConfig.class})
class FarmerLinkControllerTest {

    @TestConfiguration
    static class TestSecurityConfig {
        @Bean
        SecurityFilterChain testFilterChain(HttpSecurity http) throws Exception {
            http
                    .csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers(HttpMethod.POST, "/api/v1/farmer/register").permitAll()
                            .requestMatchers("/api/v1/farmer/**").hasRole("FARMER")
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
    private UserRepository userRepository;

    private User farmer;

    @BeforeEach
    void setUp() {
        farmer = User.builder()
                .id(1L)
                .email("farmer@test.com")
                .role(Role.builder().id(4L).name("FARMER").build())
                .build();
        when(userRepository.findByEmailWithRole("farmer@test.com"))
                .thenReturn(Optional.of(farmer));
    }

    private String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    // ------------------------------------------------------------- authorization

    @Test
    void registrationIsReachableWithoutAuthentication() throws Exception {
        FarmerRegisterResponse response = FarmerRegisterResponse.builder()
                .id(1L).email("farmer@test.com").fullName("Farm Owner").role("FARMER").build();
        when(farmerLinkService.register(any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/farmer/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRegisterRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("FARMER"));
    }

    @Test
    void registrationRejectsInvalidBody() throws Exception {
        mockMvc.perform(post("/api/v1/farmer/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void listEligibleVendorsRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/farmer/vendors"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "customer@test.com", roles = {"CUSTOMER"})
    void aCustomerCannotListEligibleVendors() throws Exception {
        mockMvc.perform(get("/api/v1/farmer/vendors"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = {"ADMIN"})
    void anAdminCannotListEligibleVendors() throws Exception {
        mockMvc.perform(get("/api/v1/farmer/vendors"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "florist@test.com", roles = {"FLORIST"})
    void aFloristCannotListEligibleVendors() throws Exception {
        mockMvc.perform(get("/api/v1/farmer/vendors"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "farmer@test.com", roles = {"FARMER"})
    void aFarmerCanListEligibleVendors() throws Exception {
        when(farmerLinkService.listEligibleVendors(anyLong(), isNull(String.class)))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/farmer/vendors"))
                .andExpect(status().isOk());
    }

    @Test
    void requestLinkRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/farmer/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validLinkRequest())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "customer@test.com", roles = {"CUSTOMER"})
    void aCustomerCannotRequestLink() throws Exception {
        mockMvc.perform(post("/api/v1/farmer/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validLinkRequest())))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "farmer@test.com", roles = {"FARMER"})
    void aFarmerCanRequestLink() throws Exception {
        when(farmerLinkService.requestLink(anyLong(), any()))
                .thenReturn(FarmerLinkResponse.builder()
                        .id(100L).vendorId(10L).vendorName("Green Acres")
                        .status("PENDING").build());

        mockMvc.perform(post("/api/v1/farmer/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validLinkRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @WithMockUser(username = "farmer@test.com", roles = {"FARMER"})
    void requestLinkRejectsMissingVendorId() throws Exception {
        FarmerLinkRequest request = new FarmerLinkRequest();
        request.setVendorId(null);

        mockMvc.perform(post("/api/v1/farmer/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.validation.vendorId").exists());
    }

    @Test
    void listFarmerLinksRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/farmer/links"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "customer@test.com", roles = {"CUSTOMER"})
    void aCustomerCannotListFarmerLinks() throws Exception {
        mockMvc.perform(get("/api/v1/farmer/links"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "farmer@test.com", roles = {"FARMER"})
    void aFarmerCanListFarmerLinks() throws Exception {
        when(farmerLinkService.listFarmerLinks(anyLong())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/farmer/links"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "farmer@test.com", roles = {"FARMER"})
    void theErrorResponseCarriesTheSharedEnvelopeShape() throws Exception {
        when(farmerLinkService.listEligibleVendors(anyLong(), isNull(String.class)))
                .thenThrow(new com.flowerconnect.exception.BusinessException(
                        com.flowerconnect.exception.ErrorCode.NOT_FOUND, "Farmer not found"));

        mockMvc.perform(get("/api/v1/farmer/vendors"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.status").value(404));
    }

    // ------------------------------------------------------------ helpers

    private static FarmerRegisterRequest validRegisterRequest() {
        FarmerRegisterRequest request = new FarmerRegisterRequest();
        request.setEmail("farmer@test.com");
        request.setPassword("Secret123");
        request.setFullName("Farm Owner");
        request.setPhone("+919999999999");
        return request;
    }

    private static FarmerLinkRequest validLinkRequest() {
        FarmerLinkRequest request = new FarmerLinkRequest();
        request.setVendorId(10L);
        return request;
    }
}
