# ClothingStore — Nền Tảng Thương Mại Điện Tử Thời Trang

> Đồ án liên ngành 2026 · K17 · Nhóm N01  
> Spring Boot 3.5 · Thymeleaf · MySQL 8 · Flutter · AI Chatbot (Gemini) · Virtual Try-On (CatVTON)

---

## Tổng quan

**ClothingStore** (thương hiệu **NOVA**) là nền tảng bán quần áo đầy đủ tính năng, bao gồm:

- Web app (Spring Boot + Thymeleaf)  
- REST API (JWT) phục vụ mobile  
- Flutter mobile app (iOS/Android)  
- AI Chatbot tư vấn bằng Gemini function calling  
- Virtual Try-On (thử đồ ảo trên ảnh người dùng)

---

## Tech Stack

| Tầng | Công nghệ |
|------|-----------|
| Backend | Java 17 · Spring Boot 3.5 · Spring Security 6 · Spring Data JPA · Hibernate 6 |
| Web frontend | Thymeleaf · Tailwind CSS 3 · Vanilla JS |
| Mobile | Flutter 3.11+ · Dart · Riverpod 2.6 · GoRouter 14 · Dio 5 |
| Database | MySQL 8 · Spring Session JDBC · Caffeine Cache |
| Auth (web) | Form login · BCrypt · Spring Session |
| Auth (API) | JWT HS256 · stateless |
| AI Chatbot | Google Gemini 2.5 Flash · function calling · truy vấn DB thật |
| Virtual Try-On | Python FastAPI · Replicate IDM-VTON (cloud) → CatVTON local (GPU) · SegFormer |
| Real-time | Server-Sent Events (SSE) |
| Email | Gmail SMTP (async) |
| Export | Apache POI (.xlsx) |
| API docs | Springdoc OpenAPI · Swagger UI |
| Monitoring | Spring Boot Actuator |

---

## Tính năng

### Khách hàng

| Tính năng | Chi tiết |
|-----------|---------|
| Trang chủ | Hero banner slider 3 slide tự xoay · best sellers theo danh mục |
| Sản phẩm | Lọc (danh mục, giá, màu, từ khóa) · sắp xếp · phân trang |
| Full-text search | MySQL FULLTEXT INDEX · autocomplete gợi ý realtime (≤8 kết quả) |
| Chi tiết sản phẩm | Gallery ảnh theo `sort_order` · chọn size/màu · giá variant · tồn kho · sản phẩm tương tự |
| Đánh giá | Rating 1–5 sao · bình luận (≤1000 ký tự) · đính kèm tối đa 5 ảnh · 1 review/order item |
| Giỏ hàng | Session-based · hoạt động cả khi chưa đăng nhập |
| Thanh toán | COD · tùy chọn mã giảm giá · ghi chú · ship free ≥500k |
| Đơn hàng | Xem lịch sử · chi tiết · tự hủy khi PENDING · yêu cầu hủy khi PROCESSING |
| Wishlist | Thêm/xóa · danh sách yêu thích |
| Coupon | Xem mã · kiểm tra trước khi đặt · user-specific coupon |
| Thông báo | SSE real-time (xác nhận đơn · cập nhật trạng thái) · badge số chưa đọc |
| Hồ sơ | Cập nhật tên/SĐT/địa chỉ · đổi mật khẩu |
| Referral | Mã giới thiệu riêng · cả 2 bên nhận coupon khi đơn đầu tiên hoàn tất |
| Virtual Try-On | Studio riêng (`/tryon-studio`) · modal trên trang chi tiết SP |
| AI Chatbot | Gemini function calling · tư vấn tiếng Việt · tìm SP theo loại/màu/giá |

### Admin

> **UI admin dạng modal:** mọi thao tác Thêm / Sửa / Xem chi tiết mở trong **modal AJAX làm mờ nền** (không nhảy trang riêng). Submit qua `fetch` → đóng modal + toast + tự refresh bảng; lỗi hiện banner trong modal, giữ nguyên dữ liệu nhập.

| Module | Tính năng |
|--------|-----------|
| Dashboard | KPI snapshot (AJAX) · biểu đồ doanh thu ngày/tuần/tháng/năm · cảnh báo tồn kho thấp · xuất Excel |
| Sản phẩm | CRUD **trong modal rộng** · gallery ảnh kéo-thả sắp xếp · ảnh đầu = bìa · nút ✕ xóa ảnh · variants (size/màu/giá/stock) |
| Try-On | Toggle on/off per sản phẩm · upload garment · chọn loại (UPPER/LOWER) · lưu chung 1 nút Save |
| Danh mục | CRUD Category + SubCategory **trong modal** |
| Đơn hàng | Danh sách toàn bộ · **xem chi tiết trong modal** · cập nhật trạng thái (AJAX) · duyệt yêu cầu hủy |
| Người dùng | Danh sách · tạo/sửa **trong modal** · gán quyền ADMIN |
| Coupon | Tạo/sửa **trong modal** · PERCENTAGE/FIXED · thời hạn · ngưỡng đơn · giới hạn lượt dùng · user-specific |
| Thông báo | Gửi thông báo real-time đến user qua SSE |
| Analytics | Top sản phẩm · trending · KPI overview (ADMIN only) |

