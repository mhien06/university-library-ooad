USE university_library;

ALTER TABLE library_cards ADD COLUMN request_reason VARCHAR(500) NULL AFTER issue_type;
