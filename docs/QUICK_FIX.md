# Quick Fix - Image Errors in Browser Console

## What's Wrong?

You're seeing these errors in your browser console:
- `GET data:image/jpeg;base64,... net::ERR_INVALID_URL`
- `GET http://localhost:8082/data:image/... 404/400`
- SVG errors with `\\20\\` characters

**Why?** Template images have extra backslashes in their `src` attributes.

## Quick Fix (2 minutes)

### Step 1: Open Browser Console
Press `F12` to open Developer Tools, then click **Console** tab.

### Step 2: Run This Command
Paste this into the console and press Enter:
```javascript
fetch('http://localhost:8082/api/template/repair/2', {method: 'POST'})
  .then(r => r.json())
  .then(d => alert(d.status === 'ok' ? '✅ Fixed!' : '❌ ' + d.message));
```

### Step 3: Refresh
Press `Ctrl+Shift+R` (hard refresh) to clear cache.

### Step 4: Verify
- Load Template 2 again
- Check console (F12) - errors should be **gone**
- Images should display correctly

## If That Didn't Work

### Check Status First
```javascript
fetch('http://localhost:8082/api/template/verify/2')
  .then(r => r.json())
  .then(d => console.log(d.summary));
```

Look for:
- `imageCount`: Should be > 0
- `dataUrlCount`: Should equal `imageCount`
- `missingLocalFiles`: Should be 0

### Try Again with Details
```javascript
fetch('http://localhost:8082/api/template/repair/2', {method: 'POST'})
  .then(r => r.json())
  .then(d => console.log(JSON.stringify(d, null, 2)));
```

## What Was Fixed

Before:
```html
<img src="\data:image/jpeg;base64,/9j/4AA..." />
```

After:
```html
<img src="data:image/jpeg;base64,/9j/4AA..." />
```

The leading backslash told the browser to fetch a file instead of using the embedded image data.

## To Fix All Templates at Once

Repeat the repair for templates 1-10:
```javascript
async function repairAll() {
  for (let i = 1; i <= 10; i++) {
    const r = await fetch(`http://localhost:8082/api/template/repair/${i}`, {method: 'POST'});
    const d = await r.json();
    console.log(`Template ${i}: ${d.status}`);
  }
}
repairAll();
```

---

For more details, see:
- `BROWSER_ERROR_FIX.md` - Complete guide with multiple methods
- `IMAGE_SRC_FIX_SUMMARY.md` - Technical deep dive

