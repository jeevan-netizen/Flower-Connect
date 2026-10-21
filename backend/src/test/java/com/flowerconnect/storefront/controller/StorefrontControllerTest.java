package com.flowerconnect.storefront.controller;

import com.flowerconnect.config.TestClockConfig;
import com.flowerconnect.geo.dto.PageResponse;
import com.flowerconnect.search.dto.PublicProductResponse;
import com.flowerconnect.storefront.dto.StorefrontHour;
import com.flowerconnect.storefront.dto.StorefrontResponse;
import com.flowerconnect.storefront.service.StorefrontService;
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
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@TestConfiguration
class TestSecurityConfigForStorefront {
    @Bean
    SecurityFilterChain testFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/api/v1/vendors/{id}/storefront").permitAll()
                        .anyRequest().permitAll())
                .httpBasic(Customizer.withDefaults());
        return http.build();
    }
}

@WebMvcTest(controllers = StorefrontController.class)
@Import({TestSecurityConfigForStorefront.class, TestClockConfig.class})
@ActiveProfiles("test")
class StorefrontControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StorefrontService storefrontService;

    @Test
    void shouldReturnStorefront() throws Exception {
        PublicProductResponse product = PublicProductResponse.builder()
                .id(100L)
                .vendorId(1L)
                .vendorName("Approved Florist")
                .categoryId(1L)
                .categoryName("Roses")
                .name("Red Roses")
                .slug("red-roses")
                .description("Fresh red roses")
                .basePrice(new BigDecimal("199.00"))
                .available(10)
                .images(List.of())
                .build();

        PageResponse<PublicProductResponse> productPage = PageResponse.<PublicProductResponse>builder()
                .content(List.of(product))
                .page(0)
                .size(20)
                .totalElements(1)
                .totalPages(1)
                .first(true)
                .last(true)
                .empty(false)
                .build();

        StorefrontResponse response = StorefrontResponse.builder()
                .id(1L)
                .businessName("Approved Florist")
                .description("A lovely florist")
                .city("Bengaluru")
                .area("Koramangala")
                .pincode("560034")
                .deliveryRadiusKm(new BigDecimal("5.00"))
                .minOrderAmount(new BigDecimal("100.00"))
                .baseDeliveryFee(new BigDecimal("10.00"))
                .perKmFee(new BigDecimal("2.00"))
                .freeDeliveryAbove(new BigDecimal("500.00"))
                .prepTimeMinutes(30)
                .avgRating(new BigDecimal("4.50"))
                .reviewCount(10)
                .acceptingOrders(true)
                .hours(List.of(StorefrontHour.builder()
                        .weekday("MONDAY")
                        .openTime(LocalTime.of(9, 0))
                        .closeTime(LocalTime.of(18, 0))
                        .closed(false)
                        .build()))
                .products(com.flowerconnect.storefront.dto.StorefrontProductPage.from(productPage))
                .build();

        when(storefrontService.storefront(eq(1L), any(), any())).thenReturn(response);

        mockMvc.perform(get("/api/v1/vendors/1/storefront"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessName").value("Approved Florist"))
                .andExpect(jsonPath("$.acceptingOrders").value(true))
                .andExpect(jsonPath("$.hours[0].weekday").value("MONDAY"))
                .andExpect(jsonPath("$.products.content[0].name").value("Red Roses"))
                .andExpect(jsonPath("$.products.totalElements").value(1));
    }

    @Test
    void shouldBeAccessibleWithoutAuthentication() throws Exception {
        when(storefrontService.storefront(any(), any(), any())).thenReturn(
                StorefrontResponse.builder()
                        .id(1L)
                        .businessName("Approved Florist")
                        .acceptingOrders(true)
                        .build());

        mockMvc.perform(get("/api/v1/vendors/1/storefront"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldRejectInvalidPageParameter() throws Exception {
        when(storefrontService.storefront(any(), any(), any())).thenReturn(
                StorefrontResponse.builder()
                        .id(1L)
                        .businessName("Approved Florist")
                        .acceptingOrders(true)
                        .build());

        mockMvc.perform(get("/api/v1/vendors/1/storefront")
                        .param("page", "-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectInvalidSizeParameterTooLarge() throws Exception {
        when(storefrontService.storefront(any(), any(), any())).thenReturn(
                StorefrontResponse.builder()
                        .id(1L)
                        .businessName("Approved Florist")
                        .acceptingOrders(true)
                        .build());

        mockMvc.perform(get("/api/v1/vendors/1/storefront")
                        .param("size", "101"))
                .andExpect(status().isBadRequest());
    }
}
