package com.kisoft.emaillist.controller;

import com.kisoft.emaillist.service.TemplateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Template Management REST Controller.
 *
 * Provides REST API endpoints for saving, loading, and managing email templates.
 * Templates are persisted to the file system in the configured templates folder
 * (default: configs/templates/).
 *
 * Endpoints:
 * - POST   /api/template/save       - Save a template
 * - GET    /api/template/load/{slot} - Load a template
 * - DELETE /api/template/delete/{slot} - Delete a template
 * - GET    /api/template/label/{slot} - Get button label
 * - POST   /api/template/log        - Log template operation
 *
 * @author KiSoft
 * @version 1.0.0
 * @since 2025-12-28
 * @see com.kisoft.emaillist.service.TemplateService
 */
@RestController
@RequestMapping("/api/template")
public class TemplateController {

    private static final Logger log = LoggerFactory.getLogger(TemplateController.class);

    private final TemplateService templateService;

    public TemplateController(TemplateService templateService) {
        this.templateService = templateService;
    }

    /**
     * Saves a template to the file system.
     *
     * Request body:
     * {
     *   "slot": 1,
     *   "subject": "Email Subject",
     *   "htmlContent": "HTML body content"
     * }
     */
    @PostMapping(value = "/save", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> saveTemplate(@RequestBody Map<String, Object> request) {
        log.info("[TEMPLATE-API] Save request received");

        try {
            Integer slot = (Integer) request.get("slot");
            String subject = (String) request.get("subject");
            String htmlContent = (String) request.get("htmlContent");

            if (slot == null || slot < 1 || slot > 10) {
                return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", "Invalid slot number: " + slot
                ));
            }

            Map<String, Object> result = templateService.saveTemplate(slot, subject, htmlContent);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("[TEMPLATE-API] Error saving template: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "message", "Failed to save template: " + e.getMessage()
            ));
        }
    }

    /**
     * Loads a template from the file system.
     *
     * @param slot Template slot number (1-10)
     */
    @PostMapping(value = "/load", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> loadTemplatePost(@RequestBody TemplateSlotRequest request) {
        Integer slot = request != null ? request.slot() : null;
        if (slot == null) {
            return ResponseEntity.badRequest().body(Map.of(
                "status", "error",
                "message", "Slot is required"
            ));
        }
        if (slot < 1 || slot > 10) {
            return ResponseEntity.badRequest().body(Map.of(
                "status", "error",
                "message", "Invalid slot number: " + slot
            ));
        }
        return loadTemplateInternal(slot);
    }

    /**
     * Loads a template from the file system.
     *
     * @param ignoredSlot Template slot number (1-10)
     */
    @GetMapping(value = "/load/{slot}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> loadTemplate(@PathVariable("slot") int ignoredSlot) {
        // This endpoint previously worked, but it encourages GET-based loading and can be retried/replayed by the browser.
        // We now require POST for template loads so the request shape is predictable and we can avoid any accidental
        // header/cookie bloat issues on some environments.
        return ResponseEntity.status(405).body(Map.of(
            "status", "error",
            "message", "Use POST /api/template/load with JSON body {\"slot\": <1-10>}"
        ));
    }

    private ResponseEntity<Map<String, Object>> loadTemplateInternal(int slot) {
        log.info("[TEMPLATE-API] Load request for slot: {}", slot);

        try {
            Map<String, Object> result = templateService.loadTemplate(slot);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("[TEMPLATE-API] Error loading template {}: {}", slot, e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "message", "Failed to load template: " + e.getMessage()
            ));
        }
    }

    /**
     * Deletes a template from the file system.
     *
     * @param slot Template slot number (1-10)
     */
    @DeleteMapping(value = "/delete/{slot}")
    public ResponseEntity<Map<String, Object>> deleteTemplate(@PathVariable int slot) {
        log.info("[TEMPLATE-API] Delete request for slot: {}", slot);

        try {
            Map<String, Object> result = templateService.deleteTemplate(slot);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("[TEMPLATE-API] Error deleting template {}: {}", slot, e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "message", "Failed to delete template: " + e.getMessage()
            ));
        }
    }

    /**
     * Gets template label for button display.
     *
     * @param slot Template slot number (1-10)
     */
    @GetMapping(value = "/label/{slot}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> getTemplateLabel(@PathVariable int slot) {
        try {
            String label = templateService.getTemplateLabel(slot);
            return ResponseEntity.ok(Map.of(
                "status", "ok",
                "slot", slot,
                "label", label
            ));

        } catch (Exception e) {
            log.error("[TEMPLATE-API] Error getting label for slot {}: {}", slot, e.getMessage());
            return ResponseEntity.ok(Map.of(
                "status", "error",
                "slot", slot,
                "label", String.valueOf(slot)
            ));
        }
    }

    /**
     * Logs template operation details (images, tables, etc.) for debugging.
     * This helps track what's being stored in templates.
     *
     * Request body:
     * {
     *   "operation": "SAVE" | "LOAD",
     *   "slot": 1,
     *   "subject": "Email Subject",
     *   "contentLength": 12345,
     *   "imageCount": 2,
     *   "tableCount": 1,
     *   "linkCount": 5,
     *   "imageDetails": [...]
     * }
     */
    @PostMapping(value = "/log", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> logTemplateOperation(@RequestBody Map<String, Object> request) {
        try {
            String operation = (String) request.get("operation");
            Integer slot = (Integer) request.get("slot");
            String subject = (String) request.get("subject");
            Integer contentLength = (Integer) request.getOrDefault("contentLength", 0);
            Integer imageCount = (Integer) request.getOrDefault("imageCount", 0);
            Integer tableCount = (Integer) request.getOrDefault("tableCount", 0);
            Integer linkCount = (Integer) request.getOrDefault("linkCount", 0);

            String templatePath = slot != null ? templateService.getTemplateFilePath(slot) : "(unknown)";

            log.info("[TEMPLATE-AUDIT] {} template slot={}, path={}, subject='{}', size={}bytes, images={}, tables={}, links={}",
                operation, slot, templatePath, subject, contentLength, imageCount, tableCount, linkCount);


            return ResponseEntity.ok(Map.of(
                "status", "logged",
                "message", "Operation logged successfully"
            ));

        } catch (Exception e) {
            log.error("[TEMPLATE-AUDIT] Error logging template operation: {}", e.getMessage());
            return ResponseEntity.ok(Map.of(
                "status", "error",
                "message", "Failed to log: " + e.getMessage()
            ));
        }
    }


    /**
     * Gets template folder information.
     */
    @GetMapping(value = "/info", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> getTemplateInfo() {
        try {
            Map<String, Object> info = new HashMap<>();
            info.put("status", "ok");
            info.put("folderPath", templateService.getTemplateFolderPath());
            info.put("maxSlots", 10);
            info.put("timestamp", java.time.Instant.now().toString());

            return ResponseEntity.ok(info);

        } catch (Exception e) {
            log.error("[TEMPLATE-API] Error getting template info: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "message", "Failed to get template info: " + e.getMessage()
            ));
        }
    }

    /**
     * Verifies template images for a slot and returns diagnostic summary.
     *
     * @param slot Template slot number (1-10)
     */
    @GetMapping(value = "/verify/{slot}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> verifyTemplateImages(@PathVariable int slot) {
        try {
            Map<String, Object> result = templateService.verifyTemplateImages(slot);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("[TEMPLATE-API] Error verifying images for slot {}: {}", slot, e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "message", "Verification failed: " + e.getMessage()
            ));
        }
    }

    /**
     * Repairs an already-saved template by normalizing image src attributes.
     * Useful if images have leading backslashes or malformed data URLs.
     *
     * @param slot Template slot number (1-10)
     */
    @PostMapping(value = "/repair/{slot}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> repairTemplate(@PathVariable int slot) {
        log.info("[TEMPLATE-API] Repair request for slot: {}", slot);

        try {
            if (slot < 1 || slot > 10) {
                return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", "Invalid slot number: " + slot
                ));
            }

            // Load the template
            Map<String, Object> loadResult = templateService.loadTemplate(slot);
            if (!"ok".equals(loadResult.get("status"))) {
                return ResponseEntity.ok(loadResult);
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) loadResult.get("data");
            String htmlContent = data.get("htmlContent") instanceof String ? (String) data.get("htmlContent") : "";
            String subject = data.get("subject") instanceof String ? (String) data.get("subject") : "";

            // Re-save with normalization applied
            Map<String, Object> saveResult = templateService.saveTemplate(slot, subject, htmlContent);
            if ("ok".equals(saveResult.get("status"))) {
                log.info("[TEMPLATE-API] Template {} repaired successfully", slot);
            }
            return ResponseEntity.ok(saveResult);

        } catch (Exception e) {
            log.error("[TEMPLATE-API] Error repairing template {}: {}", slot, e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "message", "Failed to repair template: " + e.getMessage()
            ));
        }
    }

    public record TemplateSlotRequest(Integer slot) { }

    String safeLogValue(String value, int maxLen) {
        if (value == null) {
            return "(null)";
        }
        // Prevent log injection and accidental multi-line spam.
        String sanitized = value.replace("\r", " ").replace("\n", " ");
        if (sanitized.length() <= maxLen) {
            return sanitized;
        }
        return sanitized.substring(0, maxLen) + "…";
    }
}
