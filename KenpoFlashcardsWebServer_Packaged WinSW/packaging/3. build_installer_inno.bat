@echo off
setlocal EnableExtensions

title Advanced Flashcards WebApp Server - Build Installer
cd /d "%~dp0"

echo ============================================================
echo Advanced Flashcards WebApp Server - Build Installer
echo ============================================================

REM --- Ensure packaging\output exists and is a folder
if exist "output" (
  if not exist "output\" (
    del /f /q "output"
  ) else (
    rmdir /s /q "output"
  )
)
mkdir "output" 2>nul

if not exist "..\version.json" (
  echo [ERROR] ..\version.json not found.
  goto :FAIL
)

if not exist "..\dist\AdvancedFlashcardsWebAppServer\AdvancedFlashcardsWebAppServer.exe" (
  echo [ERROR] Build the EXE first: packaging\2. build_exe.bat
  goto :FAIL
)

set "ISCC=C:\Program Files (x86)\Inno Setup 6\ISCC.exe"
if not exist "%ISCC%" set "ISCC=ISCC.exe"

echo [INFO] Using ISCC: %ISCC%
echo [INFO] Reading version/build from ..\version.json

set "TMPV=%TEMP%\afw_version.txt"
set "TMPB=%TEMP%\afw_build.txt"
del /q "%TMPV%" "%TMPB%" 2>nul

REM Write raw version/build to temp files
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$j=Get-Content -Raw '..\version.json' | ConvertFrom-Json; [string]$j.version" > "%TMPV%"
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$j=Get-Content -Raw '..\version.json' | ConvertFrom-Json; if($j.build){[string]$j.build}else{'11'}" > "%TMPB%"

REM Read first line only (strip CR/LF), then sanitize version to numeric dotted
set /p APPVER=<"%TMPV%"
set /p APPBUILD=<"%TMPB%"

REM Sanitize: extract 3.1.0(.x) from any string (v3.1.0, 3.1.0 v11, etc.)
for /f "delims=" %%S in ('powershell -NoProfile -Command ^
  "$v='%APPVER%'; if($v -match '(\d+\.\d+\.\d+(?:\.\d+)?)'){ $Matches[1] }"') do set "APPVER=%%S"

if "%APPVER%"=="" (
  echo [ERROR] version.json did not yield a numeric version (expected like 3.1.0 or 3.1.0.6)
  goto :FAIL
)

echo [INFO] AppVersion: %APPVER%
echo [INFO] Build     : %APPBUILD%

set "APPNAME=Advanced Flashcards WebApp Server"
set "APPEXENAME=AdvancedFlashcardsWebAppServer.exe"
set "APPPUBLISHER=Sidscri"
set "APPURL=https://github.com/sidscri-apps"

echo [INFO] Compiling installer_inno.iss ...
echo [DEBUG] %ISCC% /DMyAppVersion=%APPVER% /DMyAppBuild=%APPBUILD% ...

"%ISCC%" ^
  /DMyAppVersion=%APPVER% ^
  /DMyAppBuild=%APPBUILD% ^
  /DMyAppName="%APPNAME%" ^
  /DMyAppExeName="%APPEXENAME%" ^
  /DMyAppPublisher="%APPPUBLISHER%" ^
  /DMyAppURL="%APPURL%" ^
  "installer_inno.iss"

if errorlevel 1 (
  echo [ERROR] Inno Setup build failed.
  goto :FAIL
)

echo [DONE] Installer created in packaging\output\
pause
exit /b 0

:FAIL
echo.
echo [FAIL] Build aborted.
pause
exit /b 1
