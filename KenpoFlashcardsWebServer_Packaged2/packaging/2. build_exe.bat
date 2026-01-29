@echo off
setlocal EnableExtensions EnableDelayedExpansion

REM =============================================================================
REM Advanced Flashcards WebApp Server - 2. build_exe.bat (VISUAL + SAFE)
REM
REM Goals:
REM   - Keep the last-known-good build logic (venv + kenpo_words copy + pyinstaller)
REM   - Write build logs to: <project>\packaging\logs\build_exe_YYYYMMDD_HHMMSS.log
REM   - Cleaner console output (section-based progress; full details in the log)
REM   - Auto-close window ONLY on SUCCESS (stay open + show log on FAILURE)
REM =============================================================================

REM --- If double-clicked, run in a new console that closes on success ---
if /i "%~1" NEQ "__RUN" (
  start "Advanced Flashcards WebApp Server - Build EXE" cmd /c ""%~f0" __RUN"
  exit /b
)

set "SCRIPT_DIR=%~dp0"
set "PROJ_DIR=%SCRIPT_DIR%.."
pushd "%PROJ_DIR%" || (echo [ERROR] Could not cd to project root. & goto :FAIL)

set "VENV_DIR=%PROJ_DIR%\.venv"
set "PY_EXE=%VENV_DIR%\Scripts\python.exe"
set "REQFILE=%SCRIPT_DIR%requirements_packaging.txt"
set "SPECFILE=%PROJ_DIR%\packaging\pyinstaller\kenpo_tray.spec"

set "ROOT_DATA=%PROJ_DIR%\data"
set "BUILD_DATA=%SCRIPT_DIR%build_data"
set "FLAG_FILE=%BUILD_DATA%\.from_local.flag"

REM --- Read version (best-effort) ---
set "APP_VERSION=unknown"
for /f "usebackq tokens=* delims=" %%v in (`powershell -NoProfile -Command "$p=Join-Path '%PROJ_DIR%' 'version.json'; if(Test-Path $p){try{(Get-Content $p -Raw|ConvertFrom-Json).version}catch{}}" 2^>nul`) do set "APP_VERSION=%%v"

REM --- Choose data directory (flagged build_data wins) ---
set "MODE=install"
set "DATA_DIR=%ROOT_DATA%"
if exist "%BUILD_DATA%\" (
  if exist "%FLAG_FILE%" (
    set "MODE=update"
    set "DATA_DIR=%BUILD_DATA%"
  )
)

REM --- Log path (ALWAYS under packaging\logs as requested) ---
for /f %%i in ('powershell -NoProfile -Command "Get-Date -Format yyyyMMdd_HHmmss"') do set "STAMP=%%i"
set "LOG_DIR=%SCRIPT_DIR%logs"
if not exist "%LOG_DIR%" mkdir "%LOG_DIR%" >nul 2>nul
set "REPO_LOG=%LOG_DIR%\build_exe_%STAMP%.log"

echo.
echo ============================================================
echo  Advanced Flashcards WebApp Server - Build EXE
echo ============================================================
echo [INFO] Project root : %PROJ_DIR%
echo [INFO] Version      : %APP_VERSION%
echo [INFO] Mode         : %MODE%
echo [INFO] Data source  : %DATA_DIR%
echo [INFO] Spec file    : %SPECFILE%
echo [INFO] Log          : %REPO_LOG%
echo.

echo [%date% %time%] PC=%COMPUTERNAME% USER=%USERNAME% VERSION=%APP_VERSION% MODE=%MODE%>"%REPO_LOG%"
echo [%date% %time%] DATA_DIR=%DATA_DIR%>>"%REPO_LOG%"

REM --- Basic validation ---
if not exist "%REQFILE%" (
  echo [ERROR] Missing requirements file: "%REQFILE%"
  echo [%date% %time%] ERROR missing requirements_packaging.txt>>"%REPO_LOG%"
  goto :FAIL
)
if not exist "%SPECFILE%" (
  echo [ERROR] Missing spec file: "%SPECFILE%"
  echo [%date% %time%] ERROR missing spec file>>"%REPO_LOG%"
  goto :FAIL
)

REM --- Ensure kenpo_words.json exists in packaged data (must be in \data) ---
if not exist "%ROOT_DATA%\kenpo_words.json" (
  echo [ERROR] Missing required file: "%ROOT_DATA%\kenpo_words.json"
  echo         Put kenpo_words.json in: "%ROOT_DATA%" and re-run build.
  echo [%date% %time%] ERROR missing kenpo_words.json in root\data>>"%REPO_LOG%"
  goto :FAIL
)

REM --- Clean build artifacts (leave venv unless you want full rebuild) ---
echo [1/5] Cleaning build artifacts...
if exist "%PROJ_DIR%\build" rmdir /s /q "%PROJ_DIR%\build" >>"%REPO_LOG%" 2>&1
if exist "%PROJ_DIR%\dist"  rmdir /s /q "%PROJ_DIR%\dist"  >>"%REPO_LOG%" 2>&1

REM --- Ensure venv (inline; no subroutine labels) ---
echo [2/5] Ensuring venv...
if not exist "%PY_EXE%" (
  echo       Creating venv at: %VENV_DIR%
  py -3 -m venv "%VENV_DIR%" >>"%REPO_LOG%" 2>&1
  if errorlevel 1 (
    echo [ERROR] Failed to create venv.
    echo [%date% %time%] ERROR venv create failed>>"%REPO_LOG%"
    goto :FAIL
  )
)

REM Provide data dir to spec (your spec/toolchain reads this env var)
set "AFS_DATA_DIR=%DATA_DIR%"

REM --- Install deps (quiet console; full output in log) ---
echo [3/5] Upgrading pip/setuptools/wheel (details in log)...
"%PY_EXE%" -m pip --version >>"%REPO_LOG%" 2>&1
"%PY_EXE%" -m pip install --upgrade pip setuptools wheel >>"%REPO_LOG%" 2>&1
if errorlevel 1 (
  echo [ERROR] pip upgrade failed. See log: %REPO_LOG%
  goto :FAIL
)

echo [4/5] Installing packaging requirements (details in log)...
"%PY_EXE%" -m pip install -r "%REQFILE%" >>"%REPO_LOG%" 2>&1
if errorlevel 1 (
  echo [ERROR] pip install -r failed. See log: %REPO_LOG%
  goto :FAIL
)

REM --- Build EXE ---
echo [5/5] Building EXE with PyInstaller (details in log)...
"%PY_EXE%" -m PyInstaller "%SPECFILE%" --noconfirm --clean >>"%REPO_LOG%" 2>&1
if errorlevel 1 (
  echo [ERROR] PyInstaller failed. See log: %REPO_LOG%
  goto :FAIL
)

echo.
echo [DONE] Build complete.
echo       Output: dist\AdvancedFlashcardsWebAppServer\AdvancedFlashcardsWebAppServer.exe
echo [%date% %time%] OK build_exe complete>>"%REPO_LOG%"

popd
REM SUCCESS: close window (no pause)
exit /b 0

:FAIL
echo.
echo [FAILED] Build did not complete.
echo [%date% %time%] FAIL build_exe>>"%REPO_LOG%"
echo [INFO] Opening log so you can see the error...
if exist "%REPO_LOG%" start "" notepad "%REPO_LOG%"
popd
REM FAILURE: keep window open
pause
exit /b 1
