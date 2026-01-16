# 🥋 Kenpo Flashcards Web Server — Packaged

> This is a **sub-project** inside the `sidscri-apps` monorepo.  
> Folder: `KenpoFlashcardsWebServer_Packaged/`

Windows distributable packages for KenpoFlashcardsWebServer.

**Current Version:** vbeta v2 (build 2)  
**Changelog:** [CHANGELOG.md](CHANGELOG.md)

---

## 📦 Package Types

| Type | Tool | Output | Use Case |
|------|------|--------|----------|
| **Tray EXE** | PyInstaller | Folder with `.exe` | Sonarr-style tray app |
| **Installer EXE** | Inno Setup | Setup wizard `.exe` | Standard Windows install |
| **MSI** | WiX Toolset | `.msi` package | Enterprise/GPO deployment |

---

## 🚀 Quick Start (From Source)

```bat
python -m venv .venv
.venv\Scripts\activate
pip install -r requirements.txt
python app.py
```

Open: `http://localhost:8009`

---

## 🔨 Build Packages

### Tray EXE (PyInstaller)
```bat
pip install -r packaging/requirements_packaging.txt
python -m PyInstaller packaging/pyinstaller/kenpo_tray.spec --noconfirm
```
Output: `dist\KenpoFlashcardsTray\KenpoFlashcardsTray.exe`

### Installer EXE (Inno Setup)
1. Install [Inno Setup](https://jrsoftware.org/isinfo.php)
2. Run:
   ```bat
   iscc packaging/installer_inno.iss
   ```
Output: `packaging\output\KenpoFlashcardsWebSetup.exe`

### MSI (WiX Toolset) - Optional
1. Install [WiX Toolset](https://wixtoolset.org/)
2. Run:
   ```powershell
   powershell -ExecutionPolicy Bypass -File packaging\build_msi_wix.ps1
   ```
Output: `dist\KenpoFlashcardsWebServer.msi`

---

## 📁 Project Structure

```
KenpoFlashcardsWebServer_Packaged/
├── app.py                        # Main Flask application
├── KenpoFlashcardsTrayLauncher.py # Tray launcher (starts server + tray icon)
├── requirements.txt              # Python dependencies
├── ic_launcher.png               # App icon
├── CHANGELOG.md
├── README.md
├── version.json
├── packaging/
│   ├── pyinstaller/
│   │   └── kenpo_tray.spec       # PyInstaller spec for tray build
│   ├── installer_inno.iss        # Inno Setup script
│   ├── requirements_packaging.txt
│   ├── build_exe.bat
│   ├── build_installer_inno.bat
│   ├── build_msi_wix.ps1
│   └── output/                   # Installer output directory
├── static/
│   ├── index.html
│   ├── app.js
│   └── styles.css
├── data/
│   ├── profiles.json
│   ├── breakdowns.json
│   └── users/
├── windows_service/              # NSSM service scripts
└── windows_tray/                 # Standalone tray scripts
```

---

## 🔨 Automated Builds

GitHub Actions automatically builds on push to `main`.

**Workflow:** `.github/workflows/build-windows-packaging-exe-msi.yml`

**Artifacts produced:**
- `KenpoFlashcardsTray` - Tray application folder
- `KenpoFlashcardsWebSetup` - Windows installer EXE

---

## 🔐 Secrets & API Keys

**Do NOT commit API keys or secrets.**

Use one of these approaches:
1. **Environment variables** - Set `OPENAI_API_KEY` before running
2. **Local `.env` file** - Create `.env` in app directory (gitignored)
3. **Config file** - Place API key in `data/config.json` (gitignored)

---

## ⚙️ Configuration

### First Run
On first run, the packaged app will:
1. Create `data/` directory for user data
2. Generate `data/profiles.json` for user accounts
3. Generate `data/secret_key.txt` for session security

### Environment Variables
| Variable | Description |
|----------|-------------|
| `KENPO_PORT` | Server port (default: 8009) |
| `KENPO_HOST` | Bind address (default: 127.0.0.1) |
| `KENPO_WEBAPP_BASE_DIR` | Base directory for static/data |
| `OPENAI_API_KEY` | OpenAI API key for AI features |

### Data Location
By default, data is stored relative to the executable:
```
KenpoFlashcardsTray/
├── KenpoFlashcardsTray.exe
├── static/
├── data/
│   ├── profiles.json
│   ├── breakdowns.json
│   └── users/...
└── assets/
    └── ic_launcher.png
```

## 📋 Version History

| Version | Build | Key Changes |
|---------|-------|-------------|
| **beta** | 1 | Initial packaged release, EXE installer for standalone deployment & enterprise/managed deployment, scripts and build instructions, GitHub Actions workflow, KenpoFlashcardsTrayLauncher for Sonarr-style tray app |
| **beta** | 2 | GitHub Actions workflow now builds successfully, Fixed `kenpo_tray.spec` to use actual project structure (`ic_launcher.png` in root), Fixed workflow paths to match folder name `KenpoFlashcardsWebServer_Packaged`,  |

## ⚠️ Known Limitations (Beta)

- Requires manual `data/` configuration on first run
- OpenAI API key must be set via environment or config
- Anti-virus may flag PyInstaller executables (false positive)
- Windows Defender SmartScreen may warn on unsigned executables

### Signing (Optional)
For production distribution, consider code signing:
```bat
signtool sign /f certificate.pfx /p password /t http://timestamp.digicert.com dist\*.exe
```

---

## 🔗 Related Projects

- **Core Server:** `../KenpoFlashcardsWebServer/`
- **Service + Tray:** `../KenpoFlashcardsWebServer_Service_Tray/`
- **Android App:** `../KenpoFlashcardsProject-v2/`

---

## 📄 License

MIT — see `LICENSE`
