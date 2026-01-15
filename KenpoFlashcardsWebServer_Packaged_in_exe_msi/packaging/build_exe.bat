@echo off
setlocal enabledelayedexpansion

REM Build KenpoFlashcardsTray.exe using PyInstaller.
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

echo [INFO] Building EXE with PyInstaller...
py -m PyInstaller "packaging\pyinstaller\kenpo_tray.spec" --noconfirm

echo.
echo [DONE] Build complete.
echo       Output: dist\KenpoFlashcardsTray\KenpoFlashcardsTray.exe
echo.
pause
