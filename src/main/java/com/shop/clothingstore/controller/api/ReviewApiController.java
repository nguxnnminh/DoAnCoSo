package com.shop.clothingstore.controller.api;

import java.io.IOException;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.shop.clothingstore.entity.User;
import com.shop.clothingstore.service.ReviewService;
import com.shop.clothingstore.service.UserService;
import com.shop.clothingstore.service.storage.FileStorageService;

/**
 * REST endpoint cho đánh giá sản phẩm (mobile + AJAX).
 * Nhận multipart/form-data để hỗ trợ đính kèm ảnh giống web.
 */
@RestController
@RequestMapping("/api/reviews")
public class ReviewApiController {

    private static final Logger log = LoggerFactory.getLogger(ReviewApiController.class);
    private static final long MAX_IMAGE_SIZE = 5 * 1024 * 1024;
    private static final int MAX_IMAGES = 5;
    private static final Set<String> ALLOWED_EXT = Set.of("jpg", "jpeg", "png", "webp");
    private static final String REVIEW_IMAGE_FOLDER = "review-images";

    private final ReviewService reviewService;
    private final UserService userService;
    private final FileStorageService fileStorageService;

    public ReviewApiController(ReviewService reviewService, UserService userService,
            FileStorageService fileStorageService) {
        this.reviewService = reviewService;
        this.userService = userService;
        this.fileStorageService = fileStorageService;
    }

    // POST /api/reviews/{orderItemId}  multipart: rating, comment, images[]
    @PostMapping(value = "/{orderItemId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> createReview(
            @PathVariable Long orderItemId,
            @RequestParam double rating,
            @RequestParam(defaultValue = "") String comment,
            @RequestParam(name = "images", required = false) MultipartFile[] images,
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
            List<String> imageUrls = uploadImages(images);
            Long orderId = reviewService.createReview(
                    user.getId(), orderItemId, rating, comment, imageUrls);
            return ResponseEntity.ok(Map.of("success", true, "orderId", orderId));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    private List<String> uploadImages(MultipartFile[] images) {
        List<String> urls = new ArrayList<>();
        if (images == null) return urls;
        int count = 0;
        for (MultipartFile img : images) {
            if (img == null || img.isEmpty()) continue;
            if (count >= MAX_IMAGES) break;
            if (img.getSize() > MAX_IMAGE_SIZE)
                throw new IllegalArgumentException("Each image must be under 5MB.");
            String ext = ext(img.getOriginalFilename());
            if (!ALLOWED_EXT.contains(ext))
                throw new IllegalArgumentException("Only jpg, png, webp images are accepted.");
            try {
                urls.add(fileStorageService.upload(img, REVIEW_IMAGE_FOLDER));
                count++;
            } catch (IOException e) {
                log.error("Failed to upload review image", e);
                throw new IllegalStateException("Could not save image. Please try again.");
            }
        }
        return urls;
    }

    private String ext(String filename) {
        if (filename == null || !filename.contains(".")) return "";
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }
}
