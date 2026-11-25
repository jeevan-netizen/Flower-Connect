package com.flowerconnect.farmer.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowerconnect.domain.VendorProfile;
import com.flowerconnect.farmer.dto.FarmerDepositRequest;
import com.flowerconnect.farmer.dto.FarmerDepositResponse;
import com.flowerconnect.farmer.service.FarmerDepositService;
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
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Web-layer coverage for the farmer deposit endpoint: the authorization rules
 * declared in the production {@code SecurityConfig}, HTTP status codes, and the
 * validation/error response shape.
 */
@WebMvcTest(controllers = FarmerDepositController.class)
@Import({FarmerDepositControllerTest.TestSecurityConfig.class, com.flowerconnect.config.TestClockConfig.class})
class FarmerDepositControllerTest {

    @TestConfiguration
    static class TestSecurityConfig {
        @Bean
        SecurityFilterChain testFilterChain(HttpSecurity http) throws Exception {
            http
                    .csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers(HttpMethod.POST, "/api/v1/vendor/farmer-links/**/deposits")
                            .hasRole("FLORIST")
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
    private FarmerDepositService farmerDepositService;

    @MockBean
    private VendorProfileRepository vendorProfileRepository;

    @BeforeEach
    void setUp() {
        VendorProfile vendorProfile = VendorProfile.builder().id(10L).build();
        when(vendorProfileRepository.findByUserEmail("florist@test.com"))
                .thenReturn(Optional.of(vendorProfile));
    }

    private String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    // ------------------------------------------------------------- authorization

    @Test
    void recordDepositRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/vendor/farmer-links/100/deposits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "customer@test.com", roles = {"CUSTOMER"})
    void aCustomerCannotRecordDeposit() throws Exception {
        mockMvc.perform(post("/api/v1/vendor/farmer-links/100/deposits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest())))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = {"ADMIN"})
    void anAdminCannotRecordDeposit() throws Exception {
        mockMvc.perform(post("/api/v1/vendor/farmer-links/100/deposits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest())))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "farmer@test.com", roles = {"FARMER"})
    void aFarmerCannotRecordDeposit() throws Exception {
        mockMvc.perform(post("/api/v1/vendor/farmer-links/100/deposits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest())))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "florist@test.com", roles = {"FLORIST"})
    void aFloristCanRecordDeposit() throws Exception {
        when(farmerDepositService.recordDeposit(anyLong(), anyLong(), any()))
                .thenReturn(FarmerDepositResponse.builder()
                        .id(500L).linkId(100L).flowerType("Roses")
                        .grossKg(new BigDecimal("32.00")).deductionKg(new BigDecimal("3.00"))
                        .netKg(new BigDecimal("29.00")).pricePerKg(new BigDecimal("100.00"))
                        .amount(new BigDecimal("2900.00"))
                        .depositedAt(LocalDateTime.parse("2026-01-15T10:00:00"))
                        .build());

        mockMvc.perform(post("/api/v1/vendor/farmer-links/100/deposits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(500))
                .andExpect(jsonPath("$.linkId").value(100))
                .andExpect(jsonPath("$.flowerType").value("Roses"))
                .andExpect(jsonPath("$.amount").exists())
                .andExpect(jsonPath("$.deductionKg").exists())
                .andExpect(jsonPath("$.netKg").exists());
    }

    @Test
    @WithMockUser(username = "florist@test.com", roles = {"FLORIST"})
    void recordDepositRejectsInvalidBody() throws Exception {
        FarmerDepositRequest request = new FarmerDepositRequest();
        request.setGrossKg(new BigDecimal("-5"));
        request.setPricePerKg(new BigDecimal("0"));

        mockMvc.perform(post("/api/v1/vendor/farmer-links/100/deposits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @WithMockUser(username = "florist@test.com", roles = {"FLORIST"})
    void recordDepositRejectsNonPositiveGrossKg() throws Exception {
        FarmerDepositRequest request = validRequest();
        request.setGrossKg(BigDecimal.ZERO);

        mockMvc.perform(post("/api/v1/vendor/farmer-links/100/deposits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @WithMockUser(username = "florist@test.com", roles = {"FLORIST"})
    void recordDepositRejectsMissingFlowerType() throws Exception {
        FarmerDepositRequest request = validRequest();
        request.setFlowerType("");

        mockMvc.perform(post("/api/v1/vendor/farmer-links/100/deposits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @WithMockUser(username = "florist@test.com", roles = {"FLORIST"})
    void recordDepositRejectsTooLongFlowerType() throws Exception {
        FarmerDepositRequest request = validRequest();
        request.setFlowerType("a".repeat(101));

        mockMvc.perform(post("/api/v1/vendor/farmer-links/100/deposits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @WithMockUser(username = "florist@test.com", roles = {"FLORIST"})
    void recordDepositRejectsFutureDepositedAt() throws Exception {
        // Far-future date is in the future relative to both the system clock (used by
        // @PastOrPresent validation) and the injected test clock.
        FarmerDepositRequest request = validRequest();
        request.setDepositedAt(LocalDateTime.parse("2099-01-01T00:00:00"));

        mockMvc.perform(post("/api/v1/vendor/farmer-links/100/deposits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    // ------------------------------------------------------------ helpers

    private static FarmerDepositRequest validRequest() {
        FarmerDepositRequest request = new FarmerDepositRequest();
        request.setFlowerType("Roses");
        request.setGrossKg(new BigDecimal("32"));
        request.setPricePerKg(new BigDecimal("100"));
        request.setDepositedAt(LocalDateTime.parse("2026-01-15T10:00:00"));
        return request;
    }
}
