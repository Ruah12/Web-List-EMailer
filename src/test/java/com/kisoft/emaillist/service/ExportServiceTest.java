package com.kisoft.emaillist.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ExportService.
 * Tests PDF and DOCX export functionality.
 * Test Coverage:
 * - PDF export with various HTML content
 * - DOCX export with various HTML content
 * - HTML sanitization for export
 * - Image handling in exports
 * - Error handling
 * @author KiSoft
 * @version 1.0.0
 * @since 2025-12-29
 * @see ExportService
 */
class ExportServiceTest {

    private ExportService exportService;

    @BeforeEach
    void setUp() {
        exportService = new ExportService();
    }

    @Nested
    @DisplayName("PDF Export Tests")
    class PdfExportTests {

        @Test
        @DisplayName("Should export simple HTML to PDF")
        void exportToPdf_shouldExportSimpleHtml() throws IOException {
            // Arrange
            String subject = "Test Subject";
            String htmlContent = "<p>Hello World</p>";

            // Act
            byte[] pdfBytes = exportService.exportToPdf(subject, htmlContent);

            // Assert
            assertThat(pdfBytes).isNotNull();
            assertThat(pdfBytes.length).isGreaterThan(0);
            // PDF files start with %PDF
            assertThat(new String(pdfBytes, 0, 4)).isEqualTo("%PDF");
        }

        @Test
        @DisplayName("Should export HTML with formatting to PDF")
        void exportToPdf_shouldExportFormattedHtml() throws IOException {
            // Arrange
            String subject = "Formatted Content";
            String htmlContent = "<p><strong>Bold</strong> and <em>italic</em> text</p>";

            // Act
            byte[] pdfBytes = exportService.exportToPdf(subject, htmlContent);

            // Assert
            assertThat(pdfBytes).isNotNull();
            assertThat(pdfBytes.length).isGreaterThan(0);
        }

        @Test
        @DisplayName("Should export HTML with tables to PDF")
        void exportToPdf_shouldExportTables() throws IOException {
            // Arrange
            String subject = "Table Content";
            String htmlContent = "<table><tr><td>Cell 1</td><td>Cell 2</td></tr></table>";

            // Act
            byte[] pdfBytes = exportService.exportToPdf(subject, htmlContent);

            // Assert
            assertThat(pdfBytes).isNotNull();
            assertThat(pdfBytes.length).isGreaterThan(0);
        }

        @Test
        @DisplayName("Should export HTML with links to PDF")
        void exportToPdf_shouldExportLinks() throws IOException {
            // Arrange
            String subject = "Links";
            String htmlContent = "<p>Visit <a href=\"https://example.com\">Example</a></p>";

            // Act
            byte[] pdfBytes = exportService.exportToPdf(subject, htmlContent);

            // Assert
            assertThat(pdfBytes).isNotNull();
            assertThat(pdfBytes.length).isGreaterThan(0);
        }

        @Test
        @DisplayName("Should export HTML with colored text to PDF")
        void exportToPdf_shouldExportColoredText() throws IOException {
            // Arrange
            String subject = "Colors";
            String htmlContent = "<p style=\"color: red;\">Red text</p><p style=\"color: #00ff00;\">Green text</p>";

            // Act
            byte[] pdfBytes = exportService.exportToPdf(subject, htmlContent);

            // Assert
            assertThat(pdfBytes).isNotNull();
            assertThat(pdfBytes.length).isGreaterThan(0);
        }

        @Test
        @DisplayName("Should handle empty HTML content")
        void exportToPdf_shouldHandleEmptyContent() throws IOException {
            // Arrange
            String subject = "Empty";
            String htmlContent = "";

            // Act
            byte[] pdfBytes = exportService.exportToPdf(subject, htmlContent);

            // Assert
            assertThat(pdfBytes).isNotNull();
            assertThat(pdfBytes.length).isGreaterThan(0);
        }

        @Test
        @DisplayName("Should export HTML with embedded base64 image to PDF")
        void exportToPdf_shouldExportBase64Images() throws IOException {
            // Arrange
            String subject = "With Image";
            // Small 1x1 transparent PNG as base64
            String base64Png = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==";
            String htmlContent = "<p>Image below:</p><img src=\"data:image/png;base64," + base64Png + "\">";

            // Act
            byte[] pdfBytes = exportService.exportToPdf(subject, htmlContent);

            // Assert
            assertThat(pdfBytes).isNotNull();
            assertThat(pdfBytes.length).isGreaterThan(0);
        }
    }

    @Nested
    @DisplayName("DOCX Export Tests")
    class DocxExportTests {

