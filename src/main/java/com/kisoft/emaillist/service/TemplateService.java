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
import java.util.HashMap;
import java.util.Map;

/**
 * Template Management Service.
 *
 * Manages email template persistence using the file system (configs/templates folder).
 * Each template is stored as a JSON file with the naming convention: template-{slot}.json
 *
 * Configuration:
 * - app.templates.folder: Path to templates folder (default: configs/templates)
 * - app.templates.enabled: Enable/disable template persistence (default: true)
 *
 * Template File Format:
 * {
 *   "subject": "Email Subject",
 *   "htmlContent": "HTML body content",
 *   "createdAt": "2025-12-28T12:00:00Z",
 *   "updatedAt": "2025-12-28T12:30:00Z"
 * }
 *
 * @author KiSoft
 * @version 1.0.0
 * @since 2025-12-28
 * @see com.kisoft.emaillist.controller.TemplateController
 */
@Service
public class TemplateService {

    private static final Logger log = LoggerFactory.getLogger(TemplateService.class);

    private final Path templatesFolder;
    private final boolean enabled;

    public TemplateService(
        @Value("${app.templates.folder:configs/templates}") String templatesFolderPath,
        @Value("${app.templates.enabled:true}") boolean enabled
    ) {
        this.enabled = enabled;
        // Handle both absolute and relative paths
        // On Windows, paths like "d:\temp" or "D:\temp" should be recognized as absolute
        Path templatePath = Paths.get(templatesFolderPath);
        boolean isAbsolute = templatePath.isAbsolute() ||
            (templatesFolderPath.length() > 1 && templatesFolderPath.charAt(1) == ':');
        if (isAbsolute) {
            this.templatesFolder = templatePath.toAbsolutePath().normalize();
        } else {
            this.templatesFolder = Paths.get(System.getProperty("user.dir"), templatesFolderPath)
                .toAbsolutePath()
                .normalize();
        }

        log.info("[TEMPLATE-SERVICE] Initialized with folder: {}", templatesFolder);
        log.info("[TEMPLATE-SERVICE] Template persistence enabled: {}", enabled);

        // Create folder if it doesn't exist
        if (enabled) {
            try {
                Files.createDirectories(templatesFolder);
                log.info("[TEMPLATE-SERVICE] Templates folder created/verified at: {}", templatesFolder);
            } catch (IOException e) {
                log.error("[TEMPLATE-SERVICE] Failed to create templates folder: {}", e.getMessage(), e);
            }
        }
    }

    /**
     * Saves a template to the file system.
     *
     * @param slot Template slot number (1-10)
     * @param subject Email subject
     * @param htmlContent Email body HTML
     * @return Map with status and details
     */
    public Map<String, Object> saveTemplate(int slot, String subject, String htmlContent) {
        Map<String, Object> result = new HashMap<>();

        if (!enabled) {
            result.put("status", "error");
            result.put("message", "Template persistence is disabled");
            log.warn("[TEMPLATE-SERVICE] Save attempt on disabled service");
            return result;
        }

        if (slot < 1 || slot > 10) {
            result.put("status", "error");
            result.put("message", "Invalid slot number: " + slot);
            return result;
        }

        try {
            Path templateFile = templatesFolder.resolve("template-" + slot + ".json");

            if (Files.notExists(templatesFolder)) {
                Files.createDirectories(templatesFolder);
                log.info("[TEMPLATE-SERVICE] Templates folder created at: {}", templatesFolder);
            }

            // Normalize HTML content (fix image src attributes, strip leading backslashes, etc.)
            String normalizedHtml = normalizeHtmlContent(htmlContent);

            // Create template object
            Map<String, Object> template = new HashMap<>();
            template.put("subject", subject != null ? subject : "");
            template.put("htmlContent", normalizedHtml != null ? normalizedHtml : "");
            template.put("createdAt", getFileCreatedTime(templateFile));
            template.put("updatedAt", java.time.Instant.now().toString());

            // Convert to JSON and save
            String json = jsonToString(template);
            Files.writeString(templateFile, json, StandardCharsets.UTF_8);

            long fileSize = Files.size(templateFile);
            log.info("[TEMPLATE-SERVICE] Template {} SAVED: path='{}', subject='{}', size={} bytes",
                slot, templateFile.toAbsolutePath(),
                subject != null ? (subject.length() > 50 ? subject.substring(0, 50) + "..." : subject) : "(empty)",
                fileSize);

            result.put("status", "ok");
            result.put("message", "Template saved");
            result.put("slot", slot);
            result.put("filePath", templateFile.toAbsolutePath().toString());
            result.put("fileSize", fileSize);

        } catch (IOException e) {
            log.error("[TEMPLATE-SERVICE] Failed to save template {}: {}", slot, e.getMessage(), e);
            result.put("status", "error");
            result.put("message", "Failed to save template: " + e.getMessage());
        }

        return result;
    }

