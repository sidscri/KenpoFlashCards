@echo off
setlocal

set SCRIPT_DIR=%~dp0
set ROOT_DIR=%SCRIPT_DIR%\..

cd /d "%ROOT_DIR%"

if not exist ".venv\Scripts\python.exe" (
  echo [ERROR] .venv not found. Run windows_tray\INSTALL_Tray_Dependencies.bat first.
  pause
  exit /b 1
)

REM Use pythonw to avoid a console window for the tray
start "" ".venv\Scripts\pythonw.exe" "windows_tray\kenpo_tray.py"
