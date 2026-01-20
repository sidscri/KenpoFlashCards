# KenpoFlashcards Web Server (Windows) — Installer Build + Install Instructions

This project can be used two ways on Windows:

1) **End-user install (recommended):** install using a **Setup EXE** (Inno Setup) or **MSI** (WiX).  
2) **Developer run (no install):** run with Python directly.

This document focuses on **creating a true installer** and then installing it.

---

## A) If you already have the installer (fastest)

If you already have one of these files:
- `KenpoFlashcardsWebSetup.exe` (Setup EXE)
- `KenpoFlashcardsWeb.msi` (MSI)

Then:
1. Double‑click the installer.
2. Follow the prompts.
3. Launch **KenpoFlashcards Web Server** from the Start Menu (or desktop shortcut if selected).
4. The app runs locally and opens the UI in your browser:
   - http://127.0.0.1:8009

If Windows SmartScreen appears:
- Click **More info** → **Run anyway** (common for unsigned installers).

---

## B) Build the Setup EXE installer (recommended “true installer”)

You will build a tray EXE first (PyInstaller), then package it into a Setup EXE (Inno Setup).

### Prerequisites (one‑time)

1. **Windows 10/11**
2. **Python 3.10+ (64‑bit)**
   - During install, check: **“Add python.exe to PATH”**
3. **Inno Setup 6** (free)

### 1) Unzip the project

Unzip the project folder, for example:

`C:\Kenpo\KenpoFlashcardsWebServer_Packaged\`

You should see:
- `app.py`
- `packaging\` folder

### 2) Open a terminal in the project root

Open the folder that contains `app.py`, then:
- Click the File Explorer address bar
- Type `cmd`
- Press **Enter**

(You can also use PowerShell if you prefer.)

### 3) Install packaging dependencies

From the project root:

```bat
py -m pip install -r packaging\requirements_packaging.txt
```

### 4) Build the tray EXE (PyInstaller)

From the project root:

```bat
packaging\build_exe.bat
```

Expected output:
- `dist\KenpoFlashcardsTray\KenpoFlashcardsTray.exe`

### 5) Build the Setup EXE (Inno Setup)

From the project root:

```bat
packaging\build_installer_inno.bat
```

Expected output:
- `packaging\output\KenpoFlashcardsWebSetup.exe`

### 6) Install

Double‑click:
- `packaging\output\KenpoFlashcardsWebSetup.exe`

---

## C) Build a true MSI installer (optional)

If you specifically need an **MSI** (for enterprise deployment, GPO, etc.), use the WiX build.

### Prerequisites
- **WiX Toolset v3.x** installed and available on PATH
  - The script expects WiX tools such as `candle`, `light`, etc.

### Build the MSI

From the project root (PowerShell):

```powershell
powershell -ExecutionPolicy Bypass -File packaging\build_msi_wix.ps1
```

Expected output:
- `packaging\output\KenpoFlashcardsWeb.msi`

---

## D) After install — how it works

- The app runs **locally on your PC** and serves the web UI to your browser.
- Default URL:
  - http://127.0.0.1:8009

### Firewall prompt
The first time it runs, Windows may prompt about firewall access.  
Allow it on **Private networks** (recommended). This is a local app; it does not need Public network access.

---

## E) Troubleshooting

### “py is not recognized” / “python is not recognized”
- Reinstall Python and be sure to check **Add to PATH**.
- Close and re-open your terminal after installing Python.

### Pip install fails
Try upgrading pip first:
```bat
py -m pip install --upgrade pip
```
Then retry:
```bat
py -m pip install -r packaging\requirements_packaging.txt
```

### Inno Setup not found / ISCC.exe missing
- Install **Inno Setup 6**.
- If your install path is custom, open `packaging\build_installer_inno.bat` and update the `ISCC` path.

### Port 8009 already in use
- Something else is using the port.
- Close the other app, or stop the existing KenpoFlashcards process from Task Manager, then relaunch.

### SmartScreen / Antivirus warnings
- These builds are typically **unsigned**.
- SmartScreen: **More info → Run anyway**.
- If your AV quarantines files, add an exception for the install folder (only if you trust the source).

---

## F) Developer run (no installer)

If you just want to run it without building an installer:

```bat
py -m venv .venv
.venv\Scripts\activate
py -m pip install -r requirements.txt
py app.py
```
Then open:
- http://127.0.0.1:8009