        @Test
        @DisplayName("Should export simple HTML to DOCX")
        void exportToDocx_shouldExportSimpleHtml() throws IOException {
            // Arrange
            String subject = "Test Subject";
            String htmlContent = "<p>Hello World</p>";

            // Act
            byte[] docxBytes = exportService.exportToDocx(subject, htmlContent);

            // Assert
            assertThat(docxBytes).isNotNull();
            assertThat(docxBytes.length).isGreaterThan(0);
            // DOCX files are ZIP archives starting with PK
            assertThat(docxBytes[0]).isEqualTo((byte) 'P');
            assertThat(docxBytes[1]).isEqualTo((byte) 'K');
        }

        @Test
        @DisplayName("Should export HTML with formatting to DOCX")
        void exportToDocx_shouldExportFormattedHtml() throws IOException {
            // Arrange
            String subject = "Formatted Content";
            String htmlContent = "<p><strong>Bold</strong> and <em>italic</em> and <u>underline</u></p>";

            // Act
            byte[] docxBytes = exportService.exportToDocx(subject, htmlContent);

            // Assert
            assertThat(docxBytes).isNotNull();
            assertThat(docxBytes.length).isGreaterThan(0);
        }

        @Test
        @DisplayName("Should export HTML with lists to DOCX")
        void exportToDocx_shouldExportLists() throws IOException {
            // Arrange
            String subject = "Lists";
            String htmlContent = "<ul><li>Item 1</li><li>Item 2</li></ul>";

            // Act
            byte[] docxBytes = exportService.exportToDocx(subject, htmlContent);

            // Assert
            assertThat(docxBytes).isNotNull();
            assertThat(docxBytes.length).isGreaterThan(0);
        }

        @Test
        @DisplayName("Should export HTML with paragraphs to DOCX")
        void exportToDocx_shouldExportParagraphs() throws IOException {
            // Arrange
            String subject = "Paragraphs";
            String htmlContent = "<p>First paragraph</p><p>Second paragraph</p><p>Third paragraph</p>";

            // Act
            byte[] docxBytes = exportService.exportToDocx(subject, htmlContent);

            // Assert
            assertThat(docxBytes).isNotNull();
            assertThat(docxBytes.length).isGreaterThan(0);
        }

        @Test
        @DisplayName("Should export HTML with headings to DOCX")
        void exportToDocx_shouldExportHeadings() throws IOException {
            // Arrange
            String subject = "Headings";
            String htmlContent = "<h1>Heading 1</h1><h2>Heading 2</h2><p>Content</p>";

            // Act
            byte[] docxBytes = exportService.exportToDocx(subject, htmlContent);

            // Assert
            assertThat(docxBytes).isNotNull();
            assertThat(docxBytes.length).isGreaterThan(0);
        }

        @Test
        @DisplayName("Should handle empty HTML content")
        void exportToDocx_shouldHandleEmptyContent() throws IOException {
            // Arrange
            String subject = "Empty";
            String htmlContent = "";

            // Act
            byte[] docxBytes = exportService.exportToDocx(subject, htmlContent);

            // Assert
            assertThat(docxBytes).isNotNull();
            assertThat(docxBytes.length).isGreaterThan(0);
        }

        @Test
        @DisplayName("Should export HTML with line breaks to DOCX")
        void exportToDocx_shouldExportLineBreaks() throws IOException {
            // Arrange
            String subject = "Line Breaks";
            String htmlContent = "<p>Line 1<br>Line 2<br>Line 3</p>";

            // Act
            byte[] docxBytes = exportService.exportToDocx(subject, htmlContent);

            // Assert
            assertThat(docxBytes).isNotNull();
            assertThat(docxBytes.length).isGreaterThan(0);
        }

        @Test
        @DisplayName("Should export HTML with base64 image to DOCX")
        void exportToDocx_shouldExportBase64Images() throws IOException {
            // Arrange
            String subject = "With Image";
            // Small 1x1 transparent PNG as base64
            String base64Png = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==";
            String htmlContent = "<p>Image:</p><img src=\"data:image/png;base64," + base64Png + "\">";

            // Act
            byte[] docxBytes = exportService.exportToDocx(subject, htmlContent);

            // Assert
            assertThat(docxBytes).isNotNull();
            assertThat(docxBytes.length).isGreaterThan(0);
        }
    }

    @Nested
    @DisplayName("HTML Sanitization Tests")
    class HtmlSanitizationTests {

        @Test
        @DisplayName("Should handle Microsoft Office markup in PDF export")
        void exportToPdf_shouldHandleMicrosoftMarkup() throws IOException {
            // Arrange
            String subject = "Office Content";
            // Simulated Microsoft Office HTML with namespaced elements
            String htmlContent = "<p class=\"MsoNormal\">Office content</p>";

            // Act
            byte[] pdfBytes = exportService.exportToPdf(subject, htmlContent);

            // Assert
            assertThat(pdfBytes).isNotNull();
            assertThat(pdfBytes.length).isGreaterThan(0);
        }

