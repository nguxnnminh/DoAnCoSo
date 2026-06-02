# Changelog — Lịch sử thay đổi

> Format: mỗi entry có **BEFORE** (trạng thái cũ), **AFTER** (trạng thái mới), **Files** và **Lý do**.  
> Xem chi tiết kỹ thuật từng file: [work-log.md](work-log.md)

---

## DONE — Đã thay đổi (mới nhất trước)

---

### [2026-06-02] Viết lại toàn bộ tài liệu từ code

**BEFORE:**
Docs cũ không còn chính xác sau nhiều thay đổi lớn (Gemini migration, gallery kéo-thả, unified save Try-On). Nhiều endpoint, field, và logic không được ghi nhận.

**AFTER:**
- `README.md`: viết lại toàn bộ — security/auth chi tiết (2 filter chain, URL matrix), DB schema đầy đủ 23 entity, API endpoints hoàn chỉnh, cấu trúc thư mục chính xác, biến môi trường đầy đủ
- `docs/features.md`: 17 section chi tiết từ code thực tế
- `docs/work-log.md`: bảng bugs đã sửa, quyết định kỹ thuật, files thay đổi
- `docs/changelog.md`: file này

**Files:**
- `README.md`, `docs/features.md`, `docs/work-log.md`, `docs/changelog.md`

---

### [2026-06-02] AI Chatbot: chuyển Ollama → Google Gemini + function calling

**BEFORE:**
- Chatbot nặng rule-based: regex giá VN, từ điển danh mục/màu, FAQ cứng (10+ pattern)
- Tầng AI: Ollama local (`OllamaChatClient`) gọi `llama3.2:3b` — phải cài Ollama
- Không gọi trực tiếp DB để tư vấn sản phẩm thật

**AFTER:**
- Xóa `OllamaChatClient` + tất cả Ollama config
- Thêm `GeminiChatClient` — REST client Gemini v1beta, hỗ trợ function calling
- `AiChatbotService` viết lại AI-first:
  - System prompt nhồi chính sách NOVA + danh mục đọc động từ `subCategoryRepository`
  - 3 tools: `search_products`, `get_best_sellers`, `get_product_details`
  - Vòng lặp MAX_STEPS=4 (functionCall → executeFunction → functionResponse → Gemini)
  - `thinkingBudget=0` để tắt thinking (tránh cụt token)
  - Fallback: best-sellers + text khi AI offline/không key
- Model mặc định: `gemini-2.5-flash` (xác nhận qua ListModels API)

**Files:**
- `service/AiChatbotService.java` (viết lại)
- `service/ai/GeminiChatClient.java` (tạo mới)
- `service/ai/OllamaChatClient.java` (xóa)
- `config/ChatbotAiProperties.java` (viết lại — bỏ Ollama, thêm Gemini)
- `resources/application.properties`
- `controller/api/ChatbotApiController.java` (sửa comment)

**Lý do:**
Không cần cài LLM local → nhẹ máy; AI tự query DB → tư vấn dựa dữ liệu thật; Gemini 2.5 Flash free tier + tiếng Việt tốt + function calling.

---

### [2026-06-02] Admin: gallery ảnh kéo-thả + unified Try-On save

**BEFORE:**
- Trang Thêm/Sửa: chỉ có input `<file multiple>`, thứ tự ảnh = thứ tự chọn file, không sắp xếp được
- Trang Sửa: ảnh cũ dùng checkbox "đánh dấu xóa", ảnh mới ô riêng — không trộn chung
- Try-On trên Sửa: 2 form riêng ngoài form chính (form enable, form disable) → 3 nút lưu
- Không có `sort_order` column

**AFTER:**
- Thêm cột `sort_order` vào `product_images` (tự tạo qua `ddl-auto=update`)
- Gallery kéo-thả (HTML5 drag events) trên cả 2 trang — giống nhau
- Ảnh đầu = bìa tự động (`primaryImage=true`, `sortOrder=0`)
- Nút ✕ trên mỗi thumbnail để xóa ngay
- Trang Sửa: ảnh cũ + ảnh mới trong 1 gallery; token `E{existingId}`/`N{newIndex}` gửi thứ tự lên server
- Try-On gộp vào `productEditForm`: toggle on/off JS + ô upload + chọn loại → **1 nút Save**
- `TryOnService.updateTryOnSettings()` xử lý logic bật/tắt/upload

