package com.kisoft.emaillist.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Send Result DTO - Contains the results of an email send operation.
 *
 * <p>This class is returned by the /api/send endpoint and contains:</p>
 * <ul>
 *   <li>Total count of emails attempted</li>
 *   <li>Success and failure counts</li>
 *   <li>List of failed email addresses</li>
 *   <li>Map of error messages per failed email</li>
 *   <li>Human-readable summary message</li>
 * </ul>
 *
 * <h3>Usage:</h3>
 * <pre>
 * SendResult result = new SendResult(10, 0, 0, "", new ArrayList<>());
 * // ... send emails ...
 * result.setSuccessCount(8);
 * result.setFailCount(2);
 * result.addErrorMessage("bad@email.com", "SMTP error: Connection refused");
 * </pre>
 *
 * @author KiSoft
 * @version 1.0.0
 * @since 2025-12-26
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SendResult {

    /** Total number of emails in the send operation */
    private int totalEmails;

    /** Number of emails sent successfully */
    private int successCount;

    /** Number of emails that failed to send */
    private int failCount;

    /** Human-readable summary message */
    private String message;

    /** List of email addresses that failed */
    private List<String> failedEmails;

    /** Map of email address -> error message for detailed error reporting */
    private Map<String, String> errorMessages = new HashMap<>();

    /**
     * Convenience constructor without error messages map.
     *
     * @param totalEmails Total emails to send
     * @param successCount Initially 0
     * @param failCount Initially 0
     * @param message Summary message
     * @param failedEmails List for failed emails
     */
    public SendResult(int totalEmails, int successCount, int failCount, String message, List<String> failedEmails) {
        this.totalEmails = totalEmails;
        this.successCount = successCount;
        this.failCount = failCount;
        this.message = message;
        this.failedEmails = failedEmails;
        this.errorMessages = new HashMap<>();
    }

    /**
     * Adds an error message for a specific email address.
     *
     * @param email The email address that failed
     * @param error The error message describing the failure
     */
    public void addErrorMessage(String email, String error) {
        if (errorMessages == null) {
            errorMessages = new HashMap<>();
        }
        errorMessages.put(email, error);
    }
}
