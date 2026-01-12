# Changelog — KenpoFlashcardsWebServer

All notable changes to this project will be documented in this file.

The format is simple and practical:
- **Added**: new user-facing features
- **Changed**: behavior changes, refactors
- **Fixed**: bug fixes
- **Security**: auth/permissions/security changes

---

## Unreleased
- (Add changes here as you work. Move them into a release when you publish.)

---

## v5.3.0 (build 24) — 2026-01-11
### Added
- `version.json` + `GET /api/version` endpoint
- User dropdown menu (click “User: …” to open)
- `/about` page with creator/contact info
- `/admin` diagnostics page (health/version/helper/AI status)
- `/user-guide` page (print-friendly) + `/user-guide.pdf` download

### Changed
- Added dependency on `reportlab` for generating the User Guide PDF

### Fixed
- (Sync already confirmed working prior to this release; add any fixes here if needed.)
