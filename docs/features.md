# Tính năng hệ thống — ClothingStore (NOVA)

> Tài liệu này mô tả chi tiết tất cả tính năng của nền tảng dựa trên code thực tế.  
> Cập nhật lần cuối: 2026-06-02

---

## Tóm tắt nhanh

| Module | Tính năng nổi bật |
|--------|------------------|
| Auth | Đăng ký/đăng nhập web + API · JWT · reset mật khẩu email · rate limit login 10 / register 5 (IP/15min) |
| Sản phẩm | CRUD · variants (size/màu/giá/stock) · gallery ảnh kéo-thả sort_order · full-text search |
| Đơn hàng | COD · ship free ≥500k · coupon · tự hủy / yêu cầu hủy · 6 trạng thái |
| Coupon | PERCENTAGE/FIXED · thời hạn · ngưỡng đơn · user-specific · referral reward |
| Referral | Mã riêng 16 ký tự · thưởng cả 2 bên khi đơn đầu COMPLETED |
| Virtual Try-On | Studio riêng + modal trang SP · thử đơn / outfit (áo+quần) · lightbox |
| AI Chatbot | Gemini 2.5 Flash · function calling · truy vấn DB thật · tiếng Việt |
| Notifications | SSE real-time · badge số chưa đọc · đánh dấu đọc |
| Admin | Dashboard biểu đồ · KPI · xuất Excel · bulk-status sản phẩm |
| Mobile | Flutter iOS/Android · Riverpod · JWT · đầy đủ tính năng shop |

---

## 1. Xác thực & Phân quyền

### 1.1 Hai role
- `USER` — khách hàng thông thường
- `ADMIN` — quản trị viên (có thể gán từ trang admin)

### 1.2 Hai cơ chế auth song song
- **Web** (chain 2): Form login → Spring Session JDBC · CSRF bật · logout xóa cookie JSESSIONID
- **API** (chain 1): `Authorization: Bearer <JWT HS256>` · CSRF tắt · stateless · CORS bật

### 1.3 Đăng ký
- `POST /api/auth/register` — tạo user mới với role `USER`
- Có thể truyền `ref=<referralCode>` để liên kết referral
- Trả JWT ngay sau đăng ký

### 1.4 Đăng nhập
- **Web**: form `/login` → `CustomAuthenticationSuccessHandler` redirect theo role
- **API**: `POST /api/auth/login` → JWT (`{ token, email, role }`)

### 1.5 Quên/đặt lại mật khẩu
- `POST /api/auth/forgot-password` → tạo `PasswordResetToken` (bảng `password_reset_tokens`) → gửi link email async
- Luôn trả 200 (không leak email có/không tồn tại)
- `POST /api/auth/reset-password` → validate token + expiry → đặt mật khẩu mới

### 1.6 Rate limiting
- `LoginRateLimitFilter`: đăng nhập tối đa **10 lần / IP / 15 phút**; đăng ký **5 lần / IP / 15 phút** (trả HTTP 429 khi vượt)
- Chạy trước JWT filter (từ chối sớm, tiết kiệm tài nguyên)

### 1.6b Phân quyền: Guest (khách vãng lai) vs User (có tài khoản)
> Khách **chưa đăng nhập vẫn mua được** ở phía backend/web; điểm khác biệt nằm ở các tính năng gắn với tài khoản và ở chỗ **app mobile bắt đăng nhập trước khi checkout**.

| Chức năng | Guest — Web/API | Guest — Mobile | User (đăng nhập) |
|---|---|---|---|
| Duyệt / tìm / lọc sản phẩm, chi tiết, gợi ý | ✓ | ✓ | ✓ |
| Giỏ hàng (session-based) | ✓ | ✓ | ✓ |
| AI Chatbot · Virtual Try-On | ✓ | ✓ | ✓ |
| Kiểm tra coupon công khai (`/api/coupons/validate`) | ✓ | ✓ | ✓ |
| **Đặt hàng (checkout COD)** | ✓ — đơn **không gắn tài khoản** (`actor = null`) | ✗ — router chặn `/checkout` → ép `/login` | ✓ — đơn **gắn tài khoản** |
| Đề xuất coupon ở checkout (`/api/coupons/available`) | rỗng (không có user) | rỗng | ✓ danh sách coupon của user |
| Coupon user-specific (welcome/referral) | ✗ | ✗ | ✓ |
| Lịch sử đơn (`/my-orders`, `/api/orders/my`) | ✗ | ✗ | ✓ |
| Wishlist · Hồ sơ · Đổi mật khẩu | ✗ | ✗ | ✓ |
| Coupon của tôi (`/api/coupons/my`) | ✗ | ✗ | ✓ |
| Viết đánh giá (sau đơn `COMPLETED`) | ✗ | ✗ | ✓ |
| Thông báo (SSE + danh sách) · Referral | ✗ | ✗ | ✓ |

