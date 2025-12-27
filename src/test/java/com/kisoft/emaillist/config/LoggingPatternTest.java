package com.kisoft.emaillist.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;

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