    /**
     * Loads a template from the file system.
     *
     * @param slot Template slot number (1-10)
     * @return Map with template data or error
     */
    public Map<String, Object> loadTemplate(int slot) {
        Map<String, Object> result = new HashMap<>();

        if (!enabled) {
            result.put("status", "error");
            result.put("message", "Template persistence is disabled");
            return result;
        }

        if (slot < 1 || slot > 10) {
            result.put("status", "error");
            result.put("message", "Invalid slot number: " + slot);
            return result;
        }

        try {
            Path templateFile = templatesFolder.resolve("template-" + slot + ".json");

            if (!Files.exists(templateFile)) {
                log.info("[TEMPLATE-SERVICE] Template {} not found at: {}", slot, templateFile);
                result.put("status", "not_found");
                result.put("message", "Template " + slot + " not found");
                return result;
            }

            // Read and parse JSON
            String json = Files.readString(templateFile, StandardCharsets.UTF_8);
            Map<String, Object> template = jsonToMap(json);

            // Normalize HTML content when loading to fix any broken image src attributes
            if (template.containsKey("htmlContent")) {
                String htmlContent = (String) template.get("htmlContent");
                if (htmlContent != null && !htmlContent.isBlank()) {
                    String normalizedHtml = normalizeHtmlContent(htmlContent);
                    template.put("htmlContent", normalizedHtml);
                }
            }

            long fileSize = Files.size(templateFile);
            String subject = template.get("subject") != null ? template.get("subject").toString() : "(empty)";
            log.info("[TEMPLATE-SERVICE] Template {} LOADED: path='{}', subject='{}', size={} bytes",
                slot, templateFile.toAbsolutePath(),
                subject.length() > 50 ? subject.substring(0, 50) + "..." : subject,
                fileSize);

            result.put("status", "ok");
            result.put("data", template);
            result.put("slot", slot);
            result.put("filePath", templateFile.toAbsolutePath().toString());

        } catch (IOException e) {
            log.error("[TEMPLATE-SERVICE] Failed to load template {}: {}", slot, e.getMessage(), e);
            result.put("status", "error");
            result.put("message", "Failed to load template: " + e.getMessage());
        }

        return result;
    }

    /**
     * Deletes a template file.
     *
     * @param slot Template slot number (1-10)
     * @return Map with status
     */
    public Map<String, Object> deleteTemplate(int slot) {
        Map<String, Object> result = new HashMap<>();

        if (!enabled) {
            result.put("status", "error");
            result.put("message", "Template persistence is disabled");
            return result;
        }

        if (slot < 1 || slot > 10) {
            result.put("status", "error");
            result.put("message", "Invalid slot number: " + slot);
            return result;
        }

        try {
            Path templateFile = templatesFolder.resolve("template-" + slot + ".json");

            if (Files.exists(templateFile)) {
                Files.delete(templateFile);
                log.info("[TEMPLATE-SERVICE] Template {} deleted", slot);
                result.put("status", "ok");
                result.put("message", "Template deleted");
            } else {
                log.info("[TEMPLATE-SERVICE] Template {} not found for deletion", slot);
                result.put("status", "not_found");
                result.put("message", "Template not found");
            }

        } catch (IOException e) {
            log.error("[TEMPLATE-SERVICE] Failed to delete template {}: {}", slot, e.getMessage(), e);
            result.put("status", "error");
            result.put("message", "Failed to delete template: " + e.getMessage());
        }

        return result;
    }

