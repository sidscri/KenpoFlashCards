# 🌐 KenpoFlashcardsWebServer (Web App Server)


This is the **web app server** project inside the `sidscri-apps` monorepo.

It runs independently from the Android app and typically provides:
- Login/authentication for the Android client
- Sync endpoints (push/pull progress)
- Shared breakdown endpoints (pull/push breakdowns)
- Optionally: a web UI for managing/using the flashcards data

---

## 📍 Location

> KenpoFlashcardsWebServer
Path: `KenpoFlashcardsWebServer/`

- CI workflow: `.github/workflows/kenpo-webserver-ci.yml`
- Manual ZIP workflow: `.github/workflows/kenpo-webserver-build-zip.yml`

>To run locally on Windows, open `KenpoFlashcardsWebServer/` and double-click:
- `START_KenpoFlashcardsWeb.bat`

# Workflow patch (monorepo)

Copy these files into your monorepo at:

sidscri-apps\.github\workflows\

- kenpo-webserver-ci.yml
- kenpo-webserver-build-zip.yml

These are designed for a project located at:
sidscri-apps\KenpoFlashcardsWebServer\

---

## 🚀 Quick start (Windows)

> If your project already has its own run instructions, follow those first.  
> The steps below are a standard safe baseline for Python-based web servers.

1) Open PowerShell in this folder:
```powershell
cd .\KenpoFlashcardsWebServer
```

2) Create and activate a virtual environment:
```powershell
py -m venv .venv
.\.venv\Scripts\activate
```

3) Install dependencies (if you have a requirements file):
```powershell
pip install -r requirements.txt
```

4) Run the server (examples — use the one that matches your project):
```powershell
py app.py
# or
py server.py
# or (Flask)
flask --app app run --host 0.0.0.0 --port 8009
```

Open:
- `http://localhost:8009`
- or your LAN IP: `http://<your-ip>:8009`

---

## 🔐 Data folder and secrets

Runtime data should **not** be committed to Git.

These are intentionally ignored by the root `.gitignore`:
- `KenpoFlashcardsWebServer/data/`
- `KenpoFlashcardsWebServer/logs/`
- common secret files like `.env`

If you store user accounts, passwords, or device sync state, put it under:
- `KenpoFlashcardsWebServer/data/`

---

## 🔌 Android endpoint expectations (v4.0)

The Android app expects endpoints like:
- `POST /api/login`
- `GET/POST /api/sync/pull|push`
- `GET/POST /api/breakdowns`

If your server routes differ, update the Android client accordingly.

---

## 🧩 Suggested structure (optional)

```text
KenpoFlashcardsWebServer/
├── app.py / server.py
├── requirements.txt
├── data/            # ignored by git
├── logs/            # ignored by git
└── README.md
```

## 🪟 Windows options

- Service + tray (Option A2): `../KenpoFlashcardsWebServer_Service_Tray/README.md`
- Packaged EXE/MSI builds: `../KenpoFlashcardsWebServer_Packaged_in_exe_msi/README.md`

Public files to reduce common 404 noise
=====================================

Files included:
- favicon.ico
- .well-known/security.txt
- robots.txt
- sitemap.xml

How to use (common setups)
--------------------------

Flask (recommended pattern):
    1) Put favicon.ico in your static/ folder (e.g., static/favicon.ico)
    2) Copy .well-known/security.txt into a folder your app can serve:
       - easiest: create a route for /.well-known/security.txt
    3) Put robots.txt and sitemap.xml in static/ and serve at /robots.txt and /sitemap.xml

Example Flask snippets:

--- app.py ---
from flask import Flask, send_from_directory
import os

app = Flask(__name__, static_folder="static")

@app.route("/favicon.ico")
def favicon():
    return send_from_directory(app.static_folder, "favicon.ico")

@app.route("/robots.txt")
def robots():
    return send_from_directory(app.static_folder, "robots.txt")

@app.route("/sitemap.xml")
def sitemap():
    return send_from_directory(app.static_folder, "sitemap.xml")

@app.route("/.well-known/security.txt")
def security_txt():
    return send_from_directory(os.path.join(app.root_path, ".well-known"), "security.txt")

If you already have a reverse proxy (Nginx/Caddy), you can serve these as static files there instead.

What new in version (v5.2, build v23)

Adds data/helper.json (auto-generated, auto-refreshed when kenpo_words.json changes)

Adds endpoint:

GET /api/sync/helper → returns canonical term_to_id, cards, version, etc.

Canonicalizes breakdown saves:

POST /api/breakdowns now maps the incoming breakdown to the canonical card id using the term via helper.json

Response includes the canonical id: { ok: true, id: "<canonical_id>" }

version.json ✅ (v5.2, build v23)

app.py ✅ (updated)

static/favicon.ico ✅ (generic “K” icon — safe placeholder)

static/.well-known/security.txt ✅ (placeholder; edit email)

BRANDING_NOTE.md ✅ (quick reminder)


IMPORTANT:
- Change Contact: mailto:security@example.com in security.txt to your real email.
- Optionally change the sitemap <loc> to your real public URL.

What new in version (v5.3, build v24)

What’s included in this redo
1) Features re-added on top of v5.2 v23

✅ version.json updated and used as the source of truth

✅ User dropdown menu (click User: …) shows:

Current version (from /api/version)

Admin link (only visible for admin user Sidscri)

About

User Guide

✅ New pages/routes:

/about (Created by Sidney Shelton + mailto:Sidscri@yahoo.com, description, major features)

/admin (health/version/helper/AI status)

/user-guide (full guide + print)

/user-guide.pdf (downloadable PDF generated on demand)

✅ /api/version already existed in your v23 baseline — kept and ensured it reads version.json

✅ requirements.txt updated to include reportlab (needed for PDF)

2) README + Changelog (going forward)

✅ Updated KenpoFlashcardsWebServer/README.md bottom section to reflect all new UI + routes + versioning

✅ Added KenpoFlashcardsWebServer/CHANGELOG.md

Includes a release entry for v5.3.0 build 24 and an Unreleased section for ongoing work

---

## 📄 License

Personal/educational use for learning American Kenpo Karate vocabulary.

---
