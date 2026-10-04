package com.flowerconnect.vendor.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowerconnect.catalog.dto.ProductImageResponse;
import com.flowerconnect.catalog.service.ProductImageService;
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
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer coverage for the product image API (plan task 3.8): the role boundary
 * declared in the production {@code SecurityConfig}, multipart binding, the
 * {@code primary} flag default, path-variable validation, and the status code and
 * envelope each service refusal produces.
 *
 * <p>The approval gate itself is <b>not</b> covered here, for the reason recorded
 * in D-13: {@code @RequiresApprovedVendor} is a composed {@code @PreAuthorize}
 * enabled by the production {@code SecurityConfig}, and a {@code @WebMvcTest}
 * slice supplies its own filter chain and never loads it, so the annotation is
 * inert here. Approval gating is asserted against the real chain in
 * {@code ProductImageIntegrationTest}.
 *
 * <p>Likewise the byte-level pipeline: content sniffing, decoding and resizing are
 * {@code ImageProcessorTest}, and the storage key rules are
 * {@code LocalDiskStorageServiceTest}. What is left is wiring.
 */
@WebMvcTest(controllers = VendorProductImageController.class)
@Import({VendorProductImageControllerTest.TestSecurityConfig.class, TestClockConfig.class})
class VendorProductImageControllerTest {

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
    private static final String BASE = "/api/v1/vendors/products/42/images";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProductImageService productImageService;

    // ------------------------------------------------------------------
    // Role boundary
    // ------------------------------------------------------------------

