@echo off
setlocal

set SCRIPT_DIR=%~dp0

powershell -NoProfile -ExecutionPolicy Bypass ^
  -File "%SCRIPT_DIR%sync_latest_webserver.ps1" ^
  -BuildExe -BuildInstaller

echo.
echo Done. Check:
echo   KenpoFlashcardsWebServer_Packaged_in_exe_msi\dist\...
echo   KenpoFlashcardsWebServer_Packaged_in_exe_msi\packaging\output\...
pause
