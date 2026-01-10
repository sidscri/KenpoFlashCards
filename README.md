# 🥋 Kenpo Flashcards Suite (Android + Web)

This repository (**sidscri-apps**) contains two separate Kenpo flashcards projects that live side-by-side:

- **KenpoFlashcardsProject-v2/** — Android app (F-Droid build/install)
- **KenpoFlashcardsWeb/** — Core web app server (sync + web UI / API)
- **KenpoFlashcardsWebServer_Service_Tray/** — Windows Service + tray (Option A2)
- **KenpoFlashcardsWebServer_Packaged_in_exe_msi/** — Packaged builds (portable EXE / installer / MSI)

> If you’re looking for the full Android app feature list and screenshots, start here:  
> **KenpoFlashcardsProject-v2/README.md**

---

## 📦 Repository Layout

```text
sidscri-apps/
├── KenpoFlashcardsProject-v2/                     # Android app (F-Droid)
├── KenpoFlashcardsWeb/                            # Core web server + web UI/API
├── KenpoFlashcardsWebServer_Service_Tray/          # Windows Service + tray (Option A2)
└── KenpoFlashcardsWebServer_Packaged_in_exe_msi/   # Packaging: portable EXE / installer / MSI
```

---

# 📱 KenpoFlashcardsProject-v2 (Android)

An Android flash-card app designed to help students of **American Kenpo Karate** learn, memorize, and review Kenpo vocabulary efficiently using categorized, interactive flash cards.

- **Location:** `KenpoFlashcardsProject-v2/`
- **Docs:** `KenpoFlashcardsProject-v2/README.md`
- **Current Version:** 4.0
- **Sync Server (v4.0):** `sidscri.tplinkdns.com:8009`

---

# 🌐 KenpoFlashcardsWeb (Web App Server)

A separate web application/server that runs independently from the Android app codebase.

- **Location:** `KenpoFlashcardsWeb/`
- **Docs:** `KenpoFlashcardsWeb/README.md`

## What it does (high-level)
- Provides login + sync endpoints for the Android app (v4.0)
- Can host a web UI and/or API used by devices to push/pull progress and breakdown data

## Windows background + packaging options

If you want a **Sonarr-style** experience on Windows (run on boot + tray icon), use:
- `KenpoFlashcardsWebServer_Service_Tray/` — see `KenpoFlashcardsWebServer_Service_Tray/README.md`

If you want to create **Windows distributables** (portable EXE, installer EXE, MSI), use:
- `KenpoFlashcardsWebServer_Packaged_in_exe_msi/` — see `KenpoFlashcardsWebServer_Packaged_in_exe_msi/README.md`


---

## 🔐 Notes on data & secrets

Do **not** commit secrets or user data.
This repo is set up to ignore common generated folders and sensitive runtime data (see root `.gitignore`).

---

## 📄 License

Personal/educational use for learning American Kenpo Karate vocabulary.
