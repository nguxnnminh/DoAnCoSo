# Work Log — Nhật Ký Công Việc

> Ghi lại chi tiết các thay đổi kỹ thuật, lý do, vị trí file.  
> Xem tổng quan tính năng: [features.md](features.md) · Lịch sử thay đổi: [changelog.md](changelog.md)

---

## Môi trường phát triển

| Thành phần | Chi tiết |
|------------|---------|
| JDK | 17 (Eclipse Temurin) |
| Database | MySQL 8 (local port 3306, database: `clothingstore`) |
| IDE | VS Code + Extension Pack for Java |
| Build tool | Maven (./mvnw) |
| AI Chatbot | Google Gemini 2.5 Flash (API cloud — đặt `GEMINI_API_KEY`) |
| Virtual Try-On | Python FastAPI port 8081 · CatVTON local (RTX 3050Ti 4GB) |
| Mobile | Flutter SDK 3.11+, Android emulator / thiết bị thật |

---

## Bảng 1 — Files Java đã sửa / tạo (theo thứ tự thời gian)

| # | File | Vị trí thay đổi | Mô tả thay đổi | Lý do |
|---|------|-----------------|----------------|-------|
| 1 | `entity/User.java` | Thêm fields referral | `referralCode` (unique, 16 chars), `referredById`, `referralRewarded` | Hệ thống giới thiệu bạn bè |
| 2 | `entity/ProductImage.java` | Thêm field `sortOrder` | `@Column(name="sort_order") Integer sortOrder = 0` | Lưu thứ tự gallery kéo-thả |
| 3 | `entity/Product.java` | Sửa `@OrderBy` + `getImages()` | `sortOrder ASC, primaryImage DESC, id ASC` · sort in-memory theo cùng tiêu chí | Đảm bảo thứ tự ảnh nhất quán cả DB-side lẫn in-memory |
| 4 | `entity/Review.java` | `@ElementCollection imageUrls` | Bảng `review_images(review_id, image_url)` | Cho phép đính kèm ≥1 ảnh / review |
| 5 | `entity/Coupon.java` | Thêm `userSpecific` + logic | `userSpecific = false` (default); `isValid()`, `applyDiscount()`, `calculateDiscountAmount()` | Hỗ trợ coupon chỉ dành cho user cụ thể (referral reward) |
| 6 | `dto/ProductCreateDTO.java` | Thêm `tryOnEnabled` | `Boolean tryOnEnabled = false` | Đồng bộ toggle UI với trang Thêm |
| 7 | `dto/ProductUpdateDTO.java` | Thêm `imageOrder`, `tryOnEnabled`, `garmentImage`, `garmentType` | `List<String> imageOrder`, `Boolean tryOnEnabled`, `MultipartFile garmentImage`, `String garmentType` | Gallery reorder + unified try-on save |
| 8 | `service/ProductService.java` | `saveImages()`, `applyImageOrder()`, `createProduct()` | `saveImages` thêm `baseOrder` param; `applyImageOrder` xử lý token `E{id}`/`N{k}`; bỏ xử lý garment khỏi createProduct | Hỗ trợ kéo-thả sắp xếp ảnh + sort_order; garment chuyển sang TryOnService |
| 9 | `service/TryOnService.java` | Thêm `updateTryOnSettings()`, refactor `preprocessAndEnable()` | `updateTryOnSettings(productId, enabled, garmentImage, type)` — logic 4 nhánh; `preprocessAndEnable` delegate về method mới | Unified save cho cả create & edit; không còn 2 form riêng |
| 10 | `service/AiChatbotService.java` | **Viết lại hoàn toàn** | Xóa rule-based; thêm Gemini function calling (MAX_STEPS=4); 3 tools: `search_products`, `get_best_sellers`, `get_product_details`; system prompt động từ DB; fallback best-sellers | Chuyển từ Ollama local sang Gemini cloud; AI-first, ít rule |
| 11 | `service/ai/GeminiChatClient.java` | **File mới** | REST client gọi Gemini `generateContent` API v1beta; hỗ trợ multi-turn + function declarations; `thinkingBudget=0` | Thay thế OllamaChatClient |
| 12 | `service/ai/OllamaChatClient.java` | **Xóa** | Đã gỡ hoàn toàn | Không còn dùng Ollama |
| 13 | `config/ChatbotAiProperties.java` | **Viết lại** | Xóa Ollama fields; thêm `geminiApiKey`, `geminiModel`, `geminiBaseUrl` | Config Gemini |
| 14 | `controller/admin/AdminProductController.java` | `createProduct()`, `updateProduct()` | Inject `TryOnService`; gọi `updateTryOnSettings()` sau khi save sản phẩm; `parseGarmentType()` helper | Lưu try-on state + garment chung 1 nút |
| 15 | `controller/api/ChatbotApiController.java` | Comment trong `chat()` | Sửa comment cũ nhắc Ollama → Gemini | Dọn sạch reference Ollama |
| 16 | `repository/OrderRepository.java` | `@EntityGraph` + query mới | `findByActorOrderByCreatedAtDesc` (+items); `findByIdWithItems` mới (+items +actor); `findTop5ByOrderByCreatedAtDesc` (+actor); `searchAdmin` (+actor) | OSIV tắt → fetch items/actor tường minh cho my-orders, order detail (modal), dashboard, admin orders list |
| 16b | `repository/ReviewRepository.java` | `@EntityGraph` | `findAllByItemIdOrderByCreatedAtDesc` (+imageUrls +actor) | product-detail render `review.actor.fullName` khi OSIV tắt |
| 17 | `repository/WishlistItemRepository.java` | `@EntityGraph` | `findByUser` thêm `@EntityGraph({product, product.images})` | Render thumbnail wishlist khi OSIV tắt |
| 18 | `repository/ProductRepository.java` | `findProductForEdit` | Thêm `LEFT JOIN FETCH p.images` (đã có productVariants) | Modal sửa SP cần ảnh khi OSIV tắt |
| 19 | `controller/admin/AdminBaseController.java` | Thêm helper | `isAjax(req)`, `ok(ajax,ra,msg,url)`, `fail(ajax,ra,err,url)` — trả JSON khi AJAX, redirect+flash khi không | Dùng chung cho modal AJAX 6 module |
| 20 | `controller/admin/AdminCategoryController.java` | create/edit GET + create/update POST | GET → fragment `_form :: form`; POST trả `Object` (JSON/redirect) | Modal categories |
| 21 | `controller/admin/AdminSubCategoryController.java` | tương tự | Fragment + JSON | Modal subcategories |
| 22 | `controller/admin/AdminCouponController.java` | tương tự (DTO `couponDTO`) | Fragment + JSON; bỏ flash DTO khi AJAX | Modal coupons |
| 23 | `controller/admin/AdminUserController.java` | tương tự | Fragment + JSON; password chỉ ở form tạo | Modal users |
| 24 | `controller/admin/AdminProductController.java` | create/edit GET + POST | GET → `_create_form` / `_edit_form`; POST trả JSON; `firstError(BindingResult)` gộp lỗi validation | Modal products (rộng) |
| 25 | `controller/admin/AdminOrderController.java` | detail GET + 3 POST action | GET → `_detail :: detail`; status/cancel-accept/cancel-deny trả JSON khi AJAX | Modal xem chi tiết đơn |

