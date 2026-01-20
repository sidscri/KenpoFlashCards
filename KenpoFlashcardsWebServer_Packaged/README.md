# Kenpo Flashcards Web Server (Packaged)

A Windows **installer** build of the Kenpo Flashcards Web Server + Tray Launcher.

- **Packaged Version:** **v1.0.0 (build 3)**
- **Bundled Web Server:** **v5.5.2 (build 29)**

## What you get

- **Windows installer (Inno Setup)** that installs to **Program Files** and adds Start Menu shortcuts.
- **Tray Launcher** (`KenpoFlashcardsTray.exe`) so you can start/stop the server from the system tray.
- **Local web UI** (Flashcards + Admin tools) accessible from your browser.

## What’s new in v1.0.0 (build 3)

This is the first “real” release (moving from beta packaging to a stable installer) and includes the largest feature jump so far:

- **AI Access page** for managing API keys and selecting models (no longer requires editing a batch file).
- **Encrypted API key storage** (keys stored in `data/api_keys.enc`).
- **Shared Key Mode** option (one key can be shared by all authenticated users).
- **Improved Sync logic** (merge by `updated_at`, queued updates/offline friendliness).
- **Admin pages & reporting** (About/Admin/User Guide pages and PDF generation).

## Install (recommended)

1. On the target PC, run the installer:
   - `KenpoFlashcardsWebSetup.exe`
2. If Windows SmartScreen prompts:
   - Click **More info** → **Run anyway** (expected for unsigned installers).
3. After install, use:
   - **Start Menu → Kenpo Flashcards** → **Kenpo Flashcards** (Tray Launcher)

## Run / Use

1. Start the Tray Launcher.
2. In the tray icon menu, click **Open Web App** (or open your browser and go to the local address shown by the tray app).

## Where your data is stored

The web server uses a `data/` folder for user accounts, progress, breakdowns, and admin “source of truth” files.

**Important:** If you install under **Program Files**, Windows can block apps from writing inside that folder unless elevated.

Recommended options:

- **Option A (recommended):** Install to a user-writable folder (like `C:\KenpoFlashcards\`) when prompted.
- **Option B:** Run the tray app **once as Administrator** so it can create the initial `data/` folder.

After the folder exists, you typically won’t need elevation.

## Antivirus / Defender notes (PyInstaller false positives)

PyInstaller-built executables are sometimes flagged as “PUA” or suspicious, especially when unsigned.

If Defender quarantines files:

- Prefer building/installing from a non-system drive (e.g., `M:`) if your environment behaves better there.
- For production distribution, code-signing the installer/EXE reduces false positives.

## Build the installer yourself (from source)

From the project root:

1. Build the PyInstaller EXE:
   - `packaging\build_exe.bat`
2. Build the installer:
   - `packaging\build_installer_inno.bat`

The Inno Setup script is:

- `packaging\installer_inno.iss`

Output installer is typically placed in:

- `packaging\output\KenpoFlashcardsWebSetup.exe`

## Uninstall

Use:

- **Windows Settings → Apps → Installed apps → Kenpo Flashcards → Uninstall**

(If you want to preserve your progress, back up the `data/` folder before uninstalling.)
