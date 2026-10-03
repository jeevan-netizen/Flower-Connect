package com.flowerconnect.catalog.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowerconnect.catalog.dto.CategoryPageResponse;
import com.flowerconnect.catalog.dto.CategoryRequest;
import com.flowerconnect.catalog.dto.CategoryResponse;
import com.flowerconnect.catalog.service.CategoryService;
import com.flowerconnect.config.TestClockConfig;
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

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AdminCategoryController.class)
@Import({AdminCategoryControllerTest.TestSecurityConfig.class, TestClockConfig.class})
class AdminCategoryControllerTest {

    @TestConfiguration
    static class TestSecurityConfig {
        @Bean
        SecurityFilterChain testFilterChain(HttpSecurity http) throws Exception {
            http
                    .csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
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
    private CategoryService categoryService;

    // ------------------------------------------------------------- authorization

    @Test
    void listingCategoriesRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/admin/categories"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(categoryService);
    }

    @Test
    @WithMockUser(username = "customer@test.com", roles = {"CUSTOMER"})
    void aCustomerCannotListCategories() throws Exception {
        mockMvc.perform(get("/api/v1/admin/categories"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(categoryService);
    }

    @Test
    @WithMockUser(username = "florist@test.com", roles = {"FLORIST"})
    void aVendorCannotListCategories() throws Exception {
        mockMvc.perform(get("/api/v1/admin/categories"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(categoryService);
    }

    // -------------------------------------------------------------------- listing

    @Test
    @WithMockUser(username = "admin@test.com", roles = {"ADMIN"})
    void anAdminCanListCategories() throws Exception {
        when(categoryService.list(null, 0, 20)).thenReturn(samplePage());

        mockMvc.perform(get("/api/v1/admin/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = {"ADMIN"})
    void theListingCanBeFilteredByParent() throws Exception {
        when(categoryService.list(eq(1L), eq(0), eq(20))).thenReturn(samplePage());

        mockMvc.perform(get("/api/v1/admin/categories")
                        .param("parentId", "1"))
                .andExpect(status().isOk());

        verify(categoryService).list(1L, 0, 20);
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = {"ADMIN"})
    void theListingIsPaginated() throws Exception {
        when(categoryService.list(null, 2, 5)).thenReturn(samplePage());

        mockMvc.perform(get("/api/v1/admin/categories")
                        .param("page", "2")
                        .param("size", "5"))
                .andExpect(status().isOk());

        verify(categoryService).list(null, 2, 5);
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = {"ADMIN"})
    void anOversizedPageIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/admin/categories")
                        .param("size", "500"))
                .andExpect(status().isBadRequest());
    }

    // -------------------------------------------------------------------- create

    @Test
    @WithMockUser(username = "admin@test.com", roles = {"ADMIN"})
    void anAdminCanCreateACategory() throws Exception {
        when(categoryService.create(eq("admin@test.com"), any(CategoryRequest.class)))
                .thenReturn(sampleCategory());

        mockMvc.perform(post("/api/v1/admin/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("New Category", null, 1, true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("new-category"));

        verify(categoryService).create(eq("admin@test.com"), any(CategoryRequest.class));
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = {"ADMIN"})
    void createRejectsMissingName() throws Exception {
        mockMvc.perform(post("/api/v1/admin/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("", null, 1, true)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validation.name").exists());

        verifyNoInteractions(categoryService);
    }

    // -------------------------------------------------------------------- update

    @Test
    @WithMockUser(username = "admin@test.com", roles = {"ADMIN"})
    void anAdminCanUpdateACategory() throws Exception {
        CategoryResponse updated = CategoryResponse.builder()
                .id(1L)
                .name("Updated Name")
                .slug("updated-name")
                .displayOrder(2)
                .active(false)
                .build();
        when(categoryService.update(eq("admin@test.com"), eq(1L), any(CategoryRequest.class)))
                .thenReturn(updated);

        mockMvc.perform(put("/api/v1/admin/categories/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("Updated Name", 2L, 2, false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("updated-name"));

        verify(categoryService).update(eq("admin@test.com"), eq(1L), any(CategoryRequest.class));
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = {"ADMIN"})
    void anIllegalTransitionIsReportedAsConflict() throws Exception {
        doThrow(new com.flowerconnect.exception.BusinessException(
                com.flowerconnect.exception.ErrorCode.CONFLICT,
                "Category hierarchy would contain a cycle"))
                .when(categoryService).update(anyString(), anyLong(), any(CategoryRequest.class));

        mockMvc.perform(put("/api/v1/admin/categories/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("Name", 1L, 1, true)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = {"ADMIN"})
    void anUnknownCategoryIsReportedAsNotFound() throws Exception {
        doThrow(new com.flowerconnect.exception.BusinessException(
                com.flowerconnect.exception.ErrorCode.NOT_FOUND,
                "Category not found"))
                .when(categoryService).update(anyString(), anyLong(), any(CategoryRequest.class));

        mockMvc.perform(put("/api/v1/admin/categories/999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("Name", null, 1, true)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = {"ADMIN"})
    void aNonPositiveCategoryIdIsRejected() throws Exception {
        mockMvc.perform(put("/api/v1/admin/categories/0")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("Name", null, 1, true)))
                .andExpect(status().isBadRequest());
    }

    // -------------------------------------------------------------------- delete

    @Test
    @WithMockUser(username = "admin@test.com", roles = {"ADMIN"})
    void anAdminCanDeleteACategory() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/categories/1"))
                .andExpect(status().isNoContent());

        verify(categoryService).delete(eq("admin@test.com"), eq(1L));
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = {"ADMIN"})
    void deleteRefusesWhenHasChildren() throws Exception {
        doThrow(new com.flowerconnect.exception.BusinessException(
                com.flowerconnect.exception.ErrorCode.CONFLICT,
                "Cannot delete a category that has child categories"))
                .when(categoryService).delete(anyString(), anyLong());

        mockMvc.perform(delete("/api/v1/admin/categories/1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = {"ADMIN"})
    void deleteUnknownCategoryIsNotFound() throws Exception {
        doThrow(new com.flowerconnect.exception.BusinessException(
                com.flowerconnect.exception.ErrorCode.NOT_FOUND,
                "Category not found"))
                .when(categoryService).delete(anyString(), anyLong());

        mockMvc.perform(delete("/api/v1/admin/categories/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = {"ADMIN"})
    void deleteNonPositiveIdIsRejected() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/categories/0"))
                .andExpect(status().isBadRequest());
    }

    // -------------------------------------------------------------------- helpers

    private String request(String name, Long parentId, Integer displayOrder, Boolean active) throws Exception {
        CategoryRequest req = CategoryRequest.builder()
                .name(name)
                .parentId(parentId)
                .displayOrder(displayOrder)
                .active(active)
                .build();
        return objectMapper.writeValueAsString(req);
    }

    private static CategoryResponse sampleCategory() {
        return CategoryResponse.builder()
                .id(1L)
                .name("New Category")
                .slug("new-category")
                .displayOrder(1)
                .active(true)
                .build();
    }

    private static CategoryPageResponse samplePage() {
        return CategoryPageResponse.builder()
                .content(List.of(sampleCategory()))
                .page(0)
                .size(20)
                .totalElements(1)
                .totalPages(1)
                .first(true)
                .last(true)
                .empty(false)
                .build();
    }
}