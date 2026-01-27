# Changelog — Advanced Flashcards WebApp Server (Packaged)

All notable changes to the Windows packaged/installer distribution are documented here.

## v3.1.0 (build 11) — 2026-01-25

This release was completed in steps. Documentation stays on **v3.1.0 (build 11)** while the step work is in progress.

### Step 1 — Rebrand + move runtime data (v3.1.0.1)
- Rebranded the packaged app and installer to **Advanced Flashcards WebApp Server** (no more “Kenpo Flashcards”).
- Runtime data moved out of Program Files; the app now uses:  
  `%LOCALAPPDATA%\Advanced Flashcards WebApp Server\data`
- Tray executable and installer naming updated to match branding.

### Step 2 — Builder stages LOCALAPPDATA data (v3.1.0.3)
- `packaging\1. pre_build.bat`: if local user data exists at `%LOCALAPPDATA%\Advanced Flashcards WebApp Server\data`, stage it into `packaging\build_data` (otherwise treat as fresh install).
- Repo + local logs added for data staging and build decisions.

### Step 3 — Build-data flag + seed next package (v3.1.0.4)
- Adds a flag in `packaging\build_data` to indicate it was sourced from LOCALAPPDATA.
- After a successful build, `root\data` is backed up then replaced from flagged `packaging\build_data` so the next package includes the newest data for new installs.
- Local log location moved to:  
  `%LOCALAPPDATA%\Advanced Flashcards WebApp Server\log\Advanced Flashcards WebApp Server logs\`

### Step 4 — Update behavior + backups + startup options (v3.1.0.5)
- Updates prefer **existing local data**; packaged data only seeds missing files (and only safe reference files may be overwritten if packaged is newer).
- Backups added:
  - Update backups: `...\DataBackups\Data_Updated_<Date>_<AppVersion>\data.zip`
  - Auto backups (on change + interval): `...\DataBackups\Data_Auto_<Date>_<AppVersion>\data.zip` (keep last 10)
  - On-demand backup (tray): `Backup Now`
- Startup options available (tray + installer tasks):
  - Start with Windows (HKCU Run)
  - Background at login (Task Scheduler)
- Tray “Restart” now actually relaunches the app instead of only killing it.
- Tray Restart will restart the Windows Service when installed (otherwise it relaunches the tray app).

### Package variant
- WinSW Windows Service wrapper (advanced)

## v3.0.0 (build 10) — 2026-01-25

### Changed
- **Updated bundled Web Server to v7.2.0 (build 42)** (from v7.0.2 build 35), including:
  - **Custom Set Management Modal**
  - **Settings Tab**
  - **Manage Tab**
  - **Saved Sets Tab**
  - **Server Activity Logs**
  - **Settings Save Prompt**
  - Moved random cards picker from Custom toggle bar to Custom Set Settings modal
  - Settings inputs now track dirty state for save prompt
  - **Web Sync Endpoints**
  - **Breakdown Indicator**
  - **Breakdown IDs API**
  - **Enhanced User Stats**
  - **Deck Stats**
  - Tabbed interface: Overview, Users, System, Logs
  - Removed About/User Guide links (accessible from main app)
  - Android app can now sync decks and user cards with web server
  - Full cross-platform deck and card sharing
  - **Rebranded to "Advanced Flashcards WebApp Server"**
  - **Header shows active deck**
  - **Set Default Deck**
  - **API endpoint**
  - **🤖 AI Deck Generator**
  - **Keywords**
  - **Photo**
  - **Document**
  - **Edit Deck**
  - **Logout confirmation**
  - **AI Deck Generator**
  - **User cards in study deck**
  - Reshuffle button now works anytime (not just when random is enabled)

## v2.0.0 (build 9) — 2026-01-23

### Changed
- **Updated bundled Web Server to v7.0.2 (build 35)** (from v6.1.0 build 32), including:
  - **🎲 Pick Random N**
  - **User Management Modal**
  - **Admin User Editing**
  - **Password Reset**
  - **System Status Feed**
  - **Reshuffle button visible**
  - **Search clear X button**
  - **Randomize Custom Set setting**
  - **Speak pronunciation only toggle**
  - Reshuffle works regardless of random toggle state (instant shuffle on demand)
  - **Edit Decks page**
  - **Switch tab**
  - **Add Cards tab**
  - **Deleted tab**
  - **Deck management**
  - **User cards CRUD**
  - Settings page now has "Edit Decks" button at top for quick access

## v1.3.0 (build 8) — 2026-01-23

### Added
- **Upgrade Tool** (`tools/` folder) — Python script and batch file to safely sync web server updates to the packaged project:
  - Syncs `app.py`, `static/`, `requirements.txt`, `CHANGELOG.md` from web server
  - Merges `data/` folder (preserves user data, updates structure)
  - Protects packaging files (`packaging/`, `windows_service/`, `windows_tray/`, icons, shortcuts)
  - Creates automatic backups in `.sync_backups/` before making changes
  - Supports dry-run mode to preview changes
  - Updates `version.json` with web server version tracking

### Changed
- **Updated bundled Web Server to v6.1.0 (build 32)** (from v6.0.0 build 31), including:
  - **Sync Progress page** — new settings section matching Android app with Push/Pull buttons, login status banner, auto-sync info, and breakdown sync
  - **Settings tabbed navigation** — quick nav tabs (📚 Study, 🎨 Display, 🔊 Voice, 🔄 Sync, 🤖 AI) with highlighted active tab
  - **Star button on study cards** — toggle ☆/★ directly from study view to add/remove from Custom Set
  - **Sort by status dropdown** — All list can now be sorted by Unlearned first, Unsure first, Learned first, or Alphabetical
  - **Logout moved to user menu** — click User dropdown to see logout option with icon
  - **App-like button styling** — gradient backgrounds matching Android app (blue primary, green success, red danger)
  - **Settings redesign** — card-based layout with modern styling

## v1.2.0 (build 7) — 2026-01-22

### Changed
- **Updated bundled Web Server to v6.0.0 (build 31)** (from v5.5.2 build 29), including:
  - **Custom Set (⭐ Starred Cards)** — star/unstar cards and study a personalized set (All/Unsure/Learned filters)
  - **New study settings**: `show_breakdown_on_definition`, `auto_speak_on_card_change`, `speak_definition_on_flip`
  - **Admin Dashboard redesign** with richer statistics + AI status indicators
  - **New API endpoint**: `/api/admin/stats`
  - **Sync improvements**: per-card `updated_at` timestamps and newer-wins merge logic (better offline sync)

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
  - "Open Data Folder" - Opens the Advanced Flashcards WebApp Server data folder
- Config file is auto-created in `%LOCALAPPDATA%\Advanced Flashcards WebApp Server\server_config.json` on first run

### Changed
- Default host binding changed from `127.0.0.1` to `0.0.0.0` for easier LAN/Tailscale access
- Tray icon tooltip now shows current host:port binding
- Updated tray icon image

## v1.1.0 (build 5) — 2026-01-22

### Fixed
- **Bundled data not loading** — Added initial data seeding that copies profiles, progress, API keys, breakdowns, and helper data from the bundled `_internal\data` folder to `%LOCALAPPDATA%\Advanced Flashcards WebApp Server\data\` on first run. This ensures user accounts and progress from the dev build are available in the installed app.

## v1.0.1 (build 4) — 2026-01-22

### Added
- **`pre_build.bat`** — New script that copies data from dev location before building:
  - Copies from `C:\Users\Sidscri\Documents\GitHub\sidscri-apps\StudyFlashcardsWebServer\data`
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
- `StudyFlashcardsTrayLauncher.py` — Better BASE_DIR detection, sets KENPO_WEBAPP_BASE_DIR before importing app
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
- The installer now copies the full PyInstaller output folder (`dist\StudyFlashcardsTray\*`) so the app runs without requiring "extra files" to be manually copied.

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


### Startup / tray
- Installer Scheduled Task option now uses `/RL HIGHEST` and a 30-second start delay to improve reliability.
- Tray "Restart" now relaunches the app using `ShellExecuteW` (more reliable for frozen EXEs) so it actually restarts the server.
