package com.kisoft.emaillist;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Email Mass Sender Application - Main Entry Point
 *
 * <p>This Spring Boot application provides a web-based email mass sending system
 * with the following features:</p>
 * <ul>
 *   <li>Rich text email composition with Outlook-style formatting toolbar</li>
 *   <li>Recipient list management (add, remove, bulk edit)</li>
 *   <li>Individual and batch sending modes</li>
 *   <li>Template save/load functionality</li>
 *   <li>Real-time send progress tracking</li>
 *   <li>Error console for failed sends</li>
 * </ul>
 *
 * <h3>Application Architecture:</h3>
 * <pre>
 * Controller Layer:
 *   - EmailController: Handles HTTP requests for UI and API endpoints
 *
 * Service Layer:
 *   - EmailListService: Manages email recipient list (load, save, add, remove)
 *   - EmailSenderService: Sends emails via JavaMail/SMTP
 *
 * Model Layer:
 *   - EmailRequest: DTO for send request parameters
 *   - SendResult: DTO for send operation results
 * </pre>
 *
 * <h3>Configuration:</h3>
 * <p>See application.properties for:</p>
 * <ul>
 *   <li>SMTP server settings (spring.mail.*)</li>
 *   <li>Email list file path (app.email.list.path)</li>
 *   <li>Jasypt encryption settings for sensitive properties</li>
 * </ul>
 *
 * @author KiSoft
 * @version 1.0.0
 * @since 2025-12-26
 * @see com.kisoft.emaillist.controller.EmailController
 * @see com.kisoft.emaillist.service.EmailSenderService
 * @see com.kisoft.emaillist.service.EmailListService
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
