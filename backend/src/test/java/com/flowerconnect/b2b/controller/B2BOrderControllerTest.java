package com.flowerconnect.b2b.controller;

import com.flowerconnect.b2b.domain.B2BOrder.Status;
import com.flowerconnect.b2b.dto.B2BOrderActionRequest;
import com.flowerconnect.b2b.dto.B2BOrderItemRequest;
import com.flowerconnect.b2b.dto.B2BOrderPageResponse;
import com.flowerconnect.b2b.dto.B2BOrderResponse;
import com.flowerconnect.b2b.dto.PlaceB2BOrderRequest;
import com.flowerconnect.b2b.service.B2BEbillService;
import com.flowerconnect.b2b.service.B2BOrderService;
import com.flowerconnect.exception.BusinessException;
import com.flowerconnect.exception.ErrorCode;
import com.flowerconnect.test.RoleBoundaryTester;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer coverage for the B2B order controller (Stage 1 of the B2B module).
 *
 * <p>The controller is thin: every action is delegated to
 * {@link B2BOrderService}. This slice mocks the service and asserts HTTP
 * wiring, status codes and the error envelope.
 *
 * <p>The production {@code SecurityConfig} is not loaded in a slice, so a
 * {@link TestSecurityConfig} mirrors the production FLORIST namespace rule
 * for the {@code /api/v1/vendors/b2b/} base. The approval gate
 * ({@code @RequiresApprovedVendor}) is deliberately not asserted here; it is
 * covered by {@code B2BOrderIntegrationTest} against the real filter chain.
 */
@WebMvcTest(controllers = B2BOrderController.class)
@Import({B2BOrderControllerTest.TestSecurityConfig.class, com.flowerconnect.config.TestClockConfig.class})
class B2BOrderControllerTest {

