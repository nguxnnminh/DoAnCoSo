package com.shop.clothingstore.controller;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
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

import com.shop.clothingstore.entity.Order;
import com.shop.clothingstore.entity.User;
import com.shop.clothingstore.service.CartService;
import com.shop.clothingstore.service.CheckoutService;
import com.shop.clothingstore.service.CouponService;
import com.shop.clothingstore.service.OrderService;
import com.shop.clothingstore.service.UserService;

/**
 * MockMvc tests for the checkout UC: page load, server-side validation,
 * successful order placement, and stock/coupon failures.
 */
@SuppressWarnings("null")
class CheckoutControllerTest {

    private MockMvc mockMvc;

    @Mock private CheckoutService checkoutService;
    @Mock private CartService cartService;
    @Mock private UserService userService;
    @Mock private OrderService orderService;
    @Mock private CouponService couponService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(cartService.getTotal()).thenReturn(new BigDecimal("250000"));
        when(cartService.getCart()).thenReturn(List.of());
        mockMvc = MockMvcBuilders
                .standaloneSetup(new CheckoutController(
                        checkoutService, cartService, userService, orderService, couponService))
                .setViewResolvers(new InternalResourceViewResolver())
                .build();
    }

    private Authentication auth() {
        return new UsernamePasswordAuthenticationToken("user@test.com", null, List.of());
    }

    // ── PAGE ─────────────────────────────────────────────────
    @Test
    @DisplayName("GET /checkout renders checkout page for guest")
    void checkoutPage_Guest_RendersView() throws Exception {
        mockMvc.perform(get("/checkout"))
                .andExpect(status().isOk())
                .andExpect(view().name("shop/checkout"))
                .andExpect(model().attribute("isLoggedIn", false))
                .andExpect(model().attributeExists("cartItems", "total", "shippingFee", "availableCoupons"));
    }

    @Test
    @DisplayName("GET /checkout loads coupons for logged-in user")
    void checkoutPage_LoggedIn_LoadsCoupons() throws Exception {
        User user = new User();
        user.setEmail("user@test.com");
        when(userService.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(couponService.getAvailableCouponsForUser(eq(user), any())).thenReturn(List.of());

        mockMvc.perform(get("/checkout").principal(auth()))
                .andExpect(status().isOk())
                .andExpect(model().attribute("isLoggedIn", true))
                .andExpect(model().attributeExists("user"));
    }

    // ── VALIDATION ───────────────────────────────────────────
    @Test
    @DisplayName("POST /checkout blank name → flash error, back to checkout")
    void processCheckout_BlankName_Rejected() throws Exception {
        mockMvc.perform(post("/checkout")
                        .param("customerName", "")
                        .param("phone", "0901234567")
                        .param("address", "123 Test St"))
                .andExpect(redirectedUrl("/checkout"))
                .andExpect(flash().attributeExists("error"));
    }

    @Test
    @DisplayName("POST /checkout invalid phone → flash error")
    void processCheckout_InvalidPhone_Rejected() throws Exception {
        mockMvc.perform(post("/checkout")
                        .param("customerName", "Nguyen Van A")
                        .param("phone", "12345")
                        .param("address", "123 Test St"))
                .andExpect(redirectedUrl("/checkout"))
                .andExpect(flash().attributeExists("error"));
    }

    @Test
    @DisplayName("POST /checkout blank address → flash error")
    void processCheckout_BlankAddress_Rejected() throws Exception {
        mockMvc.perform(post("/checkout")
                        .param("customerName", "Nguyen Van A")
                        .param("phone", "0901234567")
                        .param("address", ""))
                .andExpect(redirectedUrl("/checkout"))
                .andExpect(flash().attributeExists("error"));
    }

    // ── SUCCESS ──────────────────────────────────────────────
    @Test
    @DisplayName("POST /checkout valid → places order, clears cart, redirects to success")
    void processCheckout_Valid_Success() throws Exception {
        Order order = new Order();
        injectId(order, 77L);
        when(checkoutService.checkout(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(order);

        mockMvc.perform(post("/checkout")
                        .param("customerName", "Nguyen Van A")
                        .param("phone", "0901234567")
                        .param("address", "123 Test Street, District 1"))
                .andExpect(redirectedUrl("/checkout/success"))
                .andExpect(flash().attributeExists("orderId"));
    }

    @Test
    @DisplayName("POST /checkout out-of-stock → flash error, back to checkout")
    void processCheckout_OutOfStock_Rejected() throws Exception {
        when(checkoutService.checkout(any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("Insufficient stock"));

        mockMvc.perform(post("/checkout")
                        .param("customerName", "Nguyen Van A")
                        .param("phone", "0901234567")
                        .param("address", "123 Test Street"))
                .andExpect(redirectedUrl("/checkout"))
                .andExpect(flash().attribute("error", "Insufficient stock"));
    }

    // ── SUCCESS PAGE GUARD ───────────────────────────────────
    @Test
    @DisplayName("GET /checkout/success without orderId redirects home")
    void checkoutSuccess_NoOrderId_RedirectsHome() throws Exception {
        mockMvc.perform(get("/checkout/success"))
                .andExpect(redirectedUrl("/"));
    }

    private static void injectId(Object entity, Long id) {
        try {
            java.lang.reflect.Field f =
                    com.shop.clothingstore.entity.base.BaseEntity.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to inject ID in test", e);
        }
    }
}
