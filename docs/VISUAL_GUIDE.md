# Image Error Fix - Visual Flowchart

## Problem Diagram

```
Browser loads email template
         ↓
HTML contains: <img src="\data:image/jpeg;base64,..." />
         ↓
Browser sees leading backslash (\)
         ↓
Interprets as file path, not data URL
         ↓
Tries to fetch: GET http://localhost:8082/data:image/jpeg;base64,...
         ↓
Server returns: 404 Not Found OR 400 Bad Request
         ↓
❌ Image fails to load - console shows error
```

## Solution Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                   APPLICATION FLOW                          │
└─────────────────────────────────────────────────────────────┘

1. SAVE TEMPLATE
   ┌─────────────┐
   │ User Editor │
   └──────┬──────┘
          │ POST /api/template/save
          │ {htmlContent: "..."}
          ↓
   ┌──────────────────────┐
   │ TemplateController   │
   │ saveTemplate()       │
   └──────┬───────────────┘
          │
          ↓
   ┌──────────────────────────────┐
   │ TemplateService              │
   │ saveTemplate()               │
   │   ├─ normalizeHtmlContent()  │◄── NEW: Cleans HTML
   │   └─ Write to JSON file      │
   └──────┬───────────────────────┘
          │
          ↓
   ┌──────────────────────┐
   │ configs/templates/   │
   │ template-{slot}.json │
   └──────────────────────┘
         (cleaned)


2. LOAD TEMPLATE
   ┌─────────────┐
   │ User Editor │
   └──────┬──────┘
          │ POST /api/template/load
          │ {slot: 2}
          ↓
   ┌──────────────────────┐
   │ TemplateController   │
   │ loadTemplate()       │
   └──────┬───────────────┘
          │
          ↓
   ┌──────────────────────────────┐
   │ TemplateService              │
   │ loadTemplate()               │
   │   ├─ Read from JSON file     │
   │   └─ normalizeHtmlContent()  │◄── NEW: Cleans HTML
   └──────┬───────────────────────┘
          │
          ↓
   ┌─────────────────────────────────┐
   │ Return to Browser               │
   │ {htmlContent: "...cleaned..."}  │
   └──────┬──────────────────────────┘
          │
          ↓
   ┌──────────────────────┐
   │ Browser renders HTML │
   │ ✅ Images display    │
   └──────────────────────┘


3. REPAIR EXISTING TEMPLATE
   ┌──────────────────────┐
   │ Admin/User           │
   │ POST /repair/{slot}  │◄── NEW Endpoint
   └──────┬───────────────┘
          │
          ↓
   ┌──────────────────────────────┐
   │ TemplateController           │
   │ repairTemplate()             │
   │   ├─ Load existing template  │
   │   ├─ Normalize HTML          │
   │   └─ Save normalized copy    │
   └──────┬───────────────────────┘
          │
          ↓
   ┌──────────────────────┐
   │ Return: {            │
   │   status: "ok",      │
   │   message: "Repaired"│
   │ }                    │
   └──────────────────────┘
```

## Normalization Process

```
INPUT HTML
  │
  ├─ Contains: <img src="\data:image/jpeg;base64,abc123" />
  │
  ↓
Parse with jsoup
  │
  ↓
Find all <img> tags
  │
  ├─ For each <img>:
  │   ├─ Get src attribute value
  │   │   Value: "\data:image/jpeg;base64,abc123"
  │   │
  │   ├─ Call cleanSrc()
  │   │   ├─ Trim whitespace
  │   │   ├─ Strip leading backslashes (\)
  │   │   │   Value: "data:image/jpeg;base64,abc123"
  │   │   │
  │   │   ├─ If data URL:
  │   │   │   └─ Remove \r\n\t from content
  │   │   │
  │   │   └─ Return cleaned value
  │   │
  │   └─ Update src attribute
  │       <img src="data:image/jpeg;base64,abc123" />
  │
  ↓
Return normalized HTML
  │
  └─ ✅ <img src="data:image/jpeg;base64,abc123" />
```

## Quick Fix Timeline

```
⏱️  Time  │  Action                          │  Expected
──────────┼──────────────────────────────────┼─────────────
   0:00   │ Open browser console (F12)       │ Console open
   0:30   │ Paste repair command             │ Command ready
   1:00   │ Run: fetch(...repair/2...)       │ ✅ Status: ok
   1:30   │ Hard refresh (Ctrl+Shift+R)      │ Page reloads
   2:00   │ Load Template 2                  │ 
   2:15   │ Check console for errors         │ ❌ NO errors!
   2:30   │ Verify images display            │ ✅ Visible!
──────────┴──────────────────────────────────┴─────────────
   TOTAL: 2.5 minutes
```

## Browser Console Commands

```
COMMAND 1: Check Status
───────────────────────
fetch('http://localhost:8082/api/template/verify/2')
  .then(r => r.json())
  .then(d => console.table(d.summary));

EXPECTED OUTPUT:
┌─────────────────────┬───────┐
│ (index)             │ Value │
├─────────────────────┼───────┤
│ imageCount          │ 2     │
│ dataUrlCount        │ 2     │
│ missingLocalFiles   │ 0     │
│ totalContentLength  │ 45892 │
└─────────────────────┴───────┘
✅ All good!


COMMAND 2: Run Repair
──────────────────────
fetch('http://localhost:8082/api/template/repair/2', {method: 'POST'})
  .then(r => r.json())
  .then(d => console.log(JSON.stringify(d, null, 2)));

