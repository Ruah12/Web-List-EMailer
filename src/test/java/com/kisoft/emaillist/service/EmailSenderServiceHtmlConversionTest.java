package com.kisoft.emaillist.service;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * Email Sender Service HTML Conversion Tests
 *
 * <p>Unit tests for the HTML conversion logic in EmailSenderService.
 * These tests verify that editor HTML is correctly transformed into
 * email-client-compatible HTML.</p>
 *
 * <h3>Test Coverage:</h3>
 * <ul>
 *   <li>Float-to-table conversion for side-by-side image/text layouts</li>
 *   <li>Image dimension preservation during resize</li>
 *   <li>Font tag to inline style conversion</li>
 *   <li>Minimum font size enforcement for Outlook compatibility</li>
 *   <li>Whitespace preservation around converted elements</li>
 *   <li>Height attribute removal for proportional scaling</li>
 * </ul>
 *
 * <h3>Testing Approach:</h3>
 * <p>Uses reflection to access the private convertToEmailSafeHtml method
 * without requiring a full Spring context or mail server connection.</p>
 *
 * <h3>Key Scenarios:</h3>
 * <ul>
 *   <li>Floated images with adjacent text &rarr; two-column table</li>
 *   <li>Non-floated images &rarr; preserved with normalized styles</li>
 *   <li>Font size 1-7 &rarr; pixel sizes (10px minimum for Outlook)</li>
 *   <li>Inline font-size below 10px &rarr; upgraded to 10px</li>
 *   <li>Explicit height on images &rarr; removed for auto-scaling</li>
 * </ul>
 *
 * @author KiSoft
 * @version 1.0.0
 * @since 2025-12-26
 */
public class EmailSenderServiceHtmlConversionTest {

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
            // New wrapper applies base typography on a container table/td for Outlook.
            .contains("role=\"presentation\"")
            .contains("mso-line-height-rule:exactly")
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
            // Wrapper table is always present; ensure we didn't inject an extra two-column layout table.
            .doesNotContain("padding-right:15px");
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
    void convertToEmailSafeHtml_whenFontSize1_shouldConvertTo10pxAndRemoveFontTag() throws Exception {
        // Font size 1 should map to 10px (smallest Outlook-readable size) and be converted from <font> to <span>.
        // Note: 8px is invisible in Outlook, so we use 10px as minimum.
        String html = "<p><font size=\"1\">Small text</font></p>";

        String converted = invokeConvertToEmailSafeHtml(html);

        Assertions.assertThat(converted)
            .contains("font-size:10px")
            .contains("Small text")
            .doesNotContain("<font");
    }

    @Test
    void convertToEmailSafeHtml_whenInlineStyleFontSize8px_shouldUpgradeTo10px() throws Exception {
        // Inline style font-size: 8px should be upgraded to 10px for Outlook readability.
        // Outlook cannot render 8px text readably (appears invisible or as tiny dots).
        String html = "<p><span style=\"font-size: 8px;\">Tiny text</span></p>";

        String converted = invokeConvertToEmailSafeHtml(html);

        Assertions.assertThat(converted)
            .contains("font-size:10px")
            .contains("Tiny text")
            .doesNotContain("font-size: 8px");
    }

    @Test
    void convertToEmailSafeHtml_whenInlineStyleFontSize8pt_shouldUpgradeTo10px() throws Exception {
        // 8pt ≈ 10.67px, which is below the 10px minimum when converted.
        // Actually 8pt * 1.333 = 10.67px which is >= 10px, so it should be preserved.
        // But 7pt * 1.333 = 9.33px which is < 10px, so let's test that instead.
        String html = "<p><span style=\"font-size: 7pt;\">Tiny pt text</span></p>";

        String converted = invokeConvertToEmailSafeHtml(html);

        Assertions.assertThat(converted)
            .contains("font-size:10px")
            .contains("Tiny pt text")
            .doesNotContain("font-size: 7pt");
    }

