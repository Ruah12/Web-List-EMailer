package com.kisoft.emaillist.controller;

import com.kisoft.emaillist.model.EmailRequest;
import com.kisoft.emaillist.model.SendResult;
import com.kisoft.emaillist.service.EmailListService;
import com.kisoft.emaillist.service.EmailSenderService;
import com.kisoft.emaillist.service.ExportService;
import com.kisoft.emaillist.service.FacebookService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for EmailController REST API endpoints.
 * Tests the email sending, email list management, and export functionality.
 * Test Coverage:
 * - Main page rendering with model attributes
 * - Email sending (individual and batch modes)
 * - Test email sending
 * - Email list CRUD operations
 * - SMTP connection testing
 * - PDF and DOCX export
 * - Facebook posting
 * @author KiSoft
 * @version 1.0.0
 * @since 2025-12-29
 * @see EmailController
 */
class EmailControllerTest {

    private EmailController controller;
    private EmailSenderService mockEmailSenderService;
    private EmailListService mockEmailListService;
    private FacebookService mockFacebookService;
    private ExportService mockExportService;

    @BeforeEach
    void setUp() throws Exception {
        mockEmailSenderService = mock(EmailSenderService.class);
        mockEmailListService = mock(EmailListService.class);
        mockFacebookService = mock(FacebookService.class);
        mockExportService = mock(ExportService.class);

        controller = new EmailController(
            mockEmailSenderService,
            mockEmailListService,
            mockFacebookService,
            mockExportService
        );

        // Set default values via reflection
        setFieldValue(controller, "editorDefaultTextColor", "#000000");
        setFieldValue(controller, "templateSlots", 5);
        setFieldValue(controller, "facebookEnabled", false);
    }

