package com.kisoft.emaillist.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for FacebookService.
 * Tests Facebook integration, configuration validation, and HTML-to-text conversion.
 * Test Coverage:
 * - Service enablement checking
 * - Configuration validation (token, page ID, email/password)
 * - HTML to plain text conversion
 * - Post result handling
 * @author KiSoft
 * @version 1.0.0
 * @since 2025-12-29
 * @see FacebookService
 */
class FacebookServiceTest {

    private FacebookService facebookService;

    @BeforeEach
    void setUp() {
        facebookService = new FacebookService();
    }

    private void setField(String fieldName, Object value) throws Exception {
        Field field = FacebookService.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(facebookService, value);
    }

    @Nested
    @DisplayName("Is Enabled Tests")
    class IsEnabledTests {

        @Test
        @DisplayName("Should return false when facebook.enabled is false")
        void isEnabled_shouldReturnFalseWhenDisabled() throws Exception {
            // Arrange
            setField("facebookEnabled", false);
            setField("accessToken", "token");
            setField("pageId", "123");

            // Act & Assert
            assertThat(facebookService.isEnabled()).isFalse();
        }

        @Test
        @DisplayName("Should return true when enabled with valid token and page ID")
        void isEnabled_shouldReturnTrueWithValidConfig() throws Exception {
            // Arrange
            setField("facebookEnabled", true);
            setField("accessToken", "valid_token");
            setField("pageId", "valid_page_id");

            // Act & Assert
            assertThat(facebookService.isEnabled()).isTrue();
        }

        @Test
        @DisplayName("Should return false when enabled but missing token")
        void isEnabled_shouldReturnFalseWhenMissingToken() throws Exception {
            // Arrange
            setField("facebookEnabled", true);
            setField("accessToken", "");
            setField("pageId", "123");
            setField("facebookEmail", "");
            setField("facebookPassword", "");

            // Act & Assert
            assertThat(facebookService.isEnabled()).isFalse();
        }

        @Test
        @DisplayName("Should return false when enabled but missing page ID")
        void isEnabled_shouldReturnFalseWhenMissingPageId() throws Exception {
            // Arrange
            setField("facebookEnabled", true);
            setField("accessToken", "token");
            setField("pageId", "");
            setField("facebookEmail", "");
            setField("facebookPassword", "");

            // Act & Assert
            assertThat(facebookService.isEnabled()).isFalse();
        }

        @Test
        @DisplayName("Should return true with email/password fallback")
        void isEnabled_shouldReturnTrueWithEmailPassword() throws Exception {
            // Arrange
            setField("facebookEnabled", true);
            setField("accessToken", "");
            setField("pageId", "");
            setField("facebookEmail", "user@test.com");
            setField("facebookPassword", "password123");

            // Act & Assert
            assertThat(facebookService.isEnabled()).isTrue();
        }

        @Test
        @DisplayName("Should return false with only email (no password)")
        void isEnabled_shouldReturnFalseWithOnlyEmail() throws Exception {
            // Arrange
            setField("facebookEnabled", true);
            setField("accessToken", "");
            setField("pageId", "");
            setField("facebookEmail", "user@test.com");
            setField("facebookPassword", "");

            // Act & Assert
            assertThat(facebookService.isEnabled()).isFalse();
        }

        @Test
        @DisplayName("Should return false with null token")
        void isEnabled_shouldReturnFalseWithNullToken() throws Exception {
            // Arrange
            setField("facebookEnabled", true);
            setField("accessToken", null);
            setField("pageId", null);
            setField("facebookEmail", null);
            setField("facebookPassword", null);

            // Act & Assert
            assertThat(facebookService.isEnabled()).isFalse();
        }
    }

    @Nested
    @DisplayName("Test Connection Tests")
    class TestConnectionTests {

        @Test
        @DisplayName("Should return true when configured with token and page ID")
        void testConnection_shouldReturnTrueWhenConfigured() throws Exception {
            // Arrange
            setField("facebookEnabled", true);
            setField("accessToken", "token");
            setField("pageId", "123");

            // Act & Assert
            assertThat(facebookService.testConnection()).isTrue();
        }

        @Test
        @DisplayName("Should return false when not configured")
        void testConnection_shouldReturnFalseWhenNotConfigured() throws Exception {
            // Arrange
            setField("facebookEnabled", false);
            setField("accessToken", "");
            setField("pageId", "");
            setField("facebookEmail", "");
            setField("facebookPassword", "");

            // Act & Assert
            assertThat(facebookService.testConnection()).isFalse();
        }
    }

    @Nested
    @DisplayName("Post To Page Tests")
    class PostToPageTests {

        @Test
        @DisplayName("Should return error when Facebook is disabled")
        void postToPage_shouldReturnErrorWhenDisabled() throws Exception {
            // Arrange
            setField("facebookEnabled", false);

            // Act
            FacebookService.FacebookPostResult result = facebookService.postToPage("Subject", "<p>Content</p>");

            // Assert
            assertThat(result.success()).isFalse();
            assertThat(result.message()).contains("disabled");
        }

        @Test
        @DisplayName("Should return error when not configured")
        void postToPage_shouldReturnErrorWhenNotConfigured() throws Exception {
            // Arrange
            setField("facebookEnabled", true);
            setField("accessToken", "");
            setField("pageId", "");
            setField("facebookEmail", "");
            setField("facebookPassword", "");

            // Act
            FacebookService.FacebookPostResult result = facebookService.postToPage("Subject", "<p>Content</p>");

            // Assert
            assertThat(result.success()).isFalse();
            assertThat(result.message()).contains("not fully configured");
        }

        @Test
        @DisplayName("Should return helpful message when only email/password configured")
        void postToPage_shouldReturnHelpfulMessageForEmailPassword() throws Exception {
            // Arrange
            setField("facebookEnabled", true);
            setField("accessToken", "");
            setField("pageId", "");
            setField("facebookEmail", "user@test.com");
            setField("facebookPassword", "password");

            // Act
            FacebookService.FacebookPostResult result = facebookService.postToPage("Subject", "<p>Content</p>");

            // Assert
            assertThat(result.success()).isFalse();
            assertThat(result.message()).contains("Page Access Token");
            assertThat(result.message()).contains("developers.facebook.com");
        }
    }

    @Nested
    @DisplayName("Facebook Post Result Tests")
    class FacebookPostResultTests {

        @Test
        @DisplayName("Should create successful result")
        void shouldCreateSuccessfulResult() {
            // Act
            FacebookService.FacebookPostResult result = new FacebookService.FacebookPostResult(
                true, "Posted successfully", "{\"id\": \"123\"}"
            );

            // Assert
            assertThat(result.success()).isTrue();
            assertThat(result.message()).isEqualTo("Posted successfully");
            assertThat(result.apiResponse()).isEqualTo("{\"id\": \"123\"}");
        }

        @Test
        @DisplayName("Should create failure result")
        void shouldCreateFailureResult() {
            // Act
            FacebookService.FacebookPostResult result = new FacebookService.FacebookPostResult(
                false, "Error occurred", null
            );

            // Assert
            assertThat(result.success()).isFalse();
            assertThat(result.message()).isEqualTo("Error occurred");
            assertThat(result.apiResponse()).isNull();
        }
    }
}

