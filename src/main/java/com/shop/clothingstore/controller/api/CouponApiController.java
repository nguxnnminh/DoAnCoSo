package com.shop.clothingstore.controller.api;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.shop.clothingstore.entity.Coupon;
import com.shop.clothingstore.entity.User;
import com.shop.clothingstore.service.CouponService;
import com.shop.clothingstore.service.UserService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

@RestController
@RequestMapping("/api/coupons")
public class CouponApiController {

    private static final Logger log = LoggerFactory.getLogger(CouponApiController.class);

    private final CouponService couponService;
    private final UserService userService;

    public CouponApiController(CouponService couponService, UserService userService) {
        this.couponService = couponService;
        this.userService = userService;
    }

    // POST /api/coupons/validate
    // Read-only preview — does NOT increment usageCount.
    // Uses BigDecimal for all monetary values.
    // Now supports user-specific coupon validation.
    @PostMapping("/validate")
    public ResponseEntity<CouponValidationResponse> validateCoupon(
            @Valid @RequestBody ValidateRequest request,
            Principal principal) {

        log.debug("Validating coupon | code={} | orderTotal={}", request.getCode(), request.getOrderTotal());

        // Resolve current user (may be null for guest)
        User user = null;
        if (principal != null) {
            user = userService.findByEmail(principal.getName()).orElse(null);
        }

        Coupon coupon = couponService.validateCoupon(request.getCode(), request.getOrderTotal(), user);

        if (coupon == null) {
            return ResponseEntity.ok(new CouponValidationResponse(
                    false, "Invalid or expired coupon code", null, null, null, null, null));
        }

        BigDecimal discountedTotal = coupon.applyDiscount(request.getOrderTotal());
        BigDecimal savedAmount = request.getOrderTotal().subtract(discountedTotal)
                .setScale(0, RoundingMode.HALF_UP);

        return ResponseEntity.ok(new CouponValidationResponse(
                true,
                "Valid coupon code",
                coupon.getDiscountType() != null ? coupon.getDiscountType().name() : null,
                coupon.getDiscountValue(),
                discountDisplay(coupon),
                discountedTotal,
                savedAmount
        ));
    }

    // Định dạng hiển thị giảm giá — giống hệt CouponService.CouponDisplayDTO.getDiscountDisplay()
    // (nguồn duy nhất theo logic web): "20%" cho PERCENTAGE, "100,000₫" cho FIXED.
    private static String discountDisplay(Coupon coupon) {
        if (coupon.getDiscountType() == Coupon.DiscountType.PERCENTAGE) {
            return coupon.getDiscountValue().stripTrailingZeros().toPlainString() + "%";
        }
        return new java.text.DecimalFormat("#,###").format(coupon.getDiscountValue()) + "₫";
    }

    // GET /api/coupons/my  (requires auth)
    // Returns all coupons for the logged-in user
    @GetMapping("/my")
    public ResponseEntity<List<MyCouponResponse>> myCoupons(Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(401).build();
        }
        User user = userService.findByEmail(principal.getName()).orElse(null);
        if (user == null) return ResponseEntity.status(401).build();

        List<MyCouponResponse> result = couponService.getAllCouponsForUser(user).stream()
                .map(dto -> new MyCouponResponse(
                        dto.getCode(),
                        dto.getDescription(),
                        dto.getDiscountType() != null ? dto.getDiscountType().name() : null,
                        dto.getDiscountValue(),
                        dto.getDiscountDisplay(),
                        dto.getMinOrderAmount(),
                        dto.getExpiryDate(),
                        dto.isUsed(),
                        dto.isExpired(),
                        dto.isUsable()
                ))
                .toList();
        return ResponseEntity.ok(result);
    }

    // GET /api/coupons/available?orderTotal=...
    // Đề xuất các coupon ĐANG dùng được cho tổng đơn hiện tại — giống web
    // (CouponService.getAvailableCouponsForUser): đã lọc usable + thỏa minOrder,
    // xếp theo giảm nhiều nhất rồi sắp hết hạn. Phần tử đầu là "Recommended".
    @GetMapping("/available")
    public ResponseEntity<List<MyCouponResponse>> availableCoupons(
            @RequestParam BigDecimal orderTotal,
            Principal principal) {
        if (principal == null) {
            return ResponseEntity.ok(List.of());
        }
        User user = userService.findByEmail(principal.getName()).orElse(null);
        if (user == null) {
            return ResponseEntity.ok(List.of());
        }

        List<MyCouponResponse> result = couponService.getAvailableCouponsForUser(user, orderTotal).stream()
                .map(dto -> new MyCouponResponse(
                        dto.getCode(),
                        dto.getDescription(),
                        dto.getDiscountType() != null ? dto.getDiscountType().name() : null,
                        dto.getDiscountValue(),
                        dto.getDiscountDisplay(),
                        dto.getMinOrderAmount(),
                        dto.getExpiryDate(),
                        dto.isUsed(),
                        dto.isExpired(),
                        dto.isUsable()
                ))
                .toList();
        return ResponseEntity.ok(result);
    }

    public record MyCouponResponse(
            String code,
            String description,
            // discountType: "PERCENTAGE" | "FIXED" — để client tự định dạng %/₫ nếu cần.
            String discountType,
            BigDecimal discountValue,
            // discountDisplay: chuỗi đã định dạng sẵn ("20%" hoặc "100,000₫"),
            // dùng đúng logic web (CouponDisplayDTO.getDiscountDisplay) làm nguồn duy nhất.
            String discountDisplay,
            BigDecimal minOrderAmount,
            LocalDateTime expiryDate,
            boolean used,
            boolean expired,
            boolean usable) {}

    @Data
    public static class ValidateRequest {

        @NotBlank(message = "Coupon code is required")
        private String code;

        @NotNull(message = "Order total is required")
        @PositiveOrZero
        private BigDecimal orderTotal;
    }

    public record CouponValidationResponse(
            boolean valid,
            String message,
            // "PERCENTAGE" | "FIXED" — null khi coupon không hợp lệ
            String discountType,
            BigDecimal discountValue,
            // chuỗi định dạng sẵn ("20%" / "100,000₫") theo logic web
            String discountDisplay,
            BigDecimal discountedTotal,
            BigDecimal savedAmount) {
    }
}
