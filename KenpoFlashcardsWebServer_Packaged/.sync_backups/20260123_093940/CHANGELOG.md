# Changelog — Kenpo Flashcards Web Server (Packaged)

All notable changes to the Windows packaged/installer distribution are documented here.

## v1.1.1 (build 6) — 2026-01-22

### Added
- **Configurable host/port binding** via `server_config.json`:
  - `host`: Set to `"0.0.0.0"` (all IPv4 interfaces), `"::"` (all interfaces including IPv6), `"127.0.0.1"` (localhost only), or a specific IP like `"192.168.0.129"` for Tailscale/LAN access
  - `port`: Default 8009, change if needed
  - `browser_url`: The URL opened in your browser (e.g., `"http://192.168.0.129:8009"` for remote access)
  - `open_browser`: Set to `false` to disable auto-opening browser on startup
- **System tray menu additions**:
  - "Server Info" - Shows current host, port, and config file location
  - "Edit Settings" - Opens `server_config.json` in your default editor
  - "Open Data Folder" - Opens the Kenpo Flashcards data folder
- Config file is auto-created in `%LOCALAPPDATA%\Kenpo Flashcards\server_config.json` on first run

### Changed
- Default host binding changed from `127.0.0.1` to `0.0.0.0` for easier LAN/Tailscale access
- Tray icon tooltip now shows current host:port binding
- Updated tray icon image

## v1.1.0 (build 5) — 2026-01-22

### Fixed
- **Bundled data not loading** — Added initial data seeding that copies profiles, progress, API keys, breakdowns, and helper data from the bundled `_internal\data` folder to `%LOCALAPPDATA%\Kenpo Flashcards\data\` on first run. This ensures user accounts and progress from the dev build are available in the installed app.

## v1.0.1 (build 4) — 2026-01-22

### Added
- **`pre_build.bat`** — New script that copies data from dev location before building:
  - Copies from `C:\Users\Sidscri\Documents\GitHub\sidscri-apps\KenpoFlashcardsWebServer\data`
  - Copies `kenpo_words.json` from Android project assets
  - Creates `build_data\` folder for build process to use
- **Spec file data priority** — Build now checks `build_data\` first, falls back to `data\`

### Fixed
- **`ModuleNotFoundError: No module named 'jaraco'`** — Added explicit jaraco dependencies to requirements and comprehensive hidden imports to spec file
- **`NameError: name 'APP_DIR' is not defined`** — Removed duplicate APP_DIR definition that was overwriting the PyInstaller-aware one
- **Version showing "unknown (build unknown)"** — Fixed VERSION_FILE path to use APP_DIR instead of app.root_path
- **Static files not loading** — Fixed Flask static_folder to use explicit APP_DIR path
- **kenpo_words.json not found** — Now properly bundled from build_data or data folder

### Changed
- `app.py` — Improved PyInstaller frozen state detection and path resolution
- `KenpoFlashcardsTrayLauncher.py` — Better BASE_DIR detection, sets KENPO_WEBAPP_BASE_DIR before importing app
- `kenpo_tray.spec` — Added version.json bundling, improved hidden imports for jaraco ecosystem
- `requirements_packaging.txt` — Added explicit jaraco.* dependencies

## v1.0.0 (build 3) — 2026-01-20

First stable installer release (graduating from the vbeta line).

### Added
- Updated bundled web server to **v5.5.2 (build 29)**, bringing in:
  - AI Access UI for API key management and model selection.
  - Encrypted API key storage (`data/api_keys.enc`).
  - Shared Key Mode (optional one-key-for-all-authenticated-users).
  - Improved Sync merge logic using `updated_at`, better handling of queued/offline updates.
  - Admin pages (About/Admin/User Guide) and PDF generation.
- Inno Setup installer installs the complete PyInstaller folder build (includes `_internal\` and dependencies) into **Program Files** and adds Start Menu shortcuts.

### Changed
- The installer now copies the full PyInstaller output folder (`dist\KenpoFlashcardsTray\*`) so the app runs without requiring "extra files" to be manually copied.

### Fixed
- Packaging reliability improvements from the beta line (build scripts, Inno Setup defaults).

### Known issues / notes
- **Windows Defender/AV false positives:** Unsigned PyInstaller EXEs can be quarantined. For distribution, consider code signing.

## vbeta (build 2) — 2026-01-19

### Fixed
- Packaging script fixes and documentation updates.

## vbeta (build 1) — 2026-01-19

### Added
- Initial packaged beta release (PyInstaller + Inno Setup build scripts).
