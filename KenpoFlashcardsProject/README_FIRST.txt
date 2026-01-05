KenpoFlashcards – Ready-to-open Android Studio project (Compose)

1) Unzip this folder somewhere (e.g. C:\Dev\KenpoFlashcards)
2) Open Android Studio
3) File → Open… → choose the unzipped folder "KenpoFlashcardsProject" (or whatever you named it)
4) Android Studio will import + sync Gradle.

IMPORTANT:
This zip INCLUDES a working gradle wrapper (gradlew/gradlew.bat + gradle-wrapper.jar).
Android Studio can still sync using its own Gradle tooling.
You can build from Android Studio OR from Command Prompt using gradlew.bat.

Your vocabulary file is already included here:
app\src\main\assets\kenpo_words.json

App behavior:
- Active stack: all cards not marked "Got it" and not Deleted
- Got it button: moves card to Learned (and it disappears from Active)
- Learned screen: review learned cards; restore to Active or move to Deleted
- Deleted screen: restore deleted cards
- Filters: group/subgroup + search
- Tap card to flip, swipe left/right to navigate, Speak button uses TTS

CSV import:
Use a CSV with columns:
group,term,pron,meaning
OR
group,subgroup,term,pron,meaning
Then in the app press Import CSV.
