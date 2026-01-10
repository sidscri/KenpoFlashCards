# 🥋 Kenpo Flashcards Web Server — Packaging (EXE / Installer / MSI)

> This is a **sub-project folder** inside the `sidscri-apps` repository.
> Folder: `KenpoFlashcardsWebServer_Packaged_in_exe_msi/`
This folder includes scripts to package the app for Windows:
- PyInstaller **portable EXE**
- Inno Setup **installer EXE**
- WiX Toolset **MSI**


> If you split this folder into its own GitHub repo later, update any badge URLs accordingly.

## Run from source
```bat
python -m venv .venv
.venv\Scripts\activate
pip install -r requirements.txt
python app.py
```

## Build portable EXE (recommended)
```bat
packaging\build_exe.bat
```
Output is under `dist\...`

## Build installer EXE (Inno Setup)
Install Inno Setup, then:
```bat
packaging\build_installer_inno.bat
```

## Build MSI (WiX Toolset)
Install WiX Toolset, then:
```powershell
powershell -ExecutionPolicy Bypass -File packaging\build_msi_wix.ps1
```

## Secrets
Do **not** commit API keys. Use environment variables or a local `.env`.

## GitHub Pages
Landing page is in `docs/index.html`. Enable Pages from `/docs`.

## License
MIT — see `LICENSE`.