---

## Phân quyền & Bảo mật

### Hai security filter chain (theo thứ tự ưu tiên)

#### Chain 1 — API (`/api/**`, Order=1)
- CSRF **tắt** · JWT stateless · CORS bật
- **Public**: `POST /api/auth/*` · `GET /api/products/**` · `GET /api/categories/**` · `GET /api/subcategories/**` · `/api/cart/**` · `/api/recommendations/**` · `/api/chatbot/**` · `POST /api/coupons/validate` · `/api/tryon/**` · `POST /api/orders/checkout` · `GET /actuator/health`
- **Authenticated** (JWT): `/api/notifications/**` · `/api/profile/**` · `/api/coupons/my` · mọi request chưa liệt kê
- **ADMIN only**: `/api/analytics/**` · `/api/admin/**` · `/actuator/**` (trừ `/health`)

#### Chain 2 — Web (`/**`, Order=2)
- CSRF **bật** · Form login · Spring Session JDBC
- **Public**: `/` · `/login` · `/register` · `/products/**` · `/product/**` · `/cart/**` · `/checkout/**` · `/tryon-studio` · `/contact` · `/returns` · `/sizing` · `/forgot-password` · `/reset-password` · `/css/**` · `/js/**` · `/images/**` · `/uploads/**`
- **Authenticated**: `/my-orders` · `/my-coupons` · `/profile` · `/orders/**` · `/reviews/**` · `/wishlist/**`
- **ADMIN only**: `/admin/**`

### Cơ chế bảo mật
- BCrypt (10 rounds) cho password
- JWT HS256 · `Authorization: Bearer <token>`
- Login rate limiting: tối đa 5 lần sai / IP / 15 phút (`LoginRateLimitFilter`)
- Upload validate magic bytes (không tin vào đuôi file) · chống path traversal
- Security headers: `X-Frame-Options: DENY` · `HSTS 1 năm` · `X-Content-Type-Options: nosniff` · `Referrer-Policy: strict-origin-when-cross-origin`
- Actuator chỉ expose `health` (public) + các endpoint khác (ADMIN)

---

## REST API — Đầy đủ endpoint

### Auth (`/api/auth`) — Public
```
POST /api/auth/register           Đăng ký → JWT  (body: email, password, fullName, ref?)
POST /api/auth/login              Đăng nhập → JWT (body: email, password)
POST /api/auth/forgot-password    Gửi email reset (body: email) — luôn 200, không leak
POST /api/auth/reset-password     Đặt lại mật khẩu (body: token, password, confirmPassword)
```

### Sản phẩm (`/api/products`) — Public
```
GET  /api/products                Danh sách phân trang
                                  ?page=0&size=12&sort=newest|oldest|name_asc|name_desc|price_asc|price_desc
                                  &keyword=&categoryId=&subCategoryId=&minPrice=&maxPrice=
GET  /api/products/suggest?q=     Autocomplete full-text (≤8 kết quả, xếp theo relevance)
GET  /api/products/{id}           Chi tiết sản phẩm (đầy đủ variants, ảnh)
GET  /api/products/{id}/similar   Sản phẩm tương tự (?limit=6)
```

### Giỏ hàng (`/api/cart`) — Public (session-based)
```
GET    /api/cart                  Xem giỏ hàng {items, total, itemCount}
POST   /api/cart/add              Thêm (body: variantId, quantity)
PUT    /api/cart/update           Sửa số lượng (body: variantId, quantity)
DELETE /api/cart/{variantId}      Xóa 1 item
DELETE /api/cart                  Xóa toàn bộ giỏ
```

### Đơn hàng (`/api/orders`)
```
POST /api/orders/checkout         Đặt hàng — Public (body: customerName, phone, address, couponCode?, note?)
GET  /api/orders/my               Đơn của tôi — Authenticated
POST /api/orders/{id}/cancel      Tự hủy khi PENDING — Authenticated
POST /api/orders/{id}/cancel-request   Yêu cầu hủy khi PROCESSING (?reason=) — Authenticated
```

### Hồ sơ (`/api/profile`) — Authenticated
```
GET  /api/profile                 Thông tin cá nhân
PUT  /api/profile                 Cập nhật (body: fullName, phone?, address?)
POST /api/profile/change-password Đổi mật khẩu (body: oldPassword, newPassword, confirmPassword)
```

