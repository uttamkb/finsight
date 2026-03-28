package com.finsight.backend.service;

import com.finsight.backend.entity.AppConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;

public interface AppConfigService {
    AppConfig getConfig();

    AppConfig saveConfig(AppConfig config);
}
