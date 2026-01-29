# 🥋 Kenpo Vocabulary Flash Cards (Android)

> This is the **Android app** project inside the `sidscri-apps` monorepo.  
> Repo root: `../README.md`

An Android flash-card app designed to help students of **American Kenpo Karate** learn, memorize, and review Kenpo vocabulary efficiently using categorized, interactive flash cards.

This app focuses on **active recall**, **progress tracking**, and **organized learning**, making it ideal for beginners through advanced practitioners.

**Current Version:** v5.3.1 (build 36)
**Changelog:** [CHANGELOG.md](CHANGELOG.md)

---

## 📱 Features

### 🃏 Flash Card Learning
- Vocabulary terms displayed on **interactive flash cards**
- **Tap to flip** between term and definition
- **Swipe navigation** between cards
- Smooth flip animations for intuitive study
- **Pronunciation display** for terms with phonetic guides
- **Reverse mode** - study definition-first

### 📂 Three-State Progress Tracking
Cards can be in one of three states:
- **To Study** - Active cards you're learning
- **Unsure** - Cards you're uncertain about
- **Learned** - Cards you've mastered

Move cards between states with dedicated buttons for flexible learning paths.

### ⭐ Custom Study Sets
- Star any card to add it to your **Custom Set**
- Create personalized review decks
- Access starred cards from any screen
- Perfect for test prep or belt reviews

### 📊 Term Breakdowns
- **Breakdown editor** for each term
- Split compound terms into parts (e.g., TaeKwonDo → Tae, Kwon, Do)
- Add meanings for each part
- Include literal translations and notes
- **Auto-split** feature detects word boundaries
- **AI-powered autofill** (ChatGPT or Gemini)

### 🔍 Filtering & Sorting
- **Group filter** - Study cards from specific categories
- **Search** - Find cards by term, definition, or pronunciation
- **5 sort modes**:
  - Original order
  - Alphabetical (A-Z)
  - Groups (alphabetical)
  - Groups (random order)
  - Random

### 🔊 Text-to-Speech
- **Speak button** reads terms aloud
- Adjustable **speech rate** (0.5x - 2.0x)
- **Pronunciation-only mode** - speaks just the phonetic pronunciation when available

### 📱 Responsive Design
- Full **landscape mode** support with side-by-side layout
- Adaptive UI for different screen sizes
- Dark theme throughout

### ☁️ Web App Sync (v4.0+)
- **Login** to sync with web app server
- **Push/Pull** progress between devices
- **Sync breakdowns** from shared database
- **First login auto-sync** - always syncs on first device login (v4.4.0+)
- **Auto-sync settings** - auto-pull on future logins, auto-push on change
- **API keys pulled for all users** - AI features available to everyone (v4.4.2+)
- Server: `sidscri.tplinkdns.com:8009`
- Endpoint: `POST /api/sync/login` (token-based auth)

### 🤖 AI Integration (v4.0+)
- **ChatGPT API** integration for breakdown autofill
- **Gemini API** integration (v4.2.0+)
- **Model selection** - choose gpt-4o, gpt-4o-mini, gemini-1.5-flash, etc. (v4.3.0+)
- **Key validation indicators** - shows "Key Accepted" or "Key Invalid" (v4.4.0+)
- **Shared API keys** - All users receive API keys on login (v4.4.2+)
- Automatically generates:
  - Term part splits
  - Part meanings/translations
  - Literal translations

### 👤 Admin Features (v4.2.0+)
- **Admin-only access** - Admin Settings visible only to admin users
- **(Admin) label** - Shows after username when logged in as admin (v4.4.0+)
- **API key management** - Push/pull encrypted keys to/from server
- **Server-based admin list** - Admin usernames fetched from server Source of Truth (v4.3.0+)

---

## 🧭 Navigation

| Tab | Description |
|-----|-------------|
| **To Study** | Active cards to learn (formerly "Unlearned") |
| **Unsure** | Cards marked as uncertain |
| **Learned** | Mastered cards (List or Study view) |
| **All** | Browse all cards with status indicators |
| **Custom** | Your starred cards |
| **More** | Settings, Login, Sync, About, Admin (if admin) |

---

## ⚙️ Settings

### Display
- Show/hide group labels
- Show/hide subgroup labels
- Reverse cards (definition first)
- Show breakdown on definition side

### Sorting
- Choose from 5 sort modes
- Per-tab randomization options

### List Views
- Show/hide definitions in lists
- Show/hide action buttons

### Voice
- Speech rate adjustment
- Pronunciation-only mode toggle
- Test voice button

### Login (v4.2.0+)
- Web app login
- Auto-sync on login toggle
- Auto-push on change toggle
- First login always syncs (no setting needed)
- Shows "(Admin)" label for admin users (v4.4.0+)

### Sync Progress (v4.2.0+)
- Manual push/pull progress
- Pending sync indicator with "Push to sync" message
- Breakdown sync
- AI service selector (Auto Select, ChatGPT, Gemini)
- Shows "(Admin)" label for admin users (v4.4.0+)

### AI Access (Admin Only, v4.3.0+)
- ChatGPT API key and model selection
- Gemini API key and model selection
- Key validation indicators ("Key Accepted" / "Key Invalid")
- Push/Pull keys to server
- Auto-pulls keys on admin login (v4.4.0+)

---

## 🧠 Learning Philosophy

This app is built around:
- **Active recall** - Test yourself before seeing answers
- **Spaced repetition** - Focus on what you don't know
- **Three-state tracking** - Nuanced progress beyond just learned/unlearned
- **Reduced cognitive overload** - Hide mastered terms

---

## 🛠️ Tech Stack

