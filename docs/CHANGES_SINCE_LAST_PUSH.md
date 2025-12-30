# Changes Since Last Git Push

**Generated:** December 30, 2025  
**Last Commit:** `8d8a257` - fix: Template HTML normalization and save feedback  
**Branch:** main

---

## Summary Statistics

- **27 files changed**
- **~4,800+ lines added**
- **~140 lines removed**
- **Comprehensive unit test coverage added**

---

## 1. New Features

### 1.1 About Dialog with Version Information (NEW)
- **Files:** `ConfigController.java`, `app.js`, `application.properties`, `pom.xml`
- Added `/api/config/version` REST endpoint to retrieve application version info
- Version is read from Maven `pom.xml` via resource filtering (`@project.version@`)
- About dialog now displays:
  - Application name
  - Version number (from pom.xml)
  - Description
  - Copyright notice (© 2025 KiSoft)
- Added Maven resource filtering configuration in `pom.xml`

### 1.2 Configuration Dialog Enhancements
- **Files:** `ConfigController.java`, `app.js`
- Added support for reading saved (but not yet applied) values from `application-local.properties`
- Configuration now properly persists and loads:
  - SMTP settings
  - Sender settings
  - Editor settings (default text color, template slots)
  - Facebook integration settings
  - Logging configuration
- Template slot changes now correctly update UI after save

---

## 2. Bug Fixes

### 2.1 Email Sending Fix - Multiple Recipients
- **Files:** `EmailController.java`, `EmailSenderService.java`
- Fixed issue where selecting 2 emails would send 2 copies to the first email
- Each email now correctly sent to its intended recipient
- Added detailed logging for debugging email send operations

### 2.2 Table Border Toggle Fix
- **Files:** `app.js`
- Fixed table border "Show" not working when table loaded from template
- Function now detects actual CSS border state when `data-borders-visible` attribute is not set
- Works correctly for tables with or without the tracking attribute

### 2.3 PDF Export Fix
- **Files:** `app.js`, `ExportService.java`
- Fixed PDF export calling browser print dialog instead of generating PDF
- Implemented fallback chain:
  1. Server-side PDF generation via `/api/export/pdf` (best quality)
  2. Client-side `html2pdf.js` 
  3. Browser print dialog (last resort)
- Added proper error handling and user feedback

### 2.4 Table Resize After Template Load
- **Files:** `app.js`
- Fixed table resize handles not working on template-loaded tables
- Increased initialization timeout from 50ms to 100ms
- Added verification and automatic retry mechanism
- Added debug logging

### 2.5 Template HTML Normalization
- **Files:** `TemplateService.java`, `TemplateStorageService.java`
- Fixed image src attribute corruption issues with CSS escape sequences
- Fixed SVG attribute parsing errors
- Removed unnecessary logging of image content

---

## 3. Test Coverage Additions

### 3.1 New Test Files (12 files)
| File | Tests | Purpose |
|------|-------|---------|
| `ConfigControllerTest.java` | 17+ | Configuration API endpoints (incl. version) |
| `EmailControllerTest.java` | 20+ | Email sending API |
| `EmailRequestTest.java` | 10+ | Email request model validation |
| `SendResultTest.java` | 15+ | Send result model |
| `EmailListServiceTest.java` | 20+ | Email list management |
| `EmailSenderServiceTest.java` | 15+ | Email sending service |
| `ExportServiceTest.java` | 20+ | PDF/DOC/HTML export |
| `FacebookServiceTest.java` | 10+ | Facebook integration |
| `TemplateServiceTest.java` | 20+ | Template CRUD operations |
| `application-test.properties` | - | Test configuration |
| `application.properties` (test) | - | Test environment config |

### 3.2 Test File Updates
- `LoggingPatternTest.java` - Extended with additional assertions
- `ResourcePathStartupLoggerTest.java` - Fixed test isolation
- `TomcatConfigTest.java` - Updated configuration
- `TemplateControllerWebMvcTest.java` - Added new test cases
- `TemplateServiceNormalizationTest.java` - Fixed test expectations
- `JasyptEncryptTest.java` - Removed password printing, fixed browser opening

---

## 4. Code Quality Improvements