### Wishlist (`/api/wishlist`) — Authenticated
```
GET    /api/wishlist              Danh sách yêu thích
POST   /api/wishlist/{productId}  Thêm vào wishlist
DELETE /api/wishlist/{productId}  Xóa khỏi wishlist
```

### Coupon (`/api/coupons`)
```
POST /api/coupons/validate        Kiểm tra mã (body: code, orderTotal) — Public
GET  /api/coupons/my              Coupon của tôi — Authenticated
```

### Thông báo (`/api/notifications`) — Authenticated
```
GET  /api/notifications           20 thông báo gần nhất
GET  /api/notifications/count     Số chưa đọc (cho badge)
POST /api/notifications/{id}/read Đánh dấu đã đọc
POST /api/notifications/read-all  Đánh dấu tất cả đã đọc
```

### SSE (`/notifications/stream`) — Authenticated
```
GET  /notifications/stream        SSE stream (event: notification) — EventSource
```

### Danh mục — Public
```
GET /api/categories               Tất cả category (lồng subcategory)
GET /api/subcategories            Tất cả subcategory
GET /api/subcategories/by-category/{id}  Subcategory theo category (dùng cho admin select)
```

### AI Chatbot (`/api/chatbot`) — Public
```
POST /api/chatbot                 Gửi tin nhắn (body: message)
                                  Response: { message, products[] }
```

### Virtual Try-On (`/api/tryon`) — Public
```
POST /api/tryon/upload-person     Upload ảnh người (max 5MB, jpg/png/webp, validate magic bytes)
                                  Response: { personId, filename, url }
POST /api/tryon/generate          Thử 1 sản phẩm (personId, productId) → JPEG bytes
POST /api/tryon/generate-outfit   Thử bộ đôi (personId, topProductId, bottomProductId) → JPEG bytes
GET  /api/tryon/health            Kiểm tra Python server
```

### Analytics (`/api/analytics`) — **ADMIN only**
```
GET /api/analytics/top-products   Top SP bán chạy (?limit=10)
GET /api/analytics/trending       Trending products (?limit=8)
GET /api/analytics/overview       KPI overview (totalOrders, revenue, users, stock, sold)
```

### Admin REST (`/api/admin`) — **ADMIN only**
```
GET  /api/admin/stats/summary              KPI snapshot (totalOrders, totalUsers, pendingOrders)
POST /api/admin/orders/{id}/status         Cập nhật trạng thái đơn (body: status)
POST /api/admin/products/bulk-status       Bật/tắt hàng loạt SP (body: ids[], active)
```

### Actuator
```
GET /actuator/health     Trạng thái ứng dụng — Public
GET /actuator/**         Chi tiết metrics — ADMIN only
```

Swagger UI: **http://localhost:8080/swagger-ui.html**

---

## Database — Entities & Schema

### Entities (23 class, 20 bảng + 2 @ElementCollection + enums)

| Entity | Bảng | Mô tả |
|--------|------|-------|
| `User` | `users` | email · password (BCrypt) · fullName · phone · address · role · referralCode · referredById · referralRewarded |
| `Product` | `products` | name · slug (unique) · description · subCategory · active · tryOnEnabled · garmentProcessedUrl · garmentType · minPrice (denorm) · totalSold (denorm) |
| `ProductVariant` | `product_variants` | sku · size · color · price · stock · sold · weight · → Product |
| `ProductImage` | `product_images` | imageUrl · primaryImage · **sortOrder** · → Product |
| `Category` | `categories` | name · slug |
| `SubCategory` | `sub_categories` | name · slug · sizeType · → Category |
| `Order` | `orders` | total · shippingFee · customerName · phone · address · note · cancelReason · status · version (@Version optimistic lock) · → User |
| `OrderItem` | `order_items` | quantity · price · → Order · → ProductVariant |
| `Review` | `reviews` | rating (1–5) · comment · imageUrls (@ElementCollection → `review_images`) · → User · → OrderItem (unique constraint) |
| `Coupon` | `coupons` | code · discountType (PERCENTAGE/FIXED) · discountValue · minOrderAmount · startDate · expiryDate · usageLimit · usageCount · userSpecific |
| `UserCoupon` | `user_coupons` | usedAt · → User · → Coupon |
| `WishlistItem` | `wishlist_items` | → User · → Product |
| `Notification` | `notifications` | title · message · isRead · type · referenceId · referenceType · → User |
| `PasswordResetToken` | `password_reset_tokens` | token · expiryDate · → User |
| `Payment` | `payments` | amount · method · status · → Order |
| `Shipment` | `shipments` | trackingNumber · carrier · shippedAt · → Order |
| `AuditLog` | `audit_logs` | action · entityType · entityId · userId · detail |
| `StockLog` | `stock_logs` | variantId · delta · reason · createdAt |
| `Address` | `addresses` | street · city · district · → User |

