@echo off
setlocal

REM ------------------------------------------------------------
REM Kenpo Sync (WebServer -> Packaged)
REM DRY-RUN first, then APPLY.
REM ------------------------------------------------------------

set "SRC=C:\Users\Sidscri\Documents\GitHub\sidscri-apps\KenpoFlashcardsWebServer"
set "TGT=C:\Users\Sidscri\Documents\GitHub\sidscri-apps\KenpoFlashcardsWebServer_Packaged"

echo.
echo ===== DRY-RUN (no changes) =====
py "%~dp0sync_webserver_to_packaged.py" --source "%SRC%" --target "%TGT%"
if errorlevel 1 goto :FAIL

echo.
echo ===== APPLY (copies files + makes backups) =====
py "%~dp0sync_webserver_to_packaged.py" --source "%SRC%" --target "%TGT%" --apply
if errorlevel 1 goto :FAIL

echo.
echo Done.
pause
exit /b 0

:FAIL
echo.
echo Sync failed.
pause
exit /b 1
