package com.kisoft.emaillist.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class JasyptDebugRunner implements CommandLineRunner {

    @Value("${spring.mail.password}")
    private String password;

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

