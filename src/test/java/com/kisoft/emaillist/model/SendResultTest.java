package com.kisoft.emaillist.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for SendResult model.
 * Tests the send result DTO used for email send operation responses.
 * Test Coverage:
 * - Constructor variations
 * - Error message handling
 * - Getters and setters
 * - Default values
 * @author KiSoft
 * @version 1.0.0
 * @since 2025-12-29
 * @see SendResult
 */
class SendResultTest {

    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {

        @Test
        @DisplayName("No-args constructor should create empty result")
        void noArgsConstructor_shouldCreateEmptyResult() {
            // Act
            SendResult result = new SendResult();

            // Assert
            assertThat(result.getTotalEmails()).isZero();
            assertThat(result.getSuccessCount()).isZero();
            assertThat(result.getFailCount()).isZero();
            assertThat(result.getMessage()).isNull();
            assertThat(result.getFailedEmails()).isNull();
            assertThat(result.getErrorMessages()).isNotNull(); // Initialized by Lombok
        }

        @Test
        @DisplayName("5-arg constructor should set values without error messages")
        void fiveArgConstructor_shouldSetValuesWithoutErrorMessages() {
            // Arrange
            List<String> failedEmails = Arrays.asList("fail1@test.com", "fail2@test.com");

            // Act
            SendResult result = new SendResult(10, 8, 2, "Completed", failedEmails);

            // Assert
            assertThat(result.getTotalEmails()).isEqualTo(10);
            assertThat(result.getSuccessCount()).isEqualTo(8);
            assertThat(result.getFailCount()).isEqualTo(2);
            assertThat(result.getMessage()).isEqualTo("Completed");
            assertThat(result.getFailedEmails()).containsExactly("fail1@test.com", "fail2@test.com");
            assertThat(result.getErrorMessages()).isEmpty();
        }

        @Test
        @DisplayName("All-args constructor should set all values")
        void allArgsConstructor_shouldSetAllValues() {
            // Arrange
            List<String> failedEmails = List.of("fail@test.com");
            Map<String, String> errorMessages = new HashMap<>();
            errorMessages.put("fail@test.com", "SMTP error");

            // Act
            SendResult result = new SendResult(5, 4, 1, "Done", failedEmails, errorMessages);

            // Assert
            assertThat(result.getTotalEmails()).isEqualTo(5);
            assertThat(result.getSuccessCount()).isEqualTo(4);
            assertThat(result.getFailCount()).isEqualTo(1);
            assertThat(result.getMessage()).isEqualTo("Done");
            assertThat(result.getFailedEmails()).containsExactly("fail@test.com");
            assertThat(result.getErrorMessages()).containsEntry("fail@test.com", "SMTP error");
        }
    }

    @Nested
    @DisplayName("Add Error Message Tests")
    class AddErrorMessageTests {

        @Test
        @DisplayName("Should add error message to existing map")
        void addErrorMessage_shouldAddToExistingMap() {
            // Arrange
            SendResult result = new SendResult(2, 0, 2, "Failed", new ArrayList<>());

            // Act
            result.addErrorMessage("user1@test.com", "Connection refused");
            result.addErrorMessage("user2@test.com", "Invalid recipient");

            // Assert
            assertThat(result.getErrorMessages())
                .hasSize(2)
                .containsEntry("user1@test.com", "Connection refused")
                .containsEntry("user2@test.com", "Invalid recipient");
        }

        @Test
        @DisplayName("Should initialize map if null")
        void addErrorMessage_shouldInitializeMapIfNull() {
            // Arrange
            SendResult result = new SendResult();
            result.setErrorMessages(null);

            // Act
            result.addErrorMessage("user@test.com", "Error");

            // Assert
            assertThat(result.getErrorMessages())
                .isNotNull()
                .containsEntry("user@test.com", "Error");
        }

        @Test
        @DisplayName("Should overwrite existing error for same email")
        void addErrorMessage_shouldOverwriteExisting() {
            // Arrange
            SendResult result = new SendResult(1, 0, 1, "Failed", new ArrayList<>());
            result.addErrorMessage("user@test.com", "First error");

            // Act
            result.addErrorMessage("user@test.com", "Second error");

            // Assert
            assertThat(result.getErrorMessages())
                .hasSize(1)
                .containsEntry("user@test.com", "Second error");
        }
    }

    @Nested
    @DisplayName("Getter and Setter Tests")
    class GetterSetterTests {

        @Test
        @DisplayName("Should get and set totalEmails")
        void shouldGetAndSetTotalEmails() {
            // Arrange
            SendResult result = new SendResult();

            // Act
            result.setTotalEmails(100);

            // Assert
            assertThat(result.getTotalEmails()).isEqualTo(100);
        }

        @Test
        @DisplayName("Should get and set successCount")
        void shouldGetAndSetSuccessCount() {
            // Arrange
            SendResult result = new SendResult();

            // Act
            result.setSuccessCount(95);

            // Assert
            assertThat(result.getSuccessCount()).isEqualTo(95);
        }

        @Test
        @DisplayName("Should get and set failCount")
        void shouldGetAndSetFailCount() {
            // Arrange
            SendResult result = new SendResult();

            // Act
            result.setFailCount(5);

            // Assert
            assertThat(result.getFailCount()).isEqualTo(5);
        }

