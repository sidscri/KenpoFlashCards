# Study Flashcards Web Server (Packaged)

A Windows **installer** build of the Study Flashcards Web Server + Tray Launcher.

- **Packaged Version:** **v3.1.0.1 (build 11)**
- **Bundled Web Server:** **v7.2.0 (build 42)**

## What you get

- **Windows installer (Inno Setup)** that installs to **Program Files** and adds Start Menu shortcuts.
- **Tray Launcher** (`AdvancedStudyFlashcards.exe`) so you can start/stop the server from the system tray.
- **Local web UI** (Flashcards + Admin tools) accessible from your browser.
- **Configurable network binding** - access from localhost, LAN, or Tailscale.
- **Upgrade Tool** to safely sync future web server updates without damaging packaging files.

## What's new in v3.0.0 (build 10)

- **Bundled Web Server updated to v7.2.0 (build 42)** (from v7.0.2 build 35), bringing:
  - **Custom Set Management Modal**
  - **Settings Tab**
  - **Manage Tab**
  - **Saved Sets Tab**
  - **Server Activity Logs**
  - **Web Sync Endpoints** — based auth (fixes "login_required" error)
  - **Breakdown Indicator**
  - **Breakdown IDs API** — lightweight endpoint returning only IDs of cards with breakdown content
  - **Enhanced User Stats** — user progress %, current deck, last sync time
  - **Deck Stats** — created count
  - **Rebranded to "Study Flashcards"**
  - **Header shows active deck**
  - **Set Default Deck**
  - **API endpoint** — Sets a deck as default
  - **🤖 AI Deck Generator**
  - **Keywords**
  - **Photo**
  - **Document**
  - **Edit Deck**
  - **AI Deck Generator**
  - **User cards in study deck** — created cards now merge with built-in cards

## What's new in v2.0.0 (build 9)

- **Bundled Web Server updated to v7.0.2 (build 35)** (from v6.1.0 build 32), bringing:
  - **🎲 Pick Random N**
  - **User Management Modal**
  - **Admin User Editing**
  - **Password Reset**
  - **System Status Feed** — style status display in admin dashboard
  - **Reshuffle button visible**
  - **Search clear X button**
  - **Randomize Custom Set setting**
  - **Speak pronunciation only toggle**
  - **Edit Decks page**
  - **Switch tab**
  - **Add Cards tab**
  - **Deleted tab**
  - **Deck management**

## What's new in v1.3.0 (build 8)

- **Bundled Web Server updated to v6.1.0 (build 32)** (from v6.0.0 build 31), bringing:
  - **Sync Progress page** — new settings section matching Android app with Push/Pull buttons, login status banner, auto-sync info
  - **Settings tabbed navigation** — quick nav tabs for Study, Display, Voice, Sync, and AI sections with highlighted active tab
  - **Star button on study cards** — toggle ☆/★ directly from study view to add/remove from Custom Set
  - **Sort by status dropdown** — All list can now be sorted by Unlearned, Unsure, Learned, or Alphabetical
  - **Logout moved to user menu** — click User dropdown to see logout option
  - **App-like button styling** — gradient backgrounds matching Android app (blue primary, green success, red danger)
- **New: Upgrade Tool** (`tools/` folder) — Python script to safely sync web server updates to the packaged project without damaging packaging code

## What's new in v1.2.0 (build 7)

- **Bundled Web Server updated to v6.0.0 (build 31)** (from v5.5.2 build 29), bringing:
  - **Custom Set (⭐ Starred Cards)** — study a personalized set of cards (add/remove stars, filter All/Unsure/Learned)
  - **New study settings**: show/hide breakdown on definition, auto-speak on card change, speak definition on flip
  - **Admin Dashboard redesign** with richer stats + AI status indicators
  - **New API endpoint**: `/api/admin/stats`
  - **Sync improvements**: per-card `updated_at` timestamps + merge logic (offline/newer-wins)

## What's new in v1.1.1 (build 6)

- **Configurable host/port binding** via `server_config.json`:
  - Bind to `0.0.0.0` (all interfaces), `::` (IPv6), `127.0.0.1` (localhost only), or a specific IP like `192.168.0.129`
  - Configure which URL opens in your browser (e.g., your Tailscale IP)
  - Optionally disable auto-opening browser on startup
- **New tray menu options**: Server Info, Edit Settings, Open Data Folder
- **Default binding changed to `0.0.0.0`** for easier LAN/Tailscale access

## Configuration

On first run, a config file is created at:

```
%LOCALAPPDATA%\Study Flashcards\server_config.json
```

