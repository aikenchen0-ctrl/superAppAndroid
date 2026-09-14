# Edge Gesture Server Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将左右侧和底部边缘手势收敛为独立、低分配、单一输入所有者的 Gesture Server，并保留无 Root 的诚实降级。

**Architecture:** `univerge-core` 持有纯 Kotlin 的配置快照、热区几何、侧边/底部状态机和领域事件；`univerge-overlay` 与 `univerge-accessibility` 只做平台适配。独立 `:gesture_server` 进程通过版本化 Binder 快照接收配置，Native 成功时不创建重叠 Overlay，Native 失败时才为缺失区域回退到窄触摸 View。

**Tech Stack:** Kotlin 2.0.21、JDK 17、Android AccessibilityService、TouchInteractionController、WindowManager、JUnit 4、现有 `univerge-core`/`univerge-overlay`/`univerge-accessibility` 模块。

---

### Task 1: 纯 Kotlin 热区几何与能力报告

**Files:**
- Create: `univerge-core/src/main/kotlin/com/paifa/univerge/core/gesture/HotZoneGeometry.kt`
- Create: `univerge-core/src/main/kotlin/com/paifa/univerge/core/gesture/ConfigSnapshot.kt`
- Create: `univerge-core/src/test/kotlin/com/paifa/univerge/core/gesture/HotZoneGeometryTest.kt`
- Create: `univerge-core/src/test/kotlin/com/paifa/univerge/core/gesture/ConfigSnapshotTest.kt`

- [ ] **Step 1: Write failing tests** for enabled-zone normalization, vertical interval merge, same-side length reporting, and version monotonicity.
- [ ] **Step 2: Run** `./gradlew :univerge-core:test --no-daemon --max-workers=1` with Windows JDK 17 and verify the new tests fail because the contracts do not exist.
- [ ] **Step 3: Implement** immutable geometry types. Convert dp to px once, sort intervals, merge overlap and adjacency, calculate total dp length, and return `DETERMINISTIC_EXCLUSION` only when the merged total is within the explicit budget.
- [ ] **Step 4: Implement** immutable `ConfigSnapshot` validation. Reject non-positive versions, invalid density, duplicate zone IDs on one side, and actions with invalid package names.
- [ ] **Step 5: Run** the targeted core tests and verify all pass without Android dependencies.

### Task 2: Side and bottom transaction state machines

**Files:**
- Create: `univerge-core/src/main/kotlin/com/paifa/univerge/core/gesture/SideGestureRecognizer.kt`
- Create: `univerge-core/src/main/kotlin/com/paifa/univerge/core/gesture/BottomGestureRecognizer.kt`
- Create: `univerge-core/src/test/kotlin/com/paifa/univerge/core/gesture/SideGestureRecognizerTest.kt`
- Create: `univerge-core/src/test/kotlin/com/paifa/univerge/core/gesture/BottomGestureRecognizerTest.kt`
- Modify: `univerge-core/src/main/kotlin/com/paifa/univerge/core/gesture/SwipeClassifier.kt`
- Modify: `univerge-core/src/main/kotlin/com/paifa/univerge/core/model/GestureType.kt`

- [ ] **Step 1: Write failing tests** covering inward/outward motion, short/long/diagonal short/long, stationary hold, upward/downward swipe, rollback, `CANCEL`, multi-pointer, focus loss, repeated `UP`, and “MOVE never commits”.
- [ ] **Step 2: Run** `./gradlew :univerge-core:test --no-daemon --max-workers=1` and verify failures identify missing transaction behavior rather than test setup errors.
- [ ] **Step 3: Implement** one allocation-free session per recognizer using primitive coordinates, `gestureId`, `activePointerId`, and a captured config/action snapshot. Timer callbacks may arm a hold candidate but only `UP` may emit `GestureCommit`.
- [ ] **Step 4: Update** diagonal classification defaults so the requested diagonal gestures are enabled in the server snapshot while preserving legacy `SwipeClassifier` callers through explicit constructor configuration.
- [ ] **Step 5: Run** core tests, then refactor only after green to remove duplicate distance calculations and precompute thresholds.

### Task 3: Cached action bindings and preview events

