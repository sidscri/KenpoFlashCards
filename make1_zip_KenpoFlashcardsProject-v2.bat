@echo off
setlocal EnableExtensions

REM ============================================================
REM KenpoFlashcardsProject-v2.bat (STAGING COPY METHOD - BULLETPROOF)
REM Put this BAT in: ...\sidscri-apps\
REM Creates ZIP in the SAME folder as this BAT:
REM   KenpoFlashcardsProject-v<versionName> v<versionCode>.zip
REM ZIP contents root:
REM   KenpoFlashcardsProject-v2\...
REM Also ensures/renames a fixes file inside KenpoFlashcardsProject-v2:
REM   Fixes v<versionName> v<versionCode>.txt
REM Reads version from:
REM   .\KenpoFlashcardsProject-v2\app\build.gradle
REM Excludes (recommended for Android projects):
REM   .git, .idea, .gradle, build, .cxx, local.properties, *.iml
REM Writes log:
REM   logs\KenpoFlashcardsProject-v2.log
REM ============================================================

REM Always run from the BAT's folder (sidscri-apps)
set "BASE=%~dp0"
if "%BASE:~-1%"=="\" set "BASE=%BASE:~0,-1%"
pushd "%BASE%" >nul

set "LOG=%BASE%\logs\KenpoFlashcardsProject-v2.log"
> "%LOG%" echo START %DATE% %TIME%
>>"%LOG%" echo BASE=%BASE%

set "PROJ=KenpoFlashcardsProject-v2"
set "SRC=%BASE%\%PROJ%"
set "GRADLE=%SRC%\app\build.gradle"

>>"%LOG%" echo SRC=%SRC%
>>"%LOG%" echo GRADLE=%GRADLE%

if not exist "%SRC%\" (
  >>"%LOG%" echo ERROR: Project folder not found
  goto :fail
)

if not exist "%GRADLE%" (
  >>"%LOG%" echo ERROR: app\build.gradle not found
  goto :fail
)

REM Find 7-Zip
set "SEVENZIP=%ProgramFiles%\7-Zip\7z.exe"
if not exist "%SEVENZIP%" set "SEVENZIP=%ProgramFiles(x86)%\7-Zip\7z.exe"
>>"%LOG%" echo SEVENZIP=%SEVENZIP%

if not exist "%SEVENZIP%" (
  >>"%LOG%" echo ERROR: 7z.exe not found. Install 7-Zip.
  goto :fail
)

REM -------------------------
REM Parse versionCode (integer) and versionName (string) from build.gradle
REM -------------------------
set "VCODE="
set "VNAME="

REM versionCode 17
for /f "tokens=2" %%A in ('findstr /R /C:"^[ ]*versionCode[ ]*[0-9][0-9]*" "%GRADLE%"') do (
  set "VCODE=%%A"
  goto :got_vcode
)
:got_vcode

REM versionName "4.1"  (also supports single quotes)
for /f "tokens=2" %%A in ('findstr /R /C:"^[ ]*versionName[ ]*\".*\"" "%GRADLE%"') do (
  set "VNAME=%%A"
  goto :got_vname
)
for /f "tokens=2" %%A in ('findstr /R /C:"^[ ]*versionName[ ]*'.*'" "%GRADLE%"') do (
  set "VNAME=%%A"
  goto :got_vname
)
:got_vname

REM Clean VNAME (strip quotes)
set "VNAME=%VNAME:"=%"
set "VNAME=%VNAME:'=%"

>>"%LOG%" echo PARSED_versionCode=%VCODE%
>>"%LOG%" echo PARSED_versionName=%VNAME%

if "%VCODE%"=="" (
  >>"%LOG%" echo ERROR: Could not parse versionCode
  goto :fail
)

if "%VNAME%"=="" (
  >>"%LOG%" echo ERROR: Could not parse versionName
  goto :fail
)

set "ZIPNAME=KenpoFlashcardsProject-v%VNAME% v%VCODE%.zip"
set "ZIPPATH=%BASE%\%ZIPNAME%"

