@echo off
setlocal

REM Requires Inno Setup installed.
REM This script builds the installer EXE using the .iss script.

cd /d "%~dp0"

if not exist "..\dist\KenpoFlashcardsTray\KenpoFlashcardsTray.exe" (
  echo [ERROR] Missing tray EXE. Build first: packaging\build_exe.bat
  if /i "%CI%"=="true" exit /b 1
  pause
  exit /b 1
)

if not exist "..\dist\KenpoFlashcardsWebServer\KenpoFlashcardsWebServer.exe" (
  echo [ERROR] Missing server EXE. Build first: packaging\build_exe.bat
  if /i "%CI%"=="true" exit /b 1
  pause
  exit /b 1
)

set ISCC_DEFAULT="C:\Program Files (x86)\Inno Setup 6\ISCC.exe"
if exist %ISCC_DEFAULT% (
  set ISCC=%ISCC_DEFAULT%
) else (
  set ISCC=ISCC.exe
)

echo [INFO] Running Inno Setup compiler...
%ISCC% "installer_inno.iss"
if errorlevel 1 (
  echo [ERROR] Inno Setup build failed.
  if /i "%CI%"=="true" exit /b 1
  pause
  exit /b 1
)

echo [DONE] Installer created in packaging\output\
if /i "%CI%"=="true" exit /b 0
pause
