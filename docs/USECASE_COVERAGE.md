# Đối chiếu use case

Phạm vi rà soát: đặc tả use case thư viện đã gửi trong hội thoại, mã nguồn Swing và reader web, schema/migration SQL hiện có.

## Kết quả theo nhóm

| Nhóm | UC | Kết quả | Ghi chú |
|---|---:|---|---|
| Tài khoản | UC01–UC11 | Đáp ứng | Đăng ký chỉ cho hồ sơ trường hợp lệ; BCrypt; cập nhật liên hệ/mật khẩu; tạo nhân viên; phân quyền; khóa/mở có lý do; ngăn trùng qua kiểm tra và ràng buộc DB. |
| Quản lý độc giả | UC01–UC06 | Đáp ứng | Tìm theo mã/tên/email/điện thoại/khoa; sửa hồ sơ; xóa chỉ khi không có giao dịch/ràng buộc; lịch sử mượn và vi phạm tra cứu theo quyền. Hồ sơ có lịch sử được ngừng hoạt động để giữ liên kết. |
| Thẻ thư viện | UC01–UC08 | Đáp ứng có điều kiện | 50.000đ cấp mới, 20.000đ cấp lại; tiền mặt hoặc QR giả lập; yêu cầu chờ giao; thủ thư xác nhận giao mới kích hoạt; cấp lại khóa thẻ cũ. Thanh toán QR là mô phỏng; giao thẻ cần thao tác trực tiếp. |
| Sách | UC01–UC09 | Đáp ứng | CRUD đầu sách và lookup thể loại/tác giả/NXB; tìm theo ISBN/tên/tác giả/thể loại/NXB; thống kê số bản theo tình trạng; trạng thái bản sách được cập nhật, nhưng bản đang mượn/được giữ phải xử lý qua luồng nghiệp vụ tương ứng. |
| Mượn–trả | UC01–UC06 | Đáp ứng có điều kiện | Lưu phiếu nháp và khóa bản ở `PENDING_LOAN`; xác nhận bàn giao mới chuyển sang `ON_LOAN`, bắt đầu hạn 21 ngày và cho in phiếu; có thể hủy nháp và trả bản về kho. Kiểm tra thẻ, điều kiện thành viên, hạn mức 5 bản, gia hạn 1 lần tối đa 7 ngày, trả sách và sinh phạt/bồi thường. |
| Nhập kho | UC01–UC07 | Đáp ứng có điều kiện | Xem tồn kho qua danh mục/báo cáo; tạo yêu cầu nhiều đầu sách; mở thư nháp nhà cung cấp; tiếp nhận nhiều đầu sách, lưu số lượng chứng từ/đạt/loại và lý do; chỉ số lượng đạt tạo mã vạch/tăng kho. Email phải được gửi thủ công và xác nhận thủ công. |
| Vi phạm | UC01–UC08 | Đáp ứng | Tự tính phạt theo ngày; tính bồi thường theo cấu hình; hao mòn thông thường không bị tính bồi thường; ghi nhận thanh toán tiền mặt tại quầy và thanh toán QR mô phỏng trên web; hỗ trợ thanh toán một phần, biên lai và lịch sử. |
| Nhân viên | UC01–UC05 | Đáp ứng | Tạo/cập nhật/tra cứu nhân viên, ngừng hoạt động thay cho xóa khi có liên kết lịch sử, phân công và cập nhật tiến độ nhiệm vụ. |
| Báo cáo | UC01–UC09 | Đáp ứng | Bốn nhóm báo cáo có khoảng ngày, bảng, biểu đồ, snapshot CSV được lưu trong database, lịch sử để xem snapshot và xuất PDF/XLSX. |
| Tài liệu điện tử | UC01–UC05 | Đáp ứng | Thêm/sửa/lưu trữ/khôi phục/xóa PDF; kiểm tra chữ ký `%PDF-` và dấu kết thúc; tra cứu cần từ khóa; đọc trực tuyến chỉ sau đăng nhập, không cần thẻ. |

## Điều kiện để chạy đúng

- Database phải dùng schema hiện hành. Với database đã có volume, lần lượt áp dụng các migration V002–V007 theo thứ tự tên tệp.
- Cần nhập trước danh mục khoa/viện và roster thật của sinh viên, học viên, giảng viên. Dự án không tự tạo danh tính giả.
- Cổng độc giả và Swing phải dùng cùng cấu hình MySQL và cùng thư mục `library-storage` để PDF đã tải lên đọc được trên web.
- Để gửi yêu cầu NCC, máy nhân viên cần ứng dụng email mặc định; hệ thống chỉ mở thư nháp, không thể tự biết email đã gửi nếu nhân viên chưa xác nhận.
- Không có cổng thanh toán thật. Các khoản `QR_SIMULATED` là giao dịch minh họa, không phải xác nhận từ ngân hàng.

## Kiểm chứng trong lần rà này

- Maven package Swing thành công với Java release 21; 26 lớp nguồn được biên dịch.
- Maven package reader web thành công; Spring Boot tạo `library-reader-web-1.0.0-exec.jar`.
- MySQL Docker được khởi động; V004–V007 đã áp dụng trên volume hiện tại. Đã xác nhận bảng yêu cầu NCC, trạng thái `WORN`, phiếu `DRAFT`/bản `PENDING_LOAN`, lịch sử báo cáo, cột lý do cấp lại, phương thức QR, phí và hạn mượn.
- Smoke test web: `/`, `/login`, `/register` trả 200; mã trường không tồn tại bị từ chối; `/catalog` chặn khách bằng redirect 302; sau khi sửa truy vấn xác minh trạng thái thành viên, tài khoản `docgia_demo` đăng nhập được và catalog hiển thị ISBN `DEMO-978-001`/`DEMO-978-002` (HTTP 200).
- Đã xác nhận ba vai trò tài khoản staff và năm hồ sơ demo còn hiệu lực; tên tiếng Việt được lưu UTF-8.
- Test tự động được bỏ qua trong lệnh package. Chưa xác minh toàn bộ luồng nghiệp vụ đầu-cuối với người dùng thật, email NCC thật, giao dịch qua ngân hàng, giao thẻ vật lý hoặc máy in thật.
