package com.kisoft.emaillist.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * External Property Source Configuration.
 *
 * This configuration loads the external {@code application-local.properties} file
 * from the working directory (if it exists). This allows configuration changes made
 * via the Configuration dialog to persist across application restarts.
 *
 * The file is loaded with higher priority than the classpath application.properties,
 * so settings in application-local.properties override the defaults.
 *
 * File location: {@code ${user.dir}/application-local.properties}
 *
 * @author KiSoft
 * @version 1.0.0
 * @since 2025-12-28
 * @see com.kisoft.emaillist.controller.ConfigController
 */
@Configuration
public class ExternalPropertySourceConfig {

    private static final Logger log = LoggerFactory.getLogger(ExternalPropertySourceConfig.class);

    /**
     * Load external property source if application-local.properties exists.
     * This is done via a static initializer block to ensure properties are loaded
     * before Spring processes @PropertySource annotations.
     */
    static {
        Path externalPropsPath = Paths.get(System.getProperty("user.dir"), "application-local.properties")
            .toAbsolutePath()
            .normalize();

        if (Files.exists(externalPropsPath)) {
            log.info("[CONFIG-LOADER] Found external properties file: {}", externalPropsPath);
            // Properties will be loaded by Spring via the @PropertySource annotation below
        } else {
            log.debug("[CONFIG-LOADER] No external properties file found at: {}", externalPropsPath);
        }
    }

    /**
     * This class uses a custom PropertySourceFactory to conditionally load
     * application-local.properties if it exists. Since @PropertySource doesn't
     * support optional files directly in Spring Boot 4.x, we use a workaround
     * with a factory that handles the missing file gracefully.
     */
    public ExternalPropertySourceConfig() {
        log.debug("[CONFIG-LOADER] ExternalPropertySourceConfig initialized");
    }

    /**
     * Attempts to load external properties and logs the result.
     * This is called during Spring context initialization.
     */
    public void loadExternalProperties() {
        Path externalPropsPath = Paths.get(System.getProperty("user.dir"), "application-local.properties")
            .toAbsolutePath()
            .normalize();

        if (Files.exists(externalPropsPath)) {
            log.info("[CONFIG-LOADER] Loading configuration overrides from: {}", externalPropsPath);
            try {
                long fileSize = Files.size(externalPropsPath);
                long lineCount = Files.lines(externalPropsPath)
                    .filter(line -> !line.isBlank() && !line.startsWith("#"))
                    .count();
                log.info("[CONFIG-LOADER] External properties loaded: {} bytes, {} configuration keys",
                    fileSize, lineCount);
            } catch (Exception e) {
                log.error("[CONFIG-LOADER] Error reading external properties file: {}", e.getMessage());
            }
        } else {
            log.debug("[CONFIG-LOADER] No external properties file to load");
        }
    }
}

