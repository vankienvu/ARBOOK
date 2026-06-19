# Hướng Dẫn Chạy & Demo Hệ Thống AR Textbook

Tài liệu này hướng dẫn chi tiết cách khởi chạy toàn bộ dự án **AR Textbook** (bao gồm Backend Spring Boot và Unity Vuforia Client) cùng kịch bản chạy thử (Demo) cho từng vai trò trong hệ thống.

---

## 1. Cấu Hình & Khởi Chạy Hệ Thống

### Bước 1: Khởi chạy Database & Backend
Dự án hỗ trợ chạy backend trực tiếp qua Maven hoặc đóng gói qua Docker.

#### Cách 1: Chạy bằng Docker (Khuyên dùng)
Đảm bảo bạn đã cài đặt Docker và Docker Compose. Tại thư mục gốc của dự án (`D:\ARBOOK`), mở terminal và chạy:
```powershell
docker-compose up --build
```
Hệ thống sẽ tự động tải các container PostgreSQL, cấu hình database và khởi chạy backend Spring Boot tại cổng `8080`.

#### Cách 2: Chạy trực tiếp local
1. Đảm bảo bạn có database PostgreSQL trống đang chạy.
2. Tạo file `.env` từ file mẫu tại thư mục `backend`:
   ```powershell
   Copy-Item backend\.env.example backend\.env
   ```
3. Điền thông tin kết nối database vào file `.env` vừa tạo:
   ```properties
   SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/arbook?options=-c%20TimeZone=UTC
   SPRING_DATASOURCE_USERNAME=postgres
   SPRING_DATASOURCE_PASSWORD=your_password
   ```
4. Chạy lệnh để khởi tạo backend:
   ```powershell
   cd backend
   .\mvnw.cmd spring-boot:run
   ```

