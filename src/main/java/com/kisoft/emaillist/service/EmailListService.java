package com.kisoft.emaillist.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Email List Service - Manages the recipient email list.
 * This service handles loading, saving, and manipulating the email recipient list.
 * It supports two storage locations:
 * 1. External file: {@code email-list.txt} in the application's working directory (preferred)
 * 2. Classpath resource: {@code email-list.txt} in {@code src/main/resources} (fallback)
 * Email Normalization:
 * All emails are cleaned/normalized by:
 * - Removing BOM (Byte Order Mark) characters
 * - Removing zero-width spaces and format characters
 * - Converting to lowercase
 * - Trimming whitespace
 * Duplicate Handling:
 * The service uses {@code LinkedHashSet} internally to prevent duplicates while
 * maintaining insertion order.
 * Configuration:
 * - {@code email.list.file} - Configurable filename (default: {@code email-list.txt})
 * @author KiSoft
 * @version 1.0.0
 * @since 2025-12-26
 * @see com.kisoft.emaillist.controller.EmailController
 */
@Service
@Slf4j
public class EmailListService {

    /** Configurable email list filename (default: email-list.txt) */
    @Value("${email.list.file:email-list.txt}")
    private String emailListFile;

    // ========================================================================
    // Character Constants for Email Cleaning
    // ========================================================================

    /** UTF-8 BOM (Byte Order Mark) character */
    private static final String BOM = "\uFEFF";

    /** Zero-width space character */
    private static final String ZERO_WIDTH_SPACE = "\u200B";

    /** Zero-width no-break space character */
    private static final String ZERO_WIDTH_NO_BREAK_SPACE = "\uFEFF";

    /**
     * Gets the path to the external email list file.
     * @return Path to {@code email-list.txt} in current working directory
     */
    private Path getExternalFilePath() {
        return Paths.get(System.getProperty("user.dir"), "email-list.txt");
    }

    /**
     * Cleans and normalizes an email address.
     * Removes invisible characters, BOM, and normalizes to lowercase.
     * @param email Raw email string
     * @return Cleaned, lowercase email or {@code null} if input was {@code null}
     */
    private String cleanEmail(String email) {
        if (email == null) return null;
        return email
            .replace(BOM, "")
            .replace(ZERO_WIDTH_SPACE, "")
            .replace(ZERO_WIDTH_NO_BREAK_SPACE, "")
            .replaceAll("[\\p{Cf}]", "") // Remove all format characters
            .trim()
            .toLowerCase();
    }

    /**
     * Loads the email list from external file or classpath resource.
     * Priority order:
     * 1. External file: {@code {working-dir}/email-list.txt}
     * 2. Classpath resource: {@code src/main/resources/email-list.txt}
     * Emails are deduplicated while maintaining order.
     * @return List of unique, cleaned email addresses
     */
    public List<String> loadEmailList() {
        Set<String> uniqueEmails = new LinkedHashSet<>();

        // First try to load from external file
        Path externalPath = getExternalFilePath();
        log.info("Looking for external email list at: {}", externalPath.toAbsolutePath());

        if (Files.exists(externalPath)) {
            try (BufferedReader reader = Files.newBufferedReader(externalPath, StandardCharsets.UTF_8)) {
                reader.lines()
                    .map(this::cleanEmail)
                    .filter(line -> !line.isEmpty() && line.contains("@"))
                    .forEach(uniqueEmails::add);
                log.info("Loaded {} emails from external file", uniqueEmails.size());
                return new ArrayList<>(uniqueEmails);
            } catch (IOException e) {
                log.warn("Failed to read external email list: {}", e.getMessage());
            }
        }

        // Fallback: Load from classpath resource
        try {
            log.info("Loading email list from classpath: {}", emailListFile);
            ClassPathResource resource = new ClassPathResource(emailListFile);
            if (resource.exists()) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                    reader.lines()
                        .map(this::cleanEmail)
                        .filter(line -> !line.isEmpty() && line.contains("@"))
                        .forEach(uniqueEmails::add);
                }
                log.info("Loaded {} emails from classpath resource", uniqueEmails.size());
            } else {
                log.warn("Email list resource not found: {}", emailListFile);
            }
        } catch (IOException e) {
            log.error("Error loading email list from classpath: {}", e.getMessage());
        }

        return new ArrayList<>(uniqueEmails);
    }

    /**
     * Saves the email list to the external file.
     *
     * <p>Emails are cleaned, deduplicated, and saved in UTF-8 encoding.</p>
     *
     * @param emails List of email addresses to save
     * @throws IOException If the file cannot be written
     */
    public void saveEmailList(List<String> emails) throws IOException {
        Set<String> uniqueEmails = emails.stream()
            .map(this::cleanEmail)
            .filter(e -> !e.isEmpty() && e.contains("@"))
            .collect(Collectors.toCollection(LinkedHashSet::new));

        Path externalPath = getExternalFilePath();
        Files.write(externalPath, uniqueEmails, StandardCharsets.UTF_8);
    }

    /**
     * Adds a single email address to the list.
     *
     * <p>Does nothing if the email already exists (case-insensitive).</p>
     *
     * @param email Email address to add
     * @throws IOException If the file cannot be updated
     */
    public void addEmail(String email) throws IOException {
        List<String> emails = loadEmailList();
        String normalizedEmail = cleanEmail(email);
        if (!emails.contains(normalizedEmail)) {
            emails.add(normalizedEmail);
            saveEmailList(emails);
        }
    }

    /**
     * Removes a single email address from the list.
     *
     * @param email Email address to remove
     * @throws IOException If the file cannot be updated
     */
    public void removeEmail(String email) throws IOException {
        List<String> emails = loadEmailList();
        emails.remove(cleanEmail(email));
        saveEmailList(emails);
    }

    /**
     * Gets the total count of email addresses.
     *
     * @return Number of emails in the list
     */
    public int getEmailCount() {
        return loadEmailList().size();
    }
}