    /**
     * Gets template label for button display (first 22 chars of subject).
     *
     * @param slot Template slot number
     * @return Label string or slot number if template doesn't exist
     */
    public String getTemplateLabel(int slot) {
        try {
            Path templateFile = templatesFolder.resolve("template-" + slot + ".json");
            if (!Files.exists(templateFile)) {
                return String.valueOf(slot);
            }

            String json = Files.readString(templateFile, StandardCharsets.UTF_8);
            Map<String, Object> template = jsonToMap(json);
            String subject = (String) template.get("subject");

            if (subject != null && !subject.isBlank()) {
                String label = subject.substring(0, Math.min(22, subject.length()));
                return slot + ". " + label + (subject.length() > 22 ? "…" : "");
            }
        } catch (Exception e) {
            log.debug("[TEMPLATE-SERVICE] Error getting label for slot {}: {}", slot, e.getMessage());
        }

        return String.valueOf(slot);
    }

    /**
     * Gets template folder path.
     *
     * @return Path to templates folder
     */
    public String getTemplateFolderPath() {
        return templatesFolder.toString();
    }

    /**
     * Gets the full file path of a template for logging/audit.
     *
     * @param slot Template slot number (1-10)
     * @return Full file path as string
     */
    public String getTemplateFilePath(int slot) {
        return templatesFolder.resolve("template-" + slot + ".json").toString();
    }

    /**
     * Verifies images stored in a template's HTML content. Checks for:
     * - Embedded data URLs (reports mime and estimated size)
     * - Local/remote paths (reports existence for local file paths)
     * Returns a summary suitable for diagnostics.
     */
    public Map<String, Object> verifyTemplateImages(int slot) {
        Map<String, Object> result = new HashMap<>();
        if (!enabled) {
            result.put("status", "error");
            result.put("message", "Template persistence is disabled");
            return result;
        }
        if (slot < 1 || slot > 10) {
            result.put("status", "error");
            result.put("message", "Invalid slot number: " + slot);
            return result;
        }
        try {
            Path templateFile = templatesFolder.resolve("template-" + slot + ".json");
            if (!Files.exists(templateFile)) {
                result.put("status", "not_found");
                result.put("message", "Template " + slot + " not found");
                return result;
            }
            String json = Files.readString(templateFile, StandardCharsets.UTF_8);
            Map<String, Object> template = jsonToMap(json);
            String htmlContent = template.get("htmlContent") instanceof String ? (String) template.get("htmlContent") : null;

            Map<String, Object> summary = analyzeImages(htmlContent);
            result.put("status", "ok");
            result.put("slot", slot);
            result.put("filePath", templateFile.toString());
            result.put("summary", summary);
            return result;
        } catch (IOException e) {
            log.error("[TEMPLATE-SERVICE] Failed to verify images for template {}: {}", slot, e.getMessage(), e);
            result.put("status", "error");
            result.put("message", "Failed to verify: " + e.getMessage());
            return result;
        }
    }

    /**
     * Analyzes images in HTML content for diagnostic purposes.
     * Counts total images, data URL images, and path-based images.
     * @param htmlContent HTML content to analyze
     * @return Map containing imageCount, dataUrlCount, and pathCount
     */
    private Map<String, Object> analyzeImages(String htmlContent) {
        Map<String, Object> summary = new HashMap<>();
        if (htmlContent == null || htmlContent.isBlank()) {
            summary.put("imageCount", 0);
            return summary;
        }
        int imageCount = 0;
        int dataUrlCount = 0;
        int pathCount = 0;
        try {
            org.jsoup.nodes.Document doc = org.jsoup.Jsoup.parse(htmlContent);
            org.jsoup.select.Elements imgs = doc.select("img");
            imageCount = imgs.size();
            for (org.jsoup.nodes.Element img : imgs) {
                String src = img.attr("src");
                if (src != null && src.startsWith("data:")) {
                    dataUrlCount++;
                } else {
                    pathCount++;
                }
            }
        } catch (Exception e) {
            summary.put("error", e.getMessage());
        }
        summary.put("imageCount", imageCount);
        summary.put("dataUrlCount", dataUrlCount);
        summary.put("pathCount", pathCount);
        return summary;
    }


    // --- Helper Methods ---

