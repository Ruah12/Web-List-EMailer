package com.kisoft.emaillist.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for EmailListService.
 * Tests email list loading, saving, and manipulation functionality.
 * Test Coverage:
 * - Loading emails from external file
 * - Loading emails from classpath resource (fallback)
 * - Saving email list to file
 * - Adding single emails
 * - Removing single emails
 * - Email count
 * - Email normalization (BOM removal, lowercase, deduplication)
 * @author KiSoft
 * @version 1.0.0
 * @since 2025-12-29
 * @see EmailListService
 */
class EmailListServiceTest {

    private EmailListService emailListService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        emailListService = new EmailListService();
        // Set emailListFile via reflection
        Field field = EmailListService.class.getDeclaredField("emailListFile");
        field.setAccessible(true);
        field.set(emailListService, "email-list.txt");
    }

    @Nested
    @DisplayName("Load Email List Tests")
    class LoadEmailListTests {

        @Test
        @DisplayName("Should load emails from external file")
        void loadEmailList_shouldLoadFromExternalFile() throws IOException {
            // Arrange
            Path emailFile = Path.of(System.getProperty("user.dir"), "email-list.txt");
            boolean fileExisted = Files.exists(emailFile);
            String originalContent = fileExisted ? Files.readString(emailFile) : null;

            try {
                Files.writeString(emailFile, "user1@test.com\nuser2@test.com\nuser3@test.com\n");

                // Act
                List<String> emails = emailListService.loadEmailList();

                // Assert
                assertThat(emails).contains("user1@test.com", "user2@test.com", "user3@test.com");
            } finally {
                // Restore original file
                if (originalContent != null) {
                    Files.writeString(emailFile, originalContent);
                } else if (!fileExisted) {
                    Files.deleteIfExists(emailFile);
                }
            }
        }

        @Test
        @DisplayName("Should clean BOM from emails")
        void loadEmailList_shouldCleanBom() throws IOException {
            // Arrange
            Path emailFile = Path.of(System.getProperty("user.dir"), "email-list.txt");
            boolean fileExisted = Files.exists(emailFile);
            String originalContent = fileExisted ? Files.readString(emailFile) : null;

            try {
                // Write file with BOM
                String contentWithBom = "\uFEFFuser@test.com\n";
                Files.writeString(emailFile, contentWithBom, StandardCharsets.UTF_8);

                // Act
                List<String> emails = emailListService.loadEmailList();

                // Assert
                assertThat(emails).contains("user@test.com");
                assertThat(emails.get(0)).doesNotStartWith("\uFEFF");
            } finally {
                if (originalContent != null) {
                    Files.writeString(emailFile, originalContent);
                } else if (!fileExisted) {
                    Files.deleteIfExists(emailFile);
                }
            }
        }

        @Test
        @DisplayName("Should normalize emails to lowercase")
        void loadEmailList_shouldNormalizeToLowercase() throws IOException {
            // Arrange
            Path emailFile = Path.of(System.getProperty("user.dir"), "email-list.txt");
            boolean fileExisted = Files.exists(emailFile);
            String originalContent = fileExisted ? Files.readString(emailFile) : null;

            try {
                Files.writeString(emailFile, "User@TEST.COM\nANOTHER@Email.com\n");

                // Act
                List<String> emails = emailListService.loadEmailList();

                // Assert
                assertThat(emails).contains("user@test.com", "another@email.com");
            } finally {
                if (originalContent != null) {
                    Files.writeString(emailFile, originalContent);
                } else if (!fileExisted) {
                    Files.deleteIfExists(emailFile);
                }
            }
        }

        @Test
        @DisplayName("Should filter out invalid emails")
        void loadEmailList_shouldFilterInvalidEmails() throws IOException {
            // Arrange
            Path emailFile = Path.of(System.getProperty("user.dir"), "email-list.txt");
            boolean fileExisted = Files.exists(emailFile);
            String originalContent = fileExisted ? Files.readString(emailFile) : null;

            try {
                Files.writeString(emailFile, "valid@test.com\ninvalid-no-at\n\n   \n");

                // Act
                List<String> emails = emailListService.loadEmailList();

                // Assert
                assertThat(emails).containsExactly("valid@test.com");
            } finally {
                if (originalContent != null) {
                    Files.writeString(emailFile, originalContent);
                } else if (!fileExisted) {
                    Files.deleteIfExists(emailFile);
                }
            }
        }

        @Test
        @DisplayName("Should deduplicate emails")
        void loadEmailList_shouldDeduplicateEmails() throws IOException {
            // Arrange
            Path emailFile = Path.of(System.getProperty("user.dir"), "email-list.txt");
            boolean fileExisted = Files.exists(emailFile);
            String originalContent = fileExisted ? Files.readString(emailFile) : null;

            try {
                Files.writeString(emailFile, "user@test.com\nuser@test.com\nUSER@TEST.COM\n");

                // Act
                List<String> emails = emailListService.loadEmailList();

                // Assert
                assertThat(emails).hasSize(1);
                assertThat(emails).containsExactly("user@test.com");
            } finally {
                if (originalContent != null) {
                    Files.writeString(emailFile, originalContent);
                } else if (!fileExisted) {
                    Files.deleteIfExists(emailFile);
                }
            }
        }

        @Test
        @DisplayName("Should remove zero-width spaces")
        void loadEmailList_shouldRemoveZeroWidthSpaces() throws IOException {
            // Arrange
            Path emailFile = Path.of(System.getProperty("user.dir"), "email-list.txt");
            boolean fileExisted = Files.exists(emailFile);
            String originalContent = fileExisted ? Files.readString(emailFile) : null;

            try {
                // Email with zero-width space
                Files.writeString(emailFile, "user\u200B@test.com\n", StandardCharsets.UTF_8);

                // Act
                List<String> emails = emailListService.loadEmailList();

                // Assert
                assertThat(emails).containsExactly("user@test.com");
            } finally {
                if (originalContent != null) {
                    Files.writeString(emailFile, originalContent);
                } else if (!fileExisted) {
                    Files.deleteIfExists(emailFile);
                }
            }
        }
    }

    @Nested
    @DisplayName("Save Email List Tests")
    class SaveEmailListTests {

        @Test
        @DisplayName("Should save email list to file")
        void saveEmailList_shouldSaveToFile() throws IOException {
            // Arrange
            Path emailFile = Path.of(System.getProperty("user.dir"), "email-list.txt");
            boolean fileExisted = Files.exists(emailFile);
            String originalContent = fileExisted ? Files.readString(emailFile) : null;

            try {
                List<String> emails = Arrays.asList("user1@test.com", "user2@test.com");

                // Act
                emailListService.saveEmailList(emails);

                // Assert
                String content = Files.readString(emailFile);
                assertThat(content).contains("user1@test.com");
                assertThat(content).contains("user2@test.com");
            } finally {
                if (originalContent != null) {
                    Files.writeString(emailFile, originalContent);
                } else if (!fileExisted) {
                    Files.deleteIfExists(emailFile);
                }
            }
        }

        @Test
        @DisplayName("Should deduplicate when saving")
        void saveEmailList_shouldDeduplicateOnSave() throws IOException {
            // Arrange
            Path emailFile = Path.of(System.getProperty("user.dir"), "email-list.txt");
            boolean fileExisted = Files.exists(emailFile);
            String originalContent = fileExisted ? Files.readString(emailFile) : null;

            try {
                List<String> emails = Arrays.asList("user@test.com", "user@test.com", "USER@TEST.COM");

                // Act
                emailListService.saveEmailList(emails);

                // Assert
                List<String> savedEmails = Files.readAllLines(emailFile);
                assertThat(savedEmails).hasSize(1);
            } finally {
                if (originalContent != null) {
                    Files.writeString(emailFile, originalContent);
                } else if (!fileExisted) {
                    Files.deleteIfExists(emailFile);
                }
            }
        }

        @Test
        @DisplayName("Should filter invalid emails when saving")
        void saveEmailList_shouldFilterInvalidOnSave() throws IOException {
            // Arrange
            Path emailFile = Path.of(System.getProperty("user.dir"), "email-list.txt");
            boolean fileExisted = Files.exists(emailFile);
            String originalContent = fileExisted ? Files.readString(emailFile) : null;

            try {
                List<String> emails = Arrays.asList("valid@test.com", "invalid", "", "   ");

                // Act
                emailListService.saveEmailList(emails);

                // Assert
                List<String> savedEmails = Files.readAllLines(emailFile);
                assertThat(savedEmails).containsExactly("valid@test.com");
            } finally {
                if (originalContent != null) {
                    Files.writeString(emailFile, originalContent);
                } else if (!fileExisted) {
                    Files.deleteIfExists(emailFile);
                }
            }
        }
    }

    @Nested
    @DisplayName("Add Email Tests")
    class AddEmailTests {

        @Test
        @DisplayName("Should add new email")
        void addEmail_shouldAddNewEmail() throws IOException {
            // Arrange
            Path emailFile = Path.of(System.getProperty("user.dir"), "email-list.txt");
            boolean fileExisted = Files.exists(emailFile);
            String originalContent = fileExisted ? Files.readString(emailFile) : null;

            try {
                Files.writeString(emailFile, "existing@test.com\n");

                // Act
                emailListService.addEmail("new@test.com");

                // Assert
                List<String> emails = emailListService.loadEmailList();
                assertThat(emails).contains("existing@test.com", "new@test.com");
            } finally {
                if (originalContent != null) {
                    Files.writeString(emailFile, originalContent);
                } else if (!fileExisted) {
                    Files.deleteIfExists(emailFile);
                }
            }
        }

        @Test
        @DisplayName("Should not add duplicate email")
        void addEmail_shouldNotAddDuplicate() throws IOException {
            // Arrange
            Path emailFile = Path.of(System.getProperty("user.dir"), "email-list.txt");
            boolean fileExisted = Files.exists(emailFile);
            String originalContent = fileExisted ? Files.readString(emailFile) : null;

            try {
                Files.writeString(emailFile, "existing@test.com\n");

                // Act
                emailListService.addEmail("EXISTING@TEST.COM");

                // Assert
                List<String> emails = emailListService.loadEmailList();
                assertThat(emails).hasSize(1);
            } finally {
                if (originalContent != null) {
                    Files.writeString(emailFile, originalContent);
                } else if (!fileExisted) {
                    Files.deleteIfExists(emailFile);
                }
            }
        }
    }

    @Nested
    @DisplayName("Remove Email Tests")
    class RemoveEmailTests {

        @Test
        @DisplayName("Should remove existing email")
        void removeEmail_shouldRemoveExistingEmail() throws IOException {
            // Arrange
            Path emailFile = Path.of(System.getProperty("user.dir"), "email-list.txt");
            boolean fileExisted = Files.exists(emailFile);
            String originalContent = fileExisted ? Files.readString(emailFile) : null;

            try {
                Files.writeString(emailFile, "user1@test.com\nuser2@test.com\n");

                // Act
                emailListService.removeEmail("user1@test.com");

                // Assert
                List<String> emails = emailListService.loadEmailList();
                assertThat(emails).containsExactly("user2@test.com");
            } finally {
                if (originalContent != null) {
                    Files.writeString(emailFile, originalContent);
                } else if (!fileExisted) {
                    Files.deleteIfExists(emailFile);
                }
            }
        }

        @Test
        @DisplayName("Should handle case-insensitive removal")
        void removeEmail_shouldHandleCaseInsensitive() throws IOException {
            // Arrange
            Path emailFile = Path.of(System.getProperty("user.dir"), "email-list.txt");
            boolean fileExisted = Files.exists(emailFile);
            String originalContent = fileExisted ? Files.readString(emailFile) : null;

            try {
                Files.writeString(emailFile, "user@test.com\n");

                // Act
                emailListService.removeEmail("USER@TEST.COM");

                // Assert
                List<String> emails = emailListService.loadEmailList();
                assertThat(emails).isEmpty();
            } finally {
                if (originalContent != null) {
                    Files.writeString(emailFile, originalContent);
                } else if (!fileExisted) {
                    Files.deleteIfExists(emailFile);
                }
            }
        }
    }

    @Nested
    @DisplayName("Get Email Count Tests")
    class GetEmailCountTests {

        @Test
        @DisplayName("Should return correct email count")
        void getEmailCount_shouldReturnCorrectCount() throws IOException {
            // Arrange
            Path emailFile = Path.of(System.getProperty("user.dir"), "email-list.txt");
            boolean fileExisted = Files.exists(emailFile);
            String originalContent = fileExisted ? Files.readString(emailFile) : null;

            try {
                Files.writeString(emailFile, "user1@test.com\nuser2@test.com\nuser3@test.com\n");

                // Act
                int count = emailListService.getEmailCount();

                // Assert
                assertThat(count).isEqualTo(3);
            } finally {
                if (originalContent != null) {
                    Files.writeString(emailFile, originalContent);
                } else if (!fileExisted) {
                    Files.deleteIfExists(emailFile);
                }
            }
        }

        @Test
        @DisplayName("Should return zero for empty list")
        void getEmailCount_shouldReturnZeroForEmptyList() throws IOException {
            // Arrange
            Path emailFile = Path.of(System.getProperty("user.dir"), "email-list.txt");
            boolean fileExisted = Files.exists(emailFile);
            String originalContent = fileExisted ? Files.readString(emailFile) : null;

            try {
                Files.writeString(emailFile, "");

                // Act
                int count = emailListService.getEmailCount();

                // Assert
                assertThat(count).isEqualTo(0);
            } finally {
                if (originalContent != null) {
                    Files.writeString(emailFile, originalContent);
                } else if (!fileExisted) {
                    Files.deleteIfExists(emailFile);
                }
            }
        }
    }
}