---

## Bảng 2 — Files Template đã sửa

| # | File | Vị trí | Mô tả | Lý do |
|---|------|--------|-------|-------|
| 1 | `admin/products/create.html` | Vùng PRODUCT IMAGES | Thay preview tĩnh bằng gallery JS kéo-thả: `DataTransfer` rebuild file input, render thumbnail draggable + nút ✕, nhãn COVER cho ảnh đầu | Sắp xếp ảnh ngay khi tạo |
| 2 | `admin/products/create.html` | Card Virtual Try-On | Đổi sang layout giống edit: toggle on/off (JS `syncTryOn()`), ẩn/hiện ô upload | Nhất quán UI giữa create và edit |
| 3 | `admin/products/edit.html` | Vùng PRODUCT IMAGES | Gộp "Existing Images" + "Add New Images" thành 1 gallery; seed từ `#existingImagesData span[data-id][data-url]`; gallery items `{type:'existing',id,url}` / `{type:'new',file,url}`; submit: sinh `imageOrder` inputs + rebuild `newImages` input | Trộn ảnh cũ/mới trong 1 gallery kéo-thả |
| 4 | `admin/products/edit.html` | Card Virtual Try-On | **Xóa** 2 form riêng (enable/disable); thêm card trong `productEditForm`: toggle `th:checked="${product.tryOnEnabled}"`, style tĩnh (không dùng `th:style` để tránh mất `position:absolute`), JS `syncTryOn()` set initial state + ẩn/hiện `tryOnFields` | Unified save + fix bug toggle vô hình |
| 5 | `shop/tryon-studio.html` | Sidebar | Rộng `340px→420px`; upload zone `min-height 320px`, icon upload, preview ảnh `max 420px`; "Change photo" link; lightbox (`openStudioLightbox`) | Ảnh to và rõ hơn |
| 6 | `shop/tryon-studio.html` | Kết quả | `min-height 300px`, hint "tap to enlarge"; Download button lớn hơn; lightbox khi click kết quả | Kết quả to và rõ hơn |
| 7 | `shop/product-detail.html` | Try-On modal | Modal rộng `780px→1040px`; upload zone `min-height 380px`, icon, prompt div; kết quả `max-height 520px`; lightbox (`openTryonLightbox`); nút "Change photo" | Ảnh to và rõ hơn + fix bấm vào zone để chọn file |
| 8 | `shop/product-detail.html` | Upload zone JS | `onclick="document.getElementById('tryonPersonInput').click()"` trên `div.upload-zone`; `event.stopPropagation()` trên ảnh để mở lightbox thay vì mở file picker | Bug: bấm vào zone không mở file picker |
| 9 | `layout/admin.html` | CSS + host modal | Thêm `.modal-box--form`/`--wide`, header/error/spinner; container `#ajaxModal`; nạp `admin-modal.js` | Hạ tầng modal AJAX dùng chung |
| 10 | `admin/categories/_form.html`, `subcategories/_form.html`, `coupons/_form.html`, `users/_form.html` | **File mới** | Fragment `[data-modal-content] th:fragment="form"` gộp create+edit theo cờ `isEdit` | Modal create/edit 4 module đơn |
| 11 | `admin/products/_create_form.html`, `_edit_form.html` | **File mới** | Fragment riêng (form lớn: gallery + variants + try-on); JS dùng `AbortController` + `__adminPageCleanup` | Modal products rộng |
| 12 | `admin/orders/_detail.html` | **File mới** | Fragment xem chi tiết đơn; form action gắn `data-modal-form` → submit AJAX | Modal xem/đổi trạng thái đơn |
| 13 | `admin/*/index.html` (6 module) | Link Add New / Edit / View | Gắn `data-modal` (+`data-modal-wide` cho products/orders) | Mở modal thay vì điều hướng |
| — | `admin/{categories,subcategories,coupons,users,products}/{create,edit}.html`, `admin/orders/show.html` | **Xóa** | Trang riêng cũ không còn dùng | Thay bằng fragment modal |

