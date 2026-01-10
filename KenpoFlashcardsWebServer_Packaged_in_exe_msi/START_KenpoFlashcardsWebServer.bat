@echo off
setlocal enabledelayedexpansion

REM =========================================================
REM Kenpo Flashcards Web - Start Script
REM Put this file in the same folder as app.py
REM =========================================================

cd /d "%~dp0"

REM Optional: set port / data root here
REM If you leave KENPO_JSON_PATH blank, the server will auto-discover the newest:
REM   <ANY_PROJECT>\app\src\main\assets\kenpo_words.json
REM under KENPO_ROOT.
REM
REM Your constant root:
set "KENPO_ROOT=C:\Users\Sidscri\Documents\GitHub\sidscri-apps"
REM If you ever want to hard-pin to a specific project, uncomment and set:
REM set "KENPO_JSON_PATH=C:\Users\Sidscri\Documents\GitHub\sidscri-apps\KenpoFlashcardsProject-v2\app\src\main\assets\kenpo_words.json"
REM Optional: override port
REM set "KENPO_WEB_PORT=8009"

REM Force-clear any old hardcoded path from your system env (so auto-discovery works)
REM NOTE: If you uncommented and set KENPO_JSON_PATH above, this will NOT override it.
if "%KENPO_JSON_PATH%"=="" set "KENPO_JSON_PATH="

REM Optional: enable AI auto-fill for compound term breakdowns (server-side)
REM 1) Create an OpenAI API key in your OpenAI dashboard
REM 2) Uncomment the line below and paste your key

REM set "OPENAI_API_KEY=PASTE_YOUR_OPENAI_KEY_HERE"

set "OPENAI_MODEL=gpt-4o-mini"

REM optional:
set "OPENAI_API_BASE=https://api.openai.com"

if "%KENPO_WEB_PORT%"=="" set "KENPO_WEB_PORT=8009"

if not exist "app.py" (
  echo [ERROR] app.py not found in: %cd%
  pause
  exit /b 1
)

REM Create venv if needed
if not exist ".venv\Scripts\python.exe" (
  echo [INFO] Creating virtual environment...
  py -m venv .venv
  if errorlevel 1 (
    echo [ERROR] Failed to create venv. Make sure Python is installed.
    pause
    exit /b 1
  )
)

echo [INFO] Activating venv...
call ".venv\Scripts\activate.bat"

echo [INFO] Installing/Updating requirements...
python -m pip install --upgrade pip >nul
pip install -r requirements.txt
if errorlevel 1 (
  echo [ERROR] pip install failed.
  pause
  exit /b 1
)

echo.
echo [INFO] Starting Kenpo Flashcards Web on port %KENPO_WEB_PORT%...
echo        Local:   http://localhost:%KENPO_WEB_PORT%
echo        LAN:     http://%COMPUTERNAME%:%KENPO_WEB_PORT%  (name may not resolve)
echo.
echo [TIP] If you want LAN by IP, use: ipconfig to find your IPv4.
echo.

python app.py

echo.
echo [INFO] Server stopped.
pause
