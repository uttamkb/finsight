package com.finsight.backend.entity;

public enum ReconciliationStatus {
    PENDING,
    MATCHED,
    UNMATCHED,
    NO_RECEIPT_REQUIRED,
    MANUAL_REVIEW,
    DISPUTED
}
