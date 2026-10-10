package com.flowerconnect.vendor.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowerconnect.catalog.dto.InventorySummary;
import com.flowerconnect.config.TestClockConfig;
import com.flowerconnect.exception.BusinessException;
import com.flowerconnect.exception.ErrorCode;
import com.flowerconnect.inventory.domain.StockMovement.MovementType;
import com.flowerconnect.inventory.dto.ExpiryDateRequest;
import com.flowerconnect.inventory.dto.LowStockPageResponse;
import com.flowerconnect.inventory.dto.LowStockProductResponse;
import com.flowerconnect.inventory.dto.LowStockThresholdRequest;
import com.flowerconnect.inventory.dto.StockAdjustmentRequest;
import com.flowerconnect.inventory.dto.StockInRequest;
import com.flowerconnect.inventory.dto.StockMovementPageResponse;
import com.flowerconnect.inventory.dto.StockMovementResponse;
import com.flowerconnect.inventory.dto.StockOutRequest;
import com.flowerconnect.inventory.dto.StockWriteOffRequest;
import com.flowerconnect.inventory.service.InventoryService;
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

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer coverage for the inventory API (plan task 3.6): the role boundary declared in the
 * production {@code SecurityConfig}, request-body validation, query-parameter wiring, HTTP status
 * codes, and the error envelope produced by the shared {@code GlobalExceptionHandler}.
 *
 * <p>The approval gate is deliberately <em>not</em> covered here: {@code @RequiresApprovedVendor} is a
 * composed {@code @PreAuthorize} enabled by the production {@code SecurityConfig}, and a
 * {@code @WebMvcTest} slice supplies its own filter chain and never loads it, so the annotation is
 * inert in this context (D-13). Gating is asserted against the real chain in
 * {@code VendorInventoryIntegrationTest}.
 *
 * <p>The pessimistic lock, the reserved-quantity floor against real quantities and the low-stock
 * predicate's SQL behaviour are equally absent: none of them can be observed through a mocked
 * repository.
 */
@WebMvcTest(controllers = VendorInventoryController.class)
@Import({VendorInventoryControllerTest.TestSecurityConfig.class, TestClockConfig.class})
class VendorInventoryControllerTest {

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
    private static final String BASE = "/api/v1/vendors/products/10/inventory";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private InventoryService inventoryService;

    // ------------------------------------------------------------- role boundary

