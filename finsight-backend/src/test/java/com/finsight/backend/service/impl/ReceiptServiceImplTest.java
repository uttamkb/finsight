package com.finsight.backend.service.impl;

import com.finsight.backend.entity.Receipt;
import com.finsight.backend.repository.ReceiptRepository;
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
    private TrainingDataService trainingDataService;

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

        Receipt result = receiptService.updateReceipt(1L, updateData);

        // Verify synchronous DB update
        assertEquals("New Vendor", result.getVendor());
        assertEquals(BigDecimal.valueOf(20.0), result.getAmount());
        assertEquals("VERIFIED", result.getStatus());
        verify(receiptRepository).save(sampleReceipt);

        // Verify asynchronous harvesting trigger
        verify(trainingDataService).harvestAsync(sampleReceipt);
    }
}
