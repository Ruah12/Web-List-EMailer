# Verification Guide - Configuration Dialog Fix

## Quick Verification (5 minutes)

### Step 1: Start the Application
```bash
cd D:\Dev\AI\Web-List-EMailer
mvn spring-boot:run
```

Wait for: `Started EmailListApplication in X seconds`

### Step 2: Open Browser and Configuration Dialog
1. Browser opens automatically to `http://localhost:8082`
2. Click **Edit** menu (top left)
3. Click **Configuration…**
4. Configuration modal should open

### Step 3: Test "Save" Button (Without Closing)
1. Change **Default Text Color** from "white" to "black"
2. Change **Template Slots** to "8"
3. Click **Save** button (bottom right)
4. Verify status shows **"Saved successfully!"**
5. Modal should **stay open**
6. Edit modal again to verify changes were saved
7. Both fields should have new values

**Expected Result:** ✅ Values saved, modal open, values persisted

### Step 4: Test "Save & Close" Button (With Refresh)
1. Change **Default Text Color** back to "white"
2. Change **Template Slots** back to "5"
3. Click **Save & Close** button (bottom left)
4. Observe modal closes
5. Observe page refreshes automatically
6. Wait for page to fully reload
7. Open configuration again
8. Verify values are saved

**Expected Result:** ✅ Values saved, modal closed, page refreshed, values persisted

### Step 5: Check Browser Console Logs
1. While configuration dialog is open, press **F12** to open Developer Tools
2. Click **Console** tab
3. Open configuration dialog
4. Look for logs starting with **[CONFIG]**
5. Click Save and watch logs appear
6. Click "Save & Close" and watch refresh logs

**Expected Console Output:**
```
[CONFIG] Opening Configuration dialog (closeOnSave=false)
[CONFIG] Configuration loaded successfully {...}
[CONFIG] Saving configuration (closeOnSave=false)
[CONFIG] Payload to save: {...}
[CONFIG] Configuration saved successfully
[CONFIG] Keeping modal open for further edits
```

**Expected Result:** ✅ Console shows [CONFIG] logs for all operations

## Detailed Verification (15 minutes)

### Test 1: SMTP Configuration
**Action:** Fill in SMTP settings
```
Host: smtp.gmail.com
Port: 587
Username: test@gmail.com
Password: (leave empty for test)
```

**Verify:**
1. Click Save
2. Reopen Configuration
3. All fields should be filled with your values
4. Password field should be empty (security feature)

### Test 2: Sender Configuration
**Action:** Fill in sender details
```
From Address: noreply@example.com
From Name: Email System
```

**Verify:**
1. Click Save
2. Reopen Configuration
3. Both values should persist

### Test 3: Facebook Configuration
**Action:** Enable Facebook and fill details
```
Enable Facebook: Toggle ON
Email: fb@example.com
Page ID: 123456789
```

**Verify:**
1. Click Save
2. Reopen Configuration
3. Toggle should be ON
4. Email and Page ID should persist
5. Password and Token should be empty

### Test 4: All Fields Together
**Action:** Modify all fields at once
```
SMTP Host: new.host.com
SMTP Port: 465
Username: newuser@host.com
From Address: sender@host.com
From Name: New Sender
Text Color: black
Template Slots: 7
Facebook: Enabled
Facebook Email: fb@example.com
Facebook Page ID: 987654321
```

**Verify:**
1. Click "Save & Close"
2. Wait for page to refresh
3. Reopen Configuration
4. All values should be saved except passwords

## Troubleshooting

### Issue: "Saved successfully!" but values don't persist
**Diagnosis:**
1. Check browser console for [CONFIG] logs
2. Check server logs for errors
3. Verify `/api/config` endpoint is accessible

**Solution:**
1. Clear browser cache (Ctrl+Shift+Delete)
2. Restart application
3. Try again

### Issue: Page doesn't refresh after "Save & Close"
**Diagnosis:**
1. Open console (F12)
2. Look for [CONFIG] Refreshing page... log

**Solution:**
1. If log appears: Browser may have blocked auto-refresh
2. Manually refresh page (F5)
3. Check if page reloads successfully

### Issue: Modal doesn't close after "Save & Close"
**Diagnosis:**
1. Check console for errors
2. Look for [CONFIG] Closing modal log

**Solution:**
1. Close modal manually with X button
2. Refresh page manually
3. Check server logs for errors

### Issue: Can't see [CONFIG] logs in console
**Solution:**
1. Press F12 to open Developer Tools
2. Click Console tab
3. Make sure you're in the Console (not Elements, Network, etc.)
4. Clear console if needed
5. Perform the action again

## Server-Side Verification

### Check Configuration File
```bash
# View properties file
cat src/main/resources/application.properties

# Or check active profile
grep -r "spring.mail" src/main/resources/application*.properties
```

### Check Encrypted Values
Values with `ENC(...)` prefix are encrypted:
```
spring.mail.password=ENC(HeSrL8QKpJ...)
facebook.password=ENC(...)
facebook.access.token=ENC(...)
```

These are encrypted using Jasypt with:
- Password: `kisoft-secret-key` (from application.properties)
- Algorithm: `PBEWithHMACSHA512AndAES_256`

### Check Application Logs
```
tail -f logs/app-*.log | grep CONFIG
```

Should show:
```
[CONFIG] Opening Configuration dialog...
[CONFIG] Configuration loaded successfully...
[CONFIG] Saving configuration...
[CONFIG] Configuration saved successfully...
```

## Performance Verification

### Test Save Time
1. Open Configuration dialog
2. Click Save
3. Status should show "Saved successfully!" within 1-2 seconds

### Test Page Refresh Time
1. Click "Save & Close"
2. Page should reload within 2-3 seconds total

## Success Criteria Checklist

✅ Configuration modal opens without errors
✅ All form fields load with current values
✅ "Save" button saves values and keeps modal open
✅ "Save & Close" button saves values, closes modal, and refreshes page
✅ All configuration parameters are saved (not just some)
✅ Password fields are empty after save (security)
✅ Console shows [CONFIG] logs
✅ Error messages display on save failure
✅ Values persist after page reload
✅ No JavaScript errors in browser console
✅ Page refresh completes successfully

## Version Information

- **Application:** Web-List-EMailer 0.0.13-SNAPSHOT
- **Java:** 21.0.7
- **Spring Boot:** 4.0.0
- **Browser:** Chrome, Firefox, Edge, Safari (all modern versions)

## Support

If you encounter issues:
1. Check browser console for [CONFIG] logs
2. Check server logs in `/logs/` folder
3. Verify network connectivity
4. Clear browser cache and try again
5. Restart application and try again