### 4.1 Detailed Comments Added
All modified files now include comprehensive Javadoc comments with:
- Class-level documentation
- Method descriptions
- Parameter documentation
- Security considerations
- Usage examples

### 4.2 Logging Improvements
- Standardized logging prefixes (e.g., `[CONFIG-API]`, `[TEMPLATE]`, `[EMAIL]`)
- Sensitive data protection (passwords never logged)
- Debug-level logging for troubleshooting

---

## 5. Files Changed Summary

### Backend (Java)
| File | Changes |
|------|---------|
| `ConfigController.java` | +129 lines - Version endpoint, property loading |
| `EmailController.java` | +25 lines - Logging, comments |
| `TemplateController.java` | +20 lines - Logging improvements |
| `EmailSenderService.java` | +46 lines - Multi-recipient fix |
| `ExportService.java` | +97 lines - PDF export improvements |
| `TemplateService.java` | +57 lines - Normalization fixes |
| `TemplateStorageService.java` | +95 lines - Storage improvements |

### Frontend (JavaScript/HTML)
| File | Changes |
|------|---------|
| `app.js` | +550 lines - About dialog, bug fixes, improvements |
| `index.html` | +7 lines - Minor updates |

### Configuration
| File | Changes |
|------|---------|
| `pom.xml` | +17 lines - Maven resource filtering |
| `application.properties` | +8 lines - Version, copyright properties |

### Tests
| File | Status |
|------|--------|
| `ConfigControllerTest.java` | NEW + Updated |
| `EmailControllerTest.java` | NEW |
| `EmailRequestTest.java` | NEW |
| `SendResultTest.java` | NEW |
| `EmailListServiceTest.java` | NEW |
| `EmailSenderServiceTest.java` | NEW |
| `ExportServiceTest.java` | NEW |
| `FacebookServiceTest.java` | NEW |
| `TemplateServiceTest.java` | NEW |
| `LoggingPatternTest.java` | Updated |
| `ResourcePathStartupLoggerTest.java` | Updated |
| `TomcatConfigTest.java` | Updated |
| `TemplateControllerWebMvcTest.java` | Updated |
| `TemplateServiceNormalizationTest.java` | Updated |
| `JasyptEncryptTest.java` | Updated |
| `application.properties` (test) | Updated |

---

## 6. API Changes

### New Endpoints
| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/config/version` | Get app version, name, copyright |

### Modified Endpoints
| Method | Path | Change |
|--------|------|--------|
| GET | `/api/config` | Now reads from local properties file |
| POST | `/api/config` | Enhanced persistence with encryption |

---

## 7. Breaking Changes

**None** - All changes are backward compatible.

---

## 8. Recommended Commit Structure

Split into logical commits:

1. **feat: Add version info to About dialog**
   - ConfigController.java (version endpoint)
   - app.js (showAbout function)
   - application.properties (version, copyright)
   - pom.xml (resource filtering)

2. **fix: Multiple recipient email sending**
   - EmailController.java
   - EmailSenderService.java

3. **fix: Table border toggle and PDF export**
   - app.js (toggleTableBorders, exportToPdf)
   - ExportService.java

4. **fix: Template loading improvements**
   - TemplateService.java
   - TemplateStorageService.java
   - TemplateController.java

5. **test: Add comprehensive unit test coverage**
   - All test files (new and updated)
   - Test configuration files

6. **docs: Update change documentation**
   - CHANGES_2025-12-30.md
   - CHANGES_SINCE_LAST_PUSH.md

---

## 9. Verification Checklist

- [ ] `mvn clean test` passes (218+ tests)
- [ ] Application starts without errors
- [ ] About dialog shows version from pom.xml
- [ ] Email sending works for multiple recipients
- [ ] Table border toggle works on template tables
- [ ] PDF export generates downloadable file
- [ ] Configuration dialog saves and loads values
- [ ] Template slot changes update UI

---

## 10. Notes

### Test Environment
- Tests use mock configurations to prevent:
  - Browser auto-opening during tests
  - Actual email sending
  - External service calls
- Password encryption tests use deterministic values

### Security
- Passwords never logged in plaintext
- Sensitive values encrypted with Jasypt before persistence
- API responses mask password fields

---

*End of Change List*

