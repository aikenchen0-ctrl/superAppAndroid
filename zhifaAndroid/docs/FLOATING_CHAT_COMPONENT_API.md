# Floating Chat Component API

## Scope

The stable boundary is a platform-independent `UiState` plus `UiEvent` pair. Android-only objects such as `Context`, `AccessibilityService`, `WindowManager`, database handles and SCRM clients stay in host adapters.

Current contracts:

- `floatingchat.contract.FloatingChatShellState/Event`
- `floatingchat.contract.ContactsUiState/Event`
- `floatingchat.contract.ContactProfileUiState/Event`
- `floatingchat.contract.GroupInfoUiState/Event`
- `floatingchat.contract.GroupMemberScreenUiState/Event`
- `floatingchat.contract.MomentsUiState/Event`

Compose screens consume state and emit events. Reducers and coordinators decide persistence, navigation and platform effects. Native overlay and media surfaces remain in their existing Android modules.

## Compatibility Rules

1. Additive fields must have defaults.
2. Event meaning must remain stable across two consecutive releases.
3. Android platform types must not enter contract packages.
4. Failed platform operations preserve an explicit error state; they must not be converted to success or mock data.
5. A contract is not publishable until it has a consumer outside the defining file and executable unit tests.

## Current Publication Decision

`ubiki-floating-chat-ui` is **not publishable yet**. The UI is still hosted inside `ubiki-accessibility`, the moments/tools panels are being migrated, and there is no second Android consumer proving API stability. Keep the contracts internal until the migration and two-release compatibility gate are satisfied.

## Verification

Run from `zhifaAndroid`:

```powershell
.\gradlew.bat :ubiki-accessibility:testDebugUnitTest --no-daemon
```

Performance P95 values must be recorded from the same physical device, build type, dataset and iteration count. No P95 is claimed until a device is online and the Macrobenchmark run produces a raw result artifact.
