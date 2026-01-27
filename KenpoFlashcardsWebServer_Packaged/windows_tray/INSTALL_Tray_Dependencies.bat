@echo off
setlocal

set SCRIPT_DIR=%~dp0
set ROOT_DIR=%SCRIPT_DIR%\..

cd /d "%ROOT_DIR%"

if not exist ".venv\Scripts\python.exe" (
  echo [INFO] Creating venv in %CD%\.venv ...
  py -3 -m venv .venv
)

echo [INFO] Installing server requirements...
call ".venv\Scripts\python.exe" -m pip install -r requirements.txt

echo [INFO] Installing tray requirements...
call ".venv\Scripts\python.exe" -m pip install -r windows_tray\requirements_tray.txt

echo [DONE] Tray dependencies installed.
pause