**Files:**
- Create: `univerge-core/src/main/kotlin/com/paifa/univerge/core/gesture/GestureBinding.kt`
- Create: `univerge-core/src/main/kotlin/com/paifa/univerge/core/gesture/GestureEvent.kt`
- Create: `univerge-core/src/test/kotlin/com/paifa/univerge/core/gesture/GestureBindingTest.kt`
- Modify: `univerge-core/src/main/kotlin/com/paifa/univerge/core/model/GestureAction.kt`

- [ ] **Step 1: Write failing tests** for side-specific and bottom-specific action lookup, unknown action fallback to `None`, immutable snapshot capture, preview labels without package queries, and one terminal commit per `gestureId`.
- [ ] **Step 2: Run** the focused tests and verify they fail for missing types or incorrect lookup semantics.
- [ ] **Step 3: Implement** immutable bindings and domain events. No event type may import Android, `Context`, `View`, `PackageManager`, or service classes.
- [ ] **Step 4: Run** `:univerge-core:test` and verify the existing action tests still pass.

### Task 4: Replace fallback MOVE hot path

**Files:**
- Modify: `univerge-overlay/src/main/kotlin/com/paifa/univerge/overlay/EdgeGestureDetector.kt`
- Modify: `univerge-overlay/src/main/kotlin/com/paifa/univerge/overlay/EdgeOverlayView.kt`
- Modify: `univerge-overlay/src/test/kotlin/com/paifa/univerge/overlay/EdgeGestureDetectorTest.kt`
- Modify: `univerge-accessibility/src/main/kotlin/com/paifa/univerge/accessibility/BottomGestureBarOverlayController.kt`
- Modify: `univerge-accessibility/src/test/kotlin/com/paifa/univerge/accessibility/BottomGestureBarOverlayControllerTest.kt`

- [ ] **Step 1: Add failing regression tests** proving MOVE does not allocate domain snapshots, does not query preferences/package manager, hold timer does not execute, and UP executes at most once.
- [ ] **Step 2: Run** the targeted overlay/accessibility tests and confirm the old implementation fails these contracts.
- [ ] **Step 3: Route** Android events into the core recognizers. Reuse a mutable primitive session and create `GestureData` only for preview delivery or terminal commit. Remove direct hold execution and avoid a second `Handler.post` after UP.
- [ ] **Step 4: Cache side action lists and labels at DOWN/config update. Keep preview rendering behind one frame scheduler.
- [ ] **Step 5: Run** targeted tests and `:univerge-overlay:testDebugUnitTest :univerge-accessibility:testDebugUnitTest` with one Gradle worker.

### Task 5: Enforce a single native/overlay input owner

**Files:**
- Modify: `univerge-accessibility/src/main/kotlin/com/paifa/univerge/accessibility/NativeEdgeGestureController.kt`
- Modify: `univerge-accessibility/src/main/kotlin/com/paifa/univerge/accessibility/NativeGestureExclusionOverlayController.kt`
- Modify: `univerge-accessibility/src/main/kotlin/com/paifa/univerge/accessibility/UniVergeAccessibilityService.kt`
- Create: `univerge-accessibility/src/main/kotlin/com/paifa/univerge/accessibility/GestureInputOwnership.kt`
- Create: `univerge-accessibility/src/test/kotlin/com/paifa/univerge/accessibility/GestureInputOwnershipTest.kt`

- [ ] **Step 1: Write failing tests** for native success suppressing the corresponding Overlay, native failure selecting fallback only for uncovered regions, bottom owner exclusivity, and no full-screen touch owner.
- [ ] **Step 2: Run** focused tests and verify the current dual paths fail.
- [ ] **Step 3: Implement** an immutable ownership report and make service refresh apply ownership before creating/removing windows. Native callbacks must not be forwarded through an extra main-handler hop when already on the service callback executor.
- [ ] **Step 4: Remove the `FLAG_NOT_TOUCHABLE` full-screen exclusion window from the input path; only real trigger Views may publish exclusion rectangles. Preserve any non-touch diagnostic drawing as non-owning preview.
- [ ] **Step 5: Run** accessibility tests and inspect the ownership report for overlapping rectangles.

### Task 6: Independent `:gesture_server` process and Binder snapshot

