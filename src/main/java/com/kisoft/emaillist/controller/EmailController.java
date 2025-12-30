package com.kisoft.emaillist.controller;

import com.kisoft.emaillist.model.EmailRequest;
import com.kisoft.emaillist.model.SendResult;
import com.kisoft.emaillist.service.EmailListService;
import com.kisoft.emaillist.service.EmailSenderService;
import com.kisoft.emaillist.service.ExportService;
import com.kisoft.emaillist.service.FacebookService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Email Controller - Handles all HTTP requests for the Email Mass Sender application.
 * This controller provides both view rendering and REST API endpoints for:
 * - Main page rendering (Thymeleaf view)
 * - Email sending (individual and batch modes)
 * - Email list management (CRUD operations)
 * - Mail server connection testing
 * - Export to PDF and DOCX formats
 * - Facebook posting integration
 * REST API Endpoints:
 * - {@code GET /} - Main page with email list
 * - {@code POST /api/send} - Send emails (individual/batch)
 * - {@code POST /api/send-test} - Send test email
 * - {@code GET /api/emails} - Get all emails
 * - {@code POST /api/emails} - Add single email
 * - {@code DELETE /api/emails} - Remove single email
 * - {@code POST /api/emails/bulk} - Replace entire email list
 * - {@code GET /api/test-connection} - Test SMTP connection
 * - {@code POST /api/export/pdf} - Export to PDF
 * - {@code POST /api/export/docx} - Export to DOCX
 * - {@code POST /api/facebook/post} - Post to Facebook
 * @author KiSoft
 * @version 1.0.0
 * @since 2025-12-26
 * @see com.kisoft.emaillist.service.EmailSenderService
 * @see com.kisoft.emaillist.service.EmailListService
 * @see com.kisoft.emaillist.service.ExportService
 * @see com.kisoft.emaillist.service.FacebookService
 * @see com.kisoft.emaillist.model.EmailRequest
 * @see com.kisoft.emaillist.model.SendResult
 */
@Controller
@Slf4j
public class EmailController {

    /** Service for sending emails via SMTP */
    private final EmailSenderService emailSenderService;

    /** Service for managing the email recipient list */
    private final EmailListService emailListService;

    /** Service for posting to Facebook */
    private final FacebookService facebookService;

    /** Service for exporting to PDF/DOCX */
    private final ExportService exportService;

    /** Default text color for the editor (from application.properties) */
    @Value("${app.editor.default.text.color:#000000}")
    private String editorDefaultTextColor;

    /** Number of template slots available (from application.properties) */
    @Value("${app.template.slots:5}")
    private int templateSlots;

    /** Whether Facebook integration is enabled */
    @Value("${facebook.enabled:false}")
    private boolean facebookEnabled;

    public EmailController(EmailSenderService emailSenderService,
                          EmailListService emailListService,
                          FacebookService facebookService,
                          ExportService exportService) {
        this.emailSenderService = emailSenderService;
        this.emailListService = emailListService;
        this.facebookService = facebookService;
        this.exportService = exportService;
    }

    /**
     * Renders the main application page.
     * Loads the email list and passes it to the Thymeleaf template along with
     * configuration values for the editor and template slots.
     * @param model Spring MVC model for template data binding
     * @return Template name {@code index} (resolves to {@code index.html})
     */
    @GetMapping("/")
    public String index(Model model) {
        List<String> emails = emailListService.loadEmailList();
        model.addAttribute("emails", emails);
        model.addAttribute("emailCount", emails.size());
        model.addAttribute("editorDefaultTextColor", editorDefaultTextColor);
        model.addAttribute("templateSlots", templateSlots);
        model.addAttribute("facebookEnabled", facebookService.isEnabled());
        return "index";
    }

