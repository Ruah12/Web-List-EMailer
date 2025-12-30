package com.kisoft.emaillist.controller;

import org.jasypt.encryption.StringEncryptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Configuration REST Controller - Manages application configuration via REST API.
 * This controller provides endpoints to read and update application configuration
 * settings at runtime. Configuration changes are persisted to an external
 * {@code application-local.properties} file in the working directory.
 * <p><b>REST API Endpoints:</b>
 * {@code GET /api/config} - Retrieve current configuration (sensitive values masked),
 * {@code POST /api/config} - Update configuration (sensitive values encrypted).
 * <p><b>Configuration Categories:</b>
 * SMTP Settings: {@code spring.mail.host}, {@code spring.mail.port},
 * {@code spring.mail.username}, {@code spring.mail.password};
 * Sender Settings: {@code mail.from}, {@code mail.from.name};
 * Editor Settings: {@code app.editor.default.text.color}, {@code app.template.slots};
 * Facebook Settings: {@code facebook.enabled}, {@code facebook.email},
 * {@code facebook.password}, {@code facebook.page.id}, {@code facebook.access.token};
 * Logging Settings: {@code logging.config}.
 * <p><b>Security:</b> Passwords and tokens are never returned in plaintext.
 * Sensitive values are encrypted using Jasypt before persistence.
 * Masked flags indicate whether values are encrypted.
 * <p><b>Persistence:</b> Configuration is saved to {@code ${user.dir}/application-local.properties}.
 * Changes require an application restart to take effect.
 * @author KiSoft
 * @version 1.0.0
 * @since 2025-12-28
 * @see com.kisoft.emaillist.config.ExternalPropertySourceConfig
 * @see org.jasypt.encryption.StringEncryptor
 */
@RestController
@RequestMapping("/api/config")
public class ConfigController {

    private static final Logger log = LoggerFactory.getLogger(ConfigController.class);

    private final Environment env;
    private final StringEncryptor encryptor;

    @Value("${spring.application.name:Web-List-EMailer}")
    private String appName;

    @Value("${app.version:0.0.14-SNAPSHOT}")
    private String appVersion;

    @Value("${app.copyright:© 2025 KiSoft. All rights reserved.}")
    private String appCopyright;

    public ConfigController(Environment env, StringEncryptor encryptor) {
        this.env = env;
        this.encryptor = encryptor;
    }

