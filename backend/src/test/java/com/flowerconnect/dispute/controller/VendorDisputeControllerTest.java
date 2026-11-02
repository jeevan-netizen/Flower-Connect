package com.flowerconnect.dispute.controller;

import com.flowerconnect.dispute.service.DisputeService;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer coverage for {@link VendorDisputeController}: HTTP status, RBAC
 * boundary, request validation, and delegation to {@link DisputeService}.
 */
@WebMvcTest(controllers = VendorDisputeController.class)
@Import({VendorDisputeControllerTest.TestSecurityConfig.class,
        com.flowerconnect.config.TestClockConfig.class})
class VendorDisputeControllerTest {

    @TestConfiguration
    static class TestSecurityConfig {
        @Bean
        SecurityFilterChain testFilterChain(HttpSecurity http) throws Exception {
            http
                    .csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers("/api/v1/vendor/disputes/**").hasRole("FLORIST")
                            .anyRequest().authenticated())
                    .httpBasic(Customizer.withDefaults());
            return http.build();
        }
    }

    private static final String FLORIST = "vendor@test.com";
    private static final String BASE = "/api/v1/vendor/disputes";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DisputeService disputeService;

    // ------------------------------------------------------- validation

    @Test
    @WithMockUser(username = FLORIST, roles = "FLORIST")
    void negativeDisputeIdIsRejected() throws Exception {
        mockMvc.perform(get(BASE + "/-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(disputeService);
    }

    @Test
    @WithMockUser(username = FLORIST, roles = "FLORIST")
    void zeroDisputeIdIsRejected() throws Exception {
        mockMvc.perform(get(BASE + "/0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(disputeService);
    }

    @Test
    @WithMockUser(username = FLORIST, roles = "FLORIST")
    void negativeDisputeIdOnRespondIsRejected() throws Exception {
        when(disputeService.respondToDispute(anyString(), anyLong(), any()))
                .thenReturn(null);

        mockMvc.perform(post(BASE + "/-3/respond")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"response\":\"looks wrong\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(disputeService);
    }

    // ------------------------------------------------------- RBAC

    @Test
    void disputeRoutesRequireAuthentication() throws Exception {
        mockMvc.perform(get(BASE + "/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "customer@test.com", roles = "CUSTOMER")
    void aCustomerCannotAccessVendorDisputes() throws Exception {
        mockMvc.perform(get(BASE + "/1"))
                .andExpect(status().isForbidden());
    }
}