    @TestConfiguration
    static class TestSecurityConfig {
        @Bean
        SecurityFilterChain testFilterChain(HttpSecurity http) throws Exception {
            http
                    .csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers("/api/v1/vendors/b2b/**").hasRole("FLORIST")
                            .anyRequest().authenticated())
                    .httpBasic(Customizer.withDefaults());
            return http.build();
        }
    }

    private static final String FLORIST = "florist@test.com";
    private static final String BASE = "/api/v1/vendors/b2b";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private B2BOrderService b2bOrderService;
    @MockBean
    private B2BEbillService b2bEbillService;

    private RoleBoundaryTester roleBoundaryTester;

    @BeforeEach
    void init() {
        roleBoundaryTester = new RoleBoundaryTester(mockMvc);
    }

    // ------------------------------------------------------------- role boundary

    @Test
    void everyEndpointRefusesNonVendorsAndAnonymousCallers() throws Exception {
        when(b2bOrderService.searchSellers(any(), any()))
                .thenReturn(List.of());
        when(b2bOrderService.listSellerProducts(any(), anyLong()))
                .thenReturn(List.of());
        when(b2bOrderService.placeOrder(any(), any(PlaceB2BOrderRequest.class)))
                .thenReturn(sampleResponse(Status.PLACED));
        when(b2bOrderService.listOrders(any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(samplePage());
        when(b2bOrderService.getOrder(any(), anyLong()))
                .thenReturn(sampleResponse(Status.PLACED));
        when(b2bOrderService.accept(any(), anyLong()))
                .thenReturn(sampleResponse(Status.ACCEPTED));
        when(b2bOrderService.reject(any(), anyLong(), anyString()))
                .thenReturn(sampleResponse(Status.REJECTED));
        when(b2bOrderService.cancel(any(), anyLong(), anyString()))
                .thenReturn(sampleResponse(Status.CANCELLED));
        when(b2bOrderService.ship(any(), anyLong(), any(B2BOrderActionRequest.class)))
                .thenReturn(sampleResponse(Status.SHIPPED));
        when(b2bOrderService.deliver(any(), anyLong()))
                .thenReturn(sampleResponse(Status.DELIVERED));
        when(b2bEbillService.generateEbillPdf(any(), anyLong()))
                .thenReturn(new byte[]{1, 2, 3});

        roleBoundaryTester.assertUnauthenticated(BASE + "/sellers", HttpMethod.GET, 401);
        roleBoundaryTester.assertAllRoles(BASE + "/sellers", HttpMethod.GET, 403, 200, 403);

        roleBoundaryTester.assertUnauthenticated(BASE + "/sellers/1/products", HttpMethod.GET, 401);
        roleBoundaryTester.assertAllRoles(BASE + "/sellers/1/products", HttpMethod.GET, 403, 200, 403);

        roleBoundaryTester.assertUnauthenticated(BASE + "/orders", HttpMethod.POST, 401);
        roleBoundaryTester.assertAllRoles(BASE + "/orders", HttpMethod.POST, 403, 400, 403);

        roleBoundaryTester.assertUnauthenticated(BASE + "/orders", HttpMethod.GET, 401);

        roleBoundaryTester.assertUnauthenticated(BASE + "/orders/1", HttpMethod.GET, 401);
        roleBoundaryTester.assertAllRoles(BASE + "/orders/1", HttpMethod.GET, 403, 200, 403);

        roleBoundaryTester.assertUnauthenticated(BASE + "/orders/1/accept", HttpMethod.POST, 401);
        roleBoundaryTester.assertAllRoles(BASE + "/orders/1/accept", HttpMethod.POST, 403, 200, 403);

        roleBoundaryTester.assertUnauthenticated(BASE + "/orders/1/reject", HttpMethod.POST, 401);
        roleBoundaryTester.assertAllRoles(BASE + "/orders/1/reject", HttpMethod.POST, 403, 400, 403);

        roleBoundaryTester.assertUnauthenticated(BASE + "/orders/1/cancel", HttpMethod.POST, 401);
        roleBoundaryTester.assertAllRoles(BASE + "/orders/1/cancel", HttpMethod.POST, 403, 400, 403);

        roleBoundaryTester.assertUnauthenticated(BASE + "/orders/1/ship", HttpMethod.POST, 401);
        roleBoundaryTester.assertAllRoles(BASE + "/orders/1/ship", HttpMethod.POST, 403, 400, 403);

        roleBoundaryTester.assertUnauthenticated(BASE + "/orders/1/deliver", HttpMethod.POST, 401);
        roleBoundaryTester.assertAllRoles(BASE + "/orders/1/deliver", HttpMethod.POST, 403, 200, 403);

        roleBoundaryTester.assertUnauthenticated(BASE + "/orders/1/e-bill", HttpMethod.GET, 401);
        roleBoundaryTester.assertAllRoles(BASE + "/orders/1/e-bill", HttpMethod.GET, 403, 200, 403);
    }

    // ------------------------------------------------------------- actions

    @Test
    @WithMockUser(username = FLORIST, roles = "FLORIST")
    void searchSellersReturnsServiceResult() throws Exception {
        when(b2bOrderService.searchSellers(eq(FLORIST), anyString()))
                .thenReturn(List.of());

        mockMvc.perform(get(BASE + "/sellers").param("query", "Rose"))
                .andExpect(status().isOk());

        verify(b2bOrderService).searchSellers(FLORIST, "Rose");
    }

    @Test
    @WithMockUser(username = FLORIST, roles = "FLORIST")
    void placeOrderReturnsCreatedOrder() throws Exception {
        when(b2bOrderService.placeOrder(eq(FLORIST), any(PlaceB2BOrderRequest.class)))
                .thenReturn(sampleResponse(Status.PLACED));

        PlaceB2BOrderRequest request = new PlaceB2BOrderRequest();
        request.setSellerId(2L);
        request.setItems(List.of(new B2BOrderItemRequest(1L, 3)));

        mockMvc.perform(post(BASE + "/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PLACED"));

        verify(b2bOrderService).placeOrder(eq(FLORIST), any(PlaceB2BOrderRequest.class));
    }

    @Test
    @WithMockUser(username = FLORIST, roles = "FLORIST")
    void acceptReturnsTheAcceptedOrder() throws Exception {
        when(b2bOrderService.accept(eq(FLORIST), eq(1L)))
                .thenReturn(sampleResponse(Status.ACCEPTED));

        mockMvc.perform(post(BASE + "/orders/1/accept"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        verify(b2bOrderService).accept(FLORIST, 1L);
    }

    @Test
    @WithMockUser(username = FLORIST, roles = "FLORIST")
    void shipPassesRequestToService() throws Exception {
        when(b2bOrderService.ship(eq(FLORIST), eq(1L), any(B2BOrderActionRequest.class)))
                .thenReturn(sampleResponse(Status.SHIPPED));

        B2BOrderActionRequest request = new B2BOrderActionRequest();
        request.setShipmentReference("TRACK-123");

        mockMvc.perform(post(BASE + "/orders/1/ship")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SHIPPED"));

        verify(b2bOrderService).ship(eq(FLORIST), eq(1L), any(B2BOrderActionRequest.class));
    }

    @Test
    @WithMockUser(username = FLORIST, roles = "FLORIST")
    void listOrdersReturnsPage() throws Exception {
        when(b2bOrderService.listOrders(eq(FLORIST), any(), any(), anyInt(), anyInt()))
                .thenReturn(samplePage());

        mockMvc.perform(get(BASE + "/orders")
                        .param("page", "0").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.content.length()").value(1));

        verify(b2bOrderService).listOrders(FLORIST, null, null, 0, 5);
    }

    @Test
    @WithMockUser(username = FLORIST, roles = "FLORIST")
    void eBillReturnsPdf() throws Exception {
        when(b2bEbillService.generateEbillPdf(eq(FLORIST), eq(1L)))
                .thenReturn("test-pdf".getBytes());
        B2BOrderResponse order = sampleResponse(Status.DELIVERED);
        order.setBillNumber("EB-2026-000010");
        when(b2bOrderService.getOrder(eq(FLORIST), eq(1L))).thenReturn(order);

        mockMvc.perform(get(BASE + "/orders/1/e-bill"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        org.hamcrest.Matchers.containsString("EB-2026-000010.pdf")));

        verify(b2bEbillService).generateEbillPdf(FLORIST, 1L);
    }

    @Test
    @WithMockUser(username = FLORIST, roles = "FLORIST")
    void eBillRefusedWhenNotDelivered() throws Exception {
        when(b2bEbillService.generateEbillPdf(eq(FLORIST), eq(1L)))
                .thenThrow(new BusinessException(ErrorCode.E_BILL_NOT_AVAILABLE,
                        "E-bill is only available for delivered orders"));

        mockMvc.perform(get(BASE + "/orders/1/e-bill"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("E_BILL_NOT_AVAILABLE"));

        verify(b2bEbillService).generateEbillPdf(FLORIST, 1L);
    }

    // ------------------------------------------------------------- fixtures

    private B2BOrderResponse sampleResponse(Status status) {
        return B2BOrderResponse.builder()
                .id(1L)
                .orderNo("B2B-1")
                .status(status)
                .paymentStatus(com.flowerconnect.b2b.domain.B2BOrder.PaymentStatus.UNPAID)
                .total(BigDecimal.valueOf(199.00))
                .build();
    }

    private B2BOrderPageResponse samplePage() {
        return B2BOrderPageResponse.builder()
                .content(List.of(sampleResponse(Status.PLACED)))
                .page(0)
                .size(5)
                .totalElements(1)
                .totalPages(1)
                .first(true)
                .last(true)
                .empty(false)
                .build();
    }
}
