@echo off
setlocal

set "MODE=%~1"
if "%MODE%"=="" set "MODE=demo"

if /I "%MODE%"=="demo" (
    powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\run-demo.ps1"
    exit /b %ERRORLEVEL%
)

if /I "%MODE%"=="dev" (
    powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\run-frontend.ps1"
    exit /b %ERRORLEVEL%
)

if /I "%MODE%"=="frontend" (
    powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\run-frontend.ps1"
    exit /b %ERRORLEVEL%
)

if /I "%MODE%"=="help" goto usage_ok
if /I "%MODE%"=="-h" goto usage_ok
if /I "%MODE%"=="--help" goto usage_ok

echo Unknown mode: %MODE%
echo.
goto usage_error

:usage_ok
echo Usage:
echo   .\run.cmd          Start React demo mode
echo   .\run.cmd demo     Start React demo mode
echo   .\run.cmd dev      Start frontend dev mode for IDEA backend
exit /b 0

:usage_error
echo Usage:
echo   .\run.cmd          Start React demo mode
echo   .\run.cmd demo     Start React demo mode
echo   .\run.cmd dev      Start frontend dev mode for IDEA backend
exit /b 1
