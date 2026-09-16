# Edge Gesture SDK Boundary

## Goal

Make the edge gesture launcher reusable without importing floating chat, video,
Compose, or the host application's activities. One authorized accessibility
service may host both the SDK runtime and optional chat runtime.

## Boundary

`univerge-core` owns immutable gesture data, recognizers, configuration, and the
host action callback. It has no Android, accessibility, WindowManager, Compose,
chat, or video dependency.

The Android adapter owns the accessibility service, Native/Overlay input
backends, preview windows, and system action adaptation. The host supplies an
`EdgeGestureActionHandler`; chat actions are optional callbacks, not controller
references.

Chat visibility does not stop Native edge input. It only affects the chat window's
own touchable surface. The SDK retains one physical input owner per region.

## Lifecycle

The host service creates separate `EdgeGestureRuntime` and
`FloatingChatRuntime` instances. Each has independent `start`, `stop`, and
`close` operations. Service destruction remains the platform-level boundary and
closes both runtimes.

## Current slice

This change introduces the pure Kotlin SDK contracts and tests. Existing service
wiring remains temporarily compatible; later slices migrate action execution and
input ownership behind these contracts.
