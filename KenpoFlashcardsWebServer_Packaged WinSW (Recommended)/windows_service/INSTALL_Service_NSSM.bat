@echo off
setlocal enabledelayedexpansion

REM =========================================================
REM Install KenpoFlashcardsWeb as a Windows Service via NSSM
REM =========================================================
REM Run this as Administrator.
REM Place nssm.exe in this same folder first.
REM Service Name: KenpoFlashcardsWeb
REM =========================================================

set SCRIPT_DIR=%~dp0
set ROOT_DIR=%SCRIPT_DIR%\..

if not exist "%SCRIPT_DIR%\nssm.exe" (
  echo [ERROR] nssm.exe not found in %SCRIPT_DIR%
  echo Download NSSM and place nssm.exe here: %SCRIPT_DIR%
  exit /b 1
)

cd /d "%ROOT_DIR%"

if not exist ".venv\Scripts\python.exe" (
  echo [INFO] Creating venv in %CD%\.venv ...
  py -3 -m venv .venv
)

echo [INFO] Updating pip...
call ".venv\Scripts\python.exe" -m pip install --upgrade pip

echo [INFO] Installing requirements...
call ".venv\Scripts\python.exe" -m pip install -r requirements.txt

set SVC=KenpoFlashcardsWeb
set PY="%CD%\.venv\Scripts\python.exe"
set APP="%CD%\app.py"
set WORKDIR="%CD%"

echo [INFO] Installing service %SVC% ...
"%SCRIPT_DIR%\nssm.exe" stop %SVC% >nul 2>&1
"%SCRIPT_DIR%\nssm.exe" remove %SVC% confirm >nul 2>&1

"%SCRIPT_DIR%\nssm.exe" install %SVC% %PY% %APP%
"%SCRIPT_DIR%\nssm.exe" set %SVC% AppDirectory %WORKDIR%
"%SCRIPT_DIR%\nssm.exe" set %SVC% Start SERVICE_AUTO_START

REM ---- Default env (edit later with SET_Service_Env.bat) ----
REM NOTE: Store secrets (OPENAI_API_KEY) at the service level, not inside git files.
"%SCRIPT_DIR%\nssm.exe" set %SVC% AppEnvironmentExtra ^
  "KENPO_WEB_PORT=8009" ^
  "OPENAI_MODEL=gpt-4o-mini"

REM ---- Log to files ----
mkdir "%CD%\logs" >nul 2>&1
"%SCRIPT_DIR%\nssm.exe" set %SVC% AppStdout "%CD%\logs\service_stdout.log"
"%SCRIPT_DIR%\nssm.exe" set %SVC% AppStderr "%CD%\logs\service_stderr.log"
"%SCRIPT_DIR%\nssm.exe" set %SVC% AppRotateFiles 1
"%SCRIPT_DIR%\nssm.exe" set %SVC% AppRotateOnline 1
"%SCRIPT_DIR%\nssm.exe" set %SVC% AppRotateBytes 1048576

echo [INFO] Starting service...
"%SCRIPT_DIR%\nssm.exe" start %SVC%

echo.
echo [DONE] Service installed: %SVC%
echo - Start/Stop via Services.msc or the provided bat files.
echo - Logs: %CD%\logs\
echo.
pause