**Files:**
- `entity/ProductImage.java` (+sortOrder)
- `entity/Product.java` (@OrderBy + getImages())
- `dto/ProductUpdateDTO.java` (+imageOrder, +tryOnEnabled, +garmentImage, +garmentType)
- `dto/ProductCreateDTO.java` (+tryOnEnabled)
- `service/ProductService.java` (saveImages baseOrder, applyImageOrder)
- `service/TryOnService.java` (+updateTryOnSettings)
- `controller/admin/AdminProductController.java` (inject TryOnService, wire create+update)
- `templates/admin/products/create.html` (gallery JS + try-on toggle)
- `templates/admin/products/edit.html` (unified gallery + try-on card, xóa 2 form riêng)

---

### [2026-06-02] Try-On UI: ảnh to hơn + lightbox + fix upload zone

**BEFORE:**
- Try-On Studio: sidebar 340px, upload zone min-height 200px, preview ảnh người max 180px, kết quả không có min-height
- Product Detail modal: modal max-width 780px, upload zone min-height 220px, preview max 280px, kết quả max 350px
- Bấm vào vùng upload không mở file picker (là `<div>`, không phải `<label>`)

**AFTER:**
- Studio: sidebar 420px · upload zone 320px · preview 420px · kết quả min-height 300px
- Modal: max-width 1040px · upload zone 380px · preview 480px · kết quả max-height 520px
- Lightbox toàn màn hình khi bấm ảnh người hoặc ảnh kết quả
- Fix: `onclick="input.click()"` trên upload div; `stopPropagation()` trên ảnh

**Files:**
- `templates/shop/tryon-studio.html`
- `templates/shop/product-detail.html`

---

### [2026-05-30] Flutter mobile app đầy đủ tính năng

**BEFORE:**
Chỉ có web app. Không có mobile app.

**AFTER:**
Flutter app `nova_mobile` (iOS/Android):
- Riverpod 2.6 state management
- GoRouter 14 navigation
- Dio 5 + JWT interceptor
- Màn hình: auth · home · shop · product detail · cart · checkout · orders · wishlist · search · profile · notifications

**Files:**
- `mobile-app/` (toàn bộ thư mục mới)

---

### [2026-05-29] Virtual Try-On: CatVTON local + outfit compositing

**BEFORE:**
Try-On chỉ hỗ trợ Replicate IDM-VTON cloud; không có fallback local; không thử outfit.

**AFTER:**
- Python FastAPI server với 2 tier:
  1. Replicate IDM-VTON (cloud, khi có token)
  2. CatVTON local (GPU ≥4GB VRAM, fallback tự động)
- Outfit try-on: SegFormer parse → 2 mask → 2 lượt CatVTON độc lập → composite
- `TryOnService.generateOutfitTryOn(personPath, topId, bottomId)`
- Admin: preprocess garment qua Python (rembg) với fallback lưu raw

**Files:**
- `python-tryon-server/main.py` (thêm `/tryon/outfit`)
- `service/TryOnService.java` (generateOutfitTryOn, preprocessAndEnable)
- `controller/api/TryOnApiController.java` (POST /api/tryon/generate-outfit)

---

### [2026-05-29] Real-time SSE notifications

**BEFORE:**
Không có thông báo real-time. User phải F5 mới thấy cập nhật.

**AFTER:**
- `SseService`: pool `SseEmitter` per user (concurrent-safe)
- `SseNotificationController`: `GET /notifications/stream`
- Gửi thông báo khi: đặt hàng · cập nhật trạng thái đơn
- Badge số chưa đọc trên header
- API: `/api/notifications` · `/count` · `/{id}/read` · `/read-all`

**Files:**
- `service/SseService.java`, `service/NotificationService.java`
- `controller/SseNotificationController.java`
- `controller/api/NotificationApiController.java`
- `templates/layout/base.html` (SSE JS + badge)

---

### [2026-05-29] Referral system

**BEFORE:**
Không có hệ thống giới thiệu.

**AFTER:**
- `User.referralCode` (unique 16 chars, tự generate khi đăng ký)
- `User.referredById` liên kết người giới thiệu
- `ReferralService.processReferralReward()`: thưởng cả 2 bên khi đơn đầu COMPLETED
- Đăng ký API nhận `ref` param

**Files:**
- `entity/User.java`, `service/ReferralService.java`
- `controller/api/AuthApiController.java`

---

### [2026-05-29] Review có ảnh đính kèm

**BEFORE:**
Review chỉ có rating + text.

