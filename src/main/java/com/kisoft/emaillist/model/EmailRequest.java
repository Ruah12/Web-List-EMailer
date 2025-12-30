package com.kisoft.emaillist.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Email Request DTO - Contains all parameters for a send email request.
 * This class is used to deserialize JSON requests from the frontend
 * for the {@code /api/send} endpoint.
 * Fields:
 * - {@code subject} - Email subject line
 * - {@code htmlContent} - HTML body of the email
 * - {@code sendToAll} - If true, send to all emails in the list
 * - {@code selectedEmails} - Array of selected recipient addresses
 * - {@code sendMode} - {@code "batch"} or {@code "individual"}
 * - {@code addressMode} - {@code "to"} or {@code "bcc"}
 * - {@code batchSize} - Number of recipients per batch (default: 10)
 * - {@code delayMs} - Delay between sends in milliseconds (default: 500)
 * @author KiSoft
 * @version 1.0.0
 * @since 2025-12-26
 * @see com.kisoft.emaillist.controller.EmailController
 * @see com.kisoft.emaillist.service.EmailSenderService
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmailRequest {

    /** Email subject line */
    private String subject;

    /** HTML content of the email body */
    private String htmlContent;

    /** If true, send to all emails in the list; otherwise use selectedEmails (wrapper type allows null from JSON) */
    private Boolean sendToAll = false;

    /** Array of selected recipient email addresses */
    private String[] selectedEmails;

    /** Send mode: "batch" (multiple per email) or "individual" (one at a time) */
    private String sendMode;

    /** Address mode: "to" (visible) or "bcc" (hidden) */
    private String addressMode;

    /** Number of recipients per batch email (default: 10, wrapper type allows null from JSON) */
    private Integer batchSize = 10;

    /** Delay in milliseconds between batch sends (default: 500, wrapper type allows null from JSON) */
    private Integer delayMs = 500;
}
