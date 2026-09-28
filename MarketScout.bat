@echo off
title MarketScout Desktop
setlocal

cd /d "%~dp0"

echo ========================================================
echo   Starting MarketScout Desktop Application Terminal...
echo ========================================================

REM Check if packaged JAR exists
if not exist "target\marketscout-0.0.1-SNAPSHOT.jar" (
    echo Building MarketScout JAR package...
    call .\mvnw.cmd package -DskipTests
    if errorlevel 1 (
        echo [ERROR] Build failed.
        pause
        exit /b 1
    )
)

start "" javaw -jar "target\marketscout-0.0.1-SNAPSHOT.jar"

echo MarketScout Desktop window is opening.
echo You can close this window.
powershell -NoProfile -Command "Start-Sleep -Seconds 2"
exit /b 0
