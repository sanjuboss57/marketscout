@echo off
title MarketScout Desktop
setlocal

cd /d "%~dp0"

echo ========================================================
echo       Starting MarketScout Application...
echo ========================================================

REM 1. Check if JAR exists, build if missing
if not exist "target\marketscout-0.0.1-SNAPSHOT.jar" (
    echo Building MarketScout package...
    call .\mvnw.cmd package -DskipTests
)

REM 2. Check if already running on port 18080
netstat -ano | findstr 18080 | findstr LISTENING >nul
if errorlevel 1 (
    echo Starting background server engine...
    start "" javaw -jar "target\marketscout-0.0.1-SNAPSHOT.jar"
)

REM 3. Wait for server to become ready
echo Initializing MarketScout desktop terminal...
powershell -NoProfile -Command "for ($i = 0; $i -lt 30; $i++) { try { $r = Invoke-WebRequest -Uri 'http://localhost:18080/api/app/info' -UseBasicParsing -TimeoutSec 1; if ($r.StatusCode -eq 200) { exit 0 } } catch { Start-Sleep -Milliseconds 500 } } exit 1"

REM 4. Bring up the application window immediately on screen!
echo Opening application window...
if exist "C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe" (
    start "" "C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe" --app=http://localhost:18080
) else if exist "C:\Program Files\Microsoft\Edge\Application\msedge.exe" (
    start "" "C:\Program Files\Microsoft\Edge\Application\msedge.exe" --app=http://localhost:18080
) else (
    start http://localhost:18080
)

powershell -NoProfile -Command "Start-Sleep -Seconds 1"
exit /b 0
