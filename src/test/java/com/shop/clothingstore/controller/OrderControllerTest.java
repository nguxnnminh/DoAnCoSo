package com.shop.clothingstore.controller;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
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

import com.shop.clothingstore.entity.Order;
import com.shop.clothingstore.entity.OrderStatus;
import com.shop.clothingstore.entity.User;
import com.shop.clothingstore.service.OrderService;
import com.shop.clothingstore.service.ReviewService;
import com.shop.clothingstore.service.UserService;

/**
 * MockMvc tests for the customer order UC: my-orders list, order detail
 * (ownership guard), self-cancel and cancel-request.
 */
@SuppressWarnings("null")
class OrderControllerTest {

    private MockMvc mockMvc;

    @Mock private OrderService orderService;
    @Mock private UserService userService;
    @Mock private ReviewService reviewService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new OrderController(orderService, userService, reviewService))
                .setViewResolvers(new InternalResourceViewResolver())
                .build();
    }

    private Authentication auth() {
        return new UsernamePasswordAuthenticationToken("user@test.com", null, List.of());
    }

    private User mockUser(Long id) {
        User u = new User();
        injectId(u, id);
        u.setEmail("user@test.com");
        return u;
    }

    // ── MY ORDERS ────────────────────────────────────────────
    @Test
    @DisplayName("GET /my-orders lists orders for the user")
    void myOrders_RendersView() throws Exception {
        User user = mockUser(1L);
        when(userService.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(orderService.findOrdersByUser(user)).thenReturn(List.of());

        mockMvc.perform(get("/my-orders").principal(auth()))
                .andExpect(status().isOk())
                .andExpect(view().name("shop/my-orders"))
                .andExpect(model().attributeExists("orders"));
    }

    @Test
    @DisplayName("GET /my-orders without login redirects to login")
    void myOrders_NoPrincipal_RedirectsLogin() throws Exception {
        mockMvc.perform(get("/my-orders"))
                .andExpect(redirectedUrl("/login"));
    }

    // ── ORDER DETAIL ─────────────────────────────────────────
    @Test
    @DisplayName("GET /orders/{id} shows detail for the owner")
    void orderDetail_Owner_RendersView() throws Exception {
        User user = mockUser(1L);
        Order order = new Order();
        injectId(order, 50L);
        order.setActor(user);
        order.setStatus(OrderStatus.PENDING);
        order.setItems(List.of()); // controller iterates order.getItems()

        when(userService.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(orderService.findByIdWithItems(50L)).thenReturn(Optional.of(order));

        mockMvc.perform(get("/orders/50").principal(auth()))
                .andExpect(status().isOk())
                .andExpect(view().name("shop/order-detail"))
                .andExpect(model().attributeExists("order", "isCompleted", "reviewedItemIds"));
    }

    @Test
    @DisplayName("GET /orders/{id} for another user's order redirects to my-orders")
    void orderDetail_NotOwner_Redirects() throws Exception {
        User user = mockUser(1L);
        User other = mockUser(2L);
        other.setEmail("other@test.com");
        Order order = new Order();
        injectId(order, 50L);
        order.setActor(other);

        when(userService.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(orderService.findByIdWithItems(50L)).thenReturn(Optional.of(order));

        mockMvc.perform(get("/orders/50").principal(auth()))
                .andExpect(redirectedUrl("/my-orders"));
    }

    @Test
    @DisplayName("GET /orders/{id} without login redirects to login")
    void orderDetail_NoPrincipal_RedirectsLogin() throws Exception {
        mockMvc.perform(get("/orders/50"))
                .andExpect(redirectedUrl("/login"));
    }

    // ── SELF CANCEL ──────────────────────────────────────────
    @Test
    @DisplayName("POST /orders/{id}/cancel cancels and redirects to detail")
    void cancelOrder_Success() throws Exception {
        User user = mockUser(1L);
        when(userService.findByEmail("user@test.com")).thenReturn(Optional.of(user));

        mockMvc.perform(post("/orders/50/cancel").principal(auth()))
                .andExpect(redirectedUrl("/orders/50"))
                .andExpect(flash().attribute("success", "Order cancelled successfully."));

        verify(orderService).selfCancel(50L, user);
    }

    @Test
    @DisplayName("POST /orders/{id}/cancel invalid state → flash error")
    void cancelOrder_InvalidState_FlashError() throws Exception {
        User user = mockUser(1L);
        when(userService.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        doThrow(new IllegalStateException("Only PENDING orders can be cancelled"))
                .when(orderService).selfCancel(eq(50L), eq(user));

        mockMvc.perform(post("/orders/50/cancel").principal(auth()))
                .andExpect(redirectedUrl("/orders/50"))
                .andExpect(flash().attribute("error", "Only PENDING orders can be cancelled"));
    }

    @Test
    @DisplayName("POST /orders/{id}/cancel without login redirects to login")
    void cancelOrder_NoPrincipal_RedirectsLogin() throws Exception {
        mockMvc.perform(post("/orders/50/cancel"))
                .andExpect(redirectedUrl("/login"));
    }

    // ── CANCEL REQUEST ───────────────────────────────────────
    @Test
    @DisplayName("POST /orders/{id}/cancel-request submits and redirects")
    void requestCancel_Success() throws Exception {
        User user = mockUser(1L);
        when(userService.findByEmail("user@test.com")).thenReturn(Optional.of(user));

        mockMvc.perform(post("/orders/50/cancel-request").param("reason", "Changed my mind").principal(auth()))
                .andExpect(redirectedUrl("/orders/50"))
                .andExpect(flash().attribute("success", "Cancellation request submitted."));

        verify(orderService).requestCancel(eq(50L), eq(user), eq("Changed my mind"));
    }

    @Test
    @DisplayName("POST /orders/{id}/cancel-request without login redirects to login")
    void requestCancel_NoPrincipal_RedirectsLogin() throws Exception {
        mockMvc.perform(post("/orders/50/cancel-request"))
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
