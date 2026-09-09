# Aispect Touch Android SDK

Reusable Android library for four-class touch classification:

- `thumb_light`
- `thumb_heavy`
- `index_light`
- `index_heavy`

Maven coordinate after publication:

```gradle
implementation "com.zhifaios.eyes:aispect-touch-android:1.1.0"
```

## Public API

Create `AispectTouchClassifier`, set an `AispectTouchListener`, call `start()`, and forward each view `MotionEvent` through `handleMotionEvent(event, width, height)`. Call `stop()` or `close()` when the host lifecycle ends.

`AispectTouchConfig` controls gesture limits, diagnostic inference, and canonical remote-model settings. The host needs the `INTERNET` permission only when remote updates are enabled. High-rate sensor access is declared by the library manifest.

For remote model management, set `remoteModelBaseUrl`, `remoteModelAppId`, and optionally `remoteModelAppVersion`, `remoteModelDeviceId`, and `remoteModelDeviceName`. When `remoteModelDeviceId` is empty, the SDK creates an App-private UUID in `SharedPreferences`; it is not an IMEI, serial number, or Android hardware identifier. Read it with `remoteModelDeviceId()` and read the display name with `remoteModelDeviceName()`.

## Model Policy

The library contains one legacy four-class fallback model. It remains available when no verified downloaded model is active. Historical, ablation, collection, and all-data-fit-only experimental models are intentionally excluded.

Remote updates use the canonical assignment endpoint configured by:

- `remoteModelBaseUrl`
- `remoteModelAppId`
- `remoteModelAppVersion`
- `remoteModelDeviceId`
- `remoteModelAllowedHosts`

When `remoteModelBaseUrl` and `remoteModelAppId` are configured, calling `start()` registers the host App/device with `POST /api/v1/devices/register` and then performs one background assignment refresh (both best effort). The device then appears under the management panel's registered devices and can receive device-scoped model assignments. `lastRemoteModelUpdateResult()` exposes the latest refresh outcome; manual `refreshRemoteModel()` and `refreshRemoteModelAsync(...)` remain available.

The management panel can apply one published model to multiple selected registered devices through a transactional batch assignment. A failed batch does not leave partial device rules. HTTP endpoints require explicit `allowInsecureRemoteModelTransport = true`; production deployments should use HTTPS.

The runtime caches the immutable model-catalog snapshot used by status callbacks. Repeated touch/status events do not copy or reparse the catalog; the snapshot is invalidated only after a verified remote catalog activation or explicit catalog reload.

The causal and fieldwise time-grid builders also reuse scalar gravity state instead of allocating a temporary three-element array for every slot; output values and channel order remain unchanged.

The SDK requires HTTPS by default. An assignment and both artifacts must pass SHA-256, immutable-version storage, input/output contract checks, and runtime loading before activation. Current and previous verified versions are retained for rollback. A model received during a touch is activated only after that touch ends.

## Causal Time-Grid Models

Remote `time_grid_groupnorm_cnn_v1` models are accepted only with one of these exact contracts:

- `causal_time_grid_imu11_touch7_mask5_delta_gravity_v1`: `[9, 23]`
- `causal_time_grid_touch7_mask5_no_gravity_v1`: `[9, 20]`

The runtime never infers a contract from the channel count. It waits until hardware-time IMU data supports the fixed `-15ms, -10ms, ..., +25ms` grid relative to `ACTION_DOWN`, and then emits one `PRESS` result. Touches that lift before `+25ms` do not receive a causal prediction. The facade de-duplicates the later touch-end status for that same touch sequence.

## Fieldwise Size Models

SDK `1.1.0` accepts remotely assigned `aispect-fieldwise-size-json-v1` models with the `aispect-fieldwise-size-cnn-v1` feature schema. These models use 22 channels: canonical no-gravity IMU, delta gravity, independently coordinated `size`, `touchMajor`, and `touchMinor` values and rates, plus availability masks.

Only these exact 5 ms physical-time grids are accepted:

- 9 slots: `-15ms ... +25ms`
- 13 slots: `-25ms ... +35ms`
- 17 slots: `-35ms ... +45ms`
- 21 slots: `-45ms ... +55ms`
- 25 slots: `-55ms ... +65ms`

Known input-device profiles may use centered-log, robust Z-score, or quantile-normal coordination. Unknown or unreliable profiles fall back independently for each touch-size field to the causal within-touch baseline; unavailable values remain zero only together with their availability mask. Touch samples are aligned by causal zero-order hold, while IMU samples use hardware-time interpolation.

The reusable SDK keeps only the selected verified remote model and its rollback predecessor. It does not automatically download or retain the full experimental model catalog. Model download, verification, and preload run on the SDK background update executor; activation is deferred while a touch is active.

## Build

From this directory on Windows:

```powershell
java -classpath gradle\wrapper\gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain testDebugUnitTest --no-daemon --max-workers=1 --offline
java -classpath gradle\wrapper\gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain assembleRelease --no-daemon --max-workers=1 --offline
```
