package com.kisoft.emaillist.service;

import com.kisoft.emaillist.model.SendResult;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for EmailSenderService.
 * Tests email sending functionality including batch and individual modes.
 * Test Coverage:
 * - Batch sending with BCC
 * - Batch sending with To
 * - Individual sending
 * - Connection testing
 * - Error handling
 * @author KiSoft
 * @version 1.0.0
 * @since 2025-12-29
 * @see EmailSenderService
 */
class EmailSenderServiceTest {

    private EmailSenderService emailSenderService;
    private JavaMailSender mockMailSender;

    @BeforeEach
    void setUp() throws Exception {
        mockMailSender = mock(JavaMailSender.class);
        emailSenderService = new EmailSenderService(mockMailSender);

        // Set required fields via reflection
        setField("fromEmail", "sender@test.com");
        setField("fromName", "Test Sender");
    }

    private void setField(String fieldName, Object value) throws Exception {
        Field field = EmailSenderService.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(emailSenderService, value);
    }

    @Nested
    @DisplayName("Send Batch Tests")
    class SendBatchTests {

        @Test
        @DisplayName("Should send batch emails with BCC")
        void sendBatch_shouldSendWithBcc() {
            // Arrange
            List<String> emails = Arrays.asList("user1@test.com", "user2@test.com");
            MimeMessage mockMessage = mock(MimeMessage.class);
            when(mockMailSender.createMimeMessage()).thenReturn(mockMessage);

            // Act
            SendResult result = emailSenderService.sendBatch(emails, "Subject", "<p>Content</p>", 10, true, 0);

            // Assert
            assertThat(result.getTotalEmails()).isEqualTo(2);
            verify(mockMailSender, atLeastOnce()).send(any(MimeMessage.class));
        }

        @Test
        @DisplayName("Should send batch emails with To")
        void sendBatch_shouldSendWithTo() {
            // Arrange
            List<String> emails = Arrays.asList("user1@test.com", "user2@test.com");
            MimeMessage mockMessage = mock(MimeMessage.class);
            when(mockMailSender.createMimeMessage()).thenReturn(mockMessage);

            // Act
            SendResult result = emailSenderService.sendBatch(emails, "Subject", "<p>Content</p>", 10, false, 0);

            // Assert
            assertThat(result.getTotalEmails()).isEqualTo(2);
        }

        @Test
        @DisplayName("Should split into multiple batches")
        void sendBatch_shouldSplitIntoBatches() {
            // Arrange
            List<String> emails = Arrays.asList(
                "user1@test.com", "user2@test.com", "user3@test.com",
                "user4@test.com", "user5@test.com"
            );
            MimeMessage mockMessage = mock(MimeMessage.class);
            when(mockMailSender.createMimeMessage()).thenReturn(mockMessage);

            // Act
            SendResult result = emailSenderService.sendBatch(emails, "Subject", "<p>Content</p>", 2, true, 0);

            // Assert
            assertThat(result.getTotalEmails()).isEqualTo(5);
            // Should send 3 batch emails (2 + 2 + 1)
            verify(mockMailSender, times(3)).send(any(MimeMessage.class));
        }

        @Test
        @DisplayName("Should handle empty email list")
        void sendBatch_shouldHandleEmptyList() {
            // Act
            SendResult result = emailSenderService.sendBatch(Collections.emptyList(), "Subject", "<p>Content</p>", 10, true, 0);

            // Assert
            assertThat(result.getTotalEmails()).isZero();
            assertThat(result.getSuccessCount()).isZero();
            verify(mockMailSender, never()).send(any(MimeMessage.class));
        }

        @Test
        @DisplayName("Should handle mail send failure")
        void sendBatch_shouldHandleFailure() {
            // Arrange
            List<String> emails = Arrays.asList("user1@test.com", "user2@test.com");
            MimeMessage mockMessage = mock(MimeMessage.class);
            when(mockMailSender.createMimeMessage()).thenReturn(mockMessage);
            doThrow(new MailSendException("SMTP error")).when(mockMailSender).send(any(MimeMessage.class));

            // Act
            SendResult result = emailSenderService.sendBatch(emails, "Subject", "<p>Content</p>", 10, true, 0);

            // Assert
            assertThat(result.getFailCount()).isEqualTo(2);
            assertThat(result.getSuccessCount()).isZero();
        }
    }

    @Nested
    @DisplayName("Send Individual Tests")
    class SendIndividualTests {

