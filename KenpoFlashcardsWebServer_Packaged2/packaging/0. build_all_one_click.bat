@echo off
setlocal EnableExtensions EnableDelayedExpansion

rem ============================================================
rem 0. build_all_one_click.bat  (keep in \packaging\)
rem Step scripts live in \packaging\one_click\
rem Logs + build_data remain in \packaging\
rem ============================================================

set "PACK_DIR=%~dp0"
if "%PACK_DIR:~-1%"=="\" set "PACK_DIR=%PACK_DIR:~0,-1%"

set "ONECLICK_DIR=%PACK_DIR%\one_click"
set "LOG_DIR=%PACK_DIR%\logs"
if not exist "%LOG_DIR%" mkdir "%LOG_DIR%" >nul 2>&1

for /f %%i in ('powershell -NoProfile -Command "Get-Date -Format yyyyMMdd_HHmmss"') do set "TS=%%i"
if not defined TS set "TS=%date:~-4%%date:~4,2%%date:~7,2%_%time:~0,2%%time:~3,2%%time:~6,2%"
set "TS=%TS: =0%"

set "MASTER_LOG=%LOG_DIR%\build_all_%TS%.log"

call :log "============================================================"
call :log "ONE CLICK BUILD started: %date% %time%"
call :log "Packaging folder: %PACK_DIR%\"
call :log "One-click folder: %ONECLICK_DIR%\"
call :log "Master log     : %MASTER_LOG%"
call :log "============================================================"
echo.

call :progress 0 "Starting..."

rem STEP 1
call :progress 10 "STEP 1/3: Pre Build of Build_Data"
call :log ""
call :log "[STEP 1] Pre Build started: %date% %time%"

if not exist "%ONECLICK_DIR%\1. pre_build_one_click.bat" (
  call :log "[ERROR] Missing: %ONECLICK_DIR%\1. pre_build_one_click.bat"
  call :progress 100 "FAILED at pre_build (missing file)"
  goto :fail
)

call "%ONECLICK_DIR%\1. pre_build_one_click.bat" --nopause
set "RC=%ERRORLEVEL%"
call :log "[STEP 1] ExitCode=%RC%"
if not "%RC%"=="0" (
  call :log "[STEP 1] FAILED"
  call :progress 100 "FAILED at pre_build"
  goto :fail
)

call :log "[STEP 1] Pre Build complete: %date% %time%"
call :progress 33 "Pre Build complete"

rem STEP 2
call :progress 40 "STEP 2/3: Build EXE"
call :log ""
call :log "[STEP 2] Build EXE started: %date% %time%"

if not exist "%ONECLICK_DIR%\2. build_exe_one_click.bat" (
  call :log "[ERROR] Missing: %ONECLICK_DIR%\2. build_exe_one_click.bat"
  call :progress 100 "FAILED at build_exe (missing file)"
  goto :fail
)

call "%ONECLICK_DIR%\2. build_exe_one_click.bat" __RUN
set "RC=%ERRORLEVEL%"
call :log "[STEP 2] ExitCode=%RC%"
if not "%RC%"=="0" (
  call :log "[STEP 2] FAILED"
  call :progress 100 "FAILED at build_exe"
  goto :fail
)

call :log "[STEP 2] Build EXE complete: %date% %time%"
call :progress 66 "Build EXE complete"

rem STEP 3
call :progress 75 "STEP 3/3: Build Installer (Inno)"
call :log ""
call :log "[STEP 3] Build Installer started: %date% %time%"

if not exist "%ONECLICK_DIR%\3. build_installer_inno_one_click.bat" (
  call :log "[ERROR] Missing: %ONECLICK_DIR%\3. build_installer_inno_one_click.bat"
  call :progress 100 "FAILED at Inno (missing file)"
  goto :fail
)

call "%ONECLICK_DIR%\3. build_installer_inno_one_click.bat" --nopause
set "RC=%ERRORLEVEL%"
call :log "[STEP 3] ExitCode=%RC%"
if not "%RC%"=="0" (
  call :log "[STEP 3] FAILED"
  call :progress 100 "FAILED at Inno installer"
  goto :fail
)

call :log "[STEP 3] Build Installer complete: %date% %time%"
call :progress 100 "ALL DONE"

call :log ""
call :log "ONE CLICK BUILD SUCCESS: %date% %time%"
echo.
echo Build finished successfully.
echo Master log: "%MASTER_LOG%"
exit /b 0

:fail
call :log ""
call :log "ONE CLICK BUILD FAILED: %date% %time%"
echo.
echo Build FAILED.
echo Master log: "%MASTER_LOG%"
exit /b 1

:log
rem Write a line to master log; support true blank lines without printing "ECHO is off."
if "%~1"=="" (
  >> "%MASTER_LOG%" echo(
) else (
  >> "%MASTER_LOG%" echo %~1
)
exit /b 0

:progress
set /a PCT=%~1
set "MSG=%~2"
set /a FILLED=PCT/3
set "BAR="
for /l %%i in (1,1,33) do (
  if %%i LEQ !FILLED! (set "BAR=!BAR!#") else set "BAR=!BAR!-"
)
echo [!BAR!] !PCT!%% - !MSG!
>> "%MASTER_LOG%" echo [PROGRESS] !PCT!%% - !MSG!
exit /b 0
