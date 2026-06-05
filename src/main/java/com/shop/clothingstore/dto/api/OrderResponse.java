package com.shop.clothingstore.dto.api;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.shop.clothingstore.entity.Order;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * DTO trả về thông tin đơn hàng qua REST API. FIXED: Dùng BigDecimal cho total
 * và price — tránh floating-point imprecision khi serialize JSON.
 */
@Data
@AllArgsConstructor
public class OrderResponse {

    private Long id;
    private String customerName;
    private String phone;
    private String address;
    private String status;

    // FIXED: BigDecimal thay Double — tài chính không được dùng Double
    private BigDecimal total;

    private List<OrderItemInfo> items;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;

    @Data
    @AllArgsConstructor
    public static class OrderItemInfo {

        // orderItemId — client cần để gửi đánh giá (review keyed theo order_item)
        private Long id;

        private String productName;
        private String size;
        private String color;

        // FIXED: BigDecimal thay Double
        private BigDecimal price;

        private Integer quantity;

        // Đã được đánh giá chưa (1 review / order_item) — client dùng để ẩn nút đánh giá
        private boolean reviewed;
    }

    /**
     * Map Order entity → OrderResponse DTO. Không expose entity trực tiếp ra
     * ngoài API.
     */
    public static OrderResponse from(Order order) {
        return from(order, java.util.Set.of());
    }

    /**
     * Overload nhận tập orderItemId đã được đánh giá để đặt cờ {@code reviewed}.
     */
    public static OrderResponse from(Order order, java.util.Set<Long> reviewedItemIds) {

        List<OrderItemInfo> items = order.getItems() != null
                ? order.getItems().stream()
                        .map(i -> new OrderItemInfo(
                        i.getId(),
                        i.getProductName(),
                        i.getSize(),
                        i.getColor(),
                        i.getPrice(), // BigDecimal → BigDecimal, no conversion needed
                        i.getQuantity(),
                        reviewedItemIds.contains(i.getId())
                ))
                        .toList()
                : List.of();

        return new OrderResponse(
                order.getId(),
                order.getCustomerName(),
                order.getPhone(),
                order.getAddress(),
                order.getStatus().name(),
                order.getTotal(), // BigDecimal → BigDecimal
                items,
                order.getCreatedAt()
        );
    }
}
