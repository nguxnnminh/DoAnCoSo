package com.shop.clothingstore.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Repository;

import com.shop.clothingstore.entity.Product;
import com.shop.clothingstore.entity.User;
import com.shop.clothingstore.entity.WishlistItem;
import com.shop.clothingstore.repository.base.BaseRepository;

@Repository
public interface WishlistItemRepository extends BaseRepository<WishlistItem, Long> {

    // Eager-load product + its images: wishlist.html renders thumbnails with OSIV disabled.
    @EntityGraph(attributePaths = {"product", "product.images"})
    List<WishlistItem> findByUser(User user);

    Optional<WishlistItem> findByUserAndProduct(User user, Product product);

    boolean existsByUserAndProduct(User user, Product product);

    boolean existsByUserAndProductId(User user, Long productId);

    void deleteByUserAndProduct(User user, Product product);
}
