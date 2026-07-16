# Floating Chat Performance Baseline

## Measurement Protocol

- Build type: `debug` for repeatable local profiling, `release` for release decisions.
- Device: record model, Android version, refresh rate and thermal state.
- Dataset: record conversation count, message count, media count and contact count.
- Iterations: 10 warmups followed by 20 measured iterations.
- Capture: Macrobenchmark startup, overlay expand and message-list scroll; use Perfetto only to investigate a reproduced slow frame.

## Gates

- Cold-start P95 regression: at most 10% over the approved baseline.
- Overlay-expand `frameOverrunMs` P95 regression: at most 10%.
- Message scrolling: no new severe jank in the measured trace.
- Native edge/bottom gesture success rate: no regression from baseline.
- Returning from external media activity: overlay state remains intact.

## Evidence Status

No current P95 is claimed. The workspace has no online Android device at the time of this record, so a benchmark result would not be reproducible. Once a device is online, archive the raw benchmark output and record the exact command, commit, device and dataset here.

## Decision

Performance release gate: **pending device measurement**. Component extraction is not considered performance-approved by source inspection or line-count reduction alone.
