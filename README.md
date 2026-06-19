# AR Textbook 3D Learning System

Ứng dụng MVP hỗ trợ học sinh quét marker/trang sách giáo khoa để xem nội dung học tập dạng 3D. Dự án gồm Spring Boot backend/web quản lý nghiệp vụ và Unity Vuforia demo chạy local bằng webcam.

## Kiến Trúc

```text
PostgreSQL Railway
        ^
        |
Spring Boot Backend local - http://localhost:8080
        ^
        |
Unity Editor + Vuforia Webcam Play Mode
```

## Công Nghệ

- Java 21, Spring Boot 3.5.x, Spring Security, Spring Data JPA, JdbcTemplate.
- PostgreSQL Railway, Flyway Migration.
- Thymeleaf, Bootstrap 5.
- Unity, Vuforia Engine, C# scripts, UnityWebRequest.

## Cấu Hình PostgreSQL Railway

Không commit connection string thật. Tạo file:

```powershell
Copy-Item backend\.env.example backend\.env
```

Điền một trong hai cách:

```properties
SPRING_DATASOURCE_URL=jdbc:postgresql://<RAILWAY_HOST>:<RAILWAY_PORT>/<RAILWAY_DATABASE>?options=-c%20TimeZone=UTC
SPRING_DATASOURCE_USERNAME=<RAILWAY_USERNAME>
SPRING_DATASOURCE_PASSWORD=<RAILWAY_PASSWORD>
```

Hoặc:

```properties
DATABASE_URL=postgresql://<USER>:<PASSWORD>@<HOST>:<PORT>/<DATABASE>
```

Nếu Railway trả về `postgresql://...`, backend có lớp chuyển sang JDBC URL tự động. Nếu tự chuyển thủ công, đổi thành:

```text
jdbc:postgresql://host:port/database?options=-c%20TimeZone=UTC
```

## Chạy Backend

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

Nếu cổng `8080` đang bận, chạy tạm ở cổng khác:

```powershell
cd backend
$env:SERVER_PORT="8081"
$env:APP_BACKEND_BASE_URL="http://localhost:8081"
.\mvnw.cmd spring-boot:run
```

Sau khi chạy, mở:

- Web: `http://localhost:8080`
- Login: `http://localhost:8080/login`
- Unity API: `http://localhost:8080/api/ar-markers/resolve?code=BIO_CELL_001`

## Tài Khoản Demo

- Admin: `admin@artextbook.com` / `123456`
- Content Manager: `content@artextbook.com` / `123456`
- Teacher: `teacher@artextbook.com` / `123456`
- Student: `student@artextbook.com` / `123456`

## Web Demo Theo Role

- Public: `/`, `/login`, `/guide`
- Student: `/student/dashboard`, `/student/textbooks`, `/student/lessons/1`, `/student/quizzes/1`, `/student/progress`, `/student/feedbacks`
- Teacher: `/teacher/dashboard`, `/teacher/classes`, `/teacher/classes/1`, `/teacher/assignments`, `/teacher/analytics`
- Content Manager: `/cms/dashboard`, `/cms/subjects`, `/cms/textbooks`, `/cms/chapters`, `/cms/lessons`, `/cms/pages`, `/cms/markers`, `/cms/models`, `/cms/ar-contents`
- Admin: `/admin/dashboard`, `/admin/users`, `/admin/roles`, `/admin/schools`, `/admin/classes`, `/admin/review-queue`, `/admin/feedbacks`, `/admin/settings`, `/admin/analytics`

## Unity Demo

1. Mở thư mục `UnityProject` bằng Unity 2022.3 LTS hoặc mới hơn.
2. Vuforia Engine `11.4.4` đã được cài trong project.
3. Mở scene `Assets/Scenes/ARTextbookDemo.unity`.
4. Mở Vuforia Configuration và nhập license key cá nhân.
5. Bấm Play, sau đó đưa một trong ba marker PNG trước webcam.

Scene đã có AR Camera, UI, API client và bộ tạo runtime Image Target cho `BIO_CELL_001`, `SOLAR_SYSTEM_001`, `HEART_001`. Không cần tạo Target Manager database cho demo local này.

Nếu backend chạy, Unity gọi API resolve marker. Nếu backend tắt hoặc lỗi, Unity dùng `DemoFallbackData`.

## Marker Demo

Marker SVG nằm ở:

