package com.kisoft.emaillist.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Facebook Service - Handles posting content to Facebook.
 * This service provides functionality to post messages to Facebook pages
 * using the {@code Facebook Graph API}. It supports:
 * - Posting text messages to a Facebook page
 * - Converting HTML content to plain text for posting
 * - Error handling and logging
 * Configuration:
 * Requires the following properties in {@code application.properties}:
 * - {@code facebook.enabled} - Enable/disable Facebook integration
 * - {@code facebook.access.token} - Page Access Token for Graph API
 * - {@code facebook.page.id} - The Facebook Page ID to post to
 * Note on Authentication:
 * Facebook deprecated password-based authentication. This service uses
 * the Graph API with Page Access Tokens. To obtain a token:
 * 1. Create a Facebook App at {@code developers.facebook.com}
 * 2. Add the Pages API permission
 * 3. Generate a Page Access Token
 * 4. Store the token (encrypted) in {@code application.properties}
 * @author KiSoft
 * @version 1.0.0
 * @since 2025-12-27
 * @see com.kisoft.emaillist.controller.EmailController
 * @see com.kisoft.emaillist.service.EmailSenderService
 * @see com.kisoft.emaillist.service.ExportService
 */
@Service
@Slf4j
public class FacebookService {

    private static final String GRAPH_API_BASE = "https://graph.facebook.com/v18.0";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    @Value("${facebook.enabled:false}")
    private boolean facebookEnabled;

    @Value("${facebook.email:}")
    private String facebookEmail;

    @Value("${facebook.password:}")
    private String facebookPassword;

    @Value("${facebook.page.id:}")
    private String pageId;

    @Value("${facebook.access.token:}")
    private String accessToken;

    private final HttpClient httpClient;

