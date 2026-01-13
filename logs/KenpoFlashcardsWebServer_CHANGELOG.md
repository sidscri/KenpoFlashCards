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

## v5.3.1 (build 25) — 2026-01-12
### Fixed
- **Critical:** Fixed duplicate `/api/login` endpoint conflict — Flask was routing Android login requests to web session endpoint (line 781) instead of token-based endpoint (line 1272)
- Changed Android login endpoint from `/api/login` to `/api/sync/login` to avoid route collision
- Auth tokens now correctly returned to Android app

### Security
- Added `.gitignore` entries for API keys and secrets (`gpt api.txt`, `START_KenpoFlashcardsWebServer.bat`)
- Excluded `data/` directory from version control (contains user passwords and progress)

---

## v5.3.0 (build 24) — 2026-01-11
### Added
- `version.json` + `GET /api/version` endpoint
- User dropdown menu (click "User: …" to open)
- `/about` page with creator/contact info
- `/admin` diagnostics page (health/version/helper/AI status)
- `/user-guide` page (print-friendly) + `/user-guide.pdf` download

### Changed
- Added dependency on `reportlab` for generating the User Guide PDF

### Fixed
- Sync regression from v5.2 — push not applying server-side changes

---

## v5.2.0 (build 23) — 2026-01-11
### Fixed
- End-to-end sync confirmed working
- Server-side helper mapping for stable card IDs across Android and Web

---

## v5.1.1 (build 22) — 2026-01-12
### Added
- `version.json` for release tracking
- Generic favicon (trademark-safe branding)
- `static/.well-known/security.txt`
- `robots.txt`, `sitemap.xml` to reduce 404 noise

---

## v5.1.0 (build 21) — 2026-01-11
### Added
- About/Admin/User Guide pages
- User dropdown menu with version display
- Admin link visible only for user 'sidscri'

### Changed
- Added `reportlab` dependency for PDF generation

---

## v5.0.0 (build 20) — 2026-01-10
### Added
- Stable card ID mapping (helper.json) for cross-device sync
- Last known working sync baseline

---

## v4.2.0 (build 18) — 2026-01-08
### Added
- Settings reorganization with Apply-to-all logic
- Admin-only breakdown overwrite protection
- Definition-side breakdown display option
- Breakdown modal with OpenAI auto-fill

### Fixed
- Python 3.8 compatibility (replaced PEP 604 unions with `typing.Optional`)
- Dark theme dropdown styling
- Random order toggle positioning
- `updateRandomStudyUI` JS error

### Changed
- Renamed "Definition first" to "Reverse the cards (Definition first)"
- Tighter spacing for small screens

---

## v4.0.0 — 2026-01-07
### Fixed
- Python SyntaxError in `app.py` (invalid string escaping)
- Server boot and UI loading confirmed

---

# How to Update This Changelog

## Manual Updates
1. When you make changes, add them under `## Unreleased`
2. When releasing, rename `## Unreleased` to `## vX.Y.Z (build N) — YYYY-MM-DD`
3. Create a new empty `## Unreleased` section at the top

## Suggested Workflow
```bash
# Before committing significant changes:
1. Edit CHANGELOG.md
2. Add entry under "Unreleased"
3. Commit with message referencing the change

# When releasing:
1. Change "Unreleased" to version number + date
2. Add new "Unreleased" section
3. Tag the release: git tag v5.3.1
4. Push: git push && git push --tags
```

## Automation Options
- **GitHub Actions**: Auto-generate changelog from commit messages using `conventional-changelog`
- **Pre-commit hook**: Remind to update changelog if certain files changed
- **Release script**: Prompt for changelog entry when bumping version
