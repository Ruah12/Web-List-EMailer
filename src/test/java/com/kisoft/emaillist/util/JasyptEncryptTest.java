package com.kisoft.emaillist.util;

import org.jasypt.encryption.pbe.PooledPBEStringEncryptor;
import org.jasypt.iv.RandomIvGenerator;
import org.junit.jupiter.api.Test;

/**
 * Jasypt Encryption Utility Test - Generates encrypted values for application.properties.
 *
 * <p>This utility test provides a convenient way to encrypt sensitive values
 * (passwords, tokens) for use in the application configuration. Run this test
 * to generate {@code ENC(...)} formatted values.</p>
 *
 * <h3>Usage:</h3>
 * <ol>
 *   <li>Set the plaintext value in the test method</li>
 *   <li>Run the test</li>
 *   <li>Copy the {@code ENC(...)} output to application.properties</li>
 * </ol>
 *
 * <h3>Encryption Algorithm:</h3>
 * <p>Uses {@code PBEWithHMACSHA512AndAES_256} with random IV generation.
 * This is a strong, modern encryption algorithm suitable for sensitive data.</p>
 *
 * <h3>Encryption Key:</h3>
 * <p>The encryption key must match the value set in:
 * <ul>
 *   <li>Environment variable: {@code JASYPT_ENCRYPTOR_PASSWORD}</li>
 *   <li>System property: {@code jasypt.encryptor.password}</li>
 * </ul>
 * The key used here must match the runtime key for decryption to succeed.</p>
 *
 * <h3>Security Note:</h3>
 * <p>Never commit plaintext passwords or the encryption key to version control.
 * This utility is for local development and testing only.</p>
 *
 * @author KiSoft
 * @version 1.0.0
 * @since 2025-12-26
 * @see org.jasypt.encryption.pbe.PooledPBEStringEncryptor
 * @see com.kisoft.emaillist.config.JasyptDebugRunner
 */
public class JasyptEncryptTest {

    @Test
    void encryptFacebookPassword() {
        PooledPBEStringEncryptor encryptor = new PooledPBEStringEncryptor();
        encryptor.setPassword("kisoft-secret-key");
        encryptor.setAlgorithm("PBEWithHMACSHA512AndAES_256");
        encryptor.setIvGenerator(new RandomIvGenerator());
        encryptor.setPoolSize(1);

        // Test password (use environment variable or placeholder in real scenarios)
        String plainPassword = System.getenv("TEST_PASSWORD") != null
            ? System.getenv("TEST_PASSWORD")
            : "test-password-placeholder";
        String encrypted = encryptor.encrypt(plainPassword);
        
        // Verify encryption/decryption cycle works without printing sensitive data
        String decrypted = encryptor.decrypt(encrypted);
        assert decrypted.equals(plainPassword) : "Decryption verification failed!";

        // Log success without exposing sensitive values
        System.out.println("[JASYPT-TEST] Encryption/decryption cycle verified successfully");
        System.out.println("[JASYPT-TEST] Encrypted format: ENC(<encrypted-value>)");
    }
}
