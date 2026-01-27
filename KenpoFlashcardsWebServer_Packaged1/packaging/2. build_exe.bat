@echo off
setlocal EnableExtensions EnableDelayedExpansion

REM =============================================================================
REM Advanced Flashcards WebApp Server - 2. build_exe.bat
REM
REM Data selection
REM   - Prefer packaging\build_data when it exists AND is newer than root\data.
REM   - Otherwise, fall back to root\data.
REM   - This selection is passed into PyInstaller via env var:
REM       AFS_DATA_DIR
REM
REM Logs
REM   - Repo log:
REM       <repo_base>\logs\Install\updates\build_exe_<stamp>.log   (if build_data used)
REM       <repo_base>\logs\Install\installs\build_exe_<stamp>.log  (if root\data used)
REM   - Local log:
REM       %LOCALAPPDATA%\Advanced Flashcards WebApp Server\log\Advanced Flashcards WebApp Server logs\builder.log
REM =============================================================================

set "SCRIPT_DIR=%~dp0"
set "PROJ_DIR=%SCRIPT_DIR%.."
pushd "%PROJ_DIR%" || (echo [ERROR] Could not cd to project root. & exit /b 1)

set "VENV_DIR=%PROJ_DIR%\.venv"
set "PY_EXE=%VENV_DIR%\Scripts\python.exe"
set "REQFILE=%SCRIPT_DIR%requirements_packaging.txt"
set "SPECFILE=%PROJ_DIR%\packaging\pyinstaller\kenpo_tray.spec"

set "ROOT_DATA=%PROJ_DIR%\data"
set "BUILD_DATA=%SCRIPT_DIR%build_data"

set "APPDATA_ROOT=%LOCALAPPDATA%\Advanced Flashcards WebApp Server"
set "LOCAL_LOG_DIR=%APPDATA_ROOT%\log\Advanced Flashcards WebApp Server logs"
set "LOCAL_LOG_FILE=%LOCAL_LOG_DIR%\builder.log"


set "REPO_BASE=%PROJ_DIR%\.."
set "REPO_LOG_BASE=%REPO_BASE%\logs\Install"

call :STAMP
set "STAMP=%STAMP%"

REM Determine version (best-effort)
set "APP_VERSION=unknown"
for /f "usebackq tokens=* delims=" %%v in (`powershell -NoProfile -Command ^
  "$p = Join-Path '%PROJ_DIR%' 'version.json'; if(Test-Path $p){ (Get-Content $p -Raw | ConvertFrom-Json).version }" 2^>nul`) do set "APP_VERSION=%%v"

REM Decide which data dir to bundle
set "DATA_DIR=%ROOT_DATA%"
set "MODE=install"
set "FLAG_FILE=%BUILD_DATA%\.from_local.flag"

REM Use build_data ONLY when it is flagged as pulled from LOCALAPPDATA in pre_build
if exist "%BUILD_DATA%\" (
  if exist "%FLAG_FILE%" (
    set "DATA_DIR=%BUILD_DATA%"
    set "MODE=update"
  )
)

REM Prepare logs
if /i "%MODE%"=="update" (
  call :ENSURE_DIR "%REPO_LOG_BASE%\updates"
  set "REPO_LOG=%REPO_LOG_BASE%\updates\build_exe_%STAMP%.log"
) else (
  call :ENSURE_DIR "%REPO_LOG_BASE%\installs"
  set "REPO_LOG=%REPO_LOG_BASE%\installs\build_exe_%STAMP%.log"
)

call :LOG_REPO "%REPO_LOG%" "PC=%COMPUTERNAME% USER=%USERNAME% VERSION=%APP_VERSION% MODE=%MODE%"
call :LOG_REPO "%REPO_LOG%" "DATA_DIR=%DATA_DIR%"
call :LOG_LOCAL "Start build_exe MODE=%MODE% DATA_DIR=%DATA_DIR%"

