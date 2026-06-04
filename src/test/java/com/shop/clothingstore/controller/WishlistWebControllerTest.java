package com.shop.clothingstore.controller;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import static org.mockito.Mockito.verify;
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

import com.shop.clothingstore.entity.Role;
import com.shop.clothingstore.entity.User;
import com.shop.clothingstore.service.UserService;
import com.shop.clothingstore.service.WishlistService;

/**
 * MockMvc tests for the wishlist web UC: view page, add, remove.
 *
 * Standalone setup keeps the test off the real Thymeleaf layout / security
 * chain; the {@code Authentication} parameter is supplied via {@code .principal()}.
 */
@SuppressWarnings("null")
class WishlistWebControllerTest {

    private MockMvc mockMvc;

    @Mock
    private WishlistService wishlistService;

    @Mock
    private UserService userService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new WishlistWebController(wishlistService, userService))
                .setViewResolvers(new InternalResourceViewResolver())
                .build();
    }

    private Authentication auth() {
        return new UsernamePasswordAuthenticationToken("user@test.com", null, List.of());
    }

    private User mockUser() {
        User u = new User();
        u.setEmail("user@test.com");
        u.setFullName("Test User");
        u.setRole(Role.USER);
        return u;
    }

    // ── VIEW ─────────────────────────────────────────────────
    @Test
    @DisplayName("GET /wishlist renders the wishlist page")
    void viewWishlist_ReturnsView() throws Exception {
        User user = mockUser();
        when(userService.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(wishlistService.getWishlist(user)).thenReturn(List.of());

        mockMvc.perform(get("/wishlist").principal(auth()))
                .andExpect(status().isOk())
                .andExpect(view().name("shop/wishlist"))
                .andExpect(model().attributeExists("wishlistItems"));
    }

    // ── ADD ──────────────────────────────────────────────────
    @Test
    @DisplayName("POST /wishlist/{id}/add adds and redirects back")
    void addToWishlist_Redirects() throws Exception {
        User user = mockUser();
        when(userService.findByEmail("user@test.com")).thenReturn(Optional.of(user));

        mockMvc.perform(post("/wishlist/10/add").principal(auth()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("success", "Added to Wishlist!"));

        verify(wishlistService).addToWishlist(user, 10L);
    }

    @Test
    @DisplayName("POST /wishlist/{id}/add honours same-origin Referer")
    void addToWishlist_RedirectsToReferer() throws Exception {
        User user = mockUser();
        when(userService.findByEmail("user@test.com")).thenReturn(Optional.of(user));

        mockMvc.perform(post("/wishlist/10/add")
                        .principal(auth())
                        .header("Referer", "http://localhost:80/product/essential-tee"))
                .andExpect(redirectedUrl("http://localhost:80/product/essential-tee"));
    }

    // ── REMOVE ───────────────────────────────────────────────
    @Test
    @DisplayName("POST /wishlist/{id}/remove removes and redirects back")
    void removeFromWishlist_Redirects() throws Exception {
        User user = mockUser();
        when(userService.findByEmail("user@test.com")).thenReturn(Optional.of(user));

        mockMvc.perform(post("/wishlist/10/remove").principal(auth()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("success", "Removed from Wishlist."));

        verify(wishlistService).removeFromWishlist(user, 10L);
    }
}
