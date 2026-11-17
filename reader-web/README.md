# Reader Web

Website độc giả viết bằng Java Spring Boot, dùng chung database MySQL với ứng dụng quản lý thư viện Java Swing.

## Chạy local

Khởi động MySQL từ thư mục gốc dự án:

```powershell
docker compose up -d
```

Sau đó đóng gói và chạy website:

```powershell
cd reader-web
$env:DB_PORT = '3307'
mvn "-Dmaven.repo.local=..\.m2\repository" package
java -jar target/library-reader-web-1.0.0-exec.jar
```

Mở `http://localhost:8080`. Hồ sơ độc giả phải là sinh viên, học viên hoặc giảng viên đang hoạt động; khi đăng ký cần dùng đúng mã thành viên và email đã lưu trong hồ sơ trường. Ứng dụng kiểm tra trạng thái tài khoản và điều kiện hồ sơ lại trên từng yêu cầu đã đăng nhập.

## Chức năng

- Đăng ký tài khoản độc giả bằng mã thành viên và email đã được lưu trong hồ sơ trường; đăng nhập và đăng xuất.
- Xem hồ sơ trường, cập nhật email/số điện thoại, đổi mật khẩu.
- Đăng ký hoặc yêu cầu cấp lại thẻ; xem phí, khoản đã thu và trạng thái xử lý. Cấp lại yêu cầu ghi lý do; thẻ cũ bị khóa sau khi thủ thư giao thẻ mới.
- Tra cứu sách theo tên, ISBN hoặc tác giả; gửi và hủy yêu cầu đặt trước.
- Xem lịch sử mượn–trả; gia hạn một lần thêm tối đa 7 ngày khi sách chưa quá hạn.
- Xem lịch sử vi phạm, khoản còn phải thu và thanh toán một phần/toàn bộ bằng QR giả lập.
- Tra cứu và đọc PDF trực tuyến sau khi đăng nhập; không yêu cầu có thẻ thư viện.
- Biểu mẫu POST có token CSRF; session hết hạn sau 30 phút; mật khẩu lưu dưới dạng BCrypt. Mỗi yêu cầu đã đăng nhập đều kiểm tra lại trạng thái tài khoản và hồ sơ trường.

## Đăng ký và thẻ thư viện

Tài khoản chỉ được tạo cho một sinh viên, học viên hoặc giảng viên đang hoạt động trong `school_members`; cần nhập đúng mã trường và email đã đăng ký. Một hồ sơ chỉ có thể liên kết với một tài khoản.

Phí cấp thẻ lần đầu là 50.000đ, cấp lại là 20.000đ. Người đọc có thể chọn tiền mặt tại quầy hoặc QR giả lập. QR giả lập chỉ ghi nhận giao dịch demo trong database, không kết nối ngân hàng và không trừ tiền thật. Thủ thư xác nhận thu tiền mặt và giao thẻ vật lý trong ứng dụng Swing; thẻ chỉ hoạt động sau khi giao. Chưa tích hợp cổng thu tiền trực tuyến.

Tài liệu PDF do ứng dụng Swing thêm được lưu trong `library-storage/documents`. Website dùng cùng thư mục để đọc tệp; cấu hình khác bằng biến môi trường `LIBRARY_STORAGE`.
