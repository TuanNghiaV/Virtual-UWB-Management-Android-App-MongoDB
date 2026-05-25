# Virtual UWB Management Android App MongoDB

**VirtualUWB** là ứng dụng Android hiện đại dùng để giả lập và giám sát hệ thống định vị trong nhà (Indoor Positioning System - IPS) sử dụng công nghệ Ultra-Wideband (UWB). Ứng dụng hỗ trợ trực quan hóa chuyển động của thẻ (tag), giám sát các vùng địa lý (geofences) và chẩn đoán thông minh bằng trí tuệ nhân tạo (AI Assistant).

Dự án sử dụng **Node.js/Express + MongoDB Atlas** làm hạ tầng dữ liệu chính thức và duy nhất cho luồng chính (main flow), hỗ trợ kết nối thời gian thực qua Server-Sent Events (SSE). Supabase chỉ còn tồn tại như cấu phần lưu trữ kế thừa (legacy/archive) trong codebase và không còn tham gia vào luồng hoạt động chính của ứng dụng.

---

## 🚀 Tính năng nổi bật

- **Google Maps & GPS Integration**: Trực quan hóa vị trí thực tế của Anchors, Tags, và vị trí điện thoại ngay trên Google Bản đồ.
- **Direct Guidance**: Chỉ hướng trực tiếp bằng khoảng cách và hướng la bàn từ Phone owner tới Tag được chọn (Tính năng Google Routes chỉ đường chi tiết hiện đang tạm tắt).
- **UWB Simulation Engine**: Bộ giả lập chuyển động ngẫu nhiên (Random Walk) tích hợp giúp mô phỏng di chuyển của Tag thời gian thực mà không cần thiết bị cứng.
- **Tìm kiếm Tag (Find My Tag)**: Giao diện la bàn hướng dẫn định vị trực tiếp từ điện thoại tới Tag được chọn.
- **Cảnh báo Geofencing**: Tự động phát hiện và cảnh báo tức thời khi các Tag đi vào hoặc ra khỏi khu vực cấm (Restricted Zone) hoặc khu vực an toàn (Safe Zone).
- **Trợ lý AI thông minh**: Tích hợp mô hình Google Gemini AI (ủy nhiệm qua backend Node.js) để phân tích trạng thái an toàn hệ thống thông qua câu hỏi ngôn ngữ tự nhiên.
- **Canvas Map Fallback**: Hỗ trợ bản đồ Canvas truyền thống làm phương án dự phòng khi không dùng Google Maps.

---

## 🛠 Công nghệ sử dụng

- **Android App**:
  - Ngôn ngữ: Kotlin
  - UI Framework: Jetpack Compose (Material 3)
  - Kiến trúc: MVVM + Repository Pattern
  - Bản đồ: Google Maps Compose SDK
  - Mạng: Ktor Client & Kotlinx Serialization
- **Backend**:
  - Môi trường: Node.js (TypeScript)
  - Framework: Express
  - ORM: Mongoose
  - Database: MongoDB Atlas
  - Realtime: Server-Sent Events (SSE)
  - AI Engine: Google Gemini API via Backend Proxy

---

## 🏗 Tổng quan kiến trúc hiện tại

```
[Android App]
      ↓ (REST / SSE Stream)
[Node.js / Express Backend]
      ↓ (Mongoose ORM)
[MongoDB Atlas]
```

### Luồng Trợ lý AI (AI Assistant):
```
[Android App] ──(Query + Device Context)──> [Backend: /api/ai/assistant] ──(Proxy)──> [Gemini API]
```

### Luồng Sự kiện thời gian thực (Realtime Events):
```
[MongoDB geofence event] ──> [Backend SSE Stream: /api/events/stream] ──> [Android Events Screen]
```

---

## 🔒 Bảo mật và Bảo vệ Secrets

Tất cả các API Key nhạy cảm (Google Maps, Gemini API) và chuỗi kết nối cơ sở dữ liệu **không bao giờ được commit** lên git. Chúng được quản lý qua:
1. `local.properties` (Android app) - đã được thêm vào `.gitignore`.
2. `backend/.env` (Backend API) - đã được thêm vào `.gitignore`.

> ⚠️ **Bảo mật quan trọng**: Android **không bao giờ** được gọi trực tiếp Gemini API hay cơ sở dữ liệu MongoDB. Tất cả các giao tiếp đều đi qua backend API để bảo vệ an toàn các key bí mật.

---

## ⚙️ Hướng dẫn cài đặt

### 1. Thiết lập Backend MongoDB API
Di chuyển vào thư mục backend và thiết lập môi trường:
```bash
cd backend
npm install
cp .env.example .env
```

Mở tệp `.env` và điền các giá trị:
```env
MONGODB_URI=<Chuỗi kết nối MongoDB Atlas thực tế>
GEMINI_API_KEY=<API key từ Google AI Studio để chạy trợ lý AI>
PORT=3001
NODE_ENV=development
CORS_ORIGIN=*
```

Khởi chạy máy chủ phát triển cục bộ:
```bash
npm run dev
```
Kiểm tra máy chủ đang chạy tại: [http://localhost:3001/health](http://localhost:3001/health).

#### 🔍 Khắc phục lỗi kết nối DNS SRV (`ECONNREFUSED` / `querySrv`):
- **Cách 1**: Thay đổi DNS Windows sang DNS công cộng (`8.8.8.8` hoặc `1.1.1.1`), sau đó chạy `ipconfig /flushdns`.
- **Cách 2**: Sử dụng chuỗi kết nối chuẩn (Standard Connection String) định dạng `mongodb://` thay vì `mongodb+srv://` liệt kê trực tiếp các shard hosts trong tệp `.env`.

### 2. Thiết lập Ứng dụng Android
1. Sao chép tệp `local.properties.example` thành `local.properties` ở thư mục gốc của dự án.
2. Điền thông tin kết nối và API Key:
   ```properties
   # Cấu hình API Backend MongoDB (Sử dụng 10.0.2.2 cho Android Emulator)
   MONGODB_API_BASE_URL=http://10.0.2.2:3001
   
   # Khóa Google Maps SDK
   MAPS_API_KEY=YOUR_GOOGLE_MAPS_ANDROID_KEY
   ```
3. Đồng bộ hóa dự án với Gradle và build trên Android Studio.
4. Chạy ứng dụng:
   ```bash
   ./gradlew assembleDebug
   ./gradlew installDebug
   ```

---

## ⚠️ Lưu ý về tính năng chỉ đường (Google Routes)
Tính năng chỉ đường chi tiết (turn-by-turn routing) qua Google Routes API hiện đang **tạm thời được tắt (disabled)** để tránh lỗi `403 Forbidden` liên quan đến cấu hình API Restriction/Billing phía Google Cloud. 

Ứng dụng hiện tại tự động chuyển sang sử dụng chế độ **chỉ hướng trực tiếp (Direct Guidance)** (vẽ đường thẳng liên kết và tính toán khoảng cách/hướng đi trực tiếp theo la bàn thời gian thực). Tính năng chỉ đường nâng cao sẽ được triển khai lại sau khi các vấn đề xác thực tài khoản Google Cloud được giải quyết.
