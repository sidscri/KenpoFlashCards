@echo off
setlocal EnableExtensions EnableDelayedExpansion

REM =============================================================================
REM Advanced Flashcards WebApp Server - 2. build_exe.bat (STAY-OPEN + FIXED)
REM
REM Fixes:
REM   - Prevents "label specified - ENSURE_VENV" by NOT using subroutine labels
REM   - Prevents PowerShell '&' / caret escaping issues by using pure PowerShell syntax
REM   - ALWAYS stays open: if double-clicked, relaunches itself inside `cmd /k`
REM   - Shows LIVE output (pip + PyInstaller) and appends it to the repo log
REM   - PAUSE on success/failure (in addition to cmd /k, harmless if run from console)
REM =============================================================================

REM --- Always run inside a persistent console if double-clicked ---
if /i "%~1" NEQ "__RUN" (
  start "Advanced Flashcards WebApp Server - Build EXE" cmd /k ""%~f0" __RUN"
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

REM --- Log path ---
for /f %%i in ('powershell -NoProfile -Command "Get-Date -Format yyyyMMdd_HHmmss"') do set "STAMP=%%i"
set "REPO_BASE=%PROJ_DIR%\.."
set "REPO_LOG_BASE=%REPO_BASE%\logs\Install"
if /i "%MODE%"=="update" (
  if not exist "%REPO_LOG_BASE%\updates" mkdir "%REPO_LOG_BASE%\updates" >nul 2>nul
  set "REPO_LOG=%REPO_LOG_BASE%\updates\build_exe_%STAMP%.log"
) else (
  if not exist "%REPO_LOG_BASE%\installs" mkdir "%REPO_LOG_BASE%\installs" >nul 2>nul
  set "REPO_LOG=%REPO_LOG_BASE%\installs\build_exe_%STAMP%.log"
)

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

echo [%date% %time%] PC=%COMPUTERNAME% USER=%USERNAME% VERSION=%APP_VERSION% MODE=%MODE%>>"%REPO_LOG%"
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



REM --- Ensure kenpo_words.json exists in packaged data (auto-copy from Android assets if missing) ---
REM Source location (Android project assets):
set "ANDROID_ASSETS=%PROJ_DIR%\..\KenpoFlashcardsProject-v2\app\src\main\assets"
REM Only copy when missing so we never overwrite a newer packaged copy:
if not exist "%ROOT_DATA%\kenpo_words.json" (
  if exist "%ANDROID_ASSETS%\kenpo_words.json" (
    echo [INFO] Copying kenpo_words.json from Android assets...
    copy /y "%ANDROID_ASSETS%\kenpo_words.json" "%ROOT_DATA%\kenpo_words.json" >nul
    echo [%date% %time%] Copied kenpo_words.json from Android assets>>"%REPO_LOG%"
  ) else (
    echo [WARNING] kenpo_words.json missing in "%ROOT_DATA%" and not found in "%ANDROID_ASSETS%"
    echo [%date% %time%] WARNING kenpo_words.json missing in packaged data and Android assets>>"%REPO_LOG%"
  )
)

REM --- Clean build artifacts (leave venv unless you want full rebuild) ---
echo [INFO] Cleaning build artifacts...
if exist "%PROJ_DIR%\build" rmdir /s /q "%PROJ_DIR%\build" >nul 2>&1
if exist "%PROJ_DIR%\dist"  rmdir /s /q "%PROJ_DIR%\dist"  >nul 2>&1

REM --- Ensure venv (inline) ---
echo [INFO] Ensuring venv...
if not exist "%PY_EXE%" (
  echo [INFO] Creating venv at: %VENV_DIR%
  py -3 -m venv "%VENV_DIR%" >>"%REPO_LOG%" 2>&1
  if errorlevel 1 (
    echo [ERROR] Failed to create venv.
    echo [%date% %time%] ERROR venv create failed>>"%REPO_LOG%"
    goto :FAIL
  )
)

REM Provide data dir to spec (your spec reads env var in your toolchain)
set "AFS_DATA_DIR=%DATA_DIR%"

REM --- Run pip + pyinstaller with LIVE output, logged ---
echo [INFO] Installing packaging requirements (LIVE output)...
powershell -NoProfile -Command ^
  "$ErrorActionPreference='Continue';" ^
  "& '%PY_EXE%' -m pip --version 2>&1 | Tee-Object -FilePath '%REPO_LOG%' -Append;" ^
  "& '%PY_EXE%' -m pip install --upgrade pip setuptools wheel 2>&1 | Tee-Object -FilePath '%REPO_LOG%' -Append;" ^
  "exit $LASTEXITCODE"
if errorlevel 1 (
  echo [ERROR] pip upgrade failed. See log: %REPO_LOG%
  goto :FAIL
)

powershell -NoProfile -Command ^
  "$ErrorActionPreference='Continue';" ^
  "& '%PY_EXE%' -m pip install -r '%REQFILE%' 2>&1 | Tee-Object -FilePath '%REPO_LOG%' -Append;" ^
  "exit $LASTEXITCODE"
if errorlevel 1 (
  echo [ERROR] pip install -r failed. See log: %REPO_LOG%
  goto :FAIL
)

echo [INFO] Building EXE with PyInstaller (LIVE output)...
powershell -NoProfile -Command ^
  "$ErrorActionPreference='Continue';" ^
  "& '%PY_EXE%' -m PyInstaller '%SPECFILE%' --noconfirm --clean 2>&1 | Tee-Object -FilePath '%REPO_LOG%' -Append;" ^
  "exit $LASTEXITCODE"
if errorlevel 1 (
  echo [ERROR] PyInstaller failed. See log: %REPO_LOG%
  goto :FAIL
)

echo.
echo [DONE] Build complete.
echo       Output: dist\AdvancedFlashcardsWebAppServer\AdvancedFlashcardsWebAppServer.exe
echo [%date% %time%] OK build_exe complete>>"%REPO_LOG%"
popd
pause
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