EXPECTED OUTPUT:
{
  "status": "ok",
  "filePath": "configs/templates/template-2.json",
  "slot": 2,
  "subject": "Namaste 🙏",
  "contentLength": 45892,
  "message": "Template saved successfully",
  "timestamp": "2025-12-29T16:00:00Z"
}
✅ Repaired!


COMMAND 3: Batch Repair (All Templates)
──────────────────────────────────────────
(async () => {
  for (let i = 1; i <= 10; i++) {
    const r = await fetch(`http://localhost:8082/api/template/repair/${i}`, {method: 'POST'});
    const d = await r.json();
    console.log(`✅ Template ${i}: ${d.status}`);
  }
})();

EXPECTED OUTPUT:
✅ Template 1: ok
✅ Template 2: ok
✅ Template 3: ok
... (all 10 templates)
✅ All templates repaired!
```

## Before & After Comparison

```
BEFORE (BROKEN)
───────────────
HTML in JSON file:
  <img src="\data:image/jpeg;base64,/9j/4AAQ..." />

Browser console:
  ❌ GET data:image/jpeg;base64,/9j/4AAQ... ERR_INVALID_URL
  ❌ GET http://localhost:8082/data:image/jpeg... 404

Browser display:
  ❌ [broken image icon]


AFTER (FIXED)
─────────────
HTML in JSON file:
  <img src="data:image/jpeg;base64,/9j/4AAQ..." />

Browser console:
  ✅ No errors!

Browser display:
  ✅ [image displays correctly]
```

## Error Resolution Decision Tree

```
                    Browser shows 404/400 errors?
                              │
                    ┌─────────┴─────────┐
                    │                   │
                   YES                  NO
                    │                   │
                    ↓                   ↓
            Run repair endpoint   → Check other issues
                    │
                    ↓
            curl -X POST .../repair/2
                    │
                    ↓
            ┌─────────────────────┐
            │ Repair successful?  │
            └──────┬──────┬───────┘
                  YES     NO
                   │       │
                   ↓       ↓
              ✅ Done   Check logs
                          │
                          ↓
                  Enable DEBUG mode
                  logging.level...=DEBUG
                          │
                          ↓
                   Restart application
                          │
                          ↓
                    Try repair again
                          │
                          ↓
                  Contact support if still failing
```

## System Components

```
┌────────────────────────────────────────────────────────────────┐
│                     WEB APPLICATION                            │
│                                                                │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │ Frontend (HTML/JavaScript)                               │ │
│  │  - Template Editor                                       │ │
│  │  - Save/Load/Repair buttons                              │ │
│  │  - Browser console integration                           │ │
│  └──────────────────────────────────────────────────────────┘ │
│                          ↕                                      │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │ TemplateController                                       │ │
│  │  - /api/template/save        POST                        │ │
│  │  - /api/template/load        POST                        │ │
│  │  - /api/template/repair/{id} POST  ← NEW!              │ │
│  │  - /api/template/verify/{id} GET   ← NEW!              │ │
│  └──────────────────────────────────────────────────────────┘ │
│                          ↕                                      │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │ TemplateService                                          │ │
│  │  - saveTemplate()                                        │ │
│  │  - loadTemplate()                                        │ │
│  │  - normalizeHtmlContent()    ← NEW!                     │ │
│  │  - cleanSrc()                ← NEW!                     │ │
│  │  - verifyTemplateImages()                               │ │
│  └──────────────────────────────────────────────────────────┘ │
│                          ↕                                      │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │ File System                                              │ │
│  │  - configs/templates/template-1.json                     │ │
│  │  - configs/templates/template-2.json  ← Normalized      │ │
│  │  - ...                                                   │ │
│  └──────────────────────────────────────────────────────────┘ │
│                                                                │
└────────────────────────────────────────────────────────────────┘
```

## Implementation Details

```
METHOD: normalizeHtmlContent(String htmlContent)
─────────────────────────────────────────────────

Input:  <img src="\data:image/jpeg;base64,abc..." />
         ↓
1. Parse HTML using jsoup
   Document = <html><body>...</body></html>
         ↓
2. Select all img elements
   Elements = [<img>, <img>, ...]
         ↓
3. For each img element:
   {
     String raw = img.attr("src");
     // raw = "\data:image/jpeg;base64,abc..."
     
     String cleaned = cleanSrc(raw);
     // cleaned = "data:image/jpeg;base64,abc..."
     
     img.attr("src", cleaned);
     // Updated!
   }
         ↓
4. Return body HTML
   Output: <img src="data:image/jpeg;base64,abc..." />


METHOD: cleanSrc(String raw)
─────────────────────────────

Input: "\data:image/jpeg;base64,abc123"
         ↓
1. Trim whitespace
   "\\data:image/jpeg;base64,abc123"
         ↓
2. Strip leading backslashes
   while (s.startsWith("\\")) {
     s = s.substring(1);
   }
   "data:image/jpeg;base64,abc123"
         ↓
3. If data URL, remove newlines/tabs
   s.replaceAll("[\\r\\n\\t]", "");
         ↓
Output: "data:image/jpeg;base64,abc123" ✅
```

---

## Legend

```
✅ = Success / Fixed / Working
❌ = Error / Broken / Not Working
⏱️  = Time / Duration
→  = Process flow / Next step
│  = Connection / Continuation
↓  = Downward flow
↕  = Bidirectional communication
```