echo.
echo ============================================================
echo  Advanced Flashcards WebApp Server - Build EXE
echo ============================================================
echo [INFO] Project root : %PROJ_DIR%
echo [INFO] Version      : %APP_VERSION%
echo [INFO] Data source  : %DATA_DIR%
echo [INFO] Spec file    : %SPECFILE%
echo [INFO] Log          : %REPO_LOG%
echo.

set "AFS_DATA_DIR=%DATA_DIR%"

call :KILL_LOCKS

REM Always build from a clean slate
call :CLEAN_ARTIFACTS

call :ENSURE_VENV || goto :FAIL

if not exist "%REQFILE%" (
  echo [ERROR] Missing requirements file: "%REQFILE%"
  call :LOG_REPO "%REPO_LOG%" "ERROR missing requirements file: %REQFILE%"
  goto :FAIL
)

echo [INFO] Installing packaging requirements (no pip self-upgrade)...
"%PY_EXE%" -m pip install --upgrade "setuptools>=75" "wheel>=0.45" >>"%REPO_LOG%" 2>&1
if errorlevel 1 (
  echo [WARN] Failed to upgrade setuptools/wheel.
  call :LOG_REPO "%REPO_LOG%" "WARN setuptools/wheel upgrade failed."
  call :START_FRESH_PROMPT || goto :FAIL
  goto :RETRY
)

"%PY_EXE%" -m pip install -r "%REQFILE%" >>"%REPO_LOG%" 2>&1
if errorlevel 1 (
  echo [ERROR] pip install failed.
  call :LOG_REPO "%REPO_LOG%" "ERROR pip install -r failed."
  call :START_FRESH_PROMPT || goto :FAIL
  goto :RETRY
)

echo [INFO] Building EXE with PyInstaller...
"%PY_EXE%" -m PyInstaller "%SPECFILE%" --noconfirm --clean >>"%REPO_LOG%" 2>&1
if errorlevel 1 (
  echo [ERROR] PyInstaller build failed.
  call :LOG_REPO "%REPO_LOG%" "ERROR PyInstaller failed."
  call :START_FRESH_PROMPT || goto :FAIL
  goto :RETRY
)

REM -----------------------------------------------------------------------------
REM Step 3: After a successful build, if build_data was sourced from LOCALAPPDATA,
REM backup root\data then replace it from packaging\build_data so the next package
REM seeds new installs with the most current data.
REM -----------------------------------------------------------------------------
if /i "%MODE%"=="update" (
  echo [INFO] Post-build: updating root\data from packaging\build_data (flagged from LOCALAPPDATA)...
  call :STAMP
  set "BACKUP_BASE=%REPO_BASE%\DataBackups\WebServer_Packaged_Data_%STAMP%"
  call :ENSURE_DIR "%BACKUP_BASE%"
  call :ZIPFOLDER "%ROOT_DATA%" "%BACKUP_BASE%\data.zip"
  if errorlevel 1 (
    echo [WARN] Backup zip failed (continuing).
    call :LOG_REPO "%REPO_LOG%" "WARN Post-build backup failed: %BACKUP_BASE%\data.zip"
  ) else (
    call :LOG_REPO "%REPO_LOG%" "Post-build backup created: %BACKUP_BASE%\data.zip"
  )
  REM Replace root data from build_data
  robocopy "%BUILD_DATA%" "%ROOT_DATA%" /MIR /R:1 /W:1 /NFL /NDL /NJH /NJS /NP >>"%REPO_LOG%" 2>&1
  set "RC2=%ERRORLEVEL%"
  if %RC2% GEQ 8 (
    echo [ERROR] Post-build data replace failed (code %RC2%).
    call :LOG_REPO "%REPO_LOG%" "ERROR Post-build data replace failed code=%RC2%"
  ) else (
    echo [OK] root\data updated from packaging\build_data.
    call :LOG_REPO "%REPO_LOG%" "OK Post-build root\data updated from build_data."
  )
)

echo.
echo [DONE] Build complete.
echo       Output: dist\AdvancedFlashcardsWebAppServer\AdvancedFlashcardsWebAppServer.exe
call :LOG_LOCAL "Build complete OK."
popd
pause
exit /b 0