- **Cơ chế thực thi**:
  - Web (chain 2): `/checkout/**`, `/cart/**`, `/products/**` là `permitAll`; `/my-orders`, `/wishlist/**`, `/profile`, `/orders/**`, `/reviews/**`, `/my-coupons` yêu cầu `authenticated`.
  - API (chain 1): `/api/cart/**`, `POST /api/orders/checkout`, `/api/coupons/validate`, `/api/coupons/available` là `permitAll`; `/api/orders/my`, `/api/wishlist/**`, `/api/profile/**`, `/api/coupons/my`, `/api/reviews/**`, `/api/notifications/**` yêu cầu JWT.
  - Mobile (GoRouter `redirect`): các tiền tố `/checkout`, `/orders`, `/profile`, `/wishlist`, `/notifications`, `/coupons` bị chặn nếu chưa đăng nhập → chuyển `/login?redirect=...`. Vì vậy **mobile không hỗ trợ guest checkout** (khác web/API).
  - Đơn của guest (web) không gắn `actor` nên **không tra cứu lại được** qua lịch sử đơn hàng.

### 1.7 Security headers
- `X-Frame-Options: DENY`
- `X-Content-Type-Options: nosniff`
- `HSTS: max-age=31536000; includeSubDomains`
- `Referrer-Policy: strict-origin-when-cross-origin` (web chain)

---

## 2. Sản phẩm

### 2.1 Cấu trúc
- `Product` → `SubCategory` → `Category` (2 cấp)
- Mỗi sản phẩm có nhiều `ProductVariant` (size + màu + giá + stock + sold)
- `minPrice` và `totalSold` denormalized trên Product để sort nhanh bằng DB

### 2.2 Danh sách & lọc
- `GET /api/products` — phân trang (max 50/page) + lọc:
  - Từ khóa (`keyword`)
  - Danh mục (`categoryId` / `subCategoryId`)
  - Khoảng giá (`minPrice` / `maxPrice`)
  - Sắp xếp: `newest` (id DESC) · `oldest` · `name_asc/desc` · `price_asc/desc`

### 2.3 Full-text search
- MySQL FULLTEXT INDEX trên cột `name` + `description` của bảng `products`
- `GET /api/products/suggest?q=<term>` — trả tối đa 8 kết quả, xếp theo relevance (MATCH AGAINST BOOLEAN MODE)
- Fallback LIKE qua JPA Specification nếu FULLTEXT không khả dụng (H2 test, MariaDB)

### 2.4 Ảnh sản phẩm (ProductImage)
- `imageUrl` — đường dẫn lưu trong `uploads/products/`
- `primaryImage` — ảnh bìa (hiển thị đầu tiên)
- `sortOrder` — thứ tự hiển thị trong gallery (tăng dần)
- Sort DB: `@OrderBy("sortOrder ASC, primaryImage DESC, id ASC")`
- Admin gallery: kéo-thả sắp xếp · ảnh đầu = bìa (nhãn COVER) · nút ✕ xóa từng ảnh

### 2.5 Variants
- Mỗi variant: `size`, `color`, `price` (BigDecimal), `stock`, `sold`, `sku` (unique), `weight`
- Tạo/sửa/xóa variant ngay trên form sản phẩm (không cần trang riêng)

### 2.6 Sản phẩm tương tự
- `GET /api/products/{id}/similar?limit=6` — `RecommendationService` truy vấn theo subcategory

---

## 3. Giỏ hàng