    /**
     * Gets the creation time of a file as an ISO-8601 string.
     * Falls back to current time if file doesn't exist or time cannot be read.
     * @param file Path to the file
     * @return ISO-8601 timestamp string
     */
    private String getFileCreatedTime(Path file) {
        try {
            if (Files.exists(file)) {
                return Files.getLastModifiedTime(file).toInstant().toString();
            }
        } catch (IOException e) {
            log.debug("Could not get file creation time: {}", e.getMessage());
        }
        return java.time.Instant.now().toString();
    }

    /**
     * JSON serialization - manual implementation.
     * Properly handles all special characters in HTML content.
     */
    private String jsonToString(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            sb.append("\"").append(escapeJsonString(entry.getKey())).append("\":");
            Object value = entry.getValue();
            if (value == null) {
                sb.append("null");
            } else if (value instanceof String) {
                sb.append("\"").append(escapeJsonString((String) value)).append("\"");
            } else if (value instanceof Number || value instanceof Boolean) {
                sb.append(value);
            } else {
                sb.append("\"").append(escapeJsonString(value.toString())).append("\"");
            }
        }
        sb.append("}");
        return sb.toString();
    }

    /**
     * Escapes a string for JSON output.
     */
    private String escapeJsonString(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < ' ') {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

    /**
     * JSON deserialization - manual implementation.
     * Properly handles all escaped characters in JSON content.
     */
    private Map<String, Object> jsonToMap(String json) {
        Map<String, Object> result = new HashMap<>();
        if (json == null || json.isBlank()) return result;

        String trimmed = json.trim();
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            log.error("[TEMPLATE-SERVICE] Invalid JSON format");
            return result;
        }

        // Remove outer braces
        String content = trimmed.substring(1, trimmed.length() - 1);

        // Parse key-value pairs
        int i = 0;
        while (i < content.length()) {
            // Skip whitespace
            while (i < content.length() && Character.isWhitespace(content.charAt(i))) i++;
            if (i >= content.length()) break;

            // Find key (must start with ")
            if (content.charAt(i) != '"') {
                i++;
                continue;
            }
            i++; // skip opening "

            // Extract key
            StringBuilder keyBuilder = new StringBuilder();
            while (i < content.length() && content.charAt(i) != '"') {
                if (content.charAt(i) == '\\' && i + 1 < content.length()) {
                    i++;
                    keyBuilder.append(parseEscapeChar(content.charAt(i)));
                } else {
                    keyBuilder.append(content.charAt(i));
                }
                i++;
            }
            i++; // skip closing "
            String key = keyBuilder.toString();

            // Skip to colon
            while (i < content.length() && content.charAt(i) != ':') i++;
            i++; // skip colon

            // Skip whitespace
            while (i < content.length() && Character.isWhitespace(content.charAt(i))) i++;

            // Parse value
            if (i >= content.length()) break;

            if (content.charAt(i) == '"') {
                // String value
                i++; // skip opening "
                StringBuilder valueBuilder = new StringBuilder();
                while (i < content.length()) {
                    char c = content.charAt(i);
                    if (c == '"') break;
                    if (c == '\\' && i + 1 < content.length()) {
                        i++;
                        char escaped = content.charAt(i);
                        if (escaped == 'u' && i + 4 < content.length()) {
                            // Unicode escape
                            String hex = content.substring(i + 1, i + 5);
                            try {
                                valueBuilder.append((char) Integer.parseInt(hex, 16));
                                i += 4;
                            } catch (NumberFormatException e) {
                                valueBuilder.append(escaped);
                            }
                        } else {
                            valueBuilder.append(parseEscapeChar(escaped));
                        }
                    } else {
                        valueBuilder.append(c);
                    }
                    i++;
                }
                i++; // skip closing "
                result.put(key, valueBuilder.toString());
            } else if (content.substring(i).startsWith("null")) {
                result.put(key, null);
                i += 4;
            } else if (content.substring(i).startsWith("true")) {
                result.put(key, true);
                i += 4;
            } else if (content.substring(i).startsWith("false")) {
                result.put(key, false);
                i += 5;
            } else {
                // Number or unknown - skip to next comma or end
                StringBuilder numBuilder = new StringBuilder();
                while (i < content.length() && content.charAt(i) != ',' && !Character.isWhitespace(content.charAt(i))) {
                    numBuilder.append(content.charAt(i));
                    i++;
                }
                try {
                    String numStr = numBuilder.toString();
                    if (numStr.contains(".")) {
                        result.put(key, Double.parseDouble(numStr));
                    } else {
                        result.put(key, Long.parseLong(numStr));
                    }
                } catch (NumberFormatException e) {
                    result.put(key, numBuilder.toString());
                }
            }

            // Skip to next comma or end
            while (i < content.length() && content.charAt(i) != ',') i++;
            i++; // skip comma
        }

        return result;
    }

    /**
     * Parses a JSON escape character and returns the corresponding character.
     * Handles standard JSON escapes: n, r, t, b, f, ", \, /
     * @param c The character following the backslash in the escape sequence
     * @return The actual character represented by the escape sequence
     */
    private char parseEscapeChar(char c) {
        return switch (c) {
            case 'n' -> '\n';
            case 'r' -> '\r';
            case 't' -> '\t';
            case 'b' -> '\b';
            case 'f' -> '\f';
            case '"' -> '"';
            case '\\' -> '\\';
            case '/' -> '/';
            default -> c;
        };
    }

    /**
     * Normalizes HTML content: fixes image src attributes by removing leading backslashes
     * and excessive whitespace. Ensures data URLs are properly formatted.
     * Preserves original HTML structure and formatting as much as possible.
     *
     * Only processes through Jsoup if potential issues are detected.
     *
     * @param htmlContent Raw HTML content (may contain malformed img src attributes)
     * @return Cleaned HTML content with normalized img src values
     */
    public String normalizeHtmlContent(String htmlContent) {
        if (htmlContent == null || htmlContent.isEmpty()) {
            return htmlContent;
        }
        // Return whitespace-only content as-is
        if (htmlContent.isBlank()) {
            return htmlContent;
        }

        // Quick check: if content doesn't contain problematic patterns, return as-is
        // Check for:
        // - Any backslash character (handles escapes, CSS sequences, embedded backslashes)
        // - Newlines or tabs in data URLs
        // - Newlines or tabs in data URLs
        boolean needsNormalization = htmlContent.contains("\\")
            || (htmlContent.contains("data:") && (htmlContent.contains("\n") || htmlContent.contains("\r") || htmlContent.contains("\t")));

        if (!needsNormalization) {
            // No obvious issues - return original content unchanged
            return htmlContent;
        }

        try {
            // Pre-process the HTML to normalize src attributes BEFORE Jsoup parsing
            // This avoids Jsoup interpreting backslashes as CSS escapes
            String preprocessed = preprocessSrcAttributes(htmlContent);

            // Parse as body fragment to preserve original structure without adding html/head/body tags
            org.jsoup.nodes.Document doc = org.jsoup.Jsoup.parseBodyFragment(preprocessed);

            // Configure output settings to preserve formatting as much as possible
            doc.outputSettings()
                .indentAmount(0)
                .outline(false)
                .prettyPrint(false)
                .escapeMode(org.jsoup.nodes.Entities.EscapeMode.xhtml);

            // Track if any changes were made
            boolean changed = !preprocessed.equals(htmlContent);

            // Normalize SVG elements (width, height, viewBox attributes may have escape sequences)
            org.jsoup.select.Elements svgs = doc.select("svg");
            for (org.jsoup.nodes.Element svg : svgs) {
                if (normalizeAttribute(svg, "width")) changed = true;
                if (normalizeAttribute(svg, "height")) changed = true;
                if (normalizeAttribute(svg, "viewBox")) changed = true;
            }

            // If no changes were made, return original to preserve exact formatting
            if (!changed) {
                return htmlContent;
            }

            // Return only the body's inner HTML to avoid wrapping with html/head/body tags
            return doc.body().html();
        } catch (Exception e) {
            log.warn("[TEMPLATE-SERVICE] Failed to normalize HTML content: {}", e.getMessage());
            return htmlContent;
        }
    }

    /**
     * Pre-processes src attributes in raw HTML to clean up backslashes and whitespace
     * BEFORE Jsoup parsing. This avoids Jsoup interpreting backslashes as CSS escapes.
     * Uses regex to find all src="..." or src='...' patterns and cleans each value.
     * @param html Raw HTML string that may contain malformed src attributes
     * @return HTML with cleaned src attribute values
     */
    private String preprocessSrcAttributes(String html) {
        if (html == null) return null;

        // Pattern to match src attribute values: src="..." or src='...'
        // Uses DOTALL flag so that . matches newlines within the attribute value
        java.util.regex.Pattern srcPattern = java.util.regex.Pattern.compile(
            "(src\\s*=\\s*)([\"'])([^\"']*?)\\2",
            java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.DOTALL);

        java.util.regex.Matcher matcher = srcPattern.matcher(html);
        StringBuilder result = new StringBuilder();

        while (matcher.find()) {
            String prefix = matcher.group(1);  // "src="
            String quote = matcher.group(2);   // quote character
            String value = matcher.group(3);   // attribute value

            // Clean the src value
            String cleaned = cleanSrc(value);

            // Build replacement
            String replacement = prefix + quote + cleaned + quote;
            matcher.appendReplacement(result, java.util.regex.Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);

        return result.toString();
    }

    /**
     * Normalizes a single attribute value by removing CSS escape sequences and backslashes.
     * @return true if the attribute was changed
     */
    private boolean normalizeAttribute(org.jsoup.nodes.Element element, String attrName) {
        if (element.hasAttr(attrName)) {
            String raw = element.attr(attrName);
            if (raw != null && !raw.isBlank()) {
                String cleaned = cleanAttributeValue(raw);
                if (!raw.equals(cleaned)) {
                    element.attr(attrName, cleaned);
                    log.debug("[TEMPLATE-SERVICE] Normalized {} attr: '{}' → '{}'", attrName, raw, cleaned);
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Cleans attribute values by removing CSS escape sequences and backslashes.
     * Handles CSS escape sequences like \20 (space) by converting hex to character.
     * @param raw Raw attribute value that may contain escape sequences
     * @return Cleaned attribute value with escapes resolved and backslashes removed
     */
    private String cleanAttributeValue(String raw) {
        if (raw == null || raw.isBlank()) {
            return raw;
        }
        String s = raw.trim();

        // Handle CSS-style escape sequences like \20 (space)
        java.util.regex.Pattern cssEscapePattern = java.util.regex.Pattern.compile("\\\\([0-9a-fA-F]{1,6})\\s?");
        java.util.regex.Matcher matcher = cssEscapePattern.matcher(s);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            try {
                String hex = matcher.group(1);
                int codePoint = Integer.parseInt(hex, 16);
                String replacement = String.valueOf((char) codePoint);
                matcher.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(replacement));
            } catch (Exception e) {
                matcher.appendReplacement(sb, "");
            }
        }
        matcher.appendTail(sb);
        s = sb.toString();

        // Remove remaining backslashes
        s = s.replace("\\", "");

        return s.trim();
    }


    /**
     * Cleans an image src attribute value.
     *
     * <p>Performs the following transformations in order:</p>
     * <ol>
     *   <li>Remove all newlines, carriage returns, and tabs</li>
     *   <li>Strip all backslashes (handles leading {@code \data:}, CSS escapes, and embedded backslashes)</li>
     *   <li>Trim leading/trailing whitespace</li>
     * </ol>
     *
     * @param raw Raw src attribute value
     * @return Cleaned src value suitable for data URL or path
     */
    private String cleanSrc(String raw) {
        if (raw == null || raw.isBlank()) {
            return raw;
        }
        String s = raw.trim();

        // Step 1: Remove all newlines, carriage returns, and tabs from the value
        // This must happen first before any other processing
        s = s.replace("\r", "").replace("\n", "").replace("\t", "");

        // Step 2: Strip all backslashes from the src attribute value
        // This handles:
        // - Leading backslashes like "\data:" -> "data:"
        // - CSS escape sequences like "\20" -> removed
        // - Backslashes inside base64 data like "SkZ\JRg" -> "SkZJRg"
        // We do this BEFORE any CSS escape processing since we want to remove ALL backslashes
        s = s.replace("\\", "");

        // Step 3: Trim leading/trailing whitespace
        s = s.trim();


        return s;
    }
}
