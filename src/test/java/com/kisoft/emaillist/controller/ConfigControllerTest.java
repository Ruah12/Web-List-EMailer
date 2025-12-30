package com.kisoft.emaillist.controller;

import org.jasypt.encryption.StringEncryptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ConfigController REST API endpoints.
 * Tests the configuration management endpoints for retrieving and updating
 * application configuration settings.
 * Test Coverage:
 * - GET /api/config - Retrieve current configuration with masked sensitive values
 * - POST /api/config - Update configuration with encryption of sensitive values
 * - Validation of encrypted password handling
 * - Error handling for file write failures
 * @author KiSoft
 * @version 1.0.0
 * @since 2025-12-29
 * @see ConfigController
 */
@ExtendWith(MockitoExtension.class)
class ConfigControllerTest {

    private ConfigController controller;
    private Environment mockEnv;
    private StringEncryptor mockEncryptor;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        mockEnv = mock(Environment.class);
        mockEncryptor = mock(StringEncryptor.class);

        // Create a spy that returns empty Properties for local properties
        // This ensures tests use only the mocked Environment values
        controller = spy(new ConfigController(mockEnv, mockEncryptor));
        lenient().doReturn(new Properties()).when(controller).loadLocalProperties();

        // Set appName via reflection since @Value won't be processed in unit test
        Field appNameField = ConfigController.class.getDeclaredField("appName");
        appNameField.setAccessible(true);
        appNameField.set(controller, "Test-App");
    }

    @Test
    @DisplayName("GET /api/config should return configuration with masked passwords")
    void getConfig_shouldReturnConfigurationWithMaskedPasswords() {
        // Arrange
        when(mockEnv.getProperty("spring.mail.host", "")).thenReturn("smtp.test.com");
        when(mockEnv.getProperty("spring.mail.port", "")).thenReturn("587");
        when(mockEnv.getProperty("spring.mail.username", "")).thenReturn("user@test.com");
        when(mockEnv.getProperty("spring.mail.password", "")).thenReturn("ENC(encryptedPassword)");
        when(mockEnv.getProperty("mail.from", "")).thenReturn("sender@test.com");
        when(mockEnv.getProperty("mail.from.name", "")).thenReturn("Test Sender");
        when(mockEnv.getProperty("app.editor.default.text.color", "white")).thenReturn("black");
        when(mockEnv.getProperty("app.template.slots", "5")).thenReturn("10");
        when(mockEnv.getProperty("facebook.enabled", "false")).thenReturn("true");
        when(mockEnv.getProperty("facebook.email", "")).thenReturn("fb@test.com");
        when(mockEnv.getProperty("facebook.password", "")).thenReturn("plainPassword");
        when(mockEnv.getProperty("facebook.page.id", "")).thenReturn("12345");
        when(mockEnv.getProperty("facebook.access.token", "")).thenReturn("ENC(encryptedToken)");
        when(mockEnv.getProperty("logging.config", "classpath:logback-spring.xml")).thenReturn("custom-logback.xml");

        // Act
        ResponseEntity<Map<String, Object>> response = controller.getConfig();

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatusCode.valueOf(200));
        Map<String, Object> cfg = response.getBody();
        assertThat(cfg).isNotNull();
        assertThat(cfg.get("spring.mail.host")).isEqualTo("smtp.test.com");
        assertThat(cfg.get("spring.mail.port")).isEqualTo("587");
        assertThat(cfg.get("spring.mail.username")).isEqualTo("user@test.com");
        assertThat(cfg.get("spring.mail.password.masked")).isEqualTo(true);
        assertThat(cfg.get("spring.mail.password.encrypted")).isEqualTo(true);
        assertThat(cfg.get("mail.from")).isEqualTo("sender@test.com");
        assertThat(cfg.get("mail.from.name")).isEqualTo("Test Sender");
        assertThat(cfg.get("app.editor.default.text.color")).isEqualTo("black");
        assertThat(cfg.get("app.template.slots")).isEqualTo("10");
        assertThat(cfg.get("facebook.enabled")).isEqualTo("true");
        assertThat(cfg.get("facebook.email")).isEqualTo("fb@test.com");
        assertThat(cfg.get("facebook.password.masked")).isEqualTo(true);
        assertThat(cfg.get("facebook.password.encrypted")).isEqualTo(false);
        assertThat(cfg.get("facebook.page.id")).isEqualTo("12345");
        assertThat(cfg.get("facebook.access.token.masked")).isEqualTo(true);
        assertThat(cfg.get("facebook.access.token.encrypted")).isEqualTo(true);
        assertThat(cfg.get("logging.config")).isEqualTo("custom-logback.xml");
        assertThat(cfg.get("spring.application.name")).isEqualTo("Test-App");
    }

    @Test
    @DisplayName("GET /api/config should detect encrypted passwords")
    void getConfig_shouldDetectEncryptedPasswords() {
        // Arrange - setup all required mocks (more specific first, then general)
        when(mockEnv.getProperty("spring.mail.host", "")).thenReturn("");
        when(mockEnv.getProperty("spring.mail.port", "")).thenReturn("");
        when(mockEnv.getProperty("spring.mail.username", "")).thenReturn("");
        when(mockEnv.getProperty("spring.mail.password", "")).thenReturn("ENC(abc123)");
        when(mockEnv.getProperty("mail.from", "")).thenReturn("");
        when(mockEnv.getProperty("mail.from.name", "")).thenReturn("");
        when(mockEnv.getProperty("app.editor.default.text.color", "white")).thenReturn("white");
        when(mockEnv.getProperty("app.template.slots", "5")).thenReturn("5");
        when(mockEnv.getProperty("facebook.enabled", "false")).thenReturn("false");
        when(mockEnv.getProperty("facebook.email", "")).thenReturn("");
        when(mockEnv.getProperty("facebook.password", "")).thenReturn("");
        when(mockEnv.getProperty("facebook.page.id", "")).thenReturn("");
        when(mockEnv.getProperty("facebook.access.token", "")).thenReturn("");
        when(mockEnv.getProperty("logging.config", "classpath:logback-spring.xml")).thenReturn("classpath:logback-spring.xml");

        // Act
        ResponseEntity<Map<String, Object>> response = controller.getConfig();

        // Assert
        Map<String, Object> cfg = response.getBody();
        assertThat(cfg).isNotNull();
        assertThat(cfg.get("spring.mail.password.encrypted")).isEqualTo(true);
    }

    @Test
    @DisplayName("GET /api/config should detect non-encrypted passwords")
    void getConfig_shouldDetectNonEncryptedPasswords() {
        // Arrange - setup all required mocks
        when(mockEnv.getProperty("spring.mail.host", "")).thenReturn("");
        when(mockEnv.getProperty("spring.mail.port", "")).thenReturn("");
        when(mockEnv.getProperty("spring.mail.username", "")).thenReturn("");
        when(mockEnv.getProperty("spring.mail.password", "")).thenReturn("plainTextPassword");
        when(mockEnv.getProperty("mail.from", "")).thenReturn("");
        when(mockEnv.getProperty("mail.from.name", "")).thenReturn("");
        when(mockEnv.getProperty("app.editor.default.text.color", "white")).thenReturn("white");
        when(mockEnv.getProperty("app.template.slots", "5")).thenReturn("5");
        when(mockEnv.getProperty("facebook.enabled", "false")).thenReturn("false");
        when(mockEnv.getProperty("facebook.email", "")).thenReturn("");
        when(mockEnv.getProperty("facebook.password", "")).thenReturn("");
        when(mockEnv.getProperty("facebook.page.id", "")).thenReturn("");
        when(mockEnv.getProperty("facebook.access.token", "")).thenReturn("");
        when(mockEnv.getProperty("logging.config", "classpath:logback-spring.xml")).thenReturn("classpath:logback-spring.xml");

        // Act
        ResponseEntity<Map<String, Object>> response = controller.getConfig();

        // Assert
        Map<String, Object> cfg = response.getBody();
        assertThat(cfg).isNotNull();
        assertThat(cfg.get("spring.mail.password.encrypted")).isEqualTo(false);
    }

    @Test
    @DisplayName("POST /api/config should encrypt plain text secrets")
    void updateConfig_shouldEncryptPlainTextSecrets() {
        // Arrange
        when(mockEncryptor.encrypt("mySecretPassword")).thenReturn("encryptedValue");

        Map<String, String> request = new HashMap<>();
        request.put("spring.mail.host", "smtp.test.com");
        request.put("spring.mail.password", "mySecretPassword");

        String originalUserDir = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", tempDir.toString());

            // Act
            ResponseEntity<Map<String, String>> response = controller.updateConfig(request);

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatusCode.valueOf(200));
            assertThat(response.getBody()).containsEntry("status", "ok");
            verify(mockEncryptor).encrypt("mySecretPassword");
        } finally {
            System.setProperty("user.dir", originalUserDir);
        }
    }

    @Test
    @DisplayName("POST /api/config should preserve already encrypted secrets")
    void updateConfig_shouldPreserveAlreadyEncryptedSecrets() {
        // Arrange
        Map<String, String> request = new HashMap<>();
        request.put("spring.mail.password", "ENC(alreadyEncrypted)");

        String originalUserDir = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", tempDir.toString());

            // Act
            ResponseEntity<Map<String, String>> response = controller.updateConfig(request);

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatusCode.valueOf(200));
            verify(mockEncryptor, never()).encrypt(anyString());
        } finally {
            System.setProperty("user.dir", originalUserDir);
        }
    }

    @Test
    @DisplayName("POST /api/config should skip empty secrets")
    void updateConfig_shouldSkipEmptySecrets() {
        // Arrange
        Map<String, String> request = new HashMap<>();
        request.put("spring.mail.password", "");
        request.put("facebook.password", "   ");

        String originalUserDir = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", tempDir.toString());

            // Act
            ResponseEntity<Map<String, String>> response = controller.updateConfig(request);

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatusCode.valueOf(200));
            verify(mockEncryptor, never()).encrypt(anyString());
        } finally {
            System.setProperty("user.dir", originalUserDir);
        }
    }

    @Test
    @DisplayName("POST /api/config should save all configuration keys")
    void updateConfig_shouldSaveAllConfigurationKeys() throws IOException {
        // Arrange
        when(mockEncryptor.encrypt("pwd")).thenReturn("encPwd");
        when(mockEncryptor.encrypt("fbPwd")).thenReturn("encFbPwd");
        when(mockEncryptor.encrypt("token")).thenReturn("encToken");

        Map<String, String> request = new HashMap<>();
        request.put("spring.mail.host", "smtp.example.com");
        request.put("spring.mail.port", "465");
        request.put("spring.mail.username", "user@example.com");
        request.put("spring.mail.password", "pwd");
        request.put("mail.from", "from@example.com");
        request.put("mail.from.name", "Sender");
        request.put("app.editor.default.text.color", "white");
        request.put("app.template.slots", "8");
        request.put("facebook.enabled", "true");
        request.put("facebook.email", "fb@example.com");
        request.put("facebook.password", "fbPwd");
        request.put("facebook.page.id", "page123");
        request.put("facebook.access.token", "token");
        request.put("logging.config", "logback.xml");

        String originalUserDir = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", tempDir.toString());

            // Act
            ResponseEntity<Map<String, String>> response = controller.updateConfig(request);

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatusCode.valueOf(200));

            Path configFile = tempDir.resolve("application-local.properties");
            assertThat(Files.exists(configFile)).isTrue();

            String content = Files.readString(configFile, StandardCharsets.UTF_8);
            assertThat(content).contains("spring.mail.host=smtp.example.com");
            assertThat(content).contains("spring.mail.port=465");
            assertThat(content).contains("spring.mail.password=ENC(encPwd)");
            assertThat(content).contains("facebook.password=ENC(encFbPwd)");
            assertThat(content).contains("facebook.access.token=ENC(encToken)");
        } finally {
            System.setProperty("user.dir", originalUserDir);
        }
    }

    @Test
    @DisplayName("POST /api/config should handle encryption errors gracefully")
    void updateConfig_shouldHandleEncryptionErrors() {
        // Arrange
        when(mockEncryptor.encrypt(anyString())).thenThrow(new RuntimeException("Encryption failed"));

        Map<String, String> request = new HashMap<>();
        request.put("spring.mail.password", "password");

        String originalUserDir = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", tempDir.toString());

            // Act
            ResponseEntity<Map<String, String>> response = controller.updateConfig(request);

            // Assert - should still succeed but not include the secret
            assertThat(response.getStatusCode()).isEqualTo(HttpStatusCode.valueOf(200));
        } finally {
            System.setProperty("user.dir", originalUserDir);
        }
    }

    @Test
    @DisplayName("POST /api/config should ignore null values")
    void updateConfig_shouldIgnoreNullValues() {
        // Arrange
        Map<String, String> request = new HashMap<>();
        request.put("spring.mail.host", "smtp.test.com");
        request.put("spring.mail.port", null);

        String originalUserDir = System.getProperty("user.dir");
        try {
            System.setProperty("user.dir", tempDir.toString());

            // Act
            ResponseEntity<Map<String, String>> response = controller.updateConfig(request);

            // Assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatusCode.valueOf(200));
        } finally {
            System.setProperty("user.dir", originalUserDir);
        }
    }

    @Test
    @DisplayName("GET /api/config should handle empty password values")
    void getConfig_shouldHandleEmptyPasswordValues() {
        // Arrange - stub all property calls
        when(mockEnv.getProperty("spring.mail.host", "")).thenReturn("");
        when(mockEnv.getProperty("spring.mail.port", "")).thenReturn("");
        when(mockEnv.getProperty("spring.mail.username", "")).thenReturn("");
        when(mockEnv.getProperty("spring.mail.password", "")).thenReturn("");
        when(mockEnv.getProperty("mail.from", "")).thenReturn("");
        when(mockEnv.getProperty("mail.from.name", "")).thenReturn("");
        when(mockEnv.getProperty("app.editor.default.text.color", "white")).thenReturn("white");
        when(mockEnv.getProperty("app.template.slots", "5")).thenReturn("5");
        when(mockEnv.getProperty("facebook.enabled", "false")).thenReturn("false");
        when(mockEnv.getProperty("facebook.email", "")).thenReturn("");
        when(mockEnv.getProperty("facebook.password", "")).thenReturn("");
        when(mockEnv.getProperty("facebook.page.id", "")).thenReturn("");
        when(mockEnv.getProperty("facebook.access.token", "")).thenReturn("");
        when(mockEnv.getProperty("logging.config", "classpath:logback-spring.xml")).thenReturn("classpath:logback-spring.xml");

        // Act
        ResponseEntity<Map<String, Object>> response = controller.getConfig();

        // Assert
        Map<String, Object> cfg = response.getBody();
        assertThat(cfg).isNotNull();
        assertThat(cfg.get("spring.mail.password.encrypted")).isEqualTo(false);
    }

    @Test
    @DisplayName("GET /api/config should return default values when properties are missing")
    void getConfig_shouldReturnDefaultValues() {
        // Arrange - Environment returns defaults
        when(mockEnv.getProperty("spring.mail.host", "")).thenReturn("");
        when(mockEnv.getProperty("spring.mail.port", "")).thenReturn("");
        when(mockEnv.getProperty("spring.mail.username", "")).thenReturn("");
        when(mockEnv.getProperty("spring.mail.password", "")).thenReturn("");
        when(mockEnv.getProperty("mail.from", "")).thenReturn("");
        when(mockEnv.getProperty("mail.from.name", "")).thenReturn("");
        when(mockEnv.getProperty("app.editor.default.text.color", "white")).thenReturn("white");
        when(mockEnv.getProperty("app.template.slots", "5")).thenReturn("5");
        when(mockEnv.getProperty("facebook.enabled", "false")).thenReturn("false");
        when(mockEnv.getProperty("facebook.email", "")).thenReturn("");
        when(mockEnv.getProperty("facebook.password", "")).thenReturn("");
        when(mockEnv.getProperty("facebook.page.id", "")).thenReturn("");
        when(mockEnv.getProperty("facebook.access.token", "")).thenReturn("");
        when(mockEnv.getProperty("logging.config", "classpath:logback-spring.xml")).thenReturn("classpath:logback-spring.xml");

        // Act
        ResponseEntity<Map<String, Object>> response = controller.getConfig();

        // Assert
        Map<String, Object> cfg = response.getBody();
        assertThat(cfg).isNotNull();
        assertThat(cfg.get("app.editor.default.text.color")).isEqualTo("white");
        assertThat(cfg.get("app.template.slots")).isEqualTo("5");
        assertThat(cfg.get("facebook.enabled")).isEqualTo("false");
    }

    @Test
    @DisplayName("GET /api/config/version should return version info")
    void getVersion_shouldReturnVersionInfo() throws Exception {
        // Arrange - Set version and copyright via reflection
        Field appVersionField = ConfigController.class.getDeclaredField("appVersion");
        appVersionField.setAccessible(true);
        appVersionField.set(controller, "1.2.3-TEST");

        Field appCopyrightField = ConfigController.class.getDeclaredField("appCopyright");
        appCopyrightField.setAccessible(true);
        appCopyrightField.set(controller, "© 2025 Test Corp.");

        // Act
        ResponseEntity<Map<String, String>> response = controller.getVersion();

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatusCode.valueOf(200));
        Map<String, String> versionInfo = response.getBody();
        assertThat(versionInfo).isNotNull();
        assertThat(versionInfo.get("name")).isEqualTo("Test-App");
        assertThat(versionInfo.get("version")).isEqualTo("1.2.3-TEST");
        assertThat(versionInfo.get("copyright")).isEqualTo("© 2025 Test Corp.");
        assertThat(versionInfo.get("description")).isEqualTo("Email Mass Sender Application");
    }

    @Test
    @DisplayName("GET /api/config/version should use default values")
    void getVersion_shouldUseDefaultValues() throws Exception {
        // Arrange - Set appVersion and appCopyright to default values via reflection
        Field appVersionField = ConfigController.class.getDeclaredField("appVersion");
        appVersionField.setAccessible(true);
        appVersionField.set(controller, "0.0.14-SNAPSHOT");

        Field appCopyrightField = ConfigController.class.getDeclaredField("appCopyright");
        appCopyrightField.setAccessible(true);
        appCopyrightField.set(controller, "© 2025 KiSoft. All rights reserved.");

        // Act
        ResponseEntity<Map<String, String>> response = controller.getVersion();

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatusCode.valueOf(200));
        Map<String, String> versionInfo = response.getBody();
        assertThat(versionInfo).isNotNull();
        assertThat(versionInfo.get("version")).isEqualTo("0.0.14-SNAPSHOT");
        assertThat(versionInfo.get("copyright")).isEqualTo("© 2025 KiSoft. All rights reserved.");
    }
}