    private void setFieldValue(Object target, String fieldName, Object value) throws Exception {
        java.lang.reflect.Field field = EmailController.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Nested
    @DisplayName("Index Page Tests")
    class IndexPageTests {

        @Test
        @DisplayName("GET / should render index page with model attributes")
        void index_shouldRenderIndexPageWithModelAttributes() {
            // Arrange
            List<String> emails = Arrays.asList("user1@test.com", "user2@test.com");
            when(mockEmailListService.loadEmailList()).thenReturn(emails);
            when(mockFacebookService.isEnabled()).thenReturn(true);
            Model model = new ExtendedModelMap();

            // Act
            String viewName = controller.index(model);

            // Assert
            assertThat(viewName).isEqualTo("index");
            assertThat(model.getAttribute("emails")).isEqualTo(emails);
            assertThat(model.getAttribute("emailCount")).isEqualTo(2);
            assertThat(model.getAttribute("editorDefaultTextColor")).isEqualTo("#000000");
            assertThat(model.getAttribute("templateSlots")).isEqualTo(5);
            assertThat(model.getAttribute("facebookEnabled")).isEqualTo(true);
        }

        @Test
        @DisplayName("GET / should handle empty email list")
        void index_shouldHandleEmptyEmailList() {
            // Arrange
            when(mockEmailListService.loadEmailList()).thenReturn(new ArrayList<>());
            when(mockFacebookService.isEnabled()).thenReturn(false);
            Model model = new ExtendedModelMap();

            // Act
            String viewName = controller.index(model);

            // Assert
            assertThat(viewName).isEqualTo("index");
            assertThat(model.getAttribute("emailCount")).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("Send Email Tests")
    class SendEmailTests {

        @Test
        @DisplayName("POST /api/send should send individual emails")
        void sendEmails_shouldSendIndividualEmails() {
            // Arrange
            EmailRequest request = new EmailRequest();
            request.setSubject("Test Subject");
            request.setHtmlContent("<p>Hello</p>");
            request.setSendToAll(false);
            request.setSelectedEmails(new String[]{"user@test.com"});
            request.setSendMode("individual");
            request.setAddressMode("to");

            SendResult expectedResult = new SendResult(1, 1, 0, "Success", new ArrayList<>());
            when(mockEmailSenderService.sendIndividual(anyList(), anyString(), anyString(), anyBoolean()))
                .thenReturn(expectedResult);

            // Act
            ResponseEntity<SendResult> response = controller.sendEmails(request);

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatusCode.valueOf(200));
            assertThat(response.getBody()).isEqualTo(expectedResult);
            verify(mockEmailSenderService).sendIndividual(anyList(), eq("Test Subject"), eq("<p>Hello</p>"), eq(false));
        }

        @Test
        @DisplayName("POST /api/send should send batch emails with BCC")
        void sendEmails_shouldSendBatchEmailsWithBcc() {
            // Arrange
            EmailRequest request = new EmailRequest();
            request.setSubject("Test Subject");
            request.setHtmlContent("<p>Hello</p>");
            request.setSendToAll(false);
            request.setSelectedEmails(new String[]{"user1@test.com", "user2@test.com"});
            request.setSendMode("batch");
            request.setAddressMode("bcc");
            request.setBatchSize(10);
            request.setDelayMs(500);

            SendResult expectedResult = new SendResult(2, 2, 0, "Success", new ArrayList<>());
            when(mockEmailSenderService.sendBatch(anyList(), anyString(), anyString(), anyInt(), anyBoolean(), anyInt()))
                .thenReturn(expectedResult);

            // Act
            ResponseEntity<SendResult> response = controller.sendEmails(request);

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatusCode.valueOf(200));
            verify(mockEmailSenderService).sendBatch(anyList(), eq("Test Subject"), eq("<p>Hello</p>"), eq(10), eq(true), eq(500));
        }

        @Test
        @DisplayName("POST /api/send should send to all emails when sendToAll is true")
        void sendEmails_shouldSendToAllEmails() {
            // Arrange
            List<String> allEmails = Arrays.asList("user1@test.com", "user2@test.com", "user3@test.com");
            when(mockEmailListService.loadEmailList()).thenReturn(allEmails);

            EmailRequest request = new EmailRequest();
            request.setSubject("Test Subject");
            request.setHtmlContent("<p>Hello</p>");
            request.setSendToAll(true);
            request.setSendMode("individual");
            request.setAddressMode("to");

            SendResult expectedResult = new SendResult(3, 3, 0, "Success", new ArrayList<>());
            when(mockEmailSenderService.sendIndividual(anyList(), anyString(), anyString(), anyBoolean()))
                .thenReturn(expectedResult);

            // Act
            ResponseEntity<SendResult> response = controller.sendEmails(request);

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatusCode.valueOf(200));
            verify(mockEmailListService).loadEmailList();
        }

        @Test
        @DisplayName("POST /api/send should use default batch size when null")
        void sendEmails_shouldUseDefaultBatchSize() {
            // Arrange
            EmailRequest request = new EmailRequest();
            request.setSubject("Test");
            request.setHtmlContent("<p>Test</p>");
            request.setSendToAll(false);
            request.setSelectedEmails(new String[]{"user@test.com"});
            request.setSendMode("batch");
            request.setAddressMode("to");
            request.setBatchSize(null);
            request.setDelayMs(null);

            SendResult expectedResult = new SendResult(1, 1, 0, "Success", new ArrayList<>());
            when(mockEmailSenderService.sendBatch(anyList(), anyString(), anyString(), anyInt(), anyBoolean(), anyInt()))
                .thenReturn(expectedResult);

            // Act
            controller.sendEmails(request);

            // Assert
            verify(mockEmailSenderService).sendBatch(anyList(), anyString(), anyString(), eq(10), eq(false), eq(500));
        }
    }

    @Nested
    @DisplayName("Test Email Tests")
    class TestEmailTests {

