# 🥋 Kenpo Flashcards Web Server — Windows Service + Tray

> This is a **sub-project** inside the `sidscri-apps` monorepo.  
> Folder: `KenpoFlashcardsWebServer_Service_Tray/`

Windows background service with system tray icon for KenpoFlashcardsWebServer.  
Runs like Sonarr/Radarr — starts on boot, lives in the tray.

**Current Version:** v1.0.0 (build 1) [BETA]  
**Changelog:** [CHANGELOG.md](CHANGELOG.md)

---

## ✨ Features

- ✅ Runs on boot as a **Windows Service** (via NSSM)
- ✅ **System tray icon** with context menu
- ✅ Start / Stop / Restart controls
- ✅ "Open in Browser" quick action
- ✅ Auto-start on Windows login (optional)
- ✅ Status indicator in tray

---

## 🚀 Quick Start (Developer Mode)

```bat
python -m venv .venv
.venv\Scripts\activate
pip install -r requirements.txt
python app.py
```

Open: `http://localhost:8009`

---

## 🔧 Install as Windows Service (Recommended)

### Prerequisites
1. Download [NSSM](https://nssm.cc/) (Non-Sucking Service Manager)
2. Place `nssm.exe` in: `windows_service\nssm.exe`

### Installation
1. Right-click `INSTALL_Service_NSSM.bat` → **Run as Administrator**
2. Set environment variables:
   ```bat
   windows_service\SET_Service_Env.bat
   ```
3. Service will auto-start on boot

### Service Management
```bat
# Start service
net start KenpoFlashcardsWeb

# Stop service
net stop KenpoFlashcardsWeb

# Check status
sc query KenpoFlashcardsWeb
```

---

## 🖥️ Tray Icon (Optional)

### Install Tray Dependencies
```bat
windows_tray\INSTALL_Tray_Dependencies.bat
```

### Start Tray
```bat
windows_tray\START_Tray.bat
```

### Tray Menu Options
- **Open Browser** - Opens `http://localhost:8009`
- **Start Server** - Starts the background service
- **Stop Server** - Stops the background service
- **Restart Server** - Restart the service
- **Exit** - Close tray icon (service keeps running)

---

## ⚙️ Configuration

### Environment Variables
| Variable | Description |
|----------|-------------|
| `KENPO_ROOT` | Root path for auto-discovering card data |
| `KENPO_JSON_PATH` | Direct path to `kenpo_words.json` |
| `OPENAI_API_KEY` | OpenAI API key for AI features |

### Data Path Auto-Mapping
The server supports automatic path discovery. Set `KENPO_ROOT` to your monorepo path:
```
KENPO_ROOT=C:\Users\Sidscri\Documents\GitHub\sidscri-apps
```

See `windows_service\README_windows_service_tray.md` for details.

---

## 📁 Project Structure

```
KenpoFlashcardsWebServer_Service_Tray/
├── app.py                    # Main Flask application
├── tray_app.py               # System tray application
├── requirements.txt          # Python dependencies
├── windows_service/
│   ├── INSTALL_Service_NSSM.bat
│   ├── UNINSTALL_Service_NSSM.bat
│   ├── SET_Service_Env.bat
│   └── nssm.exe              # Download separately
├── windows_tray/
│   ├── INSTALL_Tray_Dependencies.bat
│   └── START_Tray.bat
├── docs/
│   └── index.html            # GitHub Pages landing
└── CHANGELOG.md
```

---

## 🌐 GitHub Pages

Landing page available at `docs/index.html`.  
Enable GitHub Pages from `/docs` in repository settings.

---

## ⚠️ Known Limitations (Beta)

- Windows only (uses `pystray` for tray functionality)
- Server logs viewable only via log file (not in tray UI)
- Requires administrator privileges for service installation
- Auto-start requires running installer as admin

---

## 📄 License

MIT — see `LICENSE`
