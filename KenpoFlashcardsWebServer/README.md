# 🌐 KenpoFlashcardsWebServer (Web App Server)

> This is the **web server** project inside the `sidscri-apps` monorepo.  
> Repo root: `../README.md`

Flask-based web application providing sync API and web UI for Kenpo Flashcards.

**Current Version:** v5.5.0 (build 27)  
**Changelog:** [CHANGELOG.md](CHANGELOG.md)

---

## 🎯 What It Does

- **Authentication** - User login with token-based Android sync
- **Progress Sync** - Push/pull card progress between devices
- **Breakdown Sync** - Shared term breakdown database
- **Web UI** - Browser-based flashcard interface
- **Helper Mapping** - Canonical card IDs for cross-device consistency
- **AI Integration** - ChatGPT and Gemini API for breakdown autofill
- **Encrypted API Keys** - Secure storage shared between Android and web

---

## 📍 Location & Workflows

- **Path:** `sidscri-apps/KenpoFlashcardsWebServer/`
- **CI Workflow:** `.github/workflows/kenpo-webserver-ci.yml`
- **Build Workflow:** `.github/workflows/kenpo-webserver-build-zip.yml`

---

## 🚀 Quick Start (Windows)

### Option 1: Batch File
Double-click `START_KenpoFlashcardsWebServer.bat`

### Option 2: Manual Setup
```powershell
cd KenpoFlashcardsWebServer
python -m venv .venv
.venv\Scripts\activate
pip install -r requirements.txt
python app.py
```

Open: `http://localhost:8009`

---

## 🔌 API Endpoints

### Authentication
| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/sync/login` | POST | Android token authentication |
| `/api/login` | POST | Web session login |
| `/api/logout` | POST | Web session logout |

### Sync (Token Required)
| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/sync/push` | POST | Push progress to server |
| `/api/sync/pull` | GET | Pull progress from server |
| `/api/sync/breakdowns` | GET | Get all breakdowns |
| `/api/sync/helper` | GET | Canonical ID mapping |

### Breakdowns
| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/breakdowns` | GET | Get breakdowns (web session) |
| `/api/breakdowns` | POST | Save breakdown (admin only) |

### Admin (Token Required)
| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/admin/apikeys` | GET | Get encrypted API keys (admin) |
| `/api/admin/apikeys` | POST | Save encrypted API keys (admin) |
| `/api/admin/status` | GET | Check admin status |
| `/api/admin/users` | GET | Get admin usernames (SoT) |

### Web Admin (Session Required)
| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/web/admin/apikeys` | GET | Get API keys for web UI |
| `/api/web/admin/apikeys` | POST | Save API keys from web UI |

### Info
| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/version` | GET | Server version info |
| `/api/health` | GET | Server health check |
| `/about` | GET | About page |
| `/admin` | GET | Admin diagnostics |
| `/ai-access.html` | GET | AI Access settings (admin) |
| `/user-guide` | GET | User guide page |

---

## 📁 Data & Secrets

**Runtime data is NOT committed to Git:**
- `data/` - User accounts, progress, breakdowns
- `logs/` - Server logs
- `.env` - Environment variables

### Data Structure
```
data/
├── profiles.json        # User accounts (hashed passwords)
├── breakdowns.json      # Shared breakdowns
├── helper.json          # Auto-generated ID mapping
├── secret_key.txt       # Flask session key
├── api_keys.enc         # Encrypted API keys (safe for git)
├── admin_users.json     # Admin usernames (Source of Truth)
└── users/
    ├── {user_id}/
    │   └── progress.json
    └── ...
```

---

## 🔧 Configuration

### Environment Variables (Optional)
| Variable | Description |
|----------|-------------|
| `KENPO_ROOT` | Root path for auto-discovering `kenpo_words.json` |
| `KENPO_JSON_PATH` | Direct path to card data JSON |

**Note:** API keys are now stored encrypted in `data/api_keys.enc`. You no longer need to set `OPENAI_API_KEY` in the batch file - keys are loaded from the encrypted file on startup.

### Auto-Path Discovery
The server automatically locates `kenpo_words.json` by scanning:
```
{KENPO_ROOT}/*/app/src/main/assets/kenpo_words.json
```

---

## 🔐 Admin Management

Admin users are defined in `data/admin_users.json`:
```json
{
  "admin_usernames": ["sidscri"]
}
```

Both Android app and web server read from this Source of Truth.

---

## 🪟 Windows Deployment Options

### Service + Tray (Recommended)
Run in background like Sonarr/Radarr:
- See: `../KenpoFlashcardsWebServer_Service_Tray/README.md`

### Packaged Installers
Portable EXE, installer, or MSI:
- See: `../KenpoFlashcardsWebServer_Packaged_in_exe_msi/README.md`

---

## ✅ Verify It Works

### 1. Test Helper Endpoint
```
http://localhost:8009/api/sync/helper
```
Should return JSON with `version`, `term_to_id`, `cards`

### 2. Test Version Endpoint
```
http://localhost:8009/api/version
```
Should return `{"version": "5.5.0", "build": 27, ...}`

### 3. Check Data Files
Confirm `data/helper.json` exists on disk after first request.

---

## 📋 Version History

| Version | Build | Key Changes |
|---------|-------|-------------|
| **5.5.0** | 27 | AI Access page, model selection, startup key loading, admin_users.json SoT |
| **5.4.0** | 26 | Encrypted API key storage, Gemini API, admin endpoints |
| **5.3.1** | 25 | Fixed duplicate `/api/login` endpoint conflict |
| **5.3.0** | 24 | About/Admin/User Guide pages, user dropdown |
| **5.2.0** | 23 | End-to-end sync confirmed, helper mapping |
| **5.1.1** | 22 | version.json, favicon, security.txt |
| **5.0.0** | 20 | Stable ID mapping baseline |
| **4.2.0** | 18 | Settings reorg, Python 3.8 compat |

See [CHANGELOG.md](CHANGELOG.md) for full details.

---

## 🧩 Project Structure

```
KenpoFlashcardsWebServer/
├── app.py                 # Main Flask application
├── requirements.txt       # Python dependencies
├── version.json           # Version info
├── START_KenpoFlashcardsWebServer.bat  # Windows launcher
├── static/
│   ├── index.html         # Web UI
│   ├── app.js             # Frontend JavaScript
│   ├── styles.css         # Styles
│   ├── admin.html         # Admin diagnostics
│   ├── ai-access.html     # AI Access settings (admin)
│   ├── about.html         # About page
│   ├── user-guide.html    # User guide
│   ├── favicon.ico        # Browser icon
│   └── .well-known/
│       └── security.txt   # Security contact
├── data/                  # Runtime data (gitignored except SoT files)
│   ├── admin_users.json   # Admin usernames (Source of Truth)
│   └── api_keys.enc       # Encrypted API keys (safe for git)
└── CHANGELOG.md           # Version history
```

---

## 📄 License

Personal/educational use for learning American Kenpo Karate vocabulary.
