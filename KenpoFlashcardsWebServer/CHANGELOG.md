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

## 5.5.2 (build 29) — 2026-01-14
### Added
- **Version/docs sync with Android App 4.4.2 (v22)

### Changed
- No functional server code changes in this patch release.

---

## v5.5.1 (build 28) — 2026-01-13
### Added
- **GET /api/sync/apikeys**: New endpoint for all authenticated users to pull API keys
  - Any logged-in user can retrieve API keys (read-only)
  - Allows non-admin users to use AI breakdown features
  - Admin-only `/api/admin/apikeys` POST still required for saving keys

### Changed
- API keys are now shared with all authenticated users on login
- Admin access only required to modify/save API keys, not to use them

---

## v5.5.0 (build 27) — 2026-01-13
### Added
- **AI Access Page**: New `/ai-access.html` web page for managing API keys
- **Model Selection**: Choose ChatGPT and Gemini models from web UI
- **Startup Key Loading**: Server loads encrypted API keys from file on startup
- **Web API endpoints**: `/api/web/admin/apikeys` GET/POST for session-based admin access
- **Admin Users SoT**: `data/admin_users.json` - Source of Truth for admin usernames
- **Admin Users Endpoint**: `GET /api/admin/users` - returns admin usernames list

### Changed
- API keys now include model selection (chatGptModel, geminiModel)
- Keys loaded from `api_keys.enc` override environment variables
- Admin page now prominently links to AI Access Settings
- `_load_admin_usernames()` loads from JSON file with fallback

### Security
- Environment variable API keys no longer needed (can be removed from START_KenpoFlashcardsWebServer.bat)

---

## v5.4.0 (build 26) — 2026-01-12
### Added
- **Encrypted API Key Storage**: Admin can store ChatGPT and Gemini API keys encrypted on server
- **POST /api/admin/apikeys**: Push encrypted API keys to server (admin only)
- **GET /api/admin/apikeys**: Pull decrypted API keys from server (admin only)
- **GET /api/admin/status**: Check if current user is admin
- Admin users defined in `ADMIN_USERNAMES` set (default: sidscri)

### Security
- API keys encrypted using XOR with HMAC integrity check
- Keys derived from server's secret_key.txt using SHA-256
- Encrypted file (`api_keys.enc`) safe for git commits

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

## v5.5.2 (v29) – Login fix

- Fixed login regression for admin users after moving admin usernames to **admin_users.json**.
- Login is **case-insensitive**.
- For personal LAN deployments: admin users may log in from the private network with a **blank password** (to avoid lockouts).
  - Recommended: set an admin password in profiles.json if you want strict security.

### Logs access change

The admin Logs panel no longer requires localhost. It now requires an **authenticated admin session**.

