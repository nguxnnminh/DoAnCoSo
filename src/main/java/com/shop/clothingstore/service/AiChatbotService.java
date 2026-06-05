package com.shop.clothingstore.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shop.clothingstore.config.ChatbotAiProperties;
import com.shop.clothingstore.dto.ProductFilterDTO;
import com.shop.clothingstore.dto.api.ChatbotResponse;
import com.shop.clothingstore.entity.Category;
import com.shop.clothingstore.entity.Product;
import com.shop.clothingstore.entity.ProductVariant;
import com.shop.clothingstore.entity.SubCategory;
import com.shop.clothingstore.repository.CategoryRepository;
import com.shop.clothingstore.repository.ProductRepository;
import com.shop.clothingstore.repository.SubCategoryRepository;
import com.shop.clothingstore.service.ai.AiRequestException;
import com.shop.clothingstore.service.ai.GeminiChatClient;

/**
 * AI-first sales assistant for the NOVA clothing shop.
 *
 * <p>Powered by Google Gemini with <b>function calling</b>: the model decides
 * when to query the live product database (search, best-sellers, product
 * details) and grounds its advice in real data. Shop policies and catalogue
 * structure are injected as system knowledge, so there is almost no rule-based
 * logic — only a lightweight fallback when the AI is unconfigured or failing.</p>
 */
@Service
public class AiChatbotService {

    private static final Logger log = LoggerFactory.getLogger(AiChatbotService.class);

    /** Max AI round-trips per message (enough for a couple of tool calls + final answer). */
    private static final int MAX_STEPS = 4;

    private final GeminiChatClient gemini;
    private final ChatbotAiProperties aiProps;
    private final ObjectMapper objectMapper;
    private final ProductService productService;
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final SubCategoryRepository subCategoryRepository;

    private volatile Instant aiDisabledUntil = null;

    public AiChatbotService(
            GeminiChatClient gemini,
            ChatbotAiProperties aiProps,
            ObjectMapper objectMapper,
            ProductService productService,
            ProductRepository productRepository,
            CategoryRepository categoryRepository,
            SubCategoryRepository subCategoryRepository) {
        this.gemini = gemini;
        this.aiProps = aiProps;
        this.objectMapper = objectMapper;
        this.productService = productService;
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.subCategoryRepository = subCategoryRepository;
    }

    public boolean isEnabledAndConfigured() {
        return gemini.isConfigured();
    }

    public ChatbotResponse processMessage(String userMessage) {
        return processMessage(userMessage, List.of());
    }

    /**
     * Process a message with prior conversation history for multi-turn context.
     *
     * @param history previous turns as {role, content} maps (oldest first)
     */
    public ChatbotResponse processMessage(String userMessage, List<Map<String, Object>> history) {
        if (userMessage == null || userMessage.isBlank()) {
            return ChatbotResponse.text("Xin chào! Mình là trợ lý của NOVA 👋 Bạn cần tìm sản phẩm, "
                    + "tư vấn phối đồ/size, hay hỏi về vận chuyển – đổi trả? Cứ nhắn cho mình nhé!");
        }
        if (userMessage.length() > 2000) {
            userMessage = userMessage.substring(0, 2000);
        }

        // AI not configured → graceful, minimal fallback (no rule engine).
        if (!isEnabledAndConfigured()) {
            return offlineFallback();
        }
        Instant disabledUntil = aiDisabledUntil;
        if (disabledUntil != null && Instant.now().isBefore(disabledUntil)) {
            return offlineFallback();
        }

        try {
            return runAiConversation(userMessage, history != null ? history : List.of());
        } catch (AiRequestException e) {
            handleAiHttpError(e.getStatusCode(), e.getMessage());
            return offlineFallback();
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("AI conversation failed: {}", e.getMessage());
            return offlineFallback();
        } catch (RuntimeException e) {
            log.warn("AI conversation error: {}", e.getMessage());
            return offlineFallback();
        }
    }

