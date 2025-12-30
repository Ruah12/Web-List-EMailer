package com.kisoft.emaillist.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for EmailRequest model.
 * Tests the email request DTO used for send operations.
 * Test Coverage:
 * - Default values
 * - Getters and setters
 * - All-args constructor
 * - No-args constructor
 * @author KiSoft
 * @version 1.0.0
 * @since 2025-12-29
 * @see EmailRequest
 */
class EmailRequestTest {

    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {

        @Test
        @DisplayName("No-args constructor should set default values")
        void noArgsConstructor_shouldSetDefaults() {
            // Act
            EmailRequest request = new EmailRequest();

            // Assert
            assertThat(request.getSendToAll()).isFalse();
            assertThat(request.getBatchSize()).isEqualTo(10);
            assertThat(request.getDelayMs()).isEqualTo(500);
            assertThat(request.getSubject()).isNull();
            assertThat(request.getHtmlContent()).isNull();
            assertThat(request.getSelectedEmails()).isNull();
            assertThat(request.getSendMode()).isNull();
            assertThat(request.getAddressMode()).isNull();
        }

        @Test
        @DisplayName("All-args constructor should set all values")
        void allArgsConstructor_shouldSetAllValues() {
            // Arrange
            String[] emails = {"test1@test.com", "test2@test.com"};

            // Act
            EmailRequest request = new EmailRequest(
                "Subject",
                "<p>Content</p>",
                true,
                emails,
                "batch",
                "bcc",
                20,
                1000
            );

            // Assert
            assertThat(request.getSubject()).isEqualTo("Subject");
            assertThat(request.getHtmlContent()).isEqualTo("<p>Content</p>");
            assertThat(request.getSendToAll()).isTrue();
            assertThat(request.getSelectedEmails()).containsExactly("test1@test.com", "test2@test.com");
            assertThat(request.getSendMode()).isEqualTo("batch");
            assertThat(request.getAddressMode()).isEqualTo("bcc");
            assertThat(request.getBatchSize()).isEqualTo(20);
            assertThat(request.getDelayMs()).isEqualTo(1000);
        }
    }

    @Nested
    @DisplayName("Getter and Setter Tests")
    class GetterSetterTests {

        @Test
        @DisplayName("Should get and set subject")
        void shouldGetAndSetSubject() {
            // Arrange
            EmailRequest request = new EmailRequest();

            // Act
            request.setSubject("Test Subject");

            // Assert
            assertThat(request.getSubject()).isEqualTo("Test Subject");
        }

        @Test
        @DisplayName("Should get and set htmlContent")
        void shouldGetAndSetHtmlContent() {
            // Arrange
            EmailRequest request = new EmailRequest();

            // Act
            request.setHtmlContent("<p>Hello</p>");

            // Assert
            assertThat(request.getHtmlContent()).isEqualTo("<p>Hello</p>");
        }

        @Test
        @DisplayName("Should get and set sendToAll")
        void shouldGetAndSetSendToAll() {
            // Arrange
            EmailRequest request = new EmailRequest();

            // Act
            request.setSendToAll(true);

            // Assert
            assertThat(request.getSendToAll()).isTrue();
        }

        @Test
        @DisplayName("Should get and set selectedEmails")
        void shouldGetAndSetSelectedEmails() {
            // Arrange
            EmailRequest request = new EmailRequest();
            String[] emails = {"a@test.com", "b@test.com"};

            // Act
            request.setSelectedEmails(emails);

            // Assert
            assertThat(request.getSelectedEmails()).containsExactly("a@test.com", "b@test.com");
        }

        @Test
        @DisplayName("Should get and set sendMode")
        void shouldGetAndSetSendMode() {
            // Arrange
            EmailRequest request = new EmailRequest();

            // Act
            request.setSendMode("individual");

            // Assert
            assertThat(request.getSendMode()).isEqualTo("individual");
        }

        @Test
        @DisplayName("Should get and set addressMode")
        void shouldGetAndSetAddressMode() {
            // Arrange
            EmailRequest request = new EmailRequest();

            // Act
            request.setAddressMode("to");

            // Assert
            assertThat(request.getAddressMode()).isEqualTo("to");
        }

        @Test
        @DisplayName("Should get and set batchSize")
        void shouldGetAndSetBatchSize() {
            // Arrange
            EmailRequest request = new EmailRequest();

            // Act
            request.setBatchSize(50);

            // Assert
            assertThat(request.getBatchSize()).isEqualTo(50);
        }

        @Test
        @DisplayName("Should get and set delayMs")
        void shouldGetAndSetDelayMs() {
            // Arrange
            EmailRequest request = new EmailRequest();

            // Act
            request.setDelayMs(2000);

            // Assert
            assertThat(request.getDelayMs()).isEqualTo(2000);
        }
    }

    @Nested
    @DisplayName("Null Value Tests")
    class NullValueTests {

        @Test
        @DisplayName("Should allow null sendToAll")
        void shouldAllowNullSendToAll() {
            // Arrange
            EmailRequest request = new EmailRequest();

            // Act
            request.setSendToAll(null);

            // Assert
            assertThat(request.getSendToAll()).isNull();
        }

        @Test
        @DisplayName("Should allow null batchSize")
        void shouldAllowNullBatchSize() {
            // Arrange
            EmailRequest request = new EmailRequest();

            // Act
            request.setBatchSize(null);

            // Assert
            assertThat(request.getBatchSize()).isNull();
        }

        @Test
        @DisplayName("Should allow null delayMs")
        void shouldAllowNullDelayMs() {
            // Arrange
            EmailRequest request = new EmailRequest();

            // Act
            request.setDelayMs(null);

            // Assert
            assertThat(request.getDelayMs()).isNull();
        }
    }

    @Nested
    @DisplayName("Equals and HashCode Tests")
    class EqualsHashCodeTests {

        @Test
        @DisplayName("Equal objects should have same hashCode")
        void equalObjects_shouldHaveSameHashCode() {
            // Arrange
            String[] emails = {"test@test.com"};
            EmailRequest request1 = new EmailRequest("Sub", "<p>Hi</p>", false, emails, "batch", "to", 10, 500);
            EmailRequest request2 = new EmailRequest("Sub", "<p>Hi</p>", false, emails, "batch", "to", 10, 500);

            // Assert
            assertThat(request1).isEqualTo(request2);
            assertThat(request1.hashCode()).isEqualTo(request2.hashCode());
        }

        @Test
        @DisplayName("Different objects should not be equal")
        void differentObjects_shouldNotBeEqual() {
            // Arrange
            EmailRequest request1 = new EmailRequest();
            request1.setSubject("Subject 1");

            EmailRequest request2 = new EmailRequest();
            request2.setSubject("Subject 2");

            // Assert
            assertThat(request1).isNotEqualTo(request2);
        }
    }

    @Nested
    @DisplayName("ToString Tests")
    class ToStringTests {

        @Test
        @DisplayName("toString should include all fields")
        void toString_shouldIncludeAllFields() {
            // Arrange
            EmailRequest request = new EmailRequest();
            request.setSubject("Test");
            request.setSendMode("batch");

            // Act
            String str = request.toString();

            // Assert
            assertThat(str).contains("subject");
            assertThat(str).contains("Test");
            assertThat(str).contains("sendMode");
            assertThat(str).contains("batch");
        }
    }
}

