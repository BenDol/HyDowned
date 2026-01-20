@echo off
REM Start Hytale server with debug and auto-attach debugger

echo ================================================================================
echo Starting Dev Server with Auto-Debug
echo ================================================================================
echo.

REM Start the Gradle task in the background
start /B cmd /c "gradlew.bat startDevServerDebug > server.log 2>&1"

echo Waiting for debug port 5005 to be ready...
echo.

:WAIT_LOOP
timeout /t 1 /nobreak >nul
netstat -ano | findstr ":5005" | findstr "LISTENING" >nul 2>&1
if errorlevel 1 (
    echo Still waiting...
    goto WAIT_LOOP
)

echo.
echo ✓ Debug port 5005 is ready!
echo.
echo ================================================================================
echo ATTACHING DEBUGGER AUTOMATICALLY...
echo ================================================================================
echo.

REM Find IntelliJ IDEA installation
set IDEA_PATH=
for %%i in (
    "C:\Program Files\JetBrains\IntelliJ IDEA 2025.1\bin\idea64.exe"
    "C:\Program Files\JetBrains\IntelliJ IDEA 2024.3\bin\idea64.exe"
    "C:\Program Files\JetBrains\IntelliJ IDEA Community Edition 2025.1\bin\idea64.exe"
) do (
    if exist %%i (
        set IDEA_PATH=%%i
        goto FOUND_IDEA
    )
)

:FOUND_IDEA
if not defined IDEA_PATH (
    echo WARNING: IntelliJ IDEA not found in standard locations
    echo.
    echo MANUAL STEPS:
    echo 1. In IntelliJ: Select "Hytale Server Debug" from dropdown
    echo 2. Click Debug button or press Shift+F9
    echo.
    pause
    exit /b 1
)

REM Use IntelliJ command line to attach debugger
echo Found IntelliJ at: %IDEA_PATH%
echo Attaching debugger to localhost:5005...
echo.

REM The idea.bat command with nosplash and attach parameters
"%IDEA_PATH%" --attach localhost:5005

timeout /t 2 /nobreak >nul

echo.
echo ================================================================================
echo ✅ READY FOR HOT-SWAP DEVELOPMENT!
echo ================================================================================
echo.
echo Server is running with debugger attached.
echo.
echo 📝 HOT-SWAP WORKFLOW:
echo    1. Edit code (method bodies, logic, messages)
echo    2. Press Ctrl+Shift+F9 (Reload Changed Classes)
echo    3. Changes apply in 1-2 seconds!
echo.
echo Server logs: server.log
echo ================================================================================
echo.
echo Press any key to stop the server...
pause >nul

REM Kill the server process
taskkill /F /FI "WINDOWTITLE eq *java*" /FI "MEMUSAGE gt 100000" >nul 2>&1
echo Server stopped.
