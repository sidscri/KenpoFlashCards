Advanced Flashcards WebApp Server - NSSM Windows Service Option
==============================================================
This build adds an optional "Windows Service (NSSM wrapper)" installer task.

What it does:
- Downloads NSSM (Non-Sucking Service Manager)
- Installs AdvancedFlashcardsWebAppServer.exe --headless as a Windows Service
- Sets Auto-start and starts the service

Notes:
- Requires Administrator privileges.
- Service name: AdvancedFlashcardsWebAppServer
- Logs: %LOCALAPPDATA%\Advanced Flashcards WebApp Server\log\Advanced Flashcards WebApp Server logs\