    /**
     * Sends emails to selected recipients.
     * Supports two sending modes:
     * - {@code individual} - Send one email per recipient sequentially
     * - {@code batch} - Send to multiple recipients per email (BCC/To)
     * Address modes:
     * - {@code to} - Recipients visible to each other
     * - {@code bcc} - Recipients hidden from each other (recommended for mass emails)
     * @param request {@link EmailRequest} containing subject, content, recipients, and options
     * @return {@link SendResult} with success/fail counts and error details
     */
    @PostMapping("/api/send")
    @ResponseBody
    public ResponseEntity<SendResult> sendEmails(@RequestBody EmailRequest request) {
        String requestId = java.util.UUID.randomUUID().toString().substring(0, 8);
        log.info("[REQ-{}] Received send request: subject={}, sendToAll={}, mode={}, addressMode={}",
                requestId, request.getSubject(), request.getSendToAll(), request.getSendMode(), request.getAddressMode());
        log.info("[REQ-{}] Selected emails array: {}", requestId, java.util.Arrays.toString(request.getSelectedEmails()));

        SendResult result;
        List<String> emails;

        // Determine recipient list: all emails or selected only (default to false if null)
        boolean sendToAll = Boolean.TRUE.equals(request.getSendToAll());
        if (sendToAll) {
            emails = emailListService.loadEmailList();
            log.info("[REQ-{}] Using all emails from list: {} recipients", requestId, emails.size());
        } else {
            String[] selectedArray = request.getSelectedEmails();
            if (selectedArray != null) {
                emails = Arrays.asList(selectedArray);
                log.info("[REQ-{}] Using selected emails: {} recipients - {}", requestId, emails.size(), emails);
            } else {
                emails = new ArrayList<>();
                log.warn("[REQ-{}] No selected emails provided", requestId);
            }
        }

        String sendMode = request.getSendMode();
        String addressMode = request.getAddressMode();
        boolean useBcc = "bcc".equalsIgnoreCase(addressMode);

        log.info("[REQ-{}] Processing: sendMode={}, addressMode={}, useBcc={}, batchSize={}",
                requestId, sendMode, addressMode, useBcc, request.getBatchSize());

        // Execute appropriate sending strategy
        if ("batch".equals(sendMode)) {
            int batchSize = (request.getBatchSize() != null && request.getBatchSize() > 0) ? request.getBatchSize() : 10;
            int delayMs = (request.getDelayMs() != null && request.getDelayMs() >= 0) ? request.getDelayMs() : 500;
            log.info("[REQ-{}] Calling sendBatch with useBcc={}, delayMs={}", requestId, useBcc, delayMs);
            result = emailSenderService.sendBatch(emails, request.getSubject(), request.getHtmlContent(), batchSize, useBcc, delayMs);
        } else {
            log.info("[REQ-{}] Calling sendIndividual with emails={}", requestId, emails);
            result = emailSenderService.sendIndividual(emails, request.getSubject(), request.getHtmlContent(), useBcc);
        }

        log.info("[REQ-{}] Send complete - result: success={}, fail={}", requestId, result.getSuccessCount(), result.getFailCount());
        return ResponseEntity.ok(result);
    }

    /**
     * Sends a test email to verify SMTP configuration.
     *
     * <p>Uses the first email in selectedEmails array, or defaults to om@kisoft.ca</p>
     *
     * @param request Request containing subject and HTML content
     * @return Success/failure response with message
     */
    @PostMapping("/api/send-test")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> sendTestEmail(@RequestBody EmailRequest request) {
        Map<String, Object> response = new HashMap<>();
        try {
            String testEmail = request.getSelectedEmails() != null && request.getSelectedEmails().length > 0
                ? request.getSelectedEmails()[0]
                : "om@kisoft.ca";

            emailSenderService.sendTestEmail(testEmail, request.getSubject(), request.getHtmlContent());
            response.put("success", true);
            response.put("message", "Test email sent successfully to " + testEmail);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Failed to send test email: " + e.getMessage());
        }
        return ResponseEntity.ok(response);
    }

    /**
     * Gets all email addresses in the recipient list.
     *
     * @return List of email addresses
     */
    @GetMapping("/api/emails")
    @ResponseBody
    public ResponseEntity<List<String>> getEmails() {
        return ResponseEntity.ok(emailListService.loadEmailList());
    }

    /**
     * Adds a single email address to the recipient list.
     *
     * @param payload JSON object with "email" field
     * @return Success/failure response with updated count
     */
    @PostMapping("/api/emails")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> addEmail(@RequestBody Map<String, String> payload) {
        Map<String, Object> response = new HashMap<>();
        try {
            String email = payload.get("email");
            emailListService.addEmail(email);
            response.put("success", true);
            response.put("message", "Email added successfully");
            response.put("count", emailListService.getEmailCount());
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Failed to add email: " + e.getMessage());
        }
        return ResponseEntity.ok(response);
    }

