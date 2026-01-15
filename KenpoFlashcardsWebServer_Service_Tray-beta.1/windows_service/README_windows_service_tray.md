# Run Kenpo Flashcards Web like Sonarr (Windows Service + Tray)

This folder adds a **Windows service** (so the webapp runs in the background after boot) and an optional **system tray icon** (near Wi‑Fi/Bluetooth) to open / start / stop the app.

You chose: **Option A (Service + Tray), option 2 (NSSM)**.

---

## What you get

- **Windows service (NSSM)**: runs `app.py` in the background, starts on boot.
- **Tray icon** (optional): shows an icon in the system tray with menu items:
  - Open Kenpo Flashcards
  - Start / Stop / Restart service
  - Quit tray (service keeps running)

---

## Prereqs

1. **Python 3.8+** installed (your setup uses Python 3.8).
2. This project extracted somewhere permanent, e.g.:
   `C:\PersonalServers\KenpoFlashcardsWeb`
3. **NSSM** (Non-Sucking Service Manager) downloaded by you:
   - Place `nssm.exe` in: `windows_service\nssm.exe`

> Why isn't `__pycache__` in the ZIP?
> Python generates `__pycache__` automatically when code runs. It should not be shipped.

---

## Install as a Windows service (NSSM)

1. Open **Windows Terminal / CMD as Administrator**
2. `cd` into your project folder
3. Run:

```
windows_service\INSTALL_Service_NSSM.bat
```

This will:
- create/update `.venv`
- install `requirements.txt`
- install the service named **KenpoFlashcardsWeb**
- set it to auto-start
- set default env values (you can change them)

### Set your OpenAI key / model (recommended)

After install, set env vars for the service:

```
windows_service\SET_Service_Env.bat
```

At minimum set:
- `OPENAI_API_KEY`
- `OPENAI_MODEL` (defaults to `gpt-4o-mini`)
- `KENPO_WEB_PORT` (defaults to `8009`)

Then restart:

```
windows_service\RESTART_Service.bat
```

---

## Uninstall service

Run as Administrator:

```
windows_service\UNINSTALL_Service_NSSM.bat
```

---

## Install tray icon (optional)

The tray icon uses `pystray` and `Pillow`.

1. Run:

```
windows_tray\INSTALL_Tray_Dependencies.bat
```

2. Start the tray app:

```
windows_tray\START_Tray.bat
```

### Auto-start tray on login (recommended)

Create a shortcut to `windows_tray\START_Tray.bat` and put it in:

`Win + R` → `shell:startup`

That starts the tray for *your user* when you log in, while the **service** starts at boot.

---

## Ports / URLs

Default:
- http://localhost:8009
- http://127.0.0.1:8009

If you change `KENPO_WEB_PORT`, open the new port.

---

## JSON path auto-mapping

The server already auto-searches for your `kenpo_words.json` under your GitHub root.

If you ever need to force a specific path, set:

- `KENPO_WORDS_JSON` = full file path to `kenpo_words.json`

(Use `windows_service\SET_Service_Env.bat` to set it for the service.)
