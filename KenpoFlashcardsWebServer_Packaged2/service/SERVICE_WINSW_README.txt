Advanced Flashcards WebApp Server - WinSW Windows Service Option
===============================================================
This build adds an optional "Windows Service (WinSW wrapper)" installer task.

What it does:
- Downloads WinSW (Windows Service Wrapper)
- Writes a service XML next to the wrapper EXE
- Installs AdvancedFlashcardsWebAppServer.exe --headless as a Windows Service
- Sets Auto-start and starts the service

Notes:
- Requires Administrator privileges.
- Service id/name: AdvancedFlashcardsWebAppServer
- Logs: %LOCALAPPDATA%\Advanced Flashcards WebApp Server\log\Advanced Flashcards WebApp Server logs\
