package com.finsight.backend.service;

import com.finsight.backend.entity.AppConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;

public interface AppConfigService {
    @Cacheable(value = "settings", key = "'global'")
    AppConfig getConfig();

    @CacheEvict(value = "settings", key = "'global'")
    AppConfig saveConfig(AppConfig config);
}
