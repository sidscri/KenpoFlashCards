# Kenpo Sync Tool (WebServer -> WebServer_Packaged)

This tool copies only the **shared web/server code** (e.g., `app.py`, `static/`, docs) from:

- `KenpoFlashcardsWebServer` (source)
to
- `KenpoFlashcardsWebServer_Packaged` (target)

…and **protects** everything packaging-related so your PyInstaller/Inno/WiX setup is not touched.

## What it will sync (default)
- `app.py`
- `static/**`
- `requirements.txt`
- `README.md`, `CHANGELOG.md`, `LICENSE`, `BRANDING_NOTE.md`, `.gitattributes`

## What it will NOT touch (by design)
- Anything under: `packaging/`, `dist/`, `build/`, `.git/`, `.github/`, `.venv/`, etc.
- `KenpoFlashcardsTrayLauncher.py` (packaged-only)
- `version.json` + `Version-*.txt` (packaged has its own versioning)

It also skips `data/` and `build_data/` from the source because your packaged project already has a pre-build script
(`packaging/1. pre_build.bat`) that pulls fresh data into `build_data/`.

## Usage (Windows)
Open PowerShell in the folder containing this tool and run:

### Dry-run (recommended first)
```powershell
py .\sync_webserver_to_packaged.py
```

### Apply changes
```powershell
py .\sync_webserver_to_packaged.py --apply
```

## If your repos are in different paths
```powershell
py .\sync_webserver_to_packaged.py --source "D:\Git\KenpoFlashcardsWebServer" --target "D:\Git\KenpoFlashcardsWebServer_Packaged" --apply
```

## Backups
When you run with `--apply`, any overwritten file is backed up into:
`KenpoFlashcardsWebServer_Packaged\.sync_backups\<timestamp>\...`

## Report
A `sync_report.json` file is written to your current working directory each time you run the tool.
