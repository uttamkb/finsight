package com.finsight.backend.service;

import java.util.List;

public interface VendorDictionaryService {
    void addVendor(String tenantId, String vendorName, String source);
    List<String> getVendorNames(String tenantId);
    void exportDictionaryToJson(String tenantId);
}
