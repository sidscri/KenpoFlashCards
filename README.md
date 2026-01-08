# 🥋 Kenpo Vocabulary Flash Cards (Android)

An Android flash-card app designed to help students of **American Kenpo Karate** learn, memorize, and review Kenpo vocabulary efficiently using categorized, interactive flash cards.

This app focuses on **active recall**, **progress tracking**, and **organized learning**, making it ideal for beginners through advanced practitioners.

**Current Version:** 4.0

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
- **AI-powered autofill** (requires OpenAI API key)

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

### ☁️ Web App Sync (v4.0)
- **Login** to sync with web app server
- **Push/Pull** progress between devices
- **Sync breakdowns** from shared database
- Server: `sidscri.tplinkdns.com:8009`

### 🤖 AI Integration (v4.0)
- **ChatGPT API** integration for breakdown autofill
- Automatically generates:
  - Term part splits
  - Part meanings/translations
  - Literal translations

---

## 🧭 Navigation

| Tab | Description |
|-----|-------------|
| **To Study** | Active cards to learn (formerly "Unlearned") |
| **Unsure** | Cards marked as uncertain |
| **Learned** | Mastered cards (List or Study view) |
| **All** | Browse all cards with status indicators |
| **Custom** | Your starred cards |
| **More** | Settings, Deleted cards, Admin |

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

### Admin (v4.0)
- Web app login/sync
- ChatGPT API configuration
- Breakdown sync

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
│   ├── StudySettings.kt     # Settings data classes
│   ├── JsonUtil.kt          # JSON parsing utilities
│   ├── TtsHelper.kt         # Text-to-speech wrapper
│   ├── CsvImport.kt         # CSV import functionality
│   ├── WebAppSync.kt        # Server sync API (v4.0)
│   └── ChatGptHelper.kt     # AI breakdown autofill (v4.0)
├── assets/
│   └── kenpo_words.json     # Default vocabulary data
├── res/
│   └── ...                  # Icons, themes, strings
└── AndroidManifest.xml
```

---

## 📋 Version History

| Version | Features |
|---------|----------|
| **4.0** | Landscape mode, Web sync, ChatGPT integration, Group filters for study screens, Pronunciation-only speech |
| **3.0** | Custom study sets, Sort modes, Group filtering, Return to top, Admin placeholder |
| **2.0** | Three-state progress, Term breakdowns, Voice settings, Dark theme |
| **1.0** | Basic flashcards, Got It tracking, Categories |

---

## 🚀 Getting Started

1. Clone or download the project
2. Open in Android Studio
3. Sync Gradle dependencies
4. Run on device or emulator (API 26+)

### Optional: Web Sync Setup
To enable cloud sync, your server needs these endpoints:
- `POST /api/login` - Authentication
- `GET/POST /api/sync/pull|push` - Progress sync
- `GET/POST /api/breakdowns` - Shared breakdowns

---

## 📄 License

This project is for personal/educational use in learning American Kenpo Karate vocabulary.

---

## 🙏 Acknowledgments

Built for the Kenpo community to support vocabulary mastery and martial arts education.
