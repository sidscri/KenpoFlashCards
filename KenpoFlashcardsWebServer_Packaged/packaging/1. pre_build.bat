@echo off
setlocal EnableExtensions

REM -----------------------------------------------------------------------------
REM Kenpo Flashcards - Pre-Build Data Copy
REM Run this BEFORE build_exe.bat to pull latest data from dev location
REM
REM Priority:
REM   1) C:\Users\Sidscri\Documents\GitHub\sidscri-apps\KenpoFlashcardsWebServer\data
REM   2) If not found, uses existing data in project root\data (fresh build)
REM -----------------------------------------------------------------------------

set "SCRIPT_DIR=%~dp0"
set "PROJ_DIR=%SCRIPT_DIR%.."
set "BUILD_DATA_DIR=%PROJ_DIR%\build_data"

REM Source locations
set "DEV_DATA=C:\Users\Sidscri\Documents\GitHub\sidscri-apps\KenpoFlashcardsWebServer\data"
set "DEV_KENPO_JSON=C:\Users\Sidscri\Documents\GitHub\sidscri-apps\KenpoFlashcardsProject-v2\app\src\main\assets\kenpo_words.json"

echo.
echo ============================================================
echo  Kenpo Flashcards - Pre-Build Data Setup
echo ============================================================
echo.

REM Check if dev data location exists
if exist "%DEV_DATA%" (
    echo [FOUND] Dev data location: %DEV_DATA%
    echo [INFO] Copying data to build_data folder...
    
    REM Clean and recreate build_data folder
    if exist "%BUILD_DATA_DIR%" rmdir /s /q "%BUILD_DATA_DIR%"
    mkdir "%BUILD_DATA_DIR%"
    
    REM Copy all data files and folders
    xcopy "%DEV_DATA%\*" "%BUILD_DATA_DIR%\" /E /I /Y /Q
    
    if errorlevel 1 (
        echo [ERROR] Failed to copy data files!
        goto :FAIL
    )
    
    echo [OK] Copied data files to build_data\
    
) else (
    echo [INFO] Dev data location not found: %DEV_DATA%
    echo [INFO] Will use existing data\ folder for build (fresh build mode)
)

REM Check for kenpo_words.json from Android project
if exist "%DEV_KENPO_JSON%" (
    echo [FOUND] kenpo_words.json at: %DEV_KENPO_JSON%
    
    REM Ensure build_data exists
    if not exist "%BUILD_DATA_DIR%" mkdir "%BUILD_DATA_DIR%"
    
    copy /Y "%DEV_KENPO_JSON%" "%BUILD_DATA_DIR%\kenpo_words.json" >nul
    
    if errorlevel 1 (
        echo [ERROR] Failed to copy kenpo_words.json!
        goto :FAIL
    )
    
    echo [OK] Copied kenpo_words.json to build_data\
    
) else (
    echo [WARN] kenpo_words.json not found at: %DEV_KENPO_JSON%
    echo        Make sure it exists in build_data\ or data\ before building!
)

echo.
echo ============================================================
echo  Pre-build complete!
echo ============================================================
echo.

if exist "%BUILD_DATA_DIR%" (
    echo [INFO] Build will use: build_data\ folder
    dir /b "%BUILD_DATA_DIR%"
) else (
    echo [INFO] Build will use: data\ folder (fallback)
)

echo.
echo Next step: Run build_exe.bat
echo.
pause
exit /b 0

:FAIL
echo.
echo [FAILED] Pre-build did not complete successfully.
pause
exit /b 1
