@echo off
REM ============================================================
REM  KenpoFlashcards Web Server to Packaged Upgrade Tool
REM ============================================================
REM
REM  This tool safely updates the Packaged project from the 
REM  main Web Server project without damaging packaging files.
REM
REM  PROTECTED (never modified):
REM    - packaging/ folder (PyInstaller, Inno Setup)
REM    - windows_service/ folder
REM    - windows_tray/ folder
REM    - server_config.json
REM    - *.ico icons
REM    - *.lnk shortcuts
REM    - build_data/ folder
REM
REM  SYNCED (updated from web server):
REM    - app.py
REM    - static/ folder  
REM    - requirements.txt
REM    - CHANGELOG.md
REM    - data/ folder (merged, not replaced)
REM
REM ============================================================

setlocal enabledelayedexpansion

echo.
echo ============================================================
echo   KenpoFlashcards Web Server -^> Packaged Upgrade Tool
echo ============================================================
echo.

REM Check if Python is available
python --version >nul 2>&1
if errorlevel 1 (
    echo ERROR: Python is not installed or not in PATH
    echo Please install Python 3.8+ and try again.
    pause
    exit /b 1
)

REM Get script directory
set "SCRIPT_DIR=%~dp0"

REM Check for command line arguments
if "%~1"=="" (
    echo Usage: upgrade_webserver.bat ^<webserver_zip_or_folder^> [packaged_folder]
    echo.
    echo Examples:
    echo   upgrade_webserver.bat KenpoFlashcardsWebServer-v6_1_0.zip
    echo   upgrade_webserver.bat ..\KenpoFlashcardsWebServer\
    echo   upgrade_webserver.bat webserver.zip ..\Packaged\
    echo.
    
    REM Interactive mode - ask for paths
    set /p "SOURCE=Enter path to web server zip or folder: "
    if "!SOURCE!"=="" (
        echo No source provided. Exiting.
        pause
        exit /b 1
    )
) else (
    set "SOURCE=%~1"
)

REM Set destination (default to parent directory's Packaged folder or current)
if "%~2"=="" (
    if exist "%SCRIPT_DIR%..\KenpoFlashcardsWebServer_Packaged" (
        set "DEST=%SCRIPT_DIR%..\KenpoFlashcardsWebServer_Packaged"
    ) else if exist "%SCRIPT_DIR%KenpoFlashcardsWebServer_Packaged" (
        set "DEST=%SCRIPT_DIR%KenpoFlashcardsWebServer_Packaged"
    ) else (
        set "DEST=%CD%"
    )
) else (
    set "DEST=%~2"
)

echo Source: %SOURCE%
echo Destination: %DEST%
echo.

REM Confirm before proceeding
echo This will update the Packaged project with files from the Web Server.
echo Packaging files will NOT be modified.
echo A backup will be created before any changes.
echo.
set /p "CONFIRM=Continue? (Y/N): "
if /i not "%CONFIRM%"=="Y" (
    echo Cancelled.
    pause
    exit /b 0
)

echo.
echo Running upgrade...
echo.

REM Run the Python script
python "%SCRIPT_DIR%upgrade_webserver_to_packaged.py" "%SOURCE%" "%DEST%"

if errorlevel 1 (
    echo.
    echo ERROR: Upgrade failed. Check the output above for details.
) else (
    echo.
    echo SUCCESS: Upgrade completed.
    echo.
    echo Next steps:
    echo   1. Test the packaged project
    echo   2. Run: packaging\2. build_exe.bat
    echo   3. Run: packaging\3. build_installer_inno.bat
)

echo.
pause