Khi backend chạy thành công:
- Truy cập giao diện web tại: [http://localhost:8080](http://localhost:8080)
- Kiểm tra kết nối API Unity: [http://localhost:8080/api/ar-markers/resolve?code=BIO_CELL_001](http://localhost:8080/api/ar-markers/resolve?code=BIO_CELL_001)

---

### Bước 2: Chạy Unity Client (AR Viewer)
1. Mở Unity Hub, nhấn **Add** và trỏ đến thư mục [UnityProject](file:///d:/ARBOOK/UnityProject) bằng phiên bản Unity `2022.3 LTS` (hoặc mới hơn).
2. Trong tab **Project** của Unity, mở scene: `Assets/Scenes/ARTextbookDemo.unity`.
3. Kiểm tra cấu hình kết nối API backend tại đối tượng `ARContentApiClient` trong Hierarchy:
   - `Backend Base URL`: `http://localhost:8080`
4. Mở **Vuforia Configuration** (nhấn `Ctrl + Shift + V`) và nhập license key Vuforia của bạn để bắt đầu quét bằng Webcam trên máy tính (hoặc sử dụng file cấu hình mặc định).
5. Chuẩn bị các marker mẫu nằm trong thư mục [docs/markers](file:///d:/ARBOOK/docs/markers/):
   - `BIO_CELL_001` (Tế bào học)
   - `SOLAR_SYSTEM_001` (Hệ Mặt Trời)
   - `HEART_001` (Cấu tạo tim người)

---

## 2. Thông Tin Tài Khoản Demo

Dưới đây là các tài khoản demo đã được khởi tạo tự động trong hệ thống thông qua `DemoDataSeeder`. 
Bạn sử dụng các tài khoản này tại trang đăng nhập: [http://localhost:8080/login](http://localhost:8080/login)

| Tên vai trò | Email tài khoản | Mật khẩu | Chức năng chính |
| :--- | :--- | :--- | :--- |
| **Học sinh** (Student) | `student@artextbook.com` | `123456` | Học bài học 3D, làm bài quiz, theo dõi điểm số. |
| **Giáo viên** (Teacher) | `teacher@artextbook.com` | `123456` | Giao bài tập, xem thống kê kết quả học tập lớp học. |
| **Quản lý học liệu** (CMS) | `content@artextbook.com` | `123456` | Quản lý Marker, upload model 3D, tạo bài học và gửi duyệt AR. |
| **Quản trị viên** (Admin) | `admin@artextbook.com` | `123456` | Quản lý người dùng, duyệt bài học AR mới và xem analytics hệ thống. |

---

## 3. Kịch Bản Chạy Demo Hệ Thống (Walkthrough Scenarios)

### Kịch bản 1: Học sinh học tập trực quan (Student Walkthrough)
1. Truy cập [http://localhost:8080/login](http://localhost:8080/login).
2. Đăng nhập bằng tài khoản Học sinh: `student@artextbook.com` / `123456`.
3. Sau khi đăng nhập, sidebar bên trái sẽ chỉ xuất hiện duy nhất mục **Học Sinh**. Nhấn vào đây để vào **Student Dashboard**.
4. Chọn một bài học mẫu (ví dụ: truy cập [http://localhost:8080/student/lessons/1](http://localhost:8080/student/lessons/1)):
   - **Tương tác Web 3D WebGL:** Ở panel bên trái, bạn sẽ thấy mô hình 3D tương tác. Giữ chuột trái để xoay mô hình, chuột phải để dịch chuyển, lăn chuột để phóng to/thu nhỏ.
   - **Nhãn chú thích động:** Click các nút nhãn (như *Nhân tế bào*, *Màng sinh chất*), hệ thống sẽ highlight và hiển thị mô tả chi tiết bào quan tương ứng.
   - **Chạy hoạt cảnh (Animations):** Nhấn nút *▶ Chạy hoạt cảnh*, mô hình 3D sẽ thực hiện chuyển động sinh học trực quan.
5. **Làm Quiz kiểm tra kiến thức:** Nhấn **Bắt đầu làm Quiz** (hoặc truy cập [http://localhost:8080/student/quizzes/1](http://localhost:8080/student/quizzes/1)).
   - Chọn các câu trả lời trắc nghiệm và nhấn **Nộp bài làm**.
   - Điểm số và thông tin kết quả (Đạt/Không Đạt) sẽ hiển thị ngay lập tức thông qua AJAX mà không cần tải lại trang.

### Kịch bản 2: Giáo viên theo dõi tiến độ lớp học (Teacher Walkthrough)
1. Đăng nhập bằng tài khoản Giáo viên: `teacher@artextbook.com` / `123456`.
2. Truy cập **Giáo Viên** trên sidebar hoặc vào [http://localhost:8080/teacher/dashboard](http://localhost:8080/teacher/dashboard).
3. **Thống kê bằng biểu đồ trực quan (Chart.js):** 
   - Bạn sẽ được xem biểu đồ cột **Phổ điểm Quiz của lớp** hiển thị tỷ lệ phân bố điểm của học sinh.
   - Biểu đồ tròn **Trạng thái nộp bài** hiển thị tỷ lệ học sinh đã nộp, chưa nộp hoặc nộp muộn.
4. Truy cập **Chi tiết lớp học** (ví dụ: [http://localhost:8080/teacher/classes/1](http://localhost:8080/teacher/classes/1)) để theo dõi bảng điểm chi tiết của từng học sinh trong lớp.

### Kịch bản 3: CMS Manager quản lý học liệu và model 3D
1. Đăng nhập bằng tài khoản CMS Manager: `content@artextbook.com` / `123456`.
2. Truy cập **Quản Lý Nội Dung** trên sidebar hoặc vào [http://localhost:8080/cms/dashboard](http://localhost:8080/cms/dashboard).
3. **Quản lý & Tải lên mô hình 3D:**
   - Vào mục **Mô hình 3D** ([http://localhost:8080/cms/models](http://localhost:8080/cms/models)).
   - Tại panel tải lên, chọn file định dạng `.glb` hoặc `.gltf` của bạn và nhấn **Bắt đầu tải lên**.
   - File sẽ được lưu trữ an toàn trong thư mục `uploads/` và trả về URL để sử dụng.
4. Xem trước (Preview) nội dung AR trước khi gửi lên hàng chờ phê duyệt của Admin.

### Kịch bản 4: Admin phê duyệt nội dung AR
1. Đăng nhập bằng tài khoản Admin: `admin@artextbook.com` / `123456`.
2. Là Admin, bạn có quyền truy cập toàn bộ các vai trò trên sidebar (Học Sinh, Giáo Viên, CMS, Quản Trị Viên).
3. Vào mục **Hàng chờ kiểm duyệt** ([http://localhost:8080/admin/review-queue](http://localhost:8080/admin/review-queue)).
4. Nhấn **Phê duyệt** hoặc **Từ chối** (và nhập lý do). Khi thực hiện, bản ghi sẽ mờ dần và biến mất khỏi danh sách bằng hiệu ứng AJAX rất mượt mà.

### Kịch bản 5: Trải nghiệm AR thực tế ảo với Unity Client
1. Nhấp nút **Play** trong Unity Editor trên scene `ARTextbookDemo`.
2. Mở ảnh marker `BIO_CELL_001.png` trong thư mục [docs/markers](file:///d:/ARBOOK/docs/markers/) trên điện thoại của bạn hoặc in ra giấy.
3. Đưa ảnh marker trước Webcam máy tính.
4. **Kết quả:**
   - Unity gọi API `/api/ar-markers/resolve?code=BIO_CELL_001` tới backend.
   - Khi nhận dạng thành công, mô hình 3D của tế bào sẽ lập tức hiển thị đè lên marker trên camera, kèm bảng thông tin bài học chi tiết.
   - Bạn có thể nhấn các nút tương tác nhãn hoặc hoạt cảnh trên màn hình Unity giống hệt như trên ứng dụng web!
