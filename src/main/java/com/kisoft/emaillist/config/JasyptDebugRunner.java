package com.kisoft.emaillist.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

/**
 * Jasypt Debug Runner - Verifies encrypted property decryption at startup.
 *
 * <p>This component runs at application startup to verify that Jasypt
 * encryption is working correctly. It checks if the mail password
 * has been properly decrypted from the ENC(...) format.</p>
 *
 * <h3>Purpose:</h3>
 * <ul>
 *   <li>Detect misconfigured Jasypt encryption early</li>
 *   <li>Log helpful debug information without exposing the full password</li>
 *   <li>Alert developers if the password remains encrypted (missing key)</li>
 * </ul>
 *
 * <h3>Configuration:</h3>
 * <p>Requires the Jasypt encryption password to be set via:</p>
 * <ul>
 *   <li>Environment variable: JASYPT_ENCRYPTOR_PASSWORD</li>
 *   <li>System property: jasypt.encryptor.password</li>
 * </ul>
 *
 * @author KiSoft
 * @version 1.0.0
 * @since 2025-12-26
 * @see com.ulisesbocchio.jasyptspringboot.annotation.EnableEncryptableProperties
 */
@Component
@Slf4j
public class JasyptDebugRunner implements CommandLineRunner {

    /** Mail password - should be decrypted from ENC(...) format by Jasypt */
    @Value("${spring.mail.password}")
    private String password;

    /**
     * Runs at application startup to verify Jasypt decryption.
     *
     * <p>Logs whether the password was successfully decrypted. If the password
     * still starts with "ENC(", it means Jasypt failed to decrypt it (likely
     * due to missing or incorrect encryption key).</p>
     *
     * @param args Command line arguments (not used)
     * @throws Exception If an error occurs during execution
     */
    @Override
    public void run(String... args) throws Exception {
        log.info("==================================================================");
        log.info("Jasypt Debug:");
        if (password.startsWith("ENC(")) {
            log.error("Password is NOT decrypted! It is still: {}", password);
        } else {
            log.info("Password IS decrypted.");
            log.info("Password length: {}", password.length());
            if (password.length() > 2) {
                log.info("Password starts with: {}***", password.substring(0, 2));
            }
        }
        log.info("==================================================================");
    }
}

