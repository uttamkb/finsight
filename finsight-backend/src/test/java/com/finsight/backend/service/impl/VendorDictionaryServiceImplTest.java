package com.finsight.backend.service.impl;

import com.finsight.backend.entity.VendorDictionary;
import com.finsight.backend.repository.VendorDictionaryRepository;
import com.finsight.backend.service.AppConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VendorDictionaryServiceImplTest {

    @Mock
    private VendorDictionaryRepository repository;

    @Mock
    private AppConfigService appConfigService;

    private VendorDictionaryServiceImpl service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new VendorDictionaryServiceImpl(repository);
    }

    @Test
    void testAddVendor_NewVendor() {
        // Arrange
        String tenantId = "tenant1";
        String vendorName = "New Vendor";
        String source = "MANUAL";
        
        when(repository.existsByTenantIdAndVendorName(tenantId, vendorName)).thenReturn(false);

        // Act
        service.addVendor(tenantId, vendorName, source);

        // Assert
        ArgumentCaptor<VendorDictionary> captor = ArgumentCaptor.forClass(VendorDictionary.class);
        verify(repository).save(captor.capture());
        VendorDictionary saved = captor.getValue();
        assertEquals(tenantId, saved.getTenantId());
        assertEquals(vendorName, saved.getVendorName());
        assertEquals(source, saved.getAddedSource());
    }

    @Test
    void testAddVendor_DuplicateVendor() {
        // Arrange
        String tenantId = "tenant1";
        String vendorName = "Existing Vendor";
        
        when(repository.existsByTenantIdAndVendorName(tenantId, vendorName))
            .thenReturn(true);

        // Act
        service.addVendor(tenantId, vendorName, "SOURCE");

        // Assert
        verify(repository, never()).save(any(VendorDictionary.class));
    }

    @Test
    void testAddVendor_UnknownVendor() {
        // Act
        service.addVendor("tenant1", "Unknown Vendor", "SOURCE");
        service.addVendor("tenant1", "", "SOURCE");
        service.addVendor("tenant1", null, "SOURCE");

        // Assert
        verify(repository, never()).save(any(VendorDictionary.class));
    }

    @Test
    void testGetVendorNames() {
        // Arrange
        String tenantId = "tenant1";
        VendorDictionary v1 = new VendorDictionary();
        v1.setVendorName("Vendor A");
        VendorDictionary v2 = new VendorDictionary();
        v2.setVendorName("Vendor B");
        
        when(repository.findAllByTenantId(tenantId)).thenReturn(Arrays.asList(v1, v2));

        // Act
        List<String> names = service.getVendorNames(tenantId);

        // Assert
        assertEquals(2, names.size());
        assertTrue(names.contains("Vendor A"));
        assertTrue(names.contains("Vendor B"));
    }

    @Test
    void testExportDictionaryToJson() throws IOException {
        // Arrange
        String tenantId = "tenant1";
        VendorDictionary v1 = new VendorDictionary();
        v1.setVendorName("Export Vendor");
        when(repository.findAllByTenantId(tenantId)).thenReturn(Collections.singletonList(v1));
        
        // Ensure scripts directory exists
        Path scriptsPath = Path.of("src/main/resources/scripts");
        Files.createDirectories(scriptsPath);
        Path jsonFile = scriptsPath.resolve("vendor_dictionary.json");

        // Act
        service.exportDictionaryToJson(tenantId);

        // Assert
        assertTrue(Files.exists(jsonFile));
        String content = Files.readString(jsonFile);
        assertTrue(content.contains("Export Vendor"));
        
        // Cleanup
        // Files.deleteIfExists(jsonFile); // Keep it for verification if needed manually
    }
}
