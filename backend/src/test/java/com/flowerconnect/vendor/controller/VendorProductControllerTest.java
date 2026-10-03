package com.flowerconnect.vendor.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowerconnect.catalog.domain.Product.ProductStatus;
import com.flowerconnect.catalog.dto.ProductPageResponse;
import com.flowerconnect.catalog.dto.ProductRequest;
import com.flowerconnect.catalog.dto.ProductResponse;
import com.flowerconnect.catalog.service.ProductService;
import com.flowerconnect.config.TestClockConfig;
import com.flowerconnect.exception.BusinessException;
import com.flowerconnect.exception.ErrorCode;
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
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer coverage for the vendor catalog API (plan task 3.5): the role boundary
 * declared in the production {@code SecurityConfig}, request-body validation, query
 * parameter wiring, HTTP status codes, and the error envelope produced by the shared
 * {@code GlobalExceptionHandler}.
 *
 * <p>The approval gate itself is <em>not</em> covered here: {@code @RequiresApprovedVendor}
 * is a composed {@code @PreAuthorize} enabled by the production {@code SecurityConfig},
 * and a {@code @WebMvcTest} slice supplies its own filter chain and never loads it, so the
 * annotation is inert in this context (D-13). Approval gating is asserted against the real
 * chain in {@code VendorCatalogIntegrationTest}.
 */
@WebMvcTest(controllers = VendorProductController.class)
@Import({VendorProductControllerTest.TestSecurityConfig.class, TestClockConfig.class})
class VendorProductControllerTest {

