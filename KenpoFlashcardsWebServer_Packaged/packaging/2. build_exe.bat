@echo off
setlocal EnableExtensions EnableDelayedExpansion

REM =============================================================================
REM  Advanced Flashcards WebApp Server - 2. build_exe.bat
REM  - Uses ROOT\.venv, ROOT\build, ROOT\dist (always)
REM  - Logs to ROOT\packaging\logs\build_exe_<timestamp>_v<version>.log
REM  - Minimal console output (section progress); full details in log
REM  - Closes on SUCCESS; pauses on FAIL
REM =============================================================================

title Advanced Flashcards WebApp Server - Build EXE

REM --- Resolve paths ---
set "SCRIPT_DIR=%~dp0"
set "ROOT_DIR=%SCRIPT_DIR%.."
for %%I in ("%ROOT_DIR%") do set "ROOT_DIR=%%~fI"
set "VENV_DIR=%ROOT_DIR%\.venv"
set "PY_EXE=%VENV_DIR%\Scripts\python.exe"
set "REQ_FILE=%ROOT_DIR%\packaging\requirements_packaging.txt"
set "SPEC_FILE=%ROOT_DIR%\packaging\pyinstaller\kenpo_tray.spec"

REM --- Logs (ALWAYS under packaging\logs) ---
set "LOG_DIR=%SCRIPT_DIR%logs"
if not exist "%LOG_DIR%" mkdir "%LOG_DIR%" >nul 2>nul

call :GET_TS
call :READ_VERSION

REM sanitize version for filenames (strip spaces)
set "SAFE_VER=%APP_VERSION%"
set "SAFE_VER=%SAFE_VER: =%"
set "LOG_FILE=%LOG_DIR%\build_exe_%TS%_v%SAFE_VER%.log"

REM Ensure log file exists even if we fail very early
> "%LOG_FILE%" echo [%date% %time%] START Build EXE  VERSION=%APP_VERSION%  BUILD=%APP_BUILD%

call :LOG "============================================================="
call :LOG " Advanced Flashcards WebApp Server - Build EXE"
call :LOG "============================================================="
call :LOG "Project root : %ROOT_DIR%"
call :LOG "Version      : %APP_VERSION%"
call :LOG "Build        : %APP_BUILD%"
call :LOG "Venv         : %VENV_DIR%"
call :LOG "Spec         : %SPEC_FILE%"
call :LOG "Log          : %LOG_FILE%"
echo.
echo ============================================================
echo  Advanced Flashcards WebApp Server - Build EXE
echo ============================================================
echo [INFO] Project root : %ROOT_DIR%
echo [INFO] Version      : %APP_VERSION%
echo [INFO] Build        : %APP_BUILD%
echo [INFO] Log          : %LOG_FILE%
echo.

REM --- Basic checks ---
if not exist "%SPEC_FILE%" (
  call :LOG "[ERROR] Spec file not found: %SPEC_FILE%"
  echo [ERROR] Spec file not found: "%SPEC_FILE%"
  goto :FAIL
)

if not exist "%REQ_FILE%" (
  call :LOG "[ERROR] Requirements file not found: %REQ_FILE%"
  echo [ERROR] Requirements file not found: "%REQ_FILE%"
  goto :FAIL
)

REM --- Find a system Python to create the venv (prefers py launcher) ---
call :FIND_SYS_PY
if not defined SYS_PY (
  call :LOG "[ERROR] Could not find a system Python (py/python) on PATH to create venv."
  echo [ERROR] Could not find Python on PATH (py/python).
  echo         Install Python 3.x or enable the Python launcher.
  goto :FAIL
)

REM --- Ensure venv exists in ROOT\.venv ---
if not exist "%PY_EXE%" (
  echo [STEP 1/4] Creating venv in root\.venv ...
  call :LOG "[STEP] Creating venv: %SYS_PY% -m venv %VENV_DIR%"
  call %SYS_PY% -m venv "%VENV_DIR%" >> "%LOG_FILE%" 2>&1
  if errorlevel 1 (
    call :LOG "[ERROR] venv creation failed."
    echo [ERROR] venv creation failed. See log:
    echo         %LOG_FILE%
    goto :FAIL
  )
)

if not exist "%PY_EXE%" (
  call :LOG "[ERROR] Virtualenv python not found after creation: %PY_EXE%"
  echo [ERROR] Virtualenv python not found: "%PY_EXE%"
  echo         Create venv and install requirements first.
  goto :FAIL
)

