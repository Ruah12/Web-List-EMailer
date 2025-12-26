package com.kisoft.emaillist.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.awt.Desktop;
import java.net.URI;

/**
 * Browser Launcher Component
 *
 * <p>Automatically opens the application in the default system browser
 * when the Spring Boot application has fully started. This works when
 * running from IntelliJ, command line, or any other method.</p>
 *
 * <p>The browser launch can be disabled by setting the property
 * {@code app.browser.open=false} in application.properties.</p>
 *
 * @author KiSoft
 * @version 1.0.0
 * @since 2025-12-26
 */
@Component
public class BrowserLauncher {

    private static final Logger log = LoggerFactory.getLogger(BrowserLauncher.class);

    @Value("${server.port:8082}")
    private int serverPort;

    @Value("${app.browser.open:true}")
    private boolean openBrowser;

    /**
     * Opens the default browser to the application URL when the application is ready.
     *
     * <p>This method is triggered by Spring's ApplicationReadyEvent, which fires
     * after the application context is fully initialized and all beans are ready.</p>
     *
     * <p>Uses Java's Desktop API to open the system default browser. If the Desktop
     * API is not supported (e.g., headless environment), falls back to OS-specific
     * commands.</p>
     */
    @EventListener(ApplicationReadyEvent.class)
    public void openBrowser() {
        if (!openBrowser) {
            log.info("Browser auto-open is disabled (app.browser.open=false)");
            return;
        }

        String url = "http://localhost:" + serverPort;
        log.info("Opening browser at: {}", url);

        try {
            // Try using Desktop API first (works on most systems with GUI)
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
                log.info("Browser opened successfully using Desktop API");
            } else {
                // Fallback to OS-specific commands
                openBrowserFallback(url);
            }
        } catch (Exception e) {
            log.warn("Failed to open browser automatically: {}. Please open {} manually.",
                    e.getMessage(), url);
        }
    }

    /**
     * Fallback method to open browser using OS-specific commands.
     *
     * @param url The URL to open
     */
    private void openBrowserFallback(String url) {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            ProcessBuilder pb;

            if (os.contains("win")) {
                // Windows
                pb = new ProcessBuilder("cmd", "/c", "start", url);
            } else if (os.contains("mac")) {
                // macOS
                pb = new ProcessBuilder("open", url);
            } else {
                // Linux/Unix
                pb = new ProcessBuilder("xdg-open", url);
            }

            pb.start();
            log.info("Browser opened successfully using OS command");
        } catch (Exception e) {
            log.warn("Fallback browser open failed: {}", e.getMessage());
        }
    }
}