- **Language:** Kotlin
- **UI:** Jetpack Compose (Material 3)
- **Architecture:** Repository pattern with Flows
- **Local Storage:** DataStore Preferences
- **Networking:** HttpURLConnection (for sync)
- **Minimum SDK:** Android 8.0+ (API 26)
- **Target SDK:** Android 14 (API 34)

---

## 📦 Project Structure

```
app/src/main/
├── java/com/example/kenpoflashcards/
│   ├── MainActivity.kt      # All UI screens (Compose)
│   ├── Models.kt            # Data classes (FlashCard, TermBreakdown, etc.)
│   ├── Repository.kt        # Data access layer
│   ├── Store.kt             # DataStore persistence
│   ├── StudySettings.kt     # Settings data classes, AdminUsers object
│   ├── JsonUtil.kt          # JSON parsing utilities
│   ├── TtsHelper.kt         # Text-to-speech wrapper
│   ├── CsvImport.kt         # CSV import functionality
│   ├── WebAppSync.kt        # Server sync API, fetchAdminUsers(), pullApiKeysForUser()
│   ├── ChatGptHelper.kt     # ChatGPT AI breakdown autofill
│   └── GeminiHelper.kt      # Gemini AI breakdown autofill
├── assets/
│   └── kenpo_words.json     # Default vocabulary data
├── res/
│   └── ...                  # Icons, themes, strings
└── AndroidManifest.xml
```

---

## 📋 Version History

| Version | Code | Key Changes |
|---------|------|-------------|
| **5.3.1** | 36 | Changed icons and logos from Kenpo to Advanced Flashcards |
| **5.2.0** | 34 | Updated server data paths for Windows installer location |
| **5.1.1** | 33 | Deck switching fix, user cards in deck, AI toggles in Settings, file upload feedback |
| **5.1.0** | 32 | AI Generate buttons for definitions/pronunciations/groups, user cards management, Create Deck AI search |
| **5.0.2** | 31 | Breakdown icon fix, definition speak on Custom/Learned, Randomize Custom toggle |
| **5.0.1** | 30 | Shuffle button on study screens, auto-speak voice settings |
| **5.0.0** | 29 | Edit Decks feature with Switch/Add Cards/Create Deck tabs |
| **4.5.2** | 28 | Auto-sync explanation card on Sync Progress screen |
| **4.5.1** | 27 | Per-card timestamps for conflict-free sync |
| **4.5.0** | 26 | Deck Management groundwork, removed Import CSV, Custom Set fixes, API auto-sync |
| **4.4.4** | 24 | Removed redundant top bar headers, consistent UI pattern across all screens matching Unsure page layout |
| **4.4.3** | 23 | Custom Set isolated status, search X clear button, landscape card height fix, status filter within Custom Set |
| **4.4.2** | 22 | API keys pulled for ALL users on login (not just admins), new `/api/sync/apikeys` endpoint |
| **4.4.0** | 20 | Admin screen loading fix, (Admin) labels, key validation indicators, first-login auto-sync, admin auto-pulls API keys |
| **4.3.0** | 19 | AI model selection, admin button fix, server-based admin users SoT |
| **4.2.0** | 18 | About Screen, User Guide, Gemini AI, Dedicated Login/Sync screens, Auto-sync, API key sync |
| **4.1.0** | 17 | Shared ID mapping for cross-device sync |
| **4.0.7** | 14 | Fixed login endpoint (`/api/sync/login`) |
| **4.0.5** | 12 | Debug instrumentation for login |
| **4.0.0** | 7 | Landscape mode, Web sync, ChatGPT integration |
| **3.0.1** | — | Custom sets, Sort modes, Group filtering |
| **2.0.0** | — | Three-state progress, Term breakdowns, Dark theme |
| **1.0.0** | — | Basic flashcards, Got It tracking |

See [CHANGELOG.md](CHANGELOG.md) for full details.

---

## 🚀 Getting Started

1. Clone or download the project
2. Open in Android Studio
3. Sync Gradle dependencies
4. Run on device or emulator (API 26+)

### Build Release APK
```bash
./gradlew assembleRelease
# Output: app/build/outputs/apk/release/app-release.apk
```

### Web Sync Setup
Your server needs these endpoints:
- `POST /api/sync/login` - Token authentication
- `GET/POST /api/sync/pull|push` - Progress sync
- `GET /api/sync/breakdowns` - Shared breakdowns
- `GET /api/sync/apikeys` - API keys for all users (v4.4.2+)
- `GET /api/admin/users` - Admin usernames (SoT)
- `GET/POST /api/admin/apikeys` - Encrypted API keys (admin only for POST)

---

## 📄 License

Personal/educational use for learning American Kenpo Karate vocabulary.

---

## 🙏 Acknowledgments

Built for the Kenpo community to support vocabulary mastery and martial arts education.

## v4.4.2 (v22) – Implemented fixes (verified in code)

- **Admin badge:** if the logged-in user is an admin, the UI shows **(Admin)** after the username.
- **Search UX (portrait):**
  - Portrait uses a **search icon** toggle (matches landscape).
  - Tapping outside of search **closes the search UI but keeps filtered results**.
  - Added an **X** icon to clear search and reset the deck position.
- **Random study controls:**
  - Added **Random** checkbox beside **Card #/##**.
  - Added **⟳ Reshuffle** icon to re-randomize the current deck on demand.
  - Applies to: **To Study**, **Unsure**, **Custom**, **Learned → Study**.
- **Custom Set:**
  - Custom Set now uses **Custom Set Settings** for sort/random (instead of global study settings).
  - Added Settings action to **pick a number of random Unlearned cards** for the Custom Set (with 🎲 helper).

