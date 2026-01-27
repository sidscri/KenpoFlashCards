@echo off
setlocal EnableExtensions EnableDelayedExpansion

REM =============================================================================
REM  Advanced Flashcards WebApp Server - 2. build_exe.bat
REM
REM  Goals (per Sidney):
REM    - venv, build, dist live in project ROOT (\KenpoFlashcardsWebServer_Packaged\)
REM    - kenpo_words.json stays in \data\ (auto-copy from Android assets if missing)
REM    - version.json stays in repo root
REM    - Logs always go to: \packaging\logs\build_exe_<timestamp>_v<version>.log
REM    - Quieter console output (section progress), full details in the log
REM    - Window closes on SUCCESS; pauses on FAIL
REM =============================================================================

set "SCRIPT_DIR=%~dp0"

REM --- Resolve project root (one level up from /packaging) ---
pushd "%SCRIPT_DIR%.." >nul || (echo [ERROR] Could not cd to project root. & goto :FAIL_EARLY)
set "PROJ_DIR=%CD%"

set "VENV_DIR=%PROJ_DIR%\.venv"
set "PY_EXE=%VENV_DIR%\Scripts\python.exe"
set "REQFILE=%SCRIPT_DIR%requirements_packaging.txt"
set "SPECFILE=%PROJ_DIR%\packaging\pyinstaller\kenpo_tray.spec"

set "DATA_DIR=%PROJ_DIR%\data"
set "ANDROID_ASSETS=%PROJ_DIR%\..\KenpoFlashcardsProject-v2\app\src\main\assets"

REM --- Read version/build from version.json (supports either .version or .appVersion) ---
set "APP_VERSION=unknown"
set "BUILD_NUM="
for /f "usebackq tokens=1,2 delims=|" %%A in (`powershell -NoProfile -Command "$p=Join-Path '%PROJ_DIR%' 'version.json'; if(Test-Path $p){try{$j=Get-Content $p -Raw|ConvertFrom-Json; $v=($j.version); if(-not $v){$v=$j.appVersion}; $b=($j.build); if(-not $b){$b=$j.buildNumber}; if($v){Write-Output ($v + '|' + $b)}}catch{}}" 2^>nul`) do (
  if not "%%A"=="" set "APP_VERSION=%%A"
  if not "%%B"=="" set "BUILD_NUM=%%B"
)

REM --- Log file (always) ---
for /f %%i in ('powershell -NoProfile -Command "Get-Date -Format yyyyMMdd_HHmmss"') do set "STAMP=%%i"
set "LOG_DIR=%SCRIPT_DIR%logs"
if not exist "%LOG_DIR%" mkdir "%LOG_DIR%" >nul 2>nul
set "LOG_FILE=%LOG_DIR%\build_exe_%STAMP%_v%APP_VERSION%.log"

call :LOG "============================================================"
call :LOG " Advanced Flashcards WebApp Server - Build EXE"
call :LOG "============================================================"
call :LOG "Project root : %PROJ_DIR%"
call :LOG "Version      : %APP_VERSION%"
if defined BUILD_NUM call :LOG "Build        : %BUILD_NUM%"
call :LOG "Data dir     : %DATA_DIR%"
call :LOG "Spec file    : %SPECFILE%"
call :LOG "Log          : %LOG_FILE%"
call :LOG ""

echo.
echo ============================================================
echo  Build EXE  (v%APP_VERSION% %BUILD_NUM%)
echo ============================================================
echo [INFO] Log: %LOG_FILE%
echo.

REM --- Preflight ---
if not exist "%REQFILE%"  (call :FAIL "Missing requirements file: %REQFILE%")
if not exist "%SPECFILE%" (call :FAIL "Missing spec file: %SPECFILE%")
if not exist "%DATA_DIR%" (call :FAIL "Missing data directory: %DATA_DIR%")

REM --- Ensure kenpo_words.json is present in \data\ ---
if not exist "%DATA_DIR%\kenpo_words.json" (
  if exist "%ANDROID_ASSETS%\kenpo_words.json" (
    call :LOG "[INFO] kenpo_words.json missing in data - copying from Android assets"
    copy /y "%ANDROID_ASSETS%\kenpo_words.json" "%DATA_DIR%\kenpo_words.json" >>"%LOG_FILE%" 2>>&1
  )
)
if not exist "%DATA_DIR%\kenpo_words.json" (
  call :FAIL "kenpo_words.json not found. Expected: %DATA_DIR%\kenpo_words.json"
)

