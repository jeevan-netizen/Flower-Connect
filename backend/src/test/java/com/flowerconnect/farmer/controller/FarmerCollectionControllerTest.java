package com.flowerconnect.farmer.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowerconnect.config.TestClockConfig;
import com.flowerconnect.domain.Role;
import com.flowerconnect.domain.User;
import com.flowerconnect.farmer.dto.FarmerCollectionRequest;
import com.flowerconnect.farmer.dto.FarmerCollectionResponse;
import com.flowerconnect.farmer.dto.PassbookResponse;
import com.flowerconnect.farmer.service.FarmerCollectionService;
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
 * Web-layer coverage for the farmer collection API (Stage 3 of the farmer
 * module): authorization, HTTP status codes, validation and the shared error
 * envelope.
 */
@WebMvcTest(controllers = FarmerCollectionController.class)
@Import({FarmerCollectionControllerTest.TestSecurityConfig.class, TestClockConfig.class})
class FarmerCollectionControllerTest {

    @TestConfiguration
    static class TestSecurityConfig {
        @Bean
        SecurityFilterChain testFilterChain(HttpSecurity http) throws Exception {
            http
                    .csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers("/api/v1/farmer/links/**").hasRole("FARMER")
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
    private UserRepository userRepository;

    private User farmer;

    @BeforeEach
    void setUp() {
        farmer = User.builder().id(1L).email("farmer@test.com").role(Role.builder().name("FARMER").build()).build();
        when(userRepository.findByEmailWithRole("farmer@test.com")).thenReturn(Optional.of(farmer));
    }

    // ------------------------------------------------------------- authorization

    @Test
    void passbookRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/farmer/links/100/passbook"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "customer@test.com", roles = {"CUSTOMER"})
    void aCustomerCannotReadPassbook() throws Exception {
        mockMvc.perform(get("/api/v1/farmer/links/100/passbook"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = {"ADMIN"})
    void anAdminCannotReadPassbook() throws Exception {
        mockMvc.perform(get("/api/v1/farmer/links/100/passbook"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "florist@test.com", roles = {"FLORIST"})
    void aFloristCannotReadPassbook() throws Exception {
        mockMvc.perform(get("/api/v1/farmer/links/100/passbook"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "farmer@test.com", roles = {"FARMER"})
    void aFarmerCanReadPassbook() throws Exception {
        when(farmerCollectionService.getPassbook(anyLong(), anyLong(), anyString()))
                .thenReturn(PassbookResponse.builder()
                        .linkId(100L).outstandingBalance(new BigDecimal("1300.00"))
                        .entries(List.of()).build());

        mockMvc.perform(get("/api/v1/farmer/links/100/passbook"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outstandingBalance").value(1300.0));
    }

    @Test
    void requestCollectionRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/farmer/links/100/collections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "customer@test.com", roles = {"CUSTOMER"})
    void aCustomerCannotRequestCollection() throws Exception {
        mockMvc.perform(post("/api/v1/farmer/links/100/collections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "farmer@test.com", roles = {"FARMER"})
    void aFarmerCanRequestCollection() throws Exception {
        when(farmerCollectionService.requestCollection(anyLong(), anyLong(), any()))
                .thenReturn(FarmerCollectionResponse.builder()
                        .id(500L).linkId(100L).amount(new BigDecimal("300.00"))
                        .mode(FarmerCollectionResponse.Mode.UPI)
                        .status(FarmerCollectionResponse.Status.PENDING)
                        .requestedAt(LocalDateTime.parse("2026-01-15T10:00:00")).build());

        mockMvc.perform(post("/api/v1/farmer/links/100/collections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(500))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @WithMockUser(username = "farmer@test.com", roles = {"FARMER"})
    void requestCollectionRejectsInvalidBody() throws Exception {
        FarmerCollectionRequest request = new FarmerCollectionRequest();
        request.setAmount(BigDecimal.ZERO);
        request.setMode(null);

        mockMvc.perform(post("/api/v1/farmer/links/100/collections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    // ------------------------------------------------------------ helpers

    // JUnit 5 does not run a method named setUp() unless annotated; wire the mock
    // stubbing directly in each test that needs it instead of a broken hook.
    static FarmerCollectionRequest validRequest() {
        FarmerCollectionRequest request = new FarmerCollectionRequest();
        request.setAmount(new BigDecimal("300.00"));
        request.setMode(FarmerCollectionRequest.Mode.UPI);
        request.setNote("Weekly collection");
        return request;
    }
}
