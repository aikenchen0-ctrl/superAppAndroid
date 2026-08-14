import UIKit

struct MomentsKitContext {
    let state: ChatDemoState
    let accountContextsByID: [UUID: OpenApiWeChatAccountContext]
    let conversationContextsByID: [UUID: OpenApiConversationContext]
    let displayNameForAccount: (UUID) -> String
    let displayNameForParticipant: (ChatParticipant) -> String
}

enum MomentsKit {
    static func makeMomentsViewController(context: MomentsKitContext) -> MomentsViewController {
        let state = context.state
        let selectedAccount = state.currentUsers.first { $0.id == state.selectedAccountID } ?? state.currentUser
        let apiAccount = context.accountContextsByID[selectedAccount.id]
        let contactContexts: [OpenApiConversationContext]
        let scopedContacts: [ChatParticipant]

        if let apiAccount {
            contactContexts = context.conversationContextsByID.values
                .filter { $0.ownerWxid == apiAccount.wxid && $0.kind == .contact }
                .sorted { $0.displayName.localizedStandardCompare($1.displayName) == .orderedAscending }
            let contactIDs = Set(contactContexts.map(\.participantID))
            scopedContacts = state.participants
                .filter { contactIDs.contains($0.id) && !$0.isAIAccount }
                .sorted {
                    context.displayNameForParticipant($0)
                        .localizedStandardCompare(context.displayNameForParticipant($1)) == .orderedAscending
                }
        } else {
            contactContexts = []
            scopedContacts = state.participants
                .filter { !$0.isCurrentUser && !$0.isAIAccount }
                .sorted {
                    context.displayNameForParticipant($0)
                        .localizedStandardCompare(context.displayNameForParticipant($1)) == .orderedAscending
                }
        }

        var seenMomentParticipantIDs = Set<UUID>()
        let momentParticipants = ([selectedAccount] + scopedContacts)
            .filter { seenMomentParticipantIDs.insert($0.id).inserted }
        let syncContext = apiAccount.map {
            OpenApiMomentSyncContext(
                weChatId: $0.wxid,
                deviceUuid: $0.clientUuid,
                accountName: context.displayNameForAccount(selectedAccount.id),
                friendWxids: Set(contactContexts.map(\.wxid)),
                friendDisplayNames: Dictionary(contactContexts.map { ($0.wxid, $0.displayName) }, uniquingKeysWith: { current, _ in current }),
                friendAvatarURLs: Dictionary(contactContexts.map { ($0.wxid, $0.avatarURL) }, uniquingKeysWith: { current, _ in current })
            )
        }

        return MomentsViewController(
            owner: selectedAccount,
            accounts: momentParticipants,
            syncContext: syncContext
        )
    }
}
