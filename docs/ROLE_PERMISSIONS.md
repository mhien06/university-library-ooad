# Ma trận quyền hệ thống thư viện

Phân quyền được kiểm soát ở đăng nhập, thanh điều hướng và khởi tạo màn hình trong ứng dụng Swing. Website độc giả kiểm tra phiên `READER`, CSRF và quyền sở hữu bản ghi ở từng thao tác.

| Chức năng | Quản trị viên | Thủ thư | Thủ kho | Độc giả web |
|---|:---:|:---:|:---:|:---:|
| Xem tổng quan vận hành | Có | Có | Có | Không |
| Xem danh mục và tồn sách | Không | Có | Chỉ xem | Chỉ tra cứu trên web |
| Thêm/sửa/lưu trữ/xóa sách, cập nhật bản sách | Không | Có | Không | Không |
| Quản lý thể loại, tác giả, nhà xuất bản riêng | Không | Có | Không | Không |
| Quản lý sinh viên, học viên, giảng viên | Không | Có | Không | Chỉ sửa liên hệ của chính mình |
| Cấp, thu phí, giao, khóa/mở thẻ | Không | Có | Không | Chỉ gửi yêu cầu thẻ của chính mình |
| Duyệt/từ chối đặt trước | Không | Có | Không | Chỉ tạo/hủy yêu cầu của chính mình |
| Lập phiếu, bàn giao, nhận trả, gia hạn tại quầy | Không | Có | Không | Chỉ gia hạn lượt mượn của chính mình nếu đủ điều kiện |
| Ghi nhận/thu vi phạm | Không | Có | Không | Chỉ xem và thanh toán QR mô phỏng khoản của chính mình |
| Quản lý tài liệu PDF | Không | Có | Không | Chỉ tìm và đọc PDF đang hoạt động sau đăng nhập |
| Xem/xuất/lưu lịch sử báo cáo | Không | Có | Không | Không |
| Lập phiếu tiếp nhận, nhà cung cấp, yêu cầu nhập | Không | Không | Có | Không |
| Tạo/sửa nhân viên, khóa/mở tài khoản nhân viên | Có | Không | Không | Không |
| Đổi vai trò, khóa/mở các tài khoản | Có | Không | Không | Không |
| Phân công và cập nhật nhiệm vụ nhân viên | Có | Không | Không | Không |

## Quy tắc bắt buộc

- `ADMIN` chỉ quản trị nhân viên, tài khoản, phân quyền và phân công nhiệm vụ; không trực tiếp thực hiện nghiệp vụ thư viện hay nghiệp vụ kho.
- `LIBRARIAN` thực hiện toàn bộ nghiệp vụ bạn đọc, sách (bao gồm thể loại, tác giả, nhà xuất bản), thẻ, đặt trước, mượn–trả, vi phạm, tài liệu điện tử và báo cáo; không có quyền nhập kho hay quản trị tài khoản/nhân sự.
- `WAREHOUSE` chỉ có tổng quan kho, danh mục sách chỉ đọc và nghiệp vụ kho. Các nút thêm/sửa/xóa/lưu trữ sách không được tạo cho vai trò này.
- `READER` không thể đăng nhập ứng dụng Swing. Tài khoản này chỉ dùng cổng web và chỉ truy cập dữ liệu có `member_id` của phiên hiện tại.
- Tài khoản bị `LOCKED`, nhân viên `INACTIVE`, hoặc hồ sơ trường không còn `ACTIVE`/đã quá hạn đều bị từ chối đăng nhập.
- Không thể gỡ vai trò hoặc khóa quản trị viên đang hoạt động cuối cùng; quản trị viên cũng không tự khóa, tự ngừng làm việc hay tự gỡ quyền `ADMIN` của chính mình.
- Các thay đổi trạng thái quan trọng vẫn có kiểm tra nghiệp vụ: thẻ phải thanh toán đủ mới giao, sách đang mượn/giữ không được đổi trạng thái tự do, gia hạn bị chặn khi quá hạn hoặc đang có yêu cầu đặt trước.

## Phạm vi an toàn

Phân quyền trên áp dụng cho ứng dụng và website. Database phát triển đang dùng một tài khoản kết nối chung (`library_app`); vì vậy không được cấp mật khẩu database hoặc quyền truy cập máy chủ cho người không tin cậy. Khi triển khai thật, đặt database trong mạng nội bộ và để chỉ backend truy cập database.
