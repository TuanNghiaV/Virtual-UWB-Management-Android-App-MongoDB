# Virtual UWB Management Android App MongoDB

**VirtualUWB** là ứng dụng Android hiện đại dùng để giả lập và giám sát hệ thống định vị trong nhà (Indoor Positioning System - IPS) sử dụng công nghệ Ultra-Wideband (UWB). Ứng dụng hỗ trợ trực quan hóa chuyển động của thẻ (tag), giám sát các vùng địa lý (geofences) và chẩn đoán thông minh bằng trí tuệ nhân tạo (AI Assistant).

Dự án hiện đang sử dụng **Supabase (PostgreSQL + PostGIS, Realtime và Edge Functions)** làm hạ tầng dữ liệu chính thức, đồng thời định hướng tích hợp/di chuyển sang **MongoDB** trong roadmap phát triển tiếp theo để tối ưu hóa lưu trữ nhật ký thiết bị.

---

## 🚀 Tính năng nổi bật

- **Google Maps & GPS Integration**: Trực quan hóa vị trí thực tế của Anchors, Tags, và vị trí điện thoại ngay trên Google Bản đồ, hỗ trợ chỉ đường qua Google Routes API.
- **UWB Simulation Engine**: Bộ giả lập chuyển động ngẫu nhiên (Random Walk) tích hợp giúp mô phỏng di chuyển của Tag thời gian thực mà không cần thiết bị cứng.
- **Tìm kiếm Tag (Find My Tag)**: Giao diện la bàn hướng dẫn định vị trực tiếp từ điện thoại tới Tag được chọn.
- **Cảnh báo Geofencing**: Tự động phát hiện và cảnh báo tức thời khi các Tag đi vào hoặc ra khỏi khu vực cấm (Restricted Zone) hoặc khu vực an toàn (Safe Zone).
- **Trợ lý AI thông minh**: Tích hợp mô hình Google Gemini AI (thông qua Supabase Edge Functions) để phân tích trạng thái an toàn hệ thống thông qua câu hỏi ngôn ngữ tự nhiên.
- **Canvas Map Fallback**: Hỗ trợ bản đồ Canvas truyền thống làm phương án dự phòng khi không dùng Google Maps.

---

## 🛠 Công nghệ sử dụng

- **Ngôn ngữ**: Kotlin
- **UI Framework**: Jetpack Compose (Material 3)
- **Kiến trúc**: MVVM + Repository Pattern
- **Bản đồ**: Google Maps Compose SDK
- **Backend & Realtime**: Supabase (Postgres, Realtime, Edge Functions)
- **AI Engine**: Google Gemini API
- **Mạng**: Ktor Client & Kotlinx Serialization

---

## 🏗 Tổng quan kiến trúc Android

Dự án được cấu trúc rõ ràng theo các tầng nghiệp vụ:
- `presentation`: Quản lý các ViewModels và giao diện Jetpack Compose (Map, Devices, Find, Events, Assistant, Debug/System).
- `domain`: Định nghĩa các Models và Repository interfaces dùng chung.
- `data`: Thực thi việc kết nối, lưu trữ dữ liệu từ Supabase API và bộ nhớ đệm cục bộ (Fake Repositories).
- `simulation`: Engine tính toán giả lập di chuyển thẻ.
- `utils`: Thư viện tính toán hình học (khoảng cách Haversine, kiểm tra điểm nằm trong đa giác).

---

## 🔒 Bảo mật và Bảo vệ Secrets

Tất cả các API Key nhạy cảm (Google Maps, Google Routes, Gemini API) và thông tin kết nối Supabase **không bao giờ được commit** lên git. Chúng được quản lý qua:
1. `local.properties` (Android app) - đã được thêm vào `.gitignore`.
2. Supabase Secrets / Environment Variables (Edge Functions).

---

## ⚙️ Hướng dẫn cài đặt

### 1. Thiết lập Ứng dụng Android
1. Nhân bản dự án về máy của bạn.
2. Sao chép tệp `local.properties.example` thành `local.properties` ở thư mục gốc của dự án.
3. Điền đầy đủ thông tin dự án Supabase và Google Maps API Key của bạn:
   ```properties
   supabase.url=YOUR_SUPABASE_URL
   supabase.publishable.key=YOUR_SUPABASE_PUBLISHABLE_KEY
   supabase.anon.key=YOUR_SUPABASE_ANON_KEY
   MAPS_API_KEY=YOUR_GOOGLE_MAPS_ANDROID_KEY
   ```
4. Đồng bộ hóa dự án với các tệp Gradle trong Android Studio.
5. Chạy ứng dụng trên Emulator hoặc thiết bị thật (yêu cầu Android API 33 trở lên).

### 2. Thiết lập Supabase Edge Functions & Secrets
1. Khởi tạo Supabase và chạy các mã SQL tạo bảng trong thư mục `/supabase/schema.sql`.
2. Thiết lập khóa Gemini API và Google Routes API trong Supabase:
   ```bash
   npx supabase secrets set GEMINI_API_KEY=your_gemini_key
   npx supabase secrets set GOOGLE_ROUTES_API_KEY=your_google_routes_key
   ```
3. Deploy các hàm Edge Functions:
   ```bash
   npx supabase functions deploy uwb-ai-assistant
   npx supabase functions deploy google-routes
   ```

### 3. Hướng phát triển MongoDB (Roadmap)
Để chuẩn bị cho việc di chuyển dữ liệu log thiết bị sang MongoDB, cấu hình môi trường mẫu đã được chuẩn bị sẵn tại tệp `.env.example`. Khi backend MongoDB được kích hoạt, đổi tên thành `.env` và điền chuỗi kết nối:
```properties
MONGODB_URI=mongodb+srv://<username>:<password>@cluster.mongodb.net/uwb_db
PORT=3000
```

---

## 📱 Luồng Demo mẫu (Hà Nội)

1. **Khởi chạy**: Bản đồ được mặc định định vị quanh Lăng Chủ tịch Hồ Chí Minh tại Hà Nội.
2. **Kích hoạt Supabase**: Truy cập màn hình **System**, chuyển Data Source sang `SUPABASE`.
3. **Chạy Giả lập**: Bật chế độ chạy giả lập trong tab **Map**.
4. **Theo dõi Sự kiện**: Tab **Events** hiển thị các sự kiện ra/vào vùng cấm Geofence thời gian thực.
5. **Trò chuyện với AI**: Tab **AI** cho phép hỏi trợ lý về tình trạng an toàn hệ thống (ví dụ: *"Các tag có đang an toàn không?"*).
