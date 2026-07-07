# Compose Back Wave Gesture Design

## Goal

Add a rewritten SDK-owned back gesture for the existing edge overlay system. The gesture should intercept inward pulls on UbikiTouch's left and right edge overlays, show a Compose wave-shaped back affordance while dragging, and execute Back only when the release crosses the configured threshold.

This design does not attempt to replace Android's system back gesture globally. It operates inside UbikiTouch's accessibility overlay touch zones.

## Selected Approach

Use the current transparent `TYPE_ACCESSIBILITY_OVERLAY` edge views as the touch interception layer, and add a separate Compose-driven visual overlay for the wave feedback.

The existing gesture detector remains responsible for touch state and gesture classification. It will be extended with progress callbacks so accessibility runtime code can show, update, confirm, or dismiss the wave without duplicating gesture recognition.

## User Experience

When the user starts dragging inward from an enabled edge zone:

1. A wave panel appears from the same screen edge.
2. The wave width and alpha follow drag progress.
3. A back indicator becomes stronger once the drag crosses the return threshold.
4. Releasing past the threshold runs the configured Back action.
5. Releasing before the threshold dismisses the wave without executing Back.

The first implementation should keep the wave functional and restrained. It should avoid adding a new settings surface unless implementation reveals that a toggle is necessary.

## Architecture

### Core

Add a small pure Kotlin progress model for back drag state:

- edge side
- drag distance
- threshold distance
- normalized progress
- committed or cancelled outcome

This keeps threshold behavior testable without Android UI dependencies.

### Overlay

Extend `EdgeGestureDetector` and `EdgeOverlayView` with drag lifecycle callbacks:

- drag start
- drag progress
- drag cancel
- drag release

These callbacks should only be emitted for inward pulls that can become a Back gesture. Existing tap, double tap, long press, vertical swipe, and pull-hold behavior should keep working.

### Accessibility Runtime

`UbikiAccessibilityService` will own a lightweight controller that:

- creates the Compose wave overlay
- updates progress while the user drags
- dismisses the overlay on cancel or completion
- executes Back through the existing action executor when committed

The service should continue honoring global enabled state, pause, foreground package blocklist, landscape disable, keyboard disable, and screen state before adding overlays.

### Compose Visual

Create a Compose view for the back wave overlay. It should:

- draw a side-attached wave shape using `Canvas`
- support left and right sides
- render progress and committed state
- use stable dimensions so the overlay does not resize unpredictably

The Compose overlay is visual only. It should not own gesture recognition.

## Data Flow

1. User touches an existing edge overlay.
2. `EdgeGestureDetector` recognizes a possible inward back drag and reports progress.
3. `UbikiAccessibilityService` forwards progress to the Compose wave controller.
4. User releases.
5. The pure Kotlin progress model decides whether the drag committed.
6. If committed, the service executes `GestureAction.Back`.
7. The wave overlay dismisses.

## Error Handling

If adding the Compose visual overlay fails, the gesture should still fall back to the existing non-visual Back behavior. Failures should be logged with the existing `UbikiTouch` tag.

Pending callbacks must be cancelled when overlays are removed, the screen turns off, service is destroyed, or a preference refresh recreates overlays.

## Tests

Add JVM tests for the pure Kotlin back drag progress model:

- progress clamps between 0 and 1
- release below threshold cancels
- release at or above threshold commits
- left and right sides produce the same threshold behavior

Existing `SwipeClassifier` and action serialization tests should remain unchanged.

Android UI behavior can be manually checked with the existing ADB gesture smoke path after build issues are resolved.

## Non-Goals

- Do not globally override Android's native system back gesture.
- Do not add macro automation or node-content actions.
- Do not request floating-window permission.
- Do not add network, analytics, or external SDKs.
- Do not redesign the sample app UI unless a minimal toggle becomes necessary.

## Approval State

The user selected approach 1: Compose wave preview layer plus existing overlay gesture recognition.
