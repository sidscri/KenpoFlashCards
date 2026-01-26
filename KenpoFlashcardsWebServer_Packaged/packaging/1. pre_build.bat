@echo off
setlocal EnableExtensions EnableDelayedExpansion

REM -----------------------------------------------------------------------------
REM Study Flashcards - Pre-Build Data Copy
REM Run this BEFORE 2. build_exe.bat to stage data into packaging\build_data
REM
REM Priority:
REM   1) %LOCALAPPDATA%\Study Flashcards\data   (preferred - real user data)
REM   2) project_root\data                      (fallback - fresh-used build)
REM
REM If LOCALAPPDATA data is used:
REM   - Backup current project_root\data to ..\DataBackups\
REM   - Replace project_root\data with the staged data
REM -----------------------------------------------------------------------------

set "SCRIPT_DIR=%~dp0"
set "PROJ_DIR=%SCRIPT_DIR%.."
set "BUILD_DATA_DIR=%SCRIPT_DIR%build_data"

set "APPDATA_ROOT=%LOCALAPPDATA%\Study Flashcards"
set "LOCAL_DATA=%APPDATA_ROOT%\data"

set "BACKUP_ROOT=%PROJ_DIR%\..\DataBackups"
if not exist "%BACKUP_ROOT%" mkdir "%BACKUP_ROOT%" 1>nul 2>nul

echo.
echo ============================================================
echo  Study Flashcards - Pre-Build Data Setup
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
  exit /b 1
)

REM If we used LOCALAPPDATA as source, backup and replace project_root\data too
if "%USING_LOCAL%"=="1" (
  echo.
  echo [INFO] Local data was found - backing up project_root\data before replacing...

  call :STAMP
  set "ZIP_PATH=%BACKUP_ROOT%\WebServer_Packaged_Data_!STAMP!.zip"

  call :ZIPFOLDER "%PROJ_DIR%\data" "!ZIP_PATH!"
  if errorlevel 1 (
    echo [WARN] Backup zip failed - continuing anyway.
  ) else (
    echo [OK] Backup created: !ZIP_PATH!
  )

  echo [INFO] Replacing project_root\data with staged build_data ...
  if exist "%PROJ_DIR%\data\" rmdir /s /q "%PROJ_DIR%\data"
  mkdir "%PROJ_DIR%\data" 1>nul 2>nul
  robocopy "%BUILD_DATA_DIR%" "%PROJ_DIR%\data" /MIR /R:1 /W:1 /NFL /NDL /NJH /NJS /NP
  set "RC=%ERRORLEVEL%"
  if %RC% GEQ 8 (
    echo [ERROR] robocopy failed while replacing project data (code %RC%).
    exit /b 1
  )
)

echo.
echo [DONE] build_data staged at: %BUILD_DATA_DIR%
echo.
pause
exit /b 0

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
exit /b 0

:ZIPFOLDER
set "SRC=%~1"
set "DST=%~2"
if not exist "%SRC%\" exit /b 0

REM Use PowerShell Compress-Archive
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$src='%SRC%'; $dst='%DST%'; " ^
  "if(Test-Path $dst){Remove-Item -Force $dst}; " ^
  "$parent=Split-Path $dst -Parent; if(!(Test-Path $parent)){New-Item -ItemType Directory -Force -Path $parent | Out-Null}; " ^
  "Compress-Archive -Path (Join-Path $src '*') -DestinationPath $dst -Force" >nul 2>&1

if errorlevel 1 exit /b 1
exit /b 0
