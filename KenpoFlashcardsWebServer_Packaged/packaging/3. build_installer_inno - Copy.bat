@echo off
setlocal EnableExtensions

title Advanced Flashcards WebApp Server - Build Installer

REM ============================================================
REM Build installer with Inno Setup
REM - Reads version/build from ..\version.json
REM - Passes required defines to installer_inno.iss so Windows
REM   "Apps & features" version updates correctly.
REM ============================================================

cd /d "%~dp0"

echo ============================================================
echo Advanced Flashcards WebApp Server - Build Installer
echo ============================================================

REM --- Ensure packaging\output exists and is a folder (avoid I/O error 183)
if exist "output" (
  if not exist "output\" (
    del /f /q "output"
  ) else (
    rmdir /s /q "output"
  )
)
mkdir "output" 2>nul

REM --- Validate version.json exists
if not exist "..\version.json" (
  echo [ERROR] ..\version.json not found.
  goto :FAIL
)

echo [INFO] Reading version/build from ..\version.json

REM --- Read version/build (robust and whitespace-safe)
for /f "usebackq delims=" %%V in (`powershell -NoProfile -ExecutionPolicy Bypass -Command "$j=Get-Content -Raw '..\version.json' ^| ConvertFrom-Json; if($j.version){[string]$j.version}else{''}"`) do set "APPVER=%%V"
for /f "usebackq delims=" %%B in (`powershell -NoProfile -ExecutionPolicy Bypass -Command "$j=Get-Content -Raw '..\version.json' ^| ConvertFrom-Json; if($j.build){[string]$j.build}else{'11'}"`) do set "APPBUILD=%%B"

if "%APPVER%"=="" (
  echo [ERROR] Could not read .version from ..\version.json
  echo         Expected a key named: version
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

REM --- Define values used by installer_inno.iss
set "APPNAME=Advanced Flashcards WebApp Server"
set "APPEXENAME=AdvancedFlashcardsWebAppServer.exe"
set "APPPUBLISHER=Sidscri"
set "APPURL=https://github.com/sidscri-apps"

REM NOTE:
REM - AppVersion drives the "Version" shown in Installed Apps (uninstall entry)
REM - Your installer_inno.iss must use {#MyAppVersion} for AppVersion/VersionInfoVersion to fully reflect this
"%ISCC%" /DMyAppVersion="%APPVER%" /DMyAppBuild="%APPBUILD%" /DMyAppName="%APPNAME%" /DMyAppExeName="%APPEXENAME%" /DMyAppPublisher="%APPPUBLISHER%" /DMyAppURL="%APPURL%" "installer_inno.iss"

if errorlevel 1 (
  echo [ERROR] Inno Setup build failed.
  goto :FAIL
)

echo [DONE] Installer created in packaging\output\
pause
exit /b 0

:FAIL
echo.
echo [FAIL] See messages above.
pause
exit /b 1