Edit this file to configure your server (or right-click the tray icon → "Edit Settings"):

```json
{
  "host": "0.0.0.0",
  "port": 8009,
  "open_browser": true,
  "browser_url": "http://localhost:8009"
}
```

### Configuration options

| Setting | Description | Examples |
|---------|-------------|----------|
| `host` | IP address to bind to | `"0.0.0.0"` (all IPv4), `"::"` (all incl. IPv6), `"127.0.0.1"` (localhost only), `"192.168.0.129"` (specific IP) |
| `port` | Port number | `8009` (default) |
| `open_browser` | Auto-open browser on startup | `true` / `false` |
| `browser_url` | URL to open in browser | `"http://localhost:8009"`, `"http://192.168.0.129:8009"` |

### Common configurations

**Localhost only (default before v1.1.1):**
```json
{ "host": "127.0.0.1", "port": 8009, "browser_url": "http://localhost:8009" }
```

**LAN access (current default):**
```json
{ "host": "0.0.0.0", "port": 8009, "browser_url": "http://localhost:8009" }
```

**Tailscale access:**
```json
{ "host": "0.0.0.0", "port": 8009, "browser_url": "http://YOUR-TAILSCALE-IP:8009" }
```

After editing, restart the tray app for changes to take effect.

## Upgrade Tool

The packaged project now includes an upgrade tool in the `tools/` folder that safely syncs updates from the main StudyFlashcardsWebServer project.

### Usage

```batch
cd tools
upgrade_webserver.bat ..\path\to\StudyFlashcardsWebServer-v6_2_0.zip
```

Or with Python directly:

```bash
python tools/upgrade_webserver_to_packaged.py webserver.zip ./
```

### What it does

| Category | Items | Action |
|----------|-------|--------|
| **✅ Synced** | `app.py`, `static/`, `requirements.txt`, `CHANGELOG.md` | Updated from web server |
| **🔀 Merged** | `data/` folder | User data preserved, structure updated |
| **⏭️ Protected** | `packaging/`, `windows_service/`, `windows_tray/`, `server_config.json`, icons, shortcuts | Never touched |

The tool creates automatic backups in `.sync_backups/` before making changes.

## What's new in v1.1.0 (build 5)

- **Fixed bundled data not loading** — On first run, the app now copies profiles, progress, API keys, breakdowns, and helper data from the bundled `_internal\data` folder to `%LOCALAPPDATA%\Study Flashcards\data\`. This ensures user accounts and progress from the dev build are available in the installed app.

## What's new in v1.0.1 (build 4)

- **Pre-build data sync** — New `pre_build.bat` automatically copies data from your dev location before building.
- **Fixed PyInstaller path issues** — Version info, static files, and kenpo_words.json now load correctly in the packaged EXE.
- **Fixed jaraco dependency** — Resolved `ModuleNotFoundError: No module named 'jaraco'` that prevented tray from starting.
- **Improved APP_DIR resolution** — Properly detects PyInstaller frozen state for correct file paths.

## Install (recommended)

1. On the target PC, run the installer:
   - `StudyFlashcardsWebSetup.exe`
2. If Windows SmartScreen prompts:
   - Click **More info** → **Run anyway** (expected for unsigned installers).
3. After install, use:
   - **Start Menu → Study Flashcards** → **Study Flashcards** (Tray Launcher)

## Run / Use

1. Start the Tray Launcher.
2. In the tray icon menu, click **Open Web App** (or open your browser and go to the local address shown by the tray app).

## Where your data is stored

The web server stores user data (accounts, progress, breakdowns) in:

```
%LOCALAPPDATA%\Study Flashcards\data\
```

This is typically `C:\Users\<YourName>\AppData\Local\Study Flashcards\data\`.

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

1. **Dev location:** `C:\Users\Sidscri\Documents\GitHub\sidscri-apps\StudyFlashcardsWebServer\data`
2. **Android project:** `C:\Users\Sidscri\Documents\GitHub\sidscri-apps\StudyFlashcardsProject-v2\app\src\main\assets\kenpo_words.json`
3. **Fallback:** Uses existing `data\` folder in project root

Copied data goes to `build_data\` folder, which the build process uses if present.

### Build output

- Installer: `packaging\output\StudyFlashcardsWebSetup.exe`
- EXE folder: `dist\StudyFlashcardsTray\`

## Uninstall

Use:

- **Windows Settings → Apps → Installed apps → Study Flashcards → Uninstall**

(If you want to preserve your progress, back up the `%LOCALAPPDATA%\Study Flashcards\data\` folder before uninstalling.)
