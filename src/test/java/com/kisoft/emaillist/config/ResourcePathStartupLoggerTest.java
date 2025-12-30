package com.kisoft.emaillist.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for ResourcePathStartupLogger.
 *
 * <p>These tests verify that the application logs resolved resource paths
 * at startup. This helps developers confirm which configuration files
 * are being used (especially important when running from different contexts
 * like IDE, JAR, or Docker).</p>
 *
 * <h3>Test Coverage:</h3>
 * <ul>
 *   <li>Verifies "Resolved configuration/resource locations" is logged</li>
 *   <li>Verifies email list paths (external and classpath) are logged</li>
 *   <li>Verifies template index path is logged</li>
 * </ul>
 *
 * <h3>Configuration:</h3>
 * <p>Test runs with {@code app.logging.resource-paths.enabled=true} and
 * {@code app.browser.open=false} to enable logging while preventing
 * browser auto-open during test execution.</p>
 *
 * @author KiSoft
 * @version 1.0.0
 * @since 2025-12-26
 * @see ResourcePathStartupLogger
 */
@SpringBootTest(properties = {
    "app.logging.resource-paths.enabled=true",
    // Avoid opening a GUI browser during tests
    "app.browser.open=false"
})
@ExtendWith(OutputCaptureExtension.class)
class ResourcePathStartupLoggerTest {

    @Test
    void logsResourceLocationsOnStartup(CapturedOutput output) {
        assertThat(output.getOut()).contains("Resolved configuration/resource locations");
        assertThat(output.getOut()).contains("emailList.external=");
        assertThat(output.getOut()).contains("emailList.classpath=");
        assertThat(output.getOut()).contains("template.index=");
    }
}