### Enums
| Enum | Giá trị |
|------|---------|
| `Role` | `USER`, `ADMIN` |
| `OrderStatus` | `PENDING`, `PROCESSING`, `CANCEL_REQUESTED`, `SHIPPING`, `COMPLETED`, `CANCELLED` |
| `GarmentType` | `UPPER_BODY`, `LOWER_BODY` |
| `SizeType` | (xem code) |
| `Coupon.DiscountType` | `PERCENTAGE`, `FIXED` |

`spring.jpa.hibernate.ddl-auto=update` — schema tự tạo/cập nhật khi khởi động kể cả cột mới.

### Cấu hình JPA / connection pool
- `spring.jpa.open-in-view=false` — **tắt** open-session-in-view. Trước đây để bật (mặc định), mỗi kết nối SSE `/notifications/stream` (sống ~30 phút) giữ một JDBC connection suốt vòng đời → cạn pool. Các trang server-render dùng lazy association nay fetch tường minh bằng `@EntityGraph` / `JOIN FETCH`:
  - `OrderRepository`: `findByActorOrderByCreatedAtDesc` (+items) · `findByIdWithItems` (+items, +actor) · `findTop5ByOrderByCreatedAtDesc` (+actor) · `searchAdmin` (+actor)
  - `ReviewRepository.findAllByItemIdOrderByCreatedAtDesc` (+imageUrls, +actor)
  - `WishlistItemRepository.findByUser` (+product, +product.images)
  - `ProductRepository.findProductForEdit` (+images) · các query list/detail đã có sẵn @EntityGraph (images, productVariants, subCategory.category)
- HikariCP: `maximum-pool-size=20` · `minimum-idle=5` · `leak-detection-threshold=30000` · `connection-timeout=10000`.

---

## AI Chatbot

### Kiến trúc

```
User message
    │
    ▼
AiChatbotService.processMessage(message, history)
    ├─ AI không cấu hình / cooldown → offlineFallback() (trả best-sellers + text)
    │
    └─ Gemini 2.5 Flash ◄──────────────────────────────────────────────────┐
         ├─ System prompt:                                                  │
         │    • Chính sách NOVA (ship/đổi trả/size/thanh toán)             │
         │    • Danh mục đọc động từ subCategoryRepository                 │
         │    • Quy tắc: gọi tool khi cần SP thật, không bịa               │
         │                                                                  │
         ├─ Tool declarations (function calling):                           │
         │    • search_products(category?, subcategory?, color?, keyword?,  │
         │                      minPrice?, maxPrice?, limit?)               │
         │    • get_best_sellers(limit?)                                    │
         │    • get_product_details(name)                                   │
         │                                                                  │
         ├─ Gemini trả functionCall → executeFunction()                     │
         │    ├─ search_products → ProductService.findWithFilter()          │
         │    │   (fallback 5 bước: giảm dần tiêu chí)                     │
         │    ├─ get_best_sellers → ProductRepository.findBestSellers()     │
         │    └─ get_product_details → ProductService.fullTextSearch()      │
         │         (trả size/màu/tồn kho/giá từng variant)                 │
         │                                                                  │
         └─ Gemini nhận functionResponse → tổng hợp lời tư vấn ───────────┘
                    (tối đa MAX_STEPS=4 vòng lặp)
    │
    ▼
ChatbotResponse { message: String, products: List<ProductSummary> }
```

### Tính năng chatbot
- Lịch sử hội thoại: tối đa 12 lượt lưu trong `HttpSession` (key `chatbot_history`)
- Cooldown tự động: 5 phút sau lỗi 429/401/403 (`aiDisabledUntil volatile`)
- `thinkingBudget=0` để tắt thinking của Gemini 2.5 Flash (tránh cụt token)
- Fallback: trả best-sellers + text thân thiện khi API lỗi

---

## Virtual Try-On

### Kiến trúc

```
Upload ảnh người
    │  POST /api/tryon/upload-person
    │  ├─ Validate: max 5MB · jpg/png/webp · magic bytes check
    │  └─ Lưu tạm: {upload.dir}/tryon-persons/{UUID}.ext
    │     (tự xóa sau mỗi lần generate)
    ▼
TryOnApiController.generateTryOn() / generateOutfitTryOn()
    │
    ▼
TryOnService → Python FastAPI (port 8081)
    ├─ Single garment: POST /tryon  (person + garment → JPEG)
    └─ Outfit (top+bottom): POST /tryon/outfit
         ├─ Chạy CatVTON 2 lần trên ảnh gốc (KHÔNG chain tuần tự)
         └─ Composite từng vùng bằng SegFormer mask

Python server:
    ├─ Tier 1 — Replicate IDM-VTON (cloud): khi có REPLICATE_API_TOKEN + còn quota
    └─ Tier 2 — CatVTON local (GPU ≥4GB VRAM): fallback tự động
         ├─ Model: SD-inpainting + CatVTON attention (mix-48k-1024)
         ├─ SegFormer (mattmdjaga/segformer_b2_clothes) tự tải lần đầu
         └─ UniPC 20 steps, 768×1024
```

