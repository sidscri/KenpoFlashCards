# KenpoFlashcards Web Server Upgrade Tool

This tool safely applies updates from the main `KenpoFlashcardsWebServer` project to the `KenpoFlashcardsWebServer_Packaged` project without damaging the Windows packaging code.

## Quick Start

### Windows (Batch File)
```batch
upgrade_webserver.bat KenpoFlashcardsWebServer-v6_1_0_v32.zip
```

### Python (Direct)
```bash
python upgrade_webserver_to_packaged.py KenpoFlashcardsWebServer-v6_1_0.zip ./Packaged/
```

### Dry Run (Preview Changes)
```bash
python upgrade_webserver_to_packaged.py webserver.zip ./Packaged/ --dry-run
```

---

## What Gets Updated

### ✅ SYNCED (Updated from Web Server)

| File/Folder | Description |
|-------------|-------------|
| `app.py` | Main Flask application |
| `static/` | All UI files (HTML, CSS, JS) |
| `requirements.txt` | Python dependencies |
| `CHANGELOG.md` | Change log |
| `LICENSE` | License file |
| `BRANDING_NOTE.md` | Branding notes |
| `ic_launcher.png` | App icon image |
| `.gitattributes` | Git settings |

### 🔀 MERGED (Carefully Combined)

| File/Folder | Strategy |
|-------------|----------|
| `data/` | Structure updated, user data preserved |
| `data/profiles.json` | Existing users kept |
| `data/breakdowns.json` | Merged (existing + new) |
| `data/users/` | Preserved completely |

### ⏭️ PROTECTED (Never Modified)

| File/Folder | Purpose |
|-------------|---------|
| `packaging/` | PyInstaller specs, Inno Setup, build scripts |
| `windows_service/` | Windows service installation scripts |
| `windows_tray/` | System tray application |
| `build_data/` | Build-time data files |
| `KenpoFlashcardsTrayLauncher.py` | Tray launcher script |
| `Kenpo_Vocabulary_Study_Flashcards.ico` | Windows icon |
| `server_config.json` | Server configuration |
| `INSTALL_WINDOWS.md` | Windows installation guide |
| `RUN_AS_WINDOWS_SERVICE.md` | Service setup guide |
| `PATCH_README.txt` | Patch instructions |
| `*.lnk` | Windows shortcuts |
| `Version-WebServerPackaged-*.txt` | Package version marker |

---

## Backup System

Every time you run the upgrade tool, it creates a timestamped backup:

```
.sync_backups/
├── 20260123_093940/
│   ├── app.py
│   ├── CHANGELOG.md
│   └── ...
└── 20260123_150230/
    └── ...
```

To restore from a backup:
```batch
copy ".sync_backups\20260123_093940\app.py" "app.py"
```

---

## Typical Workflow

1. **Download new web server release**
   ```
   KenpoFlashcardsWebServer-v6_1_0_v32.zip
   ```

2. **Run the upgrade tool**
   ```batch
   cd KenpoFlashcardsWebServer_Packaged
   ..\upgrade_tool\upgrade_webserver.bat ..\KenpoFlashcardsWebServer-v6_1_0_v32.zip
   ```

3. **Test the application**
   ```batch
   python app.py
   ```

4. **Update package version** (optional)
   - Edit `version.json` to bump packaged version
   - Rename `Version-WebServerPackaged-*.txt`

5. **Rebuild executables**
   ```batch
   cd packaging
   "2. build_exe.bat"
   "3. build_installer_inno.bat"
   ```

---

## Version Tracking

After upgrade, `version.json` will contain:

```json
{
  "name": "KenpoFlashcardsWebServer_Packaged",
  "version": "1.2.0",
  "build": 7,
  "webserver_version": "6.1.0",
  "webserver_build": 32,
  "last_sync": "2026-01-23T15:30:00"
}
```

---

## Troubleshooting

### "Python is not installed"
Install Python 3.8+ from https://python.org and ensure it's in your PATH.

### "Source not found"
Make sure the zip file or folder path is correct. Use full paths if needed.

### "Permission denied"
Close any programs using the files (editors, running server, etc.)

### "Merge conflict in data/"
The tool preserves your user data. If you see issues:
1. Check `.sync_backups/` for the previous version
2. Manually merge if needed

---

## Files Included

```
upgrade_tool/
├── upgrade_webserver_to_packaged.py   # Main Python script
├── upgrade_webserver.bat              # Windows batch wrapper
└── README.md                          # This file
```

---

## Requirements

- Python 3.8+
- No additional packages required (uses standard library only)
