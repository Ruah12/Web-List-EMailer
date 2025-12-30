package com.kisoft.emaillist.util;

import org.jasypt.encryption.pbe.PooledPBEStringEncryptor;
import org.jasypt.iv.RandomIvGenerator;
import org.junit.jupiter.api.Test;

/**
 * Utility test to encrypt values using Jasypt.
 * Run this test to generate encrypted values for application.properties.
 */
public class JasyptEncryptTest {

    @Test
    void encryptFacebookPassword() {
        PooledPBEStringEncryptor encryptor = new PooledPBEStringEncryptor();
        encryptor.setPassword("kisoft-secret-key");
        encryptor.setAlgorithm("PBEWithHMACSHA512AndAES_256");
        encryptor.setIvGenerator(new RandomIvGenerator());
        encryptor.setPoolSize(1);

        String plainPassword = "bbB7033!";
        String encrypted = encryptor.encrypt(plainPassword);
        
        System.out.println("============================================");
        System.out.println("Facebook Password Encryption:");
        System.out.println("Plain: " + plainPassword);
        System.out.println("Encrypted: ENC(" + encrypted + ")");
        System.out.println("============================================");
        
        // Verify decryption works
        String decrypted = encryptor.decrypt(encrypted);
        System.out.println("Verification - Decrypted: " + decrypted);
        assert decrypted.equals(plainPassword) : "Decryption failed!";
    }
}

