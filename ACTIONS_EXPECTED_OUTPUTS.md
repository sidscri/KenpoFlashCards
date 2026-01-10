# Expected GitHub Actions outputs

These workflows are set up to produce and upload build artifacts for Windows.

## When they run
- On push to `main` when files under the matching folder change
- On PRs that touch those folders
- Manually via **Actions → Run workflow**

## What they produce
Each workflow tries, in this order:
1) Your existing build scripts (preferred):
   - `packaging/build_portable_exe.bat` or `packaging/build_exe.bat`
   - `packaging/build_installer_exe.bat` (optional)
   - `packaging/build_msi.bat` (optional)

2) Fallback build (if no scripts exist):
   - Builds a single-file portable EXE via PyInstaller from `app.py` or `server.py`

## Where artifacts appear
- GitHub → **Actions** → open a run → **Artifacts**
- Artifacts include any files found under:
  - `dist/`, `build/`, `release/`
  - `*.exe`, `*.msi` inside the project folder

## Tip
If you want releases automatically on version tags (e.g. `v1.2.0`), tell me and I’ll add a release workflow too.
