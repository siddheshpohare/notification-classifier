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
Only Docker is required. From inside this folder:

    docker build -t notifsorter-build .

    # build the debug APK (a named volume caches Gradle downloads between runs)
    docker run --rm -v "$PWD":/project -v gradle-cache:/root/.gradle notifsorter-build

    # run the unit tests instead
    docker run --rm -v "$PWD":/project -v gradle-cache:/root/.gradle notifsorter-build \
        gradle test --no-daemon --console=plain

The APK appears at `app/build/outputs/apk/debug/app-debug.apk`. Copy it to your phone (or `adb install -r app-debug.apk`
from your PC), allow "install unknown apps", then follow the setup steps above. On Windows PowerShell use `${PWD}`
in place of `$PWD`. Docker builds the app but cannot run it: it needs a real phone or emulator.
