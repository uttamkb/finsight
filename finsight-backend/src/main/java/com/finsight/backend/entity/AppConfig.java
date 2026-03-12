package com.finsight.backend.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;

@Entity
@Table(name = "app_config")
public class AppConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId = "local_tenant";

    @NotBlank
    @Column(name = "apartment_name", nullable = false)
    private String apartmentName;

    @Column(name = "gemini_api_key")
    private String geminiApiKey;

    @Column(name = "drive_folder_url")
    private String driveFolderUrl;

    @Column(name = "service_account_json", columnDefinition = "TEXT")
    private String serviceAccountJson;

    @Column(name = "currency")
    private String currency = "INR";

    @Column(name = "ocr_mode")
    private String ocrMode = "MODE_LOW_COST"; // MODE_LOW_COST | MODE_HYBRID | MODE_HIGH_ACCURACY

    @Column(name = "theme_preference")
    private String themePreference = "DARK"; // DARK | LIGHT

    @Column(name = "synced_at")
    private LocalDateTime syncedAt;

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getApartmentName() { return apartmentName; }
    public void setApartmentName(String apartmentName) { this.apartmentName = apartmentName; }
    public String getGeminiApiKey() { return geminiApiKey; }
    public void setGeminiApiKey(String geminiApiKey) { this.geminiApiKey = geminiApiKey; }
    public String getDriveFolderUrl() { return driveFolderUrl; }
    public void setDriveFolderUrl(String driveFolderUrl) { this.driveFolderUrl = driveFolderUrl; }
    public String getServiceAccountJson() { return serviceAccountJson; }
    public void setServiceAccountJson(String serviceAccountJson) { this.serviceAccountJson = serviceAccountJson; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getOcrMode() { return ocrMode; }
    public void setOcrMode(String ocrMode) { this.ocrMode = ocrMode; }
    public String getThemePreference() { return themePreference; }
    public void setThemePreference(String themePreference) { this.themePreference = themePreference; }
    public LocalDateTime getSyncedAt() { return syncedAt; }
    public void setSyncedAt(LocalDateTime syncedAt) { this.syncedAt = syncedAt; }
}
