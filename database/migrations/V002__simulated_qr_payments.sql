USE university_library;

-- The use cases include a clearly marked simulated QR payment flow.
-- A null receiver denotes a system-confirmed simulated transaction; cash
-- collection at the counter continues to require a staff receiver.
ALTER TABLE card_payments
  MODIFY COLUMN payment_method ENUM('CASH','BANK_TRANSFER','QR_SIMULATED','OTHER') NOT NULL DEFAULT 'CASH',
  MODIFY COLUMN received_by BIGINT NULL;

ALTER TABLE violation_payments
  MODIFY COLUMN payment_method ENUM('CASH','BANK_TRANSFER','QR_SIMULATED','OTHER') NOT NULL DEFAULT 'CASH',
  MODIFY COLUMN received_by BIGINT NULL;