- Session-based (không cần đăng nhập)
- `CartService` lưu trong `HttpSession`
- `GET /api/cart` — `{ items, total, itemCount }`
- `POST /api/cart/add` — thêm (variantId, quantity)
- `PUT /api/cart/update` — sửa số lượng
- `DELETE /api/cart/{variantId}` — xóa 1 item
- `DELETE /api/cart` — xóa toàn bộ

---

## 4. Thanh toán & Đặt hàng

### 4.1 Checkout
- `POST /api/orders/checkout` — public (hỗ trợ guest + đã đăng nhập). Guest tạo đơn với `actor = null` (không gắn tài khoản, không tra cứu lại được). **App mobile** chặn `/checkout` khi chưa đăng nhập (xem [§1.6b](#16b-phân-quyền-guest-khách-vãng-lai-vs-user-có-tài-khoản)) nên trên mobile chỉ user mới checkout được.
- Kiểm tra tồn kho thực (lock variant row, throw `OutOfStockException` nếu hết)
- `FREE_SHIP_THRESHOLD = 500,000₫` → ship free; dưới ngưỡng: `SHIP_FEE = 30,000₫`
- Validate và apply coupon nếu truyền `couponCode`
- Tạo `Payment` record, gửi thông báo SSE real-time cho user + admin
- Xóa giỏ hàng sau khi đặt thành công

### 4.2 Trạng thái đơn hàng
```
PENDING → PROCESSING → SHIPPING → COMPLETED
                ↓
         CANCEL_REQUESTED → CANCELLED
    ↑
   (user tự hủy khi PENDING)
```
| Trạng thái | Ý nghĩa |
|-----------|---------|
| `PENDING` | Vừa đặt, chờ xử lý |
| `PROCESSING` | Admin đang chuẩn bị |
| `CANCEL_REQUESTED` | User yêu cầu hủy khi đang PROCESSING |
| `SHIPPING` | Đang giao |
| `COMPLETED` | Đã giao thành công |
| `CANCELLED` | Đã hủy |

- User tự hủy: `POST /api/orders/{id}/cancel` — chỉ khi `PENDING`
- User yêu cầu hủy: `POST /api/orders/{id}/cancel-request?reason=` — khi `PROCESSING`
- Admin cập nhật: `POST /api/admin/orders/{id}/status` (AJAX) hoặc trang web

### 4.3 Optimistic locking
- `@Version Long version` trên `Order` → ngăn race condition khi nhiều admin cập nhật cùng lúc

---

## 5. Đánh giá (Review)

- Chỉ đánh giá được sau khi đơn hàng `COMPLETED` (liên kết với `OrderItem`)
- UniqueConstraint trên `order_item_id` → **1 review / order item**
- Rating: 1–5 sao (double) · comment: ≤1000 ký tự
- Ảnh đính kèm: tối đa 5 URL lưu trong bảng `review_images` (@ElementCollection)
- Hiển thị trên trang chi tiết sản phẩm
- **Web**: form `POST /reviews/{orderItemId}` (multipart, có upload ảnh) → redirect
- **API (mobile/AJAX)**: `POST /api/reviews/{orderItemId}` body `{ rating, comment }` → JSON, dùng **chung** `ReviewService.createReview`. `OrderResponse.OrderItemInfo` trả kèm `id` (orderItemId) + cờ `reviewed` để client ẩn nút khi đã đánh giá.

---

## 6. Coupon

### 6.1 Loại coupon
- `PERCENTAGE` — giảm % (0–100)
- `FIXED` — giảm tiền cố định

### 6.2 Điều kiện áp dụng
- `active = true`
- `startDate ≤ now ≤ expiryDate` (nếu đặt)
- `usageCount < usageLimit` (nếu đặt)
- `orderTotal ≥ minOrderAmount` (nếu đặt)

### 6.3 User-specific coupon
- `userSpecific = true` → chỉ user có bản ghi `UserCoupon` mới xem/dùng được
- Dùng cho coupon referral reward

### 6.4 API
- `POST /api/coupons/validate` — kiểm tra (không tăng `usageCount`)
- `GET /api/coupons/available?orderTotal=` — coupon **đề xuất** cho đơn hiện tại (đã lọc usable + thỏa minOrder, xếp giảm nhiều nhất trước; phần tử đầu là "Recommended"). Public, trả rỗng nếu chưa đăng nhập. Dùng `CouponService.getAvailableCouponsForUser` — cùng nguồn với web checkout.
- `GET /api/coupons/my` — xem coupon của mình (general + user-specific)

> Cả 3 endpoint trả `discountType` (PERCENTAGE/FIXED) và `discountDisplay` (chuỗi "20%" hoặc
> "100,000₫" từ `CouponDisplayDTO.getDiscountDisplay()`) để web + mobile hiển thị %/₫ thống nhất.

---

## 7. Wishlist

- `WishlistItem` liên kết User ↔ Product
- `GET /api/wishlist` · `POST /api/wishlist/{productId}` · `DELETE /api/wishlist/{productId}`
- Yêu cầu xác thực

---

## 8. Referral System

```
User A có referralCode (16 ký tự unique, tạo khi đăng ký)
  │
  └─ User B đăng ký với ?ref=<code> → user_b.referredById = user_a.id
        │
        └─ User B đặt đơn đầu tiên → đơn COMPLETED
              │
              └─ ReferralService.processReferralReward()
                    ├─ Tạo UserCoupon cho User A
                    ├─ Tạo UserCoupon cho User B
                    └─ User A.referralRewarded = true  (chỉ thưởng 1 lần)
```

---

## 9. Notifications & SSE

### 9.1 SSE stream
- `GET /notifications/stream` — `SseService` duy trì pool `SseEmitter` per user (timeout 30 phút)
- Event type: `notification` · `new-order` · payload JSON
- Tự reconnect khi mất kết nối
- ⚠️ **Lưu ý connection pool**: vì stream sống lâu, `spring.jpa.open-in-view` phải **tắt** (`false`) — nếu bật, mỗi stream giữ một JDBC connection suốt vòng đời và làm cạn HikariCP pool. Xem [changelog 2026-06-04].

### 9.2 API
- `GET /api/notifications` — 20 thông báo gần nhất
- `GET /api/notifications/count` — số chưa đọc (cho badge)
- `POST /api/notifications/{id}/read` — đánh dấu 1 đã đọc
- `POST /api/notifications/read-all` — đánh dấu tất cả

### 9.3 Khi nào tạo thông báo
- Đặt hàng thành công → thông báo user + admin
- Admin cập nhật trạng thái → thông báo user

---

## 10. Virtual Try-On

### 10.1 Luồng người dùng
1. Upload ảnh người (max 5MB, jpg/png/webp, validate magic bytes)
2. Nhận `personId` (UUID, lưu tạm trong `uploads/tryon-persons/`)
3. Chọn sản phẩm → `POST /api/tryon/generate` (1 SP) hoặc `/generate-outfit` (áo+quần)
4. Nhận JPEG bytes → hiển thị trực tiếp (URL.createObjectURL)
5. Ảnh tạm tự xóa sau khi generate (finally block)

### 10.2 Python server (port 8081)

| Tier | Điều kiện | Xử lý |
|------|-----------|-------|
| 1 — Replicate IDM-VTON | Có `REPLICATE_API_TOKEN` + còn quota | Cloud, nhanh |
| 2 — CatVTON local | Không token / quota hết / 402/429 | GPU local, fp16, 768×1024 |

Outfit (2 SP):
- SegFormer parse 1 lần → 2 mask không chồng nhau (upper + lower body)
- Chạy CatVTON **2 lượt độc lập** trên ảnh gốc (KHÔNG chain tuần tự)
- Composite từng vùng theo mask

### 10.3 Admin quản lý garment
- `TryOnService.updateTryOnSettings(productId, enabled, garmentImage, garmentType)`
- `enabled=false` → tắt ngay, lưu DB
- `enabled=true` + ảnh mới → gọi Python `/preprocess/garment` (rembg + normalize), fallback lưu raw nếu Python offline
- `enabled=true` + không ảnh mới + đã có garment → giữ nguyên URL, chỉ update type + enabled

### 10.4 Giao diện
- **Try-On Studio** (`/tryon-studio`): sidebar 420px · tab Tops/Bottoms/All · ảnh upload max 420px · kết quả min-height 300px · lightbox zoom
- **Product Detail modal**: modal 1040px · cột trái (upload 480px) · cột phải (kết quả 520px) · lightbox zoom · persist personId trong localStorage

---

## 11. AI Chatbot

### 11.1 Kiến trúc
- `AiChatbotService` → `GeminiChatClient` → Gemini REST API v1beta
- Model: `gemini-2.5-flash` (mặc định; đổi qua `GEMINI_MODEL`)
- `thinkingBudget=0` tắt thinking để không cụt token khi function calling

### 11.2 Function calling tools
| Tool | Mô tả |
|------|-------|
| `search_products` | Tìm SP theo category/subcategory/color/keyword/minPrice/maxPrice/limit |
| `get_best_sellers` | Lấy SP bán chạy nhất |
| `get_product_details` | Chi tiết SP (name, variants, colors, sizes, stock) |

### 11.3 Vòng lặp tối đa MAX_STEPS=4
- Gemini → functionCall → executeFunction() → functionResponse → Gemini → ...
- Kết thúc khi Gemini trả text (không có thêm functionCall)

### 11.4 Lịch sử hội thoại
- Lưu trong `HttpSession` (key `chatbot_history`, max 12 turns)
- Gửi 6 turns gần nhất vào Gemini mỗi lần (appendHistory)

### 11.5 Fallback & Cooldown
- Không có API key / AI offline → trả best-sellers + text thân thiện
- 429/401/403 → `aiDisabledUntil = now + cooldownSeconds(300)` (volatile)

---

## 12. Admin

### 12.0 UI modal AJAX (create / edit / detail)
Mọi thao tác Thêm / Sửa / Xem chi tiết mở trong **modal làm mờ nền**, không nhảy trang riêng:
- Link danh sách có `data-modal` (products/orders thêm `data-modal-wide` → modal 880px).
- `admin-modal.js` fetch URL kèm header `X-Requested-With` → controller trả **fragment** `_form :: form` (hoặc `_detail :: detail`) → nhét vào `#ajaxModal`.
- Submit `data-modal-form` qua `fetch` + `FormData` (giữ upload ảnh) → controller trả JSON: `{ok:true,message}` đóng modal + toast + refresh bảng; `{ok:false,error}` hiện banner trong modal.
- Phát hiện AJAX & trả kết quả qua `AdminBaseController.isAjax() / ok() / fail()`.
- Fallback non-AJAX: redirect list `?modal=create|edit|order&id=...`, client tự mở.
- Module: categories · subcategories · coupons · users · products · orders. **Đã xóa toàn bộ trang riêng cũ.**

### 12.1 Dashboard
- KPI: tổng đơn hàng · đơn hôm nay · doanh thu hôm nay/tuần/tháng/năm · tổng users · cảnh báo stock ≤10
- Biểu đồ doanh thu: 7 ngày gần nhất · 4 tuần · 12 tháng · 5 năm
- `DashboardService`: low stock threshold = 10 units
- Xuất Excel: `ReportService` dùng Apache POI, sheet chi tiết đơn hàng

### 12.2 Quản lý sản phẩm
- Modal **Thêm** (`_create_form`) và **Sửa** (`_edit_form`) — modal rộng (`data-modal-wide`) — đều có:
  - Gallery ảnh kéo-thả (drag/drop HTML5) → thứ tự lưu vào `sort_order`
  - Ảnh đầu = bìa (`primaryImage=true`, nhãn COVER xanh)
  - Nút ✕ xóa ảnh (ảnh cũ → thêm vào `imagesToDelete`, ảnh mới → bỏ khỏi mảng)
  - Card Try-On: toggle on/off → ẩn/hiện ô upload garment
  - **1 nút Save duy nhất** lưu hết: thông tin + variants + ảnh + try-on state
  - JS đăng ký listener qua `AbortController` + `window.__adminPageCleanup` để hủy sạch khi đóng modal

### 12.3 Quản lý đơn hàng
- Danh sách toàn bộ đơn (phân trang, lọc)
- **Xem chi tiết trong modal** (`orders/_detail`): items, thông tin KH, đổi trạng thái, duyệt huỷ — tất cả submit AJAX
- Cập nhật trạng thái cũng dùng được API `POST /api/admin/orders/{id}/status`

### 12.4 Quản lý người dùng
- Danh sách · tạo/sửa **trong modal** (`users/_form`) · gán ADMIN role
- Sửa: email read-only, không có ô đổi mật khẩu (chỉ ở form tạo)

### 12.5 Quản lý coupon
- Tạo/sửa **trong modal** (`coupons/_form`) coupon PERCENTAGE hoặc FIXED
- Đặt thời hạn · ngưỡng đơn · giới hạn lượt dùng · toggle active

### 12.6 Analytics API (ADMIN only)
- `GET /api/analytics/top-products?limit=10` — top SP bán chạy
- `GET /api/analytics/trending?limit=8` — trending (best sellers)
- `GET /api/analytics/overview` — KPI tổng: orders, revenue, users, stock, sold
- `GET /api/admin/stats/summary` — snapshot nhẹ cho AJAX dashboard refresh

---

## 13. Flutter Mobile App

- **Package**: `nova_mobile`
- **State management**: Riverpod 2.6
- **Routing**: GoRouter 14
- **HTTP**: Dio 5 + JWT interceptor (auto-attach `Authorization: Bearer`)
- **Base URL**: `http://10.0.2.2:8080` (Android emulator) / `http://localhost:8080` (iOS simulator)

### Màn hình
| Feature | Màn hình |
|---------|---------|
| Auth | Login · Register · Forgot password |
| Shop | Home · Product list · Product detail · Search |
| Cart | Cart · Checkout · Checkout success |
| Orders | My orders · Order detail |
| Profile | Profile view/edit · Change password |
| Wishlist | Wishlist |
| Notifications | Notification list |
| Coupon | My Coupons (hiển thị %/₫ đúng theo `discountDisplay`) |

### Tính ngang bằng với web (parity)
- **Checkout** đề xuất coupon khả dụng giống web: gọi `GET /api/coupons/available?orderTotal=`, hiển thị danh sách thẻ chọn nhanh (thẻ đầu = "Recommended") + ô nhập mã thủ công.
- **My Coupons** hiển thị mức giảm đúng định dạng (`20%` / `100,000₫`) qua `discountDisplay`.
- **Review**: gửi qua `POST /api/reviews/{orderItemId}` (cùng `ReviewService` với web); ẩn nút khi item đã `reviewed`.
- **Giỏ hàng/đặt hàng**: cùng `CartService` (session) + `CheckoutService` (free-ship ≥500k, coupon, trừ kho có khóa) như web.

---

## 14. Email

- `EmailService` — async (`@Async`) dùng Gmail SMTP
- Trường hợp gửi: reset mật khẩu, xác nhận đơn hàng, cập nhật trạng thái
- Cấu hình: `MAIL_USERNAME` + `MAIL_PASSWORD` (Gmail App Password)

---

## 15. Cache

`CacheConfig` dùng Caffeine:
- `bestSellers` — danh sách bán chạy (evict khi tạo/xóa/cập nhật sản phẩm)
- `tryOnProducts` — sản phẩm có try-on enabled (evict khi bật/tắt try-on)

---

## 16. Upload & Storage

- `FileStorageService` lưu vào thư mục `uploads/` (cấu hình qua `upload.dir`)
- Subfolder:
  - `products/` — ảnh sản phẩm (admin upload)
  - `tryon-garments/` — ảnh garment đã preprocess
  - `tryon-persons/` — ảnh người tải lên (tạm, xóa sau generate)
  - `reviews/` — ảnh đính kèm review
- Validate magic bytes khi upload person image (jpg/png/webp · max 5MB)
- Phòng chống path traversal: normalize + check startsWith(uploadDir)

---

## 17. Monitoring & Ops

- **Swagger UI**: `/swagger-ui.html` (Springdoc OpenAPI)
- **Actuator**: `/actuator/health` (public) · `/actuator/**` (ADMIN)
- **Graceful shutdown**: `server.shutdown=graceful` · timeout 30s
- **Async**: `tryOnExecutor` thread pool cho Try-On generation
- **Logging**: request ID filter (`RequestIdFilter`) thêm `reqId` vào MDC
