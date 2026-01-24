# Changelog — Study Flashcards (formerly KenpoFlashcardsWebServer)

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

## 7.0.7 (build 40) — 2026-01-24

### Added — Android Sync API
- **`GET /api/vocabulary`**: Returns kenpo_words.json (canonical source for built-in vocabulary)
- **`GET /api/sync/decks`**: Pull all decks for Android sync (requires auth)
- **`POST /api/sync/decks`**: Push deck changes from Android (requires auth)
- **`GET /api/sync/user_cards`**: Pull user-created cards (requires auth, optional deck_id filter)
- **`POST /api/sync/user_cards`**: Push user cards from Android (requires auth)
- **`DELETE /api/sync/user_cards/<card_id>`**: Delete a user card (requires auth)

### Changed
- **kenpo_words.json** now stored in `data/` folder as canonical source
- Android app can now sync decks and user cards with web server
- Full cross-platform deck and card sharing

---

## 7.0.6 (build 39) — 2026-01-24

### Added
- **Rebranded to "Study Flashcards"**: Generic app name that works for any subject
- **Header shows active deck**: App title now shows "Study Flashcards • [Deck Name]"
- **Set Default Deck**: ★ button to set a deck as the default startup deck
- **API endpoint**: `POST /api/decks/:id/set_default` - Sets a deck as default

### Changed
- **Groups filter respects active deck**: Group dropdown now shows groups from the active deck, not just Kenpo
- **Page title**: Changed from "Kenpo Flashcards (Web)" to "Study Flashcards"

### Fixed
- **Deck resets on page refresh**: Now properly loads saved `activeDeckId` before initializing app
- **Groups showing Kenpo for custom decks**: Groups API now accepts `deck_id` parameter
- **Deck switching not fully reloading**: Now reloads groups, counts, cards, and header on switch

---

## 7.0.5 (build 38) — 2026-01-24

### Added
- **🤖 AI Deck Generator**: New tab in Edit Decks to generate flashcards using AI
  - **Keywords**: Enter topic/keywords to generate cards (e.g., "Basic Spanish Words 3rd grade level")
  - **Photo**: Upload image of study material, AI extracts vocabulary
  - **Document**: Upload PDF/TXT/MD files, AI creates flashcards from content
  - Selection UI: Review generated cards, select which to add
  - Max cards configurable 1-200
  - Default keywords: Uses deck name + description if no keywords entered
- **Edit Deck**: ✏️ button to edit deck name and description
- **Logout confirmation**: "Are you sure?" prompt before logging out
- **📖 Comprehensive User Guide**: Complete rewrite with all features documented
  - Table of contents with jump links
  - Step-by-step instructions for all features
  - Tip boxes, warning boxes, and keyboard shortcuts table
  - Sections: Getting Started, Study Tabs, Edit Decks, AI Generator, Custom Set, Breakdowns, Settings, Sync, Troubleshooting
- **📱 Interactive About Page**: New tabbed interface
  - Overview with version card and quick start
  - Features grid with icons
  - Technology stack badges
  - Changelog summary
  - Contact section with email button
- **API endpoints**:
  - `POST /api/ai/generate_deck` - Generate cards from keywords, photo, or document
  - `POST /api/decks/:id` - Update deck name/description

### Changed
- **Logout moved to bottom** of user menu with red styling
- **AI definitions context-aware**: Uses deck name/description instead of always "Korean martial arts"
- **AI pronunciation**: Now generic, works for any language
- **AI group suggestions**: Now generic, not Kenpo-specific
- **Generate button**: Smaller "🔍 Generate" instead of full text
- **Max cards**: Increased from 50 to 200
- **Header card count**: Now shows count for active deck (not always 88)

### Fixed
- **Deck switching not working**: Now passes `deck_id` explicitly in all API calls
- **Active deck not loading on startup**: Loads saved `activeDeckId` from settings before loading cards
- **Cards not appearing after adding**: Added proper refresh of counts and study deck
- **AI generation errors**: Added detailed server-side logging for debugging
- **Duplicate cards in AI results**: Filters out terms that already exist in deck

---

## 7.0.4 (build 37) — 2026-01-24

### Added
- **AI Deck Generator** (initial implementation): Generate flashcards from keywords, photos, or documents
- **User cards in study deck**: User-created cards now merge with built-in cards

### Fixed
- **PDF download**: Replaced with "Print User Guide" button (avoids reportlab compatibility issues)

---

## 7.0.3 (build 36) — 2026-01-24

### Fixed
- **Health check**: Now correctly reports Kenpo JSON file status (was always showing Missing)
- **AI card generation**: API keys now loaded from encrypted storage at startup (was only reading from environment variables)
- **Custom Set random toggle**: Now properly persists when toggled (was not saving to settings)
- **Reshuffle button**: Always visible and properly sized (smaller, inline with toggle)

### Changed
- Reshuffle button now works anytime (not just when random is enabled)

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
