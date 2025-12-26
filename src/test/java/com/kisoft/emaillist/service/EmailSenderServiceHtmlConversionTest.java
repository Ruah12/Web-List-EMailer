package com.kisoft.emaillist.service;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

class EmailSenderServiceHtmlConversionTest {

    @Test
    void convertToEmailSafeHtml_whenFloatLeftImage_shouldRewriteToTwoColumnTable() throws Exception {
        String html = "<p>Intro</p>" +
            "<img src=\"data:image/png;base64,AAA\" style=\"float:left; margin:0 15px 10px 0; max-width:40%; height:auto;\">" +
            "Some text next to image" +
            "<br>More text" +
            "<p>Next paragraph</p>";

        String converted = invokeConvertToEmailSafeHtml(html);

        Assertions.assertThat(converted)
            .contains("<table")
            // Uses 100% width so text wraps based on email client window size
            .contains("width=\"100%\"")       // Table width - flexible
            .contains("width=\"260\"")        // Image column width (default)
            .contains("data:image/png")
            .doesNotContain("float:left")
            .contains("Some text next to image")
            .contains("Next paragraph")
            // Check for MSO compatibility styles
            .contains("mso-table-lspace")
            .contains("mso-table-rspace");
    }

    @Test
    void convertToEmailSafeHtml_whenNoFloatLeftImage_shouldKeepContentWithoutInjectingTable() throws Exception {
        String html = "<p>Hello</p><img src=\"data:image/png;base64,AAA\" style=\"max-width:40%;\"><p>World</p>";

        String converted = invokeConvertToEmailSafeHtml(html);

        Assertions.assertThat(converted)
            .contains("Hello")
            .contains("<img")
            .contains("World")
            .doesNotContain("<table");
    }

    @Test
    void convertToEmailSafeHtml_whenImageResized_shouldPreserveExactDimensions() throws Exception {
        // User resized image to 150px in editor
        String html = "<p>Text</p>" +
            "<img src=\"data:image/png;base64,BBB\" style=\"float:left; width:150px; margin:0 15px 10px 0; height:auto;\">" +
            "Text beside small image";

        String converted = invokeConvertToEmailSafeHtml(html);

        Assertions.assertThat(converted)
            .contains("<table")
            // Image column should use the exact user-specified width (150px)
            .contains("width=\"150\"")
            .contains("data:image/png;base64,BBB")
            .contains("Text beside small image");
    }

    private static String invokeConvertToEmailSafeHtml(String html) throws Exception {
        // No public seam today; call the private method reflectively without sending mail.
        Constructor<EmailSenderService> ctor = EmailSenderService.class.getDeclaredConstructor(
            org.springframework.mail.javamail.JavaMailSender.class,
            EmailListService.class
        );
        ctor.setAccessible(true);
        EmailSenderService service = ctor.newInstance(null, null);

        Method m = EmailSenderService.class.getDeclaredMethod("convertToEmailSafeHtml", String.class);
        m.setAccessible(true);
        return (String) m.invoke(service, html);
    }
}