### Admin Try-On
- Toggle `tryOnEnabled` on/off per sản phẩm
- Upload `garmentImage` + chọn `garmentType` (UPPER_BODY / LOWER_BODY)
- Gọi `TryOnService.updateTryOnSettings()`: preprocess qua Python (rembg background removal), fallback lưu raw nếu Python offline
- Lưu `garmentProcessedUrl` + `garmentType` trên Product entity

### Giao diện người dùng
- **Try-On Studio** (`/tryon-studio`): sidebar 420px · gallery sản phẩm theo tab (Tops/Bottoms/All) · upload ảnh người (max 420px preview) · ảnh kết quả (min-height 300px) · bấm ảnh để phóng to lightbox
- **Product Detail modal**: modal rộng 1040px · cột trái upload (max 480px) · cột phải kết quả (max 520px) · lightbox zoom

---

## Admin — Modal AJAX (create / edit / detail)

Toàn bộ thao tác Thêm / Sửa / Xem chi tiết của admin chạy trong **modal** thay vì trang riêng:

```
Bấm "Add New" / "Edit" / "View"  (link có data-modal)
    │  admin-modal.js: fetch URL kèm header X-Requested-With: XMLHttpRequest
    ▼
Controller phát hiện AJAX (AdminBaseController.isAjax)
    └─ trả FRAGMENT  "admin/<module>/_form :: form"  (chỉ phần [data-modal-content])
    ▼
admin-modal.js nhét fragment vào #ajaxModal → mở overlay (nền mờ)
    │
    ▼  Submit form (data-modal-form) qua fetch + FormData (giữ upload ảnh)
Controller POST trả JSON:
    ├─ {ok:true,  message} → đóng modal · toast · adminSpaReload() refresh bảng
    └─ {ok:false, error}   → hiện banner đỏ trong modal, giữ dữ liệu đang nhập
```

- **Module áp dụng**: categories · subcategories · coupons · users · products (`data-modal-wide` 880px) · orders (xem chi tiết).
- **Fragment**: `templates/admin/<module>/_form.html` (hoặc `_create_form.html`/`_edit_form.html` cho products, `_detail.html` cho orders).
- **Fallback non-AJAX**: GET form redirect về list kèm `?modal=create|edit|order&id=...`; client tự mở modal qua query param. Không còn trang riêng.
- **Helper controller**: `AdminBaseController.isAjax() / ok() / fail()` dùng chung cho mọi module.
- **JS**: `admin-modal.js` (overlay + fetch + submit) phối hợp `admin-spa.js` (link `data-modal` được bỏ qua SPA-navigate).

---

## Admin — Quản lý sản phẩm

### Gallery ảnh kéo-thả

Modal **Thêm** và **Sửa** đều có gallery giống nhau:
1. Kéo-thả thumbnail để sắp xếp thứ tự
2. Ảnh đầu tiên → nhãn **COVER** + `primaryImage=true` + `sortOrder=0`
3. Nút **✕** trên từng thumbnail để xóa ngay
4. Trang **Sửa**: trộn ảnh cũ (`E{id}`) + ảnh mới (`N{index}`) trong 1 gallery duy nhất, gửi `imageOrder[]` token lên server khi submit

### Thứ tự ảnh (ProductImage)
- Cột `sort_order` (Integer, default 0) trên bảng `product_images`
- JPA `@OrderBy("sortOrder ASC, primaryImage DESC, id ASC")`
- `Product.getImages()` sort in-memory theo cùng tiêu chí (cho trường hợp chưa flush)

### Try-On — 1 nút Save duy nhất
- Card Try-On nằm trong form chính `productEditForm` (trong modal Sửa, không còn form riêng)
- Toggle on/off: JS `syncTryOn()` ẩn/hiện trường upload theo `th:checked="${product.tryOnEnabled}"`
- Khi submit: `AdminProductController.updateProduct()` → `productService.updateProduct()` → `tryOnService.updateTryOnSettings()`

---

## Referral System

```
User A đăng ký → nhận referralCode (16 ký tự unique)
  │
  │  User B đăng ký dùng ?ref=<code> hoặc POST /api/auth/register?ref=...
  │  → user B.referredById = user A.id
  │
  ▼
User B đặt đơn đầu tiên → đơn COMPLETED/DELIVERED
  │
  ▼
ReferralService.processReferralReward()
  ├─ Tạo UserCoupon cho User A (cảm ơn giới thiệu)
  ├─ Tạo UserCoupon cho User B (chào mừng)
  └─ Đặt user A.referralRewarded = true (chỉ thưởng 1 lần)
```

---

## Cài đặt & Chạy

### Yêu cầu

