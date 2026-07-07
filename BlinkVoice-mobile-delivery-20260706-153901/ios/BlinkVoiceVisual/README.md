# BlinkVoice Visual iOS SDK

This package provides the iOS version of the BlinkVoice pure visual blink recognition SDK.

It is designed to be embedded by multiple iOS apps. The SDK opens a recognition screen, uses the front camera, recognizes blink events locally on the device, and returns a result to the host app.

## Capability

The SDK returns three event types:

| Swift case | Raw value | Meaning |
| --- | --- | --- |
| `.singleBlink` | `SINGLE_BLINK` | Single blink |
| `.doubleBlink` | `DOUBLE_BLINK` | Fast double blink |
| `.longClose` | `LONG_CLOSE` | Long eye close |

The raw values intentionally match the Android SDK event names.

## Requirements

- iOS 13 or later.
- Swift Package Manager.
- Camera permission.
- Real device validation is recommended because the SDK depends on the front camera.

## Add Package

In Xcode:

1. Open the host iOS app project.
2. Select `File > Add Package Dependencies`.
3. Use this repository URL.
4. Select the package path:

```text
ios/BlinkVoiceVisual
```

If Xcode cannot resolve a nested package from the repository root, add this folder as a local package during development:

```text
ios/BlinkVoiceVisual
```

## Info.plist

The host app must provide camera usage text:

```xml
<key>NSCameraUsageDescription</key>
<string>BlinkVoice needs camera access to recognize blink events.</string>
```

## Basic Usage

```swift
import BlinkVoiceVisual
import UIKit

final class ViewController: UIViewController {
    func startBlinkCapture() {
        BlinkVoiceCapture.start(from: self) { result in
            guard let result else {
                // User cancelled, permission was denied, camera was unavailable,
                // or recognition ended without an event.
                return
            }

            switch result.eventType {
            case .singleBlink:
                print("single blink")
            case .doubleBlink:
                print("double blink")
            case .longClose:
                print("long close")
            }

            print("durationMs:", result.durationMs)
        }
    }
}
```

## Options

```swift
let options = BlinkCaptureOptions(
    earCloseThreshold: 0.22,
    earOpenThreshold: 0.25,
    doubleBlinkWindowMs: 350,
    longCloseMinMs: 700,
    minShortBlinkMs: 16,
    maxShortBlinkMs: 280,
    noFaceResetMs: 500,
    autoFinishOnEvent: true,
    eventTypes: Set(BlinkEventType.allCases)
)

BlinkVoiceCapture.start(from: self, options: options) { result in
    // Handle result.
}
```

Listen only for double blink:

```swift
let options = BlinkCaptureOptions(eventTypes: [.doubleBlink])
```

## Result Fields

`BlinkCaptureResult` contains:

| Field | Type | Meaning |
| --- | --- | --- |
| `eventType` | `BlinkEventType` | Recognized event |
| `startTimeMs` | `Int64` | Event start time based on system uptime |
| `endTimeMs` | `Int64` | Event end time based on system uptime |
| `durationMs` | `Int64` | Event duration |
| `confidence` | `Float` | Current value is `1.0` |

## Current Limitations

- Camera startup speed optimization is not included in this version.
- The iOS implementation uses Apple's Vision landmarks, while Android currently uses MediaPipe. Event semantics and thresholds are aligned, but iOS threshold tuning should be validated on real devices.
- Detailed error reasons are not returned yet. `nil` means cancel, permission denial, camera unavailability, or no event.
- This package is source distribution. XCFramework binary packaging can be added later from a Mac/Xcode environment.

## Verification

On macOS with Swift installed:

```bash
cd ios/BlinkVoiceVisual
swift test
```

For camera behavior, validate in a host iOS app on a real device:

1. Add the package.
2. Add `NSCameraUsageDescription`.
3. Call `BlinkVoiceCapture.start`.
4. Grant camera permission.
5. Perform a single blink, fast double blink, or long close.
6. Confirm the host app receives `BlinkCaptureResult`.
