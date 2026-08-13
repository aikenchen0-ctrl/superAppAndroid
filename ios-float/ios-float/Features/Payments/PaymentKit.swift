import UIKit

enum PaymentKit {
    static func paymentStatusController(accountName: String, weChatId: String) -> OpenApiPaymentStatusViewController {
        OpenApiPaymentStatusViewController(accountName: accountName, weChatId: weChatId)
    }

    static func redPacketComposer(conversationName: String, maxPacketCount: Int) -> RedPacketComposerViewController {
        RedPacketComposerViewController(conversationName: conversationName, maxPacketCount: maxPacketCount)
    }

    static func transferComposer(receiverName: String) -> TransferComposerViewController {
        TransferComposerViewController(receiverName: receiverName)
    }

    static func splitBillComposer(groupName: String, selectedMembers: [ChatParticipant]) -> SplitBillComposerViewController {
        SplitBillComposerViewController(groupName: groupName, selectedMembers: selectedMembers)
    }

    static func memberSelectionController(
        titleText: String,
        subtitleText: String,
        members: [ChatParticipant],
        allowsMultipleSelection: Bool,
        selectedIDs: Set<UUID>
    ) -> PaymentMemberSelectionViewController {
        PaymentMemberSelectionViewController(
            titleText: titleText,
            subtitleText: subtitleText,
            members: members,
            allowsMultipleSelection: allowsMultipleSelection,
            selectedIDs: selectedIDs
        )
    }

    static func confirmationController(
        title: String,
        amountText: String,
        subtitle: String,
        confirmTitle: String
    ) -> WeChatPaymentConfirmationViewController {
        WeChatPaymentConfirmationViewController(
            titleText: title,
            amountText: amountText,
            subtitleText: subtitle,
            confirmTitle: confirmTitle
        )
    }
}
