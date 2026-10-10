package com.flowerconnect.storefront.controller;

import com.flowerconnect.config.TestClockConfig;
import com.flowerconnect.geo.dto.PageResponse;
import com.flowerconnect.storefront.dto.StorefrontProductResponse;
import com.flowerconnect.storefront.dto.StorefrontResponse;
import com.flowerconnect.storefront.dto.StorefrontVendorResponse;
import com.flowerconnect.storefront.service.StorefrontService;
import com.flowerconnect.vendor.dto.VendorHoursResponse;
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
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller slice for {@link StorefrontController} (plan task 4.5): the
 * response contract, the parameter validation, and the fact that the public
 * exception and the FLORIST namespace rule remain two separate matchers.
 *
 * <p>The test filter chain below deliberately reproduces the two production
 * {@code SecurityConfig} matchers for this path rather than opening the whole
 * chain up, so the slice still asserts the rule it is named after. The
 * authoritative proof is {@code StorefrontControllerIntegrationTest}, which
 * runs the same requests through the real filter chain.
 */
@TestConfiguration
class TestSecurityConfigForStorefront {

    @Bean
    SecurityFilterChain testFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/api/v1/vendors/*/storefront").permitAll()
                        .requestMatchers("/api/v1/vendors/**").hasRole("FLORIST")
                        .anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults());
        return http.build();
    }
}

@WebMvcTest(controllers = StorefrontController.class)
@Import({TestSecurityConfigForStorefront.class, TestClockConfig.class})
@ActiveProfiles("test")
class StorefrontControllerTest {

    private static final String STOREFRONT_PATH = "/api/v1/vendors/7/storefront";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StorefrontService storefrontService;

    private static StorefrontResponse sampleResponse() {
        StorefrontVendorResponse vendor = StorefrontVendorResponse.builder()
                .id(7L)
                .businessName("Koramangala Florist")
                .description("Neighbourhood flower shop")
                .logoUrl("https://example.test/logo.png")
                .city("Bengaluru")
                .area("Koramangala")
                .pincode("560034")
                .deliveryRadiusKm(new BigDecimal("5.00"))
                .minOrderAmount(new BigDecimal("199.00"))
                .baseDeliveryFee(new BigDecimal("39.00"))
                .perKmFee(new BigDecimal("12.00"))
                .freeDeliveryAbove(new BigDecimal("799.00"))
                .prepTimeMinutes(45)
                .slotDurationMinutes(60)
                .maxOrdersPerSlot(10)
                .acceptingOrders(true)
                .reviewCount(0)
                .hours(List.of(VendorHoursResponse.builder()
                        .weekday(DayOfWeek.MONDAY)
                        .openTime(LocalTime.of(9, 0))
                        .closeTime(LocalTime.of(18, 0))
                        .closed(false)
                        .build()))
                .build();

        PageResponse<StorefrontProductResponse> products = PageResponse.<StorefrontProductResponse>builder()
                .content(List.of(StorefrontProductResponse.builder()
                        .id(101L)
                        .categoryId(5L)
                        .categoryName("Roses")
                        .name("Red Rose Bunch")
                        .slug("red-rose-bunch")
                        .description("A dozen red roses")
                        .basePrice(new BigDecimal("249.00"))
                        .inStock(true)
                        .build()))
                .page(0)
                .size(20)
                .totalElements(1)
                .totalPages(1)
                .first(true)
                .last(true)
                .empty(false)
                .build();

        return StorefrontResponse.builder().vendor(vendor).products(products).build();
    }

    // ------------------------------------------------------------ the 2xx shape

    @Test
    void returnsTheStorefrontWithVendorAndPaginatedProducts() throws Exception {
        when(storefrontService.storefront(any(), any(), any())).thenReturn(sampleResponse());

        mockMvc.perform(get(STOREFRONT_PATH).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vendor.id").value(7))
                .andExpect(jsonPath("$.vendor.businessName").value("Koramangala Florist"))
                .andExpect(jsonPath("$.vendor.city").value("Bengaluru"))
                .andExpect(jsonPath("$.vendor.area").value("Koramangala"))
                .andExpect(jsonPath("$.vendor.pincode").value("560034"))
                .andExpect(jsonPath("$.vendor.acceptingOrders").value(true))
                .andExpect(jsonPath("$.vendor.hours[0].weekday").value("MONDAY"))
                .andExpect(jsonPath("$.products.content[0].id").value(101))
                .andExpect(jsonPath("$.products.content[0].name").value("Red Rose Bunch"))
                .andExpect(jsonPath("$.products.content[0].inStock").value(true))
                .andExpect(jsonPath("$.products.totalElements").value(1))
                .andExpect(jsonPath("$.products.page").value(0))
                .andExpect(jsonPath("$.products.size").value(20));
    }

    /**
     * The wire name of the availability flag is asserted at the HTTP boundary
     * because a primitive boolean's getter drives it: a field named
     * {@code isInStock} would serialise as {@code inStock} while a field named
     * {@code inStock} keeps the name. Only this kind of test catches the drift
     * — see {@code docs/decisions.md} (D-33).
     */
    @Test
    void exposesTheInStockFlagUnderItsOwnName() throws Exception {
        when(storefrontService.storefront(any(), any(), any())).thenReturn(sampleResponse());

        mockMvc.perform(get(STOREFRONT_PATH).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products.content[0].inStock").exists())
                .andExpect(jsonPath("$.products.content[0].isInStock").doesNotExist());
    }

    @Test
    void doesNotSerializePrivateVendorOrInventoryFields() throws Exception {
        when(storefrontService.storefront(any(), any(), any())).thenReturn(sampleResponse());

        String body = mockMvc.perform(get(STOREFRONT_PATH).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        for (String forbidden : List.of(
                "ownerEmail", "commissionRate", "status", "latitude", "longitude",
                "addressLine1", "addressLine2", "createdAt", "updatedAt",
                "quantity", "reservedQuantity", "inventory", "images", "storageKey")) {
            assertFalse(body.contains(forbidden),
                    "Storefront response must not expose '" + forbidden + "': " + body);
        }
        assertTrue(body.contains("\"vendor\"") && body.contains("\"products\""),
                "Compound response must carry both halves: " + body);
    }

    // ------------------------------------------------------------- validation

    @Test
    void rejectsANegativePage() throws Exception {
        mockMvc.perform(get(STOREFRONT_PATH).param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void rejectsASizeBelowOne() throws Exception {
        mockMvc.perform(get(STOREFRONT_PATH).param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void rejectsASizeAboveOneHundred() throws Exception {
        mockMvc.perform(get(STOREFRONT_PATH).param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void rejectsANonPositiveVendorId() throws Exception {
        mockMvc.perform(get("/api/v1/vendors/0/storefront"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    // ------------------------------------------------------- security boundaries

    @Test
    void theStorefrontIsPublicWhileTheNeighbouringVendorRouteIsNot() throws Exception {
        mockMvc.perform(get(STOREFRONT_PATH))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/vendors/profile"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/vendors/products"))
                .andExpect(status().isUnauthorized());
    }
}
