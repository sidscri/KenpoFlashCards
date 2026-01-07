KenpoFlashCards Launcher Icon Pack
=================================

Copy these folders into your Android project at:
  app/src/main/res/

Folders included:
  values/colors.xml
  mipmap-*/ic_launcher.png
  mipmap-*/ic_launcher_round.png
  mipmap-*/ic_launcher_foreground.png
  mipmap-anydpi-v26/ic_launcher.xml
  mipmap-anydpi-v26/ic_launcher_round.xml

AndroidManifest.xml (inside <application ...>):
  android:icon="@mipmap/ic_launcher"
  android:roundIcon="@mipmap/ic_launcher_round"

Then rebuild:
  ./gradlew assembleRelease
