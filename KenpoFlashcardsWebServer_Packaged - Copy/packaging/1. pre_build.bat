@echo off
setlocal EnableExtensions EnableDelayedExpansion

REM Optional: run with --nopause to avoid pausing (for automation)
set "NO_PAUSE=0"
for %%A in (%*) do if /I "%%~A"=="--nopause" set "NO_PAUSE=1"
set "EXITCODE=0"


REM -----------------------------------------------------------------------------
REM Advanced Flashcards WebApp Server - Pre-Build Data Copy
REM Run this BEFORE 2. build_exe.bat to stage data into packaging\build_data
REM
REM Priority:
REM   1) %LOCALAPPDATA%\Advanced Flashcards WebApp Server\data   (preferred - real user data)
REM   2) project_root\data                      (fallback - fresh-used build)
REM
REM If LOCALAPPDATA data is used:
REM   - Backup current project_root\data to ..\DataBackups\
REM   - Replace project_root\data with the staged data
REM -----------------------------------------------------------------------------

set "SCRIPT_DIR=%~dp0"
set "PROJ_DIR=%SCRIPT_DIR%.."
set "BUILD_DATA_DIR=%SCRIPT_DIR%build_data"

set "APPDATA_ROOT=%LOCALAPPDATA%\Advanced Flashcards WebApp Server"
set "LOCAL_DATA=%APPDATA_ROOT%\data"

set "LOCAL_LOG_DIR=%APPDATA_ROOT%\log\Advanced Flashcards WebApp Server logs"
set "LOCAL_LOG_FILE=%LOCAL_LOG_DIR%\builder.log"

set "BACKUP_ROOT=%PROJ_DIR%\..\DataBackups"
if not exist "%BACKUP_ROOT%" mkdir "%BACKUP_ROOT%" 1>nul 2>nul

echo.
echo ============================================================
echo  Advanced Flashcards WebApp Server - Pre-Build Data Setup
echo ============================================================
echo.
echo [INFO] Project root : %PROJ_DIR%
echo [INFO] Build data   : %BUILD_DATA_DIR%
echo [INFO] Local data   : %LOCAL_DATA%
echo.

REM Decide source
set "SRC_DATA=%PROJ_DIR%\data"
set "USING_LOCAL=0"
if exist "%LOCAL_DATA%\" (
  set "SRC_DATA=%LOCAL_DATA%"
  set "USING_LOCAL=1"
)

echo [INFO] Source data  : %SRC_DATA%
call :LOG_LOCAL "pre_build source=%SRC_DATA% USING_LOCAL=%USING_LOCAL%"
echo.

REM Ensure build_data exists (clean)
if exist "%BUILD_DATA_DIR%\" rmdir /s /q "%BUILD_DATA_DIR%"
mkdir "%BUILD_DATA_DIR%" 1>nul 2>nul

REM Mirror copy into packaging\build_data
echo [INFO] Staging data into packaging\build_data ...
robocopy "%SRC_DATA%" "%BUILD_DATA_DIR%" /MIR /R:1 /W:1 /NFL /NDL /NJH /NJS /NP
set "RC=%ERRORLEVEL%"
if %RC% GEQ 8 (
  echo [ERROR] robocopy failed (code %RC%). Aborting.
set "EXITCODE=1" & goto :LOG_LOCAL
if not exist "%LOCAL_LOG_DIR%" mkdir "%LOCAL_LOG_DIR%" 1>nul 2>nul
echo [%date% %time%] PC=%COMPUTERNAME% USER=%USERNAME% - %~1>>"%LOCAL_LOG_FILE%"
exit /b 0

:END
)


REM If we used LOCALAPPDATA as source, write a flag so the builder can seed the next package
set "FLAG_FILE=%BUILD_DATA_DIR%\.from_local.flag"
set "SOURCE_FILE=%BUILD_DATA_DIR%\_data_source.txt"
if "%USING_LOCAL%"=="1" (
  echo [INFO] Local data was used; writing build_data source flag...
  > "%FLAG_FILE%" echo FROM_LOCAL=1
  > "%SOURCE_FILE%" echo SOURCE=%LOCAL_DATA%
  >> "%SOURCE_FILE%" echo TIMESTAMP=%DATE% %TIME%
) else (
  if exist "%BUILD_DATA_DIR%\.from_local.flag" del /q "%BUILD_DATA_DIR%\.from_local.flag" >nul 2>&1
  if exist "%BUILD_DATA_DIR%\_data_source.txt" del /q "%BUILD_DATA_DIR%\_data_source.txt" >nul 2>&1
  echo [INFO] Local data not found; build_data will be treated as NOT from local.
)