        @Test
        @DisplayName("Should send individual emails")
        void sendIndividual_shouldSendToEachRecipient() {
            // Arrange
            List<String> emails = Arrays.asList("user1@test.com", "user2@test.com");
            MimeMessage mockMessage = mock(MimeMessage.class);
            when(mockMailSender.createMimeMessage()).thenReturn(mockMessage);

            // Act
            SendResult result = emailSenderService.sendIndividual(emails, "Subject", "<p>Content</p>", false);

            // Assert
            assertThat(result.getTotalEmails()).isEqualTo(2);
            // Should send 2 separate emails
            verify(mockMailSender, times(2)).send(any(MimeMessage.class));
        }

        @Test
        @DisplayName("Should continue on individual failure")
        void sendIndividual_shouldContinueOnFailure() {
            // Arrange
            List<String> emails = Arrays.asList("user1@test.com", "user2@test.com", "user3@test.com");
            MimeMessage mockMessage = mock(MimeMessage.class);
            when(mockMailSender.createMimeMessage()).thenReturn(mockMessage);

            // First call succeeds, second fails, third succeeds
            doNothing()
                .doThrow(new MailSendException("SMTP error"))
                .doNothing()
                .when(mockMailSender).send(any(MimeMessage.class));

            // Act
            SendResult result = emailSenderService.sendIndividual(emails, "Subject", "<p>Content</p>", false);

            // Assert
            assertThat(result.getSuccessCount()).isEqualTo(2);
            assertThat(result.getFailCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("Should send individual emails with BCC")
        void sendIndividual_shouldSendWithBcc() {
            // Arrange
            List<String> emails = List.of("user1@test.com");
            MimeMessage mockMessage = mock(MimeMessage.class);
            when(mockMailSender.createMimeMessage()).thenReturn(mockMessage);

            // Act
            SendResult result = emailSenderService.sendIndividual(emails, "Subject", "<p>Content</p>", true);

            // Assert
            assertThat(result.getTotalEmails()).isEqualTo(1);
            verify(mockMailSender, times(1)).send(any(MimeMessage.class));
        }
    }

    @Nested
    @DisplayName("Connection Test")
    class ConnectionTests {

        @Test
        @DisplayName("Should return true when connection succeeds")
        void testConnection_shouldReturnTrueOnSuccess() {
            // Arrange - createMimeMessage succeeds (returns a mock)
            MimeMessage mockMessage = mock(MimeMessage.class);
            when(mockMailSender.createMimeMessage()).thenReturn(mockMessage);

            // Act
            boolean result = emailSenderService.testConnection();

            // Assert
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("Should return false when connection fails")
        void testConnection_shouldReturnFalseOnFailure() {
            // Arrange - createMimeMessage throws exception
            when(mockMailSender.createMimeMessage()).thenThrow(new RuntimeException("Connection failed"));

            // Act
            boolean result = emailSenderService.testConnection();

            // Assert
            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("Send Result Tests")
    class SendResultTests {

        @Test
        @DisplayName("Should include error messages for failed emails")
        void sendBatch_shouldIncludeErrorMessages() {
            // Arrange
            List<String> emails = List.of("user@test.com");
            MimeMessage mockMessage = mock(MimeMessage.class);
            when(mockMailSender.createMimeMessage()).thenReturn(mockMessage);
            doThrow(new MailSendException("Connection refused")).when(mockMailSender).send(any(MimeMessage.class));

            // Act
            SendResult result = emailSenderService.sendBatch(emails, "Subject", "<p>Content</p>", 10, true, 0);

            // Assert
            assertThat(result.getErrorMessages()).containsKey("user@test.com");
            assertThat(result.getErrorMessages().get("user@test.com")).contains("Connection refused");
        }

        @Test
        @DisplayName("Should include failed emails in list")
        void sendBatch_shouldIncludeFailedEmailsInList() {
            // Arrange
            List<String> emails = List.of("fail@test.com");
            MimeMessage mockMessage = mock(MimeMessage.class);
            when(mockMailSender.createMimeMessage()).thenReturn(mockMessage);
            doThrow(new MailSendException("Error")).when(mockMailSender).send(any(MimeMessage.class));

            // Act
            SendResult result = emailSenderService.sendBatch(emails, "Subject", "<p>Content</p>", 10, true, 0);

            // Assert
            assertThat(result.getFailedEmails()).contains("fail@test.com");
        }
    }
}

