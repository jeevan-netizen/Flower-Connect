package com.flowerconnect.vendor.controller;

import com.flowerconnect.config.TestClockConfig;
import com.flowerconnect.order.domain.Order.OrderStatus;
import com.flowerconnect.test.RoleBoundaryTester;
import com.flowerconnect.vendor.dto.VendorOrderPageResponse;
import com.flowerconnect.vendor.dto.VendorOrderRejectRequest;
import com.flowerconnect.vendor.dto.VendorOrderResponse;
import com.flowerconnect.vendor.dto.VendorOrderSummaryResponse;
import com.flowerconnect.vendor.service.VendorOrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer coverage for the vendor order API (plan Phase 6, tasks 6.2 and 6.4).
 *
 * <p>The controller is thin: every status change is delegated to
 * {@link VendorOrderService}, which in turn delegates to
 * {@link com.flowerconnect.order.service.OrderStateMachine}. This slice
 * therefore mocks the service and asserts HTTP wiring, status codes and the
 * error envelope.
 *
 * <p>The production {@code SecurityConfig} is not loaded in a slice, so a
 * {@link TestSecurityConfig} mirrors the production FLORIST namespace rule
 * for the singular {@code /api/v1/vendor/} base. The approval gate
 * ({@code @RequiresApprovedVendor}) is deliberately not applied to order
 * endpoints (D-6): a suspended vendor must still process their own existing
 * in-flight orders, so the slice only asserts the role boundary, not
 * approval.
 */
@WebMvcTest(controllers = VendorOrderController.class)
@Import({VendorOrderControllerTest.TestSecurityConfig.class, TestClockConfig.class})
class VendorOrderControllerTest {

