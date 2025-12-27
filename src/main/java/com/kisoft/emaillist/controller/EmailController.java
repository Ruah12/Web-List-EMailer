package com.kisoft.emaillist.controller;

import com.kisoft.emaillist.model.EmailRequest;
import com.kisoft.emaillist.model.SendResult;
import com.kisoft.emaillist.service.EmailListService;
import com.kisoft.emaillist.service.EmailSenderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Email Controller - Handles all HTTP requests for the Email Mass Sender application.
 *
 * <p>This controller provides both view rendering and REST API endpoints for:</p>
 * <ul>
 *   <li>Main page rendering (Thymeleaf view)</li>
 *   <li>Email sending (individual and batch modes)</li>
 *   <li>Email list management (CRUD operations)</li>
 *   <li>Mail server connection testing</li>
 * </ul>
 *
 * <h3>Endpoints:</h3>
 * <table border="1">
 *   <tr><th>Method</th><th>Path</th><th>Description</th></tr>
 *   <tr><td>GET</td><td>/</td><td>Main page with email list</td></tr>
 *   <tr><td>POST</td><td>/api/send</td><td>Send emails (individual/batch)</td></tr>
 *   <tr><td>POST</td><td>/api/send-test</td><td>Send test email</td></tr>
 *   <tr><td>GET</td><td>/api/emails</td><td>Get all emails</td></tr>
 *   <tr><td>POST</td><td>/api/emails</td><td>Add single email</td></tr>
 *   <tr><td>DELETE</td><td>/api/emails</td><td>Remove single email</td></tr>
 *   <tr><td>POST</td><td>/api/emails/bulk</td><td>Replace entire email list</td></tr>
 *   <tr><td>GET</td><td>/api/test-connection</td><td>Test SMTP connection</td></tr>
 * </table>
 *
 * @author KiSoft
 * @version 1.0.0
 * @since 2025-12-26
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class EmailController {

    /** Service for sending emails via SMTP */
    private final EmailSenderService emailSenderService;

    /** Service for managing the email recipient list */
    private final EmailListService emailListService;

    /** Default text color for the editor (from application.properties) */
    @Value("${app.editor.default.text.color:white}")
    private String editorDefaultTextColor;

    /**
     * Renders the main application page.
     *
     * <p>Loads the email list and passes it to the Thymeleaf template.</p>
     *
     * @param model Spring MVC model for template data binding
     * @return Template name "index" (resolves to index.html)
     */
    @GetMapping("/")
    public String index(Model model) {
        List<String> emails = emailListService.loadEmailList();
        model.addAttribute("emails", emails);
        model.addAttribute("emailCount", emails.size());
        model.addAttribute("editorDefaultTextColor", editorDefaultTextColor);
        return "index";
    }

    /**
     * Sends emails to selected recipients.
     *
     * <p>Supports two sending modes:</p>
     * <ul>
     *   <li><b>individual</b>: Send one email per recipient sequentially</li>
     *   <li><b>batch</b>: Send to multiple recipients per email (BCC/To)</li>
     * </ul>
     *
     * <p>Address modes:</p>
     * <ul>
     *   <li><b>to</b>: Recipients visible to each other</li>
     *   <li><b>bcc</b>: Recipients hidden from each other</li>
     * </ul>
     *
     * @param request Email request containing subject, content, recipients, and options
     * @return SendResult with success/fail counts and error details
     */
    @PostMapping("/api/send")
    @ResponseBody
    public ResponseEntity<SendResult> sendEmails(@RequestBody EmailRequest request) {
        log.info("Received send request: subject={}, sendToAll={}, mode={}, addressMode={}",
                request.getSubject(), request.getSendToAll(), request.getSendMode(), request.getAddressMode());

        SendResult result;
        List<String> emails;

        // Determine recipient list: all emails or selected only (default to false if null)
        boolean sendToAll = Boolean.TRUE.equals(request.getSendToAll());
        if (sendToAll) {
            emails = emailListService.loadEmailList();
        } else {
            emails = Arrays.asList(request.getSelectedEmails());
        }

        String sendMode = request.getSendMode();
        String addressMode = request.getAddressMode();
        boolean useBcc = "bcc".equalsIgnoreCase(addressMode);

        log.info("Processing: sendMode={}, addressMode={}, useBcc={}, batchSize={}",
                sendMode, addressMode, useBcc, request.getBatchSize());

        // Execute appropriate sending strategy
        if ("batch".equals(sendMode)) {
            int batchSize = (request.getBatchSize() != null && request.getBatchSize() > 0) ? request.getBatchSize() : 10;
            int delayMs = (request.getDelayMs() != null && request.getDelayMs() >= 0) ? request.getDelayMs() : 500;
            log.info("Calling sendBatch with useBcc={}, delayMs={}", useBcc, delayMs);
            result = emailSenderService.sendBatch(emails, request.getSubject(), request.getHtmlContent(), batchSize, useBcc, delayMs);
        } else {
            result = emailSenderService.sendIndividual(emails, request.getSubject(), request.getHtmlContent(), useBcc);
        }

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
     *
     * <p>Called periodically by the UI to update connection status badge.</p>
     *
     * @return Connection status (connected: true/false) with message
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
}

