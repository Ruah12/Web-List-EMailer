package com.kisoft.emaillist.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for TemplateService HTML normalization functionality.
 * Tests the cleanup of malformed image src attributes and SVG attributes
 * that contain CSS escape sequences or leading backslashes.
 */
class TemplateServiceNormalizationTest {

    private TemplateService templateService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        templateService = new TemplateService(tempDir.toString(), true);
    }

    @Test
    @DisplayName("Debug: Check what Jsoup does with backslashes in src")
    void debugJsoupBackslashHandling() {
        String html = "<img src=\"\\20\\data:image/png;base64,TEST\">";
        org.jsoup.nodes.Document doc = org.jsoup.Jsoup.parseBodyFragment(html);
        org.jsoup.nodes.Element img = doc.selectFirst("img");
        String rawSrc = img.attr("src");
        System.out.println("DEBUG: Raw src from Jsoup: [" + rawSrc + "]");
        System.out.println("DEBUG: Raw src bytes: " + java.util.Arrays.toString(rawSrc.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        // Now check the output HTML
        doc.outputSettings().prettyPrint(false);
        String outputHtml = doc.body().html();
        System.out.println("DEBUG: Output HTML: [" + outputHtml + "]");
    }

    @Test
    @DisplayName("Should remove leading backslashes from data URL")
    void shouldRemoveLeadingBackslashesFromDataUrl() {
        String html = "<img src=\"\\data:image/png;base64,ABC123\">";
        String normalized = templateService.normalizeHtmlContent(html);
        assertThat(normalized).contains("src=\"data:image/png;base64,ABC123\"");
        assertThat(normalized).doesNotContain("\\data:");
    }

    @Test
    @DisplayName("Should remove multiple leading backslashes from data URL")
    void shouldRemoveMultipleLeadingBackslashesFromDataUrl() {
        String html = "<img src=\"\\\\\\data:image/jpeg;base64,XYZ789\">";
        String normalized = templateService.normalizeHtmlContent(html);
        assertThat(normalized).contains("src=\"data:image/jpeg;base64,XYZ789\"");
    }

    @Test
    @DisplayName("Should handle CSS escape sequence \\20 (space)")
    void shouldHandleCssEscapeSequenceSpace() {
        String html = "<img src=\"\\20\\data:image/png;base64,TEST\">";
        String normalized = templateService.normalizeHtmlContent(html);
        // \20 is space in CSS, should be converted or removed
        assertThat(normalized).contains("data:image/png;base64,TEST");
        assertThat(normalized).doesNotContain("\\20");
    }

    @Test
    @DisplayName("Should clean SVG width attribute with escape sequences")
    void shouldCleanSvgWidthAttribute() {
        String html = "<svg width=\"\\20\\\" height=\"100\"></svg>";
        String normalized = templateService.normalizeHtmlContent(html);
        // Should normalize the width attribute
        assertThat(normalized).doesNotContain("\\20\\");
    }

    @Test
    @DisplayName("Should clean SVG height attribute with escape sequences")
    void shouldCleanSvgHeightAttribute() {
        String html = "<svg width=\"100\" height=\"\\20\\\"></svg>";
        String normalized = templateService.normalizeHtmlContent(html);
        assertThat(normalized).doesNotContain("\\20\\");
    }

    @Test
    @DisplayName("Should preserve valid data URL without modification")
    void shouldPreserveValidDataUrl() {
        String validDataUrl = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==";
        String html = "<img src=\"" + validDataUrl + "\">";
        String normalized = templateService.normalizeHtmlContent(html);
        assertThat(normalized).contains(validDataUrl);
    }

    @Test
    @DisplayName("Should remove backslashes from inside data URL")
    void shouldRemoveBackslashesFromInsideDataUrl() {
        String html = "<img src=\"data:image/jpeg;base64,/9j/4AAQSkZ\\JRgAB\">";
        String normalized = templateService.normalizeHtmlContent(html);
        assertThat(normalized).contains("src=\"data:image/jpeg;base64,/9j/4AAQSkZJRgAB\"");
        assertThat(normalized).doesNotContain("\\");
    }

    @Test
    @DisplayName("Should handle null content gracefully")
    void shouldHandleNullContent() {
        String result = templateService.normalizeHtmlContent(null);
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Should handle empty content gracefully")
    void shouldHandleEmptyContent() {
        String result = templateService.normalizeHtmlContent("");
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should handle blank content gracefully")
    void shouldHandleBlankContent() {
        String result = templateService.normalizeHtmlContent("   ");
        assertThat(result).isEqualTo("   ");
    }

    @Test
    @DisplayName("Should handle content without images")
    void shouldHandleContentWithoutImages() {
        String html = "<p>Hello World</p>";
        String normalized = templateService.normalizeHtmlContent(html);
        assertThat(normalized).contains("Hello World");
    }

    @Test
    @DisplayName("Should remove newlines and tabs from data URLs")
    void shouldRemoveNewlinesAndTabsFromDataUrls() {
        String html = "<img src=\"data:image/png;base64,ABC\n\t123\">";
        String normalized = templateService.normalizeHtmlContent(html);
        assertThat(normalized).contains("data:image/png;base64,ABC123");
        assertThat(normalized).doesNotContain("\n");
        assertThat(normalized).doesNotContain("\t");
    }

    @Test
    @DisplayName("Should handle mixed escape sequences")
    void shouldHandleMixedEscapeSequences() {
        // Real-world scenario with multiple issues
        String html = "<img src=\"\\\\data:image/jpeg;base64,/9j/4AAQS\\kZ\">";
        String normalized = templateService.normalizeHtmlContent(html);
        assertThat(normalized).contains("data:image/jpeg;base64,/9j/4AAQSkZ");
        assertThat(normalized).doesNotContain("\\");
    }

    @Test
    @DisplayName("Should preserve paragraph styling")
    void shouldPreserveParagraphStyling() {
        String html = "<p style=\"color: #ff0000; font-size: 14px;\">Red text</p>";
        String normalized = templateService.normalizeHtmlContent(html);
        assertThat(normalized).contains("color:");
        assertThat(normalized).contains("font-size:");
        assertThat(normalized).contains("Red text");
    }

    @Test
    @DisplayName("Should preserve image dimensions")
    void shouldPreserveImageDimensions() {
        String html = "<img src=\"data:image/png;base64,ABC\" width=\"100\" height=\"50\">";
        String normalized = templateService.normalizeHtmlContent(html);
        assertThat(normalized).contains("width=\"100\"");
        assertThat(normalized).contains("height=\"50\"");
    }

    @Test
    @DisplayName("Should preserve inline styles")
    void shouldPreserveInlineStyles() {
        String html = "<span style=\"font-family: Arial; font-weight: bold;\">Bold Arial</span>";
        String normalized = templateService.normalizeHtmlContent(html);
        assertThat(normalized).contains("font-family:");
        assertThat(normalized).contains("font-weight:");
        assertThat(normalized).contains("Bold Arial");
    }
}

