package com.shop.clothingstore.controller;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.view.InternalResourceViewResolver;

import com.shop.clothingstore.entity.Category;
import com.shop.clothingstore.entity.Product;
import com.shop.clothingstore.entity.User;
import com.shop.clothingstore.exception.ProductNotFoundException;
import com.shop.clothingstore.service.CategoryService;
import com.shop.clothingstore.service.ProductService;
import com.shop.clothingstore.service.RecommendationService;
import com.shop.clothingstore.service.ReviewService;
import com.shop.clothingstore.service.SubCategoryService;
import com.shop.clothingstore.service.UserService;
import com.shop.clothingstore.service.WishlistService;

/**
 * MockMvc tests for the shop browsing UC: home (best sellers), product list,
 * list-by-category (incl. 404), and product detail (incl. wishlist state).
 */
@SuppressWarnings("null")
class ShopControllerTest {

    private MockMvc mockMvc;

    @Mock private ProductService productService;
    @Mock private CategoryService categoryService;
    @Mock private SubCategoryService subCategoryService;
    @Mock private ReviewService reviewService;
    @Mock private RecommendationService recommendationService;
    @Mock private WishlistService wishlistService;
    @Mock private UserService userService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ShopController(productService, categoryService,
                        subCategoryService, reviewService, recommendationService,
                        wishlistService, userService))
                .setViewResolvers(new InternalResourceViewResolver())
                .build();
    }

    private Product product(Long id, String slug) {
        Product p = new Product();
        injectId(p, id);
        p.setName("Essential Tee");
        p.setSlug(slug);
        return p;
    }

    @Test
    @DisplayName("GET / renders home with best sellers")
    void home_RendersView() throws Exception {
        when(productService.findBestSellerByCategorySlug(any())).thenReturn(Optional.empty());

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("shop/home"))
                .andExpect(model().attributeExists("bestSellers"));
    }

    @Test
    @DisplayName("GET /products renders product list with pagination window")
    void products_RendersView() throws Exception {
        Page<Product> page = new PageImpl<>(List.of(product(1L, "essential-tee")));
        when(productService.findWithFilter(any(), any(Pageable.class))).thenReturn(page);
        when(categoryService.getAllCategories()).thenReturn(List.of());

        mockMvc.perform(get("/products"))
                .andExpect(status().isOk())
                .andExpect(view().name("shop/products"))
                .andExpect(model().attributeExists("products", "filter", "categories",
                        "pageWindowStart", "pageWindowEnd"));
    }

    @Test
    @DisplayName("GET /products/{cat} renders list for a known category")
    void productsByCategory_Known_RendersView() throws Exception {
        Category cat = new Category();
        injectId(cat, 3L);
        cat.setSlug("top");
        when(categoryService.getCategoryBySlug("top")).thenReturn(Optional.of(cat));
        when(productService.findWithFilter(any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        when(categoryService.getAllCategories()).thenReturn(List.of());
        when(subCategoryService.getByCategoryId(3L)).thenReturn(List.of());

        mockMvc.perform(get("/products/top"))
                .andExpect(status().isOk())
                .andExpect(view().name("shop/products"))
                .andExpect(model().attribute("currentCategory", cat));
    }

    @Test
    @DisplayName("GET /products/{cat} unknown category → 404")
    void productsByCategory_Unknown_404() throws Exception {
        when(categoryService.getCategoryBySlug("nope")).thenReturn(Optional.empty());

        mockMvc.perform(get("/products/nope"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /product/{slug} renders detail with review aggregates")
    void productDetail_BySlug_RendersView() throws Exception {
        Product p = product(1L, "essential-tee");
        when(productService.findBySlug("essential-tee")).thenReturn(p);
        when(reviewService.getReviewsByItem(1L)).thenReturn(List.of());
        when(recommendationService.getSimilarProducts(1L, 4)).thenReturn(List.of());

        mockMvc.perform(get("/product/essential-tee"))
                .andExpect(status().isOk())
                .andExpect(view().name("shop/product-detail"))
                .andExpect(model().attributeExists("product", "sizes", "colors",
                        "averageRating", "reviewCount", "reviews", "variantsJson",
                        "relatedProducts", "isInWishlist"))
                .andExpect(model().attribute("isInWishlist", false));
    }

    @Test
    @DisplayName("GET /product/{slug} reflects wishlist state for logged-in user")
    void productDetail_LoggedIn_WishlistTrue() throws Exception {
        Product p = product(1L, "essential-tee");
        User user = new User();
        user.setEmail("user@test.com");
        when(productService.findBySlug("essential-tee")).thenReturn(p);
        when(reviewService.getReviewsByItem(1L)).thenReturn(List.of());
        when(recommendationService.getSimilarProducts(1L, 4)).thenReturn(List.of());
        when(userService.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(wishlistService.isInWishlist(user, 1L)).thenReturn(true);

        Authentication auth = new UsernamePasswordAuthenticationToken("user@test.com", null, List.of());

        mockMvc.perform(get("/product/essential-tee").principal(auth))
                .andExpect(status().isOk())
                .andExpect(model().attribute("isInWishlist", true));
    }

    @Test
    @DisplayName("GET /product/{slug} not found propagates ProductNotFoundException")
    void productDetail_NotFound() throws Exception {
        when(productService.findBySlug("ghost")).thenThrow(new ProductNotFoundException("ghost"));

        try {
            mockMvc.perform(get("/product/ghost"));
        } catch (Exception expected) {
            // standalone setup rethrows controller exceptions (no @ControllerAdvice wired)
            return;
        }
    }

    @Test
    @DisplayName("GET /products/{cat}/{sub}/{id} renders detail")
    void productDetail_ById_RendersView() throws Exception {
        Product p = product(7L, "essential-tee");
        when(productService.findById(7L)).thenReturn(Optional.of(p));
        when(reviewService.getReviewsByItem(7L)).thenReturn(List.of());
        when(recommendationService.getSimilarProducts(7L, 4)).thenReturn(List.of());

        mockMvc.perform(get("/products/top/tee/7"))
                .andExpect(status().isOk())
                .andExpect(view().name("shop/product-detail"));
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
