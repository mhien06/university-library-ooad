CREATE DATABASE IF NOT EXISTS university_library
  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE university_library;

-- School identities: each student, postgraduate learner, or lecturer has one base record.
CREATE TABLE departments (
  department_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  department_code VARCHAR(30) NOT NULL UNIQUE,
  department_name VARCHAR(160) NOT NULL,
  is_active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE school_members (
  member_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  member_code VARCHAR(40) NOT NULL UNIQUE,
  member_type ENUM('STUDENT','LEARNER','LECTURER') NOT NULL,
  full_name VARCHAR(160) NOT NULL,
  date_of_birth DATE,
  email VARCHAR(190) UNIQUE,
  phone VARCHAR(30),
  department_id BIGINT,
  institution_status ENUM('ACTIVE','ON_LEAVE','GRADUATED','RETIRED','INACTIVE') NOT NULL DEFAULT 'ACTIVE',
  valid_from DATE,
  valid_until DATE,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_member_department FOREIGN KEY (department_id) REFERENCES departments(department_id),
  INDEX ix_members_name (full_name),
  INDEX ix_members_type_status (member_type, institution_status)
);

CREATE TABLE students (
  member_id BIGINT PRIMARY KEY,
  program_name VARCHAR(160) NOT NULL,
  class_code VARCHAR(40),
  academic_year VARCHAR(20),
  expected_graduation DATE,
  CONSTRAINT fk_student_member FOREIGN KEY (member_id) REFERENCES school_members(member_id) ON DELETE CASCADE
);

CREATE TABLE learners (
  member_id BIGINT PRIMARY KEY,
  program_name VARCHAR(160) NOT NULL,
  cohort_code VARCHAR(40),
  training_level VARCHAR(80),
  expected_completion DATE,
  CONSTRAINT fk_learner_member FOREIGN KEY (member_id) REFERENCES school_members(member_id) ON DELETE CASCADE
);

CREATE TABLE lecturers (
  member_id BIGINT PRIMARY KEY,
  academic_title VARCHAR(100),
  specialization VARCHAR(160),
  employment_start DATE,
  employment_end DATE,
  CONSTRAINT fk_lecturer_member FOREIGN KEY (member_id) REFERENCES school_members(member_id) ON DELETE CASCADE
);

CREATE TABLE staff_profiles (
  staff_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  staff_code VARCHAR(40) NOT NULL UNIQUE,
  full_name VARCHAR(160) NOT NULL,
  email VARCHAR(190) NOT NULL UNIQUE,
  phone VARCHAR(30),
  job_title ENUM('LIBRARIAN','WAREHOUSE','ADMIN') NOT NULL,
  employment_status ENUM('ACTIVE','INACTIVE') NOT NULL DEFAULT 'ACTIVE',
  hired_on DATE,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE accounts (
  account_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(80) NOT NULL UNIQUE,
  password_hash VARCHAR(100) NOT NULL,
  member_id BIGINT UNIQUE,
  staff_id BIGINT UNIQUE,
  account_status ENUM('ACTIVE','LOCKED') NOT NULL DEFAULT 'ACTIVE',
  lock_reason VARCHAR(500),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  last_login_at TIMESTAMP NULL,
  CONSTRAINT ck_account_owner CHECK ((member_id IS NOT NULL) <> (staff_id IS NOT NULL)),
  CONSTRAINT fk_account_member FOREIGN KEY (member_id) REFERENCES school_members(member_id),
  CONSTRAINT fk_account_staff FOREIGN KEY (staff_id) REFERENCES staff_profiles(staff_id)
);

CREATE TABLE account_roles (
  account_id BIGINT NOT NULL,
  role ENUM('READER','LIBRARIAN','WAREHOUSE','ADMIN') NOT NULL,
  granted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (account_id, role),
  CONSTRAINT fk_role_account FOREIGN KEY (account_id) REFERENCES accounts(account_id) ON DELETE CASCADE
);

CREATE TABLE employee_assignments (
  assignment_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  staff_id BIGINT NOT NULL,
  title VARCHAR(160) NOT NULL,
  details TEXT,
  priority ENUM('LOW','NORMAL','HIGH','URGENT') NOT NULL DEFAULT 'NORMAL',
  due_at DATE,
  assignment_status ENUM('OPEN','IN_PROGRESS','DONE','CANCELLED') NOT NULL DEFAULT 'OPEN',
  assigned_by BIGINT NOT NULL,
  assigned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_assignment_staff FOREIGN KEY (staff_id) REFERENCES staff_profiles(staff_id),
  CONSTRAINT fk_assignment_admin FOREIGN KEY (assigned_by) REFERENCES staff_profiles(staff_id)
);

CREATE TABLE library_cards (
  card_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  card_number VARCHAR(40) NOT NULL UNIQUE,
  member_id BIGINT NOT NULL,
  issue_type ENUM('FIRST_ISSUE','REISSUE') NOT NULL,
  request_reason VARCHAR(500),
  fee DECIMAL(12,2) NOT NULL,
  card_status ENUM('PENDING_PAYMENT','PENDING_HANDOVER','ACTIVE','LOCKED','EXPIRED','CANCELLED') NOT NULL DEFAULT 'PENDING_PAYMENT',
  requested_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  handed_over_at TIMESTAMP NULL,
  handed_over_by BIGINT,
  locked_reason VARCHAR(500),
  CONSTRAINT fk_card_member FOREIGN KEY (member_id) REFERENCES school_members(member_id),
  CONSTRAINT fk_card_handover_staff FOREIGN KEY (handed_over_by) REFERENCES staff_profiles(staff_id),
  INDEX ix_cards_member_status (member_id, card_status)
);

CREATE TABLE card_payments (
  card_payment_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  card_id BIGINT NOT NULL,
  amount DECIMAL(12,2) NOT NULL,
  paid_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  payment_method ENUM('CASH','BANK_TRANSFER','QR_SIMULATED','OTHER') NOT NULL DEFAULT 'CASH',
  receipt_number VARCHAR(50) NOT NULL UNIQUE,
  received_by BIGINT NULL,
  CONSTRAINT fk_card_payment_card FOREIGN KEY (card_id) REFERENCES library_cards(card_id),
  CONSTRAINT fk_card_payment_staff FOREIGN KEY (received_by) REFERENCES staff_profiles(staff_id)
);

CREATE TABLE categories (
  category_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  category_name VARCHAR(120) NOT NULL UNIQUE,
  description VARCHAR(500),
  is_active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE authors (
  author_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  author_name VARCHAR(160) NOT NULL UNIQUE,
  biography TEXT
);

CREATE TABLE publishers (
  publisher_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  publisher_name VARCHAR(180) NOT NULL UNIQUE,
  address VARCHAR(300),
  phone VARCHAR(30),
  email VARCHAR(190)
);

CREATE TABLE books (
  book_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  isbn VARCHAR(20) UNIQUE,
  title VARCHAR(300) NOT NULL,
  subtitle VARCHAR(300),
  category_id BIGINT,
  publisher_id BIGINT,
  publication_year SMALLINT,
  edition VARCHAR(80),
  language_code VARCHAR(10) NOT NULL DEFAULT 'vi',
  price DECIMAL(12,2) NOT NULL DEFAULT 0,
  description TEXT,
  record_status ENUM('ACTIVE','ARCHIVED') NOT NULL DEFAULT 'ACTIVE',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_book_category FOREIGN KEY (category_id) REFERENCES categories(category_id),
  CONSTRAINT fk_book_publisher FOREIGN KEY (publisher_id) REFERENCES publishers(publisher_id),
  INDEX ix_books_title (title)
);

CREATE TABLE book_authors (
  book_id BIGINT NOT NULL,
  author_id BIGINT NOT NULL,
  PRIMARY KEY (book_id, author_id),
  CONSTRAINT fk_book_author_book FOREIGN KEY (book_id) REFERENCES books(book_id),
  CONSTRAINT fk_book_author_author FOREIGN KEY (author_id) REFERENCES authors(author_id)
);

CREATE TABLE book_copies (
  copy_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  book_id BIGINT NOT NULL,
  barcode VARCHAR(50) NOT NULL UNIQUE,
  copy_status ENUM('AVAILABLE','PENDING_LOAN','ON_LOAN','RESERVED','DAMAGED','LOST','WITHDRAWN') NOT NULL DEFAULT 'AVAILABLE',
  acquired_at DATE,
  notes VARCHAR(500),
  CONSTRAINT fk_copy_book FOREIGN KEY (book_id) REFERENCES books(book_id),
  INDEX ix_copy_book_status (book_id, copy_status)
);

CREATE TABLE suppliers (
  supplier_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  supplier_name VARCHAR(180) NOT NULL,
  contact_name VARCHAR(160),
  phone VARCHAR(30),
  email VARCHAR(190),
  address VARCHAR(300),
  is_active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE supplier_requests (
  request_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  request_number VARCHAR(50) NOT NULL UNIQUE,
  supplier_id BIGINT NOT NULL,
  requested_by BIGINT NOT NULL,
  requested_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  request_status ENUM('DRAFT','SENT','CANCELLED') NOT NULL DEFAULT 'DRAFT',
  notes TEXT,
  CONSTRAINT fk_supplier_request_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers(supplier_id),
  CONSTRAINT fk_supplier_request_staff FOREIGN KEY (requested_by) REFERENCES staff_profiles(staff_id)
);

CREATE TABLE supplier_request_lines (
  request_line_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  request_id BIGINT NOT NULL,
  book_id BIGINT NOT NULL,
  requested_quantity INT NOT NULL,
  estimated_unit_cost DECIMAL(12,2) NOT NULL DEFAULT 0,
  notes VARCHAR(500),
  CONSTRAINT ck_supplier_request_quantity CHECK (requested_quantity > 0),
  CONSTRAINT fk_supplier_line_request FOREIGN KEY (request_id) REFERENCES supplier_requests(request_id) ON DELETE CASCADE,
  CONSTRAINT fk_supplier_line_book FOREIGN KEY (book_id) REFERENCES books(book_id)
);

CREATE TABLE stock_receipts (
  receipt_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  receipt_number VARCHAR(50) NOT NULL UNIQUE,
  supplier_id BIGINT,
  received_by BIGINT NOT NULL,
  received_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  receipt_status ENUM('DRAFT','RECEIVED','CANCELLED') NOT NULL DEFAULT 'DRAFT',
  notes TEXT,
  CONSTRAINT fk_receipt_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers(supplier_id),
  CONSTRAINT fk_receipt_staff FOREIGN KEY (received_by) REFERENCES staff_profiles(staff_id)
);

CREATE TABLE stock_receipt_lines (
  receipt_line_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  receipt_id BIGINT NOT NULL,
  book_id BIGINT NOT NULL,
  quantity INT NOT NULL,
  unit_cost DECIMAL(12,2) NOT NULL DEFAULT 0,
  accepted_quantity INT NOT NULL DEFAULT 0,
  notes VARCHAR(500),
  CONSTRAINT ck_receipt_quantity CHECK (quantity > 0 AND accepted_quantity >= 0 AND accepted_quantity <= quantity),
  CONSTRAINT fk_receipt_line_receipt FOREIGN KEY (receipt_id) REFERENCES stock_receipts(receipt_id) ON DELETE CASCADE,
  CONSTRAINT fk_receipt_line_book FOREIGN KEY (book_id) REFERENCES books(book_id)
);

CREATE TABLE reservations (
  reservation_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  member_id BIGINT NOT NULL,
  book_id BIGINT NOT NULL,
  requested_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  reservation_status ENUM('PENDING','APPROVED','REJECTED','READY_FOR_PICKUP','FULFILLED','CANCELLED','EXPIRED') NOT NULL DEFAULT 'PENDING',
  reviewed_by BIGINT,
  reviewed_at TIMESTAMP NULL,
  expires_at TIMESTAMP NULL,
  reserved_copy_id BIGINT,
  note VARCHAR(500),
  CONSTRAINT fk_reservation_member FOREIGN KEY (member_id) REFERENCES school_members(member_id),
  CONSTRAINT fk_reservation_book FOREIGN KEY (book_id) REFERENCES books(book_id),
  CONSTRAINT fk_reservation_reviewer FOREIGN KEY (reviewed_by) REFERENCES staff_profiles(staff_id),
  CONSTRAINT fk_reservation_copy FOREIGN KEY (reserved_copy_id) REFERENCES book_copies(copy_id),
  INDEX ix_reservation_status_time (reservation_status, requested_at)
);

CREATE TABLE loans (
  loan_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  loan_number VARCHAR(50) NOT NULL UNIQUE,
  member_id BIGINT NOT NULL,
  card_id BIGINT NOT NULL,
  borrowed_by BIGINT NOT NULL,
  borrowed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  loan_status ENUM('DRAFT','OPEN','PARTIALLY_RETURNED','CLOSED','CANCELLED') NOT NULL DEFAULT 'DRAFT',
  CONSTRAINT fk_loan_member FOREIGN KEY (member_id) REFERENCES school_members(member_id),
  CONSTRAINT fk_loan_card FOREIGN KEY (card_id) REFERENCES library_cards(card_id),
  CONSTRAINT fk_loan_librarian FOREIGN KEY (borrowed_by) REFERENCES staff_profiles(staff_id),
  INDEX ix_loan_member_status (member_id, loan_status)
);

CREATE TABLE loan_items (
  loan_item_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  loan_id BIGINT NOT NULL,
  copy_id BIGINT NOT NULL,
  due_at DATE NOT NULL,
  returned_at TIMESTAMP NULL,
  renew_count TINYINT NOT NULL DEFAULT 0,
  item_condition_on_return ENUM('GOOD','WORN','DAMAGED','LOST') NULL,
  return_processed_by BIGINT,
  CONSTRAINT ck_renew_once CHECK (renew_count BETWEEN 0 AND 1),
  CONSTRAINT fk_loan_item_loan FOREIGN KEY (loan_id) REFERENCES loans(loan_id),
  CONSTRAINT fk_loan_item_copy FOREIGN KEY (copy_id) REFERENCES book_copies(copy_id),
  CONSTRAINT fk_return_staff FOREIGN KEY (return_processed_by) REFERENCES staff_profiles(staff_id),
  INDEX ix_loan_item_due (due_at, returned_at)
);

CREATE TABLE violations (
  violation_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  loan_item_id BIGINT NOT NULL,
  violation_type ENUM('OVERDUE','DAMAGED','LOST') NOT NULL,
  assessed_amount DECIMAL(12,2) NOT NULL,
  paid_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
  description VARCHAR(1000),
  assessed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  assessed_by BIGINT NOT NULL,
  violation_status ENUM('UNPAID','PARTIALLY_PAID','PAID','WAIVED') NOT NULL DEFAULT 'UNPAID',
  CONSTRAINT fk_violation_item FOREIGN KEY (loan_item_id) REFERENCES loan_items(loan_item_id),
  CONSTRAINT fk_violation_staff FOREIGN KEY (assessed_by) REFERENCES staff_profiles(staff_id),
  INDEX ix_violation_status_date (violation_status, assessed_at)
);

CREATE TABLE violation_payments (
  violation_payment_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  violation_id BIGINT NOT NULL,
  amount DECIMAL(12,2) NOT NULL,
  paid_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  payment_method ENUM('CASH','BANK_TRANSFER','QR_SIMULATED','OTHER') NOT NULL DEFAULT 'CASH',
  receipt_number VARCHAR(50) NOT NULL UNIQUE,
  received_by BIGINT NULL,
  CONSTRAINT fk_violation_payment_violation FOREIGN KEY (violation_id) REFERENCES violations(violation_id),
  CONSTRAINT fk_violation_payment_staff FOREIGN KEY (received_by) REFERENCES staff_profiles(staff_id)
);

CREATE TABLE electronic_documents (
  document_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  title VARCHAR(300) NOT NULL,
  description TEXT,
  author_label VARCHAR(300),
  category_label VARCHAR(120),
  file_path VARCHAR(1000) NOT NULL,
  mime_type VARCHAR(120) NOT NULL,
  uploaded_by BIGINT NOT NULL,
  uploaded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  document_status ENUM('ACTIVE','ARCHIVED') NOT NULL DEFAULT 'ACTIVE',
  CONSTRAINT fk_document_staff FOREIGN KEY (uploaded_by) REFERENCES staff_profiles(staff_id),
  INDEX ix_document_title (title)
);

CREATE TABLE audit_log (
  audit_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  account_id BIGINT,
  action_name VARCHAR(120) NOT NULL,
  entity_name VARCHAR(120) NOT NULL,
  entity_id BIGINT,
  details JSON,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_audit_account FOREIGN KEY (account_id) REFERENCES accounts(account_id)
);

CREATE TABLE report_runs (
  report_run_id BIGINT PRIMARY KEY AUTO_INCREMENT,
  report_title VARCHAR(300) NOT NULL,
  report_type VARCHAR(80) NOT NULL,
  from_date DATE NULL,
  to_date DATE NULL,
  filter_text VARCHAR(500),
  snapshot_csv LONGTEXT NOT NULL,
  generated_by BIGINT NOT NULL,
  generated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_report_run_staff FOREIGN KEY (generated_by) REFERENCES staff_profiles(staff_id),
  INDEX ix_report_runs_time (generated_at)
);

-- Card policy constants and default application settings.
CREATE TABLE system_settings (
  setting_key VARCHAR(100) PRIMARY KEY,
  setting_value VARCHAR(500) NOT NULL,
  description VARCHAR(500)
);

INSERT INTO system_settings (setting_key, setting_value, description) VALUES
 ('CARD_FIRST_ISSUE_FEE','50000','First library card fee in VND'),
 ('CARD_REISSUE_FEE','20000','Replacement library card fee in VND'),
 ('LOAN_MAX_COPIES','5','Maximum active physical book copies per reader'),
 ('LOAN_PERIOD_DAYS','21','Default borrowing period'),
 ('LOAN_RENEWAL_MAX_COUNT','1','Maximum renewals per loan item'),
 ('LOAN_RENEWAL_MAX_DAYS','7','Maximum days added by one renewal'),
 ('OVERDUE_FINE_PER_DAY','5000','Overdue fine per copy per day in VND'),
 ('DAMAGED_BOOK_REPLACEMENT_RATE','0.75','Replacement fraction for damaged book'),
 ('LOST_BOOK_REPLACEMENT_RATE','1.00','Replacement fraction for lost book')
ON DUPLICATE KEY UPDATE setting_value = VALUES(setting_value);

