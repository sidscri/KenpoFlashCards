# Changelog — KenpoFlashcardsWebServer_Packaged

Windows installer/packaged distribution of KenpoFlashcardsWebServer.

The format is simple and practical:
- **Added**: new user-facing features
- **Changed**: behavior changes, refactors
- **Fixed**: bug fixes
- **Security**: auth/permissions/security changes

---

## Unreleased
- (Add changes here as you work. Move them into a release when you publish.)

---

## vbeta v2 (build 2) — 2026-01-16
### Fixed
- GitHub Actions workflow now builds successfully
- Fixed `kenpo_tray.spec` to use actual project structure (`ic_launcher.png` in root)
- Fixed workflow paths to match folder name `KenpoFlashcardsWebServer_Packaged`
- Added Flask and requests to hidden imports for PyInstaller
- Removed reference to non-existent `kenpo_server.spec`

### Changed
- Simplified workflow to build only tray EXE + Inno installer
- Workflow uses `working-directory` for cleaner step commands
- Spec file now bundles `app.py` for tray launcher import

---

## vbeta v1 (build 1) — 2026-01-12
### Added
- Initial packaged release
- EXE installer for standalone deployment (Inno Setup)
- MSI installer for enterprise/managed deployment (WiX)
- Packaging scripts and build instructions
- GitHub Actions workflow for automated builds
- KenpoFlashcardsTrayLauncher for Sonarr-style tray app

### Notes
- **Beta release**: Testing installer functionality and deployment scenarios
- Based on KenpoFlashcardsWebServer v5.3.x
- Includes all web server features (sync API, breakdowns, user management)

### Known Limitations
- Requires manual configuration of `data/` directory on first run
- OpenAI API key must be set via environment variable or config file

---

# How to Update This Changelog

## Manual Updates
1. When you make changes, add them under `## Unreleased`
2. When releasing, rename `## Unreleased` to `## vX.Y.Z (build N) — YYYY-MM-DD`
3. Create a new empty `## Unreleased` section at the top

## Build Process
```bash
# Build Tray EXE (PyInstaller):
python -m PyInstaller packaging/pyinstaller/kenpo_tray.spec --noconfirm

# Build Installer (Inno Setup):
iscc packaging/installer_inno.iss
```

## Relationship to Core Server
This package wraps KenpoFlashcardsWebServer. When updating:
1. Pull latest changes from core server
2. Update version numbers in both projects
3. Rebuild installers
4. Test installation on clean Windows system
