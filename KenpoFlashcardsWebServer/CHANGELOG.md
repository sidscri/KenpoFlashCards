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

## 7.0.2 (build 35) — 2026-01-23

### Added
- **🎲 Pick Random N**: Click dice button in Custom Set to study random subset of starred cards
- **User Management Modal**: Click "Total Users" in admin to view/edit all users
- **Admin User Editing**: Grant/revoke admin status, reset passwords
- **Password Reset**: Admins can reset user passwords to default (123456789) with required change on next login
- **System Status Feed**: Activity-style status display in admin dashboard

### Fixed
- **Edit Decks Page**: Now opens correctly (added missing hideAllViews function)
- **PDF Download**: Fixed Internal Server Error (added send_file import)
- **Admin Quick Actions**: Highlight now follows active tab (Health/Sync/AI)

### Changed
- **User Guide**: Complete redesign with better layout, feature cards, keyboard shortcuts table
- **Admin Dashboard**: Removed Card Groups section, replaced with System Status feed
- **Admin UI**: Cleaner quick action buttons, clickable user stats card

---

## 7.0.1 (build 34) — 2026-01-23

### Added
- **Reshuffle button visible**: ⟳ button now always visible on study cards (works even without random mode)
- **Search clear X button**: Clear search with one click
- **Randomize Custom Set setting**: Control random order separately for Custom Set
- **Speak pronunciation only toggle**: Option to speak only pronunciation instead of term

### Changed
- Reshuffle works regardless of random toggle state (instant shuffle on demand)

---

## 7.0.0 (build 33) — 2026-01-23

### Added
- **Edit Decks page**: New page accessible from Settings with three tabs:
  - **Switch tab**: View and switch between study decks, create new decks
  - **Add Cards tab**: Manually add cards with term, definition, pronunciation, group
  - **Deleted tab**: View and restore deleted cards
- **Deck management**: Create and delete custom study decks
- **User cards CRUD**: Add, edit, and delete user-created cards
- **AI generation buttons**:
  - Generate Definition (3 AI options to choose from)
  - Generate Pronunciation
  - Generate Group suggestions (considers existing groups)
- **API endpoints**:
  - `GET/POST /api/decks` - List and create decks
  - `DELETE /api/decks/:id` - Delete a deck
  - `GET/POST/PUT/DELETE /api/user_cards` - User cards CRUD
  - `POST /api/ai/generate_definition` - AI definition generation
  - `POST /api/ai/generate_pronunciation` - AI pronunciation generation
  - `POST /api/ai/generate_group` - AI group suggestions

### Changed
- Settings page now has "Edit Decks" button at top for quick access

---

## 6.1.0 (build 32) — 2026-01-23

### Added
- **Sync Progress page**: New settings section matching Android app with Push/Pull buttons, login status banner, auto-sync info, and breakdown sync
- **Settings tabbed navigation**: Quick nav tabs for Study, Display, Voice, Sync, and AI sections with highlighted active tab
- **Star button on study cards**: Toggle Custom Set membership directly from study view
- **Sort by status dropdown**: All list can now be sorted by Unlearned, Unsure, Learned, or Alphabetical
- **Logout in user menu**: Moved logout option to user dropdown menu with icon

### Changed
- Settings page completely redesigned with app-like card layout and modern buttons
- Buttons now use gradient backgrounds matching Android app style (primary blue, success green, danger red)
- Removed standalone logout button from header controls
- More button renamed from "Show settings" to "⚙️ More"

---

## 6.0.0 (build 31) — 2026-01-22

### Added
- **Custom Set (Starred Cards)**: New ⭐ tab for studying a personalized set of starred cards
  - ☆/★ toggle buttons in All list to add/remove cards
  - Internal status tracking (Active/Unsure/Learned) within custom set
  - Filter views: All, Unsure, Learned within custom set
  - API endpoints: `/api/custom_set`, `/api/custom_set/add`, `/api/custom_set/remove`, `/api/custom_set/toggle`, `/api/custom_set/set_status`, `/api/custom_set/clear`
- **Show breakdown on definition toggle**: New setting to show/hide breakdown on card back
- **Auto-speak on card change**: Automatically speaks term when navigating prev/next
- **Speak definition on flip**: Automatically speaks definition when card flips to back
- **Admin Dashboard redesign**: Modern dashboard with stat cards, progress bars, AI status indicators
  - Visual stats for Users, Cards, Breakdowns, Learning Progress
  - AI Configuration panel with ChatGPT/Gemini status
  - Quick Actions section for health checks
  - Card groups display and admin users list
- **API endpoint**: `/api/admin/stats` for comprehensive admin statistics
- Cards API now includes `in_custom_set` field

### Changed
- Admin page completely redesigned with modern UI, gradients, and animations
- Settings now include `show_breakdown_on_definition`, `auto_speak_on_card_change`, `speak_definition_on_flip`

---

## 5.5.3 (build 30) — 2026-01-18

- Sync: progress entries now include per-card `updated_at` timestamps
- Sync: push/pull merge uses `updated_at` (newer wins); supports offline pending queue on Android
- API: /api/sync/push and /api/sync/pull accept/return object-form progress entries

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

