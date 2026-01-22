# Kenpo Flashcards Web Server (Packaged)

A Windows **installer** build of the Kenpo Flashcards Web Server + Tray Launcher.

- **Packaged Version:** **v1.1.0 (build 5)**
- **Bundled Web Server:** **v5.5.2 (build 29)**

## What you get

- **Windows installer (Inno Setup)** that installs to **Program Files** and adds Start Menu shortcuts.
- **Tray Launcher** (`KenpoFlashcardsTray.exe`) so you can start/stop the server from the system tray.
- **Local web UI** (Flashcards + Admin tools) accessible from your browser.

## What's new in v1.1.0 (build 5)

- **Fixed bundled data not loading** — On first run, the app now copies profiles, progress, API keys, breakdowns, and helper data from the bundled `_internal\data` folder to `%LOCALAPPDATA%\Kenpo Flashcards\data\`. This ensures user accounts and progress from the dev build are available in the installed app.

## What's new in v1.0.1 (build 4)

- **Pre-build data sync** — New `pre_build.bat` automatically copies data from your dev location before building.
- **Fixed PyInstaller path issues** — Version info, static files, and kenpo_words.json now load correctly in the packaged EXE.
- **Fixed jaraco dependency** — Resolved `ModuleNotFoundError: No module named 'jaraco'` that prevented tray from starting.
- **Improved APP_DIR resolution** — Properly detects PyInstaller frozen state for correct file paths.

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

The web server stores user data (accounts, progress, breakdowns) in:

```
%LOCALAPPDATA%\Kenpo Flashcards\data\
```

This is typically `C:\Users\<YourName>\AppData\Local\Kenpo Flashcards\data\`.

The bundled data in `Program Files` serves as the initial/default data on first run.

## Antivirus / Defender notes (PyInstaller false positives)

PyInstaller-built executables are sometimes flagged as "PUA" or suspicious, especially when unsigned.

If Defender quarantines files:

- Prefer building/installing from a non-system drive (e.g., `M:`) if your environment behaves better there.
- For production distribution, code-signing the installer/EXE reduces false positives.

## Build the installer yourself (from source)

From the project root:

1. **Run pre-build data sync** (copies data from dev location if available):
   ```
   packaging\pre_build.bat
   ```

2. **Build the PyInstaller EXE:**
   ```
   packaging\build_exe.bat
   ```

3. **Build the installer:**
   ```
   packaging\build_installer_inno.bat
   ```

### Data sources (pre_build.bat)

The pre-build script looks for data in this order:

1. **Dev location:** `C:\Users\Sidscri\Documents\GitHub\sidscri-apps\KenpoFlashcardsWebServer\data`
2. **Android project:** `C:\Users\Sidscri\Documents\GitHub\sidscri-apps\KenpoFlashcardsProject-v2\app\src\main\assets\kenpo_words.json`
3. **Fallback:** Uses existing `data\` folder in project root

Copied data goes to `build_data\` folder, which the build process uses if present.

### Build output

- Installer: `packaging\output\KenpoFlashcardsWebSetup.exe`
- EXE folder: `dist\KenpoFlashcardsTray\`

## Uninstall

Use:

- **Windows Settings → Apps → Installed apps → Kenpo Flashcards → Uninstall**

(If you want to preserve your progress, back up the `%LOCALAPPDATA%\Kenpo Flashcards\data\` folder before uninstalling.)
