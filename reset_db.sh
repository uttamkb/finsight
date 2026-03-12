#!/bin/bash
# script to clear all transaction data for new test runs

DB_PATH="./finsight-backend/finsight.db"

if [ ! -f "$DB_PATH" ]; then
    echo "Database file not found at $DB_PATH"
    exit 1
fi

echo "Clearing transaction data..."
sqlite3 "$DB_PATH" "DELETE FROM receipts;" 2>/dev/null || true
sqlite3 "$DB_PATH" "DELETE FROM bank_transactions;" 2>/dev/null || true
sqlite3 "$DB_PATH" "DELETE FROM vendors;" 2>/dev/null || true
sqlite3 "$DB_PATH" "DELETE FROM audit_trail;" 2>/dev/null || true
sqlite3 "$DB_PATH" "DELETE FROM forensic_anomalies;" 2>/dev/null || true
sqlite3 "$DB_PATH" "DELETE FROM survey_responses;" 2>/dev/null || true
sqlite3 "$DB_PATH" "UPDATE bank_transactions SET reconciled = 0;" 2>/dev/null || true

echo "Transaction data cleared successfully. Application is ready for a fresh sync."
