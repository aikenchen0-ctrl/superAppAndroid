# ubikiTouch

ubikiTouch is an Android accessibility-based edge gesture SDK prototype. It provides transparent left and right edge bars, recognizes common edge gestures, and maps them to configured system actions.

## Current Status

- Multi-module Android project is in place.
- Minimal `AccessibilityService` is implemented.
- Left and right `TYPE_ACCESSIBILITY_OVERLAY` edge bars are implemented.
- Gesture recognition and action mapping are implemented.
- Edge size, indicator, haptic feedback, and gesture-action settings are configurable in the sample app.
- Gesture actions support system actions and manual `LaunchApp` package bindings.
- App blocklist is implemented through foreground package tracking.
- Optional landscape auto-disable is implemented.
- Optional keyboard-visible auto-disable is implemented through accessibility window tracking.
- Quick Settings tile can pause or resume edge bars without opening the app.
- Debug APK and debug androidTest APK build successfully.
- JVM tests and `lintDebug` pass.

## Modules

- `ubiki-core`: pure Kotlin models and gesture classification.
- `ubiki-overlay`: transparent edge bar view and touch event detection.
- `ubiki-accessibility`: accessibility service, action executor, preferences, and SDK facade.
- `app`: sample and debug app.

## Build

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat test lintDebug assembleDebug :app:assembleDebugAndroidTest --no-daemon
```

Debug APK output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## SDK Example

```kotlin
val ubikiTouch = UbikiTouch.create(context)
ubikiTouch.globalEnabled = true
ubikiTouch.setAction(
    side = EdgeSide.LEFT,
    gestureType = GestureType.PULL_INWARD,
    action = GestureAction.Back
)
ubikiTouch.setAction(
    side = EdgeSide.LEFT,
    gestureType = GestureType.PULL_INWARD_HOLD,
    action = GestureAction.Home
)
ubikiTouch.addBlockedPackage("com.example.game")
ubikiTouch.pauseFor(5L * 60L * 1000L)
ubikiTouch.resumeNow()
```

The host app must still declare the accessibility service in its manifest and the user must manually enable it. The SDK cannot and should not automatically obtain accessibility permission. Keyboard-visible auto-disable requires interactive accessibility window tracking.

## ADB Testing

See [ADB_TESTING.md](docs/ADB_TESTING.md).

## SDK Integration

See [SDK_INTEGRATION.md](docs/SDK_INTEGRATION.md).

## EdgeControl Reference Boundary

See [EDGE_CONTROL_REFERENCE.md](docs/EDGE_CONTROL_REFERENCE.md).