    @Test
    @WithMockUser(username = FLORIST, roles = "CUSTOMER")
    void everyRouteRefusesANonVendor() throws Exception {
        mockMvc.perform(get(BASE)).andExpect(status().isForbidden());
        mockMvc.perform(multipart(BASE).file(upload())).andExpect(status().isForbidden());
        mockMvc.perform(put(BASE + "/7/primary")).andExpect(status().isForbidden());
        mockMvc.perform(put(BASE + "/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"imageIds\":[7]}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete(BASE + "/7")).andExpect(status().isForbidden());

        verifyNoInteractions(productImageService);
    }

    @Test
    void everyRouteRequiresAuthentication() throws Exception {
        mockMvc.perform(get(BASE)).andExpect(status().isUnauthorized());
        mockMvc.perform(multipart(BASE).file(upload())).andExpect(status().isUnauthorized());
        mockMvc.perform(delete(BASE + "/7")).andExpect(status().isUnauthorized());

        verifyNoInteractions(productImageService);
    }

    // ------------------------------------------------------------------
    // Upload
    // ------------------------------------------------------------------

    @Test
    @WithMockUser(username = FLORIST, roles = "FLORIST")
    void anUploadReturns201WithTheStoredImage() throws Exception {
        when(productImageService.upload(eq(FLORIST), eq(42L), any(), eq(false)))
                .thenReturn(ProductImageResponse.builder().id(7L).storageKey("product-images/42/x.jpg")
                        .mimeType("image/jpeg").fileSize(120L).sortOrder(0).primary(true).build());

        mockMvc.perform(multipart(BASE).file(upload()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.primary").value(true));
    }

    @Test
    @WithMockUser(username = FLORIST, roles = "FLORIST")
    void thePrimaryFlagDefaultsToFalseAndIsForwardedWhenPresent() throws Exception {
        when(productImageService.upload(eq(FLORIST), eq(42L), any(), anyBoolean()))
                .thenReturn(ProductImageResponse.builder().id(7L).build());

        mockMvc.perform(multipart(BASE).file(upload()))
                .andExpect(status().isCreated());
        verify(productImageService).upload(eq(FLORIST), eq(42L), any(), eq(false));

        mockMvc.perform(multipart(BASE).file(upload()).param("primary", "true"))
                .andExpect(status().isCreated());
        verify(productImageService).upload(eq(FLORIST), eq(42L), any(), eq(true));
    }

    @Test
    @WithMockUser(username = FLORIST, roles = "FLORIST")
    void anUploadWithoutAFilePartIsRejectedAsABadRequest() throws Exception {
        mockMvc.perform(multipart(BASE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").value("File part is required"));

        verifyNoInteractions(productImageService);
    }

    @Test
    @WithMockUser(username = FLORIST, roles = "FLORIST")
    void anUploadTheContainerRefusesForSizeIs413WithItsOwnCode() throws Exception {
        when(productImageService.upload(eq(FLORIST), eq(42L), any(), anyBoolean()))
                .thenThrow(new MaxUploadSizeExceededException(6L * 1024 * 1024));

        mockMvc.perform(multipart(BASE).file(upload()))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("PAYLOAD_TOO_LARGE"));
    }

    @Test
    @WithMockUser(username = FLORIST, roles = "FLORIST")
    void serviceRefusalsKeepTheirOwnStatusAndCode() throws Exception {
        assertRefusal(BusinessException.unsupportedMediaType("Unsupported image format"),
                415, ErrorCode.UNSUPPORTED_MEDIA_TYPE);
        assertRefusal(BusinessException.payloadTooLarge("too big"), 413, ErrorCode.PAYLOAD_TOO_LARGE);
        assertRefusal(BusinessException.badRequest("Uploaded file is empty"), 400, ErrorCode.VALIDATION_FAILED);
        assertRefusal(BusinessException.forbidden("Product does not belong to this vendor"),
                403, ErrorCode.FORBIDDEN);
        assertRefusal(BusinessException.notFound("Product not found"), 404, ErrorCode.NOT_FOUND);
        assertRefusal(BusinessException.conflict("Product already has the maximum of 8 images"),
                409, ErrorCode.CONFLICT);
    }

    // ------------------------------------------------------------------
    // Read, cover, order, delete
    // ------------------------------------------------------------------

    @Test
    @WithMockUser(username = FLORIST, roles = "FLORIST")
    void listReturnsTheOrderedImages() throws Exception {
        when(productImageService.list(FLORIST, 42L)).thenReturn(List.of(
                ProductImageResponse.builder().id(1L).sortOrder(0).primary(true).build(),
                ProductImageResponse.builder().id(2L).sortOrder(1).primary(false).build()));

        mockMvc.perform(get(BASE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].primary").value(true))
                .andExpect(jsonPath("$[1].id").value(2));
    }

    @Test
    @WithMockUser(username = FLORIST, roles = "FLORIST")
    void settingTheCoverReturnsTheFullList() throws Exception {
        when(productImageService.setPrimary(FLORIST, 42L, 2L)).thenReturn(List.of(
                ProductImageResponse.builder().id(1L).sortOrder(0).primary(false).build(),
                ProductImageResponse.builder().id(2L).sortOrder(1).primary(true).build()));

        mockMvc.perform(put(BASE + "/2/primary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[1].primary").value(true));
    }

    @Test
    @WithMockUser(username = FLORIST, roles = "FLORIST")
    void aReorderForwardsTheRequestedIds() throws Exception {
        when(productImageService.reorder(FLORIST, 42L, List.of(2L, 1L))).thenReturn(List.of());

        mockMvc.perform(put(BASE + "/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"imageIds\":[2,1]}"))
                .andExpect(status().isOk());

        verify(productImageService).reorder(FLORIST, 42L, List.of(2L, 1L));
    }

    @Test
    @WithMockUser(username = FLORIST, roles = "FLORIST")
    void anEmptyReorderIsRejectedByBeanValidation() throws Exception {
        mockMvc.perform(put(BASE + "/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"imageIds\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.validation.imageIds").exists());

        verifyNoInteractions(productImageService);
    }

    @Test
    @WithMockUser(username = FLORIST, roles = "FLORIST")
    void aReorderWithANonPositiveIdIsRejectedByBeanValidation() throws Exception {
        mockMvc.perform(put(BASE + "/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"imageIds\":[0]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(productImageService);
    }

    @Test
    @WithMockUser(username = FLORIST, roles = "FLORIST")
    void deleteReturns204() throws Exception {
        mockMvc.perform(delete(BASE + "/7")).andExpect(status().isNoContent());

        verify(productImageService).delete(FLORIST, 42L, 7L);
    }

    @Test
    @WithMockUser(username = FLORIST, roles = "FLORIST")
    void nonPositivePathVariablesAreRejected() throws Exception {
        mockMvc.perform(get("/api/v1/vendors/products/-1/images")).andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/v1/vendors/products/42/images/0/primary")).andExpect(status().isBadRequest());
        mockMvc.perform(delete("/api/v1/vendors/products/42/images/-3")).andExpect(status().isBadRequest());

        verifyNoInteractions(productImageService);
    }

    // ------------------------------------------------------------------

    private MockMultipartFile upload() {
        return new MockMultipartFile("file", "rose.jpg", "image/jpeg",
                "bytes".getBytes(StandardCharsets.UTF_8));
    }

    private void assertRefusal(BusinessException thrown, int expectedStatus, ErrorCode expectedCode)
            throws Exception {
        doThrow(thrown).when(productImageService).upload(eq(FLORIST), eq(42L), any(), anyBoolean());

        mockMvc.perform(multipart(BASE).file(upload()))
                .andExpect(status().is(expectedStatus))
                .andExpect(jsonPath("$.code").value(expectedCode.name()));
    }
}