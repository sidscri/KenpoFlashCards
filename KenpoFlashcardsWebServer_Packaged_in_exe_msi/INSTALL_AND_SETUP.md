# Kenpo Flashcards Web – Windows Install & Setup (Sonarr-style)

This package is designed to install and run like Sonarr/Radarr:
- **Writable data** lives under **ProgramData**
- **Binaries** live under a `bin` folder
- Optional **tray** app for easy launch + browser open
- Optional **service-friendly** server EXE for 24/7 background running

## Install locations
The installer places everything here:

`C:\ProgramData\KenpoFlashcardsWebServer\`

```
C:\ProgramData\KenpoFlashcardsWebServer\
  bin\
    KenpoFlashcardsTray.exe
    KenpoFlashcardsWebServer.exe
    static\
    assets\
  data\
    profiles.json
    breakdowns.json
    admin_users.json
    users\
  logs\
```

> On uninstall, **user data is kept** (Sonarr-style). Only `logs\` is removed.

## Quick start (Tray mode)
1. Run the installer `KenpoFlashcardsWebSetup.exe`.
2. (Optional) select **Start the Tray app with Windows** during install.
3. Launch **Kenpo Flashcards Web** from Start Menu.
4. Your UI is at: `http://127.0.0.1:8009`

### Change port/host
Set environment variables (System Properties → Environment Variables):
- `KENPO_WEB_PORT` (default `8009`)
- `KENPO_HOST` (default `127.0.0.1`)

Restart the tray/server after changing.

## Run like Sonarr (Service mode)
Windows services cannot directly run a normal EXE without a wrapper. The most common Sonarr-style approach is **NSSM**.

### Using NSSM
1. Install NSSM (or download it and place `nssm.exe` somewhere in PATH).
2. Run an elevated Command Prompt:

```bat
nssm install KenpoFlashcardsWeb "C:\ProgramData\KenpoFlashcardsWebServer\bin\KenpoFlashcardsWebServer.exe"
nssm set KenpoFlashcardsWeb AppDirectory "C:\ProgramData\KenpoFlashcardsWebServer\bin"
nssm set KenpoFlashcardsWeb Start SERVICE_AUTO_START
nssm set KenpoFlashcardsWeb AppStdout "C:\ProgramData\KenpoFlashcardsWebServer\logs\service_stdout.log"
nssm set KenpoFlashcardsWeb AppStderr "C:\ProgramData\KenpoFlashcardsWebServer\logs\service_stderr.log"
nssm start KenpoFlashcardsWeb
```

3. Browse: `http://127.0.0.1:8009`

### Firewall note
If you want LAN access, allow inbound on your chosen port (default 8009).

## Building locally (sync latest + build installer)
From the packaged project:

- **One-click:** `tools\UpdatePackageFromLatest.cmd`
  - Syncs the newest `KenpoFlashcardsWebServer` into the packaged project
  - Builds Tray EXE + Server EXE
  - Builds the Inno installer

### Manual build
```bat
cd KenpoFlashcardsWebServer_Packaged_in_exe_msi
packaging\build_exe.bat
packaging\build_installer_inno.bat
```

Outputs:
- Tray EXE: `dist\KenpoFlashcardsTray\KenpoFlashcardsTray.exe`
- Server EXE: `dist\KenpoFlashcardsWebServer\KenpoFlashcardsWebServer.exe`
- Installer: `packaging\output\KenpoFlashcardsWebSetup.exe`

## Building on GitHub Actions
Use the workflow:
`.github/workflows/build-windows-packaging-exe-msi.yml`

Artifacts uploaded:
- `KenpoFlashcardsTray`
- `KenpoFlashcardsWebServer`
- `KenpoFlashcardsWebSetup` (installer EXE)
