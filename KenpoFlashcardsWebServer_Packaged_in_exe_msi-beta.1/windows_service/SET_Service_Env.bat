@echo off
setlocal enabledelayedexpansion

REM Run as Administrator
REM Sets/updates environment variables for the NSSM service.

set SCRIPT_DIR=%~dp0
if not exist "%SCRIPT_DIR%\nssm.exe" (
  echo [ERROR] nssm.exe not found in %SCRIPT_DIR%
  exit /b 1
)

set SVC=KenpoFlashcardsWeb

echo.
echo ===== KenpoFlashcardsWeb service env =====
echo Leave blank to keep existing value.
echo.

set /p OPENAI_API_KEY=OPENAI_API_KEY (required for AI autofill) =
set /p OPENAI_MODEL=OPENAI_MODEL (default gpt-4o-mini) =
set /p KENPO_WEB_PORT=KENPO_WEB_PORT (default 8009) =
set /p KENPO_WORDS_JSON=KENPO_WORDS_JSON (optional full path to kenpo_words.json) =

REM Build AppEnvironmentExtra lines (NSSM replaces the whole set)
REM We'll read the current env set is not easy in batch, so we set common defaults and overwrite if provided.
set ENVLINE=KENPO_WEB_PORT=8009 OPENAI_MODEL=gpt-4o-mini

if not "%KENPO_WEB_PORT%"=="" set ENVLINE=!ENVLINE! KENPO_WEB_PORT=%KENPO_WEB_PORT%
if not "%OPENAI_MODEL%"=="" set ENVLINE=!ENVLINE! OPENAI_MODEL=%OPENAI_MODEL%
if not "%OPENAI_API_KEY%"=="" set ENVLINE=!ENVLINE! OPENAI_API_KEY=%OPENAI_API_KEY%
if not "%KENPO_WORDS_JSON%"=="" set ENVLINE=!ENVLINE! KENPO_WORDS_JSON=%KENPO_WORDS_JSON%

echo [INFO] Setting environment variables on service...
"%SCRIPT_DIR%\nssm.exe" set %SVC% AppEnvironmentExtra "%ENVLINE%"

echo [INFO] Restarting service...
"%SCRIPT_DIR%\nssm.exe" restart %SVC%

echo [DONE]
pause
