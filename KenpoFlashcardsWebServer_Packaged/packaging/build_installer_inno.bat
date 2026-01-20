@echo off
setlocal

REM Requires Inno Setup installed.
REM This script builds the installer EXE using the .iss script.

cd /d "%~dp0"

REM Ensure packaging\output exists and is a folder (avoid I/O error 183)
if exist "output" (
  if not exist "output\" (
    del /f /q "output"
  ) else (
    rmdir /s /q "output"
  )
)
mkdir "output" 2>nul

if not exist "..\dist\KenpoFlashcardsTray\KenpoFlashcardsTray.exe" (
  echo [ERROR] Build the EXE first: packaging\build_exe.bat
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
  pause
  exit /b 1
)

echo [DONE] Installer created in packaging\output\
pause