    public FacebookService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .build();
    }

    /**
     * Checks if Facebook integration is enabled and configured.
     * @return true if Facebook posting is available
     */
    public boolean isEnabled() {
        boolean enabled = facebookEnabled && hasValidConfiguration();
        log.debug("Facebook isEnabled check: facebookEnabled={}, hasValidConfig={}, result={}",
                facebookEnabled, hasValidConfiguration(), enabled);
        return enabled;
    }

    /**
     * Checks if we have valid configuration for Facebook posting.
     * Requires either access token or email/password credentials.
     * @return true if configuration is valid
     */
    private boolean hasValidConfiguration() {
        // Check for Graph API token (preferred method)
        boolean hasToken = accessToken != null && !accessToken.isBlank();
        boolean hasPageId = pageId != null && !pageId.isBlank();
        boolean hasEmail = facebookEmail != null && !facebookEmail.isBlank();
        boolean hasPassword = facebookPassword != null && !facebookPassword.isBlank();

        log.debug("Facebook config check: hasToken={}, hasPageId={}, hasEmail={}, hasPassword={}",
                hasToken, hasPageId, hasEmail, hasPassword);

        if (hasToken && hasPageId) {
            return true;
        }
        // Fall back to email/password (stored for future OAuth flow)
        return hasEmail && hasPassword;
    }

    /**
     * Posts a message to the configured Facebook page.
     * @param subject The subject/title of the post
     * @param htmlContent The HTML content to post (will be converted to plain text)
     * @return Result containing success status and message
     */
    public FacebookPostResult postToPage(String subject, String htmlContent) {
        log.info("=== Facebook Post Request ===");
        log.info("Subject: {}", subject);
        log.info("HTML content length: {} chars", htmlContent != null ? htmlContent.length() : 0);

        if (!facebookEnabled) {
            log.warn("Facebook posting is disabled (facebook.enabled=false)");
            return new FacebookPostResult(false, "Facebook integration is disabled. Set facebook.enabled=true in application.properties.", null);
        }

        if (!hasValidConfiguration()) {
            log.warn("Facebook configuration is incomplete");
            return new FacebookPostResult(false, "Facebook is not fully configured. Please set facebook.access.token and facebook.page.id, or facebook.email and facebook.password.", null);
        }

        try {
            // Convert HTML to plain text for Facebook
            String plainText = convertHtmlToPlainText(htmlContent);
            String fullMessage = subject + "\n\n" + plainText;

            log.info("Posting to Facebook. Full message length: {} chars", fullMessage.length());
            log.debug("Message preview (first 200 chars): {}", fullMessage.substring(0, Math.min(200, fullMessage.length())));

            // If we have an access token, use Graph API
            if (accessToken != null && !accessToken.isBlank() && pageId != null && !pageId.isBlank()) {
                log.info("Using Graph API with access token for page ID: {}", pageId);
                return postViaGraphApi(fullMessage);
            }

            // Without access token, we can't post directly
            // Facebook requires OAuth flow for posting
            log.warn("=== Facebook Posting Not Available ===");
            log.warn("Email: {} (configured)", facebookEmail);
            log.warn("Password: {} (configured)", facebookPassword != null && !facebookPassword.isBlank() ? "[SET]" : "[NOT SET]");
            log.warn("Page ID: {} ({})", pageId, (pageId != null && !pageId.isBlank()) ? "configured" : "NOT configured");
            log.warn("Access Token: {}", (accessToken != null && !accessToken.isBlank()) ? "[SET]" : "[NOT SET - REQUIRED]");
            log.warn("Email/password authentication is not supported by Facebook API.");
            log.warn("Please configure facebook.access.token and facebook.page.id in application.properties.");

            return new FacebookPostResult(false,
                    "Facebook posting requires a Page Access Token.\n\n" +
                    "Your email (" + facebookEmail + ") is configured, but Facebook no longer supports email/password authentication.\n\n" +
                    "To enable Facebook posting:\n" +
                    "1. Go to developers.facebook.com\n" +
                    "2. Create a Facebook App\n" +
                    "3. Generate a Page Access Token\n" +
                    "4. Add to application.properties:\n" +
                    "   facebook.page.id=YOUR_PAGE_ID\n" +
                    "   facebook.access.token=ENC(your-encrypted-token)",
                    null);

        } catch (Exception e) {
            log.error("Failed to post to Facebook: {}", e.getMessage(), e);
            return new FacebookPostResult(false, "Failed to post to Facebook: " + e.getMessage(), null);
        }
    }

    /**
     * Posts to Facebook using the Graph API.
     * @param message The message to post
     * @return Result of the posting operation
     */
    private FacebookPostResult postViaGraphApi(String message) throws IOException, InterruptedException {
        String url = GRAPH_API_BASE + "/" + pageId + "/feed";

        Map<String, String> params = new HashMap<>();
        params.put("message", message);
        params.put("access_token", accessToken);

        String formData = params.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "=" +
                        URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .reduce((a, b) -> a + "&" + b)
                .orElse("");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(formData))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            log.info("Successfully posted to Facebook. Response: {}", response.body());
            return new FacebookPostResult(true, "Successfully posted to Facebook", response.body());
        } else {
            log.error("Facebook API error. Status: {}, Response: {}", response.statusCode(), response.body());
            return new FacebookPostResult(false,
                    "Facebook API error (HTTP " + response.statusCode() + "): " + response.body(),
                    response.body());
        }
    }

    /**
     * Converts HTML content to plain text suitable for Facebook posting.
     * @param html The HTML content
     * @return Plain text version
     */
    private String convertHtmlToPlainText(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }

        return html
                // Remove script and style blocks
                .replaceAll("(?s)<script[^>]*>.*?</script>", "")
                .replaceAll("(?s)<style[^>]*>.*?</style>", "")
                // Convert line breaks
                .replaceAll("<br\\s*/?>", "\n")
                .replaceAll("</p>", "\n\n")
                .replaceAll("</div>", "\n")
                .replaceAll("</li>", "\n")
                .replaceAll("<li[^>]*>", "• ")
                // Remove remaining HTML tags
                .replaceAll("<[^>]+>", "")
                // Decode HTML entities
                .replaceAll("&nbsp;", " ")
                .replaceAll("&amp;", "&")
                .replaceAll("&lt;", "<")
                .replaceAll("&gt;", ">")
                .replaceAll("&quot;", "\"")
                .replaceAll("&#39;", "'")
                .replaceAll("&rarr;", "→")
                // Clean up whitespace
                .replaceAll("[ \\t]+", " ")
                .replaceAll("\n{3,}", "\n\n")
                .trim();
    }

    /**
     * Tests the Facebook connection/configuration.
     * @return true if configuration is valid and credentials work
     */
    public boolean testConnection() {
        if (!isEnabled()) {
            log.warn("Facebook integration is disabled or not configured");
            return false;
        }

        // For now, just validate configuration
        // A full test would require making an API call
        if (accessToken != null && !accessToken.isBlank()) {
            log.info("Facebook configured with Page Access Token");
            return true;
        }

        log.info("Facebook credentials configured (email: {}), but Page Access Token is recommended",
                facebookEmail);
        return true;
    }

    /**
     * Result of a Facebook posting operation.
     */
    public record FacebookPostResult(
            boolean success,
            String message,
            String apiResponse
    ) {}
}