    @Test
    @WithMockUser(username = FLORIST, roles = "CUSTOMER")
    void everyRouteRefusesANonVendor() throws Exception {
        mockMvc.perform(get(BASE)).andExpect(status().isForbidden());
        mockMvc.perform(post(BASE + "/stock-in")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(stockIn(5)))).andExpect(status().isForbidden());
        mockMvc.perform(post(BASE + "/stock-out")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(stockOut(5)))).andExpect(status().isForbidden());
        mockMvc.perform(post(BASE + "/adjustments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(adjustment(5, "recount")))).andExpect(status().isForbidden());
        mockMvc.perform(post(BASE + "/write-offs")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(writeOff(5, "spoiled")))).andExpect(status().isForbidden());
        mockMvc.perform(put(BASE + "/low-stock-threshold")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(threshold(3)))).andExpect(status().isForbidden());
        mockMvc.perform(put(BASE + "/expiry-date")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(expiry("2026-04-30")))).andExpect(status().isForbidden());
        mockMvc.perform(get(BASE + "/movements")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/vendors/inventory/low-stock"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(inventoryService);
    }

    @Test
    void everyRouteRequiresAuthentication() throws Exception {
        mockMvc.perform(get(BASE)).andExpect(status().isUnauthorized());
        mockMvc.perform(post(BASE + "/stock-in")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(stockIn(5)))).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/vendors/inventory/low-stock"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(inventoryService);
    }

    // ------------------------------------------------------------- read

    @Test
    @WithMockUser(username = FLORIST, roles = "FLORIST")
    void readReturnsTheInventoryWithItsAvailability() throws Exception {
        when(inventoryService.get(FLORIST, 10L)).thenReturn(sampleSummary());

        mockMvc.perform(get(BASE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value(10))
                .andExpect(jsonPath("$.quantity").value(12))
                .andExpect(jsonPath("$.reservedQuantity").value(3))
                .andExpect(jsonPath("$.available").value(9))
                .andExpect(jsonPath("$.lowStockThreshold").value(4))
                .andExpect(jsonPath("$.lowStock").value(false));
    }

    @Test
    @WithMockUser(username = FLORIST, roles = "FLORIST")
    void aForeignProductIsForbidden() throws Exception {
        when(inventoryService.get(FLORIST, 10L))
                .thenThrow(BusinessException.forbidden("Product does not belong to this vendor"));

        mockMvc.perform(get(BASE))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @WithMockUser(username = FLORIST, roles = "FLORIST")
    void anUnknownProductIsNotFound() throws Exception {
        when(inventoryService.get(FLORIST, 999L))
                .thenThrow(BusinessException.notFound("Product not found"));

        mockMvc.perform(get("/api/v1/vendors/products/999/inventory"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @WithMockUser(username = FLORIST, roles = "FLORIST")
    void readRejectsANonPositiveId() throws Exception {
        mockMvc.perform(get("/api/v1/vendors/products/0/inventory"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(inventoryService);
    }

    // ------------------------------------------------------------- stock in / out

    @Test
    @WithMockUser(username = FLORIST, roles = "FLORIST")
    void stockInReturnsTheNewLevel() throws Exception {
        when(inventoryService.stockIn(eq(FLORIST), eq(10L), any(StockInRequest.class)))
                .thenReturn(sampleSummary());

        mockMvc.perform(post(BASE + "/stock-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(stockIn(5))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(9));

        verify(inventoryService).stockIn(eq(FLORIST), eq(10L), any(StockInRequest.class));
    }

    @Test
    @WithMockUser(username = FLORIST, roles = "FLORIST")
    void stockInRejectsAZeroQuantity() throws Exception {
        mockMvc.perform(post(BASE + "/stock-in")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(stockIn(0))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.validation.quantity").exists());

        verifyNoInteractions(inventoryService);
    }

    @Test
    @WithMockUser(username = FLORIST, roles = "FLORIST")
    void stockOutRejectsANegativeQuantity() throws Exception {
        mockMvc.perform(post(BASE + "/stock-out")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(stockOut(-2))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validation.quantity").exists());

        verifyNoInteractions(inventoryService);
    }

    @Test
    @WithMockUser(username = FLORIST, roles = "FLORIST")
    void aChangeBelowTheReservedQuantityIsAConflictWithItsOwnCode() throws Exception {
        when(inventoryService.stockOut(eq(FLORIST), eq(10L), any(StockOutRequest.class)))
                .thenThrow(BusinessException.insufficientStock(
                        "Stock change refused: it would leave quantity at 3, below the 8 reserved unit(s)"));

        mockMvc.perform(post(BASE + "/stock-out")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(stockOut(9))))
                .andExpect(status().isConflict())
                // Not a plain CONFLICT: the client can tell "the stock
                // numbers do not allow this" from "already exists".
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"))
                .andExpect(jsonPath("$.message").value(
                        "Stock change refused: it would leave quantity at 3, below the 8 reserved unit(s)"));
    }

    // ------------------------------------------------------------- adjustment

    @Test
    @WithMockUser(username = FLORIST, roles = "FLORIST")
    void adjustmentAcceptsANegativeCorrection() throws Exception {
        when(inventoryService.adjust(eq(FLORIST), eq(10L), any(StockAdjustmentRequest.class)))
                .thenReturn(sampleSummary());

        mockMvc.perform(post(BASE + "/adjustments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(adjustment(-4, "recount after shrinkage"))))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = FLORIST, roles = "FLORIST")
    void adjustmentRequiresAReason() throws Exception {
        mockMvc.perform(post(BASE + "/adjustments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(adjustment(4, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.validation.reason").exists());

        verifyNoInteractions(inventoryService);
    }

    @Test
    @WithMockUser(username = FLORIST, roles = "FLORIST")
    void adjustmentRejectsABlankReason() throws Exception {
        mockMvc.perform(post(BASE + "/adjustments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(adjustment(4, "   "))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validation.reason").exists());

        verifyNoInteractions(inventoryService);
    }

    @Test
    @WithMockUser(username = FLORIST, roles = "FLORIST")
    void adjustmentRequiresAQuantity() throws Exception {
        mockMvc.perform(post(BASE + "/adjustments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"recount\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validation.quantity").exists());

        verifyNoInteractions(inventoryService);
    }

    @Test
    @WithMockUser(username = FLORIST, roles = "FLORIST")
    void anAdjustmentReasonOverTheColumnWidthIsRejected() throws Exception {
        mockMvc.perform(post(BASE + "/adjustments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(adjustment(4, "x".repeat(501)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validation.reason").exists());

        verifyNoInteractions(inventoryService);
    }

    // ------------------------------------------------------------- write-off

    @Test
    @WithMockUser(username = FLORIST, roles = "FLORIST")
    void writeOffReturnsTheNewLevel() throws Exception {
        when(inventoryService.writeOff(eq(FLORIST), eq(10L), any(StockWriteOffRequest.class)))
                .thenReturn(sampleSummary());

        mockMvc.perform(post(BASE + "/write-offs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(writeOff(3, "cooler failed overnight"))))
                .andExpect(status().isOk());

        verify(inventoryService).writeOff(eq(FLORIST), eq(10L), any(StockWriteOffRequest.class));
    }

    @Test
    @WithMockUser(username = FLORIST, roles = "FLORIST")
    void writeOffRequiresAReason() throws Exception {
        mockMvc.perform(post(BASE + "/write-offs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(writeOff(3, ""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validation.reason").exists());

        verifyNoInteractions(inventoryService);
    }

    @Test
    @WithMockUser(username = FLORIST, roles = "FLORIST")
    void writeOffRejectsANonPositiveQuantity() throws Exception {
        mockMvc.perform(post(BASE + "/write-offs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(writeOff(0, "spoiled"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validation.quantity").exists());

        verifyNoInteractions(inventoryService);
    }

    // ------------------------------------------------------------- alert settings

    @Test
    @WithMockUser(username = FLORIST, roles = "FLORIST")
    void theLowStockThresholdIsReplaced() throws Exception {
        when(inventoryService.updateLowStockThreshold(eq(FLORIST), eq(10L), any(LowStockThresholdRequest.class)))
                .thenReturn(sampleSummary());

        mockMvc.perform(put(BASE + "/low-stock-threshold")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(threshold(4))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lowStockThreshold").value(4));
    }

    @Test
    @WithMockUser(username = FLORIST, roles = "FLORIST")
    void theLowStockThresholdRejectsANegativeValue() throws Exception {
        mockMvc.perform(put(BASE + "/low-stock-threshold")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(threshold(-1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validation.lowStockThreshold").exists());

        verifyNoInteractions(inventoryService);
    }

    @Test
    @WithMockUser(username = FLORIST, roles = "FLORIST")
    void theLowStockThresholdIsRequired() throws Exception {
        mockMvc.perform(put(BASE + "/low-stock-threshold")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validation.lowStockThreshold").exists());

        verifyNoInteractions(inventoryService);
    }

    @Test
    @WithMockUser(username = FLORIST, roles = "FLORIST")
    void theExpiryDateIsReplaced() throws Exception {
        when(inventoryService.updateExpiryDate(eq(FLORIST), eq(10L), any(ExpiryDateRequest.class)))
                .thenReturn(InventorySummary.builder()
                        .productId(10L)
                        .quantity(12)
                        .reservedQuantity(3)
                        .available(9)
                        .lowStockThreshold(4)
                        .lowStock(false)
                        .expiryDate(LocalDate.of(2026, 4, 30))
                        .build());

        mockMvc.perform(put(BASE + "/expiry-date")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(expiry("2026-04-30"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiryDate").value("2026-04-30"));
    }

    @Test
    @WithMockUser(username = FLORIST, roles = "FLORIST")
    void theExpiryDateCanBeCleared() throws Exception {
        when(inventoryService.updateExpiryDate(eq(FLORIST), eq(10L), any(ExpiryDateRequest.class)))
                .thenReturn(sampleSummary());

        mockMvc.perform(put(BASE + "/expiry-date")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expiryDate\":null}"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = FLORIST, roles = "FLORIST")
    void anUnparseableExpiryDateIsRejected() throws Exception {
        mockMvc.perform(put(BASE + "/expiry-date")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expiryDate\":\"30-04-2026\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(inventoryService);
    }

    // ------------------------------------------------------------- listings

    @Test
    @WithMockUser(username = FLORIST, roles = "FLORIST")
    void theMovementHistoryDefaultsToTheFirstPageOfTwenty() throws Exception {
        when(inventoryService.listMovements(FLORIST, 10L, 0, 20)).thenReturn(sampleMovementPage());

        mockMvc.perform(get(BASE + "/movements"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].movementType").value("STOCK_IN"))
                .andExpect(jsonPath("$.content[0].quantityDelta").value(7))
                .andExpect(jsonPath("$.content[0].reason").value("Morning delivery"))
                .andExpect(jsonPath("$.content[0].actorEmail").value(FLORIST));
    }

    @Test
    @WithMockUser(username = FLORIST, roles = "FLORIST")
    void theMovementHistoryPassesItsPageThroughToTheService() throws Exception {
        when(inventoryService.listMovements(eq(FLORIST), eq(10L), anyInt(), anyInt()))
                .thenReturn(sampleMovementPage());

        mockMvc.perform(get(BASE + "/movements").param("page", "2").param("size", "5"))
                .andExpect(status().isOk());

        verify(inventoryService).listMovements(FLORIST, 10L, 2, 5);
    }

    @Test
    @WithMockUser(username = FLORIST, roles = "FLORIST")
    void theMovementHistoryRejectsAPageSizeAboveTheCap() throws Exception {
        mockMvc.perform(get(BASE + "/movements").param("size", "101"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(inventoryService);
    }

    @Test
    @WithMockUser(username = FLORIST, roles = "FLORIST")
    void theLowStockListingReturnsTheProductsThatCrossedTheirThreshold() throws Exception {
        when(inventoryService.listLowStock(FLORIST, 0, 20)).thenReturn(sampleLowStockPage());

        mockMvc.perform(get("/api/v1/vendors/inventory/low-stock"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].productId").value(10))
                .andExpect(jsonPath("$.content[0].productName").value("Red Rose Bunch"))
                .andExpect(jsonPath("$.content[0].available").value(1))
                .andExpect(jsonPath("$.content[0].lowStock").value(true));
    }

    @Test
    @WithMockUser(username = FLORIST, roles = "FLORIST")
    void theLowStockListingPassesItsPageThroughToTheService() throws Exception {
        when(inventoryService.listLowStock(eq(FLORIST), anyInt(), anyInt())).thenReturn(sampleLowStockPage());

        mockMvc.perform(get("/api/v1/vendors/inventory/low-stock").param("page", "1").param("size", "50"))
                .andExpect(status().isOk());

        verify(inventoryService).listLowStock(FLORIST, 1, 50);
    }

    @Test
    @WithMockUser(username = FLORIST, roles = "FLORIST")
    void theLowStockListingRejectsANegativePage() throws Exception {
        mockMvc.perform(get("/api/v1/vendors/inventory/low-stock").param("page", "-1"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(inventoryService);
    }

    // ------------------------------------------------------------- fixtures

    private StockInRequest stockIn(int quantity) {
        return StockInRequest.builder().quantity(quantity).reason("Morning delivery").build();
    }

    private StockOutRequest stockOut(int quantity) {
        return StockOutRequest.builder().quantity(quantity).build();
    }

    private StockAdjustmentRequest adjustment(int quantity, String reason) {
        return StockAdjustmentRequest.builder().quantity(quantity).reason(reason).build();
    }

    private StockWriteOffRequest writeOff(int quantity, String reason) {
        return StockWriteOffRequest.builder().quantity(quantity).reason(reason).build();
    }

    private LowStockThresholdRequest threshold(int value) {
        return LowStockThresholdRequest.builder().lowStockThreshold(value).build();
    }

    private ExpiryDateRequest expiry(String isoDate) {
        return ExpiryDateRequest.builder().expiryDate(LocalDate.parse(isoDate)).build();
    }

    private InventorySummary sampleSummary() {
        return InventorySummary.builder()
                .productId(10L)
                .quantity(12)
                .reservedQuantity(3)
                .available(9)
                .lowStockThreshold(4)
                .lowStock(false)
                .build();
    }

    private StockMovementPageResponse sampleMovementPage() {
        return StockMovementPageResponse.builder()
                .content(List.of(StockMovementResponse.builder()
                        .id(31L)
                        .productId(10L)
                        .movementType(MovementType.STOCK_IN)
                        .quantityDelta(7)
                        .reason("Morning delivery")
                        .actorUserId(11L)
                        .actorEmail(FLORIST)
                        .build()))
                .page(0)
                .size(20)
                .totalElements(1)
                .totalPages(1)
                .first(true)
                .last(true)
                .empty(false)
                .build();
    }

    private LowStockPageResponse sampleLowStockPage() {
        return LowStockPageResponse.builder()
                .content(List.of(LowStockProductResponse.builder()
                        .productId(10L)
                        .productName("Red Rose Bunch")
                        .quantity(2)
                        .reservedQuantity(1)
                        .available(1)
                        .lowStockThreshold(3)
                        .lowStock(true)
                        .build()))
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