---

## Bảng 3 — Files Config / Tài nguyên đã sửa

| # | File | Thay đổi |
|---|------|---------|
| 1 | `resources/application.properties` | Xóa Ollama config; thêm `chatbot.ai.gemini-api-key/model/base-url`; model mặc định: `gemini-2.5-flash` |
| 2 | `resources/application.properties` | **(2026-06-04)** `spring.jpa.open-in-view=false`; Hikari `maximum-pool-size=20` · `minimum-idle=5` · `leak-detection-threshold=30000` |
| 3 | `static/js/admin-modal.js` | **File mới** — fetch fragment, open/close overlay, submit AJAX (FormData), toast, auto-open `?modal=`, cleanup `__adminPageCleanup` |
| 4 | `static/js/admin-spa.js` | Bỏ qua link `data-modal`; expose `window.adminSpaReload()` / `adminSpaNavigate()` |
| 5 | `README.md` | Cập nhật: admin modal, OSIV/pool, cấu trúc thư mục templates/static |
| 6 | `docs/features.md` | Thêm 12.0 (modal AJAX), cập nhật 12.2–12.5, lưu ý OSIV ở 9.1 |
| 7 | `docs/changelog.md` | Thêm 2 entry 2026-06-04 (admin modal · fix OSIV pool) |
| 8 | `docs/work-log.md` | File này |

---

## Bảng 4 — Cài đặt phần mềm & môi trường

| # | Hành động | Chi tiết | Mục đích |
|---|-----------|---------|---------|
| 1 | Cài JDK 17 (Temurin) | `C:\devtools\jdk\jdk-17.0.13+11` | Biên dịch & chạy Spring Boot |
| 2 | Cài MySQL 8 | Port 3306, database `clothingstore` | Database chính |
| 3 | Cài Flutter SDK | 3.11+ | Build mobile app |
| 4 | Gỡ Ollama | Đã uninstall hoàn toàn khỏi Windows | Không còn dùng LLM local |
| 5 | Google Gemini | API cloud (không cài local) — set `GEMINI_API_KEY` | AI Chatbot |
| 6 | Sửa git credential | Đăng nhập đúng tài khoản GitHub | Push code |

