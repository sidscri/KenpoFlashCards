@echo off
setlocal EnableExtensions EnableDelayedExpansion

title Advanced Flashcards WebApp Server - Build Installer

REM ============================================================
REM 3. build_installer_inno_one_click.bat (lives in \packaging\one_click\)
REM
REM IMPORTANT PATH RULES (per your request):
REM   - output stays in    : \packaging\output
REM   - logs stay in       : \packaging\logs
REM   - installer_inno.iss : \packaging\installer_inno.iss
REM
REM Optional:
REM   --nopause
REM ============================================================

set "NO_PAUSE=0"
for %%A in (%*) do if /I "%%~A"=="--nopause" set "NO_PAUSE=1"

REM --- Resolve folders
set "ONECLICK_DIR=%~dp0"
if "%ONECLICK_DIR:~-1%"=="\" set "ONECLICK_DIR=%ONECLICK_DIR:~0,-1%"

set "PACK_DIR=%ONECLICK_DIR%\.."
pushd "%PACK_DIR%" >nul 2>&1
set "PACK_DIR=%CD%"
popd >nul 2>&1

set "PROJ_DIR=%PACK_DIR%\.."
pushd "%PROJ_DIR%" >nul 2>&1
set "PROJ_DIR=%CD%"
popd >nul 2>&1

cd /d "%PACK_DIR%"

echo ============================================================
echo Advanced Flashcards WebApp Server - Build Installer
echo ============================================================

REM --- Ensure packaging\output exists and is a folder (clean it)
if exist "output" (
  if exist "output\" (
    rmdir /s /q "output" >nul 2>&1
  ) else (
    del /f /q "output" >nul 2>&1
  )
)
mkdir "output" >nul 2>&1

REM --- Validate version.json exists
if not exist "%PROJ_DIR%\version.json" (
  echo [ERROR] %PROJ_DIR%\version.json not found.
  goto :FAIL
)

echo [INFO] Reading version/build from %PROJ_DIR%\version.json

REM --- Read version/build from JSON (whitespace-safe)
set "APPVER="
set "APPBUILD="
for /f "usebackq delims=" %%V in (`
  powershell -NoProfile -ExecutionPolicy Bypass -Command ^
    "try { $j = Get-Content -Raw '%PROJ_DIR%\version.json' | ConvertFrom-Json; $j.version } catch { '' }"
`) do set "APPVER=%%V"
for /f "usebackq delims=" %%B in (`
  powershell -NoProfile -ExecutionPolicy Bypass -Command ^
    "try { $j = Get-Content -Raw '%PROJ_DIR%\version.json' | ConvertFrom-Json; $j.build } catch { '' }"
`) do set "APPBUILD=%%B"

if not defined APPVER (
  echo [ERROR] Could not read version from version.json
  goto :FAIL
)
if not defined APPBUILD (
  echo [ERROR] Could not read build from version.json
  goto :FAIL
)

echo [INFO] Version : %APPVER%
echo [INFO] Build   : %APPBUILD%

REM --- Find ISCC (Inno Setup)
set "ISCC="
for %%P in (
  "%ProgramFiles(x86)%\Inno Setup 6\ISCC.exe"
  "%ProgramFiles%\Inno Setup 6\ISCC.exe"
) do (
  if exist "%%~P" set "ISCC=%%~P"
)

if not defined ISCC (
  echo [ERROR] ISCC.exe not found. Is Inno Setup installed?
  goto :FAIL
)

if not exist "installer_inno.iss" (
  echo [ERROR] installer_inno.iss not found in: %PACK_DIR%
  goto :FAIL
)

echo [INFO] Using ISCC: %ISCC%
echo [INFO] Compiling installer_inno.iss ...

REM --- Defines used by installer_inno.iss
set "APPNAME=Advanced Flashcards WebApp Server"
set "APPEXENAME=AdvancedFlashcardsWebAppServer.exe"
set "APPPUBLISHER=Sidscri"
set "APPURL=https://github.com/sidscri-apps"

REM ---------------- LOGGING ----------------
set "LOGDIR=%PACK_DIR%\logs"
if not exist "%LOGDIR%" mkdir "%LOGDIR%" >nul 2>&1
for /f %%i in ('powershell -NoProfile -Command "Get-Date -Format yyyyMMdd_HHmmss"') do set "TS=%%i"
if not defined TS set "TS=%date:~-4%%date:~4,2%%date:~7,2%_%time:~0,2%%time:~3,2%%time:~6,2%"
set "TS=%TS: =0%"
set "LOGFILE=%LOGDIR%\build_installer_inno_%TS%_v%APPVER%.log"
echo [INFO] Log file : %LOGFILE%
REM -----------------------------------------------------

"%ISCC%" ^
  /DMyAppVersion="%APPVER%" ^
  /DMyAppBuild="%APPBUILD%" ^
  /DMyAppName="%APPNAME%" ^
  /DMyAppExeName="%APPEXENAME%" ^
  /DMyAppPublisher="%APPPUBLISHER%" ^
  /DMyAppURL="%APPURL%" ^
  "installer_inno.iss" > "%LOGFILE%" 2>&1

if errorlevel 1 (
  echo [ERROR] Inno Setup build failed.
  goto :FAIL
)

echo.
echo [DONE] Installer created in packaging\output\
echo        Expected filename: AdvancedFlashcardsWebAppServer-v%APPVER%.exe
echo.
if "%NO_PAUSE%"=="0" pause
exit /b 0

:FAIL
echo.
echo [FAIL] See messages above.
echo.
if "%NO_PAUSE%"=="0" pause
exit /b 1
