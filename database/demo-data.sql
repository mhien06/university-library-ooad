-- Fictional local-only records for exercising staff accounts and reader registration.
-- These are not real student/staff identities. Do not import into production.
USE university_library;

INSERT INTO departments (department_code, department_name) VALUES
  ('DEMO-CNTT', 'Khoa Công nghệ thông tin (DỮ LIỆU DEMO)'),
  ('DEMO-KT', 'Khoa Kinh tế (DỮ LIỆU DEMO)')
ON DUPLICATE KEY UPDATE department_name=VALUES(department_name), is_active=TRUE;

INSERT INTO school_members (member_code, member_type, full_name, date_of_birth, email, department_id,
                           institution_status, valid_from, valid_until)
SELECT 'DEMO-SV-001','STUDENT','Nguyễn An Demo','2005-03-14','demo.sv001@example.test',department_id,'ACTIVE','2023-09-01','2027-08-31'
FROM departments WHERE department_code='DEMO-CNTT'
ON DUPLICATE KEY UPDATE member_type=VALUES(member_type),full_name=VALUES(full_name),date_of_birth=VALUES(date_of_birth),email=VALUES(email),department_id=VALUES(department_id),institution_status='ACTIVE',valid_until=VALUES(valid_until);
INSERT INTO students (member_id,program_name,class_code,academic_year,expected_graduation)
SELECT member_id,'Công nghệ phần mềm','DEMO-KTPM-01','2025-2026','2027-08-31' FROM school_members WHERE member_code='DEMO-SV-001'
ON DUPLICATE KEY UPDATE program_name=VALUES(program_name),class_code=VALUES(class_code),academic_year=VALUES(academic_year),expected_graduation=VALUES(expected_graduation);

INSERT INTO school_members (member_code, member_type, full_name, date_of_birth, email, department_id,
                           institution_status, valid_from, valid_until)
SELECT 'DEMO-SV-002','STUDENT','Trần Bình Demo','2004-11-02','demo.sv002@example.test',department_id,'ACTIVE','2022-09-01','2027-08-31'
FROM departments WHERE department_code='DEMO-CNTT'
ON DUPLICATE KEY UPDATE member_type=VALUES(member_type),full_name=VALUES(full_name),date_of_birth=VALUES(date_of_birth),email=VALUES(email),department_id=VALUES(department_id),institution_status='ACTIVE',valid_until=VALUES(valid_until);
INSERT INTO students (member_id,program_name,class_code,academic_year,expected_graduation)
SELECT member_id,'Hệ thống thông tin','DEMO-HTTT-02','2025-2026','2026-08-31' FROM school_members WHERE member_code='DEMO-SV-002'
ON DUPLICATE KEY UPDATE program_name=VALUES(program_name),class_code=VALUES(class_code),academic_year=VALUES(academic_year),expected_graduation=VALUES(expected_graduation);

INSERT INTO school_members (member_code, member_type, full_name, date_of_birth, email, department_id,
                           institution_status, valid_from, valid_until)
SELECT 'DEMO-SV-003','STUDENT','Lê Chi Demo','2005-07-20','demo.sv003@example.test',department_id,'ACTIVE','2023-09-01','2027-08-31'
FROM departments WHERE department_code='DEMO-KT'
ON DUPLICATE KEY UPDATE member_type=VALUES(member_type),full_name=VALUES(full_name),date_of_birth=VALUES(date_of_birth),email=VALUES(email),department_id=VALUES(department_id),institution_status='ACTIVE',valid_until=VALUES(valid_until);
INSERT INTO students (member_id,program_name,class_code,academic_year,expected_graduation)
SELECT member_id,'Quản trị kinh doanh','DEMO-QTKD-01','2025-2026','2027-08-31' FROM school_members WHERE member_code='DEMO-SV-003'
ON DUPLICATE KEY UPDATE program_name=VALUES(program_name),class_code=VALUES(class_code),academic_year=VALUES(academic_year),expected_graduation=VALUES(expected_graduation);

INSERT INTO school_members (member_code, member_type, full_name, date_of_birth, email, department_id,
                           institution_status, valid_from, valid_until)
