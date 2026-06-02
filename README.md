# ClothingStore — Web & Mobile Bán Quần Áo Thông Minh

> Đồ án liên ngành 2026 · K17 · Nhóm N01  
> Spring Boot · Thymeleaf · MySQL · Flutter · AI Chatbot · Virtual Try-On

---

## Giới thiệu

**ClothingStore** là nền tảng thương mại điện tử bán quần áo đầy đủ tính năng, tích hợp trí tuệ nhân tạo và ứng dụng mobile. Điểm nổi bật:

- **Virtual Try-On** — Thử đồ ảo bằng AI (Replicate IDM-VTON cloud → fallback CatVTON local trên GPU), thử cả bộ (áo + quần) trên ảnh cá nhân
- **AI Chatbot** — Google Gemini 2.5 Flash + function calling, tự truy vấn DB sản phẩm thật, tư vấn tiếng Việt
- **Flutter Mobile App** — Ứng dụng iOS/Android đầy đủ tính năng (Riverpod + Dio + JWT)
- **Real-time Notifications** — SSE (Server-Sent Events) cho thông báo đơn hàng tức thì
- **Full-text Search** — MySQL FULLTEXT INDEX + autocomplete gợi ý tìm kiếm
- **Referral System** — Chương trình mã giới thiệu, cả 2 bên nhận coupon
- **Admin Dashboard** — Phân tích doanh thu, biểu đồ theo thời gian, xuất Excel

---

## Tech Stack

| Lớp | Công nghệ |
|-----|-----------|
| Backend | Java 17 · Spring Boot 3.5 · Spring Security · Spring Data JPA |
| Frontend (Web) | Thymeleaf · Tailwind CSS 3 · Vanilla JS |
| Mobile | Flutter · Dart 3.11+ · Riverpod 2.6 · GoRouter 14 · Dio 5 |
| Database | MySQL 8 · Spring Session JDBC · Caffeine Cache |
| Auth | BCrypt · Session (Web) · JWT HS256 (API/Mobile) |
| AI Chatbot | Google Gemini 2.5 Flash · function calling · truy vấn DB thật |
| Virtual Try-On | Python FastAPI · Replicate IDM-VTON (cloud) → CatVTON local (GPU) · SegFormer |
| Real-time | Server-Sent Events (SSE) |
| Email | Gmail SMTP (async) |
| Export | Apache POI (Excel .xlsx) |
| API Docs | Springdoc OpenAPI / Swagger UI |
| Monitoring | Spring Boot Actuator |

---

## Tính năng

### Khách hàng (Web & Mobile)

| Tính năng | Mô tả |
|-----------|-------|
| Trang chủ | Hero banner slider 3 slide · best sellers theo từng danh mục |
| Danh sách sản phẩm | Lọc theo giá · danh mục · màu sắc · tìm kiếm · sắp xếp · phân trang |
| Full-text Search | MySQL FULLTEXT INDEX · autocomplete gợi ý realtime |
| Chi tiết sản phẩm | Chọn size/màu · giá theo variant · tồn kho · sản phẩm liên quan |
| Đánh giá sản phẩm | Rating 1–5 sao · nội dung · đính kèm tối đa 5 ảnh/review |
| Giỏ hàng | Session-based · hoạt động khi chưa đăng nhập |
| Thanh toán | COD · free ship ≥ 500k · mã giảm giá · ghi chú |
| Đơn hàng | Xem lịch sử · chi tiết từng đơn · yêu cầu hủy |
| Wishlist | Thêm/xóa · xem danh sách yêu thích |
| Coupon | Xem mã giảm giá · kiểm tra mã trước khi đặt hàng |
| Hồ sơ | Cập nhật thông tin · đổi mật khẩu · địa chỉ giao hàng |
| Thông báo | SSE real-time (xác nhận đơn hàng · cập nhật giao hàng) |
| **Referral** | Chia sẻ mã giới thiệu · cả 2 bên nhận coupon khi đơn đầu hoàn tất |
| **Virtual Try-On** | Upload ảnh · thử 1 sản phẩm · thử full outfit (áo + quần) · lightbox phóng to ảnh kết quả |
| **AI Chatbot** | Gemini function calling · tư vấn tiếng Việt · tìm SP theo loại/màu/giá · FAQ chính sách |
| Trang tĩnh | Liên hệ · Đổi trả · Bảng size |