        @Test
        @DisplayName("Should handle Microsoft Office markup in DOCX export")
        void exportToDocx_shouldHandleMicrosoftMarkup() throws IOException {
            // Arrange
            String subject = "Office Content";
            String htmlContent = "<p class=\"MsoNormal\">Office content</p>";

            // Act
            byte[] docxBytes = exportService.exportToDocx(subject, htmlContent);

            // Assert
            assertThat(docxBytes).isNotNull();
            assertThat(docxBytes.length).isGreaterThan(0);
        }

        @Test
        @DisplayName("Should handle special characters in export")
        void exportToPdf_shouldHandleSpecialCharacters() throws IOException {
            // Arrange
            String subject = "Special Chars";
            String htmlContent = "<p>&amp; &lt; &gt; &quot; &apos;</p>";

            // Act
            byte[] pdfBytes = exportService.exportToPdf(subject, htmlContent);

            // Assert
            assertThat(pdfBytes).isNotNull();
            assertThat(pdfBytes.length).isGreaterThan(0);
        }

        @Test
        @DisplayName("Should handle Unicode characters in export")
        void exportToPdf_shouldHandleUnicode() throws IOException {
            // Arrange
            String subject = "Unicode";
            String htmlContent = "<p>日本語 中文 한국어</p>";

            // Act
            byte[] pdfBytes = exportService.exportToPdf(subject, htmlContent);

            // Assert
            assertThat(pdfBytes).isNotNull();
            assertThat(pdfBytes.length).isGreaterThan(0);
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should handle null subject in PDF export")
        void exportToPdf_shouldHandleNullSubject() throws IOException {
            // Act
            byte[] pdfBytes = exportService.exportToPdf(null, "<p>Content</p>");

            // Assert
            assertThat(pdfBytes).isNotNull();
        }

        @Test
        @DisplayName("Should handle null subject in DOCX export")
        void exportToDocx_shouldHandleNullSubject() throws IOException {
            // Act
            byte[] docxBytes = exportService.exportToDocx(null, "<p>Content</p>");

            // Assert
            assertThat(docxBytes).isNotNull();
        }

        @Test
        @DisplayName("Should handle empty content in PDF export")
        void exportToPdf_shouldHandleEmptyContentInput() throws IOException {
            // Act
            byte[] pdfBytes = exportService.exportToPdf("Subject", "");

            // Assert
            assertThat(pdfBytes).isNotNull();
        }

        @Test
        @DisplayName("Should handle empty content in DOCX export")
        void exportToDocx_shouldHandleEmptyContentInput() throws IOException {
            // Act
            byte[] docxBytes = exportService.exportToDocx("Subject", "");

            // Assert
            assertThat(docxBytes).isNotNull();
        }

        @Test
        @DisplayName("Should handle malformed HTML in PDF export")
        void exportToPdf_shouldHandleMalformedHtml() throws IOException {
            // Arrange
            String malformedHtml = "<p>Unclosed paragraph<div>Nested improperly</p></div>";

            // Act
            byte[] pdfBytes = exportService.exportToPdf("Malformed", malformedHtml);

            // Assert
            assertThat(pdfBytes).isNotNull();
            assertThat(pdfBytes.length).isGreaterThan(0);
        }

        @Test
        @DisplayName("Should handle malformed HTML in DOCX export")
        void exportToDocx_shouldHandleMalformedHtml() throws IOException {
            // Arrange
            String malformedHtml = "<p>Unclosed paragraph<div>Nested improperly</p></div>";

            // Act
            byte[] docxBytes = exportService.exportToDocx("Malformed", malformedHtml);

            // Assert
            assertThat(docxBytes).isNotNull();
            assertThat(docxBytes.length).isGreaterThan(0);
        }
    }

    @Nested
    @DisplayName("Content Size Tests")
    class ContentSizeTests {

        @Test
        @DisplayName("Should export large HTML content to PDF")
        void exportToPdf_shouldHandleLargeContent() throws IOException {
            // Arrange
            StringBuilder largeContent = new StringBuilder();
            for (int i = 0; i < 100; i++) {
                largeContent.append("<p>Paragraph ").append(i).append(" with some content.</p>");
            }

            // Act
            byte[] pdfBytes = exportService.exportToPdf("Large Content", largeContent.toString());

            // Assert
            assertThat(pdfBytes).isNotNull();
            assertThat(pdfBytes.length).isGreaterThan(1000);
        }

        @Test
        @DisplayName("Should export large HTML content to DOCX")
        void exportToDocx_shouldHandleLargeContent() throws IOException {
            // Arrange
            StringBuilder largeContent = new StringBuilder();
            for (int i = 0; i < 100; i++) {
                largeContent.append("<p>Paragraph ").append(i).append(" with some content.</p>");
            }

            // Act
            byte[] docxBytes = exportService.exportToDocx("Large Content", largeContent.toString());

            // Assert
            assertThat(docxBytes).isNotNull();
            assertThat(docxBytes.length).isGreaterThan(1000);
        }
    }
}