SELECT 'DEMO-SV-004','STUDENT','Phạm Duy Demo','2004-01-08','demo.sv004@example.test',department_id,'ACTIVE','2022-09-01','2027-08-31'
FROM departments WHERE department_code='DEMO-KT'
ON DUPLICATE KEY UPDATE member_type=VALUES(member_type),full_name=VALUES(full_name),date_of_birth=VALUES(date_of_birth),email=VALUES(email),department_id=VALUES(department_id),institution_status='ACTIVE',valid_until=VALUES(valid_until);
INSERT INTO students (member_id,program_name,class_code,academic_year,expected_graduation)
SELECT member_id,'Tài chính - Ngân hàng','DEMO-TCNH-02','2025-2026','2026-08-31' FROM school_members WHERE member_code='DEMO-SV-004'
ON DUPLICATE KEY UPDATE program_name=VALUES(program_name),class_code=VALUES(class_code),academic_year=VALUES(academic_year),expected_graduation=VALUES(expected_graduation);

INSERT INTO school_members (member_code, member_type, full_name, date_of_birth, email, department_id,
                           institution_status, valid_from, valid_until)
SELECT 'DEMO-SV-005','STUDENT','Võ Hà Demo','2005-05-26','demo.sv005@example.test',department_id,'ACTIVE','2023-09-01','2027-08-31'
FROM departments WHERE department_code='DEMO-CNTT'
ON DUPLICATE KEY UPDATE member_type=VALUES(member_type),full_name=VALUES(full_name),date_of_birth=VALUES(date_of_birth),email=VALUES(email),department_id=VALUES(department_id),institution_status='ACTIVE',valid_until=VALUES(valid_until);
INSERT INTO students (member_id,program_name,class_code,academic_year,expected_graduation)
SELECT member_id,'An toàn thông tin','DEMO-ATTT-01','2025-2026','2027-08-31' FROM school_members WHERE member_code='DEMO-SV-005'
ON DUPLICATE KEY UPDATE program_name=VALUES(program_name),class_code=VALUES(class_code),academic_year=VALUES(academic_year),expected_graduation=VALUES(expected_graduation);

INSERT INTO staff_profiles (staff_code,full_name,email,job_title,employment_status) VALUES
  ('DEMO-LIB-001','Thủ thư Demo','thu.thu@example.test','LIBRARIAN','ACTIVE'),
  ('DEMO-WH-001','Thủ kho Demo','thu.kho@example.test','WAREHOUSE','ACTIVE')
ON DUPLICATE KEY UPDATE full_name=VALUES(full_name),email=VALUES(email),job_title=VALUES(job_title),employment_status='ACTIVE';

INSERT INTO accounts (username,password_hash,staff_id)
SELECT 'thu_thu_demo','$2a$12$ejv5xUpbxGuDK39gjq1nVerqP46NTtGtKwKxR.qSGr7E5pg3WCTV2',staff_id
FROM staff_profiles WHERE staff_code='DEMO-LIB-001'
ON DUPLICATE KEY UPDATE username=VALUES(username);
INSERT INTO account_roles (account_id,role)
SELECT account_id,'LIBRARIAN' FROM accounts WHERE username='thu_thu_demo'
ON DUPLICATE KEY UPDATE granted_at=granted_at;

INSERT INTO accounts (username,password_hash,staff_id)
SELECT 'thu_kho_demo','$2a$12$8ZN5CqpEtMqPYiuJdGhLO.lzyEnB.yp1E3UkF1UJAhLzlRWz92N5S',staff_id
FROM staff_profiles WHERE staff_code='DEMO-WH-001'
ON DUPLICATE KEY UPDATE username=VALUES(username);
INSERT INTO account_roles (account_id,role)
SELECT account_id,'WAREHOUSE' FROM accounts WHERE username='thu_kho_demo'
ON DUPLICATE KEY UPDATE granted_at=granted_at;

-- Convenience reader login for quick local smoke testing. It is linked only to DEMO-SV-001.
INSERT INTO accounts (username,password_hash,member_id)
SELECT 'docgia_demo','$2a$12$V0OCKrattuRTgcTIV6hFq.KG.7x9pA5vrDpbAZMpftquOqsWlaVTW',member_id
FROM school_members WHERE member_code='DEMO-SV-001'
ON DUPLICATE KEY UPDATE password_hash=VALUES(password_hash),member_id=VALUES(member_id),account_status='ACTIVE';
INSERT INTO account_roles (account_id,role)
SELECT account_id,'READER' FROM accounts WHERE username='docgia_demo'
ON DUPLICATE KEY UPDATE granted_at=granted_at;

-- Small fictional catalogue so the web reader and warehouse/report screens have sample inventory.
INSERT INTO categories (category_name,description) VALUES
  ('Lập trình (DEMO)','Danh mục hư cấu cho kiểm thử')
ON DUPLICATE KEY UPDATE description=VALUES(description),is_active=TRUE;
INSERT INTO authors (author_name,biography) VALUES
  ('Tác giả Demo A','Hồ sơ hư cấu để kiểm thử.'),
  ('Tác giả Demo B','Hồ sơ hư cấu để kiểm thử.')
