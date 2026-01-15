# Changelog — KenpoFlashcardsWebServer_Service_Tray

Windows system tray application for running KenpoFlashcardsWebServer in the background.

The format is simple and practical:
- **Added**: new user-facing features
- **Changed**: behavior changes, refactors
- **Fixed**: bug fixes
- **Security**: auth/permissions/security changes

---

## Unreleased
- (Add changes here as you work. Move them into a release when you publish.)

---

## v1.0.0 (build 1) — 2026-01-12 [BETA]
### Added
- Initial release
- System tray icon with context menu
- Start/Stop server controls
- "Open in Browser" quick action
- Runs Flask server in background (similar to Sonarr/Radarr)
- Auto-start option (Windows startup integration)
- Status indicator in tray icon

### Notes
- **Beta release**: Testing tray functionality and background service behavior
- Based on KenpoFlashcardsWebServer v5.3.x
- Designed for always-on home server deployment

### Known Limitations
- Windows only (uses `pystray` or similar)
- Server logs viewable only via log file (not in tray UI)
- Must be run as administrator for auto-start functionality

---

# How to Update This Changelog

## Manual Updates
1. When you make changes, add them under `## Unreleased`
2. When releasing, rename `## Unreleased` to `## vX.Y.Z (build N) — YYYY-MM-DD`
3. Create a new empty `## Unreleased` section at the top

## Running the Tray App
```bash
# From the project directory:
python tray_app.py

# Or if packaged as EXE:
KenpoFlashcardsTray.exe
```

## Relationship to Core Server
This wraps KenpoFlashcardsWebServer with a tray interface. When updating:
1. Pull latest changes from core server
2. Test tray start/stop functionality
3. Verify server responds correctly when started via tray
4. Update version numbers in both projects
