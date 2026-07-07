# ADB Testing

## Prerequisites

- Enable Developer Options on the phone.
- Enable USB debugging.
- Connect by USB and approve this computer on the phone.
- Local adb path: `C:\Users\Paifa\AppData\Local\Android\Sdk\platform-tools\adb.exe`.

## Build

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat test lintDebug assembleDebug :app:assembleDebugAndroidTest --no-daemon
```

## Install And Launch

```powershell
.\scripts\adb-install-debug.ps1
```

## Enable Accessibility For Development

```powershell
.\scripts\adb-enable-accessibility-dev.ps1
```

This is only for development testing. A production app must not silently enable its accessibility service for the user.

## Run Instrumented Smoke Test

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest --no-daemon
```

## Gesture Smoke Test

Left inward pull:

```powershell
$adb="$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb shell input swipe 1 1200 220 1200 180
```

Right inward pull:

```powershell
$adb="$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb shell input swipe 1079 1200 860 1200 180
```

Adjust coordinates for the device resolution. The default edge bar width is 24dp and each edge covers 70 percent of screen height.

## Logcat

```powershell
.\scripts\adb-logcat.ps1
```

The log tag is `UbikiTouch`. Current logs cover service connection, foreground package changes, overlay creation, gesture detection, and action execution.

## App Blocklist Verification

1. Enable the accessibility service.
2. Open another app.
3. Return to ubikiTouch and press `Refresh` in `Blocked apps`.
4. Press `Block current`.
5. Open that app again and verify the edge bars are not added.

## Keyboard Auto-Disable Verification

1. Enable `Disable when keyboard is shown` in `Global controls`.
2. Open any text field and show the keyboard.
3. Verify the edge bars are removed while the keyboard is visible.
4. Hide the keyboard and verify the edge bars return.

## Quick Settings Tile Verification

1. Install the debug APK.
2. Add the `ubikiTouch` tile from Android Quick Settings edit mode.
3. Tap the tile once to pause edge bars for 15 minutes.
4. Tap it again to resume immediately.

## Scripted Smoke Test

```powershell
.\scripts\adb-smoke-test.ps1
```

For non-1080px wide devices:

```powershell
.\scripts\adb-smoke-test.ps1 -RightStartX 1439 -CenterY 1600
```
