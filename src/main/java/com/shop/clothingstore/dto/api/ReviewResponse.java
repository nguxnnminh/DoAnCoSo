package com.shop.clothingstore.dto.api;

import java.time.LocalDateTime;
import java.util.List;

import com.shop.clothingstore.entity.Review;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ReviewResponse {

    private Long id;
    private double rating;
    private String comment;
    private String authorName;
    private List<String> imageUrls;
    private LocalDateTime createdAt;

    public static ReviewResponse from(Review r) {
        return new ReviewResponse(
                r.getId(),
                r.getRating(),
                r.getComment(),
                r.getActor() != null ? r.getActor().getFullName() : "Anonymous",
                r.getImageUrls(),
                r.getCreatedAt()
        );
    }
}
