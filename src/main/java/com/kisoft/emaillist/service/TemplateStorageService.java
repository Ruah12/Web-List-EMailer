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

/**
 * Template Storage Service - Low-level persistence for email templates.
 *
 * <p>This service provides basic file I/O operations for template persistence.
 * It handles reading and writing template JSON files to the configured templates
 * directory. For higher-level template operations (normalization, validation),
 * use {@link TemplateService} instead.</p>
 *
 * <h3>Configuration:</h3>
 * <ul>
 *   <li>{@code app.templates.dir} - Templates directory name (default: {@code templates})</li>
 *   <li>{@code app.templates.filename-pattern} - Filename pattern (default: {@code slot-%d.json})</li>
 * </ul>
 *
 * <h3>File Structure:</h3>
 * <p>Templates are stored as JSON files with the following structure:</p>
 * <pre>
 * {
 *   "subject": "Email Subject",
 *   "htmlContent": "HTML body content"
 * }
 * </pre>
 *
 * <h3>JSON Handling:</h3>
 * <p>This service uses minimal JSON encoding/decoding without external libraries.
 * Special characters are properly escaped: backslash, quotes, newlines, carriage returns.</p>
 *
 * @author KiSoft
 * @version 1.0.0
 * @since 2025-12-28
 * @see TemplateService
 */
@Service
public class TemplateStorageService {
    private static final Logger log = LoggerFactory.getLogger(TemplateStorageService.class);

    /** Templates directory name relative to working directory */
    @Value("${app.templates.dir:templates}")
    private String templatesDir;

    /** Filename pattern for template files (use %d for slot number) */
    @Value("${app.templates.filename-pattern:slot-%d.json}")
    private String filenamePattern;

    /**
     * Ensures the templates directory exists, creating it if necessary.
     *
     * @return Path to the templates directory
     * @throws IOException if directory creation fails
     */
    private Path ensureDir() throws IOException {
        Path base = Paths.get(System.getProperty("user.dir"), templatesDir).toAbsolutePath().normalize();
        if (!Files.exists(base)) {
            Files.createDirectories(base);
            log.info("Created templates directory: {}", base);
        }
        return base;
    }

    /**
     * Resolves the file path for a specific template slot.
     *
     * @param slot Template slot number (1-10)
     * @return Path to the template file
     * @throws IOException if directory creation fails
     */
    private Path resolveSlotPath(int slot) throws IOException {
        Path base = ensureDir();
        String fname = String.format(filenamePattern, slot);
        return base.resolve(fname);
    }

    /**
     * Saves a template to the file system.
     *
     * @param slot Template slot number (1-10)
     * @param subject Email subject line
     * @param htmlContent HTML body content
     * @throws IOException if file writing fails
     */
    public void saveTemplate(int slot, String subject, String htmlContent) throws IOException {
        Path file = resolveSlotPath(slot);
        String json = toJson(subject, htmlContent);
        Files.writeString(file, json, StandardCharsets.UTF_8);
        log.info("Saved template slot {} to {} ({} bytes)", slot, file, json.length());
    }

    /**
     * Loads a template from the file system.
     *
     * @param slot Template slot number (1-10)
     * @return Map containing "subject" and "htmlContent" keys (empty strings if file doesn't exist)
     * @throws IOException if file reading fails
     */
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

    /**
     * Converts template data to JSON string format.
     *
     * @param subject Email subject (null-safe)
     * @param htmlContent HTML content (null-safe)
     * @return JSON string representation
     */
    private String toJson(String subject, String htmlContent) {
        String s = subject == null ? "" : subject;
        String h = htmlContent == null ? "" : htmlContent;
        // minimal JSON without external libs
        return "{" +
            "\"subject\":\"" + escapeJson(s) + "\"," +
            "\"htmlContent\":\"" + escapeJson(h) + "\"" +
            "}";
    }

    /**
     * Parses JSON string to extract template data.
     *
     * @param json JSON string to parse
     * @return Map with "subject" and "htmlContent" keys
     */
    private Map<String, String> fromJson(String json) {
        // naive parse: expect keys subject and htmlContent
        String s = extractJsonValue(json, "subject");
        String h = extractJsonValue(json, "htmlContent");
        return Map.of("subject", s, "htmlContent", h);
    }

    /**
     * Escapes special characters for JSON string encoding.
     *
     * <p>Handles: backslash, double-quote, newline, carriage return.</p>
     *
     * @param in Input string to escape
     * @return JSON-safe escaped string
     */
    private String escapeJson(String in) {
        return in
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r");
    }

    /**
     * Extracts a string value from a JSON object by key name.
     *
     * <p>Uses simple string parsing (not a full JSON parser).
     * Handles basic escape sequences in the extracted value.</p>
     *
     * @param json JSON string to search
     * @param key Key name to find
     * @return Extracted and unescaped value, or empty string if not found
     */
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
