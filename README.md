# Notification Sorter (Android, Phase 1)

Reads every notification, labels it Important / Unsure / Silenced, and learns from your corrections.

## Open and run
1. Android Studio (Koala or newer) -> Open -> select the `NotifSorter` folder, let Gradle sync.
   If sync fails on the Gradle wrapper: File > New > New Project > Empty Activity (package
   `com.example.notifsorter`, min SDK 26), then copy this project's `app/src` files and
   `app/build.gradle.kts` dependencies over it.
2. Run on your phone (USB debugging) or an emulator.
3. Tap **Open notification access settings** and enable "Notification Sorter".
   - Android 13+ for sideloaded apps: if the toggle is greyed out, go to Settings > Apps >
     Notification Sorter > ⋮ (top right) > **Allow restricted settings**, then try again.
4. Set battery to **Unrestricted** for the app, so Android doesn't kill the listener.

## Learning mode (default)
"Hide junk notifications" starts OFF: everything is only labeled. Use it for a few days, correct mistakes with
the buttons, then turn it on once you trust it. Calls, alarms, navigation, media and ongoing notifications are never touched.

## How corrections work
- "Not important" / "This was important" saves your label and an instant rule for that app + title
  (e.g. Coursera mails from Gmail, or one WhatsApp contact), applied to the very next notification.
- **Export labels** shares a CSV (app,title,text,label) you can merge into the Colab notebook's data and retrain.
  The CSV only has notifications you corrected, so always merge it with your base data.

## Where the model plugs in
`Pipeline.decide()` calls `RuleClassifier`. To use the fine-tuned model, export it to a quantized ONNX file
(the 541 MB checkpoint is too big for a phone) and call it from `Pipeline.decide()` instead.

## Tests
`./gradlew test` runs `RuleClassifierTest` (pure Kotlin, no device needed).

## Build with Docker (no Android Studio, JDK or SDK needed)
Only Docker is required. Run from inside this folder.

    docker build -t notifsorter-build .

**Windows PowerShell** (quote the whole volume argument, otherwise PowerShell splits it and Docker says "invalid reference format"):

    docker run --rm -v "${PWD}:/project" -v gradle-cache:/root/.gradle -v android-keys:/root/.android notifsorter-build

**Windows cmd:**

    docker run --rm -v "%cd%:/project" -v gradle-cache:/root/.gradle -v android-keys:/root/.android notifsorter-build

**macOS / Linux:**

    docker run --rm -v "$PWD":/project -v gradle-cache:/root/.gradle -v android-keys:/root/.android notifsorter-build

To run the unit tests instead, add `gradle test --no-daemon --console=plain` at the end of the same command.

The APK appears at `app/build/outputs/apk/debug/app-debug.apk`. Copy it to your phone (or `adb install -r app-debug.apk`),
allow "install unknown apps", then follow the setup steps above. Docker builds the app but cannot run it: it needs a
real phone or emulator.

`-v android-keys:/root/.android` keeps the debug signing key between builds. Without it every build is signed with a new key
and Android refuses to update the installed app ("App not installed"); you would have to uninstall it each time.

## Privacy and risks
- The app needs Notification access, which lets it read every notification (including OTPs and bank messages). Android
  hides this behind "Allow restricted settings" for exactly that reason. Only install builds you made yourself.
- It has no internet permission, so it cannot send anything off the phone.
- Logged text is stored on the phone in a local database. Entries you never corrected are deleted after 14 days.
  Use "Clear all" to wipe everything now.
- "Export labels" creates a CSV of your real messages. Keep it private (never a public GitHub repo).
- Biggest functional risk: wrongly hiding an important notification. Mitigations: learning mode by default, unsure items stay
  visible, OTP/credited/debited/security messages are never hidden unless they also look like promos, and hidden items
  are kept in the Silenced tab (a hidden notification cannot be restored to the shade, only read in the app).