### Xác thực

- Đăng ký · Đăng nhập · Đăng xuất
- Quên mật khẩu (gửi link reset qua email)
- Login rate limiting — chống brute-force (tối đa 5 lần/IP/15 phút)
- Phân quyền: `USER` / `ADMIN`

### Admin

| Module | Tính năng |
|--------|-----------|
| Dashboard | KPIs · biểu đồ doanh thu ngày/tuần/tháng/năm · cảnh báo tồn kho thấp · xuất Excel |
| Sản phẩm | CRUD · gallery ảnh kéo-thả sắp xếp · nút ✕ xóa từng ảnh · ảnh đầu = bìa · variants (size/màu/giá/stock) |
| Try-On Admin | Toggle bật/tắt per sản phẩm · upload garment · chọn loại · lưu chung 1 nút Save |
| Danh mục | CRUD Category + SubCategory |
| Đơn hàng | Xem tất cả · cập nhật trạng thái · duyệt yêu cầu hủy |
| Người dùng | Danh sách · khóa/mở tài khoản · gán quyền ADMIN |
| Coupon | Tạo mã % giảm hoặc tiền cố định · thời hạn · ngưỡng đơn · giới hạn số lần dùng |
| Thông báo | Gửi thông báo real-time đến người dùng qua SSE |

### REST API

```
# Auth
POST   /api/auth/register              Đăng ký (hỗ trợ referral code)
POST   /api/auth/login                 Đăng nhập → JWT
POST   /api/auth/forgot-password       Gửi link reset mật khẩu
POST   /api/auth/reset-password        Đặt lại mật khẩu

# Sản phẩm
GET    /api/products                   Danh sách (phân trang, lọc, sắp xếp)
GET    /api/products/{id}              Chi tiết sản phẩm
GET    /api/products/suggest?q=        Autocomplete full-text search
GET    /api/products/{id}/similar      Sản phẩm tương tự

# Giỏ hàng & Đặt hàng
GET    /api/cart                       Xem giỏ hàng
POST   /api/cart/add                   Thêm vào giỏ
POST   /api/orders/checkout            Đặt hàng
GET    /api/orders/my                  Đơn hàng của tôi
POST   /api/orders/{id}/cancel         Hủy đơn
POST   /api/orders/{id}/cancel-request Yêu cầu hủy đơn

# Wishlist & Coupon
GET    /api/wishlist                   Danh sách yêu thích
POST   /api/wishlist/{id}              Thêm sản phẩm vào wishlist
DELETE /api/wishlist/{id}              Xóa khỏi wishlist
GET    /api/coupons                    Xem mã giảm giá
GET    /api/coupons/my                 Coupon của tôi
POST   /api/coupons/validate           Kiểm tra mã giảm giá

# Profile
GET    /api/profile                    Thông tin cá nhân
PUT    /api/profile                    Cập nhật hồ sơ (gồm địa chỉ)
POST   /api/profile/change-password    Đổi mật khẩu

# Danh mục
GET    /api/categories                 Tất cả danh mục (có subcategory lồng nhau)
GET    /api/subcategories              Tất cả subcategory

# AI & Try-On
POST   /api/chatbot                    Chat với AI chatbot (Gemini function calling)
POST   /api/tryon/upload-person        Upload ảnh người dùng
POST   /api/tryon/generate             Thử 1 sản phẩm
POST   /api/tryon/generate-outfit      Thử full outfit (áo + quần)
GET    /api/tryon/health               Kiểm tra Python server

# Thông báo (SSE)
GET    /notifications/stream           SSE stream (đăng nhập)
GET    /api/notifications              Lịch sử thông báo

# Admin
GET    /api/analytics/**               Thống kê doanh thu (ADMIN)
GET    /api/admin/orders               Tất cả đơn hàng (ADMIN)
POST   /api/admin/orders/{id}/status   Cập nhật trạng thái đơn (ADMIN)
GET    /api/admin/users                Quản lý người dùng (ADMIN)
POST   /api/admin/dashboard/export-excel  Xuất báo cáo Excel (ADMIN)
```