- `docs/markers/BIO_CELL_001.svg`
- `docs/markers/SOLAR_SYSTEM_001.svg`
- `docs/markers/HEART_001.svg`

Có thể mở bằng trình duyệt hoặc in ra giấy. Dùng ảnh rõ, đủ sáng và không phản quang.

## API Quan Trọng Cho Unity

```http
GET /api/ar-markers/resolve?code=BIO_CELL_001
```

Response thành công gồm marker, lesson, AR content, model URL, animation names, labels và status `PUBLISHED`.

## Lỗi Thường Gặp

- Không kết nối Railway: kiểm tra host, port, username, password, public URL và firewall.
- Flyway lỗi enum/table đã tồn tại: dùng database trống hoặc baseline thủ công trước khi chạy.
- Webcam không bật: kiểm tra quyền camera và đóng app đang chiếm webcam.
- Vuforia không nhận marker: dùng ảnh rõ, đủ sáng, target rating tốt, không rung.
- Unity gọi API lỗi connection refused: kiểm tra backend đang chạy ở `http://localhost:8080`.
- CORS: backend đã cho phép localhost, nhưng Unity Editor thường không bị CORS như browser.

## Đã Hoàn Thành & Nâng Cấp Enterprise

- **Refactor kiến trúc tách lớp:** Chuyển toàn bộ logic nghiệp vụ truy vấn SQL từ Controller sang các lớp Service chuyên biệt (`StudentService`, `TeacherService`, `CmsService`, `AdminService`) để dễ dàng bảo trì và viết test.
- **Nâng cấp UI/UX cao cấp:** Thiết kế giao diện tối (Modern Dark Theme), neon gradients, glassmorphism và micro-animations. Hỗ trợ 1-click autofill thông tin tài khoản demo.
- **Tương tác Web 3D WebGL:** Tích hợp bộ điều khiển nhãn (Labels) và chạy hoạt cảnh (Animations) trực tiếp trên Web bằng `<model-viewer>`.
- **Hệ thống Quiz tương tác:** Học sinh làm quiz trắc nghiệm và nộp bài AJAX trực tiếp, nhận kết quả đúng/sai và điểm số ngay trên màn hình.
- **Biểu đồ Analytics lớp học:** Tích hợp **Chart.js** vẽ biểu đồ Phổ điểm Quiz và Trạng thái nộp bài tập của học sinh trong lớp của Giáo viên.
- **Admin Review Queue:** Admin duyệt nhanh/từ chối nhanh nội dung AR trong hàng đợi bằng AJAX và hiệu ứng mượt mà.
- **Bảo mật File Upload:** Sử dụng `StorageService` cục bộ an toàn bên ngoài thư mục build, tự động sanitize tên file tránh path traversal và sinh UUID. Ánh xạ an toàn qua `WebMvcConfig`.
- **Đóng gói Docker:** Bổ sung `Dockerfile` tối ưu và `docker-compose.yml` chạy database PostgreSQL cùng backend.
- **Kiểm thử tự động:** Bổ sung `ArControllerIntegrationTest` kiểm thử API resolve của Unity thành công và thất bại.

## Cách chạy bằng Docker

1. Cài đặt Docker & Docker Compose trên máy tính.
2. Chạy lệnh tại thư mục root của dự án:
   ```bash
   docker-compose up --build
   ```
3. Sau khi khởi động thành công, mở trình duyệt truy cập: `http://localhost:8080`

## Chạy Test Tự Động

Chạy lệnh kiểm thử bằng Maven Wrapper:
```bash
cd backend
./mvnw clean test
```

## Chưa Hoàn Thành / Giới Hạn MVP

- License Vuforia cá nhân đã được cấu hình cục bộ trong Unity Editor.
- Webcam và nhận diện marker `BIO_CELL_001` đã được kiểm tra thành công.
- Model 3D hiện là placeholder primitive, không phải model thật.
- Web UI là MVP quản trị/demo, chưa phải CMS đầy đủ tiện ích upload file.
- Test tự động hiện là compile smoke; kiểm tra DB Railway/Flyway nằm trong checklist chạy thật.

## Hướng Phát Triển

- Upload file model thật và storage cloud.
- Preview 3D trên web bằng model-viewer.
- Import Vuforia database tự động.
- Dashboard analytics chi tiết hơn.
- Export báo cáo tiến độ lớp.
- Mobile build Android/iOS nếu cần demo ngoài laptop.
