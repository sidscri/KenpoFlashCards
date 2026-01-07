# Kenpo Flashcards v2.0 Upgrade Checklist

## Prerequisites
- Your GitHub repo: `sidscri/KenpoFlashCards`
- You've already created `KenpoFlashcardsProject v2.0` folder (needs renaming)

---

## Step 1: Rename the v2.0 Folder (Fix Space Issue)

In your local repo, rename:
```
KenpoFlashcardsProject v2.0  →  KenpoFlashcardsProject-v2
```

Or via command line:
```bash
cd KenpoFlashCards
mv "KenpoFlashcardsProject v2.0" KenpoFlashcardsProject-v2
```

---

## Step 2: Copy Upgraded Source Files

Copy these files to `KenpoFlashcardsProject-v2/app/src/main/java/com/example/kenpoflashcards/`:

| File | Action |
|------|--------|
| `Models.kt` | **Replace** existing |
| `StudySettings.kt` | **Replace** existing |
| `Store.kt` | **Replace** existing |
| `Repository.kt` | **Replace** existing |
| `TtsHelper.kt` | **Replace** existing |
| `MainActivity.kt` | **Replace** existing |
| `JsonUtil.kt` | **Keep** existing (no changes needed) |
| `CsvImport.kt` | **Keep** existing (no changes needed) |

---

## Step 3: Copy colors.xml

Copy `colors.xml` to:
```
KenpoFlashcardsProject-v2/app/src/main/res/values/colors.xml
```
(Replace the existing file)

---

## Step 4: Update build.gradle

Replace `KenpoFlashcardsProject-v2/app/build.gradle` with the provided `build.gradle` file.

Key changes:
- `versionCode 1` → `versionCode 2`
- `versionName "1.0"` → `versionName "2.0"`
- Added `material-icons-extended` dependency

---

## Step 5: Update GitHub Workflow

Replace `.github/workflows/publish-fdroid.yml` with `publish-fdroid-v2.yml`

Key change: 
- `PROJECT_DIR: KenpoFlashcardsProject-v2` (line 17)

---

## Step 6: Commit and Push

```bash
git add -A
git commit -m "Upgrade to v2.0 with Unsure status, breakdowns, and web theme"
git push origin main
```

---

## Step 7: Verify GitHub Actions

1. Go to your repo: https://github.com/sidscri/KenpoFlashCards
2. Click **Actions** tab
3. Watch the workflow run
4. If successful, your F-Droid repo will be updated

---

## New Features in v2.0

✅ **Unsure Status** - 3-state progress tracking (Active → Unsure → Learned)  
✅ **Learned Study Mode** - Review learned cards with Study/List toggle  
✅ **Term Breakdowns** - Break compound terms into parts with meanings  
✅ **Voice Settings** - Adjustable speech rate (0.5x - 2.0x)  
✅ **All Cards View** - See all cards with status badges  
✅ **Bottom Navigation** - Easy tab switching  
✅ **Web Dark Theme** - Matching colors from the web app  
✅ **Per-Tab Randomization** - Separate random settings per screen  
✅ **Enhanced Settings** - Many new customization options  

---

## Troubleshooting

### Build fails with "file not found"
- Make sure folder is named `KenpoFlashcardsProject-v2` (no spaces)
- Verify workflow `PROJECT_DIR` matches folder name

### App crashes on launch
- Check that all 6 Kotlin files were replaced
- Verify `colors.xml` was copied
- Make sure `material-icons-extended` is in build.gradle

### F-Droid doesn't show update
- Version code must be higher than previous (2 > 1)
- Clear F-Droid cache and refresh repos

---

## File Structure After Upgrade

```
KenpoFlashCards/
├── .github/
│   └── workflows/
│       └── publish-fdroid.yml        ← Updated for v2.0
├── KenpoFlashcardsProject/           ← Original v1.0 (keep for reference)
├── KenpoFlashcardsProject-v2/        ← New v2.0 (active)
│   ├── app/
│   │   ├── build.gradle              ← Updated version
│   │   └── src/main/
│   │       ├── java/.../kenpoflashcards/
│   │       │   ├── MainActivity.kt   ← Replaced
│   │       │   ├── Models.kt         ← Replaced
│   │       │   ├── Store.kt          ← Replaced
│   │       │   ├── Repository.kt     ← Replaced
│   │       │   ├── StudySettings.kt  ← Replaced
│   │       │   ├── TtsHelper.kt      ← Replaced
│   │       │   ├── JsonUtil.kt       ← Unchanged
│   │       │   └── CsvImport.kt      ← Unchanged
│   │       ├── res/values/
│   │       │   └── colors.xml        ← Replaced
│   │       └── assets/
│   │           └── kenpo_words.json  ← Unchanged
│   └── gradle/...
├── .gitignore
├── LICENSE
└── README.md
```