| Thành phần | Phiên bản | Bắt buộc |
|------------|-----------|----------|
| Java (Temurin JDK) | 17+ | ✅ |
| MySQL | 8.x | ✅ |
| Python | 3.10+ | Virtual Try-On |
| GPU NVIDIA (CUDA) | ≥4GB VRAM | CatVTON local |
| Flutter SDK | 3.11+ | Mobile app |
| Gemini API key | — | AI Chatbot (free) |

### Bước 1 — Clone & database

```bash
git clone <repo-url>
cd clothingstore
```

```sql
CREATE DATABASE clothingstore CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### Bước 2 — `application.properties`

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/clothingstore
spring.datasource.username=root
spring.datasource.password=
```

### Bước 3 — Chạy backend

> **Bắt buộc**: set `DEV_ADMIN_PASSWORD` — `DataInitializer` dùng để tạo admin, thiếu là app dừng.

```bash
# Windows (PowerShell)
$env:DEV_ADMIN_PASSWORD = "AdminPass123!"
$env:GEMINI_API_KEY     = "AIza...your-key..."   # Tùy chọn
./mvnw spring-boot:run

# macOS/Linux
export DEV_ADMIN_PASSWORD=AdminPass123!
export GEMINI_API_KEY=AIza...your-key...
./mvnw spring-boot:run
```

**Tài khoản mặc định (do DataInitializer tạo):**

| Role | Email | Mật khẩu |
|------|-------|----------|
| Admin | admin@test.com | `DEV_ADMIN_PASSWORD` |
| User | user@test.com | `User@Dev2024!` |
| User demo | lan@test.com · minh@test.com · huong@test.com · duc@test.com | `User@Dev2024!` |

### Bước 4 — (Tùy chọn) AI Chatbot

```bash
# Lấy key free: https://aistudio.google.com/apikey
# Chọn "Create API key in new project" để có đầy đủ free tier

# Windows
$env:GEMINI_API_KEY = "AIza..."
# Linux/macOS
export GEMINI_API_KEY="AIza..."
```

> Model mặc định: `gemini-2.5-flash`. Đổi model: set `GEMINI_MODEL=<tên>`.  
> Không set key → chatbot vẫn trả lời (fallback best-sellers).

### Bước 5 — (Tùy chọn) Flutter app

```bash
cd mobile-app
flutter pub get
flutter run
# Base URL mặc định: http://10.0.2.2:8080 (Android) / http://localhost:8080 (iOS)
# Sửa trong lib/core/network/api_client.dart nếu cần
```

### Bước 6 — (Tùy chọn) Virtual Try-On server

```bash
cd python-tryon-server
pip install -r requirements.txt

# CatVTON source (~38MB)
git clone https://github.com/Zheng-Chong/CatVTON catvton_src

# Weights (~4GB)
python download_models.py

# Khởi động port 8081
python main.py
```

---

## Biến môi trường

### Backend

| Biến | Mặc định | Bắt buộc | Mô tả |
|------|---------|----------|-------|
| `DEV_ADMIN_PASSWORD` | — | ✅ | Mật khẩu admin seed; thiếu là app dừng |
| `DEV_USER_PASSWORD` | `User@Dev2024!` | — | Mật khẩu user demo |
| `MAIL_USERNAME` | nguyennhatminh1811@gmail.com | — | Gmail gửi email |
| `MAIL_PASSWORD` | — | — | Gmail App Password |
| `JWT_SECRET` | *(default local)* | — | JWT signing key ≥32 ký tự |
| `APP_PUBLIC_BASE_URL` | http://localhost:8080 | — | Base URL trong link email |
| `CHATBOT_AI_ENABLED` | true | — | Bật/tắt AI chatbot |
| `GEMINI_API_KEY` | *(trống)* | — | **Không hardcode vào file** |
| `GEMINI_MODEL` | gemini-2.5-flash | — | Model Gemini |
| `GEMINI_BASE_URL` | https://generativelanguage.googleapis.com/v1beta | — | Gemini API base URL |
| `TRYON_PYTHON_URL` | http://localhost:8081 | — | Python Try-On server |
| `MOCK_INFERENCE` | false | — | Mock Try-On (trả ảnh giả, không cần GPU) |

### Python Try-On Server (`.env`)

| Biến | Mặc định | Mô tả |
|------|---------|-------|
| `REPLICATE_API_TOKEN` | *(trống)* | Token Replicate (cloud); có → dùng IDM-VTON |
| `HF_TOKEN` | *(trống)* | HuggingFace token |
| `TRYON_STEPS` | 20 | Số bước diffusion (16–25) |
| `TRYON_SCHEDULER` | unipc | `unipc` / `dpm` / `ddim` |
| `TRYON_CFG` | 2.5 | Guidance scale |
| `TRYON_PARSER_DEVICE` | cpu | `cpu` / `cuda` (SegFormer) |
| `TRYON_PARSER_MODEL` | mattmdjaga/segformer_b2_clothes | Model human-parsing |