ON DUPLICATE KEY UPDATE biography=VALUES(biography);
INSERT INTO publishers (publisher_name,address,email) VALUES
  ('Nhà xuất bản Demo','Địa chỉ hư cấu','nxb@example.test')
ON DUPLICATE KEY UPDATE address=VALUES(address),email=VALUES(email);

INSERT INTO books (isbn,title,category_id,publisher_id,publication_year,edition,language_code,price,description)
SELECT 'DEMO-978-001','Java căn bản (sách DEMO)',c.category_id,p.publisher_id,2024,'Bản thử nghiệm','vi',180000,'Đầu sách hư cấu để thử tra cứu và đặt trước.'
FROM categories c JOIN publishers p ON p.publisher_name='Nhà xuất bản Demo' WHERE c.category_name='Lập trình (DEMO)'
ON DUPLICATE KEY UPDATE title=VALUES(title),category_id=VALUES(category_id),publisher_id=VALUES(publisher_id),price=VALUES(price),description=VALUES(description);
INSERT INTO books (isbn,title,category_id,publisher_id,publication_year,edition,language_code,price,description)
SELECT 'DEMO-978-002','Cơ sở dữ liệu thực hành (sách DEMO)',c.category_id,p.publisher_id,2023,'Bản thử nghiệm','vi',210000,'Đầu sách hư cấu để thử tra cứu và đặt trước.'
FROM categories c JOIN publishers p ON p.publisher_name='Nhà xuất bản Demo' WHERE c.category_name='Lập trình (DEMO)'
ON DUPLICATE KEY UPDATE title=VALUES(title),category_id=VALUES(category_id),publisher_id=VALUES(publisher_id),price=VALUES(price),description=VALUES(description);
INSERT INTO books (isbn,title,category_id,publisher_id,publication_year,edition,language_code,price,description)
SELECT 'DEMO-978-003','Phân tích thiết kế hệ thống (sách DEMO)',c.category_id,p.publisher_id,2025,'Bản thử nghiệm','vi',195000,'Đầu sách hư cấu để thử tra cứu và đặt trước.'
FROM categories c JOIN publishers p ON p.publisher_name='Nhà xuất bản Demo' WHERE c.category_name='Lập trình (DEMO)'
ON DUPLICATE KEY UPDATE title=VALUES(title),category_id=VALUES(category_id),publisher_id=VALUES(publisher_id),price=VALUES(price),description=VALUES(description);

INSERT IGNORE INTO book_authors (book_id,author_id)
SELECT b.book_id,a.author_id FROM books b JOIN authors a ON a.author_name='Tác giả Demo A' WHERE b.isbn IN ('DEMO-978-001','DEMO-978-002');
INSERT IGNORE INTO book_authors (book_id,author_id)
SELECT b.book_id,a.author_id FROM books b JOIN authors a ON a.author_name='Tác giả Demo B' WHERE b.isbn='DEMO-978-003';
INSERT IGNORE INTO book_copies (book_id,barcode,copy_status,acquired_at,notes)
SELECT b.book_id,'DEMO-BC-001','AVAILABLE',CURRENT_DATE,'Bản hư cấu cho kiểm thử'
FROM books b WHERE b.isbn='DEMO-978-001';
INSERT IGNORE INTO book_copies (book_id,barcode,copy_status,acquired_at,notes)
SELECT b.book_id,'DEMO-BC-002','AVAILABLE',CURRENT_DATE,'Bản hư cấu cho kiểm thử'
FROM books b WHERE b.isbn='DEMO-978-001';
INSERT IGNORE INTO book_copies (book_id,barcode,copy_status,acquired_at,notes)
SELECT b.book_id,'DEMO-BC-003','AVAILABLE',CURRENT_DATE,'Bản hư cấu cho kiểm thử'
FROM books b WHERE b.isbn='DEMO-978-002';
INSERT IGNORE INTO book_copies (book_id,barcode,copy_status,acquired_at,notes)
SELECT b.book_id,'DEMO-BC-004','AVAILABLE',CURRENT_DATE,'Bản hư cấu cho kiểm thử'
FROM books b WHERE b.isbn='DEMO-978-003';

INSERT INTO suppliers (supplier_name,contact_name,phone,email,address,is_active) VALUES
  ('Nhà cung cấp Demo','Liên hệ Demo','0900000000','supplier@example.test','Địa chỉ hư cấu',TRUE)
ON DUPLICATE KEY UPDATE contact_name=VALUES(contact_name),phone=VALUES(phone),email=VALUES(email),address=VALUES(address),is_active=TRUE;
