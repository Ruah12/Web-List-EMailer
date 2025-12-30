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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/config")
public class ConfigController {

    private static final Logger log = LoggerFactory.getLogger(ConfigController.class);

    private final Environment env;
    private final StringEncryptor encryptor;

    @Value("${spring.application.name:Web-List-EMailer}")
    private String appName;

    public ConfigController(Environment env, StringEncryptor encryptor) {
        this.env = env;
        this.encryptor = encryptor;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> getConfig() {
        Map<String, Object> cfg = new HashMap<>();
        // SMTP
        cfg.put("spring.mail.host", env.getProperty("spring.mail.host", ""));
        cfg.put("spring.mail.port", env.getProperty("spring.mail.port", ""));
        cfg.put("spring.mail.username", env.getProperty("spring.mail.username", ""));
        // For password, return masked flag and whether it was encrypted
        String mailPassword = env.getProperty("spring.mail.password", "");
        boolean mailPwdEncrypted = mailPassword != null && mailPassword.startsWith("ENC(");
        cfg.put("spring.mail.password.masked", true);
        cfg.put("spring.mail.password.encrypted", mailPwdEncrypted);

        // Sender
        cfg.put("mail.from", env.getProperty("mail.from", ""));
        cfg.put("mail.from.name", env.getProperty("mail.from.name", ""));

        // Editor & templates
        cfg.put("app.editor.default.text.color", env.getProperty("app.editor.default.text.color", "white"));
        cfg.put("app.template.slots", env.getProperty("app.template.slots", "5"));

        // Facebook
        cfg.put("facebook.enabled", env.getProperty("facebook.enabled", "false"));
        cfg.put("facebook.email", env.getProperty("facebook.email", ""));
        String fbPassword = env.getProperty("facebook.password", "");
        boolean fbPwdEncrypted = fbPassword != null && fbPassword.startsWith("ENC(");
        cfg.put("facebook.password.masked", true);
        cfg.put("facebook.password.encrypted", fbPwdEncrypted);
        cfg.put("facebook.page.id", env.getProperty("facebook.page.id", ""));
        String fbAccess = env.getProperty("facebook.access.token", "");
        boolean fbAccessEncrypted = fbAccess != null && fbAccess.startsWith("ENC(");
        cfg.put("facebook.access.token.masked", true);
        cfg.put("facebook.access.token.encrypted", fbAccessEncrypted);

        // Logging
        cfg.put("logging.config", env.getProperty("logging.config", "classpath:logback-spring.xml"));

        // App name
        cfg.put("spring.application.name", appName);

        return ResponseEntity.ok(cfg);
    }

     /**
     * Update configuration values. Sensitive fields are encrypted server-side and persisted
     * to an external application-local.properties in the working directory.
     *
     * Configuration changes are saved to the file and will be loaded on the next restart
     * via spring.config.import=optional:file:${user.dir}/application-local.properties
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

