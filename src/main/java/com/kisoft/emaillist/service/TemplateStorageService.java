package com.kisoft.emaillist.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

@Service
public class TemplateStorageService {
    private static final Logger log = LoggerFactory.getLogger(TemplateStorageService.class);

    @Value("${app.templates.dir:templates}")
    private String templatesDir;

    @Value("${app.templates.filename-pattern:slot-%d.json}")
    private String filenamePattern;

    /** Ensures the templates directory exists */
    private Path ensureDir() throws IOException {
        Path base = Paths.get(System.getProperty("user.dir"), templatesDir).toAbsolutePath().normalize();
        if (!Files.exists(base)) {
            Files.createDirectories(base);
            log.info("Created templates directory: {}", base);
        }
        return base;
    }

    private Path resolveSlotPath(int slot) throws IOException {
        Path base = ensureDir();
        String fname = String.format(filenamePattern, slot);
        return base.resolve(fname);
    }

    public void saveTemplate(int slot, String subject, String htmlContent) throws IOException {
        Path file = resolveSlotPath(slot);
        String json = toJson(subject, htmlContent);
        Files.writeString(file, json, StandardCharsets.UTF_8);
        log.info("Saved template slot {} to {} ({} bytes)", slot, file, json.length());
    }

    public Map<String, String> loadTemplate(int slot) throws IOException {
        Path file = resolveSlotPath(slot);
        if (!Files.exists(file)) {
            return Map.of("subject", "", "htmlContent", "");
        }
        String json = Files.readString(file, StandardCharsets.UTF_8);
        Map<String, String> tpl = fromJson(json);
        log.info("Loaded template slot {} from {} ({} bytes)", slot, file, json.length());
        return tpl;
    }

    private String toJson(String subject, String htmlContent) {
        String s = subject == null ? "" : subject;
        String h = htmlContent == null ? "" : htmlContent;
        // minimal JSON without external libs
        return "{" +
            "\"subject\":\"" + escapeJson(s) + "\"," +
            "\"htmlContent\":\"" + escapeJson(h) + "\"" +
            "}";
    }

    private Map<String, String> fromJson(String json) {
        // naive parse: expect keys subject and htmlContent
        String s = extractJsonValue(json, "subject");
        String h = extractJsonValue(json, "htmlContent");
        return Map.of("subject", s, "htmlContent", h);
    }

    private String escapeJson(String in) {
        return in
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r");
    }

    private String extractJsonValue(String json, String key) {
        int i = json.indexOf("\"" + key + "\"");
        if (i < 0) return "";
        int colon = json.indexOf(":", i);
        int start = json.indexOf("\"", colon + 1);
        int end = json.indexOf("\"", start + 1);
        if (start < 0 || end < 0) return "";
        String raw = json.substring(start + 1, end);
        return raw
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\\"", "\"")
            .replace("\\\\", "\\");
    }
}

