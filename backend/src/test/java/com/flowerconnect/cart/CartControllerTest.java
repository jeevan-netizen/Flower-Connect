package com.flowerconnect.cart.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowerconnect.cart.dto.AddCartItemRequest;
import com.flowerconnect.cart.dto.CartItemResponse;
import com.flowerconnect.cart.dto.CartResponse;
import com.flowerconnect.cart.dto.UpdateCartItemRequest;
import com.flowerconnect.cart.service.CartService;
import com.flowerconnect.config.TestClockConfig;
import com.flowerconnect.exception.BusinessException;
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

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice tests for {@link CartController} — HTTP status, RBAC boundary,
 * request validation, and delegation to {@code CartService}. The service is
 * mocked; integration-level behavior (eligibility, stock checks, customer
 * isolation) is covered by {@code CartApiIntegrationTest}.
 */
@WebMvcTest(controllers = CartController.class)
@Import({CartControllerTest.TestSecurityConfig.class, TestClockConfig.class})
class CartControllerTest {

    @TestConfiguration
    static class TestSecurityConfig {
        @Bean
        SecurityFilterChain testFilterChain(HttpSecurity http) throws Exception {
            http
                    .csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers("/api/v1/cart/**").hasRole("CUSTOMER")
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
    private CartService cartService;

    // ------------------------------------------------------- authorization

    @Test
    void getCartRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/cart"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(cartService);
    }