REM --- Clean build artifacts (ROOT build/dist) ---
call :STEP "Clean build artifacts (root\\build, root\\dist)"
if exist "%PROJ_DIR%\build" rmdir /s /q "%PROJ_DIR%\build" >>"%LOG_FILE%" 2>>&1
if exist "%PROJ_DIR%\dist"  rmdir /s /q "%PROJ_DIR%\dist"  >>"%LOG_FILE%" 2>>&1

REM --- Ensure venv in ROOT ---
call :STEP "Ensure virtualenv (root\\.venv)"
if not exist "%PY_EXE%" (
  call :LOG "[INFO] Creating venv at: %VENV_DIR%"
  call :FIND_SYS_PY
  if not defined SYS_PY call :FAIL "No system Python found (py/python). Install Python or enable the py launcher."
  cmd /c "%SYS_PY% -m venv \"%VENV_DIR%\"" >>"%LOG_FILE%" 2>>&1
)
if not exist "%PY_EXE%" (
  call :FAIL "Virtualenv python not found after venv creation: %PY_EXE%"
)

REM --- Provide data dir to spec (spec reads env var AFS_DATA_DIR) ---
set "AFS_DATA_DIR=%DATA_DIR%"

REM --- Install packaging deps (quiet console, detailed log) ---
call :STEP "Upgrade pip/setuptools/wheel"
cmd /c "\"%PY_EXE%\" -m pip --version" >>"%LOG_FILE%" 2>>&1
cmd /c "\"%PY_EXE%\" -m pip install --upgrade pip setuptools wheel" >>"%LOG_FILE%" 2>>&1
if errorlevel 1 call :FAIL "pip upgrade failed (see log)"

call :STEP "Install packaging requirements"
cmd /c "\"%PY_EXE%\" -m pip install -r \"%REQFILE%\"" >>"%LOG_FILE%" 2>>&1
if errorlevel 1 call :FAIL "pip install -r requirements_packaging.txt failed (see log)"

REM --- Build EXE (ROOT build/dist) ---
call :STEP "Run PyInstaller"
cmd /c "\"%PY_EXE%\" -m PyInstaller \"%SPECFILE%\" --noconfirm --clean --distpath \"%PROJ_DIR%\dist\" --workpath \"%PROJ_DIR%\build\"" >>"%LOG_FILE%" 2>>&1
if errorlevel 1 call :FAIL "PyInstaller failed (see log)"

call :LOG "[OK] build_exe completed"
call :LOG "Output: %PROJ_DIR%\\dist\\AdvancedFlashcardsWebAppServer\\AdvancedFlashcardsWebAppServer.exe"

echo.
echo [DONE] Build complete.
echo        Output: dist\AdvancedFlashcardsWebAppServer\AdvancedFlashcardsWebAppServer.exe
echo.

popd >nul
exit /b 0

REM ======================= helpers =======================

:STEP
set "_MSG=%~1"
echo [STEP] %_MSG%
call :LOG "[STEP] %_MSG%"
exit /b 0

:LOG
set "_L=%~1"
echo %_L%>>"%LOG_FILE%"
exit /b 0

:FIND_SYS_PY
set "SYS_PY="
where py >nul 2>nul && set "SYS_PY=py -3"
if not defined SYS_PY where python >nul 2>nul && set "SYS_PY=python"
if not defined SYS_PY where python3 >nul 2>nul && set "SYS_PY=python3"
exit /b 0

:FAIL
set "_E=%~1"
call :LOG "[FAIL] %_E%"
call :LOG "Log: %LOG_FILE%"

echo.
echo [FAIL] Build did not complete.
echo %_E%
echo Log: %LOG_FILE%
echo.

REM Print last ~50 lines for quick view
powershell -NoProfile -Command "if(Test-Path '%LOG_FILE%'){Get-Content '%LOG_FILE%' -Tail 50}" 2>nul

popd >nul
pause
exit /b 1

:FAIL_EARLY
pause
exit /b 1
