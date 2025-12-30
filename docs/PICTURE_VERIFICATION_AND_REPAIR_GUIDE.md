# Picture Verification and Repair Guide

## Overview

Your Sound Bath & Drum Circle template (slot 2) correctly stores two embedded images as data URLs:
- **Image 1**: JPEG image (~104.69 KB)
- **Image 2**: PNG image (~1.49 KB)

The storage and loading mechanisms are working correctly. If images still don't render on screen, the issue is likely at the **rendering/display layer** (HTML editor, email client, or browser security settings).

---

## Browser Console Errors (Fixed)

If you see errors like:
```
GET http://localhost:8082/data:image/jpeg;base64,... 400 (Bad Request)
GET http://localhost:8082/data:image/png;base64,... 404 (Not Found)
```

**This is now automatically fixed!**

The app detects malformed image src attributes (with leading backslashes from browser escaping) and **normalizes them at load time**. No manual repair needed.

### What Was Happening

1. HTML editor creates image with escaped backslash: `src="\data:image/jpeg;base64,..."`
2. Browser sees the backslash and treats it as a file path
3. Tries to fetch: `http://localhost:8082/data:image/...` → **fails**

### The Fix

The service now:
- **On save**: Normalizes image src attributes (strips backslashes, removes whitespace)
- **On load**: Re-normalizes before sending to browser

This ensures images always display correctly, even if previously saved with escaping issues.

---

### Endpoint 1: Verify Images in a Template

**GET** `/api/template/verify/{slot}`

Returns a detailed diagnostic report of all images stored in a template.

**Example:**
```bash
curl -X GET http://localhost:8082/api/template/verify/2
```

**Response:**
```json
{
  "status": "ok",
  "slot": 2,
  "filePath": "D:\\Dev\\AI\\Web-List-EMailer\\configs\\templates\\template-2.json",
  "summary": {
    "imageCount": 2,
    "dataUrlCount": 2,
    "pathCount": 0,
    "missingLocalFiles": 0,
    "images": [
      {
        "type": "data-url",
        "mime": "image/jpeg",
        "size": "104.69 KB",
        "filename": "embedded",
        "srcSummary": "embedded data-url mime=image/jpeg size~=104.69 KB"
      },
      {
        "type": "data-url",
        "mime": "image/png",
        "size": "1.49 KB",
        "filename": "embedded",
        "srcSummary": "embedded data-url mime=image/png size~=1.49 KB"
      }
    ]
  }
}
```

**Interpreting the Results:**
- `imageCount: 2` — Two `<img>` tags found in the template HTML.
- `dataUrlCount: 2` — Both are embedded as data URLs (good for portability and email).
- `pathCount: 0` — No external file paths referenced.
- `missingLocalFiles: 0` — No broken local file references.

---

## Auto-Repair Templates

If a template has malformed image src attributes (e.g., leading backslashes, internal whitespace), use the repair endpoint.

### Endpoint 2: Repair a Template

**POST** `/api/template/repair/{slot}`

Reloads a template and re-saves it with automatic normalization applied to all image src attributes.

**Example:**
```bash
curl -X POST http://localhost:8082/api/template/repair/2
```

**Response:**
```json
{
  "status": "ok",
  "message": "Template saved",
  "slot": 2,
  "filePath": "D:\\Dev\\AI\\Web-List-EMailer\\configs\\templates\\template-2.json",
  "fileSize": 151904
}
```

**What It Fixes:**
- Strips leading backslashes from img src (e.g., `\data:image/...` → `data:image/...`).
- Removes accidental newlines/tabs inside data URLs.
- Preserves the rest of the HTML unchanged.

---

## Save-Time Normalization (Automatic)

Starting now, every template save automatically normalizes image src attributes. This ensures:
- No stray backslashes in stored data URLs.
- No internal whitespace breaking data URL parsing.
- Consistent, portable templates across environments.

**How It Works:**
1. User saves a template via **POST** `/api/template/save`.
2. The service calls `normalizeHtmlContent()` before persisting.
3. All `<img>` tags are inspected and cleaned.
4. The normalized HTML is saved to the template file.

---

## Common Issues and Solutions

### Issue 1: Images Don't Render in Browser Preview

**Likely Cause:** Browser security or CSP (Content Security Policy) blocking data URLs.

**Solution:**
- Check browser console for Content Security Policy violations.
- Ensure your HTML editor allows `<img src="data:image/...">`.
- Update CSP headers if you control them:
  ```
  Content-Security-Policy: img-src 'self' data:;
  ```

### Issue 2: Images Don't Appear in Sent Emails

**Likely Cause:** Email client doesn't support embedded base64 images.

**Solution:**
- Use a service that converts data URLs to attachments on send.
- Or: Host images on a web server and use `https://` URLs instead.
- Or: Accept that some email clients strip images (they require manual allow).

### Issue 3: Verify Shows `pathCount: 1, missingLocalFiles: 1`

**Likely Cause:** Template stored a file path (e.g., `C:\images\pic.jpg`) but the file doesn't exist or isn't accessible.

**Solutions:**
1. **Option A:** Re-save the template with embedded images (paste images directly into the editor instead of linking files).
2. **Option B:** Repair the template to auto-normalize (may remove broken references).
3. **Option C:** Host images on a web server and use `https://` URLs.

---

## Workflow: Fix a Broken Template

1. **Diagnose:**
   ```bash
   curl -X GET http://localhost:8082/api/template/verify/2
   ```

2. **Review the output:**
   - If `missingLocalFiles > 0`: Files are referenced but not found.
   - If `dataUrlCount: 0` and `pathCount > 0`: Images are stored as paths, not embedded.

3. **Repair (if needed):**
   ```bash
   curl -X POST http://localhost:8082/api/template/repair/2
   ```

4. **Verify the repair:**
   ```bash
   curl -X GET http://localhost:8082/api/template/verify/2
   ```

5. **Test rendering:**
   - Load the template in your editor or email preview.
   - Images should now display correctly (if the browser/client supports them).

---

## Logging

All image operations are logged to aid troubleshooting.

**Log Entries:**
- `[TEMPLATE-API] Template slot X image scan: Y image(s)...` — Logged when a template is loaded.
- `[TEMPLATE-SERVICE] Normalized img src: '...' → '...'` — Logged when normalization fixes a src.
- `[TEMPLATE-AUDIT] Image details: [...]` — Logged when template operation is audited.

Check your logs for these entries to understand what's happening during save/load/repair.

---

## Summary

✓ **Storage:** Embedded data URLs are correctly persisted.
✓ **Loading:** Templates load with images intact.
✓ **Verification:** New `/api/template/verify/{slot}` endpoint for diagnostics.
✓ **Auto-Repair:** Normalizes malformed image src on save and via `/api/template/repair/{slot}`.
✓ **Logging:** Safe, concise image logging (no raw base64 in logs).

**Next Steps:**
- If you see images in verify output, the app is working correctly.
- If images still don't render in your HTML editor or email preview, the issue is in the rendering environment (browser, email client, or security policy).
- Use the repair endpoint if you find malformed paths.

Namaste! 🙏

