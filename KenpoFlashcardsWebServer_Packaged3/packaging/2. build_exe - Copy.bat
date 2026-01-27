@echo off
setlocal EnableExtensions EnableDelayedExpansion

REM =============================================================================
REM Advanced Flashcards WebApp Server - 2. build_exe.bat
REM
REM Goals:
REM   - Clean, high-level progress output (details go to the log)
REM   - Logs are written to: <project>\packaging\logs
REM   - Uses ONLY: <project>\data  (no staging / swapping)
REM   - Auto-closes on success (no pause); stays open on failure
REM =============================================================================

set "SCRIPT_DIR=%~dp0"
set "PROJ_DIR=%SCRIPT_DIR%.."
pushd "%PROJ_DIR%" || (echo [ERROR] Could not cd to project root. & exit /b 1)

set "VENV_DIR=%PROJ_DIR%\.venv"
set "PY_EXE=%VENV_DIR%\Scripts\python.exe"
set "REQFILE=%SCRIPT_DIR%requirements_packaging.txt"
set "SPECFILE=%PROJ_DIR%\packaging\pyinstaller\kenpo_tray.spec"

set "ROOT_DATA=%PROJ_DIR%\data"
set "DATA_DIR=%ROOT_DATA%"

REM Local app logs (kept for compatibility; not used for build output)
set "APPDATA_ROOT=%LOCALAPPDATA%\Advanced Flashcards WebApp Server"
set "LOCAL_LOG_DIR=%APPDATA_ROOT%\log\Advanced Flashcards WebApp Server logs"
set "LOCAL_LOG_FILE=%LOCAL_LOG_DIR%\builder.log"

REM Repo build logs (requested)
set "REPO_LOG_DIR=%SCRIPT_DIR%logs"

call :STAMP
set "STAMP=%STAMP%"

REM Determine version (best-effort)
set "APP_VERSION=unknown"
for /f "usebackq tokens=* delims=" %%v in (`powershell -NoProfile -Command ^
  "$p = Join-Path '%PROJ_DIR%' 'version.json'; if(Test-Path $p){ (Get-Content $p -Raw | ConvertFrom-Json).version }" 2^>nul`) do set "APP_VERSION=%%v"

call :ENSURE_DIR "%REPO_LOG_DIR%"
set "REPO_LOG=%REPO_LOG_DIR%\build_exe_%STAMP%_v%APP_VERSION%.log"

call :ENSURE_DIR "%LOCAL_LOG_DIR%"

REM Validate inputs (do not move files)
if not exist "%PY_EXE%" (
  echo [INFO] Virtualenv not found. Creating at "%VENV_DIR%"...
  call :ENSURE_VENV
  if errorlevel 1 (
    echo [ERROR] Failed to create virtualenv / install base tools.
    goto :FAIL
  )
)
if not exist "%REQFILE%" (
  echo [ERROR] Missing: "%REQFILE%"
  goto :FAIL
)
if not exist "%SPECFILE%" (
  echo [ERROR] Missing: "%SPECFILE%"
  goto :FAIL
)
if not exist "%PROJ_DIR%\version.json" (
  echo [ERROR] Missing: "%PROJ_DIR%\version.json"
  goto :FAIL
)
if not exist "%DATA_DIR%\kenpo_words.json" (
  echo [ERROR] Missing: "%DATA_DIR%\kenpo_words.json"
  echo         (Per Step 5, kenpo_words.json must live in <project>\data\)
  goto :FAIL
)

REM Tell PyInstaller/spec where the data folder is
set "AFS_DATA_DIR=%DATA_DIR%"

echo.
echo ============================================================
echo  Advanced Flashcards WebApp Server - Build EXE
echo  Version: %APP_VERSION%   Stamp: %STAMP%
echo  Data   : %AFS_DATA_DIR%
echo  Log    : %REPO_LOG%
echo ============================================================
echo.

REM Start log
echo ============================================================ > "%REPO_LOG%"
echo Build EXE - %DATE% %TIME%>> "%REPO_LOG%"
echo Project: %PROJ_DIR%>> "%REPO_LOG%"
echo Version: %APP_VERSION%>> "%REPO_LOG%"
echo Data   : %AFS_DATA_DIR%>> "%REPO_LOG%"
echo ============================================================>> "%REPO_LOG%"
echo.>> "%REPO_LOG%"

echo [1/3] Ensuring packaging dependencies...
echo     (details in log)
"%PY_EXE%" -m pip install -r "%REQFILE%" >> "%REPO_LOG%" 2>&1
if errorlevel 1 (
  echo [ERROR] Packaging requirements install failed.
  goto :FAIL
)

echo [2/3] Building EXE (PyInstaller)...
echo     (this can take a bit - details in log)
"%PY_EXE%" -m PyInstaller "%SPECFILE%" --noconfirm >> "%REPO_LOG%" 2>&1
set "PYI_RC=%ERRORLEVEL%"
echo PyInstaller exit code: %PYI_RC%>> "%REPO_LOG%"
if not "%PYI_RC%"=="0" (
  echo [ERROR] PyInstaller failed (exit code %PYI_RC%).
  goto :FAIL
)

echo [3/3] Verifying output...
set "OUT_EXE=%PROJ_DIR%\dist\AdvancedFlashcardsWebAppServer\AdvancedFlashcardsWebAppServer.exe"
if not exist "%OUT_EXE%" (
  echo [ERROR] Build completed but output EXE not found:
  echo         "%OUT_EXE%"
  goto :FAIL
)

echo.>> "%REPO_LOG%"
echo [DONE] Output: %OUT_EXE%>> "%REPO_LOG%"
echo Build completed successfully.>> "%REPO_LOG%"

echo.
echo [DONE] Build complete.
echo Output: %OUT_EXE%
echo Log   : %REPO_LOG%
echo.

popd
exit /b 0

:FAIL
echo.
echo [FAIL] Build did not complete.
echo Log: %REPO_LOG%
echo.
REM Show a short tail to make it easier to spot the failure without scrolling
powershell -NoProfile -Command "if(Test-Path '%REPO_LOG%'){ '--- log tail (last 40 lines) ---'; Get-Content '%REPO_LOG%' -Tail 40 }" 2>nul
echo.
pause
popd
exit /b 1


:ENSURE_VENV
REM Create a clean virtualenv in the repo root (.venv) if missing.
if exist "%VENV_DIR%" (
  rmdir /s /q "%VENV_DIR%" >> "%REPO_LOG%" 2>&1
)
set "PYLAUNCH=py -3.8"
%PYLAUNCH% -V >nul 2>&1 || set "PYLAUNCH=py -3"
%PYLAUNCH% -V >nul 2>&1 || set "PYLAUNCH=python"
echo [INFO] Using Python launcher: %PYLAUNCH% >> "%REPO_LOG%"
%PYLAUNCH% -m venv "%VENV_DIR%" >> "%REPO_LOG%" 2>&1
if errorlevel 1 exit /b 1
if not exist "%PY_EXE%" exit /b 1
"%PY_EXE%" -m pip install -U pip setuptools wheel >> "%REPO_LOG%" 2>&1
if errorlevel 1 exit /b 1
exit /b 0

:ENSURE_DIR
if not exist "%~1\" mkdir "%~1" >nul 2>&1
exit /b 0

:STAMP
for /f %%a in ('powershell -NoProfile -Command "(Get-Date).ToString('yyyyMMdd_HHmmss')"') do set "STAMP=%%a"
exit /b 0
