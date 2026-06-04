package com.shop.clothingstore.controller;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.view.InternalResourceViewResolver;

import com.shop.clothingstore.service.CartService;

/**
 * MockMvc tests for the shop cart UC: view, add, update, remove, clear.
 *
 * Uses standalone setup so only CartController is wired with a stub view
 * resolver — the real Thymeleaf layout (security dialect, _csrf, SSE) is never
 * rendered, keeping the test focused on controller logic (view name, model,
 * redirects, flash).
 */
@SuppressWarnings("null")
class CartControllerTest {

    private MockMvc mockMvc;

    @Mock
    private CartService cartService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new CartController(cartService))
                .setViewResolvers(new InternalResourceViewResolver())
                .build();
    }

    // ── VIEW CART ────────────────────────────────────────────
    @Test
    @DisplayName("GET /cart renders cart view with totals")
    void viewCart_ReturnsViewWithModel() throws Exception {
        when(cartService.getCart()).thenReturn(List.of());
        when(cartService.getTotal()).thenReturn(new BigDecimal("250000"));

        mockMvc.perform(get("/cart"))
                .andExpect(status().isOk())
                .andExpect(view().name("shop/cart"))
                .andExpect(model().attributeExists("cartItems", "total", "shippingFee", "grandTotal"));
    }

    @Test
    @DisplayName("GET /cart over free-ship threshold has zero shipping")
    void viewCart_OverThreshold_FreeShipping() throws Exception {
        when(cartService.getCart()).thenReturn(List.of());
        when(cartService.getTotal()).thenReturn(new BigDecimal("600000"));

        mockMvc.perform(get("/cart"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("shippingFee", BigDecimal.ZERO));
    }

    // ── ADD TO CART ──────────────────────────────────────────
    @Test
    @DisplayName("POST /cart/add with valid quantity adds and redirects")
    void addToCart_Valid_Redirects() throws Exception {
        doNothing().when(cartService).addToCart(anyLong(), anyInt());

        mockMvc.perform(post("/cart/add").param("variantId", "5").param("quantity", "2"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/cart"));

        verify(cartService).addToCart(5L, 2);
    }

    @Test
    @DisplayName("POST /cart/add with quantity 0 is rejected with flash error")
    void addToCart_ZeroQuantity_FlashError() throws Exception {
        mockMvc.perform(post("/cart/add").param("variantId", "5").param("quantity", "0"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/cart"))
                .andExpect(flash().attributeExists("error"));
    }

    @Test
    @DisplayName("POST /cart/add out-of-stock surfaces service error as flash")
    void addToCart_OutOfStock_FlashError() throws Exception {
        doThrow(new IllegalStateException("Out of stock"))
                .when(cartService).addToCart(anyLong(), anyInt());

        mockMvc.perform(post("/cart/add").param("variantId", "5").param("quantity", "3"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("error", "Out of stock"));
    }

    // ── UPDATE QUANTITY ──────────────────────────────────────
    @Test
    @DisplayName("POST /cart/update changes quantity and redirects")
    void updateCart_Valid_Redirects() throws Exception {
        doNothing().when(cartService).updateQuantity(anyLong(), anyInt());

        mockMvc.perform(post("/cart/update").param("variantId", "5").param("quantity", "4"))
                .andExpect(redirectedUrl("/cart"));

        verify(cartService).updateQuantity(5L, 4);
    }

    @Test
    @DisplayName("POST /cart/update with quantity > 99 rejected")
    void updateCart_TooMany_FlashError() throws Exception {
        mockMvc.perform(post("/cart/update").param("variantId", "5").param("quantity", "100"))
                .andExpect(redirectedUrl("/cart"))
                .andExpect(flash().attributeExists("error"));
    }

    // ── REMOVE / CLEAR ───────────────────────────────────────
    @Test
    @DisplayName("POST /cart/remove removes item and redirects")
    void removeFromCart_Redirects() throws Exception {
        mockMvc.perform(post("/cart/remove").param("variantId", "5"))
                .andExpect(redirectedUrl("/cart"));
        verify(cartService).remove(5L);
    }

    @Test
    @DisplayName("POST /cart/clear empties the cart and redirects")
    void clearCart_Redirects() throws Exception {
        mockMvc.perform(post("/cart/clear"))
                .andExpect(redirectedUrl("/cart"));
        verify(cartService).clear();
    }
}
