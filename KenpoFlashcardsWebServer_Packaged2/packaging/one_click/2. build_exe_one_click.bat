@echo off
setlocal EnableExtensions EnableDelayedExpansion

REM =============================================================================
REM 2. build_exe_one_click.bat  (lives in \packaging\one_click\)
REM
REM IMPORTANT PATH RULES (per your request):
REM   - build_data stays in: \packaging\build_data
REM   - logs stay in      : \packaging\logs
REM
REM Notes:
REM   - Keeps the same "spawn new window unless __RUN" behavior
REM =============================================================================

REM --- If double-clicked, run in a new console that closes on success ---
if /i "%~1" NEQ "__RUN" (
  start "Advanced Flashcards WebApp Server - Build EXE" cmd /c ""%~f0" __RUN"
  exit /b
)

REM --- Resolve folders
set "ONECLICK_DIR=%~dp0"
if "%ONECLICK_DIR:~-1%"=="\" set "ONECLICK_DIR=%ONECLICK_DIR:~0,-1%"

set "PACK_DIR=%ONECLICK_DIR%\.."
pushd "%PACK_DIR%" >nul 2>&1
set "PACK_DIR=%CD%"
popd >nul 2>&1

set "PROJ_DIR=%PACK_DIR%\.."
pushd "%PROJ_DIR%" >nul 2>&1
set "PROJ_DIR=%CD%"
popd >nul 2>&1

pushd "%PROJ_DIR%" || (echo [ERROR] Could not cd to project root. & goto :FAIL)

set "VENV_DIR=%PROJ_DIR%\.venv"
set "PY_EXE=%VENV_DIR%\Scripts\python.exe"
set "REQFILE=%PACK_DIR%\requirements_packaging.txt"
set "SPECFILE=%PACK_DIR%\pyinstaller\kenpo_tray.spec"

set "ROOT_DATA=%PROJ_DIR%\data"
set "BUILD_DATA=%PACK_DIR%\build_data"
set "FLAG_FILE=%BUILD_DATA%\.from_local.flag"

REM --- Read version (best-effort) ---
set "APP_VERSION=unknown"
for /f "usebackq tokens=* delims=" %%v in (`powershell -NoProfile -Command "$p=Join-Path '%PROJ_DIR%' 'version.json'; if(Test-Path $p){try{(Get-Content $p -Raw|ConvertFrom-Json).version}catch{}}" 2^>nul`) do set "APP_VERSION=%%v"

REM --- Choose data directory (flagged build_data wins) ---
set "MODE=install"
set "DATA_DIR=%ROOT_DATA%"
if exist "%BUILD_DATA%\" (
  set "DATA_DIR=%BUILD_DATA%"
  if exist "%FLAG_FILE%" set "MODE=update"
)

REM ---------------- LOGGING ----------------
set "LOGDIR=%PACK_DIR%\logs"
if not exist "%LOGDIR%" mkdir "%LOGDIR%" >nul 2>&1
for /f %%i in ('powershell -NoProfile -Command "Get-Date -Format yyyyMMdd_HHmmss"') do set "TS=%%i"
if not defined TS set "TS=%date:~-4%%date:~4,2%%date:~7,2%_%time:~0,2%%time:~3,2%%time:~6,2%"
set "TS=%TS: =0%"
set "REPO_LOG=%LOGDIR%\build_exe_%TS%.log"
echo [INFO] Log file : "%REPO_LOG%"
echo.
echo [INFO] Version  : %APP_VERSION%
echo [INFO] Mode     : %MODE%
echo [INFO] Data dir : %DATA_DIR%
echo.

(
  echo ============================================================
  echo Build EXE started: %date% %time%
  echo Project root : %PROJ_DIR%
  echo Packaging    : %PACK_DIR%
  echo Mode         : %MODE%
  echo Data dir     : %DATA_DIR%
  echo ============================================================
) >> "%REPO_LOG%"

REM --- Basic validation
if not exist "%REQFILE%" (
  echo [ERROR] Missing: "%REQFILE%"
  echo [ERROR] Missing requirements file>>"%REPO_LOG%"
  goto :FAIL
)
if not exist "%SPECFILE%" (
  echo [ERROR] Missing: "%SPECFILE%"
  echo [ERROR] Missing spec file>>"%REPO_LOG%"
  goto :FAIL
)

REM --- Create venv if needed
if not exist "%PY_EXE%" (
  echo [1/5] Creating venv...
  echo [1/5] Creating venv>>"%REPO_LOG%"
  python -m venv "%VENV_DIR%" >> "%REPO_LOG%" 2>&1 || goto :FAIL
)

REM --- Install packaging requirements
echo [2/5] Installing packaging requirements...
echo [2/5] Installing packaging requirements>>"%REPO_LOG%"
"%PY_EXE%" -m pip install -r "%REQFILE%" >> "%REPO_LOG%" 2>&1 || goto :FAIL

REM --- Ensure data directory exists (already staged by Step 1)
echo [3/5] Verifying data folder...
echo [3/5] Verifying data folder>>"%REPO_LOG%"
if not exist "%DATA_DIR%\" (
  echo [ERROR] Data folder missing: "%DATA_DIR%"
  echo [ERROR] Data folder missing>>"%REPO_LOG%"
  goto :FAIL
)

REM --- Run PyInstaller
echo [4/5] Running PyInstaller...
echo [4/5] Running PyInstaller>>"%REPO_LOG%"
"%PY_EXE%" -m PyInstaller "%SPECFILE%" --noconfirm >> "%REPO_LOG%" 2>&1 || goto :FAIL

REM --- Done
echo [5/5] Done.
echo [5/5] Done>>"%REPO_LOG%"

echo.
echo [SUCCESS] Build EXE completed.
echo [%date% %time%] SUCCESS build_exe>>"%REPO_LOG%"
popd
exit /b 0

:FAIL
echo.
echo [FAILED] Build did not complete.
echo [%date% %time%] FAIL build_exe>>"%REPO_LOG%"
echo [INFO] Opening log so you can see the error...
if exist "%REPO_LOG%" start "" notepad "%REPO_LOG%"
popd
pause
exit /b 1
