package com.shop.clothingstore.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.shop.clothingstore.entity.Review;
import com.shop.clothingstore.repository.base.BaseRepository;

public interface ReviewRepository extends BaseRepository<Review, Long> {

    Optional<Review> findByOrderItem_Id(Long orderItemId);

    // Trả về các orderItemId đã có review trong tập cho trước — đặt cờ "reviewed"
    // cho danh sách đơn hàng trong 1 truy vấn (tránh N+1).
    @Query("""
        SELECT r.orderItem.id
        FROM Review r
        WHERE r.orderItem.id IN :orderItemIds
    """)
    List<Long> findReviewedOrderItemIds(@Param("orderItemIds") List<Long> orderItemIds);

    @Query("""
        SELECT AVG(r.rating)
        FROM Review r
        WHERE r.itemId = :itemId
    """)
    Double getAverageRatingByItemId(@Param("itemId") Long itemId);

    @Query("""
        SELECT COUNT(r)
        FROM Review r
        WHERE r.itemId = :itemId
    """)
    long countByItemId(@Param("itemId") Long itemId);

    // Eager-load imageUrls + actor: product-detail.html renders review attachment
    // thumbnails (review.imageUrls) and the author name (review.actor.fullName)
    // with open-session-in-view disabled.
    @EntityGraph(attributePaths = {"imageUrls", "actor"})
    @Query("""
        SELECT r
        FROM Review r
        WHERE r.itemId = :itemId
        ORDER BY r.createdAt DESC
    """)
    List<Review> findAllByItemIdOrderByCreatedAtDesc(@Param("itemId") Long itemId);
}
