package com.shop.clothingstore.controller.api;

import java.security.Principal;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.shop.clothingstore.dto.api.CheckoutRequest;
import com.shop.clothingstore.dto.api.OrderResponse;
import com.shop.clothingstore.entity.Order;
import com.shop.clothingstore.entity.User;
import com.shop.clothingstore.repository.ReviewRepository;
import com.shop.clothingstore.service.CartService;
import com.shop.clothingstore.service.CheckoutService;
import com.shop.clothingstore.service.OrderService;
import com.shop.clothingstore.service.UserService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/orders")
public class OrderApiController {

    private final CheckoutService checkoutService;
    private final OrderService orderService;
    private final UserService userService;
    private final CartService cartService;
    private final ReviewRepository reviewRepository;

    public OrderApiController(
            CheckoutService checkoutService,
            OrderService orderService,
            UserService userService,
            CartService cartService,
            ReviewRepository reviewRepository) {
        this.checkoutService = checkoutService;
        this.orderService = orderService;
        this.userService = userService;
        this.cartService = cartService;
        this.reviewRepository = reviewRepository;
    }

    // POST /api/orders/checkout
    @PostMapping("/checkout")
    public ResponseEntity<OrderResponse> checkout(
            @Valid @RequestBody CheckoutRequest request,
            Principal principal) {

        User user = getUser(principal);
        Order order = checkoutService.checkout(
                request.getCustomerName(),
                request.getPhone(),
                request.getAddress(),
                cartService.getCart(),
                user,
                request.getCouponCode(),
                request.getNote()
        );
        cartService.clear();
        return ResponseEntity.ok(OrderResponse.from(order));
    }

    // GET /api/orders/my
    @GetMapping("/my")
    public ResponseEntity<List<OrderResponse>> myOrders(Principal principal) {
        User user = getUser(principal);
        if (user == null) {
            throw new IllegalStateException("Authentication required to view orders");
        }

        List<Order> userOrders = orderService.findOrdersByUser(user);

        // Tập orderItemId đã được đánh giá (1 truy vấn) → đặt cờ reviewed cho từng item.
        List<Long> itemIds = userOrders.stream()
                .filter(o -> o.getItems() != null)
                .flatMap(o -> o.getItems().stream())
                .map(i -> i.getId())
                .toList();
        java.util.Set<Long> reviewedItemIds = itemIds.isEmpty()
                ? java.util.Set.of()
                : new java.util.HashSet<>(reviewRepository.findReviewedOrderItemIds(itemIds));

        List<OrderResponse> orders = userOrders.stream()
                .map(o -> OrderResponse.from(o, reviewedItemIds))
                .toList();
        return ResponseEntity.ok(orders);
    }

    // POST /api/orders/{id}/cancel
    // User self-cancel while order is PENDING
    @PostMapping("/{id}/cancel")
    public ResponseEntity<Map<String, Object>> cancelOrder(
            @PathVariable Long id,
            Principal principal) {

        User user = getUser(principal);
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "Authentication required."));
        }
        try {
            Order order = orderService.selfCancel(id, user);
            return ResponseEntity.ok(Map.of("success", true, "status", order.getStatus().name()));
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    // POST /api/orders/{id}/cancel-request
    // User requests cancellation with a reason while order is PROCESSING
    @PostMapping("/{id}/cancel-request")
    public ResponseEntity<Map<String, Object>> requestCancel(
            @PathVariable Long id,
            @RequestParam String reason,
            Principal principal) {

        User user = getUser(principal);
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "Authentication required."));
        }
        try {
            Order order = orderService.requestCancel(id, user, reason);
            return ResponseEntity.ok(Map.of("success", true, "status", order.getStatus().name()));
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    private User getUser(Principal principal) {
        if (principal == null) {
            return null;
        }
        return userService.findByEmail(principal.getName()).orElse(null);
    }
}