---

## Cấu trúc thư mục

```
clothingstore/
├── src/main/java/com/shop/clothingstore/
│   ├── config/
│   │   ├── SecurityConfig.java        2 filter chain (API + Web)
│   │   ├── ChatbotAiProperties.java   Gemini config
│   │   ├── CacheConfig.java           Caffeine (bestSellers, tryOnProducts)
│   │   ├── AsyncConfig.java           tryOnExecutor thread pool
│   │   ├── DataInitializer.java       Seed data khi khởi động
│   │   └── WebConfig.java             Static resources, CORS
│   │
│   ├── controller/
│   │   ├── api/
│   │   │   ├── AuthApiController.java          POST /api/auth/*
│   │   │   ├── ProductApiController.java       GET  /api/products/**
│   │   │   ├── CartApiController.java          /api/cart/**
│   │   │   ├── OrderApiController.java         /api/orders/**
│   │   │   ├── UserApiController.java          /api/profile/**
│   │   │   ├── WishlistApiController.java      /api/wishlist/**
│   │   │   ├── CouponApiController.java        /api/coupons/**
│   │   │   ├── NotificationApiController.java  /api/notifications/**
│   │   │   ├── CategoryApiController.java      /api/categories/**
│   │   │   ├── SubCategoryApiController.java   /api/subcategories/**
│   │   │   ├── TryOnApiController.java         /api/tryon/**
│   │   │   ├── ChatbotApiController.java       /api/chatbot
│   │   │   ├── RecommendationApiController.java /api/recommendations/**
│   │   │   ├── AnalyticsApiController.java     /api/analytics/** (ADMIN)
│   │   │   └── AdminApiController.java         /api/admin/** (ADMIN)
│   │   │
│   │   ├── admin/ (web, /admin/**)
│   │   │   ├── AdminProductController.java     CRUD sản phẩm + ảnh + try-on
│   │   │   ├── AdminOrderController.java       Quản lý đơn hàng
│   │   │   ├── AdminUserController.java        Quản lý người dùng
│   │   │   ├── AdminCategoryController.java    Danh mục
│   │   │   ├── AdminSubCategoryController.java Danh mục con
│   │   │   ├── AdminCouponController.java      Mã giảm giá
│   │   │   ├── AdminDashboardController.java   Dashboard + Excel export
│   │   │   ├── AdminTryOnController.java       /admin/products/{id}/tryon/* (legacy)
│   │   │   └── AdminBaseController.java        Base class
│   │   │
│   │   └── (web, public/authenticated)
│   │       ├── ShopController.java             /, /products, /product/{slug}
│   │       ├── AuthController.java             /login, /register, /forgot-password, /reset-password
│   │       ├── CartController.java             /cart
│   │       ├── CheckoutController.java         /checkout, /checkout/success
│   │       ├── OrderController.java            /my-orders, /orders/**
│   │       ├── ProfileController.java          /profile
│   │       ├── ReviewController.java           /reviews/**
│   │       ├── TryOnStudioController.java      /tryon-studio
│   │       ├── WishlistWebController.java      /wishlist
│   │       ├── CouponController.java           /my-coupons
│   │       └── SseNotificationController.java  /notifications/stream (SSE)
│   │
│   ├── service/
│   │   ├── AiChatbotService.java      Gemini function calling
│   │   ├── TryOnService.java          Garment preprocess + outfit compositing
│   │   ├── ProductService.java        CRUD + full-text + image ordering
│   │   ├── OrderService.java          Quản lý đơn hàng + trạng thái
│   │   ├── CheckoutService.java       Đặt hàng + kiểm tra stock + coupon
│   │   ├── CartService.java           Giỏ hàng (session-based)
│   │   ├── UserService.java           CRUD người dùng
│   │   ├── ReferralService.java       Xử lý thưởng referral
│   │   ├── CouponService.java         Validate + apply coupon
│   │   ├── NotificationService.java   Tạo + đọc + đánh dấu thông báo
│   │   ├── SseService.java            SSE emitter pool
│   │   ├── ReviewService.java         Tạo + lấy đánh giá
│   │   ├── WishlistService.java       Wishlist
│   │   ├── EmailService.java          Gmail SMTP async
│   │   ├── DashboardService.java      KPI + biểu đồ doanh thu
│   │   ├── ReportService.java         Xuất Excel
│   │   ├── RecommendationService.java Sản phẩm tương tự
│   │   ├── CategoryService.java       Danh mục
│   │   ├── SubCategoryService.java    Danh mục con
│   │   ├── PasswordResetService.java  Token reset mật khẩu
│   │   ├── PaymentService.java        Ghi nhận payment
│   │   ├── ShipmentService.java       Ghi nhận shipment
│   │   └── ai/
│   │       └── GeminiChatClient.java  REST client Gemini generateContent API
│   │
│   ├── entity/           23 entity class + enums (xem mục Database)
│   ├── repository/       19 Spring Data JPA repository
│   ├── dto/              Form DTOs + API request/response
│   ├── security/         JwtUtil · JwtAuthenticationFilter · LoginRateLimitFilter
│   └── exception/        Custom exceptions
│
├── src/main/resources/
│   ├── application.properties
│   ├── templates/
│   │   ├── layout/       base.html (chatbot widget + SSE) · admin.html (modal host + CSS)
│   │   ├── auth/         login · register · forgot-password · reset-password
│   │   ├── admin/        dashboard · mỗi module: index.html + fragment modal
│   │   │                 (categories/_form · subcategories/_form · coupons/_form ·
│   │   │                  users/_form · products/_create_form + _edit_form · orders/_detail)
│   │   └── shop/         home · products · product/detail · cart · checkout
│   │                     my-orders · order/detail · profile · wishlist
│   │                     my-coupons · tryon-studio · contact · returns · sizing
│   └── static/
│       ├── js/           admin-spa.js (SPA điều hướng) · admin-modal.js (modal AJAX)
│       └── tailwind.css · images
│
├── src/test/java/...     JUnit5 — service unit tests + controller MockMvc tests (shop + api)
├── mobile-app/           Flutter app (lib/core · lib/features · lib/models · lib/router)
├── python-tryon-server/  FastAPI (main.py · catvton_src/ · weights/ · download_models.py)
├── uploads/              Ảnh upload (ngoài git: products/ · tryon-garments/ · tryon-persons/ · reviews/)
├── docs/                 features.md · changelog.md · work-log.md
├── pom.xml
└── package.json          Tailwind/PostCSS tooling
```

