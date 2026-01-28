@echo off
setlocal EnableExtensions EnableDelayedExpansion

REM =============================================================================
REM 1. pre_build_one_click.bat  (lives in \packaging\one_click\)
REM
REM PATHS:
REM   - build_data: \packaging\build_data   (stays here)
REM   - logs     : \packaging\logs
REM
REM Optional:
REM   --nopause
REM =============================================================================

set "NO_PAUSE=0"
for %%A in (%*) do if /I "%%~A"=="--nopause" set "NO_PAUSE=1"

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

set "BUILD_DATA_DIR=%PACK_DIR%\build_data"
set "FLAG_FILE=%BUILD_DATA_DIR%\.from_local.flag"

set "APPDATA_ROOT=%LOCALAPPDATA%\Advanced Flashcards WebApp Server"
set "LOCAL_DATA=%APPDATA_ROOT%\data"
set "LOCAL_LOG_DIR=%APPDATA_ROOT%\log\Advanced Flashcards WebApp Server logs"
set "LOCAL_LOG_FILE=%LOCAL_LOG_DIR%\builder.log"
set "ROOT_DATA=%PROJ_DIR%\data"

REM --- logging under packaging\logs
set "LOG_DIR=%PACK_DIR%\logs"
if not exist "%LOG_DIR%" mkdir "%LOG_DIR%" >nul 2>&1
for /f %%i in ('powershell -NoProfile -Command "Get-Date -Format yyyyMMdd_HHmmss"') do set "TS=%%i"
if not defined TS set "TS=%date:~-4%%date:~4,2%%date:~7,2%_%time:~0,2%%time:~3,2%%time:~6,2%"
set "TS=%TS: =0%"
set "LOGFILE=%LOG_DIR%\pre_build_%TS%.log"

set "EXITCODE=0"
set "SOURCE_DATA="

call :log "============================================================"
call :log "PRE BUILD started: %date% %time%"
call :log "Project root : %PROJ_DIR%"
call :log "Packaging    : %PACK_DIR%"
call :log "Build data   : %BUILD_DATA_DIR%"
call :log "Local data   : %LOCAL_DATA%"
call :log "Log file     : %LOGFILE%"
call :log "============================================================"

REM --- pick source
if exist "%LOCAL_DATA%\" (
  set "SOURCE_DATA=%LOCAL_DATA%"
  call :log "[INFO] Using LOCALAPPDATA data"
) else (
  set "SOURCE_DATA=%ROOT_DATA%"
  call :log "[WARN] LOCALAPPDATA data not found; using project data"
)

if not exist "%SOURCE_DATA%\" (
  call :log "[ERROR] Source data folder not found: %SOURCE_DATA%"
  set "EXITCODE=1"
  goto :DONE
)

REM --- ensure build_data exists
if not exist "%BUILD_DATA_DIR%\" mkdir "%BUILD_DATA_DIR%" >nul 2>&1
if exist "%FLAG_FILE%" del /f /q "%FLAG_FILE%" >nul 2>&1

REM --- mirror copy with robocopy (exit codes: <8 success, >=8 failure)
call :log "[INFO] robocopy \"%SOURCE_DATA%\" \"%BUILD_DATA_DIR%\" /MIR"
echo.>>"%LOGFILE%"
robocopy "%SOURCE_DATA%" "%BUILD_DATA_DIR%" /MIR /R:1 /W:1 /NFL /NDL /NP /NJH /NJS >> "%LOGFILE%" 2>&1
set "RC=%ERRORLEVEL%"
call :log "[INFO] robocopy exit=%RC%"

if %RC% GEQ 8 (
  call :log "[ERROR] robocopy failed (exit=%RC%)"
  set "EXITCODE=1"
  goto :DONE
)

REM --- mark local usage for build_exe
if /I "%SOURCE_DATA%"=="%LOCAL_DATA%" (
  echo from_local>"%FLAG_FILE%"
  call :log "[INFO] Wrote flag: %FLAG_FILE%"
)

call :log "[OK] Data staged successfully."

:DONE
REM --- local builder.log append (best-effort)
if not exist "%LOCAL_LOG_DIR%" mkdir "%LOCAL_LOG_DIR%" >nul 2>&1
>>"%LOCAL_LOG_FILE%" echo [%date% %time%] PC=%COMPUTERNAME% USER=%USERNAME% pre_build exit=%EXITCODE% source="%SOURCE_DATA%"

echo.
if %EXITCODE% NEQ 0 (
  echo [ERROR] Pre-build finished with errors. ExitCode=%EXITCODE%
) else (
  echo [OK] Pre-build completed successfully.
)
echo [INFO] Log: "%LOGFILE%"
echo.

if "%NO_PAUSE%"=="0" pause
exit /b %EXITCODE%

:log
>> "%LOGFILE%" echo %~1
echo %~1
exit /b 0