    @Test
    @WithMockUser(username = "vendor@test.com", roles = {"FLORIST"})
    void aFloristCannotAccessTheCart() throws Exception {
        mockMvc.perform(get("/api/v1/cart"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(cartService);
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = {"ADMIN"})
    void anAdminCannotAccessTheCart() throws Exception {
        mockMvc.perform(get("/api/v1/cart"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(cartService);
    }

    // ----------------------------------------------------------------- get

    @Test
    @WithMockUser(username = "customer@test.com", roles = {"CUSTOMER"})
    void getCartReturnsTheResponse() throws Exception {
        when(cartService.getCart("customer@test.com")).thenReturn(sampleCart());

        mockMvc.perform(get("/api/v1/cart"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.subtotal").value(598.00))
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.shops").isArray());

        verify(cartService).getCart("customer@test.com");
    }

    // ----------------------------------------------------------- add item

    @Test
    @WithMockUser(username = "customer@test.com", roles = {"CUSTOMER"})
    void addItemReturns201() throws Exception {
        AddCartItemRequest request = AddCartItemRequest.builder()
                .productId(10L)
                .quantity(2)
                .build();
        when(cartService.addItem(anyString(), any())).thenReturn(sampleItem());

        mockMvc.perform(post("/api/v1/cart/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.productId").value(10))
                .andExpect(jsonPath("$.quantity").value(2));

        verify(cartService).addItem(anyString(), any());
    }

    @Test
    @WithMockUser(username = "customer@test.com", roles = {"CUSTOMER"})
    void addItemRejectsMissingProductId() throws Exception {
        AddCartItemRequest request = AddCartItemRequest.builder()
                .quantity(2)
                .build();

        mockMvc.perform(post("/api/v1/cart/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(cartService);
    }

    @Test
    @WithMockUser(username = "customer@test.com", roles = {"CUSTOMER"})
    void addItemRejectsZeroQuantity() throws Exception {
        String body = "{\"productId\":10,\"quantity\":0}";

        mockMvc.perform(post("/api/v1/cart/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(cartService);
    }

    @Test
    @WithMockUser(username = "customer@test.com", roles = {"CUSTOMER"})
    void addItemRejectsUnknownProductWith404() throws Exception {
        doThrow(BusinessException.notFound("Product not found"))
                .when(cartService).addItem(anyString(), any());

        mockMvc.perform(post("/api/v1/cart/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":10,\"quantity\":2}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @WithMockUser(username = "customer@test.com", roles = {"CUSTOMER"})
    void addItemRejectsUnapprovedVendorWith403() throws Exception {
        doThrow(BusinessException.forbidden("Vendor is not approved"))
                .when(cartService).addItem(anyString(), any());

        mockMvc.perform(post("/api/v1/cart/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":10,\"quantity\":2}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @WithMockUser(username = "customer@test.com", roles = {"CUSTOMER"})
    void addItemRejectsInsufficientStockWith409() throws Exception {
        doThrow(BusinessException.insufficientStock("Only 1 units available"))
                .when(cartService).addItem(anyString(), any());

        mockMvc.perform(post("/api/v1/cart/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":10,\"quantity\":2}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));
    }

    // ----------------------------------------------------------- update item

    @Test
    @WithMockUser(username = "customer@test.com", roles = {"CUSTOMER"})
    void updateItemReturns200() throws Exception {
        UpdateCartItemRequest request = UpdateCartItemRequest.builder().quantity(5).build();
        CartItemResponse updated = sampleItem();
        updated.setQuantity(5);
        when(cartService.updateItem(anyString(), anyLong(), any())).thenReturn(updated);

        mockMvc.perform(patch("/api/v1/cart/items/5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(5));

        verify(cartService).updateItem(anyString(), anyLong(), any());
    }

    @Test
    @WithMockUser(username = "customer@test.com", roles = {"CUSTOMER"})
    void updateItemWithQuantityZeroReturns204() throws Exception {
        when(cartService.updateItem(anyString(), anyLong(), any())).thenReturn(null);

        mockMvc.perform(patch("/api/v1/cart/items/5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":0}"))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(username = "customer@test.com", roles = {"CUSTOMER"})
    void updateItemRejectsMissingQuantity() throws Exception {
        mockMvc.perform(patch("/api/v1/cart/items/5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "customer@test.com", roles = {"CUSTOMER"})
    void updateItemRejectsNegativeQuantity() throws Exception {
        mockMvc.perform(patch("/api/v1/cart/items/5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":-1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "customer@test.com", roles = {"CUSTOMER"})
    void updateItemWithUnknownIdReturns404() throws Exception {
        doThrow(BusinessException.notFound("Cart item not found"))
                .when(cartService).updateItem(anyString(), anyLong(), any());

        mockMvc.perform(patch("/api/v1/cart/items/999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":3}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @WithMockUser(username = "customer@test.com", roles = {"CUSTOMER"})
    void updateItemRejectsNonPositiveId() throws Exception {
        mockMvc.perform(patch("/api/v1/cart/items/0")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":3}"))
                .andExpect(status().isBadRequest());
    }

    // -------------------------------------------------------- remove item

    @Test
    @WithMockUser(username = "customer@test.com", roles = {"CUSTOMER"})
    void removeItemReturns204() throws Exception {
        doNothing().when(cartService).removeItem(anyString(), anyLong());

        mockMvc.perform(delete("/api/v1/cart/items/5"))
                .andExpect(status().isNoContent());

        verify(cartService).removeItem(anyString(), anyLong());
    }

    @Test
    @WithMockUser(username = "customer@test.com", roles = {"CUSTOMER"})
    void removeItemWithUnknownIdReturns404() throws Exception {
        doThrow(BusinessException.notFound("Cart item not found"))
                .when(cartService).removeItem(anyString(), anyLong());

        mockMvc.perform(delete("/api/v1/cart/items/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    // ------------------------------------------------------------- clear

    @Test
    @WithMockUser(username = "customer@test.com", roles = {"CUSTOMER"})
    void clearCartReturns204() throws Exception {
        doNothing().when(cartService).clearCart(anyString());

        mockMvc.perform(delete("/api/v1/cart"))
                .andExpect(status().isNoContent());

        verify(cartService).clearCart(anyString());
    }

    // -----------------------------------------------------------------

    private static CartResponse sampleCart() {
        return CartResponse.builder()
                .cartId(1L)
                .totalItems(2)
                .subtotal(new BigDecimal("598.00"))
                .items(List.of(sampleItem(), sampleItem()))
                .shops(List.of())
                .build();
    }

    private static CartItemResponse sampleItem() {
        return CartItemResponse.builder()
                .id(5L)
                .productId(10L)
                .productName("Red Rose Bunch")
                .vendorId(3L)
                .quantity(2)
                .priceSnapshot(new BigDecimal("299.00"))
                .subtotal(new BigDecimal("598.00"))
                .purchasable(true)
                .build();
    }
}