    @Test
    void convertToEmailSafeHtml_whenInlineStyleFontSize9px_shouldUpgradeTo10px() throws Exception {
        // 9px is below the 10px minimum, should be upgraded.
        String html = "<p><span style=\"font-size: 9px;\">Small text</span></p>";

        String converted = invokeConvertToEmailSafeHtml(html);

        Assertions.assertThat(converted)
            .contains("font-size:10px")
            .contains("Small text")
            .doesNotContain("font-size: 9px");
    }

    @Test
    void convertToEmailSafeHtml_whenInlineStyleFontSize12px_shouldPreserve() throws Exception {
        // 12px is above the 10px minimum. We normalize to px and keep it.
        String html = "<p><span style=\"font-size: 12px;\">Normal text</span></p>";

        String converted = invokeConvertToEmailSafeHtml(html);

        Assertions.assertThat(converted)
            .contains("font-size:12px")
            .contains("Normal text");
    }

    @Test
    void convertToEmailSafeHtml_whenInlineStyleFontSize9pt_shouldNormalizeToPxAndPreserve() throws Exception {
        // 9pt ~= 12px, should normalize to px.
        String html = "<p><span style=\"font-size: 9pt;\">Pt text</span></p>";

        String converted = invokeConvertToEmailSafeHtml(html);

        Assertions.assertThat(converted)
            .contains("font-size:12px")
            .contains("Pt text")
            .doesNotContain("9pt");
    }

    @Test
    void convertToEmailSafeHtml_whenFontSize8AtEndOfText_shouldConvertTo10px() throws Exception {
        // Font size 1 at END of text should map to 10px (Outlook-readable minimum).
        String html = "<p>Normal text <font size=\"1\">small at end</font></p>";

        String converted = invokeConvertToEmailSafeHtml(html);

        Assertions.assertThat(converted)
            .contains("font-size:10px")
            .contains("small at end")
            .doesNotContain("<font");
    }

    @Test
    void convertToEmailSafeHtml_whenFontSize8InMiddleOfText_shouldConvertTo10px() throws Exception {
        // Font size 1 in MIDDLE of text should map to 10px (Outlook-readable minimum).
        String html = "<p>Start <font size=\"1\">small middle</font> end</p>";

        String converted = invokeConvertToEmailSafeHtml(html);

        Assertions.assertThat(converted)
            .contains("font-size:10px")
            .contains("small middle")
            .doesNotContain("<font");
    }

    @Test
    void convertToEmailSafeHtml_whenFontTagBetweenText_shouldPreserveWhitespace() throws Exception {
        // Critical: whitespace around <font> tags must be preserved when converting to <span>.
        // This was causing "Sunday, December 28" to become "Sunday,December28".
        String html = "<p>Sunday, <font size=\"1\">December</font> 28, 2025</p>";

        String converted = invokeConvertToEmailSafeHtml(html);

        // Verify spaces are preserved: "Sunday, " before and " 28" after
        Assertions.assertThat(converted)
            .contains("Sunday, <span")
            .contains("December</span> 28")
            .doesNotContain("Sunday,<span")
            .doesNotContain("December</span>28");
    }

    @Test
    void convertToEmailSafeHtml_whenParagraphHasNoFontSize_shouldNotInjectFontSize14px() throws Exception {
        // Default font-size is defined in the email wrapper TD.
        // We must NOT inject font-size:14px into every paragraph/span/div; it breaks mixed-size content.
        String html = "<p>Hello</p><p>World</p>";

        String converted = invokeConvertToEmailSafeHtml(html);

        Assertions.assertThat(converted)
            .contains("<p style=\"margin:0 0 10px 0; padding:0;\">Hello</p>")
            .contains("<p style=\"margin:0 0 10px 0; padding:0;\">World</p>")
            // font-size:14px is expected in the wrapper, but should not be injected into elements.
            .doesNotContain("<p style=\"font-size:14px")
            .doesNotContain("<span style=\"font-size:14px");
    }

