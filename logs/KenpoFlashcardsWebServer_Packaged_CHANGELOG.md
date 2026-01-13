# Changelog — KenpoFlashcardsWebServer_Packaged_in_exe_msi

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

## v1.0.0 (build 1) — 2026-01-12 [BETA]
### Added
- Initial packaged release
- EXE installer for standalone deployment
- MSI installer for enterprise/managed deployment
- Packaging scripts and build instructions
- GitHub Actions workflow for automated builds

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
# Build EXE (PyInstaller):
pyinstaller --onefile --windowed app.py

# Build MSI (requires WiX Toolset or similar):
# See packaging/README.md for detailed instructions
```

## Relationship to Core Server
This package wraps KenpoFlashcardsWebServer. When updating:
1. Pull latest changes from core server
2. Update version numbers in both projects
3. Rebuild installers
4. Test installation on clean Windows system
