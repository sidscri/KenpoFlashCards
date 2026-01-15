@echo off
setlocal

REM Run as Administrator
set SCRIPT_DIR=%~dp0
if not exist "%SCRIPT_DIR%\nssm.exe" (
  echo [ERROR] nssm.exe not found in %SCRIPT_DIR%
  exit /b 1
)

set SVC=KenpoFlashcardsWeb

echo [INFO] Stopping service...
"%SCRIPT_DIR%\nssm.exe" stop %SVC%

echo [INFO] Removing service...
"%SCRIPT_DIR%\nssm.exe" remove %SVC% confirm

echo [DONE] Removed %SVC%
pause
