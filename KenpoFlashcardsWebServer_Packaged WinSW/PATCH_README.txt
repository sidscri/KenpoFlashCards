KenpoFlashcardsWebServer_Packaged - Patch (platformdirs/pkg_resources fix)

What this patch fixes
- Installer ran on another PC but the tray app crashed with:
  "Failed to execute script 'pyi_rth_pkgres' ... No module named 'platformdirs'"
- This happens when setuptools/pkg_resources (pulled in by PyInstaller) needs
  'platformdirs' at runtime but the frozen build did not include it.

Files updated
1) packaging/requirements_packaging.txt
   - Adds platformdirs + jaraco.* + setuptools so the venv used for packaging
     has everything required.
2) packaging/pyinstaller/kenpo_tray.spec
   - Adds hiddenimports collection for platformdirs + jaraco so PyInstaller
     bundles them into the dist folder.

How to use
1) Unzip your project to a folder (example: Desktop\KenpoFlashcardsWebServer_Packaged)
2) Copy/overwrite the patched files into your project, preserving the folder paths.
3) Build the EXE:
     .\packaging\build_exe.bat
4) Build the installer:
     .\packaging\build_installer_inno.bat

Notes / common gotchas
- If Inno Setup errors with I/O error 183, delete the old output\*.exe and
  close any program that may be locking it, then re-run.
- "_internal" folder: This is normal for a one-folder PyInstaller build.
  The installer should include it. Do NOT copy only the .exe by itself.
- AV false positives: unsigned PyInstaller executables are often flagged.
  Best practice is to code-sign your installer/exe, and/or submit to Microsoft
  for false-positive review if needed.
