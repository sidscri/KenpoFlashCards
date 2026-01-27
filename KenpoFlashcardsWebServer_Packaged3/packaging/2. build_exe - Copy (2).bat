@echo off
setlocal EnableExtensions EnableDelayedExpansion

title Advanced Flashcards WebApp Server - Build EXE

rem ------------------------------------------------------------
rem Paths
rem ------------------------------------------------------------
set "SCRIPT_DIR=%~dp0"
set "ROOT=%SCRIPT_DIR%.."
for %%I in ("%ROOT%") do set "ROOT=%%~fI"
set "PACKAGING_DIR=%ROOT%\packaging"
set "LOG_DIR=%PACKAGING_DIR%\logs"
if not exist "%LOG_DIR%" mkdir "%LOG_DIR%" >nul 2>&1

rem ------------------------------------------------------------
rem Read version/build from version.json (repo root)
rem ------------------------------------------------------------
set "VERSION="
set "BUILD="
for /f "usebackq delims=" %%V in (`powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "try { $j = Get-Content -Raw '%ROOT%\version.json' | ConvertFrom-Json; '{0}' -f $j.version } catch { '' }"`) do set "VERSION=%%V"
for /f "usebackq delims=" %%B in (`powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "try { $j = Get-Content -Raw '%ROOT%\version.json' | ConvertFrom-Json; '{0}' -f $j.build } catch { '' }"`) do set "BUILD=%%B"
if "%VERSION%"=="" set "VERSION=unknown"
if "%BUILD%"=="" set "BUILD=unknown"

rem Timestamp for log file (YYYYMMDD_HHMMSS)
for /f %%t in ('powershell -NoProfile -ExecutionPolicy Bypass -Command "Get-Date -Format yyyyMMdd_HHmmss"') do set "TS=%%t"
set "LOG=%LOG_DIR%\build_exe_%TS%_v%VERSION%.log"

rem ------------------------------------------------------------
rem Header
rem ------------------------------------------------------------
echo ============================================================ 
echo  Advanced Flashcards WebApp Server - Build EXE
echo ============================================================ 
echo [INFO] Project root : %ROOT%
echo [INFO] Version      : %VERSION%
echo [INFO] Build        : %BUILD%
echo [INFO] Log          : %LOG%
echo.

> "%LOG%" echo [%date% %time%] PC=%COMPUTERNAME% USER=%USERNAME% VERSION=%VERSION% BUILD=%BUILD% MODE=build_exe
>>"%LOG%" echo [ROOT] %ROOT%
>>"%LOG%" echo.

rem ------------------------------------------------------------
rem Enforce that build/ dist/ .venv are in repo root
rem ------------------------------------------------------------
set "VENV_DIR=%ROOT%\.venv"
set "VENV_PY=%VENV_DIR%\Scripts\python.exe"

rem ------------------------------------------------------------
rem Preflight: ensure data files exist (do NOT move anything)
rem ------------------------------------------------------------
if not exist "%ROOT%\data\kenpo_words.json" (
  echo [WARN] data\kenpo_words.json not found. Build can still run, but app may be missing vocabulary data.
  >>"%LOG%" echo [WARN] data\kenpo_words.json not found.
)
if not exist "%ROOT%\data\helper.json" (
  echo [WARN] data\helper.json not found.
  >>"%LOG%" echo [WARN] data\helper.json not found.
)

rem ------------------------------------------------------------
rem Step 1: Ensure venv
rem ------------------------------------------------------------
echo [1/5] Ensuring virtualenv (.venv)...
>>"%LOG%" echo.
>>"%LOG%" echo [STEP 1] Ensure venv
if not exist "%VENV_PY%" (
  echo   Creating venv...
  >>"%LOG%" echo Creating venv at "%VENV_DIR%"
  call :TryCreateVenv
  if errorlevel 1 goto :FAIL
) else (
  >>"%LOG%" echo venv exists: "%VENV_PY%"
)

