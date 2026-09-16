# Edge Gesture Server Verification

Date: 2026-09-16

## Completed

- Core hot-zone geometry merges overlapping/adjacent intervals, reports the 200dp same-side budget, and validates immutable configuration snapshots.
- Side and bottom recognizers use primitive input sessions. Side `MOVE` only updates/reports a reusable preview and commits on valid `UP`; bottom Tap commits on `UP`, while bottom direction/timed gestures commit once at their stable distance/time threshold and use `UP/CANCEL` only for cleanup. Rollback, cancel, multi-pointer, focus loss, and repeated `UP` are covered.
- Native input now adapts to the same Core recognizers as the Overlay path. Configuration is captured for an active transaction, and sub-threshold MOVE events do not delegate the stream.
- Bottom Overlay input uses `BottomGestureRecognizer`; bindings are cached when the window is created. Preview delivery can be coalesced to one frame with `postOnAnimation`.
- Native/Overlay ownership is explicit. Native success suppresses the matching fallback window, native failure falls back only for uncovered regions, and no full-screen touch owner is used.
- `:gesture_server` has an isolated AccessibilityService/Binder process, persisted versioned geometry and action snapshots, restart recovery, and `canPerformGestures=true` metadata.
- Preferences retain legacy keys, persist normalized multi-zone keys, and maintain a monotonic gesture configuration version.
- Legacy bottom-bar preferences are migrated on load: an explicitly configured pause gesture is preserved by disabling the default upward swipe, while two explicitly conflicting upward bindings prefer the immediate ordinary swipe.
- Geometry-only refreshes now reuse existing side Overlay windows with `updateViewLayout`; active gestures defer the latest geometry until UP/CANCEL/focus loss. Bottom-bar width updates use the same idle/defer rule.
- Structural refreshes (owner/backend/action/threshold changes) are deferred while an input transaction is active and released at the transaction boundary.
- Terminal gesture metadata now carries `gestureId`, snapshot revision, and zone through Native, Overlay, Compose, and bottom adapters.
- Service actions use a bounded, synchronized terminal gate; stale owner callbacks are rejected when floating chat ownership changes.
- Native lost-terminal cleanup now aborts the old core session, Back preview, and selection before accepting a new `DOWN`.
- Side-function preview items are cached outside `MOVE`; hint and meteor teardown no longer use blocking `removeViewImmediate` in the input path, and the BackWave window is prewarmed/reused for Native input.

## Verification

Passed:

```text
:univerge-core:test --tests ...GestureThresholdPolicyTest (focused gesture suite)
:univerge-overlay:testDebugUnitTest (18 tests)
:univerge-accessibility:testDebugUnitTest (105 targeted gesture/ownership/action tests)
:gesture-server:testDebugUnitTest (49 tests)
:benchmark:compileDebugKotlin
:app:assembleDebug
:app:processDebugMainManifest
new gesture regression tests (owner policy, terminal gate, abort ordering, preview cache)
git diff --check
```

The full Core unit run completed 128 tests with one unrelated failure in
`FloatingChatPrototypeTest.sampleConversationContainsOneExampleForEverySupportedMessageType`.
The targeted Core gesture suite passed.

Latest debug artifact:

```text
C:\codeDev\_superAppAndroid_remote\app\build\outputs\apk\debug\app-debug.apk
SHA-256: FF57E41074A838A1B4380063574BD23AFF26002A0E93F4F94159FDF709EBB05E
Built: 2026-09-16 12:45:29
```

Merged manifest confirms the isolated services run with `android:process=":gesture_server"`; the accessibility metadata declares `android:canPerformGestures="true"`.

## Remaining

- The Macrobenchmark source now compiles at [EdgeGestureLatencyBenchmark.kt](C:/codeDev/_superAppAndroid_remote/benchmark/src/main/kotlin/com/paifa/univerge/benchmark/EdgeGestureLatencyBenchmark.kt); same-device latency/allocation measurements and real-device gesture acceptance remain pending because `adb devices` reports no connected device.
- A pre-existing Core sample-data test still fails because `FloatingChatMessageType.EnterpriseInvite` exists in the enum but is absent from that test's expected set; it is unrelated to the gesture changes.
- The full accessibility suite currently has 38 existing UI/contract failures (for example `FloatingChatAiVoiceEntryTest`, `FloatingChatMessageUiContractTest`, and workspace presentation contracts); the targeted gesture suites and Debug APK build pass independently.
- `adb devices` currently reports no connected device, so the reported “first swipe delayed, then two returns” classification still needs a real-device trace. The added debug fields are `owner`, `gestureId`, `action`, and terminal acceptance/rejection.
- Native delegation now only marks the stream delegated when the request succeeds or the platform is already delegating; failed requests leave the next event eligible for processing.
- Bottom cancel paths use the same abort cleanup as side gestures, and preview action labels are resolved once per configuration instead of querying `PackageManager` from MOVE.
- The hint window is prewarmed as a hidden, non-touchable overlay alongside the BackWave prewarm.
- A failed `requestDelegating()` no longer marks the stream as delegated; the next event remains processable unless the platform reports `STATE_DELEGATING`.
- Native bottom/side cancel branches now share the same abort cleanup seam, and the final targeted suite covers delegation, hint prewarm, preview-label caching, and cancellation ordering.