---

## Bảng 5 — Bugs đã sửa

| # | Bug | Triệu chứng | Root cause | Fix |
|---|-----|-------------|-----------|-----|
| 1 | Nested form HTML | Try-On trên edit page không submit được | Form Try-On lồng trong form chính → HTML invalid | Tách thành form riêng với action `/tryon/enable` |
| 2 | Nested form (giải quyết triệt để) | Unified save mới: Try-On form riêng vẫn phức tạp | Hai form riêng gây trải nghiệm kém | Gộp Try-On vào form chính (multipart/form-data), xử lý trong controller sau khi save product |
| 3 | Try-On toggle vô hình (edit) | Nút on/off không nhìn thấy | `th:style` ghi đè toàn bộ `style=""` bao gồm `position:absolute` → span co lại 0px | Bỏ `th:style`, dùng style tĩnh đầy đủ, JS `syncTryOn()` set trạng thái ban đầu |
| 4 | Upload zone không mở file picker | Bấm vào khung không mở chọn file | `<div>` không có hành vi click mặc định như `<label for>` | Thêm `onclick="...input.click()"` trên div; `stopPropagation()` trên ảnh (tránh conflict lightbox) |
| 5 | `productImages` thứ tự sai sau khi kéo-thả | Ảnh không đúng thứ tự sau save | Không có `sort_order` column, chỉ dùng `primaryImage DESC, id ASC` | Thêm cột `sort_order`, gán tuần tự khi submit; DB `@OrderBy` + in-memory sort đồng bộ |
| 6 | Gemini 404 | Chatbot lỗi `models/gemini-1.5-flash is not found` | Key không có model `gemini-1.5-flash` | Gọi `ListModels` API xác nhận model available → đổi sang `gemini-2.5-flash` |
| 7 | Gemini 429 quota | Chatbot lỗi `limit: 0` | Project của key không có free tier quota | Hướng dẫn tạo key mới qua "Create API key in new project" trong AI Studio |
| 8 | API key lộ trong git | Key hardcode trong `application.properties` | Bạn đặt giá trị mặc định có key thật vào property file | Gỡ key khỏi file, chỉ dùng qua biến môi trường `GEMINI_API_KEY` |
| 9 | `MultipleBagFetchException` | Hibernate lỗi khi fetch product | Hai `List` (productVariants + images) JOIN-fetch cùng lúc | Đổi `images` từ `List` sang `Set` — một `List` + một `Set` là an toàn |
| 10 | Gemini token bị cụt | Response bị cắt giữa chừng khi function calling | `gemini-2.5-flash` bật thinking mặc định, thinking tokens cạnh tranh với `maxOutputTokens` | `"thinkingConfig": {"thinkingBudget": 0}` trong generationConfig |

---

## Bảng 6 — Quyết định kỹ thuật quan trọng

| Quyết định | Lý do |
|-----------|-------|
| Gemini 2.5 Flash thay Ollama | Không cần cài LLM local, free tier đủ dùng, tiếng Việt tốt, hỗ trợ function calling |
| Function calling thay rule-based | AI tự quyết định khi nào query DB → chính xác hơn, ít code cứng, mở rộng dễ |
| `sort_order` column thay đổi thứ tự ngầm định | DB `@OrderBy` không đủ — cần giá trị explicit để persist thứ tự người dùng chọn |
| Session-based cart | Hoạt động cho cả guest + user đã đăng nhập, không cần bảng cart trong DB |
| Optimistic locking `@Version` trên Order | Ngăn race condition khi nhiều admin cập nhật cùng đơn |
| `Set<ProductImage>` thay `List` | Tránh `MultipleBagFetchException` khi productVariants đã là `List` |
| Tách Python server riêng (port 8081) | Inference GPU cần Python/PyTorch, không đưa vào JVM được |
| `ddl-auto=update` | Tiện cho dev/prototype — cột mới như `sort_order` tự tạo khi khởi động |
| `thinkingBudget=0` cho Gemini 2.5 Flash | Chatbot không cần "thinking"; tắt để dành toàn bộ `maxOutputTokens` cho answer |
| UserSpecific coupon | Cho phép tạo coupon chỉ hiện với user cụ thể (referral reward) mà không cần bảng riêng |
| Magic bytes validate cho upload | Không tin vào MIME type hay đuôi file — đọc header bytes thực để kiểm tra |
