# Web-List-EMailer

A Spring Boot web application for managing email distribution lists and sending HTML emails with automatic conversion for maximum email client compatibility.

![Java](https://img.shields.io/badge/Java-21-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.0-green)
![License](https://img.shields.io/badge/License-Proprietary-blue)

## 📋 Overview

**Web-List-EMailer** is a self-hosted email list management tool designed for sending mass HTML emails with rich formatting. It provides a web-based interface for composing emails with images and text, automatically converting modern CSS layouts to email-client-compatible table-based HTML.

### Key Capabilities

- 📧 **Email List Management** - Create, edit, and manage recipient lists
- ✍️ **Rich HTML Editor** - WYSIWYG email composition with formatting tools
- 🖼️ **Image Support** - Embedded images with automatic sizing and positioning
- 🔄 **Email-Safe Conversion** - Automatic CSS-to-table conversion for Outlook compatibility
- 🔐 **Secure Configuration** - Encrypted SMTP credentials using Jasypt
- 📊 **Send Tracking** - Real-time progress with success/failure reporting

---

## 🚀 Features

### Email Composition
- Rich HTML editor with formatting toolbar
- Image embedding with drag-and-drop support
- Side-by-side image and text layouts
- Automatic proportional image scaling

### Sending Modes
| Mode | Description | Use Case |
|------|-------------|----------|
| **Individual** | One email per recipient | Maximum reliability, slower |
| **Batch** | Multiple recipients per email | Faster, configurable batch size |

### Address Modes
| Mode | Description | Recommendation |
|------|-------------|----------------|
| **To** | Recipients visible to each other | Small groups |
| **BCC** | Recipients hidden from each other | Mass emails (recommended) |

### HTML Conversion Engine
The application includes a sophisticated HTML converter that ensures emails render correctly across all email clients:

- **Float-to-Table Conversion** - CSS floats converted to table-based layouts
- **MSO Compatibility** - VML and conditional comments for Microsoft Outlook
- **Inline Styles** - All CSS converted to inline styles for maximum compatibility
- **Image Optimization** - Proper width/height attributes for email clients

---

## 🛠️ Technology Stack

| Component | Technology | Version |
|-----------|------------|---------|
| **Language** | Java | 21 |
| **Framework** | Spring Boot | 4.0.0 |
| **Build Tool** | Maven | 3.8+ |
| **Template Engine** | Thymeleaf | 3.1.x |
| **HTML Parsing** | Jsoup | 1.18.3 |
| **Encryption** | Jasypt Spring Boot | 3.0.5 |
| **Testing** | JUnit 5, Mockito, AssertJ | - |
| **Code Generation** | Lombok | 1.18.30 |

---

## 📦 Prerequisites

Before running the application, ensure you have:

- **Java 21** or later (JDK, not just JRE)
- **Maven 3.8+** (or use included `mvnw` wrapper)
- **SMTP Server** credentials for sending emails

---

## ⚡ Quick Start

### 1. Clone the Repository

```bash
git clone https://github.com/your-username/Web-List-EMailer.git
cd Web-List-EMailer
```

### 2. Configure SMTP Settings

Edit `src/main/resources/application.properties`:

```properties
# SMTP Server Configuration
spring.mail.host=smtp.your-provider.com
spring.mail.port=587
spring.mail.username=your-email@example.com
spring.mail.password=ENC(your-encrypted-password)

# Sender Information
mail.from=your-email@example.com
mail.from.name=Your Name or Organization

# Jasypt encryption key (use environment variable in production!)
jasypt.encryptor.password=your-secret-key
```

### 3. Encrypt Your SMTP Password

Generate an encrypted password using Jasypt:

```bash
# Using the Jasypt CLI
java -cp jasypt.jar org.jasypt.intf.cli.JasyptPBEStringEncryptionCLI \
  algorithm=PBEWithHMACSHA512AndAES_256 \
  password=your-secret-key \
  input=YOUR_SMTP_PASSWORD
```

Then wrap the output in `ENC()`:
```properties
spring.mail.password=ENC(encrypted-output-here)
```

### 4. Run the Application

**Option A: Using the provided script (Windows)**
```cmd
run-app.cmd
```
This will:
1. Build the application if needed
2. Start the server on port 8082
3. Automatically open your browser

**Option B: Using Maven**
```bash
./mvnw spring-boot:run
```

**Option C: Using the JAR directly**
```bash
./mvnw clean package -DskipTests
java -jar target/Web-List-EMailer-0.0.1-SNAPSHOT.jar
```

### 5. Access the Application

Open your browser and navigate to:
```
http://localhost:8082
```

---

## 📁 Project Structure

```
Web-List-EMailer/
├── .github/
│   └── copilot-instructions.md    # AI coding guidelines
├── src/
│   ├── main/
│   │   ├── java/com/kisoft/emaillist/
│   │   │   ├── config/            # Configuration classes
│   │   │   │   └── JasyptDebugRunner.java
│   │   │   ├── controller/        # REST & Web controllers
│   │   │   │   └── EmailController.java
│   │   │   ├── model/             # DTOs and domain objects
│   │   │   │   ├── EmailRequest.java
│   │   │   │   └── SendResult.java
│   │   │   ├── service/           # Business logic
│   │   │   │   ├── EmailListService.java
│   │   │   │   └── EmailSenderService.java
│   │   │   └── EmailListApplication.java
│   │   └── resources/
│   │       ├── static/
│   │       │   ├── css/app.css    # Application styles
│   │       │   └── js/app.js      # Frontend JavaScript
│   │       ├── templates/
│   │       │   └── index.html     # Main Thymeleaf template
│   │       ├── application.properties
│   │       └── email-list.txt     # Default email list
│   └── test/
│       └── java/                  # Unit and integration tests
├── email-list.txt                 # External email list (preferred)
├── pom.xml                        # Maven build configuration
├── mvnw / mvnw.cmd               # Maven wrapper scripts
├── run-app.cmd                   # Windows launch script
└── README.md
```

---

## 🔧 Configuration Reference

### Application Properties

| Property | Description | Default |
|----------|-------------|---------|
| `server.port` | HTTP server port | `8082` |
| `spring.mail.host` | SMTP server hostname | - |
| `spring.mail.port` | SMTP server port | `465` |
| `spring.mail.username` | SMTP username | - |
| `spring.mail.password` | SMTP password (encrypted) | - |
| `mail.from` | From email address | - |
| `mail.from.name` | From display name | `Email Sender` |
| `email.list.file` | Email list filename | `email-list.txt` |
| `jasypt.encryptor.password` | Encryption master key | - |
| `jasypt.encryptor.algorithm` | Encryption algorithm | `PBEWithHMACSHA512AndAES_256` |

### Environment Variables

For production deployments, use environment variables for sensitive data:

```bash
# Required for password decryption
export JASYPT_PASSWORD=your-secret-encryption-key

# Override SMTP settings
export SPRING_MAIL_HOST=smtp.production.com
export SPRING_MAIL_USERNAME=prod@example.com
```

---

## 🌐 API Endpoints

### Web Interface

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/` | Main application page |

### REST API

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/send` | Send emails (batch/individual) |
| `POST` | `/api/send-test` | Send test email to single address |
| `GET` | `/api/emails` | Get all emails in list |
| `POST` | `/api/emails` | Add single email to list |
| `DELETE` | `/api/emails` | Remove single email from list |
| `POST` | `/api/emails/bulk` | Replace entire email list |
| `GET` | `/api/test-connection` | Test SMTP connection |

### Send Email Request Body

```json
{
  "subject": "Email Subject",
  "htmlContent": "<p>HTML content here</p>",
  "sendToAll": false,
  "selectedEmails": ["user1@example.com", "user2@example.com"],
  "sendMode": "batch",
  "addressMode": "bcc",
  "batchSize": 10,
  "delaySeconds": 2
}
```

### Send Result Response

```json
{
  "totalEmails": 100,
  "successCount": 98,
  "failCount": 2,
  "message": "Batch sending complete. Success: 98, Failed: 2 out of 100 total",
  "failedEmails": ["bad1@example.com", "bad2@example.com"],
  "errorMessages": {
    "bad1@example.com": "Invalid address",
    "bad2@example.com": "Mailbox not found"
  }
}
```

---

## 📧 Email HTML Conversion

The application automatically converts modern HTML/CSS to email-safe formats:

### Before (Editor HTML)
```html
<img src="photo.jpg" style="float:left; width:260px; margin-right:15px;">
<p>Text wraps around the image. This is how modern CSS works.</p>
```

### After (Email-Safe HTML)
```html
<table role="presentation" cellpadding="0" cellspacing="0" border="0" width="100%">
  <tr>
    <td width="260" valign="top">
      <img src="photo.jpg" width="260" style="display:block; height:auto;">
    </td>
    <td valign="top">
      <p>Text wraps around the image. This is how modern CSS works.</p>
    </td>
  </tr>
</table>
```

### Conversion Features

- ✅ Float-based layouts → Table-based layouts
- ✅ CSS width/height → HTML attributes
- ✅ Margin/padding → Table cell spacing
- ✅ Automatic `height:auto` for proportional scaling
- ✅ MSO conditional comments for Outlook
- ✅ VML fallbacks for background images

---

## 🧪 Development

### Build the Project

```bash
# Full build with tests
./mvnw clean package

# Build without tests (faster)
./mvnw clean package -DskipTests
```

### Run Tests

```bash
# Run all tests
./mvnw test

# Run specific test class
./mvnw test -Dtest=EmailSenderServiceHtmlConversionTest

# Run with verbose output
./mvnw test -X
```

### Run in Development Mode

```bash
# With auto-reload (DevTools)
./mvnw spring-boot:run

# With debug logging
./mvnw spring-boot:run -Dspring-boot.run.arguments="--logging.level.com.kisoft=DEBUG"
```

### Code Style

The project follows these conventions:
- Java 21 language features (records, switch expressions)
- Lombok for boilerplate reduction
- SLF4J for logging
- AssertJ for test assertions

---

## 🐛 Troubleshooting

### Images Not Scaling Proportionally

**Problem**: Images appear stretched or have incorrect proportions in received emails.

**Solution**: Ensure the HTML converter removes explicit `height` attributes and uses `height:auto` in styles. The converter should handle this automatically.

### Jasypt Decryption Errors

**Problem**: Application fails to start with encryption errors.

**Solutions**:
1. Verify `jasypt.encryptor.password` is set correctly
2. Ensure encrypted values use `ENC(...)` wrapper
3. Check the encryption algorithm matches
4. Review Jasypt debug output in logs:
   ```
   Jasypt Debug:
   Password IS decrypted.
   Password length: 9
   ```

### SMTP Connection Issues

**Problem**: Emails fail to send with connection errors.

**Solutions**:
1. Verify SMTP host and port settings
2. Check firewall rules for outbound connections
3. Ensure SSL/TLS settings match your provider:
   - Port 465: SSL enabled
   - Port 587: STARTTLS
   - Port 25: Plain (not recommended)
4. Test connection using the `/api/test-connection` endpoint

### Email Layout Differs from Editor

**Problem**: Received emails look different from the editor preview.

**Solutions**:
1. Use the "Preview" feature to see converted HTML
2. Ensure images have explicit widths
3. Avoid complex CSS (flexbox, grid) - use simple floats
4. Test with multiple email clients

---

## 📄 License

This project is proprietary software. All rights reserved.

---

## 👤 Author

**KiSoft** - Email List Management Solutions

---

## 📚 Additional Resources

- [Spring Boot Documentation](https://docs.spring.io/spring-boot/docs/current/reference/html/)
- [Jasypt Spring Boot](https://github.com/ulisesbocchio/jasypt-spring-boot)
- [Email HTML Best Practices](https://www.campaignmonitor.com/css/)
- [Jsoup Documentation](https://jsoup.org/)

---

*Built with Spring Boot and ❤️*

