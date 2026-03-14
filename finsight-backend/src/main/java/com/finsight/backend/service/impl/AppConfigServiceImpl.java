package com.finsight.backend.service.impl;

import com.finsight.backend.entity.AppConfig;
import com.finsight.backend.repository.AppConfigRepository;
import com.finsight.backend.service.AppConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AppConfigServiceImpl implements AppConfigService {

    private static final Logger log = LoggerFactory.getLogger(AppConfigServiceImpl.class);
    private static final String TENANT_ID = "local_tenant";

    private final AppConfigRepository repository;

    @org.springframework.beans.factory.annotation.Value("${app.sync.drive-folder-url:}")
    private String envDriveFolderUrl;

    @org.springframework.beans.factory.annotation.Value("${app.sync.service-account-json:}")
    private String envServiceAccountJson;

    @org.springframework.beans.factory.annotation.Value("${app.sync.gemini-api-key:}")
    private String envGeminiApiKey;

    public AppConfigServiceImpl(AppConfigRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "settings", key = "'app_config'")
    public AppConfig getConfig() {
        AppConfig config = repository.findByTenantId(TENANT_ID).orElseGet(() -> {
            log.info("No config found for tenant '{}', creating default.", TENANT_ID);
            AppConfig defaultConfig = new AppConfig();
            defaultConfig.setApartmentName("My Apartment");
            return repository.save(defaultConfig);
        });

        // Apply environment/property fallbacks ONLY if database values are null.
        // This allows users to explicitly save an empty string in the UI to disable a feature.
        if (config.getDriveFolderUrl() == null && envDriveFolderUrl != null && !envDriveFolderUrl.trim().isEmpty()) {
            log.info("Using environment fallback for Drive Folder URL");
            config.setDriveFolderUrl(envDriveFolderUrl);
        }
        if (config.getServiceAccountJson() == null && envServiceAccountJson != null && !envServiceAccountJson.trim().isEmpty()) {
            log.info("Using environment fallback for Service Account JSON");
            config.setServiceAccountJson(envServiceAccountJson);
        }
        if (config.getGeminiApiKey() == null && envGeminiApiKey != null && !envGeminiApiKey.trim().isEmpty()) {
            log.info("Using environment fallback for Gemini API Key");
            config.setGeminiApiKey(envGeminiApiKey);
        }

        return config;
    }

    @Override
    @CacheEvict(value = "settings", key = "'app_config'")
    public AppConfig saveConfig(AppConfig incoming) {
        AppConfig existing = repository.findByTenantId(TENANT_ID).orElse(new AppConfig());
        existing.setApartmentName(incoming.getApartmentName());
        if (incoming.getGeminiApiKey() != null) existing.setGeminiApiKey(incoming.getGeminiApiKey());
        if (incoming.getDriveFolderUrl() != null) existing.setDriveFolderUrl(incoming.getDriveFolderUrl());
        if (incoming.getServiceAccountJson() != null) existing.setServiceAccountJson(incoming.getServiceAccountJson());
        if (incoming.getCurrency() != null) existing.setCurrency(incoming.getCurrency());
        if (incoming.getOcrMode() != null) existing.setOcrMode(incoming.getOcrMode());
        if (incoming.getThemePreference() != null) existing.setThemePreference(incoming.getThemePreference());
        log.info("Saving app config for apartment: {}", existing.getApartmentName());
        return repository.save(existing);
    }
}
