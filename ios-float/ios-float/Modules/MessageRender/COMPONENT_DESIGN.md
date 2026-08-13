# Message Component Design

## Compatibility Rules

- `ChatMessage` and the existing SQLite schema remain the source of truth.
- Existing enum raw values, notification names, storage keys, OpenAPI paths, request fields, and task polling behavior do not change.
- Componentization does not change fonts, colors, spacing, radii, constraints, or Dynamic Type behavior.
- Every migration keeps a legacy renderer fallback until its replacement passes the same tests and smoke flow.

## Component Contracts

### Model

`MessageRenderModel` is a read-only projection of `ChatMessage`. It may normalize display inputs but must retain the source message ID and type.

### State

`MessageRenderState` contains transient presentation state only: playback, AI streaming, blur, 3D appearance, delivery, selection, and group display.

### Style

`MessageStyleTokens.legacy` is the initial token source. Its values must be copied from the current implementation without redesign.

### Renderer

Renderers are grouped by behavior:

- Text and System
- Voice and Media
- File and Card
- Payment and Transfer

Unmigrated message types use `LegacyMessageRenderer`.

### Events

Views emit typed `MessageInteractionEvent` values. Navigation, persistence, payment, forwarding, and OpenAPI side effects remain outside rendering views.

### Reuse

Every renderer must clear closures, hit targets, transforms, hidden state, active constraints, represented IDs, represented URLs, and asynchronous tasks before rendering another message.

## Migration Gates

1. Add characterization tests before changing production rendering.
2. Add state, token, layout, event, and registry contracts without switching behavior.
3. Register all 30 message types with the legacy fallback.
4. Migrate one renderer group at a time behind a version switch.
5. Build Debug and Release, run all tests, and complete the relevant device smoke flow after each group.

## Current Coverage

All 30 `ChatMessageType` cases are registered as componentized renderers:

- `SystemMessageRenderer`: system.
- `TextMessageRenderer`: text, emoji, quoted reply, relay, and group notice.
- `MediaMessageRenderer`: image, captured photo, sticker GIF, video, and channels video.
- `VoiceCallMessageRenderer`: voice, voice call, and video call.
- `CardMessageRenderer`: file, location, live location, contact card, group invite, web link, article, mini program, channels live, music, favorite, merged forward, and coupon.
- `PaymentMessageRenderer`: red packet, transfer, and split bill.

The first componentization stage intentionally wraps the existing `ChatMessageBubbleView` so rendering, measurement, reuse, SQLite, OpenAPI, payment, and navigation behavior remain unchanged. `LegacyMessageRenderer` remains available as a defensive fallback.
