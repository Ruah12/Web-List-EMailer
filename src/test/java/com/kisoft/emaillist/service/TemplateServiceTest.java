package com.kisoft.emaillist.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for TemplateService.
 * Tests template CRUD operations, path handling, and image verification.
 * Test Coverage:
 * - Save template to file system
 * - Load template from file system
 * - Delete template
 * - Get template label
 * - Template info retrieval
 * - Image verification
 * - Path handling (absolute vs relative)
 * - Disabled service handling
 * @author KiSoft
 * @version 1.0.0
 * @since 2025-12-29
 * @see TemplateService
 */
class TemplateServiceTest {

    private TemplateService templateService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        templateService = new TemplateService(tempDir.toString(), true);
    }

    @Nested
    @DisplayName("Save Template Tests")
    class SaveTemplateTests {

        @Test
        @DisplayName("Should save template successfully")
        void saveTemplate_shouldSaveSuccessfully() {
            // Act
            Map<String, Object> result = templateService.saveTemplate(1, "Test Subject", "<p>Hello</p>");

            // Assert
            assertThat(result.get("status")).isEqualTo("ok");
            assertThat(result.get("slot")).isEqualTo(1);
            assertThat(result).containsKey("filePath");
            assertThat(result).containsKey("fileSize");

            // Verify file was created
            Path templateFile = tempDir.resolve("template-1.json");
            assertThat(Files.exists(templateFile)).isTrue();
        }

        @Test
        @DisplayName("Should reject invalid slot number (too low)")
        void saveTemplate_shouldRejectInvalidSlotTooLow() {
            // Act
            Map<String, Object> result = templateService.saveTemplate(0, "Subject", "Content");

            // Assert
            assertThat(result.get("status")).isEqualTo("error");
            assertThat(result.get("message").toString()).contains("Invalid slot");
        }

        @Test
        @DisplayName("Should reject invalid slot number (too high)")
        void saveTemplate_shouldRejectInvalidSlotTooHigh() {
            // Act
            Map<String, Object> result = templateService.saveTemplate(11, "Subject", "Content");

            // Assert
            assertThat(result.get("status")).isEqualTo("error");
            assertThat(result.get("message").toString()).contains("Invalid slot");
        }

        @Test
        @DisplayName("Should handle null subject")
        void saveTemplate_shouldHandleNullSubject() {
            // Act
            Map<String, Object> result = templateService.saveTemplate(1, null, "<p>Content</p>");

            // Assert
            assertThat(result.get("status")).isEqualTo("ok");
        }

        @Test
        @DisplayName("Should handle null content")
        void saveTemplate_shouldHandleNullContent() {
            // Act
            Map<String, Object> result = templateService.saveTemplate(1, "Subject", null);

            // Assert
            assertThat(result.get("status")).isEqualTo("ok");
        }

        @Test
        @DisplayName("Should overwrite existing template")
        void saveTemplate_shouldOverwriteExisting() {
            // Arrange
            templateService.saveTemplate(1, "Original", "<p>Original</p>");

            // Act
            Map<String, Object> result = templateService.saveTemplate(1, "Updated", "<p>Updated</p>");

            // Assert
            assertThat(result.get("status")).isEqualTo("ok");

            // Verify content was updated
            Map<String, Object> loaded = templateService.loadTemplate(1);
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) loaded.get("data");
            assertThat(data.get("subject")).isEqualTo("Updated");
        }
    }

    @Nested
    @DisplayName("Load Template Tests")
    class LoadTemplateTests {

        @Test
        @DisplayName("Should load existing template")
        void loadTemplate_shouldLoadExisting() {
            // Arrange
            templateService.saveTemplate(2, "My Subject", "<p>My Content</p>");

            // Act
            Map<String, Object> result = templateService.loadTemplate(2);

            // Assert
            assertThat(result.get("status")).isEqualTo("ok");
            assertThat(result.get("slot")).isEqualTo(2);

            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) result.get("data");
            assertThat(data.get("subject")).isEqualTo("My Subject");
            assertThat(data.get("htmlContent")).isEqualTo("<p>My Content</p>");
        }

        @Test
        @DisplayName("Should return not_found for missing template")
        void loadTemplate_shouldReturnNotFound() {
            // Act
            Map<String, Object> result = templateService.loadTemplate(9);

            // Assert
            assertThat(result.get("status")).isEqualTo("not_found");
        }

        @Test
        @DisplayName("Should reject invalid slot number")
        void loadTemplate_shouldRejectInvalidSlot() {
            // Act
            Map<String, Object> result = templateService.loadTemplate(0);

            // Assert
            assertThat(result.get("status")).isEqualTo("error");
        }
    }

    @Nested
    @DisplayName("Delete Template Tests")
    class DeleteTemplateTests {

        @Test
        @DisplayName("Should delete existing template")
        void deleteTemplate_shouldDeleteExisting() {
            // Arrange
            templateService.saveTemplate(3, "To Delete", "Content");

            // Act
            Map<String, Object> result = templateService.deleteTemplate(3);

            // Assert
            assertThat(result.get("status")).isEqualTo("ok");

            // Verify file is deleted
            Path templateFile = tempDir.resolve("template-3.json");
            assertThat(Files.exists(templateFile)).isFalse();
        }

        @Test
        @DisplayName("Should return not_found for missing template")
        void deleteTemplate_shouldReturnNotFoundForMissing() {
            // Act
            Map<String, Object> result = templateService.deleteTemplate(8);

            // Assert
            assertThat(result.get("status")).isEqualTo("not_found");
        }

        @Test
        @DisplayName("Should reject invalid slot number")
        void deleteTemplate_shouldRejectInvalidSlot() {
            // Act
            Map<String, Object> result = templateService.deleteTemplate(-1);

            // Assert
            assertThat(result.get("status")).isEqualTo("error");
        }
    }

    @Nested
    @DisplayName("Get Template Label Tests")
    class GetTemplateLabelTests {

        @Test
        @DisplayName("Should return formatted label for existing template")
        void getTemplateLabel_shouldReturnFormattedLabel() {
            // Arrange
            templateService.saveTemplate(4, "My Template Subject", "Content");

            // Act
            String label = templateService.getTemplateLabel(4);

            // Assert
            assertThat(label).startsWith("4. ");
            assertThat(label).contains("My Template Subject");
        }

        @Test
        @DisplayName("Should truncate long subjects")
        void getTemplateLabel_shouldTruncateLongSubjects() {
            // Arrange
            String longSubject = "This is a very long subject that should be truncated in the label";
            templateService.saveTemplate(5, longSubject, "Content");

            // Act
            String label = templateService.getTemplateLabel(5);

            // Assert
            assertThat(label).contains("…");
            assertThat(label.length()).isLessThan(longSubject.length() + 5);
        }

        @Test
        @DisplayName("Should return slot number for missing template")
        void getTemplateLabel_shouldReturnSlotForMissing() {
            // Act
            String label = templateService.getTemplateLabel(7);

            // Assert
            assertThat(label).isEqualTo("7");
        }
    }

    @Nested
    @DisplayName("Template Info Tests")
    class TemplateInfoTests {

        @Test
        @DisplayName("Should return template folder path")
        void getTemplateFolderPath_shouldReturnPath() {
            // Act
            String path = templateService.getTemplateFolderPath();

            // Assert
            assertThat(path).isEqualTo(tempDir.toString());
        }

        @Test
        @DisplayName("Should return template file path")
        void getTemplateFilePath_shouldReturnFilePath() {
            // Act
            String path = templateService.getTemplateFilePath(1);

            // Assert
            assertThat(path).contains("template-1.json");
        }
    }

    @Nested
    @DisplayName("Image Verification Tests")
    class ImageVerificationTests {

        @Test
        @DisplayName("Should verify images in template")
        void verifyTemplateImages_shouldVerifyImages() {
            // Arrange
            String htmlWithImages = "<p>Text</p><img src=\"data:image/png;base64,ABC123\"><img src=\"data:image/jpeg;base64,XYZ\">";
            templateService.saveTemplate(6, "With Images", htmlWithImages);

            // Act
            Map<String, Object> result = templateService.verifyTemplateImages(6);

            // Assert
            assertThat(result.get("status")).isEqualTo("ok");
            assertThat(result.get("slot")).isEqualTo(6);

            @SuppressWarnings("unchecked")
            Map<String, Object> summary = (Map<String, Object>) result.get("summary");
            assertThat(summary.get("imageCount")).isEqualTo(2);
            assertThat(summary.get("dataUrlCount")).isEqualTo(2);
        }

        @Test
        @DisplayName("Should return not_found for missing template")
        void verifyTemplateImages_shouldReturnNotFound() {
            // Act
            Map<String, Object> result = templateService.verifyTemplateImages(10);

            // Assert
            assertThat(result.get("status")).isEqualTo("not_found");
        }

        @Test
        @DisplayName("Should handle template without images")
        void verifyTemplateImages_shouldHandleNoImages() {
            // Arrange
            templateService.saveTemplate(7, "No Images", "<p>Just text</p>");

            // Act
            Map<String, Object> result = templateService.verifyTemplateImages(7);

            // Assert
            assertThat(result.get("status")).isEqualTo("ok");

            @SuppressWarnings("unchecked")
            Map<String, Object> summary = (Map<String, Object>) result.get("summary");
            assertThat(summary.get("imageCount")).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("Disabled Service Tests")
    class DisabledServiceTests {

        @Test
        @DisplayName("Should return error when service is disabled - save")
        void saveTemplate_whenDisabled_shouldReturnError() {
            // Arrange
            TemplateService disabledService = new TemplateService(tempDir.toString(), false);

            // Act
            Map<String, Object> result = disabledService.saveTemplate(1, "Subject", "Content");

            // Assert
            assertThat(result.get("status")).isEqualTo("error");
            assertThat(result.get("message").toString()).contains("disabled");
        }

        @Test
        @DisplayName("Should return error when service is disabled - load")
        void loadTemplate_whenDisabled_shouldReturnError() {
            // Arrange
            TemplateService disabledService = new TemplateService(tempDir.toString(), false);

            // Act
            Map<String, Object> result = disabledService.loadTemplate(1);

            // Assert
            assertThat(result.get("status")).isEqualTo("error");
            assertThat(result.get("message").toString()).contains("disabled");
        }

        @Test
        @DisplayName("Should return error when service is disabled - delete")
        void deleteTemplate_whenDisabled_shouldReturnError() {
            // Arrange
            TemplateService disabledService = new TemplateService(tempDir.toString(), false);

            // Act
            Map<String, Object> result = disabledService.deleteTemplate(1);

            // Assert
            assertThat(result.get("status")).isEqualTo("error");
            assertThat(result.get("message").toString()).contains("disabled");
        }

        @Test
        @DisplayName("Should return error when service is disabled - verify")
        void verifyTemplateImages_whenDisabled_shouldReturnError() {
            // Arrange
            TemplateService disabledService = new TemplateService(tempDir.toString(), false);

            // Act
            Map<String, Object> result = disabledService.verifyTemplateImages(1);

            // Assert
            assertThat(result.get("status")).isEqualTo("error");
            assertThat(result.get("message").toString()).contains("disabled");
        }
    }

    @Nested
    @DisplayName("JSON Serialization Tests")
    class JsonSerializationTests {

        @Test
        @DisplayName("Should handle special characters in content")
        void saveTemplate_shouldHandleSpecialCharacters() {
            // Arrange
            String contentWithSpecialChars = "<p>Quote: \"Hello\" and 'World'</p><p>Backslash: \\ Forward: /</p>";

            // Act
            Map<String, Object> saveResult = templateService.saveTemplate(8, "Special Chars", contentWithSpecialChars);

            // Assert
            assertThat(saveResult.get("status")).isEqualTo("ok");

            // Load and verify
            Map<String, Object> loadResult = templateService.loadTemplate(8);
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) loadResult.get("data");
            assertThat(data.get("htmlContent")).isEqualTo(contentWithSpecialChars);
        }

        @Test
        @DisplayName("Should handle newlines in content")
        void saveTemplate_shouldHandleNewlines() {
            // Arrange
            String contentWithNewlines = "<p>Line 1</p>\n<p>Line 2</p>\r\n<p>Line 3</p>";

            // Act
            Map<String, Object> saveResult = templateService.saveTemplate(9, "Newlines", contentWithNewlines);

            // Assert
            assertThat(saveResult.get("status")).isEqualTo("ok");

            // Load and verify
            Map<String, Object> loadResult = templateService.loadTemplate(9);
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) loadResult.get("data");
            assertThat(data.get("htmlContent")).isEqualTo(contentWithNewlines);
        }

        @Test
        @DisplayName("Should handle Unicode content")
        void saveTemplate_shouldHandleUnicode() {
            // Arrange
            String unicodeContent = "<p>日本語 中文 한국어 العربية</p><p>Émoji: 🎉🚀</p>";

            // Act
            Map<String, Object> saveResult = templateService.saveTemplate(10, "Unicode", unicodeContent);

            // Assert
            assertThat(saveResult.get("status")).isEqualTo("ok");

            // Load and verify
            Map<String, Object> loadResult = templateService.loadTemplate(10);
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) loadResult.get("data");
            assertThat(data.get("htmlContent")).isEqualTo(unicodeContent);
        }
    }
}

