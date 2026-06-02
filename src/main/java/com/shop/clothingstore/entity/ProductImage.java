package com.shop.clothingstore.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.shop.clothingstore.entity.base.BaseEntity;
import com.shop.clothingstore.entity.base.ItemImage;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Entity
@Table(name = "product_images")
@Data
@EqualsAndHashCode(callSuper = true)
public class ProductImage extends BaseEntity implements ItemImage {

    @Column(nullable = false)
    private String imageUrl;

    private boolean primaryImage = false;

    /** Display order of the image within its product (0 = first/cover). */
    @Column(name = "sort_order")
    private Integer sortOrder = 0;

    @ManyToOne(optional = false)
    @JoinColumn(name = "product_id")
    @JsonIgnore
    private Product product;

    // ===== Implement ItemImage =====
    @Override
    public String getUrl() {
        return imageUrl;
    }

    @Override
    public boolean isPrimary() {
        return primaryImage;
    }
}
