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
