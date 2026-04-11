#!/bin/bash
# script to clear all transaction data for new test runs

DB_PATH="./finsight-backend/finsight.db"

if [ ! -f "$DB_PATH" ]; then
    echo "Database file not found at $DB_PATH"
    exit 1
fi

echo "Clearing ALL data except app_config and flyway_schema_history..."
# Purge all operational and reference tables
sqlite3 "$DB_PATH" "DELETE FROM receipts;" 2>/dev/null || true
sqlite3 "$DB_PATH" "DELETE FROM bank_transactions;" 2>/dev/null || true
sqlite3 "$DB_PATH" "DELETE FROM audit_trail;" 2>/dev/null || true
sqlite3 "$DB_PATH" "DELETE FROM forensic_anomalies;" 2>/dev/null || true
sqlite3 "$DB_PATH" "DELETE FROM vendors;" 2>/dev/null || true
sqlite3 "$DB_PATH" "DELETE FROM categories;" 2>/dev/null || true
sqlite3 "$DB_PATH" "DELETE FROM surveys;" 2>/dev/null || true
sqlite3 "$DB_PATH" "DELETE FROM survey_responses;" 2>/dev/null || true
sqlite3 "$DB_PATH" "DELETE FROM survey_insights;" 2>/dev/null || true
sqlite3 "$DB_PATH" "DELETE FROM survey_action_items;" 2>/dev/null || true
sqlite3 "$DB_PATH" "DELETE FROM feedback;" 2>/dev/null || true
sqlite3 "$DB_PATH" "DELETE FROM parser_patterns;" 2>/dev/null || true
sqlite3 "$DB_PATH" "DELETE FROM vendor_dictionary;" 2>/dev/null || true
sqlite3 "$DB_PATH" "DELETE FROM vendor_aliases;" 2>/dev/null || true
sqlite3 "$DB_PATH" "DELETE FROM category_keyword_mappings;" 2>/dev/null || true
sqlite3 "$DB_PATH" "DELETE FROM statement_uploads;" 2>/dev/null || true
sqlite3 "$DB_PATH" "DELETE FROM reconciliation_runs;" 2>/dev/null || true
sqlite3 "$DB_PATH" "DELETE FROM receipt_sync_runs;" 2>/dev/null || true

echo "Full reset complete. app_config and flyway_schema_history preserved."