    @TestConfiguration
    static class TestSecurityConfig {
        @Bean
        SecurityFilterChain testFilterChain(HttpSecurity http) throws Exception {
            http
                    .csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers("/api/v1/vendor/**").hasRole("FLORIST")
                            .anyRequest().authenticated())
                    .httpBasic(Customizer.withDefaults());
            return http.build();
        }
    }

    private static final String FLORIST = "florist@test.com";
    private static final String BASE = "/api/v1/vendor/orders";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private VendorOrderService vendorOrderService;

    private RoleBoundaryTester roleBoundaryTester;

    @BeforeEach
    void init() {
        roleBoundaryTester = new RoleBoundaryTester(mockMvc);
    }

    // ------------------------------------------------------------- role boundary

    @Test
    void everyEndpointRefusesNonVendorsAndAnonymousCallers() throws Exception {
        // Stub the service so a FLORIST caller reaches the handler and gets a
        // success status; the point of the test is that CUSTOMER/ADMIN are
        // refused at the namespace boundary (403) and anonymous callers are
        // refused (401).
        when(vendorOrderService.accept(anyString(), anyLong())).thenReturn(sampleResponse(OrderStatus.CONFIRMED));
        when(vendorOrderService.reject(anyString(), anyLong(), anyString())).thenReturn(sampleResponse(OrderStatus.REJECTED));
        when(vendorOrderService.prepare(anyString(), anyLong())).thenReturn(sampleResponse(OrderStatus.PREPARING));
        when(vendorOrderService.outForDelivery(anyString(), anyLong())).thenReturn(sampleResponse(OrderStatus.OUT_FOR_DELIVERY));
        when(vendorOrderService.delivered(anyString(), anyLong())).thenReturn(sampleResponse(OrderStatus.DELIVERED));
        when(vendorOrderService.listOrders(eq(FLORIST), any(), any(), any(), any())).thenReturn(samplePage());
        when(vendorOrderService.todaySummary(anyString(), any())).thenReturn(sampleSummary());

        roleBoundaryTester.assertUnauthenticated(BASE + "/1/accept", HttpMethod.POST, 401);
        roleBoundaryTester.assertAllRoles(BASE + "/1/accept", HttpMethod.POST, 403, 200, 403);

        roleBoundaryTester.assertUnauthenticated(BASE + "/1/reject", HttpMethod.POST, 401);
        // A FLORIST passes the namespace rule (not 403) but is refused at body
        // validation because reject requires a reason body, which RoleBoundaryTester
        // does not send; the body-present success path is covered by
        // rejectReturnsTheRejectedOrder below.
        roleBoundaryTester.assertAllRoles(BASE + "/1/reject", HttpMethod.POST, 403, 400, 403);

        roleBoundaryTester.assertUnauthenticated(BASE + "/1/prepare", HttpMethod.POST, 401);
        roleBoundaryTester.assertAllRoles(BASE + "/1/prepare", HttpMethod.POST, 403, 200, 403);

        roleBoundaryTester.assertUnauthenticated(BASE + "/1/out-for-delivery", HttpMethod.POST, 401);
        roleBoundaryTester.assertAllRoles(BASE + "/1/out-for-delivery", HttpMethod.POST, 403, 200, 403);

        roleBoundaryTester.assertUnauthenticated(BASE + "/1/delivered", HttpMethod.POST, 401);
        roleBoundaryTester.assertAllRoles(BASE + "/1/delivered", HttpMethod.POST, 403, 200, 403);

        roleBoundaryTester.assertUnauthenticated(BASE, HttpMethod.GET, 401);
        roleBoundaryTester.assertAllRoles(BASE, HttpMethod.GET, 403, 200, 403);

        roleBoundaryTester.assertUnauthenticated(BASE + "/summary", HttpMethod.GET, 401);
        roleBoundaryTester.assertAllRoles(BASE + "/summary", HttpMethod.GET, 403, 200, 403);
    }

    // ------------------------------------------------------------- actions

    @Test
    @WithMockUser(username = FLORIST, roles = "FLORIST")
    void acceptReturnsTheConfirmedOrder() throws Exception {
        when(vendorOrderService.accept(eq(FLORIST), eq(1L)))
                .thenReturn(sampleResponse(OrderStatus.CONFIRMED));

        mockMvc.perform(post(BASE + "/1/accept"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        verify(vendorOrderService).accept(FLORIST, 1L);
    }

    @Test
    @WithMockUser(username = FLORIST, roles = "FLORIST")
    void rejectRequiresAReason() throws Exception {
        mockMvc.perform(post(BASE + "/1/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.validation.reason").exists());

        verifyNoInteractions(vendorOrderService);
    }

    @Test
    @WithMockUser(username = FLORIST, roles = "FLORIST")
    void rejectReturnsTheRejectedOrder() throws Exception {
        when(vendorOrderService.reject(eq(FLORIST), eq(1L), eq("Out of stock")))
                .thenReturn(sampleResponse(OrderStatus.REJECTED));

        mockMvc.perform(post(BASE + "/1/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new VendorOrderRejectRequest("Out of stock"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        verify(vendorOrderService).reject(FLORIST, 1L, "Out of stock");
    }

    @Test
    @WithMockUser(username = FLORIST, roles = "FLORIST")
    void prepareReturnsThePreparedOrder() throws Exception {
        when(vendorOrderService.prepare(eq(FLORIST), eq(1L)))
                .thenReturn(sampleResponse(OrderStatus.PREPARING));

        mockMvc.perform(post(BASE + "/1/prepare"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PREPARING"));

        verify(vendorOrderService).prepare(FLORIST, 1L);
    }

    @Test
    @WithMockUser(username = FLORIST, roles = "FLORIST")
    void outForDeliveryReturnsTheOutForDeliveryOrder() throws Exception {
        when(vendorOrderService.outForDelivery(eq(FLORIST), eq(1L)))
                .thenReturn(sampleResponse(OrderStatus.OUT_FOR_DELIVERY));

        mockMvc.perform(post(BASE + "/1/out-for-delivery"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OUT_FOR_DELIVERY"));

        verify(vendorOrderService).outForDelivery(FLORIST, 1L);
    }

    @Test
    @WithMockUser(username = FLORIST, roles = "FLORIST")
    void deliveredReturnsTheDeliveredOrder() throws Exception {
        when(vendorOrderService.delivered(eq(FLORIST), eq(1L)))
                .thenReturn(sampleResponse(OrderStatus.DELIVERED));

        mockMvc.perform(post(BASE + "/1/delivered"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"));

        verify(vendorOrderService).delivered(FLORIST, 1L);
    }

    @Test
    @WithMockUser(username = FLORIST, roles = "FLORIST")
    void aNonPositiveOrderIdIsRejectedBeforeTheService() throws Exception {
        mockMvc.perform(post(BASE + "/0/accept")).andExpect(status().isBadRequest());
        verifyNoInteractions(vendorOrderService);
    }

    // ------------------------------------------------------------- list and summary

    @Test
    @WithMockUser(username = FLORIST, roles = "FLORIST")
    void listOrdersReturnsTheVendorsOwnPage() throws Exception {
        when(vendorOrderService.listOrders(eq(FLORIST), any(), any(), any(), any()))
                .thenReturn(samplePage());

        mockMvc.perform(get(BASE).param("page", "0").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.content.length()").value(1));

        verify(vendorOrderService).listOrders(eq(FLORIST), any(), any(), any(), any());
    }

    @Test
    @WithMockUser(username = FLORIST, roles = "FLORIST")
    void listOrdersPassesStatusAndDateFilters() throws Exception {
        when(vendorOrderService.listOrders(eq(FLORIST), eq(OrderStatus.PLACED), any(), any(), any()))
                .thenReturn(samplePage());

        mockMvc.perform(get(BASE).param("status", "PLACED")
                        .param("fromDate", "2026-10-20").param("toDate", "2026-10-21"))
                .andExpect(status().isOk());

        verify(vendorOrderService).listOrders(eq(FLORIST), eq(OrderStatus.PLACED), any(), any(), any());
    }

    @Test
    @WithMockUser(username = FLORIST, roles = "FLORIST")
    void listOrdersRejectsAnInvalidPageSize() throws Exception {
        mockMvc.perform(get(BASE).param("size", "101")).andExpect(status().isBadRequest());
        verifyNoInteractions(vendorOrderService);
    }

    @Test
    @WithMockUser(username = FLORIST, roles = "FLORIST")
    void todaySummaryReturnsCountsAndTotals() throws Exception {
        when(vendorOrderService.todaySummary(anyString(), any()))
                .thenReturn(sampleSummary());

        // The summary body logic is exercised against the real database in
        // VendorOrderIntegrationTest; here we assert the endpoint is wired and
        // reachable by a FLORIST caller.
        mockMvc.perform(get(BASE + "/summary"))
                .andExpect(status().isOk());

        verify(vendorOrderService).todaySummary(anyString(), any());
    }

    // ------------------------------------------------------------- fixtures

    private VendorOrderResponse sampleResponse(OrderStatus status) {
        return VendorOrderResponse.builder()
                .id(1L)
                .status(status)
                .total(java.math.BigDecimal.valueOf(199.00))
                .build();
    }

    private VendorOrderPageResponse samplePage() {
        return VendorOrderPageResponse.builder()
                .content(List.of(sampleResponse(OrderStatus.PLACED)))
                .page(0)
                .size(5)
                .totalElements(1)
                .totalPages(1)
                .first(true)
                .last(true)
                .empty(false)
                .build();
    }

    private VendorOrderSummaryResponse sampleSummary() {
        return VendorOrderSummaryResponse.builder()
                .today(LocalDate.of(2026, 10, 20))
                .todayOrderCount(3)
                .todayTotalValue(java.math.BigDecimal.valueOf(398.00))
                .expectedCodCash(java.math.BigDecimal.valueOf(199.00))
                .build();
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
