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

    @Test
    void convertToEmailSafeHtml_whenFontSize1_shouldConvertToMinimum14px() throws Exception {
        // Font size 1 (8pt) should be converted to 14px for Outlook compatibility
        String html = "<p><font size=\"1\">Small text</font></p>";

        String converted = invokeConvertToEmailSafeHtml(html);

        Assertions.assertThat(converted)
            .contains("font-size:14px")
            .contains("Small text")
            .doesNotContain("<font");
    }

    @Test
    void convertToEmailSafeHtml_whenInlineStyleFontSize8px_shouldConvertToMinimum14px() throws Exception {
        // Inline style font-size: 8px should be converted to 14px for Outlook compatibility
        String html = "<p><span style=\"font-size: 8px;\">Tiny text</span></p>";

        String converted = invokeConvertToEmailSafeHtml(html);

        Assertions.assertThat(converted)
            .contains("font-size:14px")
            .contains("Tiny text");
    }

    @Test
    void convertToEmailSafeHtml_whenInlineStyleFontSize8pt_shouldConvertToMinimum14px() throws Exception {
        // Inline style font-size: 8pt should be converted to 14px for Outlook compatibility
        String html = "<p><span style=\"font-size: 8pt;\">Tiny pt text</span></p>";

        String converted = invokeConvertToEmailSafeHtml(html);

        Assertions.assertThat(converted)
            .contains("font-size:14px")
            .contains("Tiny pt text");
    }

    @Test
    void convertToEmailSafeHtml_whenFontSize8AtEndOfText_shouldConvertToMinimum14px() throws Exception {
        // Font size 8 at END of text - this was causing invisible text in Outlook
        String html = "<p>Normal text <font size=\"1\">small at end</font></p>";

        String converted = invokeConvertToEmailSafeHtml(html);
        System.out.println("=== FONT SIZE 8 AT END ===");
        System.out.println(converted);
        System.out.println("=== END ===");

        Assertions.assertThat(converted)
            .contains("font-size:14px")
            .contains("small at end")
            .doesNotContain("<font");
    }

    @Test
    void convertToEmailSafeHtml_whenFontSize8InMiddleOfText_shouldConvertToMinimum14px() throws Exception {
        // Font size 8 in MIDDLE of text
        String html = "<p>Start <font size=\"1\">small middle</font> end</p>";

        String converted = invokeConvertToEmailSafeHtml(html);
        System.out.println("=== FONT SIZE 8 IN MIDDLE ===");
        System.out.println(converted);
        System.out.println("=== END ===");

        Assertions.assertThat(converted)
            .contains("font-size:14px")
            .contains("small middle")
            .doesNotContain("<font");
    }

    @Test
    void convertToEmailSafeHtml_whenWhiteText_shouldConvertToBlack() throws Exception {
        // White text should be converted to black for email clients with white background
        String html = "<p><span style=\"color: white;\">White text</span></p>";

        String converted = invokeConvertToEmailSafeHtml(html);

        Assertions.assertThat(converted)
            .contains("color: black")
            .contains("White text")
            .doesNotContain("color: white");
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
