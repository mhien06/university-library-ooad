ALTER TABLE loan_items
  MODIFY item_condition_on_return ENUM('GOOD','WORN','DAMAGED','LOST') NULL;
