@echo off
setlocal ENABLEEXTENSIONS

REM Resolve project directory and move there
set "PROJECT_DIR=%~dp0"
pushd "%PROJECT_DIR%"
if errorlevel 1 goto :fail_dir

set "JAR_PATH=target\Web-List-EMailer-0.0.1-SNAPSHOT.jar"
set "PORT=8082"
set "JAVA_CMD=java"

REM Prefer JAVA_HOME through a helper so special chars are handled safely
if defined JAVA_HOME call :use_java_home

if not exist "%JAR_PATH%" (
    echo [INFO] Fat JAR not found. Building with Maven tests skipped...
    call mvnw.cmd clean package -DskipTests
    if errorlevel 1 goto :fail
)

echo [INFO] Using JAVA_CMD=%JAVA_CMD%
echo [INFO] Starting email list application on port %PORT% ...
echo [INFO] Opening browser in 4 seconds...
echo.

REM Open browser after delay in background
start /B cmd /c "timeout /t 4 /nobreak >nul && start http://localhost:%PORT%/"

REM Run Java in foreground (same window) so only one CMD window exists
"%JAVA_CMD%" -jar "%JAR_PATH%" --server.port=%PORT%

popd
goto :eof

:fail
echo [ERROR] Startup failed. Cleaning up...
popd
exit /b 1

:fail_dir
echo [ERROR] Unable to change to project directory.
exit /b 1

:use_java_home
set JH_BIN=%JAVA_HOME%\bin\java.exe
if exist "%JH_BIN%" set JAVA_CMD=%JH_BIN%
goto :eof
