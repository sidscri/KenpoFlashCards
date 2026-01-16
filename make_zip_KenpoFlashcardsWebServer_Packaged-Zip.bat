@echo off
setlocal EnableExtensions

REM ============================================================
REM make_zip.bat (STAGING COPY METHOD - BULLETPROOF)
REM Put this BAT in: ...\sidscri-apps\
REM Creates ZIP in the SAME folder as this BAT:
REM   KenpoFlashcardsWebServer_Packaged_in_exe_msi-v<major.minor> v<build>.zip
REM ZIP contents root:
REM   KenpoFlashcardsWebServer_Packaged_in_exe_msi\...
REM Reads:
REM   .\KenpoFlashcardsWebServer_Packaged_in_exe_msi\version.json
REM Excludes (recursively, guaranteed):
REM   .venv, __pycache__, *.pyc
REM Writes log:
REM   logs\KenpoFlashcardsWebServer_Packaging.log
REM ============================================================

REM Always run from the BAT's folder
set "BASE=%~dp0"
if "%BASE:~-1%"=="\" set "BASE=%BASE:~0,-1%"
pushd "%BASE%" >nul

set "LOG=%BASE%\logs\KenpoFlashcardsWebServerPackaging.log"
> "%LOG%" echo START %DATE% %TIME%
>>"%LOG%" echo BASE=%BASE%

set "PROJ=KenpoFlashcardsWebServer_Packaged_in_exe_msi"
set "SRC=%BASE%\%PROJ%"
set "VERJSON=%SRC%\version.json"

>>"%LOG%" echo SRC=%SRC%
>>"%LOG%" echo VERJSON=%VERJSON%

if not exist "%SRC%\" (
  >>"%LOG%" echo ERROR: Project folder not found
  goto :fail
)

if not exist "%VERJSON%" (
  >>"%LOG%" echo ERROR: version.json not found
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

REM Parse version/build from JSON using findstr (no PowerShell)
set "VER="
set "BUILD="

for /f "tokens=1,* delims=:" %%A in ('findstr /i "\"version\"" "%VERJSON%"') do set "VER=%%B"
for /f "tokens=1,* delims=:" %%A in ('findstr /i "\"build\"" "%VERJSON%"') do set "BUILD=%%B"

REM Clean extracted values
set "VER=%VER:,=%"
set "VER=%VER:"=%"
set "VER=%VER: =%"
set "BUILD=%BUILD:,=%"
set "BUILD=%BUILD:"=%"
set "BUILD=%BUILD: =%"

>>"%LOG%" echo RAW_VER=%VER%
>>"%LOG%" echo RAW_BUILD=%BUILD%

if "%VER%"=="" (
  >>"%LOG%" echo ERROR: Could not parse version from version.json
  goto :fail
)

if "%BUILD%"=="" (
  >>"%LOG%" echo ERROR: Could not parse build from version.json
  goto :fail
)


set "ZIPNAME=%PROJ%-v%VER% v%BUILD%.zip"
set "ZIPPATH=%BASE%\%ZIPNAME%"
>>"%LOG%" echo ZIPPATH=%ZIPPATH%

if exist "%ZIPPATH%" del /f /q "%ZIPPATH%" >>"%LOG%" 2>&1

REM ---------- STAGING COPY ----------
set "STAGE=%BASE%\_zip_stage"
>>"%LOG%" echo STAGE=%STAGE%

if exist "%STAGE%" rmdir /s /q "%STAGE%" >>"%LOG%" 2>&1
mkdir "%STAGE%\%PROJ%" >>"%LOG%" 2>&1

REM Copy project to stage while excluding directories by NAME (recursive)
REM robocopy exit codes 0-7 = OK, >=8 = failure
robocopy "%SRC%" "%STAGE%\%PROJ%" /E /R:1 /W:1 /NFL /NDL /NP /NJH /NJS /XD ".venv" "__pycache__" >>"%LOG%" 2>&1
set "RC=%ERRORLEVEL%"
>>"%LOG%" echo ROBOCOPY_EXIT_CODE=%RC%
if %RC% GEQ 8 (
  >>"%LOG%" echo ERROR: robocopy failed
  goto :fail_cleanup
)

REM Remove any stray .pyc files (just in case)
del /s /q "%STAGE%\%PROJ%\*.pyc" >>"%LOG%" 2>&1

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

REM Verify .venv did not slip in (log only)
"%SEVENZIP%" l "%ZIPPATH%" | findstr /i "\.venv" >nul 2>&1
if "%ERRORLEVEL%"=="0" (
  >>"%LOG%" echo WARNING: .venv paths detected in zip listing
) else (
  >>"%LOG%" echo Verified: no .venv paths detected
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
REM Try to cleanup stage folder on failure
if exist "%STAGE%" rmdir /s /q "%STAGE%" >>"%LOG%" 2>&1

:fail
echo FAILED. See log: "%LOG%"
popd >nul
exit /b 1