REM If we used LOCALAPPDATA as source, write a flag so the builder can seed the next package
set "FLAG_FILE=%BUILD_DATA_DIR%\.from_local.flag"
set "SOURCE_FILE=%BUILD_DATA_DIR%\_data_source.txt"
if "%USING_LOCAL%"=="1" (
  echo [INFO] Local data was used; writing build_data source flag...
  > "%FLAG_FILE%" echo FROM_LOCAL=1
  > "%SOURCE_FILE%" echo SOURCE=%LOCAL_DATA%
  >> "%SOURCE_FILE%" echo TIMESTAMP=%DATE% %TIME%
) else (
  if exist "%BUILD_DATA_DIR%\.from_local.flag" del /q "%BUILD_DATA_DIR%\.from_local.flag" >nul 2>&1
  if exist "%BUILD_DATA_DIR%\_data_source.txt" del /q "%BUILD_DATA_DIR%\_data_source.txt" >nul 2>&1
  echo [INFO] Local data not found; build_data will be treated as NOT from local.
)

:STAMP
for /f "tokens=1-3 delims=/- " %%a in ("%date%") do (
  set "d1=%%a"
  set "d2=%%b"
  set "d3=%%c"
)
for /f "tokens=1-3 delims=:." %%a in ("%time%") do (
  set "t1=%%a"
  set "t2=%%b"
  set "t3=%%c"
)
set "t1=!t1: =0!"
REM Produce YYYYMMDD_HHMMSS-ish without locale assumptions by using powershell
for /f %%i in ('powershell -NoProfile -Command "Get-Date -Format yyyyMMdd_HHmmss"') do set "STAMP=%%i"
set "EXITCODE=0" & goto :LOG_LOCAL
if not exist "%LOCAL_LOG_DIR%" mkdir "%LOCAL_LOG_DIR%" 1>nul 2>nul
echo [%date% %time%] PC=%COMPUTERNAME% USER=%USERNAME% - %~1>>"%LOCAL_LOG_FILE%"
exit /b 0

:END

:ZIPFOLDER
set "SRC=%~1"
set "DST=%~2"
if not exist "%SRC%\" set "EXITCODE=0" & goto :LOG_LOCAL
if not exist "%LOCAL_LOG_DIR%" mkdir "%LOCAL_LOG_DIR%" 1>nul 2>nul
echo [%date% %time%] PC=%COMPUTERNAME% USER=%USERNAME% - %~1>>"%LOCAL_LOG_FILE%"
exit /b 0

:END

REM Use PowerShell Compress-Archive
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$src='%SRC%'; $dst='%DST%'; " ^
  "if(Test-Path $dst){Remove-Item -Force $dst}; " ^
  "$parent=Split-Path $dst -Parent; if(!(Test-Path $parent)){New-Item -ItemType Directory -Force -Path $parent | Out-Null}; " ^
  "Compress-Archive -Path (Join-Path $src '*') -DestinationPath $dst -Force" >nul 2>&1

if errorlevel 1 set "EXITCODE=1" & goto :LOG_LOCAL
if not exist "%LOCAL_LOG_DIR%" mkdir "%LOCAL_LOG_DIR%" 1>nul 2>nul
echo [%date% %time%] PC=%COMPUTERNAME% USER=%USERNAME% - %~1>>"%LOCAL_LOG_FILE%"
exit /b 0

:END
set "EXITCODE=0" & goto :LOG_LOCAL
if not exist "%LOCAL_LOG_DIR%" mkdir "%LOCAL_LOG_DIR%" 1>nul 2>nul
echo [%date% %time%] PC=%COMPUTERNAME% USER=%USERNAME% - %~1>>"%LOCAL_LOG_FILE%"
exit /b 0

:END

:LOG_LOCAL
if not exist "%LOCAL_LOG_DIR%" mkdir "%LOCAL_LOG_DIR%" 1>nul 2>nul
echo [%date% %time%] PC=%COMPUTERNAME% USER=%USERNAME% - %~1>>"%LOCAL_LOG_FILE%"
exit /b 0

:END
echo.
if %EXITCODE% NEQ 0 (
  echo [ERROR] Pre-build finished with errors. ExitCode=%EXITCODE%
) else (
  echo [OK] Pre-build completed successfully.
)
echo.
if "%NO_PAUSE%"=="0" pause
exit /b %EXITCODE%