    /**
     * Removes a single email address from the recipient list.
     *
     * @param payload JSON object with "email" field
     * @return Success/failure response with updated count
     */
    @DeleteMapping("/api/emails")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> removeEmail(@RequestBody Map<String, String> payload) {
        Map<String, Object> response = new HashMap<>();
        try {
            String email = payload.get("email");
            emailListService.removeEmail(email);
            response.put("success", true);
            response.put("message", "Email removed successfully");
            response.put("count", emailListService.getEmailCount());
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Failed to remove email: " + e.getMessage());
        }
        return ResponseEntity.ok(response);
    }

    /**
     * Replaces the entire email list with new emails.
     *
     * <p>Used by the bulk edit feature to save all emails at once.</p>
     *
     * @param payload JSON object with "emails" array
     * @return Success/failure response with updated count
     */
    @PostMapping("/api/emails/bulk")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> saveEmailList(@RequestBody Map<String, List<String>> payload) {
        Map<String, Object> response = new HashMap<>();
        try {
            List<String> emails = payload.get("emails");
            emailListService.saveEmailList(emails);
            response.put("success", true);
            response.put("message", "Email list saved successfully");
            response.put("count", emailListService.getEmailCount());
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Failed to save email list: " + e.getMessage());
        }
        return ResponseEntity.ok(response);
    }

    /**
     * Tests the SMTP server connection.
     * Called periodically by the UI to update connection status badge.
     * @return Connection status ({@code connected: true/false}) with message
     */
    @GetMapping("/api/test-connection")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> testConnection() {
        Map<String, Object> response = new HashMap<>();
        boolean connected = emailSenderService.testConnection();
        response.put("connected", connected);
        response.put("message", connected ? "Mail server connection OK" : "Mail server connection failed");
        return ResponseEntity.ok(response);
    }

    /**
     * Posts content to Facebook.
     *
     * @param request Request containing subject and HTML content
     * @return Success/failure response with message
     */
    @PostMapping("/api/facebook/post")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> postToFacebook(@RequestBody EmailRequest request) {
        Map<String, Object> response = new HashMap<>();
        try {
            if (!facebookService.isEnabled()) {
                response.put("success", false);
                response.put("message", "Facebook integration is not enabled. Please configure facebook.access.token and facebook.page.id in application.properties.");
                return ResponseEntity.ok(response);
            }

            FacebookService.FacebookPostResult result = facebookService.postToPage(
                    request.getSubject(),
                    request.getHtmlContent()
            );

            response.put("success", result.success());
            response.put("message", result.message());
            if (result.apiResponse() != null) {
                response.put("apiResponse", result.apiResponse());
            }
        } catch (Exception e) {
            log.error("Error posting to Facebook: {}", e.getMessage(), e);
            response.put("success", false);
            response.put("message", "Failed to post to Facebook: " + e.getMessage());
        }
        return ResponseEntity.ok(response);
    }

    /**
     * Tests Facebook connection.
     *
     * @return Connection status
     */
    @GetMapping("/api/facebook/test")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> testFacebookConnection() {
        Map<String, Object> response = new HashMap<>();
        boolean connected = facebookService.testConnection();
        response.put("connected", connected);
        response.put("enabled", facebookService.isEnabled());
        response.put("message", connected ? "Facebook is configured" : "Facebook is not configured");
        return ResponseEntity.ok(response);
    }

    /* =========================================================================
       EXPORT ENDPOINTS - PDF and DOCX
       ========================================================================= */

    /**
     * Export request model for PDF/DOCX export.
     */
    public record ExportRequest(String subject, String htmlContent) {}

