package com.finsight.backend;

import com.finsight.backend.entity.AppConfig;
import com.finsight.backend.repository.AppConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Runs at application startup to bootstrap app_config with credentials
 * from well-known file locations, if the database values are empty.
 *
 * Search order for service-account.json:
 *   1. ./service-account.json   (working directory — same as DB file)
 *   2. ../service-account.json  (parent of working directory)
 */
@Component
public class ConfigBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ConfigBootstrapRunner.class);
    private static final String TENANT_ID = "local_tenant";

    private static final List<String> SERVICE_ACCOUNT_SEARCH_PATHS = List.of(
            "service-account.json",
            "../service-account.json",
            "../../service-account.json"
    );

    private final AppConfigRepository repository;

    public ConfigBootstrapRunner(AppConfigRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(ApplicationArguments args) {
        AppConfig config = repository.findByTenantId(TENANT_ID).orElseGet(() -> {
            AppConfig c = new AppConfig();
            c.setApartmentName("My Apartment");
            return c;
        });

        // Auto-load service account JSON if DB is empty
        if (config.getServiceAccountJson() == null || config.getServiceAccountJson().isBlank()) {
            String json = readFirstExisting(SERVICE_ACCOUNT_SEARCH_PATHS);
            if (json != null) {
                config.setServiceAccountJson(json);
                repository.save(config);
                log.info("✅ Auto-loaded service-account.json into app_config (DB was empty).");
            } else {
                log.warn("⚠️  service-account.json not found in known locations. " +
                         "Google Forms integration will be unavailable until configured in Settings.");
            }
        } else {
            log.info("✅ Service account JSON already configured in DB — skipping auto-load.");
        }
    }

    private String readFirstExisting(List<String> paths) {
        for (String relativePath : paths) {
            try {
                Path p = Paths.get(relativePath).toAbsolutePath().normalize();
                if (Files.exists(p)) {
                    String content = Files.readString(p).trim();
                    if (!content.isBlank()) {
                        log.info("Found service-account.json at: {}", p);
                        return content;
                    }
                }
            } catch (Exception e) {
                log.debug("Could not read {}: {}", relativePath, e.getMessage());
            }
        }
        return null;
    }
}