        @Test
        @DisplayName("POST /api/send-test should send test email successfully")
        void sendTestEmail_shouldSendSuccessfully() throws Exception {
            // Arrange
            EmailRequest request = new EmailRequest();
            request.setSubject("Test Subject");
            request.setHtmlContent("<p>Test</p>");
            request.setSelectedEmails(new String[]{"test@test.com"});

            doNothing().when(mockEmailSenderService).sendTestEmail(anyString(), anyString(), anyString());

            // Act
            ResponseEntity<Map<String, Object>> response = controller.sendTestEmail(request);

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatusCode.valueOf(200));
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().get("success")).isEqualTo(true);
            assertThat(response.getBody().get("message").toString()).contains("test@test.com");
        }

        @Test
        @DisplayName("POST /api/send-test should use default email when none provided")
        void sendTestEmail_shouldUseDefaultEmail() throws Exception {
            // Arrange
            EmailRequest request = new EmailRequest();
            request.setSubject("Test");
            request.setHtmlContent("<p>Test</p>");
            request.setSelectedEmails(null);

            doNothing().when(mockEmailSenderService).sendTestEmail(anyString(), anyString(), anyString());

            // Act
            controller.sendTestEmail(request);

            // Assert
            verify(mockEmailSenderService).sendTestEmail(eq("om@kisoft.ca"), anyString(), anyString());
        }

        @Test
        @DisplayName("POST /api/send-test should handle failures")
        void sendTestEmail_shouldHandleFailure() throws Exception {
            // Arrange
            EmailRequest request = new EmailRequest();
            request.setSubject("Test");
            request.setHtmlContent("<p>Test</p>");
            request.setSelectedEmails(new String[]{"test@test.com"});

            doThrow(new RuntimeException("SMTP error")).when(mockEmailSenderService)
                .sendTestEmail(anyString(), anyString(), anyString());

            // Act
            ResponseEntity<Map<String, Object>> response = controller.sendTestEmail(request);

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatusCode.valueOf(200));
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().get("success")).isEqualTo(false);
            assertThat(response.getBody().get("message").toString()).contains("Failed");
        }
    }

    @Nested
    @DisplayName("Email List Management Tests")
    class EmailListTests {

        @Test
        @DisplayName("GET /api/emails should return email list")
        void getEmails_shouldReturnEmailList() {
            // Arrange
            List<String> emails = Arrays.asList("user1@test.com", "user2@test.com");
            when(mockEmailListService.loadEmailList()).thenReturn(emails);

            // Act
            ResponseEntity<List<String>> response = controller.getEmails();

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatusCode.valueOf(200));
            assertThat(response.getBody()).containsExactly("user1@test.com", "user2@test.com");
        }

        @Test
        @DisplayName("POST /api/emails should add email successfully")
        void addEmail_shouldAddSuccessfully() throws IOException {
            // Arrange
            Map<String, String> payload = new HashMap<>();
            payload.put("email", "new@test.com");
            when(mockEmailListService.getEmailCount()).thenReturn(3);

            // Act
            ResponseEntity<Map<String, Object>> response = controller.addEmail(payload);

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatusCode.valueOf(200));
            assertThat(response.getBody().get("success")).isEqualTo(true);
            assertThat(response.getBody().get("count")).isEqualTo(3);
            verify(mockEmailListService).addEmail("new@test.com");
        }

        @Test
        @DisplayName("POST /api/emails should handle add failure")
        void addEmail_shouldHandleFailure() throws IOException {
            // Arrange
            Map<String, String> payload = new HashMap<>();
            payload.put("email", "invalid");
            doThrow(new IOException("Write failed")).when(mockEmailListService).addEmail(anyString());

            // Act
            ResponseEntity<Map<String, Object>> response = controller.addEmail(payload);

            // Assert
            assertThat(response.getBody().get("success")).isEqualTo(false);
        }

        @Test
        @DisplayName("DELETE /api/emails should remove email successfully")
        void removeEmail_shouldRemoveSuccessfully() throws IOException {
            // Arrange
            Map<String, String> payload = new HashMap<>();
            payload.put("email", "remove@test.com");
            when(mockEmailListService.getEmailCount()).thenReturn(2);

            // Act
            ResponseEntity<Map<String, Object>> response = controller.removeEmail(payload);

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatusCode.valueOf(200));
            assertThat(response.getBody().get("success")).isEqualTo(true);
            verify(mockEmailListService).removeEmail("remove@test.com");
        }

        @Test
        @DisplayName("POST /api/emails/bulk should save email list")
        void saveEmailList_shouldSaveSuccessfully() throws IOException {
            // Arrange
            Map<String, List<String>> payload = new HashMap<>();
            payload.put("emails", Arrays.asList("user1@test.com", "user2@test.com"));
            when(mockEmailListService.getEmailCount()).thenReturn(2);

            // Act
            ResponseEntity<Map<String, Object>> response = controller.saveEmailList(payload);

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatusCode.valueOf(200));
            assertThat(response.getBody().get("success")).isEqualTo(true);
            verify(mockEmailListService).saveEmailList(anyList());
        }
    }

    @Nested
    @DisplayName("Connection Test")
    class ConnectionTests {

        @Test
        @DisplayName("GET /api/test-connection should return connected status")
        void testConnection_shouldReturnConnectedStatus() {
            // Arrange
            when(mockEmailSenderService.testConnection()).thenReturn(true);

            // Act
            ResponseEntity<Map<String, Object>> response = controller.testConnection();

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatusCode.valueOf(200));
            assertThat(response.getBody().get("connected")).isEqualTo(true);
            assertThat(response.getBody().get("message")).isEqualTo("Mail server connection OK");
        }

        @Test
        @DisplayName("GET /api/test-connection should return disconnected status")
        void testConnection_shouldReturnDisconnectedStatus() {
            // Arrange
            when(mockEmailSenderService.testConnection()).thenReturn(false);

            // Act
            ResponseEntity<Map<String, Object>> response = controller.testConnection();

            // Assert
            assertThat(response.getBody().get("connected")).isEqualTo(false);
            assertThat(response.getBody().get("message")).isEqualTo("Mail server connection failed");
        }
    }

    @Nested
    @DisplayName("Facebook Integration Tests")
    class FacebookTests {

        @Test
        @DisplayName("POST /api/facebook/post should post successfully")
        void postToFacebook_shouldPostSuccessfully() {
            // Arrange
            EmailRequest request = new EmailRequest();
            request.setSubject("Test Post");
            request.setHtmlContent("<p>Content</p>");

            when(mockFacebookService.isEnabled()).thenReturn(true);
            when(mockFacebookService.postToPage(anyString(), anyString()))
                .thenReturn(new FacebookService.FacebookPostResult(true, "Posted", "{}"));

            // Act
            ResponseEntity<Map<String, Object>> response = controller.postToFacebook(request);

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatusCode.valueOf(200));
            assertThat(response.getBody().get("success")).isEqualTo(true);
        }

        @Test
        @DisplayName("POST /api/facebook/post should return error when disabled")
        void postToFacebook_shouldReturnErrorWhenDisabled() {
            // Arrange
            EmailRequest request = new EmailRequest();
            request.setSubject("Test");
            request.setHtmlContent("<p>Test</p>");

            when(mockFacebookService.isEnabled()).thenReturn(false);

            // Act
            ResponseEntity<Map<String, Object>> response = controller.postToFacebook(request);

            // Assert
            assertThat(response.getBody().get("success")).isEqualTo(false);
            assertThat(response.getBody().get("message").toString()).contains("not enabled");
        }

        @Test
        @DisplayName("GET /api/facebook/test should return connection status")
        void testFacebookConnection_shouldReturnStatus() {
            // Arrange
            when(mockFacebookService.testConnection()).thenReturn(true);
            when(mockFacebookService.isEnabled()).thenReturn(true);

            // Act
            ResponseEntity<Map<String, Object>> response = controller.testFacebookConnection();

            // Assert
            assertThat(response.getBody().get("connected")).isEqualTo(true);
            assertThat(response.getBody().get("enabled")).isEqualTo(true);
        }
    }

    @Nested
    @DisplayName("Export Tests")
    class ExportTests {

        @Test
        @DisplayName("POST /api/export/pdf should export to PDF")
        void exportToPdf_shouldExportSuccessfully() throws IOException {
            // Arrange
            byte[] pdfBytes = "PDF content".getBytes();
            when(mockExportService.exportToPdf(anyString(), anyString())).thenReturn(pdfBytes);

            EmailController.ExportRequest request = new EmailController.ExportRequest("Subject", "<p>Content</p>");

            // Act
            ResponseEntity<byte[]> response = controller.exportToPdf(request);

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatusCode.valueOf(200));
            assertThat(response.getBody()).isEqualTo(pdfBytes);
            assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
        }

        @Test
        @DisplayName("POST /api/export/pdf should handle export failure")
        void exportToPdf_shouldHandleFailure() throws IOException {
            // Arrange
            when(mockExportService.exportToPdf(anyString(), anyString()))
                .thenThrow(new IOException("Export failed"));

            EmailController.ExportRequest request = new EmailController.ExportRequest("Subject", "<p>Content</p>");

            // Act
            ResponseEntity<byte[]> response = controller.exportToPdf(request);

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatusCode.valueOf(500));
        }

        @Test
        @DisplayName("POST /api/export/docx should export to DOCX")
        void exportToDocx_shouldExportSuccessfully() throws IOException {
            // Arrange
            byte[] docxBytes = "DOCX content".getBytes();
            when(mockExportService.exportToDocx(anyString(), anyString())).thenReturn(docxBytes);

            EmailController.ExportRequest request = new EmailController.ExportRequest("Subject", "<p>Content</p>");

            // Act
            ResponseEntity<byte[]> response = controller.exportToDocx(request);

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatusCode.valueOf(200));
            assertThat(response.getBody()).isEqualTo(docxBytes);
        }

        @Test
        @DisplayName("Export should sanitize filename")
        void exportToPdf_shouldSanitizeFilename() throws IOException {
            // Arrange
            byte[] pdfBytes = "PDF".getBytes();
            when(mockExportService.exportToPdf(anyString(), anyString())).thenReturn(pdfBytes);

            // Subject with invalid filename characters
            EmailController.ExportRequest request = new EmailController.ExportRequest("Test: Subject <with> *special* chars?", "<p>Content</p>");

            // Act
            ResponseEntity<byte[]> response = controller.exportToPdf(request);

            // Assert
            HttpHeaders headers = response.getHeaders();
            String contentDisposition = headers.getFirst("Content-Disposition");
            assertThat(contentDisposition).doesNotContain(":");
            assertThat(contentDisposition).doesNotContain("<");
            assertThat(contentDisposition).doesNotContain(">");
            assertThat(contentDisposition).doesNotContain("*");
            assertThat(contentDisposition).doesNotContain("?");
        }
    }

    @Nested
    @DisplayName("Template Log Tests")
    class TemplateLogTests {

        @Test
        @DisplayName("POST /api/template/log should log operation")
        void logTemplateOperation_shouldLogSuccessfully() {
            // Arrange
            EmailController.TemplateLogRequest request = new EmailController.TemplateLogRequest(
                "SAVE", 1, "Test Subject", 1000, 2, 1, 5, null
            );

            // Act
            ResponseEntity<Map<String, String>> response = controller.logTemplateOperation(request);

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatusCode.valueOf(200));
            assertThat(response.getBody().get("status")).isEqualTo("logged");
            assertThat(response.getBody()).containsKey("timestamp");
        }

        @Test
        @DisplayName("POST /api/template/log should handle image details")
        void logTemplateOperation_shouldHandleImageDetails() {
            // Arrange
            List<Map<String, String>> imageDetails = new ArrayList<>();
            Map<String, String> img1 = new HashMap<>();
            img1.put("type", "png");
            img1.put("size", "1024");
            img1.put("dimensions", "100x100");
            img1.put("filename", "test.png");
            imageDetails.add(img1);

            EmailController.TemplateLogRequest request = new EmailController.TemplateLogRequest(
                "LOAD", 2, "Subject", 500, 1, 0, 0, imageDetails
            );

            // Act
            ResponseEntity<Map<String, String>> response = controller.logTemplateOperation(request);

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatusCode.valueOf(200));
        }
    }
}

