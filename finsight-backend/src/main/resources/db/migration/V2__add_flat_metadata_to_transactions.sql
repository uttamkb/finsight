-- V2: Add flat-based transaction metadata to bank_transactions
ALTER TABLE bank_transactions ADD COLUMN vendor_normalized TEXT;
ALTER TABLE bank_transactions ADD COLUMN vendor_type TEXT;
ALTER TABLE bank_transactions ADD COLUMN block TEXT;
ALTER TABLE bank_transactions ADD COLUMN floor TEXT;
ALTER TABLE bank_transactions ADD COLUMN flat_number TEXT;
ALTER TABLE bank_transactions ADD COLUMN sub_category TEXT;
