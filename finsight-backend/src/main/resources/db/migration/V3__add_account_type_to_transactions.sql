-- V3__add_account_type_to_transactions.sql
-- Adds support for multiple account types: MAINTENANCE, CORPUS, SINKING_FUND

ALTER TABLE bank_transactions ADD COLUMN account_type VARCHAR(50) DEFAULT 'MAINTENANCE';

-- Update existing records to MAINTENANCE (though the default handled it, it's good practice)
UPDATE bank_transactions SET account_type = 'MAINTENANCE' WHERE account_type IS NULL;
