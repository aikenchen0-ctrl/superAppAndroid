# EdgeControl Reference Boundary

This project uses `https://github.com/Nir757/EdgeControl` as a product and architecture reference, but the current implementation does not copy upstream source code.

## Referenced Structure

- `AccessibilityService` hosts the edge bars.
- `TYPE_ACCESSIBILITY_OVERLAY` creates transparent overlays.
- Overlay views receive `MotionEvent` and classify edge gestures.
- The action layer uses `performGlobalAction()` for back, home, recents, notifications, quick settings, screenshot, and lock screen.
- Edge side, gesture type, action, and zone configuration are modeled separately.

## Deliberately Narrowed Scope

- No macro recording.
- No window content retrieval.
- No default text copy, paste, node click, or screen-content automation.
- No regular floating-window permission.
- No network, ads, or analytics SDK.

## Current Modules

- `ubiki-core`: pure Kotlin models and gesture classifier.
- `ubiki-overlay`: Android overlay view and touch event recognition.
- `ubiki-accessibility`: accessibility service, action execution, preferences, and SDK facade.
- `app`: sample app for install, enablement, and debug configuration.

## Next Reference Targets

- Per-app profiles.
- IME-aware disable policy.
- Installed-app picker for blocklist and launch actions.
- Publishable AAR and host integration guide.
