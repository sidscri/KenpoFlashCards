@echo off
setlocal EnableExtensions

REM -----------------------------------------------------------------------------
REM Kenpo Flashcards - Build EXE (PyInstaller one-folder)
REM Run this from the repo root:
REM   M:\KenpoFlashcardsWebServer_Packaged> .\packaging\build_exe.bat
REM -----------------------------------------------------------------------------

set "SCRIPT_DIR=%~dp0"
set "PROJ_DIR=%SCRIPT_DIR%.."
pushd "%PROJ_DIR%" || (echo [ERROR] Could not cd to project root. & exit /b 1)

set "VENV_DIR=%PROJ_DIR%\.venv"
set "PY_EXE=%VENV_DIR%\Scripts\python.exe"
set "REQFILE=%SCRIPT_DIR%requirements_packaging.txt"
set "SPECFILE=%PROJ_DIR%\packaging\pyinstaller\kenpo_tray.spec"

call :KILL_LOCKS

REM Always build from a clean slate
call :CLEAN_ARTIFACTS

call :ENSURE_VENV || goto :FAIL

if not exist "%REQFILE%" (
  echo [ERROR] Missing requirements file: "%REQFILE%"
  goto :FAIL
)

echo [INFO] Installing packaging requirements (no pip self-upgrade)...
REM IMPORTANT: Upgrading pip inside a fresh venv on some systems can fail with WinError 5
REM when pip.exe is being replaced. Avoid upgrading pip; just ensure setuptools/wheel are new.
"%PY_EXE%" -m pip install --upgrade "setuptools>=75" "wheel>=0.45"
if errorlevel 1 (
  echo [WARN] Failed to upgrade setuptools/wheel.
  call :START_FRESH_PROMPT || goto :FAIL
  goto :RETRY
)

"%PY_EXE%" -m pip install -r "%REQFILE%"
if errorlevel 1 (
  echo [ERROR] pip install failed.
  call :START_FRESH_PROMPT || goto :FAIL
  goto :RETRY
)

echo [INFO] Building EXE with PyInstaller...
"%PY_EXE%" -m PyInstaller "%SPECFILE%" --noconfirm --clean
if errorlevel 1 (
  echo [ERROR] PyInstaller build failed.
  call :START_FRESH_PROMPT || goto :FAIL
  goto :RETRY
)

echo.
echo [DONE] Build complete.
echo       Output: dist\KenpoFlashcardsTray\KenpoFlashcardsTray.exe
popd
pause
exit /b 0

:RETRY
echo.
echo [INFO] Retrying build from a clean slate...
call :CLEAN_ARTIFACTS
call :ENSURE_VENV || goto :FAIL

"%PY_EXE%" -m pip install --upgrade "setuptools>=75" "wheel>=0.45"
if errorlevel 1 goto :FAIL

"%PY_EXE%" -m pip install -r "%REQFILE%"
if errorlevel 1 goto :FAIL

"%PY_EXE%" -m PyInstaller "%SPECFILE%" --noconfirm --clean
if errorlevel 1 goto :FAIL

echo.
echo [DONE] Build complete (after retry).
echo       Output: dist\KenpoFlashcardsTray\KenpoFlashcardsTray.exe
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
taskkill /F /IM KenpoFlashcardsTray.exe >nul 2>&1
exit /b 0

:FAIL
echo.
echo [FAILED] Build did not complete.
echo If you see "Access is denied" on pip.exe:
echo   - Close any terminals that have the venv activated
echo   - Re-run this bat from the repo root as Administrator once (optional)
popd
pause
exit /b 1
