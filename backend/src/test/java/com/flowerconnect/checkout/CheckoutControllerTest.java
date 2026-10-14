package com.flowerconnect.checkout.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowerconnect.checkout.dto.CheckoutItemQuote;
import com.flowerconnect.checkout.dto.CheckoutQuoteRequest;
import com.flowerconnect.checkout.dto.CheckoutQuoteResponse;
import com.flowerconnect.checkout.dto.CheckoutShopQuote;
import com.flowerconnect.checkout.service.CheckoutService;
import com.flowerconnect.config.TestClockConfig;
import com.flowerconnect.exception.BusinessException;
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

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice tests for {@link CheckoutController} — HTTP status, RBAC boundary,
 * request validation, and delegation to {@code CheckoutService}. The service is
 * mocked; integration-level behavior is covered by {@code CheckoutApiIntegrationTest}.
 */
@WebMvcTest(controllers = CheckoutController.class)
@Import({CheckoutControllerTest.TestSecurityConfig.class, TestClockConfig.class})
class CheckoutControllerTest {

    @TestConfiguration
    static class TestSecurityConfig {
        @Bean
        SecurityFilterChain testFilterChain(HttpSecurity http) throws Exception {
            http
                    .csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers("/api/v1/checkout/**").hasRole("CUSTOMER")
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
    private CheckoutService checkoutService;

    // ------------------------------------------------------- authorization

    @Test
    void quoteRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/checkout/quote")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(checkoutService);
    }

    @Test
    @WithMockUser(username = "vendor@test.com", roles = {"FLORIST"})
    void aFloristCannotUseCheckout() throws Exception {
        mockMvc.perform(post("/api/v1/checkout/quote")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(checkoutService);
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = {"ADMIN"})
    void anAdminCannotUseCheckout() throws Exception {
        mockMvc.perform(post("/api/v1/checkout/quote")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(checkoutService);
    }

    // ----------------------------------------------------------------- quote

    @Test
    @WithMockUser(username = "customer@test.com", roles = {"CUSTOMER"})
    void quoteReturns200() throws Exception {
        CheckoutQuoteResponse response = sampleQuote();
        when(checkoutService.generateQuote(anyString(), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/checkout/quote")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shops").isArray())
                .andExpect(jsonPath("$.total").exists())
                .andExpect(jsonPath("$.hasErrors").value(false))
                .andExpect(jsonPath("$.hasPriceChanges").value(false));

        verify(checkoutService).generateQuote(anyString(), any());
    }

    @Test
    @WithMockUser(username = "customer@test.com", roles = {"CUSTOMER"})
    void quoteRejectsMissingAddressId() throws Exception {
        String body = "{\"items\":[{\"vendorId\":1,\"slotStart\":\"2026-10-20T10:00:00\"}]}";

        mockMvc.perform(post("/api/v1/checkout/quote")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "customer@test.com", roles = {"CUSTOMER"})
    void quoteRejectsMissingItems() throws Exception {
        String body = "{\"addressId\":5}";

        mockMvc.perform(post("/api/v1/checkout/quote")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "customer@test.com", roles = {"CUSTOMER"})
    void quoteRejectsNegativeAddressId() throws Exception {
        String body = "{\"addressId\":-1,\"items\":[{\"vendorId\":1,\"slotStart\":\"2026-10-20T10:00:00\"}]}";

        mockMvc.perform(post("/api/v1/checkout/quote")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "customer@test.com", roles = {"CUSTOMER"})
    void quoteRejectsEmptyCart() throws Exception {
        doThrow(BusinessException.badRequest("Cart is empty"))
                .when(checkoutService).generateQuote(anyString(), any());

        mockMvc.perform(post("/api/v1/checkout/quote")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleRequest())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @WithMockUser(username = "customer@test.com", roles = {"CUSTOMER"})
    void quoteRejectsUnknownAddress() throws Exception {
        doThrow(BusinessException.notFound("Address not found"))
                .when(checkoutService).generateQuote(anyString(), any());

        mockMvc.perform(post("/api/v1/checkout/quote")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleRequest())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @WithMockUser(username = "customer@test.com", roles = {"CUSTOMER"})
    void quoteReturnsHasErrorsWhenVendorNotApproved() throws Exception {
        CheckoutQuoteResponse response = sampleQuote();
        response.setHasErrors(true);
        CheckoutShopQuote shop = response.getShops().get(0);
        shop.setErrors(List.of("Vendor is not approved"));
        when(checkoutService.generateQuote(anyString(), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/checkout/quote")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasErrors").value(true))
                .andExpect(jsonPath("$.shops[0].errors[0]").value("Vendor is not approved"));
    }

    @Test
    @WithMockUser(username = "customer@test.com", roles = {"CUSTOMER"})
    void quoteReturnsHasPriceChangesWhenPriceDrift() throws Exception {
        CheckoutQuoteResponse response = sampleQuote();
        response.setHasPriceChanges(true);
        CheckoutShopQuote shop = response.getShops().get(0);
        shop.setWarnings(List.of("price_changed_since_add_to_cart"));
        when(checkoutService.generateQuote(anyString(), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/checkout/quote")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(sampleRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasPriceChanges").value(true))
                .andExpect(jsonPath("$.shops[0].warnings[0]").value("price_changed_since_add_to_cart"));
    }

    // ----------------------------------------------------------------- helpers

    private CheckoutQuoteRequest sampleRequest() {
        return CheckoutQuoteRequest.builder()
                .addressId(5L)
                .items(List.of(CheckoutQuoteRequest.QuoteItem.builder()
                        .vendorId(3L)
                        .slotStart("2026-10-20T10:00:00")
                        .build()))
                .build();
    }

    private CheckoutQuoteResponse sampleQuote() {
        CheckoutItemQuote item = CheckoutItemQuote.builder()
                .productId(20L)
                .productName("Red Rose Bunch")
                .productSlug("red-rose-bunch")
                .quantity(2)
                .priceSnapshot(new BigDecimal("299.00"))
                .currentPrice(new BigDecimal("299.00"))
                .priceChanged(false)
                .subtotal(new BigDecimal("598.00"))
                .available(50)
                .purchasable(true)
                .notPurchasableReason(null)
                .build();

        CheckoutShopQuote shop = CheckoutShopQuote.builder()
                .vendorId(3L)
                .vendorName("Test Florist")
                .items(List.of(item))
                .subtotal(new BigDecimal("598.00"))
                .deliveryFee(new BigDecimal("45.00"))
                .platformFee(BigDecimal.ZERO)
                .minOrderAmount(new BigDecimal("200.00"))
                .total(new BigDecimal("643.00"))
                .distanceKm(new BigDecimal("3.00"))
                .withinRadius(true)
                .minOrderMet(true)
                .slotAvailable(true)
                .warnings(List.of())
                .errors(List.of())
                .build();

        return CheckoutQuoteResponse.builder()
                .shops(List.of(shop))
                .total(new BigDecimal("643.00"))
                .hasErrors(false)
                .hasPriceChanges(false)
                .build();
    }
}