**Files:**
- Create: `gesture-server/src/main/AndroidManifest.xml`
- Create: `gesture-server/build.gradle.kts`
- Create: `gesture-server/src/main/kotlin/com/paifa/univerge/gesture/server/GestureAccessibilityService.kt`
- Create: `gesture-server/src/main/kotlin/com/paifa/univerge/gesture/server/GestureServerRuntime.kt`
- Create: `gesture-server/src/main/kotlin/com/paifa/univerge/gesture/server/GestureServerBinder.kt`
- Create: `gesture-server/src/test/kotlin/com/paifa/univerge/gesture/server/GestureServerRuntimeTest.kt`
- Modify: `settings.gradle.kts`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `univerge-accessibility/build.gradle.kts`

- [ ] **Step 1: Write failing runtime tests** for snapshot versioning, activity snapshot capture, Binder disconnect, restart recovery, and no business UI dependency.
- [ ] **Step 2: Run** the new module tests and verify missing runtime behavior fails.
- [ ] **Step 3: Implement** the minimal service and Binder contract. The service must declare `android:process=":gesture_server"`, `BIND_ACCESSIBILITY_SERVICE`, and `canPerformGestures`; it must not initialize chat/video/Compose controllers.
- [ ] **Step 4: Connect** the main process preference owner to push a serialized snapshot after persistence. Keep the last valid snapshot in a small service-owned store.
- [ ] **Step 5: Run** `:gesture-server:testDebugUnitTest` and manifest merge checks.

### Task 7: Configuration migration and in-place window updates

**Files:**
- Modify: `univerge-accessibility/src/main/kotlin/com/paifa/univerge/accessibility/UniVergePreferences.kt`
- Modify: `univerge-accessibility/src/main/kotlin/com/paifa/univerge/accessibility/UniVergeAccessibilityService.kt`
- Modify: `univerge-accessibility/src/test/kotlin/com/paifa/univerge/accessibility/UniVergePreferencesTest.kt`
- Create: `univerge-accessibility/src/test/kotlin/com/paifa/univerge/accessibility/GestureConfigSnapshotTest.kt`

- [x] **Step 1: Write failing tests** for legacy single-zone migration, four-zone persistence, version increments, in-place update decisions, and active-gesture snapshot stability.
- [x] **Step 2: Run** the preference and geometry-decision tests and isolate the former full remove/recreate path.
- [x] **Step 3: Implement** versioned snapshot generation from existing preferences. Keep legacy keys readable, write normalized multi-zone keys, and never read preferences from MOVE.
- [x] **Step 4: Replace full overlay rebuilds with per-zone `updateViewLayout` when only geometry changes. Recreate only when the input owner or window type changes; active transactions defer the latest plan.
- [x] **Step 5: Run** targeted tests and accessibility compilation.

### Task 8: Verification, benchmark, and delivery review

**Files:**
- Modify: `docs/superpowers/specs/2026-09-13-edge-gesture-server-design.md`
- Modify: `docs/superpowers/plans/2026-09-13-edge-gesture-server.md`
- Create: `benchmark/src/main/kotlin/com/paifa/univerge/benchmark/EdgeGestureLatencyBenchmark.kt`
- Create: `docs/superpowers/reports/2026-09-13-edge-gesture-server-verification.md`

- [x] **Step 1: Run** `git diff --check` and the focused core, overlay, accessibility, and server tests with Windows JDK 17.
- [x] **Step 2: Run** `:app:assembleDebug` and manifest merge validation with `--no-daemon --max-workers=1`.
- [ ] **Step 3: Run** the same-device benchmark for preview latency, action latency, MOVE allocations, window count, CPU and memory. Record device, API, density, build type, configuration and raw values.
- [ ] **Step 4: Exercise** single-finger, long-press, diagonal, rollback, multi-pointer, cancel, focus loss, bottom gestures, central taps, native failure and service restart on a real device when ADB is available.
- [x] **Step 5: Review** input ownership, Root restoration, package/privacy logging, and unrelated dirty files; do not stage or revert unrelated changes.

## Acceptance Gates

- [ ] Every physical region has exactly one input owner.
- [ ] MOVE never executes an action; UP executes at most one action.
- [ ] Rollback, cancel, multi-pointer and focus loss never execute an action.
- [ ] Central touch remains with the foreground app.
- [ ] Configuration updates do not alter an active gesture.
- [ ] Same-side exclusion length is measured after interval union; over-budget state is explicitly reported.
- [ ] Native success suppresses matching Overlay; Native failure creates only the required fallback.
- [ ] Server process starts without chat/video/Compose initialization and recovers its last valid snapshot.
- [ ] No performance claim is made without a same-device, same-build benchmark.