rem ------------------------------------------------------------
rem Step 2: Upgrade pip tooling
rem ------------------------------------------------------------
echo [2/5] Updating pip/setuptools/wheel...
>>"%LOG%" echo.
>>"%LOG%" echo [STEP 2] Upgrade pip/setuptools/wheel
"%VENV_PY%" -m pip install --disable-pip-version-check -U pip setuptools wheel >>"%LOG%" 2>&1
if errorlevel 1 goto :FAIL

rem ------------------------------------------------------------
rem Step 3: Install requirements
rem ------------------------------------------------------------
echo [3/5] Installing requirements...
>>"%LOG%" echo.
>>"%LOG%" echo [STEP 3] Install requirements
if exist "%ROOT%\requirements.txt" (
  "%VENV_PY%" -m pip install --disable-pip-version-check -r "%ROOT%\requirements.txt" >>"%LOG%" 2>&1
  if errorlevel 1 goto :FAIL
) else (
  >>"%LOG%" echo [WARN] requirements.txt not found (skipped).
)
if exist "%PACKAGING_DIR%\requirements_packaging.txt" (
  "%VENV_PY%" -m pip install --disable-pip-version-check -r "%PACKAGING_DIR%\requirements_packaging.txt" >>"%LOG%" 2>&1
  if errorlevel 1 goto :FAIL
) else (
  >>"%LOG%" echo [WARN] packaging\requirements_packaging.txt not found (skipped).
)

rem ------------------------------------------------------------
rem Step 4: Clean build artifacts (in repo root)
rem ------------------------------------------------------------
echo [4/5] Cleaning build artifacts...
>>"%LOG%" echo.
>>"%LOG%" echo [STEP 4] Clean build/dist
if exist "%ROOT%\build" rmdir /s /q "%ROOT%\build" >>"%LOG%" 2>&1
if exist "%ROOT%\dist"  rmdir /s /q "%ROOT%\dist"  >>"%LOG%" 2>&1
if exist "%ROOT%\__pycache__" rmdir /s /q "%ROOT%\__pycache__" >>"%LOG%" 2>&1

rem ------------------------------------------------------------
rem Step 5: PyInstaller build (from repo root)
rem ------------------------------------------------------------
set "SPEC=%PACKAGING_DIR%\pyinstaller\kenpo_tray.spec"
echo [5/5] Building EXE (PyInstaller)...
>>"%LOG%" echo.
>>"%LOG%" echo [STEP 5] PyInstaller
>>"%LOG%" echo Spec: "%SPEC%"
if not exist "%SPEC%" (
  echo [ERROR] Spec file not found: "%SPEC%"
  >>"%LOG%" echo [ERROR] Spec file not found: "%SPEC%"
  goto :FAIL
)

pushd "%ROOT%" >nul
"%VENV_PY%" -m PyInstaller "%SPEC%" --noconfirm --clean --distpath "%ROOT%\dist" --workpath "%ROOT%\build" >>"%LOG%" 2>&1
set "RC=%ERRORLEVEL%"
popd >nul
if not "%RC%"=="0" goto :FAIL

echo.
echo [OK] Build completed successfully.
echo      Output folder: %ROOT%\dist
>>"%LOG%" echo.
>>"%LOG%" echo [OK] Build completed successfully.
exit /b 0

rem ------------------------------------------------------------
rem Helpers
rem ------------------------------------------------------------
:TryCreateVenv
rem Prefer Python 3.8 if available; fall back to any py/python.
where py >nul 2>&1
if not errorlevel 1 (
  py -3.8 -m venv "%VENV_DIR%" >>"%LOG%" 2>&1
  if not errorlevel 1 goto :eof
  py -3 -m venv "%VENV_DIR%" >>"%LOG%" 2>&1
  if not errorlevel 1 goto :eof
)
where python >nul 2>&1
if not errorlevel 1 (
  python -m venv "%VENV_DIR%" >>"%LOG%" 2>&1
  if not errorlevel 1 goto :eof
)
echo [ERROR] Could not create virtualenv. Install Python (preferably 3.8) and ensure 'py' launcher is available.
>>"%LOG%" echo [ERROR] Could not create virtualenv.
exit /b 1

:FAIL
echo.
echo [FAIL] Build did not complete.
echo Log: %LOG%
echo.
echo Press any key to continue . . .
pause >nul
exit /b 1
