# Verification Guide: Image Loading Fix

## Quick Test (5 minutes)

### 1. Start the Application
```bash
mvn spring-boot:run
# or use your run script
```

Application starts at: `http://localhost:8082`

### 2. Open Browser Developer Tools
Press `F12` → Go to **Console** tab

### 3. Load a Template with Images
Navigate to: **Load Template → Slot 2** (Sound Bath example with 2 images)

### 4. Check Console
**Before Fix** ❌:
```
GET http://localhost:8082/data:image/jpeg;base64,/9j... 400 (Bad Request)
GET http://localhost:8082/data:image/png;base64,iVBO... 404 (Not Found)
```

**After Fix** ✅:
```
(No red errors about data: URLs)
Images display correctly in the HTML editor
```

---

## Detailed Verification

### Test Case 1: Load Template with Images

**Step 1**: Open the template management UI
```
http://localhost:8082
```

**Step 2**: Click "Load Template" → Select Slot 2

**Step 3**: Check:
- ✅ HTML preview shows images
- ✅ No 404/400 errors in console
- ✅ Console shows no red errors about data URLs

**Expected Result**: 
```
Template 2 loaded successfully
2 images displayed correctly
```

---

### Test Case 2: Verify Normalization on Load

**Step 1**: Check template JSON file
```bash
cat configs/templates/template-2.json | grep "data:image"
```

**Step 2**: Observe:
- If template has `\data:image` → normalization on load strips the backslash
- If template has clean `data:image` → no change needed

**Step 3**: Load the template again
```
http://localhost:8082/api/template/load/2
```

**Step 4**: Browser renders the HTML
- Images load without console errors
- Embedded base64 data URLs work correctly

**Expected Result**:
```
✅ Images render
✅ No console errors
✅ Data URLs are valid
```

---

### Test Case 3: Scan Images for Integrity

**Step 1**: Call the verification endpoint
```bash
curl -X GET http://localhost:8082/api/template/verify/2
```

**Step 2**: Review output:
```json
{
  "status": "ok",
  "summary": {
    "imageCount": 2,
    "dataUrlCount": 2,
    "pathCount": 0,
    "missingLocalFiles": 0
  },
  "images": [
    {
      "type": "data-url",
      "mime": "image/jpeg",
      "size": "104.69 KB"
    },
    {
      "type": "data-url",
      "mime": "image/png",
      "size": "1.49 KB"
    }
  ]
}
```

**Expected Result**:
```
✅ Both images are data-urls
✅ Both MIME types are correct
✅ No missing files
✅ Status is "ok"
```

---

## Automated Testing

### Run Unit Tests
```bash
mvn test -DskipITs
```

**Expected Output**:
```
[INFO] BUILD SUCCESS
[INFO] Tests run: XX, Failures: 0, Errors: 0, Skipped: 0
```

### Run Integration Tests (Optional)
```bash
mvn test
```

**Expected Output**:
```
[INFO] BUILD SUCCESS
[INFO] All tests passed
```

---

## Log Inspection

### Check Application Logs

**Location**: `logs/Web-List-EMailer-YYYY-MM-DD_HH-MM-SS.log`

**Look for**:
```
[TEMPLATE-SERVICE] Template 2 loaded successfully: 151904 bytes
[TEMPLATE-SERVICE] Normalized img src: '\data:image/jpeg...' → 'data:image/jpeg...'
```

**Indicates**: Normalization is working correctly

---

## Troubleshooting

### Issue: Images Still Show 404 Errors

**Solution**:
1. Clear browser cache: Ctrl+Shift+Delete → Clear all
2. Hard refresh: Ctrl+Shift+R
3. Close and reopen the browser
4. Check that application was restarted after the fix

### Issue: Some Images Load, Others Don't

**Diagnosis**:
```bash
curl http://localhost:8082/api/template/verify/2
```

**Look for**:
- `"missingLocalFiles": 1` → File-based image references broken
- `"pathCount": 1` → Template uses file paths instead of data URLs

**Solution**: Re-save the template with embedded images

### Issue: Console Still Shows Errors

**Cause**: Browser cache or old template

**Solution**:
```bash
# Force re-save the template
curl -X POST http://localhost:8082/api/template/repair/2

# Then reload in browser
```

---

## Summary

| Check | Status | Details |
|-------|--------|---------|
| Browser Errors | ✅ Fixed | No 404/400 for data URLs |
| Image Display | ✅ Works | Embedded images render correctly |
| Normalization | ✅ Active | On save AND on load |
| Old Templates | ✅ Fixed | Transparently repaired when loaded |
| Performance | ✅ Good | Minimal overhead; only on load |

---

## Need Help?

If images still don't display:
1. ✅ Verify application restarted
2. ✅ Clear browser cache and hard-refresh
3. ✅ Check `/api/template/verify/2` for image health
4. ✅ Call `/api/template/repair/2` to force re-save
5. ✅ Check logs for normalization messages
6. ✅ Review console for red errors

**All green?** 🎉 Images are fixed and working correctly!

