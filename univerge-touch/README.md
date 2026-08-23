# Univerge Touch Android SDK

Reusable Android library for four-class touch classification:

- `thumb_light`
- `thumb_heavy`
- `index_light`
- `index_heavy`

Maven coordinate after publication:

```gradle
implementation "com.paifa.univerge:univerge-touch:1.1.0"
```

## Public API

Create `AispectTouchClassifier`, set an `AispectTouchListener`, call `start()`, and forward each view `MotionEvent` through `handleMotionEvent(event, width, height)`. Call `stop()` or `close()` when the host lifecycle ends.

`AispectTouchConfig` controls gesture limits, diagnostic inference, and canonical remote-model settings. The host needs the `INTERNET` permission only when remote updates are enabled. High-rate sensor access is declared by the library manifest.

## Model Policy

The APK contains one legacy four-class fallback model. It remains available when no verified downloaded model is active. Historical, ablation, collection, and all-data-fit-only experimental models are intentionally excluded.

Remote updates use the canonical assignment endpoint configured by:

- `remoteModelBaseUrl`
- `remoteModelAppId`
- `remoteModelAppVersion`
- `remoteModelDeviceId`
- `remoteModelAllowedHosts`

The SDK requires HTTPS by default. An assignment and both artifacts must pass SHA-256, immutable-version storage, input/output contract checks, and runtime loading before activation. Current and previous verified versions are retained for rollback. A model received during a touch is activated only after that touch ends.

## Causal Time-Grid Models

Remote `time_grid_groupnorm_cnn_v1` models are accepted only with one of these exact contracts:

- `causal_time_grid_imu11_touch7_mask5_delta_gravity_v1`: `[9, 23]`
- `causal_time_grid_touch7_mask5_no_gravity_v1`: `[9, 20]`

The runtime never infers a contract from the channel count. It waits until hardware-time IMU data supports the fixed `-15ms, -10ms, ..., +25ms` grid relative to `ACTION_DOWN`, and then emits one `PRESS` result. Touches that lift before `+25ms` do not receive a causal prediction. The facade de-duplicates the later touch-end status for that same touch sequence.

## Build

From this directory on Windows:

```powershell
java -classpath gradle\wrapper\gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain testDebugUnitTest --no-daemon --max-workers=1 --offline
java -classpath gradle\wrapper\gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain assembleRelease --no-daemon --max-workers=1 --offline
```
