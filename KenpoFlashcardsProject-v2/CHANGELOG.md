# Changelog — KenpoFlashcardsProject-v2 (Android)

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

## v4.3.0 (versionCode 19) — 2026-01-13
### Added
- **Model Selection**: Choose AI model for ChatGPT (gpt-4o default) and Gemini (gemini-1.5-flash default)
- Model settings sync with server alongside API keys
- **Admin Users SoT**: Fetches admin usernames from server on login (`/api/admin/users`)

### Fixed
- **Admin Button**: Fixed isAdmin() check not recognizing logged-in admin user
- Simplified admin username comparison logic with server-side Source of Truth

### Changed
- Admin screen renamed to "AI Access Settings"
- Push/Pull buttons now sync models along with API keys
- ChatGPT default model changed from gpt-3.5-turbo to gpt-4o
- AdminUsers object now caches admin list from server

---

## v4.2.0 (versionCode 18) — 2026-01-12
### Added
- **About Screen**: New About page in More with app info, creator contact (Sidney Shelton, Sidscri@yahoo.com), and feature overview
- **User Guide Screen**: Comprehensive printable/downloadable user guide accessible from About page
- **Login Screen**: Dedicated login page moved from Admin Settings (all users can access)
- **Sync Progress Screen**: New screen for Push/Pull progress sync and Breakdown sync
- **Gemini AI Integration**: Added Google Gemini API support for breakdown autofill
- **Breakdown AI Selector**: Users can choose between Auto Select (best result), ChatGPT, or Gemini for AI breakdowns
- **Auto-sync settings**: Option to auto-pull progress on login and auto-push changes when made
- **Pending sync indicator**: Visual indicator when offline changes need syncing
- **API key sync**: Admin can push/pull encrypted API keys to/from server
- **Version display**: Current app version shown in Settings screen

### Changed
- **Navigation restructure**: 
  - Login moved from Admin to its own Login screen in More
  - Sync functions moved to dedicated Sync Progress screen
  - Admin Settings only visible to admin users (Sidscri)
- Admin screen now only contains API key management (ChatGPT + Gemini)
- Improved login flow with auto-sync on successful login

### Security
- Admin Settings screen restricted to admin users only
- API keys can be encrypted and stored on server for cross-device access

---

## v4.1.0 (versionCode 17) — 2026-01-12
### Added
- Shared ID mapping for cross-device sync compatibility
- Helper mapping integration matching web server IDs

### Fixed
- Sync fully working with web server v5.2+

---

## v4.0.7.1 (versionCode 15/16) — 2026-01-11
### Added
- Shared card ID approach — both Android and Web resolve identical IDs for breakdowns/progress

### Fixed
- Cross-device sync alignment with web server

---

## v4.0.7 (versionCode 14) — 2026-01-10
### Fixed
- **Critical:** Changed login endpoint from `/api/login` to `/api/sync/login`
- Root cause: Server had duplicate `/api/login` routes; Flask used web session endpoint instead of token endpoint
- Auth tokens now correctly received and saved

---

## v4.0.5 (versionCode 12) — 2026-01-10
### Added
- Debug instrumentation for login response
- `debugInfo` field in `LoginResult` capturing token length and raw response preview
- This debug output revealed the root cause (server returning `{ok, user}` with no token)

---

## v4.0.4 (versionCode 11) — 2026-01-09
### Changed
- Rewrote login save logic to create fresh `AdminSettings` object instead of using `.copy()` on stale state
- Added token preview display showing first 8 characters

### Fixed
- Attempted fix for token not persisting (root cause was server-side)

---

## v4.0.3 (versionCode 10) — 2026-01-09
### Added
- `syncPushProgressWithToken()` and `syncPullProgressWithToken()` methods
- `CustomCardStatus` enum and `CustomSetSettings` for future Custom Set isolation

### Fixed
- Attempted fix for "No auth token" error (root cause was server-side)

---

## v4.0.2 (versionCode 9) — 2026-01-09
### Fixed
- Changed breakdowns endpoint from `/api/breakdowns` to `/api/sync/breakdowns`
- Resolved 401 errors on Sync Breakdowns

---

## v4.0.1 (versionCode 8) — 2026-01-09
### Fixed
- Button layout issues in All Cards screen
- Improved landscape mode for Custom/Learned screens
- Renamed status labels

### Added
- Reset to Default Settings option

---

## v4.0.0 (versionCode 7) — 2026-01-09
### Added
- Landscape mode support
- Group filtering for study screens
- Web app login/sync system (Admin screen)
- ChatGPT API integration for breakdown auto-fill
- Admin settings screen

---

## v3.0.1 — 2026-01-08
### Changed
- Speaker button: removed text, icon-only
- Renamed "Unlearned" to "To Study"

### Added
- Custom set star icon on study screens (To Study, Unsure, Learned>Study)

---

## v2.2.0 — 2026-01-08
### Added
- Custom study sets with star-based selection
- Group filtering dropdown
- Sort mode settings (JSON order / alphabetical / group-based)
- Admin section placeholder
- Return-to-top navigation

### Fixed
- Text visibility issues in Learned/All screens
- Breakdown button visibility across all views

---

## v2.1.0 — 2026-01-08
### Fixed
- Text visibility issues (purple text on dark background)

### Changed
- Updated versioning strategy for F-Droid updates

### Added
- QR code landing page

---

## v2.0.0 — 2026-01-07
### Added
- 3-state progress tracking (Active / Unsure / Learned)
- Term breakdowns with AI auto-fill capability
- Voice customization
- Dark theme styling
- Bottom navigation
- Custom study sets

---

# How to Update This Changelog

## Manual Updates
1. When you make changes, add them under `## Unreleased`
2. When releasing:
   - Update `versionCode` and `versionName` in `app/build.gradle`
   - Rename `## Unreleased` to `## vX.Y.Z (versionCode N) — YYYY-MM-DD`
   - Create a new empty `## Unreleased` section

## Version Numbering
- `versionName`: User-visible version (e.g., "4.1.0")
- `versionCode`: Integer that must increment for each release (e.g., 17)

## Build & Release
```bash
# Update build.gradle:
versionCode 17
versionName "4.1.0"

# Build release APK:
./gradlew assembleRelease

# Output: app/build/outputs/apk/release/app-release.apk
```