        @Test
        @DisplayName("Should get and set message")
        void shouldGetAndSetMessage() {
            // Arrange
            SendResult result = new SendResult();

            // Act
            result.setMessage("All emails sent successfully");

            // Assert
            assertThat(result.getMessage()).isEqualTo("All emails sent successfully");
        }

        @Test
        @DisplayName("Should get and set failedEmails")
        void shouldGetAndSetFailedEmails() {
            // Arrange
            SendResult result = new SendResult();
            List<String> failed = Arrays.asList("a@test.com", "b@test.com");

            // Act
            result.setFailedEmails(failed);

            // Assert
            assertThat(result.getFailedEmails()).containsExactly("a@test.com", "b@test.com");
        }

        @Test
        @DisplayName("Should get and set errorMessages")
        void shouldGetAndSetErrorMessages() {
            // Arrange
            SendResult result = new SendResult();
            Map<String, String> errors = new HashMap<>();
            errors.put("user@test.com", "Error message");

            // Act
            result.setErrorMessages(errors);

            // Assert
            assertThat(result.getErrorMessages()).containsEntry("user@test.com", "Error message");
        }
    }

    @Nested
    @DisplayName("Computed Values Tests")
    class ComputedValuesTests {

        @Test
        @DisplayName("Success + Fail should equal Total")
        void successPlusFail_shouldEqualTotal() {
            // Arrange
            SendResult result = new SendResult(10, 7, 3, "Done", new ArrayList<>());

            // Assert
            assertThat(result.getSuccessCount() + result.getFailCount()).isEqualTo(result.getTotalEmails());
        }

        @Test
        @DisplayName("All success scenario")
        void allSuccess_scenario() {
            // Arrange
            SendResult result = new SendResult(50, 50, 0, "All sent", new ArrayList<>());

            // Assert
            assertThat(result.getSuccessCount()).isEqualTo(50);
            assertThat(result.getFailCount()).isZero();
            assertThat(result.getFailedEmails()).isEmpty();
        }

        @Test
        @DisplayName("All failure scenario")
        void allFailure_scenario() {
            // Arrange
            List<String> failedEmails = Arrays.asList("a@test.com", "b@test.com", "c@test.com");
            SendResult result = new SendResult(3, 0, 3, "All failed", failedEmails);

            // Assert
            assertThat(result.getSuccessCount()).isZero();
            assertThat(result.getFailCount()).isEqualTo(3);
            assertThat(result.getFailedEmails()).hasSize(3);
        }
    }

    @Nested
    @DisplayName("Equals and HashCode Tests")
    class EqualsHashCodeTests {

        @Test
        @DisplayName("Equal objects should have same hashCode")
        void equalObjects_shouldHaveSameHashCode() {
            // Arrange
            List<String> failed = new ArrayList<>();
            Map<String, String> errors = new HashMap<>();
            SendResult result1 = new SendResult(10, 8, 2, "Done", failed, errors);
            SendResult result2 = new SendResult(10, 8, 2, "Done", failed, errors);

            // Assert
            assertThat(result1).isEqualTo(result2);
            assertThat(result1.hashCode()).isEqualTo(result2.hashCode());
        }

        @Test
        @DisplayName("Different objects should not be equal")
        void differentObjects_shouldNotBeEqual() {
            // Arrange
            SendResult result1 = new SendResult(10, 8, 2, "Done", new ArrayList<>());
            SendResult result2 = new SendResult(10, 9, 1, "Done", new ArrayList<>());

            // Assert
            assertThat(result1).isNotEqualTo(result2);
        }
    }

    @Nested
    @DisplayName("ToString Tests")
    class ToStringTests {

        @Test
        @DisplayName("toString should include key fields")
        void toString_shouldIncludeKeyFields() {
            // Arrange
            SendResult result = new SendResult(10, 8, 2, "Complete", new ArrayList<>());

            // Act
            String str = result.toString();

            // Assert
            assertThat(str).contains("totalEmails");
            assertThat(str).contains("10");
            assertThat(str).contains("successCount");
            assertThat(str).contains("8");
            assertThat(str).contains("failCount");
            assertThat(str).contains("2");
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should handle zero emails")
        void shouldHandleZeroEmails() {
            // Arrange
            SendResult result = new SendResult(0, 0, 0, "No emails to send", new ArrayList<>());

            // Assert
            assertThat(result.getTotalEmails()).isZero();
            assertThat(result.getSuccessCount()).isZero();
            assertThat(result.getFailCount()).isZero();
        }

        @Test
        @DisplayName("Should handle large number of emails")
        void shouldHandleLargeNumberOfEmails() {
            // Arrange
            SendResult result = new SendResult(1000000, 999999, 1, "Almost all sent", new ArrayList<>());

            // Assert
            assertThat(result.getTotalEmails()).isEqualTo(1000000);
            assertThat(result.getSuccessCount()).isEqualTo(999999);
        }

        @Test
        @DisplayName("Should handle empty failed emails list")
        void shouldHandleEmptyFailedEmailsList() {
            // Arrange
            SendResult result = new SendResult(5, 5, 0, "All sent", new ArrayList<>());

            // Assert
            assertThat(result.getFailedEmails()).isEmpty();
        }

        @Test
        @DisplayName("Should handle null message")
        void shouldHandleNullMessage() {
            // Arrange
            SendResult result = new SendResult(5, 5, 0, null, new ArrayList<>());

            // Assert
            assertThat(result.getMessage()).isNull();
        }
    }
}

