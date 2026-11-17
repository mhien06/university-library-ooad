# University Library Management

Hệ thống quản lý thư viện trường đại học gồm ứng dụng quản lý bằng Java Swing, cổng độc giả bằng Java Spring Boot và cơ sở dữ liệu MySQL dùng chung.

## Cấu trúc

- `database/schema.sql`: schema MySQL 8, gồm hồ sơ sinh viên, học viên, giảng viên, nhân viên, sách, thẻ, mượn–trả, kho, vi phạm và tài liệu điện tử.
- `database/migrations/`: cập nhật schema cho database đã khởi tạo trước đó. Database mới nhận schema hiện tại khi tạo volume lần đầu.
- `src/main/java`: ứng dụng Swing.
- `reader-web/`: website độc giả.
- `docker-compose.yml`: MySQL 8.4, cổng máy host mặc định `3307`.

## Khởi động database

Cài Docker Desktop, mở PowerShell tại thư mục dự án rồi chạy:

```powershell
docker compose up -d
```

Kết nối mặc định khi phát triển: `localhost:3307`, database `university_library`, user `library_app`, password `change-this-app-password`. Có thể cấu hình `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`. Hãy đổi mật khẩu mặc định trước khi dùng chung hoặc triển khai.

Docker chỉ chạy `database/schema.sql` khi tạo database volume lần đầu. Với volume cũ, lần lượt chạy các migration V002–V007 trong MySQL Workbench hoặc công cụ MySQL tương đương. Không xóa volume nếu còn dữ liệu cần giữ.

## Build và chạy Swing

Cần Java 21 trở lên và Maven:

```powershell
mvn "-Dmaven.repo.local=.m2/repository" -DskipTests package
java -jar target/university-library-1.0.0.jar
```

Nếu database chưa có tài khoản quản trị, lần chạy đầu tạo tài khoản `admin` với mật khẩu demo `Admin@123`. Có thể đặt `LIBRARY_ADMIN_USERNAME` và `LIBRARY_ADMIN_PASSWORD` trước lần chạy đầu để thay thông tin này.

## Build và chạy website độc giả

Chạy trong PowerShell thứ hai:

```powershell
cd reader-web
mvn "-Dmaven.repo.local=..\.m2\repository" -DskipTests package
$env:DB_PORT = '3307'
java -jar target/library-reader-web-1.0.0-exec.jar
```

Truy cập `http://localhost:8080`. Thư mục PDF mặc định là `library-storage/documents`; thay đổi bằng `LIBRARY_STORAGE` và dùng cùng đường dẫn cho Swing lẫn web.

Tài khoản độc giả được đăng ký bằng mã trường và email của hồ sơ đang hoạt động đã có trong database. Dự án không tạo dữ liệu sinh viên, học viên hay giảng viên giả.

### Tài khoản thử và danh sách đăng ký demo

Database ban đầu chỉ tạo tài khoản quản trị `admin` / `Admin@123`. Muốn có tài khoản thủ thư, thủ kho và danh sách sinh viên để thử đăng ký, chạy **một lần** script demo sau trong PowerShell từ thư mục gốc:

```powershell
Get-Content -Raw database/demo-data.sql | docker exec -i university-library-db mysql --default-character-set=utf8mb4 -ulibrary_app -pchange-this-app-password university_library
```

Script này thêm hồ sơ hư cấu có chữ `DEMO` và tài khoản thử:

| Vai trò | Tên đăng nhập | Mật khẩu |
|---|---|---|
| Quản trị viên | `admin` | `Admin@123` |
| Thủ thư demo | `thu_thu_demo` | `ThuThu@123` |
| Thủ kho demo | `thu_kho_demo` | `ThuKho@123` |
| Độc giả demo | `docgia_demo` | `DocGia@123` |

Các sinh viên demo đăng ký ở web bằng mã trường và email dưới đây:

| Mã trường | Email đăng ký | Chương trình |
|---|---|---|
| `DEMO-SV-001` | `demo.sv001@example.test` | Công nghệ phần mềm |
| `DEMO-SV-002` | `demo.sv002@example.test` | Hệ thống thông tin |
| `DEMO-SV-003` | `demo.sv003@example.test` | Quản trị kinh doanh |
| `DEMO-SV-004` | `demo.sv004@example.test` | Tài chính - Ngân hàng |
| `DEMO-SV-005` | `demo.sv005@example.test` | An toàn thông tin |

Những hồ sơ `.test` là dữ liệu giả lập cục bộ, không thuộc sinh viên thật. Dùng danh sách trường thật của bạn trong phiên bản sử dụng thực tế. Đổi mật khẩu demo hoặc xóa các hồ sơ `DEMO-*` trước khi đưa database ra khỏi máy phát triển.

## Nghiệp vụ đã triển khai

- Quản lý hồ sơ sinh viên, học viên, giảng viên và trạng thái hợp lệ tại trường.
- Đăng nhập, đăng ký độc giả, cập nhật thông tin, đổi mật khẩu, tạo tài khoản nhân viên, phân quyền và khóa/mở khóa tài khoản.
- Quản lý đầu sách, thể loại, tác giả, nhà xuất bản, tình trạng từng bản sách, số lượng theo trạng thái và lưu trữ đầu sách.
- Đăng ký/cấp lại thẻ, kiểm tra phí, thu tiền tại quầy hoặc ghi nhận QR giả lập, giao thẻ và vô hiệu hóa thẻ cũ khi cấp lại.
- Đặt trước, duyệt/từ chối, giữ bản sách trong ba ngày, tự hết hạn và giải phóng bản đã giữ.
- Mượn/trả, in phiếu mượn, gia hạn tối đa một lần thêm tối đa bảy ngày, giới hạn năm bản trong 21 ngày.
- Tự tính phạt quá hạn và bồi thường sách hỏng/mất; hao mòn thông thường không bị tính bồi thường; ghi nhận thanh toán một phần và biên lai.
- Tạo yêu cầu nhập nhiều đầu sách, mở email nháp cho nhà cung cấp và ghi nhận riêng số lượng khai báo, số lượng đạt/loại khi tiếp nhận.
- Quản lý tài liệu PDF: kiểm tra chữ ký tệp, sửa, lưu trữ/khôi phục, xóa và đọc sau đăng nhập.
- Báo cáo sách, mượn–trả, độc giả–thẻ, vi phạm; xem bảng/biểu đồ và xuất XLSX/PDF.
- Cổng độc giả tra cứu sách, đặt trước, xem lịch sử, gia hạn, xem vi phạm, thanh toán QR mô phỏng, yêu cầu thẻ và đọc PDF không cần có thẻ.

## Giới hạn tích hợp

- QR hiện chỉ ghi giao dịch mô phỏng, không gọi ngân hàng và không thu tiền thật. Thủ thư vẫn phải giao thẻ vật lý trong Swing.
- Yêu cầu nhà cung cấp được lưu trong hệ thống; nút gửi mở thư nháp trong ứng dụng email mặc định. Nhân viên phải gửi thư và xác nhận thủ công.
- Việc đăng ký độc giả phụ thuộc danh sách thành viên trường đã được nhập trước.
- Để nhận các thay đổi database mới trên volume đang dùng, lần lượt áp dụng V002–V007; chúng bổ sung QR giả lập, lý do cấp lại, yêu cầu NCC, hao mòn thường, phiếu mượn nháp/chờ giao và lưu lịch sử báo cáo.

Đối chiếu chi tiết đặc tả use case: [docs/USECASE_COVERAGE.md](docs/USECASE_COVERAGE.md).

Ma trận vai trò và các thao tác được phép: [docs/ROLE_PERMISSIONS.md](docs/ROLE_PERMISSIONS.md).
