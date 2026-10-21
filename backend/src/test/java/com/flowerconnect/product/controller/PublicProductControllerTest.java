package com.flowerconnect.product.controller;

import com.flowerconnect.config.TestClockConfig;
import com.flowerconnect.product.service.PublicProductService;
import com.flowerconnect.search.dto.PublicProductResponse;
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
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@TestConfiguration
class TestSecurityConfigForPublicProduct {
    @Bean
    SecurityFilterChain testFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/products/{id}").permitAll()
                        .anyRequest().permitAll())
                .httpBasic(Customizer.withDefaults());
        return http.build();
    }
}

@WebMvcTest(controllers = PublicProductController.class)
@Import({TestSecurityConfigForPublicProduct.class, TestClockConfig.class})
@ActiveProfiles("test")
class PublicProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PublicProductService publicProductService;

    @Test
    void shouldReturnProductDetail() throws Exception {
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
                .available(8)
                .images(List.of())
                .build();

        when(publicProductService.productDetail(eq(100L), eq(1L))).thenReturn(product);

        mockMvc.perform(get("/api/v1/products/100")
                        .param("locationId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Red Roses"))
                .andExpect(jsonPath("$.vendorName").value("Approved Florist"))
                .andExpect(jsonPath("$.available").value(8));
    }

    @Test
    void shouldRejectMissingLocationId() throws Exception {
        mockMvc.perform(get("/api/v1/products/100"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void shouldBeAccessibleWithoutAuthentication() throws Exception {
        when(publicProductService.productDetail(any(), any())).thenReturn(
                PublicProductResponse.builder()
                        .id(100L)
                        .name("Red Roses")
                        .available(8)
                        .build());

        mockMvc.perform(get("/api/v1/products/100")
                        .param("locationId", "1"))
                .andExpect(status().isOk());
    }
}