Swagger UI: `http://localhost:8080/swagger-ui.html`

---

## Cài đặt & Chạy

### Yêu cầu hệ thống

| Thành phần | Phiên bản | Bắt buộc |
|------------|-----------|----------|
| Java (Temurin JDK) | 17+ | ✅ Bắt buộc |
| MySQL | 8.x | ✅ Bắt buộc |
| Python | 3.10+ | Chỉ nếu dùng Virtual Try-On |
| GPU NVIDIA (CUDA) | ≥ 4GB VRAM | Chỉ cho CatVTON local; bỏ qua nếu dùng Replicate cloud |
| Flutter SDK | 3.11+ | Chỉ nếu build mobile app |
| Gemini API key | — | Tùy chọn; free tại [Google AI Studio](https://aistudio.google.com/apikey) |

---

### 1. Clone & tạo database

```bash
git clone <repo-url>
cd clothingstore
```

```sql
CREATE DATABASE clothingstore CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

---

### 2. Cấu hình `application.properties`

Mở `src/main/resources/application.properties`, cập nhật thông tin kết nối MySQL:

```properties
spring.datasource.url=jdbc:mysql://localhost:3306/clothingstore
spring.datasource.username=root
spring.datasource.password=
```

---

### 3. Chạy Spring Boot Backend

> **Bắt buộc:** đặt biến môi trường `DEV_ADMIN_PASSWORD` trước khi chạy.  
> `DataInitializer` yêu cầu nó để tạo tài khoản admin — thiếu là app dừng khi khởi động.

```bash
# Windows (PowerShell)
$env:DEV_ADMIN_PASSWORD = "YourAdminPass123!"
./mvnw spring-boot:run

# macOS/Linux
export DEV_ADMIN_PASSWORD=YourAdminPass123!
./mvnw spring-boot:run
```

Server khởi động tại: **http://localhost:8080**

> Lần đầu chạy, `DataInitializer` tự động tạo dữ liệu mẫu (danh mục, sản phẩm, tài khoản).

**Tài khoản mặc định:**

| Role | Email | Mật khẩu |
|------|-------|----------|
| Admin | admin@test.com | `DEV_ADMIN_PASSWORD` (bạn tự đặt) |
| User | user@test.com | `User@Dev2024!` |

> Ngoài ra còn 4 user demo: `lan@test.com` · `minh@test.com` · `huong@test.com` · `duc@test.com` (cùng mật khẩu user).

---

### 4. (Tùy chọn) Bật AI Chatbot với Google Gemini

Chatbot là **AI-first**: dùng Google Gemini 2.5 Flash với **function calling** để tự truy vấn database sản phẩm thật (tìm theo loại/màu/giá, xem bán chạy, chi tiết size/tồn kho) rồi tổng hợp tư vấn.

Nếu **chưa cấu hình API key**, chatbot vẫn trả lời (fallback gợi ý sản phẩm bán chạy) — **không bao giờ lỗi hay im lặng**.

```bash
# Lấy API key free tại: https://aistudio.google.com/apikey
# → chọn "Create API key in new project" để được free tier đầy đủ

# Windows (PowerShell)
$env:GEMINI_API_KEY = "AIza...your-key..."

# macOS/Linux
export GEMINI_API_KEY="AIza...your-key..."
```

> **Lưu ý:** Không hardcode key vào `application.properties` (tránh lộ khi push git).  
> Muốn đổi model: set `GEMINI_MODEL=gemini-2.5-flash` (hoặc tên khác từ `ListModels`).

---

### 5. (Tùy chọn) Chạy Flutter Mobile App

```bash
cd mobile-app

# Cài dependencies
flutter pub get

# Chạy trên emulator hoặc thiết bị thật
flutter run
```

> Mặc định app kết nối tới `http://10.0.2.2:8080` (Android emulator) hoặc `http://localhost:8080` (iOS simulator).  
> Sửa base URL trong `lib/core/network/api_client.dart` nếu cần.

---

### 6. (Tùy chọn) Chạy Virtual Try-On Server

```bash
cd python-tryon-server

# Cài dependencies (xem chú thích trong requirements.txt về torch CUDA build)
pip install -r requirements.txt

# Khởi động server tại cổng 8081
python main.py
```

Server có **2 tầng inference, tự động fallback**:

1. **Replicate IDM-VTON (cloud)** — nhanh, dùng khi đặt `REPLICATE_API_TOKEN` trong `.env`.  
   Khi hết quota (402/429) tự chuyển sang local.
2. **CatVTON local (GPU)** — chạy khi không có token hoặc cloud hết quota.  
   Cần 2 thứ (đều **không** nằm trong git do quá nặng):

   ```bash
   # a) Source CatVTON (~38MB)
   git clone https://github.com/Zheng-Chong/CatVTON catvton_src

   # b) Weights (~4GB: SD-inpainting + CatVTON attention + VAE)
   python download_models.py
   ```

   SegFormer human-parsing (`mattmdjaga/segformer_b2_clothes`, ~110MB) tự tải lần đầu chạy.

> Tinh chỉnh qua biến môi trường: `TRYON_STEPS` (mặc định 20), `TRYON_SCHEDULER` (`unipc`/`dpm`/`ddim`), `TRYON_PARSER_DEVICE` (`cpu`/`cuda`), `TRYON_CFG`.  
> Trên RTX 3050Ti 4GB: cả bộ (áo + quần) ~1.3–1.7 phút.

> **Mock mode**: thêm `tryon.mock=true` vào `application.properties` để dùng ảnh giả cho phát triển.

---

## Cấu trúc thư mục

```
clothingstore/
├── src/main/java/com/shop/clothingstore/
│   ├── config/          SecurityConfig · CacheConfig · AsyncConfig · DataInitializer
│   ├── controller/
│   │   ├── (web)        Auth · Cart · Checkout · Order · Profile · Shop · TryOn · Wishlist
│   │   ├── admin/       Dashboard · Product · Category · Order · User · Coupon · TryOn
│   │   └── api/         REST endpoints (JWT-based)
│   ├── dto/             Form DTOs · API request/response
│   ├── entity/          JPA entities (19 bảng + enums)
│   ├── repository/      Spring Data JPA (19 repos)
│   ├── security/        JWT · LoginRateLimit · RequestIdFilter
│   └── service/
│       ├── AiChatbotService.java    Gemini function calling · tool execution
│       ├── TryOnService.java        Preprocess garment · outfit compositing
│       ├── ProductService.java      CRUD · image ordering · search
│       └── ai/
│           └── GeminiChatClient.java  REST client cho Gemini generateContent API
│
├── src/main/resources/
│   ├── application.properties
│   ├── templates/
│   │   ├── layout/      base.html · admin.html (master layouts + chatbot widget)
│   │   ├── auth/        login · register · forgot/reset password
│   │   ├── admin/       dashboard · products (create/edit) · categories · orders · users · coupons
│   │   └── shop/        home · products · detail · cart · checkout · orders · profile
│   │                    wishlist · coupons · tryon-studio · contact · sizing · returns
│   └── static/          tailwind.css · JS · images
│
├── mobile-app/                Flutter app "nova_mobile" (iOS/Android)
│   ├── lib/
│   │   ├── main.dart    App entry
│   │   ├── core/        network/api_client.dart (Dio + JWT) · theme · config
│   │   ├── features/    auth · home · shop · product · cart · checkout
│   │   │                orders · wishlist · search · profile · notifications
│   │   ├── models/      Data models
│   │   ├── router/      GoRouter
│   │   └── shared/      Widgets dùng chung
│   └── pubspec.yaml
│
├── python-tryon-server/       FastAPI bridge · Replicate IDM-VTON → CatVTON local
│   ├── main.py                Server chính (dispatch · SegFormer mask · composite outfit)
│   ├── download_models.py     Tải weights CatVTON local (~4GB)
│   ├── catvton_src/           Source CatVTON (pipeline · cloth_masker)
│   ├── weights/               SD-inpainting · CatVTON · VAE (sau khi download)
│   └── requirements.txt
│
├── docs/
│   ├── features.md      Danh sách tính năng đầy đủ
│   ├── changelog.md     Lịch sử thay đổi (BEFORE/AFTER)
│   └── work-log.md      Nhật ký công việc chi tiết
│
├── uploads/                   Ảnh sản phẩm & review do người dùng upload (ngoài git)
├── pom.xml                    Maven build
└── package.json               Frontend tooling (Tailwind, PostCSS)
```

---

## Database Schema

```
User ──── Role
 │
 ├── Order ──── OrderItem ──── ProductVariant ──── Product ──── ProductImage (sort_order)
 │      │                                              │
 │      └── Coupon ◄── UserCoupon ◄── User             ├── SubCategory ──── Category
 │                                                     ├── GarmentType (try-on)
 ├── WishlistItem ──── Product                         └── garmentProcessedUrl
 ├── Review ──── Product  (có ảnh đính kèm)
 ├── Notification
 └── PasswordResetToken
```

**19 entity (@Entity) + enums** (Role · GarmentType · OrderStatus · SizeType)  
`spring.jpa.hibernate.ddl-auto=update` — schema tự tạo/cập nhật khi khởi động (kể cả cột mới như `sort_order`).

---

## Virtual Try-On — Kiến trúc

```
User Upload ──► Java API ──► Python FastAPI (port 8081)
                                    │
                          run_tryon / tryon_outfit
                                    │
                        ┌───────────┴───────────┐
                        ▼ (còn quota)            ▼ (hết quota / không token)
              Replicate IDM-VTON           CatVTON local (GPU, fp16, 768×1024)
                 (cloud, cuuupid)          │
                                           ├─ SegFormer parse → agnostic mask
                                           │  (bảo vệ mặt/tóc/tay/đồ còn lại)
                                           └─ UniPC @ 20 steps
                        └───────────┬───────────┘
                                    ▼
                              Result JPEG
```

- **Outfit (áo + quần)**: parse 1 lần → 2 mask không chồng nhau → chạy CatVTON **2 lượt trên ảnh gốc** → composite từng vùng (KHÔNG chain tuần tự).
- **Garment types** (enum `GarmentType`): `UPPER_BODY` · `LOWER_BODY`
- **Mock mode**: trả ảnh giả ngay, không cần Python server.

### Giao diện Try-On (người dùng)

- **Try-On Studio** (`/tryon-studio`): sidebar 420px · gallery sản phẩm kéo-thả theo tab (Tops / Bottoms / All) · upload ảnh người dùng hiện to (max 420px) · ảnh kết quả rõ (min-height 300px) · bấm ảnh để phóng to lightbox toàn màn hình.
- **Product Detail modal**: modal rộng 1040px · 2 cột (upload trái · kết quả phải) · ảnh người dùng max 480px · ảnh kết quả max 520px · lightbox zoom khi bấm.

---

## AI Chatbot — Cách hoạt động

```
User: "Tìm áo hoodie dưới 300k màu đen"
         │
         ▼
  AiChatbotService
  ├─ System prompt: chính sách NOVA + danh mục (đọc động từ DB)
  ├─ Gemini 2.5 Flash quyết định gọi tool:
  │    ├─ search_products(subcategory, color, maxPrice, limit)
  │    ├─ get_best_sellers(limit)
  │    └─ get_product_details(name)
  ├─ Tool truy vấn ProductService / ProductRepository → dữ liệu THẬT
  └─ Gemini tổng hợp lời tư vấn tự nhiên (hệ thống hiển thị thẻ SP kèm theo)
         │
         ▼
  ChatbotResponse { message, products[] }
```

**Đặc điểm:**
- Gần như không có rule-based — AI tự quyết định khi nào cần query DB
- System prompt nhồi toàn bộ chính sách (ship · đổi trả · size · thanh toán) + danh mục động từ DB
- Lịch sử hội thoại: tối đa 12 lượt (session-based), hiểu câu hỏi nối tiếp ("còn màu khác không?")
- Fallback: khi chưa có API key hoặc AI lỗi → trả danh sách bán chạy, không bao giờ im lặng
- Cooldown tự động 5 phút khi gặp 429 / 401 tránh spam API

---

## Admin — Quản lý sản phẩm

### Gallery ảnh kéo-thả (mới)

Trang **Thêm** và **Sửa** sản phẩm đều có cùng gallery:

- **Kéo-thả** để sắp xếp lại thứ tự ảnh
- **Ảnh đầu tiên** tự động trở thành ảnh bìa (nhãn **COVER** màu xanh)
- **Nút ✕** trên mỗi ảnh để xóa ngay lập tức
- Trang **Sửa**: trộn chung ảnh cũ + ảnh mới vào 1 gallery duy nhất, thứ tự cuối cùng được lưu vào cột `sort_order`

### Try-On (unified save)

- Card "Virtual Try-On" nằm ngay trong form chính (không còn form riêng)
- **Toggle on/off**: bật → hiện ô upload garment + chọn loại; tắt → ẩn gọn
- Hiển thị ảnh garment hiện tại nếu đã có; ô upload chỉ cần điền khi muốn thay
- **1 nút Save duy nhất** lưu tất cả: thông tin sản phẩm + variants + ảnh (theo thứ tự gallery) + trạng thái/ảnh try-on

---

## Hệ thống Referral

```
User A chia sẻ mã giới thiệu ──► User B đăng ký với mã
                                          │
                                  User B đặt đơn đầu tiên
                                  & đơn hoàn tất (DELIVERED)
                                          │
                          ┌───────────────┴───────────────┐
                          ▼                               ▼
                  User A nhận coupon              User B nhận coupon
                  (cảm ơn đã giới thiệu)          (chào mừng thành viên mới)
```

---

## Real-time Notifications (SSE)

Server-Sent Events — hoạt động trên mọi browser, không cần WebSocket:

- **Khách hàng**: xác nhận đơn hàng · cập nhật giao hàng · đơn đã giao
- **Admin**: đơn mới vào · yêu cầu hủy · thông báo hệ thống
- Tự động reconnect khi mất kết nối

---

## Security

- **Web chain**: Form login + Spring Session JDBC · CSRF bật
- **API chain**: JWT HS256 · Stateless · CSRF tắt
- **Headers**: `X-Frame-Options: DENY` · `HSTS 1 năm` · `X-Content-Type-Options: nosniff` · `Content-Security-Policy`
- **Upload**: Validate magic bytes (không tin vào đuôi file) · chống path traversal
- **Rate limit**: Tối đa 5 lần login sai/IP/15 phút
- **Password**: BCrypt (10 rounds)
- **Actuator**: `/actuator/health` public · `/actuator/**` chỉ ADMIN

---

## Các trang chính

| URL | Mô tả |
|-----|-------|
| `/` | Trang chủ (hero slider + best sellers) |
| `/products` | Tất cả sản phẩm |
| `/products/{category}` | Sản phẩm theo danh mục |
| `/product/{slug}` | Chi tiết sản phẩm (+ try-on modal) |
| `/cart` | Giỏ hàng |
| `/checkout` | Thanh toán |
| `/checkout/success` | Xác nhận đặt hàng |
| `/my-orders` | Đơn hàng của tôi |
| `/wishlist` | Yêu thích |
| `/my-coupons` | Coupon của tôi |
| `/tryon-studio` | Thử đồ ảo (studio đầy đủ) |
| `/profile` | Hồ sơ cá nhân & địa chỉ |
| `/contact` | Liên hệ |
| `/returns` | Chính sách đổi trả |
| `/sizing` | Bảng kích cỡ |
| `/admin` | Trang quản trị |
| `/swagger-ui.html` | API documentation |

---

## Biến môi trường

### Backend

| Biến | Mặc định | Mô tả |
|------|---------|-------|
| `DEV_ADMIN_PASSWORD` | *(bắt buộc)* | Mật khẩu admin seed; thiếu là app dừng khi khởi động |
| `DEV_USER_PASSWORD` | `User@Dev2024!` | Mật khẩu các user demo |
| `MAIL_USERNAME` | nguyennhatminh1811@gmail.com | Gmail gửi email |
| `MAIL_PASSWORD` | *(app password)* | Gmail App Password |
| `JWT_SECRET` | *(default local key)* | JWT signing key (≥ 32 ký tự, thay cho production) |
| `APP_PUBLIC_BASE_URL` | http://localhost:8080 | Base URL public (dùng trong link email) |
| `CHATBOT_AI_ENABLED` | true | Bật/tắt AI chatbot |
| `GEMINI_API_KEY` | *(trống)* | Google Gemini API key — **không hardcode vào file** |
| `GEMINI_MODEL` | gemini-2.5-flash | Model Gemini (kiểm tra model khả dụng qua ListModels API) |
| `TRYON_PYTHON_URL` | http://localhost:8081 | Python Try-On server |
| `MOCK_INFERENCE` | false | Mock Try-On (trả ảnh giả, không cần GPU/Python) |

### Python Try-On Server (`.env` trong `python-tryon-server/`)

| Biến | Mặc định | Mô tả |
|------|---------|-------|
| `REPLICATE_API_TOKEN` | *(trống)* | Token Replicate; có token → ưu tiên IDM-VTON cloud |
| `HF_TOKEN` | *(trống)* | HuggingFace token (tùy chọn) |
| `TRYON_STEPS` | 20 | Số bước diffusion (16–25) |
| `TRYON_SCHEDULER` | unipc | `unipc` / `dpm` / `ddim` |
| `TRYON_CFG` | 2.5 | Guidance scale |
| `TRYON_PARSER_DEVICE` | cpu | Thiết bị chạy SegFormer (`cpu` / `cuda`) |
| `TRYON_PARSER_MODEL` | mattmdjaga/segformer_b2_clothes | Model human-parsing |

---

## Build & Deploy

```bash
# Chạy tests
./mvnw test

# Build JAR
./mvnw clean package -DskipTests

# Chạy JAR (với env)
GEMINI_API_KEY=... DEV_ADMIN_PASSWORD=... java -jar target/clothingstore-*.jar

# Build Flutter (Android APK)
cd mobile-app && flutter build apk --release

# Build Flutter (iOS)
cd mobile-app && flutter build ios --release
```

---

## Roadmap

- [x] Real-time notification (SSE)
- [x] Full-text search (MySQL FULLTEXT) + autocomplete
- [x] Hero banner slider
- [x] Review có ảnh đính kèm
- [x] Hệ thống mã giới thiệu (referral)
- [x] Flutter mobile app (Riverpod + JWT)
- [x] Admin image gallery — kéo-thả sắp xếp + ✕ xóa + ảnh bìa
- [x] Admin Try-On — toggle on/off + 1 nút Save cho cả trang
- [x] AI Chatbot — Gemini 2.5 Flash + function calling + truy vấn DB thật
- [x] Try-On UI — ảnh to hơn + lightbox phóng to
- [ ] Thanh toán online (VietQR / SePay)
- [ ] Push notifications (Firebase FCM)

---

## Nhóm phát triển

**Đồ án liên ngành 2026 — K17 — Nhóm N01**

---

*Tính năng chi tiết: [docs/features.md](docs/features.md)*  
*Lịch sử thay đổi: [docs/changelog.md](docs/changelog.md)*  
*Nhật ký công việc: [docs/work-log.md](docs/work-log.md)*
