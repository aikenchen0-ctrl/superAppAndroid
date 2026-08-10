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

## SCRM Floating Chat Read Roadmap

The Android host currently reads devices, WeChat accounts, contacts, chatrooms and members. To catch up with the iOS floating chat data flow, the next read-only contracts are deliberately staged:

| Stage | Endpoint | Consumer | Status | Test boundary |
|---|---|---|---|---|
| 0 | `GET /devices`, `/wechat-accounts`, `/contacts`, `/chatrooms`, `/chatrooms/{id}/members` | account rail and avatars | Android code exists; real-device verification pending | Read only |
| 1 | `GET /chat/bootstrap` | conversation list, unread summaries and avatar source | Android contract and fixture test complete; real-device verification pending | Read only; verify request query and response summaries |
| 2 | `GET /chat/history` | selected conversation messages | Android contract and text-message thread mapping complete; real-device verification pending | Cursor, backend-ID fallback and thread-mapping tests |
| 3 | `GET /messages/changes` | incremental messages and unread updates | Android checkpoint, pagination and deduplicated merge complete; real-device verification pending | Sequence cursor, nested message parsing and `hasMore` tests |
| 4 | `GET /contacts/{contactId}/customer-profile` | customer-profile host and profile surface | Android read contract, iOS alias-compatible model and fixture test complete; real-device verification pending | Contact route, `weChatId` query and profile fields; read only |
| 5 | `GET /contacts/{contactId}/detail` | contact-profile host, labels and relationship records | Android aggregate read contract and fixture test complete; real-device verification pending | Contact route, detail limits and aggregate snapshots; read only |
| 6 | `GET /contacts/{friendId}/common-chatrooms` | expandable common-chatroom list | Android paged read contract and fixture test complete; real-device verification pending | Friend route, paging/filter query and member-role summary; read only |
| 7 | `GET /contact-labels` | profile and contact label dictionary | Android read contract and fixture test complete; real-device verification pending | Account/deleted filters and label visual metadata; read only |
| 8 | `GET /contacts/wxids` | batch friend filtering and downstream selection | Android filter query and direct wxid-list contract complete; real-device verification pending | Profile/label filters and wxid list parsing; read only |
| 9 | `POST /contacts/labels` | single-contact full label replacement | Android write contract complete; manual verification pending | Do not automate: record current labels, issue one request in Web panel, then re-read contact detail |
| 10 | `POST /contacts/labels/batch` | explicit friend-list label merge or replacement | Android request, aggregate response and per-friend task contracts complete; manual verification pending | Do not automate: use one test friend, `mergeExisting=true`, `maxCount=1`, query its task, then re-read contact detail |

### Extended contracts pending UI integration

The Android client now defines, but does not automatically invoke, the following SCRM domains:

- Contact and permissions: label lifecycle, filter-based labels, customer-profile writes, friend deletion/profile refresh, and single/batch/filter friend permissions.
- Message operations: card templates, emoji and mini-program cards, batch send, sync commands, forwarding, revoke, detail/media recovery, and voice transcription.
- Chatroom management: filtered create/invite/kick, invite approval, jielong, QR join, managers, room settings, and owner transfer.

Every mutating contract contains KDoc describing the missing UI entry point and manual test boundary. Message sending, uploads and other mutating endpoints remain excluded from automated verification.

Message sending, uploads and other mutating endpoints are excluded from automated verification and remain manual test tasks.
