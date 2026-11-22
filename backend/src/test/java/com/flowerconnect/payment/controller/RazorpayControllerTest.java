package com.flowerconnect.payment.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowerconnect.config.AppProperties;
import com.flowerconnect.exception.BusinessException;
import com.flowerconnect.exception.ErrorCode;
import com.flowerconnect.payment.service.RazorpayService;
import com.flowerconnect.payment.service.RazorpayService.RazorpayWebhookResult;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
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

@WebMvcTest(RazorpayController.class)
@Import({RazorpayControllerTest.TestSecurityConfig.class, com.flowerconnect.config.TestClockConfig.class})
class RazorpayControllerTest {

    @TestConfiguration
    static class TestSecurityConfig {
        @Bean
        SecurityFilterChain testFilterChain(HttpSecurity http) throws Exception {
            http
                    .csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers(HttpMethod.POST, "/api/v1/payments/razorpay/webhook").permitAll()
                            .requestMatchers("/api/v1/payments/**").hasRole("CUSTOMER")
                            .anyRequest().authenticated())
                    .httpBasic(Customizer.withDefaults());
            return http.build();
        }
    }

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private RazorpayService razorpayService;
    @MockBean
    private AppProperties appProperties;

    @Test
    @WithMockUser(username = "customer@test.com", roles = "CUSTOMER")
    void getKeyReturnsRazorpayKeyId() throws Exception {
        when(appProperties.getRazorpayKeyId()).thenReturn("rzp_test_1234567890");

        mvc.perform(get("/api/v1/payments/razorpay/key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keyId").value("rzp_test_1234567890"));
    }

    @Test
    @WithMockUser(username = "customer@test.com", roles = "CUSTOMER")
    void getKeyReturns404WhenNotConfigured() throws Exception {
        when(appProperties.getRazorpayKeyId()).thenReturn(null);

        mvc.perform(get("/api/v1/payments/razorpay/key"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "customer@test.com", roles = "CUSTOMER")
    void verifyReturnsUpdatedPaymentState() throws Exception {
        when(razorpayService.verifyCheckout(
                eq(500L), eq("order_test_123"), eq("pay_test_456"), eq("sig_abc"), eq("customer@test.com")))
                .thenReturn(new RazorpayService.RazorpayVerifyResult(
                        500L, "PLACED", "PAID", "group_abc"));

        String body = objectMapper.writeValueAsString(new RazorpayController.RazorpayVerifyRequest(
                "order_test_123", "pay_test_456", "sig_abc"));

        mvc.perform(post("/api/v1/payments/razorpay/500/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(500))
                .andExpect(jsonPath("$.orderStatus").value("PLACED"))
                .andExpect(jsonPath("$.paymentStatus").value("PAID"))
                .andExpect(jsonPath("$.paymentGroupId").value("group_abc"));
    }

    @Test
    void verifyReturns401WhenUnauthenticated() throws Exception {
        mvc.perform(post("/api/v1/payments/razorpay/500/verify"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void webhookReturnsAcceptedForValidSignature() throws Exception {
        when(appProperties.getRazorpayWebhookSecret()).thenReturn("wh_secret_at_least_32_characters_long_here");
        when(appProperties.getWebhookMaxBodyBytes()).thenReturn(65536);
        when(razorpayService.processWebhook(any(), any()))
                .thenReturn(RazorpayWebhookResult.accepted("evt_1"));

        byte[] body = "{\"id\":\"evt_1\",\"event\":\"payment.captured\"}".getBytes(StandardCharsets.UTF_8);
        String sig = testHmacSha256("wh_secret_at_least_32_characters_long_here", body);

        mvc.perform(post("/api/v1/payments/razorpay/webhook")
                .header("X-Razorpay-Signature", sig)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("accepted"))
                .andExpect(jsonPath("$.providerEventId").value("evt_1"));
    }

    @Test
    void webhookReturns401ForInvalidSignature() throws Exception {
        when(appProperties.getRazorpayWebhookSecret()).thenReturn("wh_secret_at_least_32_characters_long_here");
        when(appProperties.getWebhookMaxBodyBytes()).thenReturn(65536);
        doThrow(new BusinessException(ErrorCode.UNAUTHORIZED, "Invalid Razorpay webhook signature"))
                .when(razorpayService).processWebhook(any(), any());

        mvc.perform(post("/api/v1/payments/razorpay/webhook")
                .header("X-Razorpay-Signature", "bad_sig")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void webhookReturnsAcceptedForDuplicate() throws Exception {
        when(appProperties.getRazorpayWebhookSecret()).thenReturn("wh_secret_at_least_32_characters_long_here");
        when(appProperties.getWebhookMaxBodyBytes()).thenReturn(65536);
        when(razorpayService.processWebhook(any(), any()))
                .thenReturn(RazorpayWebhookResult.duplicate("evt_dup"));

        byte[] body = "{\"id\":\"evt_dup\"}".getBytes(StandardCharsets.UTF_8);
        String sig = testHmacSha256("wh_secret_at_least_32_characters_long_here", body);

        mvc.perform(post("/api/v1/payments/razorpay/webhook")
                .header("X-Razorpay-Signature", sig)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("duplicate"))
                .andExpect(jsonPath("$.providerEventId").value("evt_dup"));
    }

    private static String testHmacSha256(String key, byte[] payload) throws Exception {
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec(
                key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] hash = mac.doFinal(payload);
        StringBuilder sb = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
