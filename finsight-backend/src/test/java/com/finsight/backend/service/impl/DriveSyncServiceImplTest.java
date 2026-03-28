package com.finsight.backend.service.impl;

import com.finsight.backend.client.GoogleDriveClient;
import com.finsight.backend.entity.AppConfig;
import com.finsight.backend.entity.ReceiptSyncRun;
import com.finsight.backend.repository.ReceiptRepository;
import com.finsight.backend.repository.ReceiptSyncRunRepository;
import com.finsight.backend.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DriveSyncServiceImplTest {

    @Mock private AppConfigService appConfigService;
    @Mock private GoogleDriveClient driveClient;
    @Mock private ReceiptRepository receiptRepository;
    @Mock private OcrService ocrService;
    @Mock private ClassificationService classificationService;
    @Mock private VendorManager vendorManager;
    @Mock private ReceiptSyncRunRepository receiptSyncRunRepository;

    @InjectMocks
    private DriveSyncServiceImpl driveSyncService;

    private final String tenantId = "test-tenant";

    @BeforeEach
    void setUp() {
    }

    @Test
    void sync_AlreadyRunning_ShouldReturnEarly() {
        // Arrange
        ReceiptSyncRun activeRun = new ReceiptSyncRun();
        activeRun.setStatus("RUNNING");
        when(receiptSyncRunRepository.findFirstByTenantIdOrderByStartedAtDesc(tenantId))
                .thenReturn(Optional.of(activeRun));

        // Act
        driveSyncService.sync(tenantId);

        // Assert
        verify(receiptSyncRunRepository, never()).save(any(ReceiptSyncRun.class));
    }

    @Test
    void sync_StartsNewRun() {
        // Arrange
        when(receiptSyncRunRepository.findFirstByTenantIdOrderByStartedAtDesc(tenantId))
                .thenReturn(Optional.empty());
        
        ReceiptSyncRun savedRun = new ReceiptSyncRun();
        savedRun.setId(1L);
        when(receiptSyncRunRepository.save(any(ReceiptSyncRun.class))).thenReturn(savedRun);
        
        // Mock appConfig to avoid crash in Async block (though we might not be able to verify async easily without a custom executor)
        AppConfig config = new AppConfig();
        config.setDriveFolderUrl("https://drive.google.com/drive/folders/folderId");
        when(appConfigService.getConfig()).thenReturn(config);

        // Act
        driveSyncService.sync(tenantId);

        // Assert
        verify(receiptSyncRunRepository).save(argThat(run -> 
            "RUNNING".equals(run.getStatus()) && 
            "INITIALIZING".equals(run.getStage()) &&
            tenantId.equals(run.getTenantId())
        ));
    }

    @Test
    void getStatus_ReturnsStatusFromLatestRun() {
        // Arrange
        ReceiptSyncRun lastRun = new ReceiptSyncRun();
        lastRun.setStatus("SUCCESS");
        lastRun.setStage("COMPLETED");
        lastRun.setProcessedFiles(5);
        lastRun.setTotalFiles(5);
        lastRun.setCompletedAt(LocalDateTime.now());
        
        when(receiptSyncRunRepository.findFirstByTenantIdOrderByStartedAtDesc(tenantId))
                .thenReturn(Optional.of(lastRun));

        // Act
        var status = driveSyncService.getStatus(tenantId);

        // Assert
        assertEquals("SUCCESS", status.getStatus());
        assertEquals("COMPLETED", status.getStage());
        assertEquals(5, status.getProcessedFiles());
    }
    
    private void assertEquals(Object expected, Object actual) {
        if (expected == null && actual == null) return;
        if (expected != null && expected.equals(actual)) return;
        throw new AssertionError("Expected " + expected + " but was " + actual);
    }
}