REM --- Install packaging requirements ---
echo [STEP 2/4] Installing / updating packaging requirements ...
call :LOG "[STEP] pip install -r %REQ_FILE%"
set "PIP_DISABLE_PIP_VERSION_CHECK=1"
set "PYTHONUTF8=1"
"%PY_EXE%" -m pip install --upgrade pip setuptools wheel >> "%LOG_FILE%" 2>&1
if errorlevel 1 (
  call :LOG "[ERROR] pip bootstrap failed."
  echo [ERROR] pip bootstrap failed. See log:
  echo         %LOG_FILE%
  goto :FAIL
)

"%PY_EXE%" -m pip install -r "%REQ_FILE%" >> "%LOG_FILE%" 2>&1
if errorlevel 1 (
  call :LOG "[ERROR] pip install requirements failed."
  echo [ERROR] pip install failed. See log:
  echo         %LOG_FILE%
  goto :FAIL
)

REM --- Clean build/dist in ROOT ---
echo [STEP 3/4] Cleaning build artifacts (root\build, root\dist) ...
call :LOG "[STEP] Cleaning build/dist"
if exist "%ROOT_DIR%\build" rmdir /s /q "%ROOT_DIR%\build" >> "%LOG_FILE%" 2>&1
if exist "%ROOT_DIR%\dist"  rmdir /s /q "%ROOT_DIR%\dist"  >> "%LOG_FILE%" 2>&1

REM --- Run PyInstaller ---
echo [STEP 4/4] Building EXE with PyInstaller (this can take a bit) ...
call :LOG "[STEP] PyInstaller build"
pushd "%ROOT_DIR%" >nul
"%PY_EXE%" -m PyInstaller --noconfirm "%SPEC_FILE%" >> "%LOG_FILE%" 2>&1
set "RC=%ERRORLEVEL%"
popd >nul

if not "%RC%"=="0" (
  call :LOG "[ERROR] PyInstaller failed with code %RC%."
  echo [ERROR] Build failed with code %RC%.
  echo         See log: %LOG_FILE%
  goto :FAIL
)

REM --- Success ---
call :LOG "[OK] Build completed successfully."
echo.
echo [OK] Build completed successfully.
echo      Output: %ROOT_DIR%\dist
echo      Log   : %LOG_FILE%
exit /b 0

:FAIL
call :LOG "[FAIL] Build did not complete."
echo.
echo [FAIL] Build did not complete.
echo Log: %LOG_FILE%
echo.
pause
exit /b 1

REM =============================================================================
REM Helpers
REM =============================================================================

:LOG
>> "%LOG_FILE%" echo [%date% %time%] %~1
exit /b 0

:GET_TS
for /f "tokens=1-3 delims=/ " %%a in ("%date%") do (
  set "MM=%%a"
  set "DD=%%b"
  set "YY=%%c"
)
for /f "tokens=1-3 delims=:." %%a in ("%time%") do (
  set "HH=%%a"
  set "MI=%%b"
  set "SS=%%c"
)
if "%HH:~0,1%"==" " set "HH=0%HH:~1,1%"
set "TS=%YY%%MM%%DD%_%HH%%MI%%SS%"
exit /b 0

:READ_VERSION
set "APP_VERSION=unknown"
set "APP_BUILD=unknown"
if exist "%ROOT_DIR%\version.json" (
  for /f "usebackq delims=" %%L in (`powershell -NoProfile -ExecutionPolicy Bypass ^
    "(Get-Content -Raw '%ROOT_DIR%\version.json' | ConvertFrom-Json | ForEach-Object { $_.version.ToString().Trim() + '|' + $_.build.ToString().Trim() })" 2^>nul`) do (
      for /f "tokens=1,2 delims=|" %%a in ("%%L") do (
        if not "%%a"=="" set "APP_VERSION=%%a"
        if not "%%b"=="" set "APP_BUILD=%%b"
      )
  )
)
exit /b 0

:FIND_SYS_PY
set "SYS_PY="
REM Prefer Python launcher with a specific major.minor if present
where py >nul 2>nul && (
  py -3.8 -V >nul 2>nul && (set "SYS_PY=py -3.8" & exit /b 0)
  py -3   -V >nul 2>nul && (set "SYS_PY=py -3"   & exit /b 0)
  py -V        >nul 2>nul && (set "SYS_PY=py"     & exit /b 0)
)

REM Fallback to python.exe on PATH
where python >nul 2>nul && (
  python -V >nul 2>nul && (set "SYS_PY=python" & exit /b 0)
)

exit /b 0
