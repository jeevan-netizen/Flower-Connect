package com.flowerconnect.customer.order.controller;

import com.flowerconnect.config.TestClockConfig;
import com.flowerconnect.customer.order.dto.CustomerOrderItemResponse;
import com.flowerconnect.customer.order.dto.CustomerOrderListItem;
import com.flowerconnect.customer.order.dto.CustomerOrderPageResponse;
import com.flowerconnect.customer.order.dto.CustomerOrderResponse;
import com.flowerconnect.customer.order.service.CustomerOrderService;
import com.flowerconnect.order.domain.Order.OrderStatus;
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
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer coverage for the customer order API (plan Phase 6, task 6.3).
 *
 * <p>The controller is thin: every read is delegated to
 * {@link CustomerOrderService}. This slice therefore mocks the service
 * and asserts HTTP wiring, status codes and the error envelope.
 *
 * <p>The production {@code SecurityConfig} is not loaded in a slice, so a
 * {@link TestSecurityConfig} mirrors the production CUSTOMER namespace rule
 * for the {@code /api/v1/orders/} base.
 */
@WebMvcTest(controllers = CustomerOrderController.class)
@Import({CustomerOrderControllerTest.TestSecurityConfig.class, TestClockConfig.class})
class CustomerOrderControllerTest {

    @TestConfiguration
    static class TestSecurityConfig {
        @Bean
        SecurityFilterChain testFilterChain(HttpSecurity http) throws Exception {
            http
                    .csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers("/api/v1/orders/**").hasRole("CUSTOMER")
                            .anyRequest().authenticated())
                    .httpBasic(Customizer.withDefaults());
            return http.build();
        }
    }

    private static final String CUSTOMER = "customer@test.com";
    private static final String BASE = "/api/v1/orders";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CustomerOrderService customerOrderService;

    private RoleBoundaryTester roleBoundaryTester;

    @BeforeEach
    void init() {
        roleBoundaryTester = new RoleBoundaryTester(mockMvc);
    }

    // ------------------------------------------------------------- role boundary

    @Test
    void everyEndpointRefusesNonCustomersAndAnonymousCallers() throws Exception {
        when(customerOrderService.listOrders(anyString(), any(), any()))
                .thenReturn(samplePage());
        when(customerOrderService.getOrder(anyString(), anyLong()))
                .thenReturn(sampleResponse());

        roleBoundaryTester.assertUnauthenticated(BASE, HttpMethod.GET, 401);
        roleBoundaryTester.assertAllRoles(BASE, HttpMethod.GET, 200, 403, 403);

        roleBoundaryTester.assertUnauthenticated(BASE + "/1", HttpMethod.GET, 401);
        roleBoundaryTester.assertAllRoles(BASE + "/1", HttpMethod.GET, 200, 403, 403);
    }

    // ------------------------------------------------------------- list

    @Test
    @WithMockUser(username = CUSTOMER, roles = "CUSTOMER")
    void listOrdersReturnsTheCustomersOwnPage() throws Exception {
        when(customerOrderService.listOrders(eq(CUSTOMER), any(), any()))
                .thenReturn(samplePage());

        mockMvc.perform(get(BASE).param("page", "0").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.content.length()").value(1));

        verify(customerOrderService).listOrders(eq(CUSTOMER), any(), any());
    }

    @Test
    @WithMockUser(username = CUSTOMER, roles = "CUSTOMER")
    void listOrdersPassesStatusFilter() throws Exception {
        when(customerOrderService.listOrders(eq(CUSTOMER), eq(OrderStatus.PLACED), any()))
                .thenReturn(samplePage());

        mockMvc.perform(get(BASE).param("status", "PLACED"))
                .andExpect(status().isOk());

        verify(customerOrderService).listOrders(eq(CUSTOMER), eq(OrderStatus.PLACED), any());
    }

    @Test
    @WithMockUser(username = CUSTOMER, roles = "CUSTOMER")
    void listOrdersRejectsAnInvalidPageSize() throws Exception {
        mockMvc.perform(get(BASE).param("size", "101")).andExpect(status().isBadRequest());
        verifyNoInteractions(customerOrderService);
    }

    // ------------------------------------------------------------- detail

    @Test
    @WithMockUser(username = CUSTOMER, roles = "CUSTOMER")
    void getOrderReturnsTheCustomersOwnOrder() throws Exception {
        when(customerOrderService.getOrder(eq(CUSTOMER), eq(1L)))
                .thenReturn(sampleResponse());

        mockMvc.perform(get(BASE + "/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("PLACED"))
                .andExpect(jsonPath("$.vendorName").value("Test Florist"));

        verify(customerOrderService).getOrder(CUSTOMER, 1L);
    }

    @Test
    @WithMockUser(username = CUSTOMER, roles = "CUSTOMER")
    void getOrderRejectsANonPositiveIdBeforeTheService() throws Exception {
        mockMvc.perform(get(BASE + "/0")).andExpect(status().isBadRequest());
        verifyNoInteractions(customerOrderService);
    }

    // ------------------------------------------------------------- fixtures

    private CustomerOrderResponse sampleResponse() {
        return CustomerOrderResponse.builder()
                .id(1L)
                .status(OrderStatus.PLACED)
                .vendorName("Test Florist")
                .total(BigDecimal.valueOf(199.00))
                .items(List.of(
                        CustomerOrderItemResponse.builder()
                                .productName("Red Roses")
                                .quantity(2)
                                .priceSnapshot(BigDecimal.valueOf(99.50))
                                .subtotal(BigDecimal.valueOf(199.00))
                                .note(null)
                                .build()))
                .build();
    }

    private CustomerOrderPageResponse samplePage() {
        return CustomerOrderPageResponse.builder()
                .content(List.of(sampleListItem()))
                .page(0)
                .size(5)
                .totalElements(1)
                .totalPages(1)
                .first(true)
                .last(true)
                .empty(false)
                .build();
    }

    private CustomerOrderListItem sampleListItem() {
        return CustomerOrderListItem.builder()
                .id(1L)
                .status(OrderStatus.PLACED)
                .vendorName("Test Florist")
                .total(BigDecimal.valueOf(199.00))
                .build();
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
