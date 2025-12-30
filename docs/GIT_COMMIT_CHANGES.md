# Git Commit Changes Summary

**Date:** December 30, 2025  
**Version:** 0.0.14-SNAPSHOT  
**Previous Commit:** a8340c14a0555f7add86590d59cf308c2358b856 ("new docs")

## Files Changed Summary

| File | Type | Lines Changed | Description |
|------|------|---------------|-------------|
| `configs/templates/template-1.json` | Added | +1 | New template slot 1 configuration file |
| `configs/templates/template-2.json` | Added | +1 | New template slot 2 configuration file |
| `configs/templates/template-3.json` | Added | +1 | New template slot 3 configuration file |
| `configs/templates/template-4.json` | Added | +1 | New template slot 4 configuration file |
| `src/main/resources/static/js/app.js` | Modified | +1/-1 | Version number update in Help dialog |

**Total:** 5 files changed, 5 insertions(+), 1 deletion(-)

---

## Detailed Changes

### 1. New Template Configuration Files (Added)

**Location:** `configs/templates/`

Four new template slot JSON files were added to store user-saved email templates:

- `template-1.json` - Template slot 1 data
- `template-2.json` - Template slot 2 data  
- `template-3.json` - Template slot 3 data
- `template-4.json` - Template slot 4 data

These files are created by the template save/load functionality and store:
- Email subject
- Email HTML content
- Template metadata

### 2. app.js Version Update (Modified)

**File:** `src/main/resources/static/js/app.js`

**Change:** Updated hardcoded version in Help dialog footer

**Before:**
```javascript
<p><strong>Version:</strong> 0.0.13-SNAPSHOT</p>
```

**After:**
```javascript
<p><strong>Version:</strong> 0.0.14-SNAPSHOT</p>
```

**Reason:** Synchronize displayed version with pom.xml version (0.0.14-SNAPSHOT)

---

## Commit Message Template

```
fix: Update version to 0.0.14-SNAPSHOT in Help dialog

- Updated hardcoded version in app.js Help dialog footer
- Added template configuration files for slots 1-4

Files changed: 5
- configs/templates/template-1.json (new)
- configs/templates/template-2.json (new)
- configs/templates/template-3.json (new)
- configs/templates/template-4.json (new)
- src/main/resources/static/js/app.js (modified)
```

---

## Related Configuration

The About dialog dynamically fetches version from `/api/config/version` endpoint:
- `ConfigController.java` - Returns version from `@Value("${app.version:0.0.14-SNAPSHOT}")`
- `application.properties` - Contains `app.version=@project.version@` (Maven filtered)

When running via Maven (`mvn spring-boot:run`) or from built JAR, the version is correctly populated from pom.xml.

---

© 2025 KiSoft. All rights reserved.