**AFTER:**
- `Review.imageUrls` (@ElementCollection → bảng `review_images`)
- Tối đa 5 ảnh / review
- Upload ảnh khi submit review
- Hiển thị ảnh trong trang chi tiết sản phẩm

**Files:**
- `entity/Review.java`
- `controller/ReviewController.java`
- `templates/shop/product-detail.html`

---

### [2026-05-29] Full-text search + autocomplete

**BEFORE:**
Tìm kiếm chỉ dùng LIKE (`%keyword%`), không gợi ý.

**AFTER:**
- MySQL FULLTEXT INDEX trên `products(name, description)`
- `ProductRepository.fullTextSearchIds()`: MATCH AGAINST BOOLEAN MODE
- `ProductService.fullTextSearch()`: trả kết quả xếp theo relevance; fallback LIKE nếu FULLTEXT lỗi
- `GET /api/products/suggest?q=` trả ≤8 kết quả
- Autocomplete dropdown realtime trên header search

**Files:**
- `repository/ProductRepository.java`
- `service/ProductService.java`
- `controller/api/ProductApiController.java`
- `templates/layout/base.html` (autocomplete JS)

---

### [2026-05-28] Admin dashboard + Excel export

**BEFORE:**
Không có dashboard phân tích.

**AFTER:**
- `DashboardService`: KPI (đơn hôm nay, doanh thu, cảnh báo stock ≤10), biểu đồ 7 ngày/4 tuần/12 tháng/5 năm
- `ReportService`: xuất Excel (.xlsx) dùng Apache POI
- `AnalyticsApiController`: `/api/analytics/top-products`, `/trending`, `/overview`
- `AdminApiController`: `/api/admin/stats/summary` (AJAX), bulk-status sản phẩm

**Files:**
- `service/DashboardService.java`, `service/ReportService.java`
- `controller/admin/AdminDashboardController.java`
- `controller/api/AnalyticsApiController.java`, `AdminApiController.java`

---

### [2026-05-27] Checkout + Coupon + Order management

**BEFORE:**
Không có hệ thống đặt hàng / coupon.

**AFTER:**
- `CheckoutService.checkout()`: kiểm tra stock, tính ship fee (free ≥500k, 30k dưới ngưỡng), apply coupon, tạo Payment, gửi thông báo
- Optimistic locking `@Version` trên `Order`
- 6 `OrderStatus`: PENDING → PROCESSING → SHIPPING → COMPLETED / CANCEL_REQUESTED → CANCELLED
- Coupon: PERCENTAGE + FIXED, user-specific, `UserCoupon` tracking

**Files:**
- `service/CheckoutService.java`, `service/OrderService.java`, `service/CouponService.java`
- `entity/Order.java`, `entity/Coupon.java`, `entity/UserCoupon.java`
- `controller/api/OrderApiController.java`, `CouponApiController.java`

---

### [2026-05-26] Security: login rate limiting + JWT API chain

**BEFORE:**
Chỉ có Spring Security form login cơ bản.

**AFTER:**
- `LoginRateLimitFilter`: 5 lần sai/IP/15 phút (in-memory Guava Cache)
- `JwtAuthenticationFilter`: parse và validate JWT cho `/api/**`
- 2 security filter chain: web (form login + session) + api (JWT stateless)
- Security headers: HSTS, X-Frame-Options, Referrer-Policy

**Files:**
- `security/LoginRateLimitFilter.java`, `security/JwtAuthenticationFilter.java`, `security/JwtUtil.java`
- `config/SecurityConfig.java`

---

## PLANNED — Việc sẽ làm tiếp

### [TODO-01] Thanh toán online (VietQR / SePay)

**Mục tiêu:**
- Tích hợp SePay.vn (VietQR) hoặc VNPay
- Thêm trạng thái `PENDING_PAYMENT` → `PAID`
- Webhook nhận xác nhận thanh toán
- Lưu payment reference vào `Payment` entity

### [TODO-02] Push notifications (Firebase FCM)

**Mục tiêu:**
- Thêm FCM token vào `User` entity
- Gửi push notification song song với SSE
- Flutter: `firebase_messaging` package

### [TODO-03] Tối ưu hoá production

**Mục tiêu:**
- Đổi `ddl-auto=validate` thay `update`
- Flyway/Liquibase cho database migration
- Redis thay Caffeine cache (distributed)
- HTTPS + reverse proxy (Nginx)
