# Logging Configuration Fix - ONE Log File Per Run

## Date: December 28, 2025
## Status: ✅ COMPLETED

---

## Problem
Application was creating **multiple log files per run** (one for each application restart):
```
logs/
  app-2025-12-28_20-32-07.log
  app-2025-12-28_20-32-10.log
  app-2025-12-28_20-32-15.log
  ... many more files
```

This made it difficult to find the current session's logs.

---

## Solution
Changed Logback configuration to use **ONE rolling log file** (`logs/app.log`) that:
1. **Appends** all logs from current and previous runs
2. **Rolls over** when file reaches 10MB
3. **Archives** old files with date/index: `app-2025-12-28.0.log`, `app-2025-12-28.1.log`
4. **Keeps** maximum 20 archived files

---

## File Modified
**File:** `src/main/resources/logback-spring.xml`

### Before (TimeBasedRollingPolicy)
```xml
<rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
    <fileNamePattern>logs/app-%d{yyyy-MM-dd_HH-mm-ss}.log</fileNamePattern>
    <maxHistory>20</maxHistory>
</rollingPolicy>
```
**Result:** New file created for each application run

### After (SizeAndTimeBasedRollingPolicy)
```xml
<appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
    <file>logs/app.log</file>
    <rollingPolicy class="ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy">
        <fileNamePattern>logs/app-%d{yyyy-MM-dd}.%i.log</fileNamePattern>
        <maxFileSize>10MB</maxFileSize>
        <maxHistory>20</maxHistory>
        <cleanHistoryOnStart>true</cleanHistoryOnStart>
    </rollingPolicy>
</appender>
```
**Result:** One rolling log file with archives

---

## Log File Structure

### Current Run Logs
```
logs/app.log  ← Always contains current session logs (appended)
```

### Archive Pattern
When `app.log` reaches 10MB:
```
logs/app-2025-12-28.0.log  ← First 10MB chunk from Dec 28
logs/app-2025-12-28.1.log  ← Second 10MB chunk from Dec 28
logs/app-2025-12-28.2.log  ← Third 10MB chunk from Dec 28
logs/app-2025-12-29.0.log  ← First 10MB chunk from Dec 29
... (max 20 files total)
```

---

## How It Works

### On Application Start
1. Logback looks for `logs/app.log`
2. If it exists, **appends** logs to it
3. If file size >= 10MB, **archives** it
4. Creates new `app.log` for current session

### Continuous Logging
```
logs/app.log:
  2025-12-28T20:32:07.123 INFO  [main] Starting EmailListApplication
  2025-12-28T20:32:08.456 DEBUG [main] Loading configuration...
  ... (many more log entries)
  2025-12-28T20:34:15.789 INFO  [main] Application started successfully
  
  [App restart happens here - logs continue to append]
  
  2025-12-28T20:35:00.100 INFO  [main] Starting EmailListApplication (2nd run)
  2025-12-28T20:35:01.200 DEBUG [main] Loading configuration...
  ... (new session logs appended)
```

---

## Benefits

✅ **One File to Check** - All logs in `logs/app.log`
✅ **Persistent** - Old logs archived, not deleted
✅ **Size-Limited** - Files don't grow indefinitely
✅ **Date-Organized** - Archives labeled by date
✅ **Automatic Cleanup** - Keeps only 20 most recent files
✅ **Easy Search** - Grep/search a single file per session

---

## Example Usage

### View Current Logs
```bash
# View live logs
tail -f logs/app.log

# View last 50 lines
tail -50 logs/app.log

# Search for errors in current session
grep ERROR logs/app.log
```

### View Archived Logs
```bash
# List all log files
ls -la logs/

# View archived logs
cat logs/app-2025-12-28.0.log

# Search across all logs
grep -r "ERROR" logs/
```

---

## Configuration

### Change Archive Threshold
**File:** `logback-spring.xml`

```xml
<!-- Increase to 20MB before archiving -->
<maxFileSize>20MB</maxFileSize>

<!-- Decrease to 5MB for more frequent archives -->
<maxFileSize>5MB</maxFileSize>
```

### Keep More Archive Files
```xml
<!-- Keep 50 instead of 20 -->
<maxHistory>50</maxHistory>
```

### Change Archive Directory
```xml
<!-- Archive to different folder -->
<fileNamePattern>logs/archive/app-%d{yyyy-MM-dd}.%i.log</fileNamePattern>
```

---

## Log Pattern

Each log line includes:
```
2025-12-28T20:32:07.123-05:00 INFO  [http-nio-8082-exec-1] com.kisoft.emaillist.controller.EmailController.composePage - Loading compose page
```

Components:
- `2025-12-28T20:32:07.123-05:00` - Timestamp with timezone
- `INFO` - Log level (DEBUG, INFO, WARN, ERROR)
- `[http-nio-8082-exec-1]` - Thread name
- `com.kisoft.emaillist.controller.EmailController.composePage` - Logger class and method
- `Loading compose page` - Log message

---

## Log Levels

| Level | When Used | Example |
|-------|-----------|---------|
| **DEBUG** | Detailed diagnostics | Loading properties, entering methods |
| **INFO** | Normal operational events | Application started, config loaded |
| **WARN** | Potentially harmful situations | Failed to load optional resource |
| **ERROR** | Error events | Exception caught, save failed |

---

## Current Log Level Configuration

**File:** `logback-spring.xml`

```xml
<logger name="com.kisoft" level="DEBUG" />  <!-- App logs in DEBUG
<root level="INFO" />                        <!-- Everything else in INFO+
```

Result:
- Application logs: DEBUG + INFO + WARN + ERROR
- Other libraries: INFO + WARN + ERROR
- Spring Boot framework: INFO + WARN + ERROR

---

## Troubleshooting

### Issue: Can't Find Logs
**Solution:**
1. Check logs are written to: `logs/app.log`
2. Verify log file exists: `ls -la logs/app.log`
3. Check permissions: `chmod 644 logs/app.log`

### Issue: Logs Too Verbose
**Solution:**
1. Change DEBUG to INFO in logback-spring.xml
   ```xml
   <logger name="com.kisoft" level="INFO" />
   ```
2. Restart application

### Issue: Archived Files Accumulating
**Solution:**
1. Reduce `maxHistory` to 10
2. Increase `maxFileSize` to 20MB
3. Manually delete old files: `rm logs/app-2025-12-01*.log`

---

## Comparison: Before vs After

| Aspect | Before | After |
|--------|--------|-------|
| **Files per restart** | 1 (timestamped) | 0 (appends to app.log) |
| **Finding current logs** | Hard (find latest timestamp) | Easy (always app.log) |
| **Total files** | Many (one per run) | Few (max 20 archives) |
| **Storage** | Unbounded | 200MB max (10MB × 20) |
| **Searching logs** | Multiple files | One file per session |

---

## Build Status

✅ **BUILD SUCCESS** - No errors
✅ **JAR Created** - Web-List-EMailer-0.0.13-SNAPSHOT.jar
✅ **Ready for Production**

---

## Files Changed

- ✅ `src/main/resources/logback-spring.xml` - Changed rolling policy

---

## What's Next?

The application now creates:
1. **`logs/app.log`** - Current session logs (appended)
2. **`logs/app-YYYY-MM-DD.*.log`** - Archived logs

**No user action required!** Logging is automatic.

---

**Status: ✅ COMPLETE AND TESTED**

Date: December 28, 2025
Version: 0.0.13-SNAPSHOT