    @Test
    void convertToEmailSafeHtml_whenImageHasExplicitHeight_shouldRemoveItForProportionalScaling() throws Exception {
        // User's browser may add height attribute or style - these must be removed for proper scaling.
        // Only width should be preserved; height should always be 'auto' for proportional display.
        String html = "<p>Text</p>" +
            "<img src=\"data:image/png;base64,CCC\" style=\"float:left; width:200px; height:150px; margin:0 15px;\">" +
            "Side text";

        String converted = invokeConvertToEmailSafeHtml(html);

        Assertions.assertThat(converted)
            .contains("width=\"200\"")
            .contains("height:auto")
            // The explicit height should NOT be in the output
            .doesNotContain("height:150px")
            .doesNotContain("height=\"150\"");
    }

    @Test
    void convertToEmailSafeHtml_whenNonFloatedImageResized_shouldPreserveWidth() throws Exception {
        // Non-floated images that have been resized should still preserve their width
        String html = "<p>Text</p>" +
            "<img src=\"data:image/png;base64,DDD\" style=\"width:300px; height:auto;\">" +
            "<p>More text</p>";

        String converted = invokeConvertToEmailSafeHtml(html);

        Assertions.assertThat(converted)
            .contains("width=\"300\"")
            .contains("height:auto")
            .contains("data:image/png;base64,DDD");
    }

    @Test
    void convertToEmailSafeHtml_whenFloatedImageWith50pxWidth_shouldPreserve50px() throws Exception {
        // This test simulates the exact scenario from the user's email:
        // A floated image with width:50px in the style attribute but no width HTML attribute
        String html = "<p>Text before</p>" +
            "<img src=\"data:image/png;base64,EEE\" style=\"float: left; margin: 0px 15px 10px 0px; display: block; width: 50px; height: auto;\">" +
            "Text beside the small image" +
            "<p>More text</p>";

        String converted = invokeConvertToEmailSafeHtml(html);

        System.out.println("Converted HTML: " + converted);

        Assertions.assertThat(converted)
            .contains("<table")
            // The image should preserve its 50px width
            .contains("width=\"50\"")
            .contains("width:50px")
            .contains("data:image/png;base64,EEE");
    }

    @Test
    void convertToEmailSafeHtml_whenInlineLineHeightUnitless_shouldConvertToPxAndAddMsoRule() throws Exception {
        String html = "<p><span style=\"font-size: 14px; line-height: 1.5;\">Line test</span></p>";

        String converted = invokeConvertToEmailSafeHtml(html);

        // 14px * 1.5 = 21px
        Assertions.assertThat(converted)
            .contains("line-height:21px")
            .contains("mso-line-height-rule:exactly")
            .contains("Line test");
    }

    @Test
    void convertToEmailSafeHtml_whenInlineLineHeightEm_shouldConvertToPxAndAddMsoRule() throws Exception {
        String html = "<p><span style=\"font-size: 12px; line-height: 2em;\">Line test 2</span></p>";

        String converted = invokeConvertToEmailSafeHtml(html);

        // 12px * 2 = 24px
        Assertions.assertThat(converted)
            .contains("line-height:24px")
            .contains("mso-line-height-rule:exactly")
            .contains("Line test 2");
    }

    private static String invokeConvertToEmailSafeHtml(String html) throws Exception {
        // No public seam today; call the private method reflectively without sending mail.
        Constructor<EmailSenderService> ctor = EmailSenderService.class.getDeclaredConstructor(
            org.springframework.mail.javamail.JavaMailSender.class
        );
        ctor.setAccessible(true);
        EmailSenderService service = ctor.newInstance((Object) null);

        Method m = EmailSenderService.class.getDeclaredMethod("convertToEmailSafeHtml", String.class);
        m.setAccessible(true);
        return (String) m.invoke(service, html);
    }
}
