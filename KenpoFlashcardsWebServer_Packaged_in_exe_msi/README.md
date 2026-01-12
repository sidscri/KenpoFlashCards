# 🥋 Kenpo Flashcards Web Server — Packaging (EXE / Installer / MSI)

> This is a **sub-project** inside the `sidscri-apps` monorepo.  
> Folder: `KenpoFlashcardsWebServer_Packaged_in_exe_msi/`

Windows distributable packages for KenpoFlashcardsWebServer.

**Current Version:** v1.0.0 (build 1) [BETA]  
**Changelog:** [CHANGELOG.md](CHANGELOG.md)

---

## 📦 Package Types

| Type | Tool | Output | Use Case |
|------|------|--------|----------|
| **Portable EXE** | PyInstaller | Single `.exe` file | USB/portable deployment |
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

### Portable EXE (Recommended)
```bat
packaging\build_exe.bat
```
Output: `dist\KenpoFlashcardsWebServer.exe`

### Installer EXE (Inno Setup)
1. Install [Inno Setup](https://jrsoftware.org/isinfo.php)
2. Run:
   ```bat
   packaging\build_installer_inno.bat
   ```
Output: `dist\KenpoFlashcardsWebServer_Setup.exe`

### MSI (WiX Toolset)
1. Install [WiX Toolset](https://wixtoolset.org/)
2. Run:
   ```powershell
   powershell -ExecutionPolicy Bypass -File packaging\build_msi_wix.ps1
   ```
Output: `dist\KenpoFlashcardsWebServer.msi`

---

## 📁 Project Structure

```
KenpoFlashcardsWebServer_Packaged_in_exe_msi/
├── app.py                    # Main Flask application
├── requirements.txt          # Python dependencies
├── packaging/
│   ├── build_exe.bat         # PyInstaller build script
│   ├── build_installer_inno.bat
│   ├── build_msi_wix.ps1
│   ├── setup.iss             # Inno Setup script
│   └── setup.wxs             # WiX XML definition
├── dist/                     # Build outputs (gitignored)
├── docs/
│   └── index.html            # GitHub Pages landing
└── CHANGELOG.md
```

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

### Data Location
By default, data is stored relative to the executable:
```
KenpoFlashcardsWebServer.exe
└── data/
    ├── profiles.json
    ├── breakdowns.json
    └── users/...
```

---

## 🌐 GitHub Pages

Landing page available at `docs/index.html`.  
Enable GitHub Pages from `/docs` in repository settings.

---

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
