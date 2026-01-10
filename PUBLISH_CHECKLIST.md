# Publish Checklist (GitHub Desktop)

1) Confirm this folder is a single Git repository:
   - sidscri-apps/.git exists
   - NO nested .git folders inside project subfolders

2) In GitHub Desktop:
   - Summary: "Add web server options and workflows"
   - Commit to main
   - Push origin (or Publish repository)

3) Verify on GitHub:
   - Repo shows folders:
     - KenpoFlashcardsProject-v2
     - KenpoFlashcardsWeb
     - KenpoFlashcardsWebServer_Service_Tray
     - KenpoFlashcardsWebServer_Packaged_in_exe_msi
   - Actions tab shows workflows (optional)

Notes:
- The workflows are optional. They only matter if you want GitHub Actions to build artifacts.
- Web server runtime data should stay ignored (KenpoFlashcardsWeb/data, logs, .env).
