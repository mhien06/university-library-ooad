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
