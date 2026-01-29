@echo off
setlocal EnableExtensions

title Advanced Flashcards WebApp Server - Build Installer

REM ============================================================
REM Build installer with Inno Setup
REM - Reads version/build from ..\version.json (robust)
REM - Passes defines to installer_inno.iss
REM - Keeps window open on success/fail (pause)
REM ============================================================

cd /d "%~dp0"

echo ============================================================
echo Advanced Flashcards WebApp Server - Build Installer
echo ============================================================

REM --- Ensure packaging\output exists and is a folder
if exist "output" (
  if not exist "output\" (
    del /f /q "output" >nul 2>&1
  ) else (
    rmdir /s /q "output" >nul 2>&1
  )
)
mkdir "output" >nul 2>&1

REM --- Validate version.json exists
if not exist "..\version.json" (
  echo [ERROR] ..\version.json not found.
  goto :FAIL
)

echo [INFO] Reading version/build from ..\version.json

REM --- Read version/build from JSON (whitespace-safe)
for /f "usebackq delims=" %%V in (`
  powershell -NoProfile -ExecutionPolicy Bypass -Command ^
    "try { $j = Get-Content -Raw '..\version.json' | ConvertFrom-Json; if($j.version){ [string]$j.version } } catch { '' }"
`) do set "APPVER=%%V"

for /f "usebackq delims=" %%B in (`
  powershell -NoProfile -ExecutionPolicy Bypass -Command ^
    "try { $j = Get-Content -Raw '..\version.json' | ConvertFrom-Json; if($j.build){ [string]$j.build } else { '11' } } catch { '11' }"
`) do set "APPBUILD=%%B"

if "%APPVER%"=="" (
  echo [ERROR] Could not read .version from ..\version.json
  echo         Make sure it contains:  "version": "3.1.0.5"
  goto :FAIL
)

echo [INFO] AppVersion: %APPVER%
echo [INFO] Build     : %APPBUILD%

REM --- Validate EXE exists
if not exist "..\dist\AdvancedFlashcardsWebAppServer\AdvancedFlashcardsWebAppServer.exe" (
  echo [ERROR] Build the EXE first: packaging\2. build_exe.bat
  goto :FAIL
)

REM --- Locate ISCC.exe
set "ISCC_DEFAULT=C:\Program Files (x86)\Inno Setup 6\ISCC.exe"
if exist "%ISCC_DEFAULT%" (
  set "ISCC=%ISCC_DEFAULT%"
) else (
  set "ISCC=ISCC.exe"
)

echo [INFO] Using ISCC: %ISCC%
echo [INFO] Compiling installer_inno.iss ...

REM --- Defines used by installer_inno.iss
set "APPNAME=Advanced Flashcards WebApp Server"
set "APPEXENAME=AdvancedFlashcardsWebAppServer.exe"
set "APPPUBLISHER=Sidscri"
set "APPURL=https://github.com/sidscri-apps"

REM ---------------- LOGGING (requested) ----------------
set "LOGDIR=%~dp0logs"
if not exist "%LOGDIR%" mkdir "%LOGDIR%" >nul 2>&1
set "DATESTAMP=%DATE%"
set "DATESTAMP=%DATESTAMP:/=-%"
set "DATESTAMP=%DATESTAMP:\=-%"
set "DATESTAMP=%DATESTAMP::=-%"
set "DATESTAMP=%DATESTAMP:,=%"
set "DATESTAMP=%DATESTAMP: =_%"
set "LOGFILE=%LOGDIR%\build_installer_inno_%DATESTAMP%_%APPVER%.log"
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
pause
exit /b 0

:FAIL
echo.
echo [FAIL] See messages above.
echo.
pause
exit /b 1
