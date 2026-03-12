#!/bin/bash
# script to clear all transaction data for new test runs

DB_PATH="/Users/uttamkumar_barik/Documents/Antigravity/java/finsight-backend/finsight.db"

if [ ! -f "$DB_PATH" ]; then
    echo "Database file not found at $DB_PATH"
    exit 1
fi

echo "Clearing transaction data..."
sqlite3 "$DB_PATH" "DELETE FROM receipts;" 2>/dev/null || true
sqlite3 "$DB_PATH" "DELETE FROM bank_statements;" 2>/dev/null || true
sqlite3 "$DB_PATH" "DELETE FROM vendor_records;" 2>/dev/null || true

echo "Transaction data cleared successfully. Application is ready for a fresh sync."