    private ChatbotResponse runAiConversation(String userMessage, List<Map<String, Object>> history)
            throws IOException, InterruptedException {

        String system = buildSystemPrompt();
        List<Map<String, Object>> tools = buildTools();

        List<Map<String, Object>> contents = new ArrayList<>();
        appendHistory(contents, history);
        contents.add(content("user", List.of(textPart(userMessage))));

        List<Product> attachedProducts = List.of();

        for (int step = 0; step < MAX_STEPS; step++) {
            JsonNode modelContent = gemini.generate(system, contents, tools);
            if (modelContent == null) {
                break;
            }
            JsonNode parts = modelContent.path("parts");

            // Collect any function calls in this turn
            List<JsonNode> calls = new ArrayList<>();
            StringBuilder textBuf = new StringBuilder();
            if (parts.isArray()) {
                for (JsonNode part : parts) {
                    if (part.has("functionCall")) {
                        calls.add(part.get("functionCall"));
                    } else if (part.hasNonNull("text")) {
                        textBuf.append(part.get("text").asText());
                    }
                }
            }

            // No tool calls → final answer
            if (calls.isEmpty()) {
                String answer = textBuf.toString().trim();
                if (answer.isEmpty()) {
                    answer = "Mình chưa rõ ý bạn lắm. Bạn mô tả loại sản phẩm (áo/quần/giày...), "
                            + "màu sắc hoặc khoảng giá để mình tư vấn nhé!";
                }
                return ChatbotResponse.withProducts(answer, attachedProducts);
            }

            // Echo the model's function-call turn back into the conversation
            contents.add(objectMapper.convertValue(modelContent, MAP_TYPE));

            // Execute each call and build function responses
            List<Map<String, Object>> responseParts = new ArrayList<>();
            for (JsonNode call : calls) {
                String name = call.path("name").asText("");
                JsonNode args = call.path("args");
                ToolResult result = executeFunction(name, args);
                if (!result.products().isEmpty()) {
                    attachedProducts = result.products(); // keep the latest non-empty result for cards
                }
                responseParts.add(Map.of("functionResponse",
                        Map.of("name", name, "response", result.response())));
            }
            contents.add(content("user", responseParts));
        }

        // Ran out of steps without a final text answer
        return ChatbotResponse.withProducts(
                attachedProducts.isEmpty()
                        ? "Mình cần thêm thông tin để tư vấn chính xác. Bạn cho mình biết loại sản phẩm, màu hoặc giá nhé!"
                        : "Đây là một vài gợi ý phù hợp:",
                attachedProducts);
    }

    private ToolResult executeFunction(String name, JsonNode args) {
        try {
            return switch (name) {
                case "search_products" -> doSearchProducts(args);
                case "get_best_sellers" -> doBestSellers(args);
                case "get_product_details" -> doProductDetails(args);
                default -> new ToolResult(Map.of("error", "unknown function: " + name), List.of());
            };
        } catch (RuntimeException e) {
            log.warn("Tool '{}' failed: {}", name, e.getMessage());
            return new ToolResult(Map.of("error", "internal error"), List.of());
        }
    }

    private ToolResult doSearchProducts(JsonNode args) {
        ProductFilterDTO filter = new ProductFilterDTO();

        String subcategory = argStr(args, "subcategory");
        String category = argStr(args, "category");
        if (subcategory != null) {
            setSubCategoryBySlug(filter, subcategory);
        }
        if (filter.getSubCategoryId() == null && category != null) {
            setCategoryBySlug(filter, category);
        }

        String color = argStr(args, "color");
        String keyword = argStr(args, "keyword");
        // When a category/subcategory is set, names rarely contain the type word,
        // so prefer the colour (product names embed the colour) for keyword search.
        if (filter.getSubCategoryId() != null || filter.getCategoryId() != null) {
            filter.setKeyword(color != null ? color : null);
        } else {
            filter.setKeyword(color != null ? color : keyword);
        }

        filter.setMinPrice(argMoney(args, "minPrice"));
        filter.setMaxPrice(argMoney(args, "maxPrice"));

        int limit = clamp(argInt(args, "limit", 6), 1, 8);
        List<Product> products = searchWithFallback(filter, limit);

        return new ToolResult(
                Map.of("count", products.size(), "products", summarize(products)),
                products);
    }

    private ToolResult doBestSellers(JsonNode args) {
        int limit = clamp(argInt(args, "limit", 6), 1, 8);
        List<Product> products = productRepository.findBestSellers(PageRequest.of(0, limit));
        return new ToolResult(
                Map.of("count", products.size(), "products", summarize(products)),
                products);
    }

    private ToolResult doProductDetails(JsonNode args) {
        String name = argStr(args, "name");
        if (name == null) {
            return new ToolResult(Map.of("found", false), List.of());
        }
        List<Product> matches = productService.fullTextSearch(name, 1);
        if (matches.isEmpty()) {
            return new ToolResult(Map.of("found", false), List.of());
        }
        Product p = matches.get(0);
        return new ToolResult(productDetail(p), List.of(p));
    }

