# Template Storage Migration - localStorage to File-Based System

## Date: December 28, 2025
## Status: ✅ COMPLETED

---

## Overview

Migrated email template storage from **browser localStorage** to a **file-based system** in the `configs/templates/` folder. This provides better persistence, backup, and management capabilities.

## Problem with localStorage

❌ **Browser localStorage limitations:**
- ❌ Lost when browser data is cleared
- ❌ Not persistent across computers
- ❌ Limited to ~5-10MB per site
- ❌ No server-side backup
- ❌ Not version-controllable
- ❌ Per-browser storage (can't share templates)

## Solution: File-Based Storage

✅ **New file-based approach:**
- ✅ Persistent across browser clears
- ✅ Can be backed up and version controlled
- ✅ Stored in configurable folder (`configs/templates/`)
- ✅ Each template as separate JSON file
- ✅ Server-managed (no browser limitations)
- ✅ Shareable across machines

---

## Architecture

### File Structure
```
{working-directory}/
  configs/
    templates/
      template-1.json
      template-2.json
      template-3.json
      template-4.json
      template-5.json
      ... (up to template-10.json)
```

### Template File Format
```json
{
  "subject": "Email Subject Line",
  "htmlContent": "<p>HTML body content...</p>",
  "createdAt": "2025-12-28T12:00:00Z",
  "updatedAt": "2025-12-28T12:30:00Z"
}
```

### API Endpoints

| Method | Endpoint | Purpose |
|--------|----------|---------|
| POST | `/api/template/save` | Save template to file |
| GET | `/api/template/load/{slot}` | Load template from file |
| DELETE | `/api/template/delete/{slot}` | Delete template file |
| GET | `/api/template/label/{slot}` | Get button label (first 22 chars) |
| POST | `/api/template/log` | Log template operation |
| GET | `/api/template/info` | Get template folder info |

---

## Implementation Details

### Backend Components

#### 1. **TemplateService** (`TemplateService.java`)
```java
// Core service for template file operations
- saveTemplate(slot, subject, htmlContent)
- loadTemplate(slot)
- deleteTemplate(slot)
- getTemplateLabel(slot)
- getTemplateFolderPath()
```

**Features:**
- File I/O with error handling
- JSON serialization/deserialization
- Detailed logging with [TEMPLATE-SERVICE] prefix
- Automatic folder creation

#### 2. **TemplateController** (`TemplateController.java`)
```java
// REST API endpoints for frontend
POST   /api/template/save       - Save template
GET    /api/template/load/{slot} - Load template
DELETE /api/template/delete/{slot} - Delete template
GET    /api/template/label/{slot} - Get label
POST   /api/template/log        - Log operation
GET    /api/template/info       - Get folder info
```

**Logging:**
- All operations logged with [TEMPLATE-API] prefix
- Audit trail with [TEMPLATE-AUDIT] prefix

### Frontend Changes

#### JavaScript Functions Updated:

1. **`saveTemplate(index)`**
   - Changed from: `localStorage.setItem('emailTemplate' + index, JSON.stringify(template))`
   - Changed to: `fetch('/api/template/save', { method: 'POST', ... })`
   - Still provides visual feedback and logging

2. **`loadTemplate(index)`**
   - Changed from: `localStorage.getItem('emailTemplate' + index)`
   - Changed to: `fetch('/api/template/load/' + index)`
   - Async operation with error handling

3. **`getTemplateButtonLabel(index)`**
   - Now returns just the slot number
   - Labels are fetched dynamically

4. **`updateLoadButtonLabels()`**
   - Now calls `/api/template/label/{slot}` for each slot
   - Updates button text asynchronously

### Configuration

**File:** `application.properties`

```properties
# ==============================================================================
# 6.2 EMAIL TEMPLATE STORAGE (File-Based)
# ==============================================================================
# Templates are now persisted to the file system instead of browser localStorage.

# Templates folder path (relative to working directory)
app.templates.folder=configs/templates

# Enable/disable template persistence
app.templates.enabled=true
```

---

## Migration Path

### For Existing Users

**Automatic Migration Script (Optional):**
If you want to preserve existing templates from localStorage:

```javascript
// Run in browser console on first load
const slots = 5;
for (let i = 1; i <= slots; i++) {
    const raw = localStorage.getItem('emailTemplate' + i);
    if (raw) {
        try {
            const template = JSON.parse(raw);
            // Save to new server-based system
            fetch('/api/template/save', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    slot: i,
                    subject: template.subject,
                    htmlContent: template.htmlContent
                })
            });
            console.log('Migrated template slot ' + i);
        } catch(e) {
            console.error('Failed to migrate slot ' + i, e);
        }
    }
}
```

### Step-by-Step for Users

1. **Upgrade Application** to version with file-based templates
2. **First Load** - App detects templates folder and creates it
3. **Templates Created** - Each save creates a JSON file
4. **(Optional) Backup** - Copy `configs/templates/` folder
5. **Old localStorage** - Can be deleted (browser will still work)

---

## Benefits

### For Users
✅ **Persistence** - Templates survive browser cache clear
✅ **Backup** - Easy to backup entire templates folder
✅ **Sharing** - Copy templates folder to another machine
✅ **Reliability** - No storage quota issues

### For Developers
✅ **Debugging** - Can inspect template JSON files directly
✅ **Version Control** - Can track template changes in git
✅ **Server Logging** - Full audit trail of template operations
✅ **Scalability** - No limit on template size
✅ **Testing** - Can create test templates programmatically

### For Operations
✅ **Backup & Restore** - Simple file operations
✅ **Monitoring** - Can track template folder size
✅ **Security** - Can apply filesystem permissions
✅ **Archival** - Easy to archive old template files

---

## Configuration Options

### Change Template Folder Location

**In `application.properties`:**
```properties
# Store templates in different location
app.templates.folder=data/email-templates
```

### Disable Template Persistence

**In `application.properties`:**
```properties
# Disable file-based storage (templates will not save)
app.templates.enabled=false
```

---

## Logging

### Console Logs (Browser)
```javascript
[TEMPLATE] Saving template to slot: 1
[TEMPLATE] Template 1 saved successfully
[TEMPLATE] Loading template from slot: 1
[TEMPLATE] Template 1 loaded successfully
```

### Server Logs
```
[TEMPLATE-SERVICE] Initialized with folder: D:\...\configs\templates
[TEMPLATE-SERVICE] Templates folder created/verified at: ...
[TEMPLATE-SERVICE] Template 1 saved successfully: 1234 bytes
[TEMPLATE-SERVICE] Subject: My Email Subject
[TEMPLATE-SERVICE] Content length: 5678 characters
[TEMPLATE-AUDIT] SAVE template slot=1, subject='My Email Subject', size=5678bytes
[TEMPLATE-AUDIT] Image details: [...]
```

---

## File Locations

### Working Directory
```
${user.dir}/
  configs/
    templates/
      template-1.json
      template-2.json
      ...
```

### Template Folder Path
Can be viewed at: `GET /api/template/info`

Response:
```json
{
  "status": "ok",
  "folderPath": "D:\\Dev\\AI\\Web-List-EMailer\\configs\\templates",
  "maxSlots": 10,
  "timestamp": "2025-12-28T12:00:00Z"
}
```

---

## Error Handling

| Error | Cause | Solution |
|-------|-------|----------|
| "Failed to save template" | No write permission | Check folder permissions |
| "Template X not found" | Slot is empty | Save a template first |
| "Invalid slot number" | Slot < 1 or > 10 | Use slots 1-10 only |
| "Failed to create folder" | Cannot create `configs/` | Check write permissions |

---

## Backup & Restore

### Backup Templates
```bash
# Copy templates folder
cp -r configs/templates configs/templates.backup

# Or zip it
zip -r templates.zip configs/templates
```

### Restore Templates
```bash
# Restore from backup
cp -r configs/templates.backup/* configs/templates/

# Or unzip
unzip templates.zip
```

---

## Testing

### Test Save
1. Compose an email with subject and content
2. Click "Save Template #1"
3. Check: `configs/templates/template-1.json` exists
4. Verify file contains your subject and HTML

### Test Load
1. Clear browser cache/localStorage
2. Click "Load Template #1"
3. Email should appear (from server, not browser cache)

### Test Persistence
1. Save a template
2. Restart browser completely
3. Load the template - should work
4. Restart application
5. Load the template - should still work

---

## Database Migration (If Needed in Future)

Current implementation uses file-based storage. If future versions need database storage:

```sql
-- Example schema for future database migration
CREATE TABLE email_templates (
  id INT PRIMARY KEY AUTO_INCREMENT,
  slot INT NOT NULL UNIQUE (1-10),
  subject VARCHAR(500),
  html_content LONGTEXT,
  created_at TIMESTAMP DEFAULT NOW(),
  updated_at TIMESTAMP DEFAULT NOW() ON UPDATE NOW(),
  user_id INT (for multi-user support)
);
```

---

## Files Modified/Created

### Backend
- ✅ Created: `TemplateService.java`
- ✅ Created: `TemplateController.java`
- ✅ Modified: `application.properties`

### Frontend
- ✅ Modified: `app.js` (saveTemplate, loadTemplate, etc.)

### Configuration
- ✅ Modified: `application.properties`
- ✅ Added: Template storage section

---

## Build Status

✅ **BUILD SUCCESS** - No compilation errors
✅ **JAR Created** - `Web-List-EMailer-0.0.13-SNAPSHOT.jar`
✅ **Ready for Deployment**

---

## Version Information

- **Application:** Web-List-EMailer 0.0.13-SNAPSHOT
- **Java:** 21.0.7
- **Spring Boot:** 4.0.0
- **Migration Date:** December 28, 2025

---

## Summary

**Template storage has been successfully migrated from browser localStorage to a file-based system.**

### Key Changes:
1. ✅ File-based storage in `configs/templates/` folder
2. ✅ Each template as separate JSON file
3. ✅ REST API endpoints for save/load/delete
4. ✅ Server-side logging and audit trail
5. ✅ Configurable folder location
6. ✅ Persistent across browser clears
7. ✅ Ready for backup and version control

### User Impact:
- ✅ No user action required
- ✅ Templates automatically saved to files
- ✅ Existing localStorage templates still work initially
- ✅ Can be migrated using simple script

### Operations:
- ✅ Easy to backup/restore
- ✅ Can monitor folder size
- ✅ Full audit trail available
- ✅ Can archive old templates

