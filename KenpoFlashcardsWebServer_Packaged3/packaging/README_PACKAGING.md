# Packaging KenpoFlashcardsWeb (Windows EXE / Installer)

This folder lets you build a **single-click Windows EXE** (tray app that starts the web server in the background),
and optionally produce an **installer** (EXE via Inno Setup, or MSI via WiX).

## What you get
- **EXE (recommended)**: a tray app that runs the Flask server in the background and opens the UI in your browser.
- **Installer**:
  - **Option 1 (easy EXE installer)**: Inno Setup creates a standard Windows installer `.exe`.
  - **Option 2 (true MSI)**: WiX Toolset creates an `.msi` (more setup, but “real MSI”).

---

## 1) Build a portable EXE (PyInstaller)

### Prereqs
- Windows 10/11
- Python **3.10+** recommended (works with 3.8 too, but 3.10+ is smoother)
- From PowerShell / CMD, in the project root (same folder as `app.py`):

### One-time install
```bat
py -m pip install -r packaging\requirements_packaging.txt
```

### Build
```bat
packaging\build_exe.bat
```

Output:
- `dist\KenpoFlashcardsTray\KenpoFlashcardsTray.exe` (one-folder build)
- (Optional) If you enable one-file mode in the spec, you’ll get a single EXE, but start-up is slower.

Run it:
- Double-click the EXE. You’ll see a tray icon.
- Right-click tray icon for Open / Restart / Exit.

---

## 2) Build an EXE installer (Inno Setup) — easiest “installer”
### Prereqs
- Install **Inno Setup** (free)
- Then run:
```bat
packaging\build_installer_inno.bat
```
Output:
- `packaging\output\KenpoFlashcardsWebSetup.exe`

---

## 3) Build an MSI (WiX Toolset) — true MSI
### Prereqs
- Install WiX Toolset v3.x (or use v4 with small script tweaks)
- Then run (PowerShell):
```powershell
powershell -ExecutionPolicy Bypass -File packaging\build_msi_wix.ps1
```
Output:
- `packaging\output\KenpoFlashcardsWeb.msi`

---

## Notes
- `__pycache__` is **generated automatically by Python at runtime**. It won’t exist until Python runs a `.py` file.
  It should NOT be shipped in the repo/zip.
- The tray EXE uses the same JSON auto-discovery logic your server uses.
  If you move your Android project folder name (e.g. `KenpoFlashcardsProject-v2`), the auto-discovery should still work
  as long as the path stays under:
  `C:\Users\Sidscri\Documents\GitHub\sidscri-apps\<PROJECT>\app\src\main\assets\kenpo_words.json`