:RETRY
echo.
echo [INFO] Retrying build from a clean slate...
call :LOG_REPO "%REPO_LOG%" "Retrying after fresh start."
call :CLEAN_ARTIFACTS
call :ENSURE_VENV || goto :FAIL

"%PY_EXE%" -m pip install --upgrade "setuptools>=75" "wheel>=0.45" >>"%REPO_LOG%" 2>&1
if errorlevel 1 goto :FAIL

"%PY_EXE%" -m pip install -r "%REQFILE%" >>"%REPO_LOG%" 2>&1
if errorlevel 1 goto :FAIL

"%PY_EXE%" -m PyInstaller "%SPECFILE%" --noconfirm --clean >>"%REPO_LOG%" 2>&1
if errorlevel 1 goto :FAIL

echo.
echo [DONE] Build complete (after retry).
echo       Output: dist\AdvancedFlashcardsWebAppServer\AdvancedFlashcardsWebAppServer.exe
call :LOG_LOCAL "Build complete OK (after retry)."
popd
pause
exit /b 0

:ENSURE_VENV
echo [INFO] Creating/using venv...
if not exist "%PY_EXE%" (
  py -3 -m venv "%VENV_DIR%"
  if errorlevel 1 (
    echo [ERROR] Failed to create venv.
    exit /b 1
  )
)
exit /b 0

:CLEAN_ARTIFACTS
echo [INFO] Cleaning build artifacts...
if exist "%VENV_DIR%" rmdir /s /q "%VENV_DIR%" >nul 2>&1
if exist "%PROJ_DIR%\build" rmdir /s /q "%PROJ_DIR%\build" >nul 2>&1
if exist "%PROJ_DIR%\dist"  rmdir /s /q "%PROJ_DIR%\dist"  >nul 2>&1
exit /b 0

:START_FRESH_PROMPT
echo.
echo [PROMPT] A dependency/build step failed.
echo          I can "start fresh" (delete .venv, build, dist) and retry automatically.
choice /C YN /N /M "Start fresh and retry? [Y/N] "
if errorlevel 2 exit /b 1
exit /b 0

:KILL_LOCKS
REM Kill common processes that can hold files open (pip.exe/python.exe or running tray)
taskkill /F /IM python.exe >nul 2>&1
taskkill /F /IM AdvancedFlashcardsWebAppServer.exe >nul 2>&1
exit /b 0

:FAIL
echo.
echo [FAILED] Build did not complete.
call :LOG_LOCAL "FAILED build_exe."
echo If you see "Access is denied" on pip.exe:
echo   - Close any terminals that have the venv activated
echo   - Re-run this bat from the repo root as Administrator once (optional)
popd
pause
exit /b 1

:STAMP
for /f %%i in ('powershell -NoProfile -Command "Get-Date -Format yyyyMMdd_HHmmss"') do set "STAMP=%%i"
exit /b 0

:ENSURE_DIR
if not exist "%~1" mkdir "%~1" 1>nul 2>nul
exit /b 0

:LOG_REPO
set "LF=%~1"
set "MSG=%~2"
echo [%date% %time%] %MSG%>>"%LF%"
exit /b 0

:LOG_LOCAL
call :ENSURE_DIR "%LOCAL_LOG_DIR%"
echo [%date% %time%] PC=%COMPUTERNAME% USER=%USERNAME% VERSION=%APP_VERSION% - %~1>>"%LOCAL_LOG_FILE%"
exit /b 0

:NEWEST_TICKS
REM Usage: call :NEWEST_TICKS "<dir>" VAROUT
set "D=%~1"
set "V=%~2"
for /f "usebackq delims=" %%t in (`powershell -NoProfile -Command ^
  "$d='%D%'; if(Test-Path $d){ $f = Get-ChildItem -Path $d -Recurse -File -ErrorAction SilentlyContinue | Sort-Object LastWriteTimeUtc -Descending | Select -First 1; if($f){ $f.LastWriteTimeUtc.ToFileTimeUtc() } }"`) do set "%V%=%%t"
exit /b 0