    @TestConfiguration
    static class TestSecurityConfig {
        /** Mirrors the production rule: everything under /api/v1/vendors/** needs the vendor role. */
        @Bean
        SecurityFilterChain testFilterChain(HttpSecurity http) throws Exception {
            http
                    .csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers("/api/v1/vendors/**").hasRole("FLORIST")
                            .anyRequest().authenticated())
                    .httpBasic(Customizer.withDefaults());
            return http.build();
        }
    }

    private static final String FLORIST = "florist@test.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProductService productService;

    // ------------------------------------------------------------- role boundary

    @Test
    @WithMockUser(username = FLORIST, roles = "CUSTOMER")
    void everyRouteRefusesANonVendor() throws Exception {
        mockMvc.perform(get("/api/v1/vendors/products"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/vendors/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest())))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/vendors/products/1"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/vendors/products/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest())))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/v1/vendors/products/1/deactivate"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(productService);
    }

    @Test
    void everyRouteRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/vendors/products"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(productService);
    }

    // ------------------------------------------------------------- create

    @Test
    @WithMockUser(username = FLORIST, roles = "FLORIST")
    void createReturns201AndResolvesTheVendorFromTheJwtSubject() throws Exception {
        when(productService.create(eq(FLORIST), any(ProductRequest.class)))
                .thenReturn(sampleResponse());

        mockMvc.perform(post("/api/v1/vendors/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.slug").value("hybrid-tea"));

        verify(productService).create(eq(FLORIST), any(ProductRequest.class));
    }

    @Test
    @WithMockUser(username = FLORIST, roles = "FLORIST")
    void createRejectsABlankName() throws Exception {
        ProductRequest request = validRequest();
        request.setName("  ");

        mockMvc.perform(post("/api/v1/vendors/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.validation.name").exists());

        verifyNoInteractions(productService);
    }

    @Test
    @WithMockUser(username = FLORIST, roles = "FLORIST")
    void createRejectsAMissingCategory() throws Exception {
        ProductRequest request = validRequest();
        request.setCategoryId(null);

        mockMvc.perform(post("/api/v1/vendors/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validation.categoryId").exists());

        verifyNoInteractions(productService);
    }

    @Test
    @WithMockUser(username = FLORIST, roles = "FLORIST")
    void createRejectsAZeroPrice() throws Exception {
        ProductRequest request = validRequest();
        request.setBasePrice(BigDecimal.ZERO);

        mockMvc.perform(post("/api/v1/vendors/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validation.basePrice").exists());

        verifyNoInteractions(productService);
    }

    @Test
    @WithMockUser(username = FLORIST, roles = "FLORIST")
    void createRejectsANegativePrice() throws Exception {
        ProductRequest request = validRequest();
        request.setBasePrice(new BigDecimal("-1.00"));

        mockMvc.perform(post("/api/v1/vendors/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validation.basePrice").exists());

        verifyNoInteractions(productService);
    }

    @Test
    @WithMockUser(username = FLORIST, roles = "FLORIST")
    void createRejectsAPriceWithTooManyFractionDigits() throws Exception {
        ProductRequest request = validRequest();
        request.setBasePrice(new BigDecimal("10.005"));

        mockMvc.perform(post("/api/v1/vendors/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validation.basePrice").exists());

        verifyNoInteractions(productService);
    }

    @Test
    @WithMockUser(username = FLORIST, roles = "FLORIST")
    void aRejectedCategoryFromTheServiceIsReportedAsABadRequest() throws Exception {
        when(productService.create(eq(FLORIST), any(ProductRequest.class)))
                .thenThrow(BusinessException.badRequest("Category is not active"));

        mockMvc.perform(post("/api/v1/vendors/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Category is not active"));
    }

    // ------------------------------------------------------------- list

    @Test
    @WithMockUser(username = FLORIST, roles = "FLORIST")
    void listDefaultsToTheFirstPageOfTwenty() throws Exception {
        when(productService.list(eq(FLORIST), isNull(), isNull(), isNull(), eq(0), eq(20)))
                .thenReturn(samplePage());

        mockMvc.perform(get("/api/v1/vendors/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(10))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @WithMockUser(username = FLORIST, roles = "FLORIST")
    void listPassesEveryFilterThroughToTheService() throws Exception {
        when(productService.list(eq(FLORIST), eq(ProductStatus.ACTIVE), eq(7L), eq("rose"), eq(2), eq(5)))
                .thenReturn(samplePage());

        mockMvc.perform(get("/api/v1/vendors/products")
                        .param("status", "ACTIVE")
                        .param("categoryId", "7")
                        .param("name", "rose")
                        .param("page", "2")
                        .param("size", "5"))
                .andExpect(status().isOk());

        verify(productService).list(FLORIST, ProductStatus.ACTIVE, 7L, "rose", 2, 5);
    }

    @Test
    @WithMockUser(username = FLORIST, roles = "FLORIST")
    void listRejectsAPageSizeAboveTheCap() throws Exception {
        mockMvc.perform(get("/api/v1/vendors/products").param("size", "101"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(productService);
    }

    @Test
    @WithMockUser(username = FLORIST, roles = "FLORIST")
    void listRejectsANegativePage() throws Exception {
        mockMvc.perform(get("/api/v1/vendors/products").param("page", "-1"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(productService);
    }

    @Test
    @WithMockUser(username = FLORIST, roles = "FLORIST")
    void listRejectsAnUnknownStatus() throws Exception {
        mockMvc.perform(get("/api/v1/vendors/products").param("status", "NOT_A_STATUS"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(productService);
    }

    // ------------------------------------------------------------- read

    @Test
    @WithMockUser(username = FLORIST, roles = "FLORIST")
    void readReturnsTheProduct() throws Exception {
        when(productService.getByIdForVendor(FLORIST, 10L)).thenReturn(sampleResponse());

        mockMvc.perform(get("/api/v1/vendors/products/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10));
    }

    @Test
    @WithMockUser(username = FLORIST, roles = "FLORIST")
    void aForeignProductIsForbidden() throws Exception {
        when(productService.getByIdForVendor(FLORIST, 10L))
                .thenThrow(BusinessException.forbidden("Product does not belong to this vendor"));

        mockMvc.perform(get("/api/v1/vendors/products/10"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @WithMockUser(username = FLORIST, roles = "FLORIST")
    void anUnknownProductIsNotFound() throws Exception {
        when(productService.getByIdForVendor(FLORIST, 999L))
                .thenThrow(BusinessException.notFound("Product not found"));

        mockMvc.perform(get("/api/v1/vendors/products/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @WithMockUser(username = FLORIST, roles = "FLORIST")
    void readRejectsANonPositiveId() throws Exception {
        mockMvc.perform(get("/api/v1/vendors/products/0"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(productService);
    }

    // ------------------------------------------------------------- update

    @Test
    @WithMockUser(username = FLORIST, roles = "FLORIST")
    void updateReturnsTheChangedProduct() throws Exception {
        when(productService.update(eq(FLORIST), eq(10L), any(ProductRequest.class)))
                .thenReturn(sampleResponse());

        mockMvc.perform(put("/api/v1/vendors/products/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10));

        verify(productService).update(eq(FLORIST), eq(10L), any(ProductRequest.class));
    }

    @Test
    @WithMockUser(username = FLORIST, roles = "FLORIST")
    void updateRejectsABlankName() throws Exception {
        ProductRequest request = validRequest();
        request.setName("");

        mockMvc.perform(put("/api/v1/vendors/products/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validation.name").exists());

        verifyNoInteractions(productService);
    }

    @Test
    @WithMockUser(username = FLORIST, roles = "FLORIST")
    void updateRefusesAProductOwnedByAnotherVendor() throws Exception {
        when(productService.update(eq(FLORIST), eq(10L), any(ProductRequest.class)))
                .thenThrow(BusinessException.forbidden("Product does not belong to this vendor"));

        mockMvc.perform(put("/api/v1/vendors/products/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(validRequest())))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------- deactivate

    @Test
    @WithMockUser(username = FLORIST, roles = "FLORIST")
    void deactivateReturns204AndTakesNoBody() throws Exception {
        mockMvc.perform(patch("/api/v1/vendors/products/10/deactivate"))
                .andExpect(status().isNoContent());

        verify(productService).deactivate(FLORIST, 10L);
    }

    @Test
    @WithMockUser(username = FLORIST, roles = "FLORIST")
    void deactivateRefusesAProductOwnedByAnotherVendor() throws Exception {
        doThrow(BusinessException.forbidden("Product does not belong to this vendor"))
                .when(productService).deactivate(FLORIST, 10L);

        mockMvc.perform(patch("/api/v1/vendors/products/10/deactivate"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = FLORIST, roles = "FLORIST")
    void deactivateReportsAMissingProductAsNotFound() throws Exception {
        doThrow(BusinessException.notFound("Product not found"))
                .when(productService).deactivate(FLORIST, 999L);

        mockMvc.perform(patch("/api/v1/vendors/products/999/deactivate"))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------- fixtures

    private ProductRequest validRequest() {
        return ProductRequest.builder()
                .name("Hybrid Tea")
                .categoryId(1L)
                .description("Fresh flowers")
                .basePrice(new BigDecimal("299.00"))
                .build();
    }

    private ProductResponse sampleResponse() {
        return ProductResponse.builder()
                .id(10L)
                .vendorId(1L)
                .categoryId(1L)
                .name("Hybrid Tea")
                .slug("hybrid-tea")
                .description("Fresh flowers")
                .basePrice(new BigDecimal("299.00"))
                .status(ProductStatus.DRAFT)
                .build();
    }

    private ProductPageResponse samplePage() {
        return ProductPageResponse.builder()
                .content(List.of(sampleResponse()))
                .page(0)
                .size(20)
                .totalElements(1)
                .totalPages(1)
                .first(true)
                .last(true)
                .empty(false)
                .build();
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}