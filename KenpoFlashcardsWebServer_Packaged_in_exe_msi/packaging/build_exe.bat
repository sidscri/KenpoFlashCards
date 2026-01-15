@echo off
setlocal enabledelayedexpansion

REM Build KenpoFlashcardsTray.exe and KenpoFlashcardsWebServer.exe using PyInstaller.
REM Run this from the project root (same folder as app.py).

cd /d "%~dp0\.."

if not exist ".venv" (
  echo [INFO] Creating venv...
  py -m venv .venv
)

echo [INFO] Activating venv...
call ".venv\Scripts\activate.bat"

echo [INFO] Installing packaging requirements...
py -m pip install --upgrade pip >nul
py -m pip install -r "packaging\requirements_packaging.txt"

echo [INFO] Building Tray EXE...
py -m PyInstaller "packaging\pyinstaller\kenpo_tray.spec" --noconfirm || goto :err

echo [INFO] Building Server EXE...
py -m PyInstaller "packaging\pyinstaller\kenpo_server.spec" --noconfirm || goto :err

echo.
echo [DONE] Build complete.
echo   Tray  : dist\KenpoFlashcardsTray\KenpoFlashcardsTray.exe
echo   Server: dist\KenpoFlashcardsWebServer\KenpoFlashcardsWebServer.exe
echo.

if /i "%CI%"=="true" exit /b 0
pause
exit /b 0

:err
echo [ERROR] Build failed.
if /i "%CI%"=="true" exit /b 1
pause
exit /b 1
