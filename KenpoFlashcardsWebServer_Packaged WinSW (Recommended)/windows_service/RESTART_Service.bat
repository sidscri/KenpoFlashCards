@echo off
setlocal

set SCRIPT_DIR=%~dp0
if not exist "%SCRIPT_DIR%\nssm.exe" (
  echo [ERROR] nssm.exe not found in %SCRIPT_DIR%
  exit /b 1
)

set SVC=KenpoFlashcardsWeb
"%SCRIPT_DIR%\nssm.exe" restart %SVC%
echo [DONE] Restarted %SVC%
