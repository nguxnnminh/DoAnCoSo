package com.shop.clothingstore.controller;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.view.InternalResourceViewResolver;

import com.shop.clothingstore.entity.User;
import com.shop.clothingstore.service.ReviewService;
import com.shop.clothingstore.service.UserService;
import com.shop.clothingstore.service.storage.FileStorageService;

/**
 * MockMvc tests for the review UC: create review (success + validation
 * failures), and the not-authenticated guard.
 */
@SuppressWarnings("null")
class ReviewControllerTest {

    private MockMvc mockMvc;

    @Mock private ReviewService reviewService;
    @Mock private UserService userService;
    @Mock private FileStorageService fileStorageService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ReviewController(reviewService, userService, fileStorageService))
                .setViewResolvers(new InternalResourceViewResolver())
                .build();
    }

    private Authentication auth() {
        return new UsernamePasswordAuthenticationToken("user@test.com", null, List.of());
    }

    private User mockUser() {
        User u = new User();
        injectId(u, 1L);
        u.setEmail("user@test.com");
        return u;
    }

    @Test
    @DisplayName("POST /reviews/{itemId} creates review and redirects to order")
    void createReview_Success() throws Exception {
        when(userService.findByEmail("user@test.com")).thenReturn(java.util.Optional.of(mockUser()));
        when(reviewService.createReview(anyLong(), anyLong(), anyDouble(), anyString(), any()))
                .thenReturn(99L);

        mockMvc.perform(post("/reviews/5")
                        .param("rating", "5").param("comment", "Great product")
                        .principal(auth()))
                .andExpect(redirectedUrl("/orders/99"))
                .andExpect(flash().attribute("success", "Review submitted successfully!"));
    }

    @Test
    @DisplayName("POST /reviews/{itemId} duplicate/invalid → flash error to my-orders")
    void createReview_InvalidArgument_FlashError() throws Exception {
        when(userService.findByEmail("user@test.com")).thenReturn(java.util.Optional.of(mockUser()));
        when(reviewService.createReview(anyLong(), anyLong(), anyDouble(), anyString(), any()))
                .thenThrow(new IllegalArgumentException("Already reviewed"));

        mockMvc.perform(post("/reviews/5")
                        .param("rating", "5").param("comment", "Dup")
                        .principal(auth()))
                .andExpect(redirectedUrl("/my-orders"))
                .andExpect(flash().attribute("error", "Already reviewed"));
    }

    @Test
    @DisplayName("POST /reviews/{itemId} without login redirects to login")
    void createReview_NoAuth_RedirectsLogin() throws Exception {
        mockMvc.perform(post("/reviews/5").param("rating", "5").param("comment", "x"))
                .andExpect(redirectedUrl("/login"));
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
