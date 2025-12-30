# Browser Image Error Fix - Quick Solution

## Problem
You're seeing browser console errors like:
- `GET data:image/jpeg;base64,... net::ERR_INVALID_URL`
- `GET http://localhost:8082/data:image/jpeg;base64,... 400 (Bad Request)`
- SVG parsing errors with `\\20\\` in attribute values

**Root Cause**: Image `src` attributes have extra escape characters (leading backslashes) from HTML editor escaping.

## Solution - Repair Your Templates

### Step 1: Verify Current State
Open your browser developer console and run:
```javascript
// Check template 2 for image issues
fetch('http://localhost:8082/api/template/verify/2')
  .then(r => r.json())
  .then(d => console.log(JSON.stringify(d, null, 2)));
```

Expected output should show image src attributes with issues.

### Step 2: Repair Template 2
**Method A: Browser Console**
```javascript
fetch('http://localhost:8082/api/template/repair/2', {method: 'POST'})
  .then(r => r.json())
  .then(d => console.log('Repair result:', JSON.stringify(d, null, 2)));
```

**Method B: Using curl**
```bash
curl -X POST http://localhost:8082/api/template/repair/2
```

**Method C: Using wget**
```bash
wget --method=POST http://localhost:8082/api/template/repair/2 -O -
```

### Step 3: Verify the Fix
Run this in browser console:
```javascript
fetch('http://localhost:8082/api/template/verify/2')
  .then(r => r.json())
  .then(d => console.log('After repair:', JSON.stringify(d.summary, null, 2)));
```

Expected: `missingLocalFiles: 0` and all `type: "data-url"`

### Step 4: Clear Cache and Test
1. Press `Ctrl+Shift+R` (hard refresh) to clear browser cache
2. Load Template 2 in your app
3. Open DevTools (F12) → Console tab
4. Look for the previous 404/400 errors - **they should be gone!**
5. Images should now render correctly

## What Was Fixed

Your template files had image src attributes like:
```html
<img src="\data:image/jpeg;base64,..."/>
```

After repair, they become:
```html
<img src="data:image/jpeg;base64,..."/>
```

The leading backslash caused browsers to:
1. Treat `data:` as a URL path
2. Try to fetch `http://localhost:8082/data:image/...` 
3. Fail with 404/400 errors

## Technical Details

The fix is implemented in `TemplateService.java`:
- **normalizeHtmlContent()** - Cleans HTML on template load and save
- **cleanSrc()** - Removes leading backslashes from img src attributes
- **repairTemplate()** endpoint - Forces re-save with normalization

The normalization process:
1. Parses HTML using jsoup
2. Finds all `<img>` tags
3. Strips leading backslashes from src attributes
4. Removes excessive whitespace in data URLs
5. Saves the cleaned HTML back

## Prevention

Going forward, the app automatically normalizes templates:
- On **save**: `normalizeHtmlContent()` is called
- On **load**: HTML is cleaned before returning to frontend
- Manual **repair**: `/api/template/repair/{slot}` endpoint available

## Files Modified
- `src/main/java/com/kisoft/emaillist/service/TemplateService.java` - Added normalization logic
- `src/main/java/com/kisoft/emaillist/controller/TemplateController.java` - Added `/repair/{slot}` endpoint

## Testing Checklist

- [ ] Run repair on Template 2
- [ ] Verify fix successful
- [ ] Hard refresh browser (Ctrl+Shift+R)
- [ ] Load template - no 404/400 errors in console
- [ ] Images render correctly
- [ ] Can see proper base64 data URLs in network inspector

---

**Status**: ✅ Fix is ready to deploy
**Estimated Time**: < 1 minute to repair all templates

