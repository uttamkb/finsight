package com.finsight.backend.service;

import com.finsight.backend.dto.SyncStatus;

public interface DriveSyncService {
    void sync(String tenantId);
    SyncStatus getStatus(String tenantId);
}
