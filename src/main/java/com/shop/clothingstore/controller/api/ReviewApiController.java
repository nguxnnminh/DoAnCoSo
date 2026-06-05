package com.shop.clothingstore.controller.api;

import java.security.Principal;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shop.clothingstore.entity.User;
import com.shop.clothingstore.service.ReviewService;
import com.shop.clothingstore.service.UserService;

import lombok.Data;

/**
 * REST endpoint cho đánh giá sản phẩm (mobile + AJAX).
 *
 * <p>Dùng CHUNG {@link ReviewService#createReview} với web ({@code ReviewController}):
 * review keyed theo {@code orderItemId}, ràng buộc 1 review / order_item, chỉ
 * đánh giá được đơn của chính mình và đã hoàn tất. Khác biệt duy nhất so với web
 * là trả JSON thay vì redirect.</p>
 */
@RestController
@RequestMapping("/api/reviews")
public class ReviewApiController {

    private final ReviewService reviewService;
    private final UserService userService;

    public ReviewApiController(ReviewService reviewService, UserService userService) {
        this.reviewService = reviewService;
        this.userService = userService;
    }

    // POST /api/reviews/{orderItemId}  body: { rating, comment }
    @PostMapping("/{orderItemId}")
    public ResponseEntity<Map<String, Object>> createReview(
            @PathVariable Long orderItemId,
            @RequestBody ReviewRequest request,
            Principal principal) {

        if (principal == null) {
            return ResponseEntity.status(401)
                    .body(Map.of("success", false, "message", "Authentication required."));
        }
        User user = userService.findByEmail(principal.getName()).orElse(null);
        if (user == null) {
            return ResponseEntity.status(401)
                    .body(Map.of("success", false, "message", "Authentication required."));
        }

        try {
            Long orderId = reviewService.createReview(
                    user.getId(), orderItemId, request.getRating(), request.getComment(), List.of());
            return ResponseEntity.ok(Map.of("success", true, "orderId", orderId));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @Data
    public static class ReviewRequest {
        private double rating;
        private String comment;
    }
}
