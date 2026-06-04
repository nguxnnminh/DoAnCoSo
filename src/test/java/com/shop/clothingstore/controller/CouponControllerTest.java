package com.shop.clothingstore.controller;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import static org.mockito.Mockito.when;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.view.InternalResourceViewResolver;

import com.shop.clothingstore.entity.User;
import com.shop.clothingstore.service.CouponService;
import com.shop.clothingstore.service.UserService;

/**
 * MockMvc tests for the customer "my coupons" UC: listing, filter, sort,
 * and the not-authenticated guard.
 */
@SuppressWarnings("null")
class CouponControllerTest {

    private MockMvc mockMvc;

    @Mock private CouponService couponService;
    @Mock private UserService userService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new CouponController(couponService, userService))
                .setViewResolvers(new InternalResourceViewResolver())
                .build();
    }

    private Authentication auth() {
        return new UsernamePasswordAuthenticationToken("user@test.com", null, List.of());
    }

    @Test
    @DisplayName("GET /my-coupons renders coupons for the user")
    void myCoupons_Default_RendersView() throws Exception {
        User user = new User();
        user.setEmail("user@test.com");
        when(userService.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(couponService.getAllCouponsForUser(user)).thenReturn(List.of());

        mockMvc.perform(get("/my-coupons").principal(auth()))
                .andExpect(status().isOk())
                .andExpect(view().name("shop/my-coupons"))
                .andExpect(model().attributeExists("coupons", "filter", "sort", "totalAvailable"))
                .andExpect(model().attribute("filter", "all"))
                .andExpect(model().attribute("sort", "best"));
    }

    @Test
    @DisplayName("GET /my-coupons honours filter & sort params")
    void myCoupons_WithFilterSort_PassesThrough() throws Exception {
        User user = new User();
        user.setEmail("user@test.com");
        when(userService.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(couponService.getAllCouponsForUser(user)).thenReturn(List.of());

        mockMvc.perform(get("/my-coupons")
                        .param("filter", "available").param("sort", "expiring")
                        .principal(auth()))
                .andExpect(status().isOk())
                .andExpect(model().attribute("filter", "available"))
                .andExpect(model().attribute("sort", "expiring"));
    }

    @Test
    @DisplayName("GET /my-coupons unknown user redirects to login")
    void myCoupons_UserNotFound_RedirectsLogin() throws Exception {
        when(userService.findByEmail("user@test.com")).thenReturn(Optional.empty());

        mockMvc.perform(get("/my-coupons").principal(auth()))
                .andExpect(redirectedUrl("/login"));
    }
}