    /**
     * Exports email content to PDF format.
     *
     * @param request The export request containing subject and HTML content
     * @return PDF file as byte array
     */
    @PostMapping("/api/export/pdf")
    @ResponseBody
    public ResponseEntity<byte[]> exportToPdf(@RequestBody ExportRequest request) {
        log.info("PDF export requested. Subject: {}", request.subject());

        try {
            byte[] pdfBytes = exportService.exportToPdf(request.subject(), request.htmlContent());

            String filename = sanitizeFilename(request.subject()) + ".pdf";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", filename);
            headers.setContentLength(pdfBytes.length);

            log.info("PDF export successful. Size: {} bytes, Filename: {}", pdfBytes.length, filename);
            return ResponseEntity.ok().headers(headers).body(pdfBytes);

        } catch (Exception e) {
            log.error("PDF export failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Exports email content to DOCX (Microsoft Word) format.
     *
     * @param request The export request containing subject and HTML content
     * @return DOCX file as byte array
     */
    @PostMapping("/api/export/docx")
    @ResponseBody
    public ResponseEntity<byte[]> exportToDocx(@RequestBody ExportRequest request) {
        log.info("DOCX export requested. Subject: {}", request.subject());

        try {
            byte[] docxBytes = exportService.exportToDocx(request.subject(), request.htmlContent());

            String filename = sanitizeFilename(request.subject()) + ".docx";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
            headers.setContentDispositionFormData("attachment", filename);
            headers.setContentLength(docxBytes.length);

            log.info("DOCX export successful. Size: {} bytes, Filename: {}", docxBytes.length, filename);
            return ResponseEntity.ok().headers(headers).body(docxBytes);

        } catch (Exception e) {
            log.error("DOCX export failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Logs template load/save operations on the server side.
     * Provides detailed logging for debugging and audit purposes.
     *
     * @param request The template log request containing operation details
     * @return Success response
     */
    @PostMapping("/api/template/log")
    @ResponseBody
    public ResponseEntity<Map<String, String>> logTemplateOperation(@RequestBody TemplateLogRequest request) {
        String timestamp = java.time.Instant.now().toString();

        log.info("========================================");
        log.info("[Template {}] Timestamp: {}", request.operation(), timestamp);
        log.info("[Template {}] Slot: {}", request.operation(), request.slot());
        log.info("[Template {}] Subject: {}", request.operation(),
                request.subject() != null ? request.subject() : "(empty)");
        log.info("[Template {}] Content length: {} chars", request.operation(),
                request.contentLength() != null ? request.contentLength() : 0);
        log.info("[Template {}] Storage: browser localStorage (key: emailTemplate{})",
                request.operation(), request.slot());

        if (request.imageCount() != null && request.imageCount() > 0) {
            log.info("[Template {}] Images: {} found", request.operation(), request.imageCount());
            if (request.imageDetails() != null && !request.imageDetails().isEmpty()) {
                for (int i = 0; i < request.imageDetails().size(); i++) {
                    var imgDetail = request.imageDetails().get(i);
                    log.info("  [Image {}] Type: {} | Size: {} | Dimensions: {} | Filename: {} | Path: {}",
                            i + 1,
                            imgDetail.get("type"),
                            imgDetail.get("size"),
                            imgDetail.get("dimensions"),
                            imgDetail.get("filename"),
                            imgDetail.get("filePath") != null ? imgDetail.get("filePath") : "(embedded base64)");
                }
            }
        } else {
            log.info("[Template {}] No images in template", request.operation());
        }

        if (request.tableCount() != null && request.tableCount() > 0) {
            log.info("[Template {}] Tables: {} found", request.operation(), request.tableCount());
        }

        if (request.linkCount() != null && request.linkCount() > 0) {
            log.info("[Template {}] Links: {}", request.operation(), request.linkCount());
        }

        log.info("[Template {}] Operation completed", request.operation());
        log.info("========================================");

        Map<String, String> response = new HashMap<>();
        response.put("status", "logged");
        response.put("timestamp", timestamp);
        return ResponseEntity.ok(response);
    }

    /**
     * Request record for template logging.
     */
    public record TemplateLogRequest(
        String operation,
        Integer slot,
        String subject,
        Integer contentLength,
        Integer imageCount,
        Integer tableCount,
        Integer linkCount,
        java.util.List<java.util.Map<String, String>> imageDetails
    ) {}

    /**
     * Sanitizes a filename by removing/replacing invalid characters.
     *
     * @param filename The original filename
     * @return Sanitized filename safe for file systems
     */
    private String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "export";
        }
        // Remove or replace invalid filename characters
        String sanitized = filename
                .replaceAll("[\\\\/:*?\"<>|]", "_")
                .replaceAll("\\s+", "_")
                .trim();
        // Limit length
        if (sanitized.length() > 100) {
            sanitized = sanitized.substring(0, 100);
        }
        return sanitized.isEmpty() ? "export" : sanitized;
    }
}
