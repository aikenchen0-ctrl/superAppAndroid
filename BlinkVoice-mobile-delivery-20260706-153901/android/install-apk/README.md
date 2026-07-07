# BlinkVoice Android Phone Test Package

This folder contains the Android APK for direct phone testing.

## Install On Phone

Download and install:

```text
blinkvoice-demo-debug.apk
```

This APK is for Android phone testing. It includes the demo app and the BlinkVoice visual SDK module.

## What To Send

For direct phone installation, send:

```text
release/android/blinkvoice-demo-debug.apk
```

Optional checksum file:

```text
release/android/blinkvoice-demo-debug.apk.sha256
```

Do not send the AAR for direct installation. The AAR is only for Android developers who need to integrate the SDK into another Android app.

## Runtime Notes

- Android min SDK: 24.
- The app requests camera permission.
- The current package is a debug build and is suitable for internal testing.