    private List<Product> searchWithFallback(ProductFilterDTO base, int limit) {
        PageRequest page = PageRequest.of(0, limit);

        List<Product> r = productService.findWithFilter(base, page).getContent();
        if (!r.isEmpty()) return r;

        if (base.getKeyword() != null) {
            ProductFilterDTO f2 = copyFilter(base);
            f2.setKeyword(null);
            r = productService.findWithFilter(f2, page).getContent();
            if (!r.isEmpty()) return r;
        }
        if (base.getMinPrice() != null || base.getMaxPrice() != null) {
            ProductFilterDTO f3 = copyFilter(base);
            f3.setMinPrice(null);
            f3.setMaxPrice(null);
            r = productService.findWithFilter(f3, page).getContent();
            if (!r.isEmpty()) return r;
        }
        if (base.getSubCategoryId() != null) {
            ProductFilterDTO f4 = new ProductFilterDTO();
            f4.setSubCategoryId(base.getSubCategoryId());
            r = productService.findWithFilter(f4, page).getContent();
            if (!r.isEmpty()) return r;
        }
        if (base.getCategoryId() != null) {
            ProductFilterDTO f5 = new ProductFilterDTO();
            f5.setCategoryId(base.getCategoryId());
            r = productService.findWithFilter(f5, page).getContent();
            if (!r.isEmpty()) return r;
        }
        return List.of();
    }

    private static ProductFilterDTO copyFilter(ProductFilterDTO src) {
        ProductFilterDTO f = new ProductFilterDTO();
        f.setCategoryId(src.getCategoryId());
        f.setSubCategoryId(src.getSubCategoryId());
        f.setKeyword(src.getKeyword());
        f.setMinPrice(src.getMinPrice());
        f.setMaxPrice(src.getMaxPrice());
        return f;
    }

