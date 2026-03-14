package com.finsight.backend.service.impl;

import com.finsight.backend.client.GoogleDriveClient;
import com.finsight.backend.entity.AppConfig;
import com.finsight.backend.entity.Receipt;
import com.finsight.backend.repository.ReceiptRepository;
import com.finsight.backend.service.AppConfigService;
import com.finsight.backend.service.TrainingDataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReceiptServiceImplTest {

    @Mock
    private ReceiptRepository receiptRepository;
    
    @Mock
    private GoogleDriveClient googleDriveClient;
    
    @Mock
    private TrainingDataService trainingDataService;
    
    @Mock
    private AppConfigService appConfigService;

    @InjectMocks
    private ReceiptServiceImpl receiptService;

    private Receipt sampleReceipt;

    @BeforeEach
    void setUp() {
        sampleReceipt = new Receipt();
        sampleReceipt.setId(1L);
        sampleReceipt.setVendor("Old Vendor");
        sampleReceipt.setAmount(BigDecimal.valueOf(10.0));
        sampleReceipt.setDriveFileId("drive-123");
    }

    @Test
    void testUpdateReceipt_HarvestsData() throws Exception {
        Receipt updateData = new Receipt();
        updateData.setVendor("New Vendor");
        updateData.setAmount(BigDecimal.valueOf(20.0));

        when(receiptRepository.findById(1L)).thenReturn(Optional.of(sampleReceipt));
        when(receiptRepository.save(any(Receipt.class))).thenAnswer(i -> i.getArgument(0));

        AppConfig mockConfig = new AppConfig();
        mockConfig.setServiceAccountJson("{}");
        when(appConfigService.getConfig()).thenReturn(mockConfig);
        when(googleDriveClient.getDriveService(anyString())).thenReturn(null); // Return null mock drive
        when(googleDriveClient.downloadFile(any(), eq("drive-123"))).thenReturn("fake_pdf".getBytes());

        Receipt result = receiptService.updateReceipt(1L, updateData);

        // Verify synchronous DB update
        assertEquals("New Vendor", result.getVendor());
        assertEquals(BigDecimal.valueOf(20.0), result.getAmount());
        assertEquals("VERIFIED", result.getStatus());
        verify(receiptRepository).save(sampleReceipt);

        // Give the background thread a moment to run
        Thread.sleep(500);

        // Verify asynchronous harvesting
        verify(trainingDataService).harvest(eq(sampleReceipt), any(byte[].class));
    }
}
