# 🥋 Kenpo Flashcards Suite (Android + Web)

This repository (**sidscri-apps**) contains the Kenpo flashcards ecosystem:

| Project | Description | Current Version |
|---------|-------------|-----------------|
| **KenpoFlashcardsProject-v2/** | Android app (F-Droid) | v4.1.0 (versionCode 17) |
| **KenpoFlashcardsWebServer/** | Core web server (sync + web UI/API) | v5.3.1 (build 25) |
| **KenpoFlashcardsWebServer_Service_Tray/** | Windows Service + tray runner | v1.0.0 (build 1) [BETA] |
| **KenpoFlashcardsWebServer_Packaged_in_exe_msi/** | Packaged builds (EXE/MSI) | v1.0.0 (build 1) [BETA] |

---

## 📦 Repository Layout

```text
sidscri-apps/
├── KenpoFlashcardsProject-v2/                      # Android app (F-Droid)
├── KenpoFlashcardsWebServer/                       # Core web server + web UI/API
├── KenpoFlashcardsWebServer_Service_Tray/          # Windows Service + tray (Option A2)
├── KenpoFlashcardsWebServer_Packaged_in_exe_msi/   # Packaging: portable EXE / installer / MSI
├── .github/workflows/                              # CI/CD workflows
└── .gitignore                                      # Shared ignore rules
```

---

## 📱 KenpoFlashcardsProject-v2 (Android)

An Android flash-card app for learning **American Kenpo Karate** vocabulary.

- **Location:** `KenpoFlashcardsProject-v2/`
- **Docs:** `KenpoFlashcardsProject-v2/README.md`
- **Changelog:** `KenpoFlashcardsProject-v2/CHANGELOG.md`
- **Current Version:** v4.1.0 (versionCode 17)

### Key Features (v4.1)
- Three-state progress tracking (To Study / Unsure / Learned)
- Custom study sets with star selection
- Term breakdowns with AI auto-fill (ChatGPT)
- Web sync with server
- Landscape mode support
- Text-to-speech with pronunciation mode

---

## 🌐 KenpoFlashcardsWebServer (Web App Server)

Flask-based web server providing sync API and web UI.

- **Location:** `KenpoFlashcardsWebServer/`
- **Docs:** `KenpoFlashcardsWebServer/README.md`
- **Changelog:** `KenpoFlashcardsWebServer/CHANGELOG.md`
- **Current Version:** v5.3.1 (build 25)
- **Server URL:** `sidscri.tplinkdns.com:8009`

### Key Features (v5.3.1)
- User authentication with token-based Android sync
- Progress push/pull endpoints
- Shared breakdown database
- Helper mapping for cross-device ID consistency
- About/Admin/User Guide pages
- PDF user guide generation

### API Endpoints
| Endpoint | Description |
|----------|-------------|
| `POST /api/sync/login` | Android token authentication |
| `GET/POST /api/sync/pull\|push` | Progress sync |
| `GET/POST /api/sync/breakdowns` | Breakdown sync |
| `GET /api/sync/helper` | Canonical ID mapping |
| `GET /api/version` | Server version info |

---

## 🪟 Windows Deployment Options

### Option 1: Service + Tray (Recommended)
**Location:** `KenpoFlashcardsWebServer_Service_Tray/`

Runs the server in the background like Sonarr/Radarr:
- Windows Service via NSSM
- System tray icon for Start/Stop/Open
- Auto-start on boot

### Option 2: Packaged Installers
**Location:** `KenpoFlashcardsWebServer_Packaged_in_exe_msi/`

Distributable packages:
- Portable EXE (PyInstaller)
- Installer EXE (Inno Setup)
- MSI (WiX Toolset)

---

## 🔧 Quick Start

### Android App
```bash
cd KenpoFlashcardsProject-v2
# Open in Android Studio, sync Gradle, run on device/emulator
```

### Web Server
```bash
cd KenpoFlashcardsWebServer
python -m venv .venv
.venv\Scripts\activate        # Windows
pip install -r requirements.txt
python app.py
# Open http://localhost:8009
```

### Verify Sync Works
1. **Server:** Open `http://localhost:8009/api/sync/helper` — should return JSON with `version`, `term_to_id`, `cards`
2. **Android:** Login in Admin screen, test Push/Pull buttons
3. **Logs:** Watch for `POST /api/sync/login 200` and `POST /api/sync/push 200`

---

## 🔐 Security & Secrets

**Do NOT commit:**
- API keys (`gpt api.txt`, `.env`)
- User data (`data/` directories)
- Batch files with embedded secrets

These are excluded via `.gitignore`. See each project's README for data folder locations.

---

## 📋 Changelogs

Each project maintains its own changelog:
- `KenpoFlashcardsProject-v2/CHANGELOG.md`
- `KenpoFlashcardsWebServer/CHANGELOG.md`
- `KenpoFlashcardsWebServer_Service_Tray/CHANGELOG.md`
- `KenpoFlashcardsWebServer_Packaged_in_exe_msi/CHANGELOG.md`

---

## 📄 License

Personal/educational use for learning American Kenpo Karate vocabulary.
