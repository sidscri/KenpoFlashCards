@echo off
REM ============================================================
REM  KenpoFlashcards Web Server → Packaged Sync Tool
REM  Syncs web server updates and auto-updates documentation
REM ============================================================
setlocal

REM Get the directory where this script is located (tools folder)
set TOOLS_DIR=%~dp0
set TOOLS_DIR=%TOOLS_DIR:~0,-1%

REM Default paths (sibling folders to tools)
set DEFAULT_WEBSERVER=%TOOLS_DIR%\..\KenpoFlashcardsWebServer
set DEFAULT_PACKAGED=%TOOLS_DIR%\..\KenpoFlashcardsWebServer_Packaged

REM Check if Python is available
where python >nul 2>&1
if %errorlevel% neq 0 (
    echo.
    echo ERROR: Python not found in PATH
    echo Please install Python 3.8+ and add it to your PATH
    pause
    exit /b 1
)

REM Parse arguments
set WEBSERVER_FOLDER=
set PACKAGED_FOLDER=
set DRY_RUN=
set OUTPUT_MODE=

:parse_args
if "%~1"=="" goto done_parsing
if "%~1"=="--dry-run" (
    set DRY_RUN=--dry-run
    shift
    goto parse_args
)
if "%~1"=="-n" (
    set DRY_RUN=--dry-run
    shift
    goto parse_args
)
if "%~1"=="--synced" (
    set OUTPUT_MODE=--output synced
    shift
    goto parse_args
)
if "%~1"=="--inplace" (
    set OUTPUT_MODE=--output inplace
    shift
    goto parse_args
)
if "%~1"=="--output" (
    if "%~2"=="" goto done_parsing
    set OUTPUT_MODE=--output %~2
    shift
    shift
    goto parse_args
)
if "%WEBSERVER_FOLDER%"=="" (
    set WEBSERVER_FOLDER=%~1
    shift
    goto parse_args
)
if "%PACKAGED_FOLDER%"=="" (
    set PACKAGED_FOLDER=%~1
    shift
    goto parse_args
)
shift
goto parse_args
:done_parsing

REM Use defaults if not specified
if "%WEBSERVER_FOLDER%"=="" set WEBSERVER_FOLDER=%DEFAULT_WEBSERVER%
if "%PACKAGED_FOLDER%"=="" set PACKAGED_FOLDER=%DEFAULT_PACKAGED%

REM Show help if --help
if "%~1"=="--help" goto show_help
if "%~1"=="-h" goto show_help

REM Check if source folder exists
if not exist "%WEBSERVER_FOLDER%" (
    echo.
    echo ERROR: Web server folder not found: %WEBSERVER_FOLDER%
    echo.
    goto show_help
)

REM Check if source has version.json (valid web server project)
if not exist "%WEBSERVER_FOLDER%\version.json" (
    echo.
    echo ERROR: Not a valid web server project folder
    echo        Missing version.json in: %WEBSERVER_FOLDER%
    pause
    exit /b 1
)

REM Check if destination folder exists
if not exist "%PACKAGED_FOLDER%" (
    echo.
    echo ERROR: Packaged folder not found: %PACKAGED_FOLDER%
    echo.
    goto show_help
)

REM Run the Python sync tool
echo.
echo ============================================================
echo  Starting Sync...
echo ============================================================
echo.
echo  Source:      %WEBSERVER_FOLDER%
echo  Destination: %PACKAGED_FOLDER%
echo  Backups:     %TOOLS_DIR%\sync_backups\
echo.
python "%TOOLS_DIR%\sync_webserver_to_packaged.py" "%WEBSERVER_FOLDER%" "%PACKAGED_FOLDER%" %DRY_RUN% %OUTPUT_MODE%

if %errorlevel% neq 0 (
    echo.
    echo Sync failed with error code %errorlevel%
    pause
    exit /b %errorlevel%
)

echo.
pause
exit /b 0

:show_help
echo.
echo ============================================================
echo  KenpoFlashcards Web Server to Packaged Sync Tool
echo ============================================================
echo.
echo Usage: sync_webserver.bat [webserver_folder] [packaged_folder] [options]
echo.
echo Arguments (optional - uses defaults if not provided):
echo   webserver_folder - Path to KenpoFlashcardsWebServer project
echo   packaged_folder  - Path to KenpoFlashcardsWebServer_Packaged project
echo.
echo Defaults:
echo   webserver_folder = ..\KenpoFlashcardsWebServer
echo   packaged_folder  = ..\KenpoFlashcardsWebServer_Packaged
echo.
echo Options:
echo   --dry-run, -n    Preview changes without applying them
echo   --help, -h       Show this help
echo.
echo Examples:
echo   sync_webserver.bat                          (use defaults)
echo   sync_webserver.bat --dry-run                (preview with defaults)
echo   sync_webserver.bat ..\MyWebServer           (custom source)
echo   sync_webserver.bat ..\WebServer ..\Packaged (custom both)
echo.
echo Backups are stored in: %TOOLS_DIR%\sync_backups\
echo.
pause
exit /b 0