set "FIXES_NAME=Fixes v%VNAME% v%VCODE%.txt"

>>"%LOG%" echo ZIPPATH=%ZIPPATH%
>>"%LOG%" echo FIXES_NAME=%FIXES_NAME%

if exist "%ZIPPATH%" del /f /q "%ZIPPATH%" >>"%LOG%" 2>&1

REM ---------- STAGING COPY ----------
set "STAGE=%BASE%\_zip_stage_kfproject"
>>"%LOG%" echo STAGE=%STAGE%

if exist "%STAGE%" rmdir /s /q "%STAGE%" >>"%LOG%" 2>&1
mkdir "%STAGE%\%PROJ%" >>"%LOG%" 2>&1

REM Copy project to stage while excluding common generated/IDE folders by NAME
REM robocopy exit codes 0-7 = OK, >=8 = failure
robocopy "%SRC%" "%STAGE%\%PROJ%" /E /R:1 /W:1 /NFL /NDL /NP /NJH /NJS ^
  /XD ".git" ".idea" ".gradle" "build" ".cxx" >>"%LOG%" 2>&1

set "RC=%ERRORLEVEL%"
>>"%LOG%" echo ROBOCOPY_EXIT_CODE=%RC%
if %RC% GEQ 8 (
  >>"%LOG%" echo ERROR: robocopy failed
  goto :fail_cleanup
)

REM Remove files that should not be shared
del /s /q "%STAGE%\%PROJ%\*.iml" >>"%LOG%" 2>&1
if exist "%STAGE%\%PROJ%\local.properties" del /f /q "%STAGE%\%PROJ%\local.properties" >>"%LOG%" 2>&1

REM ---------- Ensure/rename Fixes file ----------
set "FIXES_PATH=%STAGE%\%PROJ%\%FIXES_NAME%"

REM If a fixes file exists with a different version/build name, rename it to the correct name
set "FOUND_FIXES="
for %%F in ("%STAGE%\%PROJ%\Fixes v*.txt") do (
  set "FOUND_FIXES=%%~fF"
  goto :have_fixes
)

:have_fixes
if defined FOUND_FIXES (
  if /I not "%FOUND_FIXES%"=="%FIXES_PATH%" (
    >>"%LOG%" echo Renaming fixes file: "%FOUND_FIXES%" ^> "%FIXES_PATH%"
    del /f /q "%FIXES_PATH%" >>"%LOG%" 2>&1
    ren "%FOUND_FIXES%" "%FIXES_NAME%" >>"%LOG%" 2>&1
  ) else (
    >>"%LOG%" echo Fixes file already correct: "%FIXES_PATH%"
  )
) else (
  >>"%LOG%" echo Creating fixes file: "%FIXES_PATH%"
  > "%FIXES_PATH%" echo Fixes for v%VNAME% (versionCode %VCODE%)
  >>"%FIXES_PATH%" echo Created: %DATE% %TIME%
  >>"%FIXES_PATH%" echo.
)

REM ---------- ZIP FROM STAGE ----------
>>"%LOG%" echo Running 7z (from stage)...
pushd "%STAGE%" >nul

"%SEVENZIP%" a -tzip "%ZIPPATH%" "%PROJ%" -r >>"%LOG%" 2>&1
set "ZERR=%ERRORLEVEL%"
popd >nul

>>"%LOG%" echo 7Z_EXIT_CODE=%ZERR%

if not "%ZERR%"=="0" (
  >>"%LOG%" echo ERROR: 7-Zip failed
  goto :fail_cleanup
)

REM Cleanup stage
rmdir /s /q "%STAGE%" >>"%LOG%" 2>&1

if not exist "%ZIPPATH%" (
  >>"%LOG%" echo ERROR: Zip not created
  goto :fail
)

>>"%LOG%" echo SUCCESS
echo DONE: "%ZIPPATH%"
popd >nul
exit /b 0

:fail_cleanup
if exist "%STAGE%" rmdir /s /q "%STAGE%" >>"%LOG%" 2>&1

:fail
echo FAILED. See log: "%LOG%"
popd >nul
exit /b 1
