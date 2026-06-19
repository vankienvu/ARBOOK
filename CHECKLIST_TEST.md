# Checklist Test MVP

## Backend / Database

- [ ] Tạo `backend/.env` từ `backend/.env.example`.
- [ ] Chuyển Railway URL dạng `postgresql://user:pass@host:port/db` sang JDBC hoặc đặt `DATABASE_URL`.
- [ ] Chạy `.\mvnw.cmd spring-boot:run` trong `backend`.
- [ ] Flyway migration chạy thành công trên PostgreSQL Railway.
- [ ] Seed data được tạo.
- [ ] Đăng nhập được 4 tài khoản demo.
- [ ] Tài khoản bị `LOCKED` không đăng nhập được.
- [ ] Student không truy cập được `/admin/**` và `/cms/**`.
- [ ] Teacher chỉ xem lớp được phân công.
- [ ] Content Manager tạo subject/textbook/chapter/lesson/marker/model/AR content.
- [ ] Content Manager submit review AR content.
- [ ] Admin approve/reject AR content.
- [ ] API `GET /api/ar-markers/resolve?code=BIO_CELL_001` trả model, animation, labels.
- [ ] API marker không tồn tại trả `MARKER_NOT_FOUND`.
- [ ] API content chưa publish trả `CONTENT_NOT_PUBLISHED`.
- [ ] Student làm quiz và có quiz_attempt.
- [ ] Student gửi feedback.
- [ ] Teacher giao bài và tạo assignment_students.
- [ ] Teacher xem tiến độ lớp.
- [ ] Admin xử lý feedback.

## Unity / Vuforia

- [ ] Mở `UnityProject` bằng Unity 2022.3 LTS hoặc mới hơn.
- [ ] Import Vuforia Engine.
- [ ] Cấu hình Vuforia license key.
- [ ] Tạo scene `ARTextbookDemo` với AR Camera.
- [ ] Tạo Image Target cho `BIO_CELL_001`, `SOLAR_SYSTEM_001`, `HEART_001`.
- [ ] Gắn `ARMarkerController` và `ModelController` vào từng Image Target.
- [ ] Gắn `ARContentApiClient` vào scene object, backend URL mặc định `http://localhost:8080`.
- [ ] Tạo UI panel với marker code, lesson title, AR content title, description.
- [ ] Gắn Button animation/reset vào `ARInfoPanelController`.
- [ ] Bấm Play trong Unity Editor.
- [ ] Webcam nhận diện marker demo.
- [ ] Model placeholder hiện đúng trên marker.
- [ ] Button animation làm model xoay/chuyển động.
- [ ] Button reset trả model về transform ban đầu.
- [ ] Khi backend chạy, Unity log API success.
- [ ] Khi backend tắt, Unity log API failed và dùng fallback data.