    /**
     * Retrieves application version information for the About dialog.
     * Returns the application name, version (from pom.xml), and copyright notice.
     * @return ResponseEntity containing version info map
     */
    @GetMapping(value = "/version", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> getVersion() {
        Map<String, String> versionInfo = new HashMap<>();
        versionInfo.put("name", appName);
        versionInfo.put("version", appVersion);
        versionInfo.put("copyright", appCopyright);
        versionInfo.put("description", "Email Mass Sender Application");
        log.debug("[CONFIG-API] Version info requested: {} v{}", appName, appVersion);
        return ResponseEntity.ok(versionInfo);
    }

    /**
     * Retrieves current application configuration.
     * Returns all configurable properties with sensitive values masked.
     * Password and token fields return only a masked indicator and encryption status.
     * First checks for saved (but not yet applied) values in application-local.properties,
     * then falls back to environment properties.
     * @return ResponseEntity containing configuration map with masked sensitive values
     */
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> getConfig() {
        Map<String, Object> cfg = new HashMap<>();

        // Load local properties file to get saved (but not yet applied) values
        Properties localProps = loadLocalProperties();

        // SMTP
        cfg.put("spring.mail.host", getPropertyWithOverride(localProps, "spring.mail.host", ""));
        cfg.put("spring.mail.port", getPropertyWithOverride(localProps, "spring.mail.port", ""));
        cfg.put("spring.mail.username", getPropertyWithOverride(localProps, "spring.mail.username", ""));
        // For password, return masked flag and whether it was encrypted
        String mailPassword = getPropertyWithOverride(localProps, "spring.mail.password", "");
        boolean mailPwdEncrypted = mailPassword != null && mailPassword.startsWith("ENC(");
        cfg.put("spring.mail.password.masked", true);
        cfg.put("spring.mail.password.encrypted", mailPwdEncrypted);

        // Sender
        cfg.put("mail.from", getPropertyWithOverride(localProps, "mail.from", ""));
        cfg.put("mail.from.name", getPropertyWithOverride(localProps, "mail.from.name", ""));

        // Editor & templates
        cfg.put("app.editor.default.text.color", getPropertyWithOverride(localProps, "app.editor.default.text.color", "white"));
        cfg.put("app.template.slots", getPropertyWithOverride(localProps, "app.template.slots", "5"));

        // Facebook
        cfg.put("facebook.enabled", getPropertyWithOverride(localProps, "facebook.enabled", "false"));
        cfg.put("facebook.email", getPropertyWithOverride(localProps, "facebook.email", ""));
        String fbPassword = getPropertyWithOverride(localProps, "facebook.password", "");
        boolean fbPwdEncrypted = fbPassword != null && fbPassword.startsWith("ENC(");
        cfg.put("facebook.password.masked", true);
        cfg.put("facebook.password.encrypted", fbPwdEncrypted);
        cfg.put("facebook.page.id", getPropertyWithOverride(localProps, "facebook.page.id", ""));
        String fbAccess = getPropertyWithOverride(localProps, "facebook.access.token", "");
        boolean fbAccessEncrypted = fbAccess != null && fbAccess.startsWith("ENC(");
        cfg.put("facebook.access.token.masked", true);
        cfg.put("facebook.access.token.encrypted", fbAccessEncrypted);

        // Logging
        cfg.put("logging.config", getPropertyWithOverride(localProps, "logging.config", "classpath:logback-spring.xml"));

        // App name
        cfg.put("spring.application.name", appName);

        return ResponseEntity.ok(cfg);
    }

    /**
     * Gets the path to the local properties file.
     * Package-private for testability - tests can mock this method.
     * @return Path to application-local.properties file
     */
    Path getLocalPropertiesPath() {
        return Paths.get(System.getProperty("user.dir"), "application-local.properties")
            .toAbsolutePath()
            .normalize();
    }

    /**
     * Loads the local properties file if it exists.
     * Package-private for testability.
     * @return Properties object with local overrides, or empty Properties if file doesn't exist
     */
    Properties loadLocalProperties() {
        Properties props = new Properties();
        Path external = getLocalPropertiesPath();
        if (Files.exists(external)) {
            try {
                String content = Files.readString(external, StandardCharsets.UTF_8);
                props.load(new StringReader(content));
                log.debug("[CONFIG-API] Loaded {} properties from local file", props.size());
            } catch (IOException e) {
                log.warn("[CONFIG-API] Failed to read local properties file: {}", e.getMessage());
            }
        }
        return props;
    }

    /**
     * Gets a property value, preferring the local override if present.
     * @param localProps Local properties loaded from file
     * @param key Property key
     * @param defaultVal Default value if not found
     * @return Property value from local file, environment, or default
     */
    private String getPropertyWithOverride(Properties localProps, String key, String defaultVal) {
        // First check local properties file (saved but not applied values)
        String localVal = localProps.getProperty(key);
        if (localVal != null) {
            return localVal;
        }
        // Fall back to environment properties
        return env.getProperty(key, defaultVal);
    }

    /**
     * Update configuration values. Sensitive fields are encrypted server-side and persisted
     * to an external application-local.properties in the working directory.
     * Configuration changes are saved to the file and will be loaded on the next restart
     * via {@code spring.config.import=optional:file:${user.dir}/application-local.properties}.
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> updateConfig(@RequestBody Map<String, String> request) {
        log.info("[CONFIG-API] Configuration update request received with {} keys", request.size());

        // Prepare property map to write
        Map<String, String> out = new HashMap<>();

        // SMTP
        putIfPresent(out, request, "spring.mail.host");
        putIfPresent(out, request, "spring.mail.port");
        putIfPresent(out, request, "spring.mail.username");
        handleSecret(out, request, "spring.mail.password");

        // Sender
        putIfPresent(out, request, "mail.from");
        putIfPresent(out, request, "mail.from.name");

        // Editor & templates
        putIfPresent(out, request, "app.editor.default.text.color");
        putIfPresent(out, request, "app.template.slots");

        // Facebook
        putIfPresent(out, request, "facebook.enabled");
        putIfPresent(out, request, "facebook.email");
        handleSecret(out, request, "facebook.password");
        putIfPresent(out, request, "facebook.page.id");
        handleSecret(out, request, "facebook.access.token");

        // Logging
        putIfPresent(out, request, "logging.config");

        // Persist to external file in working directory
        Path external = Paths.get(System.getProperty("user.dir"), "application-local.properties")
            .toAbsolutePath()
            .normalize();

        try {
            StringBuilder sb = new StringBuilder();
            sb.append("# ").append(appName).append(" - Local Overrides\n");
            sb.append("# Generated at ").append(java.time.Instant.now()).append("\n");
            sb.append("# This file overrides settings from application.properties and is reloaded on application restart\n\n");
            out.forEach((k, v) -> {
                sb.append(k).append("=").append(v).append("\n");
                log.debug("[CONFIG-API] Will persist: {} = {}", k,
                    v.length() > 50 ? v.substring(0, 50) + "..." : v);
            });

            Files.writeString(external, sb.toString(), StandardCharsets.UTF_8);
            log.info("[CONFIG-API] Configuration saved successfully to: {}", external);
            log.info("[CONFIG-API] Saved {} configuration keys ({} bytes)", out.size(),
                sb.toString().getBytes(StandardCharsets.UTF_8).length);
            log.info("[CONFIG-API] NOTE: Application restart required for changes to take effect");

        } catch (IOException e) {
            log.error("[CONFIG-API] Failed to write configuration to {}: {}", external, e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "message", "Failed to save configuration: " + e.getMessage()
            ));
        }

        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    /**
     * Copies a configuration value from request to output map if present.
     * @param out Output map for persisted configuration
     * @param req Input request map from client
     * @param key Configuration key to copy
     */
    private void putIfPresent(Map<String, String> out, Map<String, String> req, String key) {
        String val = req.get(key);
        if (val != null) {
            out.put(key, val);
        }
    }

    /**
     * Encrypts a secret value using Jasypt and writes as ENC(...). If the value already
     * looks encrypted (starts with ENC()), it is kept as-is.
     */
    private void handleSecret(Map<String, String> out, Map<String, String> req, String key) {
        String val = req.get(key);
        if (val == null || val.isBlank()) {
            return;
        }
        if (val.startsWith("ENC(")) {
            out.put(key, val);
            return;
        }
        try {
            String enc = encryptor.encrypt(val);
            out.put(key, "ENC(" + enc + ")");
        } catch (Exception e) {
            log.error("Failed to encrypt secret for {}: {}", key, e.getMessage());
            // As a safety, do not write plain secret; instead, omit or write a placeholder
        }
    }
}
