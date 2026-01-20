# Changelog — Kenpo Flashcards Web Server (Packaged)

All notable changes to the Windows packaged/installer distribution are documented here.

## v1.0.0 (build 3) — 2026-01-20

First stable installer release (graduating from the vbeta line).

### Added
- Updated bundled web server to **v5.5.2 (build 29)**, bringing in:
  - AI Access UI for API key management and model selection.
  - Encrypted API key storage (`data/api_keys.enc`).
  - Shared Key Mode (optional one-key-for-all-authenticated-users).
  - Improved Sync merge logic using `updated_at`, better handling of queued/offline updates.
  - Admin pages (About/Admin/User Guide) and PDF generation.
- Inno Setup installer installs the complete PyInstaller folder build (includes `_internal\` and dependencies) into **Program Files** and adds Start Menu shortcuts.

### Changed
- The installer now copies the full PyInstaller output folder (`dist\KenpoFlashcardsTray\*`) so the app runs without requiring “extra files” to be manually copied.

### Fixed
- Packaging reliability improvements from the beta line (build scripts, Inno Setup defaults).

### Known issues / notes
- **Windows Defender/AV false positives:** Unsigned PyInstaller EXEs can be quarantined. For distribution, consider code signing.
- **Data folder permissions:** If installed under Program Files, the app may need elevation once to create/write its `data/` directory.

## vbeta (build 2) — 2026-01-19

### Fixed
- Packaging script fixes and documentation updates.

## vbeta (build 1) — 2026-01-19

### Added
- Initial packaged beta release (PyInstaller + Inno Setup build scripts).
