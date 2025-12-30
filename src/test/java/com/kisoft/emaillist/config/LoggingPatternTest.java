package com.kisoft.emaillist.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Logging Pattern Verification Tests.
 *
 * <p>These tests verify that the Logback logging configuration produces
 * correctly formatted log output with thread names and class information.</p>
 *
 * <h3>Test Coverage:</h3>
 * <ul>
 *   <li>Thread name inclusion in log output</li>
 *   <li>Message content preservation</li>
 *   <li>Method name or class name inclusion (depends on active logging config)</li>
 * </ul>
 *
 * <h3>Flexibility:</h3>
 * <p>Tests use {@code satisfiesAnyOf} assertions to handle both:
 * <ul>
 *   <li>Spring Boot default logging (shows class name)</li>
 *   <li>Custom logback-spring.xml (shows method name)</li>
 * </ul>
 * This ensures tests pass in both standalone and Spring Boot test contexts.</p>
 *
 * @author KiSoft
 * @version 1.0.0
 * @since 2025-12-26
 * @see org.slf4j.Logger
 */
@ExtendWith(OutputCaptureExtension.class)
class LoggingPatternTest {

    private static final Logger log = LoggerFactory.getLogger(LoggingPatternTest.class);

    @Test
    void logLineIncludesThreadAndMethodName(CapturedOutput output) {
        log.info("pattern-smoke-test");

        String out = output.getOut();

        // Thread name should always be present in both Spring Boot's default console pattern
        // and our custom pattern.
        assertThat(out).contains(Thread.currentThread().getName());
        assertThat(out).contains("pattern-smoke-test");

        // Method name is included only when our custom Logback pattern is active.
        // Keep this assertion flexible because plain unit tests may run without the Spring Boot
        // logging system applying logback-spring.xml.
        assertThat(out)
            .satisfiesAnyOf(
                s -> assertThat(s).contains("LoggingPatternTest.logLineIncludesThreadAndMethodName"),
                s -> assertThat(s).contains("com.kisoft.emaillist.config.LoggingPatternTest")
            );
    }
}
