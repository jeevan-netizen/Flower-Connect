package com.flowerconnect.payment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowerconnect.config.TestClockConfig;
import com.flowerconnect.payment.dto.PaymentGroupStateResponse;
import com.flowerconnect.payment.dto.PaymentResumeResponse;
import com.flowerconnect.payment.dto.PaymentStateResponse;
import com.flowerconnect.payment.service.PaymentService;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice tests for {@link PaymentController} — HTTP status and RBAC boundary.
 * The service is mocked; integration-level behavior (real DB, real tokens) is
 * covered by {@code OnlineCheckoutIntegrationTest}.
 */
@WebMvcTest(controllers = PaymentController.class)
@Import({PaymentControllerTest.TestSecurityConfig.class, TestClockConfig.class})
class PaymentControllerTest {

    @TestConfiguration
    static class TestSecurityConfig {
        @Bean
        SecurityFilterChain testFilterChain(HttpSecurity http) throws Exception {
            http
                    .csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers("/api/v1/payments/**").hasRole("CUSTOMER")
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
    private PaymentService paymentService;

    // ------------------------------------------------------- authorization

    @Test
    void getPaymentStateRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/payments/1"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(paymentService);
    }

    @Test
    @WithMockUser(username = "florist@test.com", roles = {"FLORIST"})
    void getPaymentStateRejectsFloristWith403() throws Exception {
        mockMvc.perform(get("/api/v1/payments/1"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(paymentService);
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = {"ADMIN"})
    void getPaymentStateRejectsAdminWith403() throws Exception {
        mockMvc.perform(get("/api/v1/payments/1"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(paymentService);
    }

    @Test
    @WithMockUser(username = "florist@test.com", roles = {"FLORIST"})
    void getPaymentGroupStateRejectsFloristWith403() throws Exception {
        mockMvc.perform(get("/api/v1/payments/group/g1"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(paymentService);
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = {"ADMIN"})
    void resumePaymentRejectsAdminWith403() throws Exception {
        mockMvc.perform(post("/api/v1/payments/1/resume"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(paymentService);
    }

    // ------------------------------------------------------- get payment state

    @Test
    @WithMockUser(username = "customer@test.com", roles = {"CUSTOMER"})
    void getPaymentStateReturns200() throws Exception {
        PaymentStateResponse state = PaymentStateResponse.builder()
                .orderId(100L)
                .paymentGroupId("group-1")
                .intentReference("intent_sim_abc")
                .method("ONLINE")
                .paymentStatus("PENDING")
                .orderStatus("PENDING_PAYMENT")
                .amount(java.math.BigDecimal.valueOf(643.00))
                .expiresAt("2026-10-20T10:30:00")
                .build();
        when(paymentService.fetchPaymentState(100L, "customer@test.com")).thenReturn(state);

        mockMvc.perform(get("/api/v1/payments/100")
                .principal(() -> "customer@test.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(100))
                .andExpect(jsonPath("$.method").value("ONLINE"))
                .andExpect(jsonPath("$.paymentStatus").value("PENDING"))
                .andExpect(jsonPath("$.orderStatus").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.paymentGroupId").value("group-1"))
                .andExpect(jsonPath("$.intentReference").value("intent_sim_abc"));

        verify(paymentService).fetchPaymentState(100L, "customer@test.com");
    }

    // ------------------------------------------------------- get group state

    @Test
    @WithMockUser(username = "customer@test.com", roles = {"CUSTOMER"})
    void getPaymentGroupStateReturns200() throws Exception {
        PaymentGroupStateResponse state = PaymentGroupStateResponse.builder()
                .paymentGroupId("group-1")
                .intentReference("intent_sim_abc")
                .status("CREATED")
                .total(java.math.BigDecimal.valueOf(643.00))
                .expiresAt("2026-10-20T10:30:00")
                .orders(java.util.List.of())
                .build();
        when(paymentService.fetchGroupState("group-1", "customer@test.com")).thenReturn(state);

        mockMvc.perform(get("/api/v1/payments/group/group-1")
                .principal(() -> "customer@test.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentGroupId").value("group-1"))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.orders").isArray());

        verify(paymentService).fetchGroupState("group-1", "customer@test.com");
    }

    // ------------------------------------------------------- resume payment

    @Test
    @WithMockUser(username = "customer@test.com", roles = {"CUSTOMER"})
    void resumePaymentReturns200() throws Exception {
        PaymentResumeResponse result = PaymentResumeResponse.builder()
                .orderId(100L)
                .orderStatus("PLACED")
                .paymentStatus("PAID")
                .paymentGroupId("group-1")
                .intentReference("intent_sim_abc")
                .paidAt("2026-10-20T10:00:00")
                .build();
        when(paymentService.resumePayment(100L, "customer@test.com")).thenReturn(result);

        mockMvc.perform(post("/api/v1/payments/100/resume")
                .principal(() -> "customer@test.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(100))
                .andExpect(jsonPath("$.orderStatus").value("PLACED"))
                .andExpect(jsonPath("$.paymentStatus").value("PAID"));

        verify(paymentService).resumePayment(100L, "customer@test.com");
    }

    @Test
    @WithMockUser(username = "customer@test.com", roles = {"CUSTOMER"})
    void resumePaymentRejectsUnknownOrder() throws Exception {
        com.flowerconnect.exception.BusinessException be =
                new com.flowerconnect.exception.BusinessException(
                        com.flowerconnect.exception.ErrorCode.NOT_FOUND, "Order not found: 999");
        doThrow(be).when(paymentService).resumePayment(anyLong(), anyString());

        mockMvc.perform(post("/api/v1/payments/999/resume")
                .principal(() -> "customer@test.com"))
                .andExpect(status().isNotFound());
    }
}
