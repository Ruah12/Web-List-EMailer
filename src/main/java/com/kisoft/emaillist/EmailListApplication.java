package com.kisoft.emaillist;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Email Mass Sender Application - Main Entry Point.
 * This Spring Boot application provides a web-based email mass sending system
 * with the following features:
 * - Rich text email composition with Outlook-style formatting toolbar
 * - Recipient list management (add, remove, bulk edit)
 * - Individual and batch sending modes
 * - Template save/load functionality (configurable via {@code app.template.slots})
 * - Real-time send progress tracking
 * - Error console for failed sends
 * - Export to PDF and DOCX formats
 * - Facebook integration for social posting
 * Application Architecture:
 * - Controller Layer: {@link com.kisoft.emaillist.controller.EmailController} handles HTTP requests
 * - Service Layer: {@link com.kisoft.emaillist.service.EmailSenderService} sends emails,
 *   {@link com.kisoft.emaillist.service.EmailListService} manages recipients,
 *   {@link com.kisoft.emaillist.service.ExportService} handles document export,
 *   {@link com.kisoft.emaillist.service.FacebookService} handles social posting
 * - Model Layer: {@link com.kisoft.emaillist.model.EmailRequest} and
 *   {@link com.kisoft.emaillist.model.SendResult} DTOs
 * Configuration in {@code application.properties}:
 * - SMTP server settings ({@code spring.mail.*})
 * - Email list file path ({@code email.list.file})
 * - Jasypt encryption settings for sensitive properties
 * - Template slots ({@code app.template.slots})
 * @author KiSoft
 * @version 1.0.0
 * @since 2025-12-26
 * @see com.kisoft.emaillist.controller.EmailController
 * @see com.kisoft.emaillist.service.EmailSenderService
 * @see com.kisoft.emaillist.service.EmailListService
 * @see com.kisoft.emaillist.service.ExportService
 * @see com.kisoft.emaillist.service.FacebookService
 */
@SpringBootApplication
public class EmailListApplication {

    /**
     * Application entry point.
     *
     * <p>Starts the embedded Tomcat server and initializes Spring context.</p>
     *
     * @param args Command line arguments (not used)
     */
    public static void main(String[] args) {
        SpringApplication.run(EmailListApplication.class, args);
    }

}