    private List<Map<String, Object>> summarize(List<Product> products) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Product p : products) {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("id", p.getId());
            m.put("name", p.getName());
            m.put("price", p.getMinPrice());
            m.put("url", "/products/" + p.getSlug());
            if (p.getSubCategory() != null) {
                m.put("type", p.getSubCategory().getName());
            }
            m.put("colors", distinctColors(p));
            out.add(m);
        }
        return out;
    }

    private Map<String, Object> productDetail(Product p) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("found", true);
        m.put("id", p.getId());
        m.put("name", p.getName());
        m.put("price", p.getMinPrice());
        m.put("url", "/products/" + p.getSlug());
        if (p.getDescription() != null) {
            m.put("description", p.getDescription());
        }
        if (p.getSubCategory() != null) {
            m.put("type", p.getSubCategory().getName());
        }
        m.put("colors", distinctColors(p));
        m.put("sizes", distinctSizes(p));
        // Compact stock-by-variant so the model can answer "còn size M màu đen không?"
        List<Map<String, Object>> variants = new ArrayList<>();
        for (ProductVariant v : p.getProductVariants()) {
            variants.add(Map.of(
                    "size", v.getSize() == null ? "" : v.getSize(),
                    "color", v.getColor() == null ? "" : v.getColor(),
                    "price", v.getPrice() == null ? BigDecimal.ZERO : v.getPrice(),
                    "stock", v.getStock() == null ? 0 : v.getStock()));
        }
        m.put("variants", variants);
        return m;
    }

    private static List<String> distinctColors(Product p) {
        Set<String> colors = new LinkedHashSet<>();
        for (ProductVariant v : p.getProductVariants()) {
            if (v.getColor() != null && !v.getColor().isBlank()) colors.add(v.getColor());
        }
        return new ArrayList<>(colors);
    }

    private static List<String> distinctSizes(Product p) {
        Set<String> sizes = new LinkedHashSet<>();
        for (ProductVariant v : p.getProductVariants()) {
            if (v.getSize() != null && !v.getSize().isBlank()) sizes.add(v.getSize());
        }
        return new ArrayList<>(sizes);
    }

    private String buildSystemPrompt() {
        return """
Bạn là trợ lý bán hàng thân thiện của website thời trang NOVA. Trả lời bằng tiếng Việt, ngắn gọn, đúng trọng tâm.

NHIỆM VỤ:
- Tư vấn sản phẩm, phối đồ, chọn size, và giải đáp về vận chuyển/đổi trả/thanh toán.
- Khi khách hỏi/tìm sản phẩm cụ thể, BẮT BUỘC gọi hàm để lấy dữ liệu THẬT từ cửa hàng — không được bịa tên/giá sản phẩm.
- Sau khi có kết quả hàm, viết lời tư vấn ngắn gọn; danh sách sản phẩm sẽ được hệ thống hiển thị dạng thẻ kèm theo, nên không cần liệt kê lại link.

CÔNG CỤ:
- search_products: tìm theo loại (category/subcategory), màu, khoảng giá.
- get_best_sellers: sản phẩm bán chạy.
- get_product_details: chi tiết (size/màu/tồn kho/giá) theo tên sản phẩm.

DANH MỤC HIỆN CÓ:
%s

CHÍNH SÁCH NOVA:
- Vận chuyển: miễn phí ship cho đơn từ 500.000đ; dưới 500.000đ phí 30.000đ; giao 2–4 ngày nội thành, 3–6 ngày tỉnh khác; chỉ giao trong Việt Nam.
- Đổi trả: miễn phí trong 14 ngày, sản phẩm còn nguyên tem chưa qua sử dụng; lỗi sản xuất đổi mới hoặc hoàn tiền 100%%.
- Thanh toán: COD hoặc chuyển khoản ngân hàng.
- Size: S (48–55kg, 155–165cm), M (55–65kg, 163–170cm), L (65–75kg, 168–175cm), XL (75–85kg, 173–180cm), XXL (85–95kg, 178–185cm). Phân vân giữa 2 size thì chọn size lớn hơn.
- Chất liệu: cotton cao cấp, garment-dyed, form oversized, 100%% chính hãng.

QUY TẮC:
- Không bịa thông tin. Nếu không có dữ liệu, hướng dẫn khách vào mục tương ứng trên web.
- Có thể tham chiếu các tin nhắn trước để hiểu câu hỏi nối tiếp (vd "còn màu khác không?").
""".formatted(buildCatalogText()).trim();
    }

    /** Build a compact "Category > subcategory (slug)" listing from the DB. */
    private String buildCatalogText() {
        StringBuilder sb = new StringBuilder();
        try {
            for (SubCategory sc : subCategoryRepository.findAll()) {
                String cat = sc.getCategory() != null ? sc.getCategory().getName() : "?";
                sb.append("- ").append(cat).append(" > ").append(sc.getName())
                        .append(" (slug: ").append(sc.getSlug()).append(")\n");
            }
        } catch (RuntimeException e) {
            log.debug("Catalogue build failed: {}", e.getMessage());
        }
        if (sb.length() == 0) {
            sb.append("- Top: tee, hoodie, shirt\n- Bottom: pants, shorts, jeans\n- Accessories: shoes, bag, cap\n");
        }
        return sb.toString().trim();
    }

    private List<Map<String, Object>> buildTools() {
        Map<String, Object> searchProducts = Map.of(
                "name", "search_products",
                "description", "Tìm sản phẩm trong cửa hàng theo loại, màu sắc và/hoặc khoảng giá. "
                        + "Dùng khi khách muốn xem/mua/gợi ý sản phẩm.",
                "parameters", Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "category", Map.of("type", "string",
                                        "enum", List.of("top", "bottom", "accessories"),
                                        "description", "Nhóm lớn: top (áo), bottom (quần), accessories (phụ kiện)"),
                                "subcategory", Map.of("type", "string",
                                        "enum", List.of("tee", "hoodie", "shirt", "pants", "shorts", "jeans", "shoes", "bag", "cap"),
                                        "description", "Loại cụ thể"),
                                "color", Map.of("type", "string", "description", "Màu tiếng Việt, vd: đen, trắng, xanh, đỏ"),
                                "keyword", Map.of("type", "string", "description", "Từ khóa tên sản phẩm nếu không xác định loại"),
                                "minPrice", Map.of("type", "number", "description", "Giá tối thiểu (VND)"),
                                "maxPrice", Map.of("type", "number", "description", "Giá tối đa (VND)"),
                                "limit", Map.of("type", "integer", "description", "Số sản phẩm trả về (1-8), mặc định 6"))));

        Map<String, Object> bestSellers = Map.of(
                "name", "get_best_sellers",
                "description", "Lấy danh sách sản phẩm bán chạy nhất.",
                "parameters", Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "limit", Map.of("type", "integer", "description", "Số sản phẩm (1-8), mặc định 6"))));

        Map<String, Object> productDetails = Map.of(
                "name", "get_product_details",
                "description", "Lấy chi tiết một sản phẩm theo tên: mô tả, các màu, size và tồn kho, giá.",
                "parameters", Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "name", Map.of("type", "string", "description", "Tên (hoặc một phần tên) sản phẩm")),
                        "required", List.of("name")));

        return List.of(Map.of("functionDeclarations", List.of(searchProducts, bestSellers, productDetails)));
    }

    private void appendHistory(List<Map<String, Object>> contents, List<Map<String, Object>> history) {
        if (history == null || history.isEmpty()) return;
        int start = Math.max(0, history.size() - 6);
        for (int i = start; i < history.size(); i++) {
            Map<String, Object> turn = history.get(i);
            if (turn == null) continue;
            Object role = turn.get("role");
            Object text = turn.get("content");
            if (role == null || text == null) continue;
            String r = String.valueOf(role);
            // Gemini uses "model" for assistant turns
            String geminiRole = "assistant".equals(r) ? "model" : ("user".equals(r) ? "user" : null);
            if (geminiRole == null) continue;
            contents.add(content(geminiRole, List.of(textPart(String.valueOf(text)))));
        }
    }

    private ChatbotResponse offlineFallback() {
        try {
            List<Product> products = productRepository.findBestSellers(PageRequest.of(0, 6));
            if (!products.isEmpty()) {
                return ChatbotResponse.withProducts(
                        "Trợ lý AI đang tạm bận. Trong lúc chờ, đây là vài sản phẩm bán chạy tại NOVA — "
                                + "bạn cũng có thể vào trang Shop để xem toàn bộ nhé:", products);
            }
        } catch (RuntimeException ignored) {
            // fall through to text
        }
        return ChatbotResponse.text("Trợ lý AI đang tạm bận, bạn thử lại sau ít phút nhé. "
                + "Bạn có thể duyệt sản phẩm trực tiếp ở trang Shop.");
    }

    private void handleAiHttpError(int status, String message) {
        int cooldown;
        if (status == 429) {
            cooldown = Math.max(30, aiProps.getCooldownSeconds());
            log.warn("AI 429 (rate limit) — disabled for {}s. err={}", cooldown, message);
        } else if (status == 401 || status == 403) {
            cooldown = Math.max(60, aiProps.getCooldownSeconds());
            log.warn("AI auth error {} — disabled for {}s. err={}", status, cooldown, message);
        } else {
            log.warn("AI request failed: status={}. err={}", status, message);
            return;
        }
        aiDisabledUntil = Instant.now().plusSeconds(cooldown);
    }

    private static final com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>> MAP_TYPE =
            new com.fasterxml.jackson.core.type.TypeReference<>() {};

    private static Map<String, Object> content(String role, List<Map<String, Object>> parts) {
        return Map.of("role", role, "parts", parts);
    }

    private static Map<String, Object> textPart(String text) {
        return Map.of("text", text);
    }

    private void setCategoryBySlug(ProductFilterDTO filter, String slug) {
        if (slug == null || slug.isBlank()) return;
        categoryRepository.findBySlug(slug.trim().toLowerCase())
                .map(Category::getId)
                .ifPresent(filter::setCategoryId);
    }

    private void setSubCategoryBySlug(ProductFilterDTO filter, String slug) {
        if (slug == null || slug.isBlank()) return;
        subCategoryRepository.findBySlug(slug.trim().toLowerCase())
                .map(SubCategory::getId)
                .ifPresent(filter::setSubCategoryId);
    }

    private static String argStr(JsonNode args, String key) {
        if (args == null) return null;
        JsonNode n = args.get(key);
        if (n == null || n.isNull()) return null;
        String s = n.asText();
        return (s == null || s.isBlank() || "null".equalsIgnoreCase(s)) ? null : s.trim();
    }

    private static int argInt(JsonNode args, String key, int def) {
        if (args == null) return def;
        JsonNode n = args.get(key);
        if (n == null || n.isNull()) return def;
        if (n.isNumber()) return n.asInt();
        try {
            return Integer.parseInt(n.asText().replaceAll("[^0-9-]", ""));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static BigDecimal argMoney(JsonNode args, String key) {
        if (args == null) return null;
        JsonNode n = args.get(key);
        if (n == null || n.isNull()) return null;
        try {
            if (n.isNumber()) return BigDecimal.valueOf(n.asLong());
            String cleaned = n.asText().replaceAll("[^0-9]", "");
            return cleaned.isBlank() ? null : new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static int clamp(int n, int min, int max) {
        return Math.max(min, Math.min(max, n));
    }

    private record ToolResult(Map<String, Object> response, List<Product> products) {}
}