---

## Build & Test

```bash
# Chạy toàn bộ test
./mvnw test

# Build JAR
./mvnw clean package -DskipTests

# Chạy JAR
DEV_ADMIN_PASSWORD=xxx GEMINI_API_KEY=xxx java -jar target/clothingstore-*.jar

# Flutter
cd mobile-app
flutter build apk --release   # Android
flutter build ios --release    # iOS
```

### Test suite (JUnit 5 · Mockito · Spring Security Test · H2) — 125 test

| Loại | Lớp test | Phủ |
|------|----------|-----|
| Service (unit) | `CheckoutServiceTest` · `CouponServiceTest` · `OrderServiceTest` · `ProductServiceTest` · `ReferralServiceTest` | Logic nghiệp vụ: checkout/stock/ship, coupon, trạng thái đơn, referral |
| API controller (`@SpringBootTest` + MockMvc) | `ProductApiControllerTest` · `WishlistApiControllerTest` · `ChatbotApiControllerTest` | Endpoint JSON, auth 401, 404 |
| Web controller (standalone MockMvc) | `Cart` · `Wishlist` · `Checkout` · `Order` · `Review` · `Profile` · `Coupon` · `Shop` ControllerTest | Mọi UC shop: view-name + model, redirect, flash, validate, phân quyền |

- Web controller test dùng `MockMvcBuilders.standaloneSetup(controller)` — cô lập từng controller với ViewResolver stub, **không** render layout Thymeleaf thật (tránh phụ thuộc `_csrf`/security dialect) và **không** boot full context → nhanh, ổn định.
- Test cần entity ID dùng reflection (`BaseEntity.id` protected). `@AuthenticationPrincipal` được seed qua `SecurityContextHolder`.

---

## Roadmap

- [x] Web app đầy đủ (Spring Boot + Thymeleaf)
- [x] REST API + JWT cho mobile
- [x] Flutter mobile app (iOS/Android)
- [x] Full-text search (MySQL FULLTEXT + autocomplete)
- [x] Hero banner slider
- [x] Review có ảnh đính kèm
- [x] SSE real-time notification
- [x] Referral system
- [x] Virtual Try-On (studio + product detail modal)
- [x] Admin image gallery kéo-thả + sort_order
- [x] Admin Try-On unified save (1 nút)
- [x] AI Chatbot Gemini 2.5 Flash + function calling
- [x] Admin UI modal AJAX (create/edit/detail toàn bộ module)
- [x] Tắt OSIV + sửa cạn HikariCP pool (@EntityGraph fetch tường minh)
- [ ] Thanh toán online (VietQR / SePay)
- [ ] Push notifications (Firebase FCM)

---

## Nhóm phát triển

**Đồ án liên ngành 2026 — K17 — Nhóm N01**

---

*Tính năng đầy đủ: [docs/features.md](docs/features.md)*  
*Lịch sử thay đổi: [docs/changelog.md](docs/changelog.md)*  
*Nhật ký công việc: [docs/work-log.md](docs/work-log.md)*
