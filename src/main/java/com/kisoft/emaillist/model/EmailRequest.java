package com.kisoft.emaillist.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Email Request DTO - Contains all parameters for a send email request.
 *
 * <p>This class is used to deserialize JSON requests from the frontend
 * for the /api/send endpoint.</p>
 *
 * <h3>Fields:</h3>
 * <ul>
 *   <li><b>subject</b>: Email subject line</li>
 *   <li><b>htmlContent</b>: HTML body of the email</li>
 *   <li><b>sendToAll</b>: If true, send to all emails in the list</li>
 *   <li><b>selectedEmails</b>: Array of selected recipient addresses</li>
 *   <li><b>sendMode</b>: "batch" or "individual"</li>
 *   <li><b>addressMode</b>: "to" or "bcc"</li>
 *   <li><b>batchSize</b>: Number of recipients per batch (default: 10)</li>
 *   <li><b>delaySeconds</b>: Delay between sends (default: 2)</li>
 * </ul>
 *
 * @author KiSoft
 * @version 1.0.0
 * @since 2025-12-26
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

    /** Delay in seconds between sends (default: 2, wrapper type allows null from JSON) */
    private Integer delaySeconds = 2;
}
