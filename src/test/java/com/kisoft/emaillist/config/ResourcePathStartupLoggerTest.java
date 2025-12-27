package com.kisoft.emaillist.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;

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

