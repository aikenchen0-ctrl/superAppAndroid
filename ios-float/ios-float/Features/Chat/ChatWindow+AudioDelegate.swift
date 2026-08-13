import UIKit
@preconcurrency import AVFoundation
import AVKit
import BlinkVoiceKit
import CoreImage
import CoreLocation
import CoreMotion
import CryptoKit
import EventKit
import ImageIO
import MapKit
import MobileCoreServices
import Photos
import PhotosUI
import QuickLook
import Security
import UniformTypeIdentifiers
import Vision

fileprivate struct OpenApiRedPacketSnapshot {
    let detail: String
    let receivedAmount: String?
    let canOpen: Bool
    let isFinished: Bool
}


extension ChatWindowViewController: AVAudioPlayerDelegate {
    func audioPlayerDidFinishPlaying(_ player: AVAudioPlayer, successfully flag: Bool) {
        DispatchQueue.main.async { [weak self] in
            guard let self, self.audioPlayer === player else { return }
            let previousID = self.playingVoiceMessageID
            self.audioPlayer?.delegate = nil
            self.audioPlayer = nil
            self.playingVoiceMessageID = nil
            try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
            self.refreshVoicePlaybackCells(messageIDs: [previousID])
            self.syncIslandActivityIfNeeded()
        }
    }

    func audioPlayerDecodeErrorDidOccur(_ player: AVAudioPlayer, error: Error?) {
        DispatchQueue.main.async { [weak self] in
            guard let self, self.audioPlayer === player else { return }
            self.stopVoicePlayback(shouldShowNotice: false)
            self.showNotice("语音无法播放")
        }
    }
}



extension ChatWindowViewController {
    func openApiPaymentMessageServerID(for message: ChatMessage) -> String? {
        if let backendMessageID = message.backendMessageID?.trimmingCharacters(in: .whitespacesAndNewlines),
           !backendMessageID.isEmpty,
           backendMessageID != "0" {
            return backendMessageID
        }

        // Keep a hidden fallback for locally reconstructed messages. The visible
        // payment card never renders this line, but it lets a later action use
        // the server ID before the next full chat sync.
        let keys = ["msgSvrId：", "消息服务器ID：", "消息ID："]
        for line in message.detail.components(separatedBy: .newlines) {
            guard let key = keys.first(where: { line.hasPrefix($0) }) else { continue }
            let value = String(line.dropFirst(key.count)).trimmingCharacters(in: .whitespacesAndNewlines)
            if !value.isEmpty, value != "0" { return value }
        }
        return nil
    }

    fileprivate func loadOpenApiRedPacketSnapshot(
        account: OpenApiSocialAccount,
        messageServerID: String,
        fallbackDetail: String,
        receiverName: String
    ) async throws -> OpenApiRedPacketSnapshot {
        let service = OpenApiPaymentService()
        let statusResult = try await service.redPacketStatus(
            account: account,
            messageServerID: messageServerID
        )
        let detailResult = try await service.redPacketDetail(
            account: account,
            messageServerID: messageServerID
        )
        let statusPayload = openApiPaymentPayload(statusResult.jsonObject)
        let detailPayload = openApiPaymentPayload(detailResult.jsonObject)
        let combined: [String: Any] = statusPayload.merging(detailPayload) { _, detailValue in detailValue }

        if openApiPaymentResponseFailed(statusPayload) || openApiPaymentResponseFailed(detailPayload) {
            let message = openApiPaymentResponseMessage(statusPayload)
                ?? openApiPaymentResponseMessage(detailPayload)
                ?? "后端返回 success=false"
            throw OpenApiMediaSendError.taskFailed("读取红包状态", message)
        }

        let entries = openApiRedPacketEntries(from: combined)
        let fallbackEntries = redPacketReceiptEntries(from: fallbackDetail)
        let mergedEntries = entries.isEmpty ? fallbackEntries : entries
        let totalAmount = openApiPaymentAmount(
            from: combined,
            yuanKeys: ["totalAmount", "packetAmount", "redPacketAmount", "amount", "money"],
            fenKeys: ["totalAmountFen", "packetAmountFen", "redPacketAmountFen", "amountFen", "moneyFen"]
        ) ?? Decimal(redPacketTotalAmount(from: fallbackDetail))
        let totalCount = openApiPaymentInt(
            from: combined,
            keys: ["totalCount", "packetCount", "number", "total", "totalNum", "count"]
        ) ?? redPacketProgress(from: fallbackDetail)?.total
        let receivedCount = openApiPaymentInt(
            from: combined,
            keys: ["receivedCount", "claimedCount", "takenCount", "openedCount", "received", "claimed"]
        ) ?? redPacketProgress(from: fallbackDetail)?.received
        let explicitReceivedAmount = openApiPaymentAmount(
            from: combined,
            yuanKeys: ["receivedAmount", "claimedAmount", "takenAmount", "myAmount", "receiveAmount"],
            fenKeys: ["receivedAmountFen", "claimedAmountFen", "takenAmountFen", "myAmountFen", "receiveAmountFen"]
        )
        let receivedAmount = explicitReceivedAmount.map(openApiMoneyText)
            ?? mergedEntries.first(where: {
                normalizePaymentName($0.name) == normalizePaymentName(receiverName)
            })?.amount

        let statusText = openApiPaymentStatusText(from: combined)
        let statusLowercased = statusText.lowercased()
        let explicitFinished = openApiPaymentBool(
            from: combined,
            keys: ["isFinished", "finished", "isComplete", "completed", "isOver", "expired", "isExpired"]
        )
        let finishedByStatus = [
            "finished", "complete", "completed", "exhausted", "expired", "closed",
            "已领完", "已抢完", "已结束", "已过期", "已领取"
        ].contains { statusLowercased.contains($0.lowercased()) }
        let isFinished = explicitFinished ?? finishedByStatus
            || ((totalCount ?? 0) > 0 && (receivedCount ?? 0) >= (totalCount ?? 0))

        let explicitCanOpen = openApiPaymentBool(
            from: combined,
            keys: ["canOpen", "canReceive", "canTake", "可领取", "可抢"]
        )
        let alreadyClaimed = openApiPaymentBool(
            from: combined,
            keys: ["isReceived", "isClaimed", "claimed", "received", "hasReceived", "hasClaimed"]
        ) ?? (receivedAmount != nil)
        let canOpen = explicitCanOpen ?? (!isFinished && !alreadyClaimed)
        let detail = openApiRedPacketDetailText(
            fallback: fallbackDetail,
            totalAmount: totalAmount,
            receivedCount: receivedCount,
            totalCount: totalCount,
            statusText: statusText,
            entries: mergedEntries
        )
        return OpenApiRedPacketSnapshot(
            detail: detail,
            receivedAmount: receivedAmount,
            canOpen: canOpen && !isFinished,
            isFinished: isFinished
        )
    }

    private func openApiPaymentPayload(_ object: Any?) -> [String: Any] {
        if let dictionary = object as? [String: Any] {
            var payload = dictionary
            for key in ["data", "result", "payload", "redPacket", "redpacket", "detail", "status"] {
                if let nested = openApiValue(in: dictionary, matching: key) as? [String: Any] {
                    payload.merge(openApiPaymentPayload(nested)) { _, nestedValue in nestedValue }
                } else if let nested = openApiValue(in: dictionary, matching: key) as? [Any] {
                    payload["items"] = nested
                }
            }
            return payload
        }
        if let array = object as? [Any] {
            return ["items": array]
        }
        if let text = object as? String,
           let data = text.data(using: .utf8),
           let decoded = try? JSONSerialization.jsonObject(with: data) {
            return openApiPaymentPayload(decoded)
        }
        return [:]
    }

    private func openApiPaymentResponseFailed(_ payload: [String: Any]) -> Bool {
        guard let value = openApiValue(in: payload, matching: "success") else { return false }
        if let bool = value as? Bool { return !bool }
        if let number = value as? NSNumber { return !number.boolValue }
        if let text = value as? String {
            return ["false", "0", "fail", "failed", "error"].contains(text.lowercased())
        }
        return false
    }

    private func openApiPaymentResponseMessage(_ payload: [String: Any]) -> String? {
        for key in ["message", "error", "errorMessage", "detail", "reason", "msg"] {
            if let value = openApiValue(in: payload, matching: key) as? String {
                let text = value.trimmingCharacters(in: .whitespacesAndNewlines)
                if !text.isEmpty { return text }
            }
        }
        return nil
    }

    private func openApiPaymentStatusText(from payload: [String: Any]) -> String {
        for key in ["statusText", "statusName", "stateText", "status", "state", "packetStatus", "redPacketStatus"] {
            if let value = openApiValue(in: payload, matching: key) {
                let text = String(describing: value).trimmingCharacters(in: .whitespacesAndNewlines)
                if !text.isEmpty { return text }
            }
        }
        return ""
    }

    private func openApiPaymentBool(from payload: [String: Any], keys: [String]) -> Bool? {
        for key in keys {
            guard let value = openApiValue(in: payload, matching: key) else { continue }
            if let bool = value as? Bool { return bool }
            if let number = value as? NSNumber { return number.boolValue }
            if let text = value as? String {
                let normalized = text.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
                if ["true", "1", "yes", "y", "success", "可领取", "可抢"].contains(normalized) { return true }
                if ["false", "0", "no", "n", "fail", "failed", "已领取", "不可领取"].contains(normalized) { return false }
            }
        }
        return nil
    }

    private func openApiPaymentInt(from payload: [String: Any], keys: [String]) -> Int? {
        for key in keys {
            guard let value = openApiValue(in: payload, matching: key) else { continue }
            if let number = value as? NSNumber { return number.intValue }
            if let text = value as? String, let number = Int(text.trimmingCharacters(in: .whitespacesAndNewlines)) {
                return number
            }
        }
        return nil
    }

    private func openApiPaymentAmount(
        from payload: [String: Any],
        yuanKeys: [String],
        fenKeys: [String]
    ) -> Decimal? {
        for key in fenKeys {
            if let value = openApiDecimalValue(openApiValue(in: payload, matching: key)) {
                return value / 100
            }
        }
        for key in yuanKeys {
            if let value = openApiDecimalValue(openApiValue(in: payload, matching: key)) {
                return value
            }
        }
        return nil
    }

    private func openApiMoneyText(_ value: Decimal) -> String {
        let number = NSDecimalNumber(decimal: value)
        return String(format: "%.2f", number.doubleValue)
    }

    private func openApiRedPacketEntries(from payload: [String: Any]) -> [(name: String, amount: String)] {
        let arrayKeys = ["items", "records", "claims", "receivers", "receiveList", "redPacketDetails", "details", "list"]
        guard let array = openApiPaymentArray(in: payload, keys: arrayKeys) else { return [] }
            let entries = array.compactMap { item -> (name: String, amount: String)? in
                guard let dictionary = item as? [String: Any] else { return nil }
                let name = ["remark", "nickname", "nickName", "displayName", "receiverName", "friendNo", "userName", "wxid", "name"]
                    .compactMap { openApiValue(in: dictionary, matching: $0) as? String }
                    .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
                    .first { !$0.isEmpty && !$0.hasPrefix("wxid_") } ?? ""
                let amount = openApiPaymentAmount(
                    from: dictionary,
                    yuanKeys: ["amount", "money", "amountYuan", "moneyYuan", "receiveAmount"],
                    fenKeys: ["amountFen", "moneyFen", "receiveAmountFen"]
                ).map(openApiMoneyText) ?? ""
                guard !name.isEmpty, !amount.isEmpty else { return nil }
                return (name, amount)
            }
        return entries
    }

    private func openApiPaymentArray(in object: Any?, keys: [String]) -> [Any]? {
        if let array = object as? [Any] { return array }
        guard let dictionary = object as? [String: Any] else { return nil }
        for key in keys {
            if let array = openApiValue(in: dictionary, matching: key) as? [Any] {
                return array
            }
        }
        for key in ["data", "result", "payload", "redPacket", "redpacket", "detail", "status"] {
            if let nested = openApiValue(in: dictionary, matching: key),
               let array = openApiPaymentArray(in: nested, keys: keys) {
                return array
            }
        }
        return nil
    }

    private func openApiRedPacketDetailText(
        fallback: String,
        totalAmount: Decimal,
        receivedCount: Int?,
        totalCount: Int?,
        statusText: String,
        entries: [(name: String, amount: String)]
    ) -> String {
        var lines = fallback.components(separatedBy: .newlines)
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty && !$0.hasPrefix("领取明细：") && !$0.contains("：¥") }
        if totalAmount > 0 {
            let totalLine = "红包金额：¥\(openApiMoneyText(totalAmount))"
            if let index = lines.firstIndex(where: { $0.hasPrefix("红包金额：") }) {
                lines[index] = totalLine
            } else {
                lines.insert(totalLine, at: 0)
            }
        }
        if let totalCount {
            let received = min(max(receivedCount ?? entries.count, 0), max(totalCount, 1))
            let progressLine = "已领取：\(received)/\(max(totalCount, 1))"
            if let index = lines.firstIndex(where: { $0.hasPrefix("已领取：") }) {
                lines[index] = progressLine
            } else {
                lines.append(progressLine)
            }
        }
        let normalizedStatus: String
        let lower = statusText.lowercased()
        if lower.contains("expired") || lower.contains("finished") || lower.contains("complete") || lower.contains("已领完") || lower.contains("已抢完") {
            normalizedStatus = "已领完"
        } else if lower.contains("claimed") || lower.contains("received") || lower.contains("已领取") {
            normalizedStatus = "已领取"
        } else if statusText.isEmpty {
            normalizedStatus = lines.first(where: { $0.hasPrefix("状态：") })?.replacingOccurrences(of: "状态：", with: "") ?? "等待领取"
        } else {
            normalizedStatus = statusText
        }
        let statusLine = "状态：\(normalizedStatus)"
        if let index = lines.firstIndex(where: { $0.hasPrefix("状态：") }) {
            lines[index] = statusLine
        } else {
            lines.append(statusLine)
        }
        lines.append("领取明细：")
        lines.append(contentsOf: entries.map { "\($0.name)：¥\($0.amount)" })
        return lines.joined(separator: "\n")
    }

    func deleteAutoReplyPrediction(_ message: ChatMessage) {
        guard isAutoReplyPrediction(message) else {
            deleteMessage(message)
            return
        }
        if let sourceMessageID = autoReplySourceMessageID(from: message) {
            autoReplyProcessedMessageIDs.insert(sourceMessageID)
        }
        autoReplyActiveConversationIDs.remove(message.conversationID)
        streamingAIMessageIDs.remove(message.id)
        state.messages.removeAll { $0.id == message.id }
        ChatSQLiteStore.shared.deleteMessages(ids: [message.id])
        persistMessages()
        messageCollectionView.reloadData()
        rightAccountCollectionView.reloadData()
        rightToolCollectionView.reloadData()
        updateConnections()
        syncIslandActivityIfNeeded()
        showNotice("已删除草稿")
    }

    func regenerateAutoReplyPrediction(_ message: ChatMessage) {
        guard let sourceID = autoReplySourceMessageID(from: message),
              let sourceMessage = state.messages.first(where: { $0.id == sourceID })
        else {
            showMessageActionNotice("找不到原始消息，无法重新生成")
            return
        }
        guard !autoReplyActiveConversationIDs.contains(message.conversationID) else {
            showNotice("AI 正在生成中")
            return
        }
        autoReplyActiveConversationIDs.insert(message.conversationID)
        streamingAIMessageIDs.insert(message.id)
        setAutoReplyDraftState(messageID: message.id, state: .generating)
        let receiverAccountID = autoReplyReceiverAccountID(from: message) ?? autoReplyReceiverAccountID(for: sourceMessage)
        let plan = makeAutoReplyIntentPlan(for: sourceMessage)
        updateAutoReplyCalendarStatus(messageID: message.id, status: autoReplyInitialExecutionStatus(for: plan), eventID: nil, plan: plan)
        replaceAutoReplyPredictionBody(id: message.id, body: "AI自动回复重新生成中...")
        let prompt = autoReplyPrompt(for: sourceMessage, receiverAccountID: receiverAccountID, intentPlan: plan)
        var streamedText = ""
        streamAutoReplyMessage(prompt: prompt) { [weak self] delta in
            DispatchQueue.main.async {
                streamedText += delta
                self?.replaceAutoReplyPredictionBody(id: message.id, body: streamedText)
            }
        } completion: { [weak self] result in
            DispatchQueue.main.async {
                guard let self else { return }
                self.autoReplyActiveConversationIDs.remove(message.conversationID)
                switch result {
                case .success:
                    self.replaceAutoReplyPredictionBody(id: message.id, body: streamedText)
                    self.finishAIStreamingMessage(id: message.id)
                    self.setAutoReplyDraftState(messageID: message.id, state: .ready)
                case .failure(let error):
                    self.replaceAutoReplyPredictionBody(
                        id: message.id,
                        body: streamedText.isEmpty ? "AI 草稿生成失败，请点击后重新生成。" : streamedText
                    )
                    self.finishAIStreamingMessage(id: message.id)
                    self.setAutoReplyDraftState(
                        messageID: message.id,
                        state: .generationFailed,
                        failureReason: error.localizedDescription
                    )
                    self.showNotice("AI 自动回复重新生成失败：\(error.localizedDescription)")
                }
                self.reloadVisibleRightToolItems()
            }
        }
    }

    func presentReceiveRedPacket(for message: ChatMessage) {
        if !canCurrentAccountReceiveRedPacket(message) {
            showMessageActionNotice(redPacketRestrictionText(for: message))
            return
        }
        let backendMessageID = openApiPaymentMessageServerID(for: message)
        let packetProgress = redPacketProgress(from: message.detail)
        let receiverName = displayNameForAccount(id: state.selectedAccountID)
        let alreadyReceivedAmount = redPacketReceivedAmount(for: receiverName, detail: message.detail)
        let isFullyReceived = packetProgress.map { $0.received >= $0.total } ?? message.detail.contains("已领取")
        let isReceived = alreadyReceivedAmount != nil || isFullyReceived
        let controller = RedPacketOpenViewController(
            greeting: message.body.isEmpty ? "恭喜发财，大吉大利" : message.body,
            detail: message.detail,
            receiverName: receiverName,
            alreadyReceivedAmount: alreadyReceivedAmount,
            canOpen: !isReceived,
            isFinished: isFullyReceived,
            entries: redPacketReceiptEntries(from: message.detail)
        )
        controller.onOpen = { [weak self, weak controller] in
            guard let self else { return }
            guard let backendMessageID,
                  let context = self.openApiSendContext(for: message, allowsCurrentSelectionFallback: false)
            else {
                self.showNotice("红包缺少真实 msgSvrId 或帐号上下文，未执行领取")
                return
            }
            let account = OpenApiSocialAccount(
                deviceUUID: context.account.clientUuid,
                weChatID: context.account.wxid
            )
            controller?.showRemoteState(
                detail: message.detail,
                entries: self.redPacketReceiptEntries(from: message.detail),
                alreadyReceivedAmount: nil,
                canOpen: false,
                isFinished: false
            )
            self.showNotice("正在领取红包…")
            Task { @MainActor in
                do {
                    let taskID = try await OpenApiPaymentService().takeRedPacket(
                        account: account,
                        messageServerID: backendMessageID
                    )
                    let snapshot = try await self.loadOpenApiRedPacketSnapshot(
                        account: account,
                        messageServerID: backendMessageID,
                        fallbackDetail: self.state.messages.first(where: { $0.id == message.id })?.detail ?? message.detail,
                        receiverName: receiverName
                    )
                    self.updateMessageDetail(id: message.id, detail: snapshot.detail)
                    controller?.showRemoteState(
                        detail: snapshot.detail,
                        entries: self.redPacketReceiptEntries(from: snapshot.detail),
                        alreadyReceivedAmount: snapshot.receivedAmount,
                        canOpen: false,
                        isFinished: snapshot.isFinished
                    )
                    self.insertSystemMessage(
                        "\(receiverName)已领取红包",
                        conversationID: message.conversationID,
                        forceReload: true
                    )
                    self.showNotice("领取红包已完成\(taskID.isEmpty ? "" : "：\(taskID)")")
                } catch {
                    controller?.showRemoteState(
                        detail: message.detail,
                        entries: self.redPacketReceiptEntries(from: message.detail),
                        alreadyReceivedAmount: nil,
                        canOpen: true,
                        isFinished: false
                    )
                    self.showNotice("领取红包失败：\(error.localizedDescription)")
                }
            }
        }
        present(controller, animated: true)
        if let backendMessageID,
           let context = openApiSendContext(for: message, allowsCurrentSelectionFallback: false) {
            let account = OpenApiSocialAccount(
                deviceUUID: context.account.clientUuid,
                weChatID: context.account.wxid
            )
            Task { @MainActor in
                do {
                    let snapshot = try await self.loadOpenApiRedPacketSnapshot(
                        account: account,
                        messageServerID: backendMessageID,
                        fallbackDetail: message.detail,
                        receiverName: receiverName
                    )
                    self.updateMessageDetail(id: message.id, detail: snapshot.detail)
                    controller.showRemoteState(
                        detail: snapshot.detail,
                        entries: self.redPacketReceiptEntries(from: snapshot.detail),
                        alreadyReceivedAmount: snapshot.receivedAmount,
                        canOpen: snapshot.canOpen,
                        isFinished: snapshot.isFinished
                    )
                } catch {
                    self.showNotice("读取红包状态失败：\(error.localizedDescription)")
                }
            }
        }
    }

    func canCurrentAccountReceiveRedPacket(_ message: ChatMessage) -> Bool {
        if let group = state.participants.first(where: { $0.id == message.conversationID }),
           isGroupConversation(group) {
            return currentAccountIDsInGroup(group).contains(state.selectedAccountID)
        }
        return !message.isOutgoing && !message.sender.isCurrentUser
    }

    func redPacketRestrictionText(for message: ChatMessage) -> String {
        if message.isGroupConversation {
            return "当前帐号不在该群聊中，不能领取这个红包"
        }
        if message.isOutgoing {
            return "单聊红包只能由对方领取"
        }
        return "当前帐号不能领取这个红包"
    }

    func redPacketProgress(from detail: String) -> (received: Int, total: Int)? {
        guard let line = detail
            .components(separatedBy: .newlines)
            .first(where: { $0.hasPrefix("已领取：") })
        else { return nil }
        let progress = line
            .replacingOccurrences(of: "已领取：", with: "")
            .split(separator: "/")
            .compactMap { Int($0.trimmingCharacters(in: .whitespacesAndNewlines)) }
        guard progress.count == 2 else { return nil }
        return (progress[0], max(progress[1], 1))
    }

    func redPacketReceivedAmount(for name: String, detail: String) -> String? {
        detail.components(separatedBy: .newlines)
            .first { $0.hasPrefix("\(name)：¥") }
            .map { $0.replacingOccurrences(of: "\(name)：¥", with: "") }
    }

    func redPacketReceiptEntries(from detail: String) -> [(name: String, amount: String)] {
        detail.components(separatedBy: .newlines).compactMap { line in
            guard let separator = line.range(of: "：¥") else { return nil }
            let name = String(line[..<separator.lowerBound]).trimmingCharacters(in: .whitespacesAndNewlines)
            let amount = String(line[separator.upperBound...]).trimmingCharacters(in: .whitespacesAndNewlines)
            guard !name.isEmpty, !amount.isEmpty, !name.hasPrefix("红包金额"), !name.hasPrefix("状态"), !name.hasPrefix("已领取") else {
                return nil
            }
            return (name, amount)
        }
    }

    func redPacketTotalAmount(from detail: String) -> Double {
        guard let line = detail
            .components(separatedBy: .newlines)
            .first(where: { $0.hasPrefix("红包金额：¥") })
        else { return 0 }
        return Double(line.replacingOccurrences(of: "红包金额：¥", with: "")) ?? 0
    }

    func redPacketAmountForNextReceive(_ detail: String) -> String {
        let totalAmount = redPacketTotalAmount(from: detail)
        guard totalAmount > 0 else { return "0.00" }
        let progress = redPacketProgress(from: detail)
        let receivedCount = progress?.received ?? 0
        let totalCount = max(progress?.total ?? 1, 1)
        let remainingCount = max(totalCount - receivedCount, 1)
        let receivedAmount = redPacketReceiptEntries(from: detail)
            .compactMap { Double($0.amount) }
            .reduce(0, +)
        let remainingCents = max(Int((totalAmount * 100).rounded()) - Int((receivedAmount * 100).rounded()), 0)
        guard remainingCount > 1 else {
            return String(format: "%.2f", Double(remainingCents) / 100.0)
        }
        let minimumReserve = remainingCount - 1
        let maxAssignable = max(1, remainingCents - minimumReserve)
        let average = max(1, remainingCents / remainingCount)
        let upperBound = max(1, min(maxAssignable, average * 2))
        let amountCents = Int.random(in: 1...upperBound)
        return String(format: "%.2f", Double(amountCents) / 100.0)
    }

    func updatedRedPacketDetailAfterReceive(_ detail: String, receiverName: String, amount: String) -> String {
        if redPacketReceivedAmount(for: receiverName, detail: detail) != nil {
            return detail
        }
        guard let progress = redPacketProgress(from: detail) else {
            var lines = detail
                .replacingOccurrences(of: "等待领取", with: "已领取")
                .components(separatedBy: .newlines)
            lines.append("领取明细：")
            lines.append("\(receiverName)：¥\(amount)")
            return lines.joined(separator: "\n")
        }
        let nextReceived = min(progress.received + 1, progress.total)
        var lines = detail.components(separatedBy: .newlines).map { line in
            line.hasPrefix("已领取：") ? "已领取：\(nextReceived)/\(progress.total)" : line
        }
        if let statusIndex = lines.firstIndex(where: { $0.hasPrefix("状态：") }) {
            lines[statusIndex] = nextReceived >= progress.total ? "状态：已领完" : "状态：等待领取"
        }
        if !lines.contains("领取明细：") {
            lines.append("领取明细：")
        }
        lines.append("\(receiverName)：¥\(amount)")
        return lines.joined(separator: "\n")
    }

    func presentGroupInviteAcceptance(for message: ChatMessage) {
        let groupName = groupName(fromInvite: message)
        let alert = UIAlertController(
            title: "群邀请",
            message: "\(message.body)\n\n\(message.detail.isEmpty ? "是否接受邀请加入「\(groupName)」？" : message.detail)",
            preferredStyle: .alert
        )
        alert.addAction(UIAlertAction(title: "取消", style: .cancel))
        alert.addAction(UIAlertAction(title: "接受加入群聊", style: .default) { [weak self] _ in
            self?.acceptGroupInvite(groupName: groupName, inviter: message.sender, inviteMessage: message)
        })
        present(alert, animated: true)
    }

    func groupName(fromInvite message: ChatMessage) -> String {
        if let line = message.detail.components(separatedBy: .newlines).first(where: { $0.hasPrefix("群聊：") }) {
            let name = line.replacingOccurrences(of: "群聊：", with: "").trimmingCharacters(in: .whitespacesAndNewlines)
            if !name.isEmpty { return name }
        }
        let candidates = [message.body, message.detail]
        for candidate in candidates {
            if let range = candidate.range(of: #"(?:加入|邀请加入)([^，。\n]+群)"#, options: .regularExpression) {
                let matched = String(candidate[range])
                    .replacingOccurrences(of: "邀请加入", with: "")
                    .replacingOccurrences(of: "加入", with: "")
                    .trimmingCharacters(in: .whitespacesAndNewlines)
                if !matched.isEmpty { return matched }
            }
        }
        return "周末活动群"
    }

    func acceptGroupInvite(groupName: String, inviter: ChatParticipant, inviteMessage: ChatMessage? = nil) {
        let group = existingOrCreateGroup(named: groupName)
        if let inviteMessage {
            applyGroupInviteMetadata(from: inviteMessage, to: group)
        }
        let selectedAccount = state.currentUsers.first { $0.id == state.selectedAccountID } ?? state.currentUser
        let existingExplicitIDs = explicitGroupMemberIDsByGroupID[group.id] ?? []
        if !existingExplicitIDs.contains(selectedAccount.id) {
            explicitGroupMemberIDsByGroupID[group.id] = existingExplicitIDs + [selectedAccount.id]
            invalidateVisibleDataCaches()
        }
        let alreadyJoined = state.messages.contains {
            $0.conversationID == group.id
                && $0.type == .system
                && $0.body.contains("加入了群聊")
                && $0.body.contains(selectedAccount.displayName)
        }
        if !alreadyJoined {
            let notice = ChatMessage(
                id: UUID(),
                conversationID: group.id,
                type: .system,
                sender: selectedAccount,
                body: "\(selectedAccount.displayName)通过\(inviter.displayName)的邀请加入了群聊",
                detail: "",
                isOutgoing: false,
                presentation: .bare,
                timestamp: currentTimestamp(),
                sentAt: Date(),
                isGroupConversation: true
            )
            state.messages.append(notice)
            persistMessages()
        }

        cancelLeftScrollPreview()
        invalidateVisibleDataCaches()
        leftCollectionView.reloadData()
        updateLeftCollectionBounceInsets()
        rightAccountCollectionView.reloadData()
        rightToolCollectionView.reloadData()
        selectFriend(id: group.id)
        showNotice("已加入\(group.displayName)")
    }

    func applyGroupInviteMetadata(from message: ChatMessage, to group: ChatParticipant) {
        let detailLines = message.detail.components(separatedBy: .newlines)
        let remark = valueAfterPrefix("备注：", in: detailLines)
        let activity = valueAfterPrefix("活动：", in: detailLines)
        let memberNames = valueAfterPrefix("成员：", in: detailLines)
            .components(separatedBy: CharacterSet(charactersIn: "、,， "))
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
        let members = memberNames.compactMap { name in
            state.participants.first { $0.displayName == name }
        }
        let selectedAccount = state.currentUsers.first { $0.id == state.selectedAccountID } ?? state.currentUser
        let allMembers = ([selectedAccount] + members).reduce(into: [ChatParticipant]()) { result, member in
            guard !result.contains(where: { $0.id == member.id }) else { return }
            result.append(member)
        }
        if !allMembers.isEmpty {
            explicitGroupMemberIDsByGroupID[group.id] = allMembers.map(\.id)
            invalidateVisibleDataCaches()
        }

        var settings = groupDisplaySettingsByGroupID[group.id] ?? GroupDisplaySettings()
        if !remark.isEmpty {
            settings.remark = remark
        }
        if !activity.isEmpty {
            settings.remark = settings.remark.isEmpty ? activity : "\(settings.remark) · \(activity)"
        }
        if let currentNickname = groupNickname(for: state.currentUser, in: detailLines), !currentNickname.isEmpty {
            settings.myGroupName = currentNickname
        }
        groupDisplaySettingsByGroupID[group.id] = settings
        persistGroupDisplaySettings()
    }

    func valueAfterPrefix(_ prefix: String, in lines: [String]) -> String {
        lines.first { $0.hasPrefix(prefix) }?
            .replacingOccurrences(of: prefix, with: "")
            .trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
    }

    func groupNickname(for participant: ChatParticipant, in lines: [String]) -> String? {
        let prefix = "\(participant.displayName)："
        return lines.first { $0.hasPrefix(prefix) }?
            .replacingOccurrences(of: prefix, with: "")
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }

    func existingOrCreateGroup(named groupName: String) -> ChatParticipant {
        let normalized = groupName.trimmingCharacters(in: .whitespacesAndNewlines)
        if let group = state.participants.first(where: {
            $0.kind == .group
                && normalizedConversationName($0.displayName) == normalizedConversationName(normalized)
        }) {
            return group
        }

        let group = ChatParticipant(
            id: UUID(),
            displayName: normalized.isEmpty ? "新群聊" : normalized,
            tintColor: UIColor(red: 0.30, green: 0.62, blue: 0.52, alpha: 1),
            initials: String((normalized.isEmpty ? "群" : normalized).prefix(1)),
            isCurrentUser: false,
            kind: .group
        )
        state.participants.append(group)
        return group
    }

    func normalizedConversationName(_ name: String) -> String {
        name.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
    }

    func presentReceiveTransfer(for message: ChatMessage) {
        let isReceived = message.detail.contains("已收款")
        let receiverName = transferReceiverName(from: message)
        let isReceiver = isCurrentAccountPaymentTarget(
            idLinePrefix: "收款方ID：",
            name: receiverName,
            detail: message.detail
        )
        let note = paymentLineValue(prefix: "备注：", in: message.detail)
        let controller = TransferReceiveViewController(
            amountText: transferAmountText(from: message),
            receiverName: receiverName ?? "指定收款人",
            noteText: note == "无" ? nil : note,
            isReceived: isReceived,
            canConfirm: !isReceived && isReceiver
        )
        controller.onConfirm = { [weak self, weak controller] in
            guard let self else { return }
            guard let backendMessageID = self.openApiPaymentMessageServerID(for: message),
                  let context = self.openApiSendContext(for: message, allowsCurrentSelectionFallback: false)
            else {
                self.showNotice("转账缺少真实 msgSvrId 或帐号上下文，未执行领取")
                return
            }
            let account = OpenApiSocialAccount(
                deviceUUID: context.account.clientUuid,
                weChatID: context.account.wxid
            )
            self.showNotice("正在领取转账…")
            Task { @MainActor in
                do {
                    let taskID = try await OpenApiPaymentService().takeTransfer(
                        account: account,
                        messageServerID: backendMessageID
                    )
                    let latestDetail = self.state.messages.first(where: { $0.id == message.id })?.detail
                        ?? message.detail
                    let updatedDetail = latestDetail
                        .replacingOccurrences(of: "待收款", with: "已收款")
                        .replacingOccurrences(of: "状态：已领取", with: "状态：已收款")
                    self.updateMessageDetail(id: message.id, detail: updatedDetail)
                    controller?.dismiss(animated: true)
                    let receiverDisplayName = self.displayNameForAccount(id: self.state.selectedAccountID)
                    self.insertSystemMessage(
                        "\(receiverDisplayName)已收款",
                        conversationID: message.conversationID,
                        forceReload: true
                    )
                    self.showNotice("领取转账已完成\(taskID.isEmpty ? "" : "：\(taskID)")")
                } catch {
                    self.showNotice("领取转账失败：\(error.localizedDescription)")
                }
            }
        }
        present(controller, animated: false)
    }

    func presentPaySplitBill(for message: ChatMessage) {
        let participants = splitBillParticipants(for: message)
        let isPaymentMember = isCurrentAccountInSplitBill(message)
        let hasPaid = currentSplitBillPaidNames(in: message.detail).contains(state.currentUser.displayName)
        let controller = SplitBillDetailViewController(
            amountText: splitBillAmountText(from: message),
            summaryText: message.detail,
            participants: participants,
            currentUserID: state.currentUser.id,
            currentUserHasPaid: hasPaid || !isPaymentMember,
            payButtonTitle: isPaymentMember ? nil : "非收款成员"
        )
        controller.onPay = { [weak self] finish in
            guard let self else { return }
            let paidName = self.state.currentUser.displayName
            guard self.isCurrentAccountInSplitBill(message) else {
                self.showMessageActionNotice("当前帐号不在本次群收款成员中，不能付款")
                finish(false)
                return
            }
            self.presentPaymentConfirmation(
                title: "群收款付款",
                amountText: self.splitBillAmountText(from: message),
                subtitle: "付款给 \(message.sender.displayName)",
                confirmTitle: "确认付款",
                onCancel: {
                    finish(false)
                }
            ) { method, _ in
                self.updateMessageDetail(
                    id: message.id,
                    detail: "\(self.updatedSplitBillDetail(afterPaying: message, paidName: paidName, participantCount: participants.count))\n支付方式：\(method)"
                )
                self.insertSystemMessage(
                    "\(paidName)完成了群收款付款",
                    conversationID: message.conversationID,
                    forceReload: true
                )
                finish(true)
            }
        }
        navigationController?.setNavigationBarHidden(false, animated: false)
        navigationController?.pushViewController(controller, animated: true)
    }

    func presentRelayEditor(for message: ChatMessage) {
        guard let group = state.participants.first(where: { $0.id == message.conversationID }),
              isGroupConversation(group)
        else {
            showMessageActionNotice("接龙只能在群聊中操作")
            return
        }

        let controller = RelayEditorViewController(
            titleText: relayTitle(from: message),
            noteText: relayNote(from: message),
            entries: relayEntries(from: message),
            currentUser: state.currentUser,
            canEditNote: relayCreatorID(from: message) == state.currentUser.id
        )
        controller.onConfirm = { [weak self] note, entries in
            self?.sendRelayMessage(
                title: self?.relayTitle(from: message) ?? message.body,
                note: note,
                entries: entries,
                conversationID: message.conversationID,
                creatorID: self?.relayCreatorID(from: message) ?? message.sender.id
            )
        }
        navigationController?.setNavigationBarHidden(false, animated: false)
        navigationController?.pushViewController(controller, animated: true)
    }

    func presentNewRelayComposer() {
        guard let group = state.activeFriend, isGroupConversation(group) else {
            showMessageActionNotice("群接龙只能在群聊中发起")
            return
        }
        let controller = RelayEditorViewController(
            titleText: "接龙",
            noteText: "",
            entries: [],
            currentUser: state.currentUser,
            canEditNote: true
        )
        controller.onConfirm = { [weak self] note, entries in
            self?.sendRelayMessage(
                title: "接龙",
                note: note,
                entries: entries,
                conversationID: group.id,
                creatorID: self?.state.currentUser.id ?? UUID()
            )
        }
        navigationController?.setNavigationBarHidden(false, animated: false)
        navigationController?.pushViewController(controller, animated: true)
    }

    func sendRelayMessage(title: String, note: String, entries: [RelayEntry], conversationID: UUID, creatorID: UUID) {
        guard !entries.isEmpty else {
            showNotice("请至少保留 1 条接龙信息")
            return
        }
        let conversation = state.participants.first { $0.id == conversationID }
        let sender = outgoingSenderForSelectedAccount(in: conversation)
        let orderedEntries = orderedRelayEntries(entries)
        let detail = relayDetail(note: note, entries: orderedEntries, creatorID: creatorID)
        let message = ChatMessage(
            id: UUID(),
            conversationID: conversationID,
            type: .relay,
            sender: sender,
            body: title.isEmpty ? "接龙" : title,
            detail: detail,
            isOutgoing: true,
            presentation: .avatarAndName,
            timestamp: currentTimestamp(),
            sentAt: Date(),
            isGroupConversation: true,
            recipientAccountID: sender.id
        )
        state.messages.append(message)
        persistMessages()
        navigationController?.popViewController(animated: true)
        selectFriend(id: conversationID, focusMessageID: message.id)
        showNotice("接龙已发送")
    }

    func relayTitle(from message: ChatMessage) -> String {
        message.body.isEmpty ? "接龙" : message.body
    }

    func relayNote(from message: ChatMessage) -> String {
        message.detail
            .components(separatedBy: .newlines)
            .first { $0.hasPrefix("备注：") }
            .map { String($0.dropFirst("备注：".count)).trimmingCharacters(in: .whitespacesAndNewlines) } ?? ""
    }

    func relayCreatorID(from message: ChatMessage) -> UUID {
        let lines = message.detail
            .components(separatedBy: .newlines)
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
        if let rawID = lines
            .first(where: { $0.hasPrefix("发起人ID：") })
            .map({ String($0.dropFirst("发起人ID：".count)).trimmingCharacters(in: .whitespacesAndNewlines) }),
           let id = UUID(uuidString: rawID) {
            return id
        }
        return message.sender.id
    }

    func relayEntries(from message: ChatMessage) -> [RelayEntry] {
        let lines = message.detail
            .components(separatedBy: .newlines)
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty && !isRelayMetaLine($0) }
        let parsed = lines.enumerated().compactMap { index, line -> RelayEntry? in
            guard let range = line.range(of: #"^\d+[\.\、]\s*"#, options: .regularExpression) else { return nil }
            let content = String(line[range.upperBound...]).trimmingCharacters(in: .whitespacesAndNewlines)
            let parts = content.split(separator: " ", maxSplits: 1).map(String.init)
            guard let name = parts.first, !name.isEmpty else { return nil }
            let text = parts.dropFirst().first ?? ""
            let participant = state.participants.first { $0.displayName == name }
            return RelayEntry(
                id: participant?.id ?? UUID(),
                name: name,
                text: text,
                submittedAt: Date(timeIntervalSince1970: Double(index)),
                tieBreaker: Double(index) / 1000
            )
        }
        if !parsed.isEmpty { return orderedRelayEntries(parsed) }
        return []
    }

    func relayDetail(note: String, entries: [RelayEntry], creatorID: UUID) -> String {
        let entryLines = orderedRelayEntries(entries)
            .enumerated()
            .map { index, entry in
                let text = entry.text.trimmingCharacters(in: .whitespacesAndNewlines)
                return "\(index + 1). \(entry.name)\(text.isEmpty ? "" : " \(text)")"
            }
        let trimmedNote = note.trimmingCharacters(in: .whitespacesAndNewlines)
        return (["发起人ID：\(creatorID.uuidString)"] + [trimmedNote.isEmpty ? nil : "备注：\(trimmedNote)"] + entryLines.map(Optional.some))
            .compactMap { $0 }
            .joined(separator: "\n")
    }

    func isRelayMetaLine(_ line: String) -> Bool {
        line.hasPrefix("备注：") || line.hasPrefix("发起人ID：")
    }

    func orderedRelayEntries(_ entries: [RelayEntry]) -> [RelayEntry] {
        entries.sorted {
            let delta = $0.submittedAt.timeIntervalSince($1.submittedAt)
            if abs(delta) < 0.001 {
                return $0.tieBreaker < $1.tieBreaker
            }
            return $0.submittedAt < $1.submittedAt
        }
    }

    func splitBillParticipants(for message: ChatMessage) -> [ChatParticipant] {
        if let namedParticipants = splitBillNamedParticipants(from: message.detail), !namedParticipants.isEmpty {
            return namedParticipants
        }
        let group = state.participants.first { $0.id == message.conversationID }
        var participants = groupCallParticipants(for: group)
        participants.insert(state.currentUser, at: 0)
        return participants.reduce(into: [ChatParticipant]()) { result, participant in
            guard !result.contains(where: { $0.id == participant.id }) else { return }
            result.append(participant)
        }
    }

    func transferReceiverName(from message: ChatMessage) -> String? {
        paymentLineValue(prefix: "收款方：", in: message.detail)
            ?? message.body
                .replacingOccurrences(of: "转账给", with: "")
                .components(separatedBy: " ¥")
                .first?
                .trimmingCharacters(in: .whitespacesAndNewlines)
    }

    func transferAmountText(from message: ChatMessage) -> String {
        splitBillAmountText(from: message)
    }

    func isCurrentAccountInSplitBill(_ message: ChatMessage) -> Bool {
        if currentPaymentTargetIDs(prefix: "收款成员ID：", in: message.detail).contains(state.selectedAccountID) {
            return true
        }
        let names = splitBillNamedParticipants(from: message.detail)?.map(\.displayName)
            ?? paymentNames(prefix: "收款成员：", in: message.detail)
            ?? paymentNames(prefix: "未支付：", in: message.detail)
            ?? []
        guard !names.isEmpty else { return true }
        return names.contains { isCurrentAccountNamed($0) }
    }

    func isCurrentAccountPaymentTarget(idLinePrefix: String, name: String?, detail: String) -> Bool {
        let ids = currentPaymentTargetIDs(prefix: idLinePrefix, in: detail)
        if !ids.isEmpty {
            return ids.contains(state.selectedAccountID)
        }
        guard let name, !name.isEmpty else { return false }
        return isCurrentAccountNamed(name)
    }

    func currentPaymentTargetIDs(prefix: String, in detail: String) -> Set<UUID> {
        guard let raw = paymentLineValue(prefix: prefix, in: detail) else { return [] }
        let ids = raw
            .components(separatedBy: CharacterSet(charactersIn: "、,， "))
            .compactMap { UUID(uuidString: $0.trimmingCharacters(in: .whitespacesAndNewlines)) }
        return Set(ids)
    }

    func paymentNames(prefix: String, in detail: String) -> [String]? {
        guard let raw = paymentLineValue(prefix: prefix, in: detail) else { return nil }
        let names = raw
            .components(separatedBy: CharacterSet(charactersIn: "、,， "))
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty && $0 != "无" && !$0.contains("人") }
        return names.isEmpty ? nil : names
    }

    func paymentLineValue(prefix: String, in detail: String) -> String? {
        detail
            .components(separatedBy: .newlines)
            .first { $0.hasPrefix(prefix) }
            .map { String($0.dropFirst(prefix.count)).trimmingCharacters(in: .whitespacesAndNewlines) }
    }

    func isCurrentAccountNamed(_ name: String) -> Bool {
        let normalizedName = normalizePaymentName(name)
        let currentNames = [
            state.currentUser.displayName,
            displayNameForAccount(id: state.selectedAccountID),
            state.contactCards[state.selectedAccountID]?.displayName
        ]
        return currentNames.compactMap { $0 }.contains {
            normalizePaymentName($0) == normalizedName
        }
    }

    func normalizePaymentName(_ name: String) -> String {
        name.trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: " ", with: "")
            .lowercased()
    }

    func splitBillNamedParticipants(from detail: String) -> [ChatParticipant]? {
        guard let line = detail
            .components(separatedBy: .newlines)
            .first(where: { $0.hasPrefix("收款成员：") || $0.hasPrefix("未支付：") })
        else { return nil }
        let namesText = line
            .replacingOccurrences(of: "收款成员：", with: "")
            .replacingOccurrences(of: "未支付：", with: "")
        let names = namesText
            .components(separatedBy: CharacterSet(charactersIn: "、,， "))
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty && !$0.contains("人") }
        let participants = names.compactMap { name in
            state.participants.first { $0.displayName == name }
        }
        return participants.isEmpty ? nil : participants
    }

    func splitBillAmountText(from message: ChatMessage) -> String {
        if let range = message.detail.range(of: #"¥\d+(?:\.\d+)?"#, options: .regularExpression) {
            return String(message.detail[range])
        }
        if let range = message.body.range(of: #"¥\d+(?:\.\d+)?"#, options: .regularExpression) {
            return String(message.body[range])
        }
        return "¥320.00"
    }

    func updatedSplitBillDetail(afterPaying message: ChatMessage, paidName: String, participantCount: Int) -> String {
        let total = max(participantCount, 1)
        let paidCount = min(total, max(1, currentSplitBillPaidCount(in: message.detail) + 1))
        let amount = splitBillAmountText(from: message)
        let allNames = splitBillNamedParticipants(from: message.detail)?.map(\.displayName) ?? []
        let paidNames = currentSplitBillPaidNames(in: message.detail)
            .union([paidName])
        let unpaidNames = allNames.filter { !paidNames.contains($0) }
        if allNames.isEmpty {
            return "群收款总额：\(amount)\n已收 \(paidCount)/\(total) 人\n已支付：\(paidName)\n未支付：\(max(total - paidCount, 0)) 人"
        }
        return "群收款总额：\(amount)\n已收 \(paidCount)/\(total) 人\n收款成员：\(allNames.joined(separator: "、"))\n已支付：\(Array(paidNames).joined(separator: "、"))\n未支付：\(unpaidNames.isEmpty ? "无" : unpaidNames.joined(separator: "、"))"
    }

    func currentSplitBillPaidCount(in detail: String) -> Int {
        guard let range = detail.range(of: #"已收\s+\d+/"#, options: .regularExpression) else { return 0 }
        let matched = String(detail[range])
        let numberText = matched
            .replacingOccurrences(of: "已收", with: "")
            .replacingOccurrences(of: "/", with: "")
            .trimmingCharacters(in: .whitespacesAndNewlines)
        return Int(numberText) ?? 0
    }

    func currentSplitBillPaidNames(in detail: String) -> Set<String> {
        guard let line = detail.components(separatedBy: .newlines).first(where: { $0.hasPrefix("已支付：") }) else {
            return []
        }
        let names = line
            .replacingOccurrences(of: "已支付：", with: "")
            .components(separatedBy: CharacterSet(charactersIn: "、,， "))
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty && $0 != "无" }
        return Set(names)
    }

    func presentMergedForwardDetail(for message: ChatMessage) {
        let controller = MergedForwardDetailViewController(
            title: message.body.isEmpty ? "聊天记录" : message.body,
            sourceMessage: message,
            entries: makeMergedForwardEntries(for: message)
        )
        controller.onSelectEntry = { [weak self] entry, urlText in
            self?.handleMergedForwardEntryTap(entry, selectedURLText: urlText)
        }
        controller.onPreviewMediaEntry = { [weak self] entry in
            self?.handleMergedForwardMediaPreview(entry)
        }
        navigationController?.setNavigationBarHidden(false, animated: true)
        navigationController?.pushViewController(controller, animated: true)
    }

    func handleMergedForwardMediaPreview(_ entry: MergedForwardEntry) {
        guard let sourceMessage = entry.sourceMessage else {
            showNotice(entry.kind == .video ? "视频预览" : "图片预览")
            return
        }
        if previewAttachment(for: sourceMessage) {
            return
        }
        switch sourceMessage.type {
        case .video:
            presentMockMediaPreview(for: sourceMessage, symbolName: "play.rectangle.fill", title: "视频预览")
        case .channelsVideo:
            presentChannelsVideoDetail(for: sourceMessage)
        default:
            presentMockMediaPreview(for: sourceMessage, symbolName: "photo", title: "图片预览")
        }
    }

    func handleMergedForwardEntryTap(_ entry: MergedForwardEntry, selectedURLText: String? = nil) {
        if let urlText = selectedURLText {
            presentLinkJumpConfirmation(urlText: urlText)
            return
        }

        guard let sourceMessage = entry.sourceMessage else {
            showNotice("暂无可打开的原消息内容")
            return
        }

        if previewAttachment(for: sourceMessage) {
            return
        }

        switch sourceMessage.type {
        case .webLink:
            presentWebLinkPreview(for: sourceMessage)
        case .miniProgram:
            openMiniProgramCard(sourceMessage)
        case .article:
            presentArticleDetail(for: sourceMessage)
        case .channelsLive:
            presentChannelsLiveDetail(for: sourceMessage)
        case .music:
            presentMusicPlayer(for: sourceMessage)
        case .favorite:
            presentFavoriteDetail(for: sourceMessage)
        case .coupon:
            presentCouponDetail(for: sourceMessage)
        case .mergedForward:
            presentMergedForwardDetail(for: sourceMessage)
        case .image, .capturedPhoto:
            presentMockMediaPreview(for: sourceMessage, symbolName: "photo", title: "图片预览")
        case .video:
            presentMockMediaPreview(for: sourceMessage, symbolName: "play.rectangle.fill", title: "视频预览")
        case .channelsVideo:
            presentChannelsVideoDetail(for: sourceMessage)
        case .file:
            presentMockFilePreview(for: sourceMessage)
        default:
            showNotice(sourceMessage.type.title)
        }
    }

    func makeMergedForwardEntries(for message: ChatMessage) -> [MergedForwardEntry] {
        if !message.mergedForwardMessages.isEmpty {
            return message.mergedForwardMessages.map(mergedForwardEntry)
        }

        let sourceMessages = decodeMergedForwardMessages(from: message.detail)
        if !sourceMessages.isEmpty {
            return sourceMessages.map(mergedForwardEntry)
        }

        let senderName = message.sender.displayName
        let dateText = "2026年6月24日 14:16"
        return [
            MergedForwardEntry(
                senderName: senderName,
                senderInitials: message.sender.initials,
                senderColor: message.sender.tintColor,
                timestamp: dateText,
                kind: .text,
                title: "https://github.com/lemon-zzn/zhifaAndroid.git",
                detail: "android 规范:UI 使用 compose 代码用 kotlin 用无障碍画悬浮窗的也 compose ui\nios UIKit + UICollectionView",
                image: nil,
                sourceMessage: nil
            ),
            MergedForwardEntry(
                senderName: senderName,
                senderInitials: message.sender.initials,
                senderColor: message.sender.tintColor,
                timestamp: dateText,
                kind: .file,
                title: "perfect-coordinate-system(1).html",
                detail: "213.1K · 微信电脑版",
                image: nil,
                sourceMessage: nil
            ),
            MergedForwardEntry(
                senderName: senderName,
                senderInitials: message.sender.initials,
                senderColor: message.sender.tintColor,
                timestamp: dateText,
                kind: .image,
                title: "聊天界面截图",
                detail: "图片消息",
                image: makeMergedForwardPreviewImage(),
                sourceMessage: nil
            ),
            MergedForwardEntry(
                senderName: senderName,
                senderInitials: message.sender.initials,
                senderColor: message.sender.tintColor,
                timestamp: "2026年6月24日 14:18",
                kind: .text,
                title: "https://docs.google.com/document/d/1m9SHhJh1cgZASG07zw8qzjUqCHzXPgND/edit?usp=sharing",
                detail: "这是整理后的需求文档链接，后续直接按这里更新。",
                image: nil,
                sourceMessage: nil
            )
        ]
    }

    func mergedForwardEntry(from message: ChatMessage) -> MergedForwardEntry {
        let sourceMessage = hydratedMergedForwardSource(message)
        let kind: MergedForwardEntry.Kind
        let image: UIImage?
        switch sourceMessage.type {
        case .image, .capturedPhoto, .stickerGif:
            kind = .image
            image = previewImageForMergedForward(sourceMessage)
        case .video, .channelsVideo:
            kind = .video
            image = previewImageForMergedForward(sourceMessage) ?? makeMergedForwardPreviewImage()
        case .file:
            kind = .file
            image = nil
        default:
            kind = .text
            image = nil
        }

        return MergedForwardEntry(
            senderName: sourceMessage.sender.displayName,
            senderInitials: sourceMessage.sender.initials,
            senderColor: sourceMessage.sender.tintColor,
            timestamp: sourceMessage.displayTimestamp,
            kind: kind,
            title: mergedForwardTitle(for: sourceMessage),
            detail: mergedForwardDetailText(for: sourceMessage),
            image: image,
            sourceMessage: sourceMessage
        )
    }

    func hydratedMergedForwardSource(_ message: ChatMessage) -> ChatMessage {
        guard message.attachmentURL == nil else { return message }
        let attachmentURL: URL?
        switch message.type {
        case .image, .capturedPhoto:
            attachmentURL = makeDemoImageFile(for: message)
        case .stickerGif:
            attachmentURL = makeDemoGIFFile(for: message)
        case .video, .channelsVideo:
            attachmentURL = makeDemoVideoFile()
        case .voice:
            attachmentURL = makeDemoVoiceAudio(for: message)
        case .music:
            attachmentURL = makeDemoMusicAudio(for: message)
        case .file:
            attachmentURL = makeDemoPDFDocument()
        default:
            attachmentURL = nil
        }
        guard let attachmentURL else { return message }
        return replacingMessage(message, attachmentURL: attachmentURL)
    }

    func previewImageForMergedForward(_ message: ChatMessage) -> UIImage? {
        guard let url = message.attachmentURL else { return nil }
        switch message.type {
        case .image, .capturedPhoto, .stickerGif:
            return animatedImageIfNeeded(from: url) ?? UIImage(contentsOfFile: url.path)
        case .video, .channelsVideo:
            let asset = AVAsset(url: url)
            let generator = AVAssetImageGenerator(asset: asset)
            generator.appliesPreferredTrackTransform = true
            if let cgImage = try? generator.copyCGImage(at: .zero, actualTime: nil) {
                return UIImage(cgImage: cgImage)
            }
            return nil
        default:
            return nil
        }
    }

    func mergedForwardTitle(for message: ChatMessage) -> String {
        if !message.body.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            return message.body
        }
        return message.type.title
    }

    func mergedForwardDetailText(for message: ChatMessage) -> String {
        let detail = message.detail.trimmingCharacters(in: .whitespacesAndNewlines)
        if !detail.isEmpty { return detail }
        switch message.type {
        case .image, .capturedPhoto:
            return "图片消息"
        case .stickerGif:
            return "动态表情"
        case .video, .channelsVideo:
            return "视频消息"
        case .file:
            return message.attachmentURL?.lastPathComponent ?? "文件消息"
        case .voice:
            return "语音消息"
        case .location, .liveLocation:
            return "位置消息"
        case .redPacket, .transfer, .splitBill:
            return "资金类消息"
        default:
            return message.type.title
        }
    }

    func decodeMergedForwardMessages(from detail: String) -> [ChatMessage] {
        []
    }

    func makeMergedForwardPreviewImage() -> UIImage? {
        let size = CGSize(width: 220, height: 320)
        let renderer = UIGraphicsImageRenderer(size: size)
        return renderer.image { context in
            let rect = CGRect(origin: .zero, size: size)
            UIColor(red: 0.85, green: 0.91, blue: 0.92, alpha: 1).setFill()
            context.fill(rect)
            UIColor.white.withAlphaComponent(0.55).setFill()
            UIBezierPath(roundedRect: CGRect(x: 12, y: 18, width: 196, height: 284), cornerRadius: 12).fill()
            for index in 0..<8 {
                let y = 34 + index * 32
                let isRight = index % 2 == 1
                let bubble = CGRect(x: isRight ? 92 : 36, y: y, width: isRight ? 88 : 118, height: 22)
                UIColor.white.withAlphaComponent(isRight ? 0.92 : 0.70).setFill()
                UIBezierPath(roundedRect: bubble, cornerRadius: 8).fill()
                UIColor(red: 0.30, green: 0.50, blue: 0.55, alpha: 0.32).setFill()
                UIBezierPath(roundedRect: CGRect(x: bubble.minX + 10, y: bubble.midY - 2, width: bubble.width - 20, height: 4), cornerRadius: 2).fill()
            }
            UIColor.white.withAlphaComponent(0.88).setFill()
            UIBezierPath(roundedRect: CGRect(x: 172, y: 42, width: 28, height: 220), cornerRadius: 8).fill()
        }
    }

    @discardableResult
    func openDetectedLink(in text: String) -> Bool {
        guard let url = firstDetectedURL(in: text) else { return false }
        presentLinkJumpConfirmation(urlText: url.absoluteString)
        return true
    }

    func presentLinkJumpConfirmation(urlText: String) {
        guard let url = normalizedURL(from: urlText) else { return }
        let accessState = linkAccessState(for: url)
        guard accessState == .viewable || accessState == .approved else {
            presentLinkAccessRequestPrompt(url: url, state: accessState)
            return
        }
        let alert = UIAlertController(
            title: "即将跳转",
            message: url.absoluteString,
            preferredStyle: .alert
        )
        alert.addAction(UIAlertAction(title: "取消", style: .cancel))
        alert.addAction(UIAlertAction(title: "跳转", style: .default) { _ in
            UIApplication.shared.open(url)
        })
        present(alert, animated: true)
    }

    func openWebLinkMessageWithAccessCheck(_ message: ChatMessage) {
        let combined = [message.body, message.detail].joined(separator: " ")
        guard let url = firstDetectedURL(in: combined) else {
            presentWebLinkPreview(for: message)
            return
        }
        restrictedSentLinkURLs.insert(url.absoluteString)
        saveRestrictedSentLinks()
        presentLinkJumpConfirmation(urlText: url.absoluteString)
    }

    func presentLinkAccessRequestPrompt(url: URL, state: MediaAccessState) {
        let message: String
        switch state {
        case .requested:
            message = "该链接暂不能直接访问，访问申请已提交。\n\n\(url.absoluteString)"
        default:
            message = "该链接暂不能直接访问，需要发布者同意后才能访问。\n\n\(url.absoluteString)"
        }
        let alert = UIAlertController(title: "申请访问链接", message: message, preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "取消", style: .cancel))
        if state == .requested {
            alert.addAction(UIAlertAction(title: "去审核查看", style: .default) { [weak self] _ in
                self?.presentAccessRequestReviewPage()
            })
        } else {
            alert.addAction(UIAlertAction(title: "申请访问", style: .default) { [weak self] _ in
                self?.submitLinkAccessRequest(title: url.host ?? "链接访问", urlText: url.absoluteString)
            })
            alert.addAction(UIAlertAction(title: "模拟同意并访问", style: .default) { [weak self] _ in
                guard let self else { return }
                self.submitLinkAccessRequest(title: url.host ?? "链接访问", urlText: url.absoluteString)
                self.approveMediaAccessRequest(key: url.absoluteString)
                UIApplication.shared.open(url)
            })
        }
        present(alert, animated: true)
    }

    func linkAccessState(for url: URL) -> MediaAccessState {
        let key = url.absoluteString
        let requiresApproval = linkRequiresApproval || restrictedSentLinkURLs.contains(key)
        if !requiresApproval && canDirectlyAccessLink(url) {
            return .viewable
        }
        guard let request = mediaAccessRequests.first(where: { $0.key == key }) else {
            return .requestable
        }
        return request.status == .approved ? .approved : .requested
    }

    func canDirectlyAccessLink(_ url: URL) -> Bool {
        guard let host = url.host?.lowercased() else { return false }
        if host.contains("private") || host.contains("internal") || host.contains("file.ai") || host.contains("img.ai") {
            return false
        }
        return ["cc2.cx", "baidu.com", "qq.com", "weixin.qq.com", "apple.com", "douyin.com"].contains { host == $0 || host.hasSuffix(".\($0)") }
    }

    func presentWebLinkPreview(for message: ChatMessage) {
        let url = firstDetectedURL(in: [message.body, message.detail].joined(separator: " "))
            ?? URL(string: "https://cc2.cx")!
        let controller = RichContentDetailViewController(
            mode: .webLink(url: url),
            titleText: message.body.isEmpty ? "网页链接" : message.body,
            subtitleText: url.absoluteString,
            detailText: message.detail
        )
        controller.onPrimaryAction = { [weak self] in
            self?.presentLinkJumpConfirmation(urlText: url.absoluteString)
        }
        pushDetailController(controller)
    }

    func presentArticleDetail(for message: ChatMessage) {
        let url = firstDetectedURL(in: [message.body, message.detail].joined(separator: " "))
        let controller = RichContentDetailViewController(
            mode: .article,
            titleText: message.body.isEmpty ? "公众号文章" : message.body,
            subtitleText: "公众号 · 城市活动观察",
            detailText: message.detail.isEmpty
                ? "这是一篇图文文章详情页，包含封面、摘要和正文内容。"
                : message.detail
        )
        if let url {
            controller.onPrimaryAction = { [weak self] in
                self?.openFirstLaunchCandidate(
                    [ExternalLaunchCandidate(appName: "微信", url: url, requiresInstalledApp: false)],
                    fallbackAppName: "微信"
                )
            }
        }
        pushDetailController(controller)
    }

    func presentChannelsLiveDetail(for message: ChatMessage) {
        let controller = RichContentDetailViewController(
            mode: .live,
            titleText: message.body.isEmpty ? "视频号直播" : message.body,
            subtitleText: "直播中 · 3,286 人看过",
            detailText: message.detail.isEmpty ? "点击预约或进入直播间查看互动状态。" : message.detail
        )
        controller.onPrimaryAction = { [weak self] in
            self?.showNotice("已进入直播间")
        }
        pushDetailController(controller)
    }

    func presentChannelsVideoDetail(for message: ChatMessage) {
        let controller = ChannelsVideoDetailViewController(
            titleText: message.body.isEmpty ? "视频号视频" : message.body,
            detailText: message.detail,
            videoURL: message.attachmentURL ?? makeDemoVideoFile()
        )
        controller.onShowNotice = { [weak self] notice in
            self?.showNotice(notice)
        }
        controller.onForward = { [weak self, weak controller] payload in
            guard let self else { return }
            controller?.navigationController?.popViewController(animated: false)
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.12) {
                self.presentChannelsVideoForwardPicker(payload: payload)
            }
        }
        navigationController?.setNavigationBarHidden(false, animated: true)
        navigationController?.pushViewController(controller, animated: true)
    }

    func presentMusicPlayer(for message: ChatMessage) {
        let controller = MusicPlayerViewController(
            titleText: musicTitle(from: message),
            artistText: musicArtist(from: message),
            audioURL: message.attachmentURL
        )
        navigationController?.setNavigationBarHidden(false, animated: true)
        navigationController?.pushViewController(controller, animated: true)
    }

    func presentFavoriteDetail(for message: ChatMessage) {
        let controller = FavoriteDetailViewController(
            titleText: message.body.isEmpty ? "收藏内容" : message.body,
            detailText: message.detail.isEmpty ? "收藏内容详情" : message.detail
        )
        controller.onOpenLink = { [weak self] urlText in
            self?.presentLinkJumpConfirmation(urlText: urlText)
        }
        pushDetailController(controller)
    }

    func presentCouponDetail(for message: ChatMessage) {
        let controller = CouponDetailViewController(
            titleText: message.body.isEmpty ? "微信卡券" : message.body,
            detailText: message.detail.isEmpty ? "到店核销可用" : message.detail
        )
        controller.onReceive = { [weak self] in
            self?.updateMessageDetail(
                id: message.id,
                detail: message.detail.contains("已领取")
                    ? message.detail
                    : "\(message.detail)\n状态：已领取，已加入卡包"
            )
            self?.showNotice("已加入卡包")
        }
        pushDetailController(controller)
    }

    func presentCouponWalletPage() {
        let controller = CouponWalletViewController(items: CouponWalletItem.demoItems)
        controller.onSelectItem = { [weak self] item in
            self?.presentCouponWalletItemDetail(item)
        }
        navigationController?.setNavigationBarHidden(false, animated: true)
        navigationController?.pushViewController(controller, animated: true)
    }

    func presentCouponWalletItemDetail(_ item: CouponWalletItem) {
        let controller = CouponDetailViewController(
            titleText: item.title,
            detailText: item.messageDetail,
            actionTitle: "已在卡包"
        )
        controller.onReceive = { [weak self] in
            self?.showNotice("卡券已在卡包中")
        }
        navigationController?.pushViewController(controller, animated: true)
    }

    func presentLiveLocationViewer(for message: ChatMessage) {
        let participants = groupCallParticipants(for: state.participants.first { $0.id == message.conversationID })
        let controller = LiveLocationViewController(
            titleText: message.body.isEmpty ? "实时位置共享" : message.body,
            detailText: message.detail.isEmpty ? "正在共享位置" : message.detail,
            participants: participants.isEmpty ? [message.sender, state.currentUser] : participants
        )
        navigationController?.setNavigationBarHidden(false, animated: true)
        navigationController?.pushViewController(controller, animated: true)
    }

    func pushDetailController(_ controller: UIViewController) {
        navigationController?.setNavigationBarHidden(false, animated: true)
        navigationController?.pushViewController(controller, animated: true)
    }

    func presentUIOperationLabPage() {
        let controller = UIOperationLabViewController()
        pushDetailController(controller)
    }

    func musicTitle(from message: ChatMessage) -> String {
        message.body
            .replacingOccurrences(of: "音乐分享：", with: "")
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .isEmpty ? "路上听" : message.body.replacingOccurrences(of: "音乐分享：", with: "")
    }

    func musicArtist(from message: ChatMessage) -> String {
        let detail = message.detail.trimmingCharacters(in: .whitespacesAndNewlines)
        return detail.isEmpty ? "城市电台" : detail.components(separatedBy: .newlines).first ?? "城市电台"
    }

    func openMiniProgramCard(_ message: ChatMessage) {
        let candidates = miniProgramLaunchCandidates(for: message)
        guard !candidates.isEmpty else {
            showMessageActionNotice("没有可打开的小程序链接")
            return
        }
        openFirstLaunchCandidate(candidates, fallbackAppName: candidates.first?.appName ?? "对应 App")
    }

    func presentMiniProgramPreview(for message: ChatMessage) {
        let controller = RichContentDetailViewController(
            mode: .miniProgram,
            titleText: message.body.isEmpty ? "小程序页面" : message.body,
            subtitleText: "小程序 · 门店导航",
            detailText: message.detail.isEmpty ? "这是 App 内的小程序页面预览，可继续跳转微信打开。" : message.detail
        )
        controller.onPrimaryAction = { [weak self] in
            self?.openMiniProgramCard(message)
        }
        pushDetailController(controller)
    }

    func miniProgramURL(for message: ChatMessage) -> URL {
        miniProgramLaunchCandidates(for: message).first?.url ?? URL(string: "https://cc2.cx")!
    }

    func miniProgramLaunchCandidates(for message: ChatMessage) -> [ExternalLaunchCandidate] {
        let combined = [message.body, message.detail].joined(separator: "\n")
        let lower = combined.lowercased()
        let hasWeAppFields = openApiDetailValue(in: message, keys: ["appId", "appid", "小程序AppId"]) != nil
            || openApiDetailValue(in: message, keys: ["pagePath", "页面路径", "页面"]) != nil
            || openApiDetailValue(in: message, keys: ["sourceUsername", "原始ID", "gh"]) != nil
        var candidates: [ExternalLaunchCandidate] = []
        if let shortcut = firstMiniProgramShortcutText(in: combined),
           let wechatURL = URL(string: "weixin://") {
            candidates.append(ExternalLaunchCandidate(
                appName: "微信",
                url: wechatURL,
                requiresInstalledApp: true,
                pasteboardText: shortcut
            ))
        }
        for urlText in miniProgramLaunchURLTexts(from: message) {
            guard let url = normalizedURL(from: urlText) else { continue }
            appendLaunchCandidates(for: url, sourceText: combined, to: &candidates)
        }
        if hasWeAppFields {
            candidates.removeAll { candidate in
                guard !candidate.requiresInstalledApp else { return false }
                let host = candidate.url.host?.lowercased() ?? ""
                return host.contains("weixin") || host.contains("qq.com")
            }
            if !candidates.contains(where: { $0.appName.contains("微信") && $0.pasteboardText?.isEmpty == false }),
               let wechatURL = URL(string: "weixin://") {
                candidates.insert(ExternalLaunchCandidate(appName: "微信", url: wechatURL, requiresInstalledApp: true), at: 0)
            }
        } else if candidates.isEmpty || lower.contains("微信") || lower.contains("小程序") {
            if let wechatURL = URL(string: "weixin://") {
                candidates.append(ExternalLaunchCandidate(appName: "微信", url: wechatURL, requiresInstalledApp: true))
            }
        }
        return uniqueLaunchCandidates(candidates)
    }

    func miniProgramLaunchURLTexts(from message: ChatMessage) -> [String] {
        var values: [String] = []
        let keys = ["openUrl", "scheme", "url", "链接", "小程序页面链接", "小程序链接", "网页链接"]
        for key in keys {
            if let value = openApiDetailValue(in: message, keys: [key]) {
                values.append(value)
            }
        }
        let combined = [message.body, message.detail].joined(separator: "\n")
        if let detected = firstDetectedURLText(in: combined) {
            values.append(detected)
        }
        return values
    }

    func appendLaunchCandidates(
        for url: URL,
        sourceText: String,
        to candidates: inout [ExternalLaunchCandidate]
    ) {
        let scheme = url.scheme?.lowercased() ?? ""
        let host = url.host?.lowercased() ?? ""
        let lower = sourceText.lowercased()

        if scheme == "weixin" || scheme == "wechat" {
            candidates.append(ExternalLaunchCandidate(appName: "微信", url: url, requiresInstalledApp: true))
            return
        }
        if scheme == "snssdk1128" || scheme == "douyin" || scheme == "iesdouyin" {
            candidates.append(ExternalLaunchCandidate(appName: "抖音", url: url, requiresInstalledApp: true))
            return
        }
        if scheme == "http" || scheme == "https" {
            if (host.contains("weixin") || host.contains("qq.com")) && (lower.contains("小程序") || lower.contains("weapp") || lower.contains("mini")) {
                if let wechatURL = URL(string: "weixin://") {
                    candidates.append(ExternalLaunchCandidate(appName: "微信", url: wechatURL, requiresInstalledApp: true))
                }
                return
            }
            if host.contains("douyin.com") || lower.contains("抖音") {
                if let encoded = url.absoluteString.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed),
                   let douyinURL = URL(string: "snssdk1128://microapp?url=\(encoded)") {
                    candidates.append(ExternalLaunchCandidate(appName: "抖音", url: douyinURL, requiresInstalledApp: true))
                }
            }
            candidates.append(ExternalLaunchCandidate(appName: host.contains("weixin") || host.contains("qq.com") ? "微信" : "浏览器", url: url, requiresInstalledApp: false))
            return
        }
        candidates.append(ExternalLaunchCandidate(appName: "对应 App", url: url, requiresInstalledApp: true))
    }

    func uniqueLaunchCandidates(_ candidates: [ExternalLaunchCandidate]) -> [ExternalLaunchCandidate] {
        var seen = Set<String>()
        return candidates.filter { candidate in
            let key = "\(candidate.url.absoluteString)|\(candidate.pasteboardText ?? "")"
            guard !seen.contains(key) else { return false }
            seen.insert(key)
            return true
        }
    }

    func openFirstLaunchCandidate(_ candidates: [ExternalLaunchCandidate], fallbackAppName: String) {
        var remaining = candidates
        while !remaining.isEmpty {
            let candidate = remaining.removeFirst()
            if candidate.requiresInstalledApp && !UIApplication.shared.canOpenURL(candidate.url) {
                continue
            }
            if let pasteboardText = candidate.pasteboardText, !pasteboardText.isEmpty {
                UIPasteboard.general.string = pasteboardText
            }
            UIApplication.shared.open(candidate.url) { [weak self] success in
                DispatchQueue.main.async {
                    guard let self else { return }
                    if success {
                        if candidate.pasteboardText?.isEmpty == false {
                            self.showMessageActionNotice("已复制小程序链接并打开\(candidate.appName)")
                        } else {
                            self.showMessageActionNotice("已打开\(candidate.appName)")
                        }
                    } else if !remaining.isEmpty {
                        self.openFirstLaunchCandidate(remaining, fallbackAppName: fallbackAppName)
                    } else {
                        self.presentInstallPromptIfNeeded(appName: candidate.appName)
                    }
                }
            }
            return
        }
        presentInstallPromptIfNeeded(appName: fallbackAppName)
    }

    func presentInstallPromptIfNeeded(appName: String) {
        if appName.contains("微信") {
            presentWechatInstallPrompt()
        } else if appName.contains("抖音") {
            presentDouyinInstallPrompt()
        } else {
            showMessageActionNotice("无法打开\(appName)")
        }
    }

    func openDouyinFromRightEdgeGesture() {
        let candidates = [
            URL(string: "snssdk1128://")!,
            URL(string: "douyin://")!
        ]

        if let url = candidates.first(where: { UIApplication.shared.canOpenURL($0) }) {
            UIApplication.shared.open(url) { [weak self] success in
                guard !success else { return }
                DispatchQueue.main.async {
                    self?.presentDouyinInstallPrompt()
                }
            }
        } else {
            presentDouyinInstallPrompt()
        }
    }

    func openWechatFromRightEdgeGesture() {
        let candidates = [
            URL(string: "weixin://")!,
            URL(string: "wechat://")!
        ]

        if let url = candidates.first(where: { UIApplication.shared.canOpenURL($0) }) {
            UIApplication.shared.open(url) { [weak self] success in
                guard !success else { return }
                DispatchQueue.main.async {
                    self?.presentWechatInstallPrompt()
                }
            }
        } else {
            presentWechatInstallPrompt()
        }
    }

    func presentCodexAssistantPage() {
        presentAIAssistantPage(for: .codex, in: navigationController)
    }

    func presentAIAssistantPage(for provider: AIProvider, in navigationController: UINavigationController?) {
        setEmojiPanelVisible(false, animated: true)
        view.endEditing(true)
        let controller = CodexAssistantViewController(
            assistantTitle: provider.title,
            modelName: provider == .claude ? claudeConfiguration.model : codexConfiguration.model,
            introText: provider == .claude ? "我是 Claude AI，可以直接问我问题。" : "我是 Codex，可以直接问我问题。",
            thinkingText: provider == .claude ? "Claude 正在思考..." : "Codex 正在思考...",
            emptyText: provider == .claude ? "Claude 未返回内容。" : "Codex 未返回内容。",
            failureText: provider == .claude ? "Claude 生成失败，请检查 Claude 配置。" : "Codex 生成失败。",
            placeholder: provider == .claude ? "问 Claude..." : "问 Codex...",
            streamHandler: { [weak self] prompt, onDelta, completion in
                if provider == .claude {
                    self?.streamClaudeMessage(prompt: prompt, onDelta: onDelta, completion: completion)
                } else {
                    self?.streamCodexMessage(prompt: prompt, onDelta: onDelta, completion: completion)
                }
            }
        )
        navigationController?.setNavigationBarHidden(false, animated: true)
        navigationController?.pushViewController(controller, animated: true)
    }

    func presentDouyinInstallPrompt() {
        let alert = UIAlertController(
            title: "无法打开抖音",
            message: "请确认真机已安装抖音，并允许从当前 App 打开。",
            preferredStyle: .alert
        )
        alert.addAction(UIAlertAction(title: "取消", style: .cancel))
        alert.addAction(UIAlertAction(title: "去 App Store", style: .default) { _ in
            guard let url = URL(string: "itms-apps://itunes.apple.com/app/id1142110895") else { return }
            UIApplication.shared.open(url)
        })
        present(alert, animated: true)
    }

    func presentWechatInstallPrompt() {
        let alert = UIAlertController(
            title: "无法打开微信",
            message: "请确认真机已安装微信，并允许从当前 App 打开。",
            preferredStyle: .alert
        )
        alert.addAction(UIAlertAction(title: "取消", style: .cancel))
        alert.addAction(UIAlertAction(title: "去 App Store", style: .default) { _ in
            guard let url = URL(string: "itms-apps://itunes.apple.com/app/id414478124") else { return }
            UIApplication.shared.open(url)
        })
        present(alert, animated: true)
    }

    func miniProgramLaunchToken(from message: ChatMessage) -> String {
        let combined = [message.body, message.detail]
            .joined(separator: " ")
        if let shortcut = firstMiniProgramShortcutText(in: combined),
           let encoded = shortcut.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed),
           !encoded.isEmpty {
            return encoded
        }
        if let urlText = firstDetectedURLText(in: combined),
           let encoded = urlText.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed),
           !encoded.isEmpty {
            return encoded
        }
        return "store-nav"
    }

    func firstMiniProgramShortcutText(in text: String) -> String? {
        let pattern = #"(#小程序://[^\s<>"']+)"#
        guard let regex = try? NSRegularExpression(pattern: pattern) else { return nil }
        let fullRange = NSRange(text.startIndex..<text.endIndex, in: text)
        guard let match = regex.firstMatch(in: text, range: fullRange),
              let range = Range(match.range(at: 1), in: text)
        else { return nil }

        var end = range.upperBound
        while end > range.lowerBound,
              trailingURLPunctuation.contains(text[text.index(before: end)]) {
            end = text.index(before: end)
        }
        return String(text[range.lowerBound..<end])
    }

    func miniProgramShortcutTitle(from shortcut: String) -> String {
        let payload = shortcut.replacingOccurrences(of: "#小程序://", with: "")
        let title = payload.split(separator: "/", maxSplits: 1).first.map(String.init) ?? ""
        return title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? Self.jdMiniProgramTitle : title
    }

    func miniProgramShortcutSourceName(from shortcut: String) -> String {
        let title = miniProgramShortcutTitle(from: shortcut)
        let source = title.split(separator: "丨", maxSplits: 1).first.map(String.init) ?? title
        return source.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? Self.jdMiniProgramSourceName : source
    }

    func firstDetectedURL(in text: String) -> URL? {
        guard let urlText = firstDetectedURLText(in: text) else { return nil }
        return normalizedURL(from: urlText)
    }

    func normalizedURL(from urlText: String) -> URL? {
        let trimmed = urlText.trimmingCharacters(in: .whitespacesAndNewlines)
        if let direct = URL(string: trimmed), direct.scheme != nil {
            return direct
        }
        if trimmed.hasPrefix("//") {
            return URL(string: "https:\(trimmed)")
        }
        if trimmed.lowercased().hasPrefix("www.") {
            return URL(string: "https://\(trimmed)")
        }
        return URL(string: "https://\(trimmed)")
    }

    func firstDetectedURLText(in text: String) -> String? {
        let pattern = #"(?i)\b((?:[a-z][a-z0-9+.-]*://|www\.)[^\s<>"']+)"#
        guard let regex = try? NSRegularExpression(pattern: pattern) else { return nil }
        let fullRange = NSRange(text.startIndex..<text.endIndex, in: text)
        guard let match = regex.firstMatch(in: text, range: fullRange),
              let range = Range(match.range(at: 1), in: text)
        else { return nil }

        var end = range.upperBound
        while end > range.lowerBound,
              trailingURLPunctuation.contains(text[text.index(before: end)]) {
            end = text.index(before: end)
        }
        guard end > range.lowerBound else { return nil }
        return String(text[range.lowerBound..<end])
    }

    var trailingURLPunctuation: Set<Character> {
        [".", ",", "!", "?", ":", ";", ")", "]", "}", "。", "，", "！", "？", "：", "；"]
    }

    @discardableResult
    func previewAttachment(for message: ChatMessage) -> Bool {
        if message.type == .stickerGif {
            if message.attachmentURL != nil {
                presentPagedMediaPreview(startingAt: message)
            } else {
                presentImagePreview(
                    image: makeStickerPreviewImage(for: message),
                    title: message.body.isEmpty ? "GIF 预览" : message.body
                )
            }
            return true
        }

        guard let url = message.attachmentURL else { return false }

        switch message.type {
        case .image, .capturedPhoto:
            presentPagedMediaPreview(startingAt: message)
            return true
        case .video:
            presentPagedMediaPreview(startingAt: message)
            return true
        case .channelsVideo:
            return false
        case .voice:
            toggleVoicePlayback(for: message, url: url)
            return true
        case .file:
            presentFilePreview(url: url)
            return true
        default:
            if url.isFileURL {
                presentFilePreview(url: url)
                return true
            }
            return false
        }
    }

    func presentImagePreview(url: URL, title: String) {
        let signpostID = PerformanceSignpost.begin("MediaImageLoad")
        defer { PerformanceSignpost.end("MediaImageLoad", id: signpostID) }
        guard let image = animatedImageIfNeeded(from: url) ?? UIImage(contentsOfFile: url.path) else {
            showNotice("\u{56fe}\u{7247}\u{65e0}\u{6cd5}\u{6253}\u{5f00}")
            return
        }
        presentImagePreview(image: image.trimmedTransparentCanvasIfNeeded(), title: title)
    }

    func presentImagePreview(image: UIImage, title: String) {
        let previewController = MediaPreviewKit.imageController(
            image: image,
            title: title,
            onDragMedia: { [weak self] payload, state, screenPoint in
                self?.handlePreviewMediaDrag(payload: payload, state: state, screenPoint: screenPoint)
            },
            onBackgroundRemoved: { [weak self] image, originalTitle in
                self?.handleBackgroundRemovedImage(image, originalTitle: originalTitle)
            }
        )
        present(previewController, animated: true)
    }

    func presentPagedMediaPreview(startingAt message: ChatMessage) {
        let signpostID = PerformanceSignpost.begin("MediaPreviewOpen")
        defer { PerformanceSignpost.end("MediaPreviewOpen", id: signpostID) }
        let items = currentMediaPreviewItems()
        guard !items.isEmpty else {
            if let url = message.attachmentURL {
                if message.type == .video || message.type == .channelsVideo {
                    presentVideoPreview(url: url)
                } else {
                    presentImagePreview(url: url, title: message.body)
                }
            }
            return
        }
        let startIndex = items.firstIndex { $0.id == message.id } ?? 0
        let controller = MediaPreviewKit.pagedController(
            items: items,
            startIndex: startIndex,
            onRequestAccess: { [weak self] item in
                self?.submitMediaAccessRequest(item: item)
            },
            onDragMedia: { [weak self] payload, state, screenPoint in
                self?.handlePreviewMediaDrag(payload: payload, state: state, screenPoint: screenPoint)
            },
            onBackgroundRemoved: { [weak self] image, originalTitle in
                self?.handleBackgroundRemovedImage(image, originalTitle: originalTitle)
            }
        )
        present(controller, animated: true)
    }

    func currentMediaPreviewItems() -> [ChatMediaPreviewItem] {
        renderedMessages.compactMap(mediaPreviewItem)
    }

    func mediaPreviewItem(for message: ChatMessage) -> ChatMediaPreviewItem? {
        let signpostID = PerformanceSignpost.begin("MediaPreviewItemLoad")
        defer { PerformanceSignpost.end("MediaPreviewItemLoad", id: signpostID) }
        switch message.type {
        case .image, .capturedPhoto, .stickerGif:
            guard let url = message.attachmentURL else { return nil }
            let rawImage = animatedImageIfNeeded(from: url) ?? UIImage(contentsOfFile: url.path)
            let image = message.type == .stickerGif
                ? rawImage
                : rawImage?.trimmedTransparentCanvasIfNeeded()
            let sourceURL = publicMediaURLText(for: message, fallbackURL: url)
            return ChatMediaPreviewItem(
                id: message.id,
                title: message.body.isEmpty ? url.lastPathComponent : message.body,
                url: url,
                image: image,
                isVideo: false,
                accessScope: .public,
                accessState: .viewable,
                sourceURLText: sourceURL
            )
        case .video:
            guard let url = message.attachmentURL else { return nil }
            let sourceURL = publicMediaURLText(for: message, fallbackURL: url)
            return ChatMediaPreviewItem(
                id: message.id,
                title: message.body.isEmpty ? url.lastPathComponent : message.body,
                url: url,
                image: nil,
                isVideo: true,
                accessScope: .public,
                accessState: .viewable,
                sourceURLText: sourceURL
            )
        default:
            return nil
        }
    }

    func handlePreviewMediaDrag(payload: MediaDragPayload, state: UIGestureRecognizer.State, screenPoint: CGPoint) {
        switch state {
        case .began:
            showMediaDragView(payload: payload, screenPoint: screenPoint)
        case .changed:
            moveMediaDragView(to: screenPoint, payload: payload)
        case .ended:
            let target = participantForMediaDrop(at: screenPoint)
            hideMediaDragView()
            guard let target else {
                showNotice("请拖到左侧好友或群聊后松手")
                return
            }
            confirmSendDraggedMedia(payload, to: target)
        case .cancelled, .failed:
            hideMediaDragView()
        default:
            break
        }
    }

    @objc func handleMessageDragLongPress(_ gesture: UILongPressGestureRecognizer) {
        let location = gesture.location(in: messageCollectionView)
        let screenPoint = messageCollectionView.convert(location, to: nil)

        switch gesture.state {
        case .began:
            guard let indexPath = messageCollectionView.indexPathForItem(at: location),
                  let message = renderedMessage(at: indexPath),
                  canShowContextMenu(for: message)
            else {
                resetMessageDrag()
                return
            }
            messageDragCandidate = message
            messageDragStartScreenPoint = screenPoint
            isMessageDragActive = false
        case .changed:
            guard let message = messageDragCandidate else { return }
            let distance = hypot(screenPoint.x - messageDragStartScreenPoint.x, screenPoint.y - messageDragStartScreenPoint.y)
            if !isMessageDragActive {
                guard distance >= 12 else { return }
                isMessageDragActive = true
                UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                showMessageDragView(message: message, screenPoint: screenPoint)
            } else {
                moveMessageDragView(to: screenPoint, message: message)
            }
        case .ended:
            guard let message = messageDragCandidate, isMessageDragActive else {
                resetMessageDrag()
                return
            }
            let target = participantForMediaDrop(at: screenPoint)
            hideMediaDragView()
            resetMessageDrag()
            guard let target else {
                showNotice("请拖到左侧好友或群聊后松手")
                return
            }
            forwardDraggedMessage(message, to: target)
        case .cancelled, .failed:
            hideMediaDragView()
            resetMessageDrag()
        default:
            break
        }
    }

    func resetMessageDrag() {
        messageDragCandidate = nil
        messageDragStartScreenPoint = .zero
        isMessageDragActive = false
    }

    func showMessageDragView(message: ChatMessage, screenPoint: CGPoint) {
        hideMediaDragView()
        guard let window = view.window else { return }

        let container = UIView(frame: CGRect(x: 0, y: 0, width: 176, height: 70))
        container.backgroundColor = UIColor.black.withAlphaComponent(0.72)
        container.layer.cornerRadius = 14
        container.layer.cornerCurve = .continuous
        container.layer.borderWidth = 1
        container.layer.borderColor = UIColor.white.withAlphaComponent(0.24).cgColor
        container.clipsToBounds = true

        let iconBox = UIView()
        iconBox.backgroundColor = message.type.accentColor.withAlphaComponent(0.22)
        iconBox.layer.cornerRadius = 10
        iconBox.layer.cornerCurve = .continuous
        iconBox.translatesAutoresizingMaskIntoConstraints = false

        let iconView = UIImageView(image: UIImage(systemName: message.type.symbolName))
        iconView.tintColor = .white
        iconView.contentMode = .scaleAspectFit
        iconView.translatesAutoresizingMaskIntoConstraints = false

        let label = UILabel()
        label.text = messageDragInstruction(for: message)
        label.font = .systemFont(ofSize: 12.2, weight: .semibold)
        label.textColor = .white
        label.numberOfLines = 2
        label.translatesAutoresizingMaskIntoConstraints = false

        iconBox.addSubview(iconView)
        container.addSubview(iconBox)
        container.addSubview(label)
        window.addSubview(container)

        NSLayoutConstraint.activate([
            iconBox.leadingAnchor.constraint(equalTo: container.leadingAnchor, constant: 10),
            iconBox.centerYAnchor.constraint(equalTo: container.centerYAnchor),
            iconBox.widthAnchor.constraint(equalToConstant: 46),
            iconBox.heightAnchor.constraint(equalToConstant: 46),
            iconView.centerXAnchor.constraint(equalTo: iconBox.centerXAnchor),
            iconView.centerYAnchor.constraint(equalTo: iconBox.centerYAnchor),
            iconView.widthAnchor.constraint(equalToConstant: 23),
            iconView.heightAnchor.constraint(equalToConstant: 23),
            label.leadingAnchor.constraint(equalTo: iconBox.trailingAnchor, constant: 10),
            label.trailingAnchor.constraint(equalTo: container.trailingAnchor, constant: -10),
            label.centerYAnchor.constraint(equalTo: container.centerYAnchor)
        ])

        mediaDragView = container
        mediaDragTitleLabel = label
        moveMessageDragView(to: screenPoint, message: message)
    }

    func moveMessageDragView(to screenPoint: CGPoint, message: ChatMessage) {
        guard let dragView = mediaDragView else { return }
        dragView.center = CGPoint(x: screenPoint.x + 56, y: screenPoint.y - 28)
        if let participant = participantForMediaDrop(at: screenPoint) {
            mediaDragTitleLabel?.text = "松手转发给\(participant.displayName)"
            if mediaDragHighlightedParticipantID != participant.id {
                mediaDragHighlightedParticipantID = participant.id
                if let index = visibleLeftItemsForSelectedAccount().firstIndex(where: { $0.participantID == participant.id }) {
                    pulseSidebarItem(in: leftCollectionView, at: IndexPath(item: index, section: 0))
                }
            }
            dragView.backgroundColor = UIColor(red: 0.12, green: 0.46, blue: 0.32, alpha: 0.84)
        } else {
            mediaDragTitleLabel?.text = messageDragInstruction(for: message)
            mediaDragHighlightedParticipantID = nil
            dragView.backgroundColor = UIColor.black.withAlphaComponent(0.72)
        }
    }

    func messageDragInstruction(for message: ChatMessage) -> String {
        "拖到左侧转发\n\(messageDragSummary(for: message))"
    }

    func messageDragSummary(for message: ChatMessage) -> String {
        let text = [
            message.body,
            message.detail,
            message.attachmentURL?.lastPathComponent ?? ""
        ]
            .map { $0.replacingOccurrences(of: "\n", with: " ").trimmingCharacters(in: .whitespacesAndNewlines) }
            .first { !$0.isEmpty } ?? message.type.title
        return text.count > 32 ? "\(text.prefix(32))..." : text
    }

    func forwardDraggedMessage(_ message: ChatMessage, to target: ChatParticipant) {
        guard isForwardTargetAllowed(target) else {
            showNotice("不能转发给该对象")
            return
        }
        let newMessageIDs = appendIndividualForwardMessages([message], to: target)
        persistMessages()
        sendOpenApiForwardedMessages(messageIDs: newMessageIDs)
        focusForwardedMessages(in: target, messageID: newMessageIDs.last)
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.35) {
            self.showNotice("已转发给\(target.displayName)")
        }
    }

    func showMediaDragView(payload: MediaDragPayload, screenPoint: CGPoint) {
        hideMediaDragView()
        guard let window = view.window else { return }

        let container = UIView(frame: CGRect(x: 0, y: 0, width: 156, height: 74))
        container.backgroundColor = UIColor.black.withAlphaComponent(0.72)
        container.layer.cornerRadius = 14
        container.layer.cornerCurve = .continuous
        container.layer.borderWidth = 1
        container.layer.borderColor = UIColor.white.withAlphaComponent(0.24).cgColor
        container.clipsToBounds = true

        let thumbView = UIImageView()
        thumbView.image = payload.image
        thumbView.contentMode = .scaleAspectFill
        thumbView.backgroundColor = UIColor.white.withAlphaComponent(0.12)
        thumbView.layer.cornerRadius = 9
        thumbView.layer.cornerCurve = .continuous
        thumbView.clipsToBounds = true
        thumbView.translatesAutoresizingMaskIntoConstraints = false

        let iconView = UIImageView(image: UIImage(systemName: payload.isVideo ? "play.rectangle.fill" : "photo.fill"))
        iconView.tintColor = .white
        iconView.contentMode = .scaleAspectFit
        iconView.translatesAutoresizingMaskIntoConstraints = false

        let label = UILabel()
        label.text = "拖到左侧发送\(payload.kindTitle)"
        label.font = .systemFont(ofSize: 12.5, weight: .semibold)
        label.textColor = .white
        label.numberOfLines = 2
        label.translatesAutoresizingMaskIntoConstraints = false

        container.addSubview(thumbView)
        container.addSubview(iconView)
        container.addSubview(label)
        window.addSubview(container)

        NSLayoutConstraint.activate([
            thumbView.leadingAnchor.constraint(equalTo: container.leadingAnchor, constant: 10),
            thumbView.centerYAnchor.constraint(equalTo: container.centerYAnchor),
            thumbView.widthAnchor.constraint(equalToConstant: 52),
            thumbView.heightAnchor.constraint(equalToConstant: 52),
            iconView.centerXAnchor.constraint(equalTo: thumbView.centerXAnchor),
            iconView.centerYAnchor.constraint(equalTo: thumbView.centerYAnchor),
            iconView.widthAnchor.constraint(equalToConstant: 22),
            iconView.heightAnchor.constraint(equalToConstant: 22),
            label.leadingAnchor.constraint(equalTo: thumbView.trailingAnchor, constant: 10),
            label.trailingAnchor.constraint(equalTo: container.trailingAnchor, constant: -10),
            label.centerYAnchor.constraint(equalTo: container.centerYAnchor)
        ])

        mediaDragView = container
        mediaDragTitleLabel = label
        moveMediaDragView(to: screenPoint, payload: payload)
    }

    func moveMediaDragView(to screenPoint: CGPoint, payload: MediaDragPayload) {
        guard let dragView = mediaDragView else { return }
        dragView.center = CGPoint(x: screenPoint.x + 56, y: screenPoint.y - 28)
        if let participant = participantForMediaDrop(at: screenPoint) {
            mediaDragTitleLabel?.text = "松手发送给\(participant.displayName)"
            if mediaDragHighlightedParticipantID != participant.id {
                mediaDragHighlightedParticipantID = participant.id
                if let index = visibleLeftItemsForSelectedAccount().firstIndex(where: { $0.participantID == participant.id }) {
                    pulseSidebarItem(in: leftCollectionView, at: IndexPath(item: index, section: 0))
                }
            }
            dragView.backgroundColor = UIColor(red: 0.12, green: 0.46, blue: 0.32, alpha: 0.82)
        } else {
            mediaDragTitleLabel?.text = "拖到左侧发送\(payload.kindTitle)"
            mediaDragHighlightedParticipantID = nil
            dragView.backgroundColor = UIColor.black.withAlphaComponent(0.72)
        }
    }

    func hideMediaDragView() {
        mediaDragView?.removeFromSuperview()
        mediaDragView = nil
        mediaDragTitleLabel = nil
        mediaDragHighlightedParticipantID = nil
    }

    func participantForMediaDrop(at screenPoint: CGPoint) -> ChatParticipant? {
        guard leftCollectionView.window != nil else { return nil }
        let point = leftCollectionView.convert(screenPoint, from: nil)
        guard leftCollectionView.bounds.insetBy(dx: -8, dy: -8).contains(point),
              let indexPath = leftCollectionView.indexPathForItem(at: point),
              let leftItem = visibleLeftItemsForSelectedAccount()[safe: indexPath.item],
              let participantID = leftItem.participantID
        else { return nil }
        guard let participant = state.participants.first(where: { $0.id == participantID }),
              isForwardTargetAllowed(participant)
        else { return nil }
        return participant
    }

    func confirmSendDraggedMedia(_ payload: MediaDragPayload, to target: ChatParticipant) {
        let alert = UIAlertController(
            title: "发送\(payload.kindTitle)",
            message: "是否发送「\(payload.title)」给\(target.displayName)？",
            preferredStyle: .alert
        )
        alert.addAction(UIAlertAction(title: "取消", style: .cancel))
        alert.addAction(UIAlertAction(title: "发送", style: .default) { [weak self] _ in
            self?.sendDraggedMedia(payload, to: target)
        })
        (presentedViewController ?? self).present(alert, animated: true)
    }

    func sendDraggedMedia(_ payload: MediaDragPayload, to target: ChatParticipant) {
        guard let attachmentURL = mediaDragAttachmentURL(for: payload) else {
            showNotice("\(payload.kindTitle)无法发送")
            return
        }
        let sender = outgoingSenderForSelectedAccount(in: target)
        let message = MessageFlowCoordinator.outgoingMessage(
            conversationID: target.id,
            type: payload.isVideo ? .video : .image,
            sender: sender,
            body: payload.title.isEmpty ? payload.kindTitle : payload.title,
            detail: "拖拽转发\(payload.kindTitle)",
            presentation: outgoingPresentationForSelectedAccount(),
            timestamp: currentTimestamp(),
            attachmentURL: attachmentURL,
            isGroupConversation: isGroupParticipant(target)
        )
        state.messages.append(message)
        persistMessages()
        dismiss(animated: true) {
            self.focusForwardedMessages(in: target, messageID: message.id)
            self.showNotice("已发送给\(target.displayName)")
        }
    }

    func mediaDragAttachmentURL(for payload: MediaDragPayload) -> URL? {
        if let url = payload.url { return url }
        guard !payload.isVideo, let image = payload.image, let data = image.jpegData(compressionQuality: 0.92) else {
            return nil
        }
        let url = FileManager.default.temporaryDirectory
            .appendingPathComponent("drag-forward-\(UUID().uuidString)")
            .appendingPathExtension("jpg")
        do {
            try data.write(to: url)
            return url
        } catch {
            return nil
        }
    }

    @discardableResult
    func requestMediaAccessIfNeeded(for message: ChatMessage) -> Bool {
        guard let item = mediaPreviewItem(for: message) else { return false }
        switch item.accessState {
        case .viewable, .approved:
            return false
        case .requested:
            showMessageActionNotice("已申请查看\n请在右侧工具「申请审核」中模拟发布者同意后查看清晰内容。")
            return true
        case .requestable:
            let alert = UIAlertController(
                title: "申请查看\(item.kindTitle)",
                message: "\(item.title)\n可见范围：\(item.accessScope.rawValue)\n\n发布者同意后可查看清晰内容。",
                preferredStyle: .alert
            )
            alert.addAction(UIAlertAction(title: "取消", style: .cancel))
            alert.addAction(UIAlertAction(title: "发起申请", style: .default) { [weak self] _ in
                self?.submitMediaAccessRequest(item: item)
            })
            present(alert, animated: true)
            return true
        }
    }

    func inferredAccessScope(for message: ChatMessage) -> BubbleAccessScope {
        let combined = "\(message.body)\n\(message.detail)"
        if combined.contains(BubbleAccessScope.recipients.rawValue) { return .recipients }
        if combined.contains(BubbleAccessScope.friends.rawValue) { return .friends }
        if combined.contains(BubbleAccessScope.fans.rawValue) { return .fans }
        return .public
    }

    func publicMediaURLText(for message: ChatMessage, fallbackURL: URL) -> String {
        let combined = "\(message.body)\n\(message.detail)"
        if let match = combined.range(of: #"https?://[^\s，。；、\)\]]+"#, options: .regularExpression) {
            return String(combined[match])
        }
        return fallbackURL.isFileURL ? "app://media/\(message.id.uuidString)" : fallbackURL.absoluteString
    }

    func animatedImageIfNeeded(from url: URL) -> UIImage? {
        guard url.pathExtension.lowercased() == "gif",
              let source = CGImageSourceCreateWithURL(url as CFURL, nil)
        else { return nil }

        let frameCount = CGImageSourceGetCount(source)
        guard frameCount > 1 else { return nil }

        var frames: [UIImage] = []
        var totalDuration: TimeInterval = 0
        for index in 0..<frameCount {
            guard let cgImage = CGImageSourceCreateImageAtIndex(source, index, nil) else { continue }
            frames.append(UIImage(cgImage: cgImage))
            totalDuration += gifFrameDuration(at: index, source: source)
        }

        guard !frames.isEmpty else { return nil }
        return UIImage.animatedImage(
            with: frames,
            duration: max(totalDuration, Double(frames.count) * 0.08)
        )
    }

    func gifFrameDuration(at index: Int, source: CGImageSource) -> TimeInterval {
        guard let properties = CGImageSourceCopyPropertiesAtIndex(source, index, nil) as? [CFString: Any],
              let gifProperties = properties[kCGImagePropertyGIFDictionary] as? [CFString: Any]
        else { return 0.08 }

        let unclampedDelay = gifProperties[kCGImagePropertyGIFUnclampedDelayTime] as? NSNumber
        let delay = unclampedDelay ?? gifProperties[kCGImagePropertyGIFDelayTime] as? NSNumber
        return max(delay?.doubleValue ?? 0.08, 0.03)
    }

    func makeStickerPreviewImage(for message: ChatMessage) -> UIImage {
        let size = CGSize(width: 520, height: 360)
        let renderer = UIGraphicsImageRenderer(size: size)
        return renderer.image { context in
            let rect = CGRect(origin: .zero, size: size)
            UIColor(red: 0.96, green: 0.91, blue: 0.98, alpha: 1).setFill()
            context.fill(rect)

            UIColor(red: 1.0, green: 0.74, blue: 0.20, alpha: 1).setFill()
            UIBezierPath(ovalIn: CGRect(x: 128, y: 58, width: 216, height: 216)).fill()
            UIColor(red: 0.20, green: 0.16, blue: 0.12, alpha: 1).setFill()
            UIBezierPath(ovalIn: CGRect(x: 192, y: 136, width: 20, height: 30)).fill()
            UIBezierPath(ovalIn: CGRect(x: 258, y: 136, width: 20, height: 30)).fill()
            let smile = UIBezierPath()
            smile.move(to: CGPoint(x: 190, y: 202))
            smile.addQuadCurve(to: CGPoint(x: 282, y: 202), controlPoint: CGPoint(x: 236, y: 246))
            smile.lineWidth = 10
            smile.lineCapStyle = .round
            smile.stroke()

            UIColor.white.withAlphaComponent(0.88).setFill()
            UIBezierPath(roundedRect: CGRect(x: 318, y: 76, width: 92, height: 48), cornerRadius: 24).fill()
            UIBezierPath(roundedRect: CGRect(x: 350, y: 146, width: 112, height: 48), cornerRadius: 24).fill()
            UIBezierPath(roundedRect: CGRect(x: 296, y: 218, width: 148, height: 48), cornerRadius: 24).fill()

            let badgeRect = CGRect(x: 46, y: 270, width: 124, height: 48)
            UIColor(red: 0.50, green: 0.22, blue: 0.72, alpha: 1).setFill()
            UIBezierPath(roundedRect: badgeRect, cornerRadius: 24).fill()
            let badgeAttributes: [NSAttributedString.Key: Any] = [
                .font: UIFont.systemFont(ofSize: 22, weight: .bold),
                .foregroundColor: UIColor.white
            ]
            ("GIF" as NSString).draw(
                in: badgeRect.insetBy(dx: 38, dy: 10),
                withAttributes: badgeAttributes
            )

            let title = message.body.isEmpty ? "动态贴纸" : message.body
            let attributes: [NSAttributedString.Key: Any] = [
                .font: UIFont.systemFont(ofSize: 22, weight: .semibold),
                .foregroundColor: UIColor(red: 0.18, green: 0.12, blue: 0.22, alpha: 0.78)
            ]
            (title as NSString).draw(
                in: CGRect(x: 190, y: 280, width: 280, height: 34),
                withAttributes: attributes
            )
        }
    }

    func presentMockMediaPreview(for message: ChatMessage, symbolName: String, title: String) {
        let previewController = UIViewController()
        previewController.view.backgroundColor = .black
        previewController.title = title

        let iconView = UIImageView(image: UIImage(systemName: symbolName))
        iconView.tintColor = .white
        iconView.contentMode = .scaleAspectFit
        iconView.translatesAutoresizingMaskIntoConstraints = false

        let titleLabel = UILabel()
        titleLabel.text = message.body
        titleLabel.textColor = .white
        titleLabel.font = .systemFont(ofSize: 17, weight: .semibold)
        titleLabel.textAlignment = .center
        titleLabel.numberOfLines = 0
        titleLabel.translatesAutoresizingMaskIntoConstraints = false

        let detailLabel = UILabel()
        detailLabel.text = message.detail.isEmpty ? message.type.title : message.detail
        detailLabel.textColor = UIColor.white.withAlphaComponent(0.72)
        detailLabel.font = .systemFont(ofSize: 13)
        detailLabel.textAlignment = .center
        detailLabel.numberOfLines = 0
        detailLabel.translatesAutoresizingMaskIntoConstraints = false

        previewController.view.addSubview(iconView)
        previewController.view.addSubview(titleLabel)
        previewController.view.addSubview(detailLabel)
        NSLayoutConstraint.activate([
            iconView.centerXAnchor.constraint(equalTo: previewController.view.centerXAnchor),
            iconView.centerYAnchor.constraint(equalTo: previewController.view.centerYAnchor, constant: -48),
            iconView.widthAnchor.constraint(equalToConstant: 86),
            iconView.heightAnchor.constraint(equalToConstant: 86),

            titleLabel.topAnchor.constraint(equalTo: iconView.bottomAnchor, constant: 24),
            titleLabel.leadingAnchor.constraint(equalTo: previewController.view.leadingAnchor, constant: 28),
            titleLabel.trailingAnchor.constraint(equalTo: previewController.view.trailingAnchor, constant: -28),

            detailLabel.topAnchor.constraint(equalTo: titleLabel.bottomAnchor, constant: 10),
            detailLabel.leadingAnchor.constraint(equalTo: titleLabel.leadingAnchor),
            detailLabel.trailingAnchor.constraint(equalTo: titleLabel.trailingAnchor)
        ])

        let navigationController = UINavigationController(rootViewController: previewController)
        previewController.navigationItem.rightBarButtonItem = UIBarButtonItem(
            barButtonSystemItem: .done,
            target: self,
            action: #selector(dismissPresentedController)
        )
        present(navigationController, animated: true)
    }

    func presentMockFilePreview(for message: ChatMessage) {
        let alert = UIAlertController(
            title: message.body,
            message: message.detail.isEmpty ? "文件预览" : message.detail,
            preferredStyle: .alert
        )
        alert.addAction(UIAlertAction(title: "预览", style: .default) { [weak self] _ in
            self?.showNotice("正在打开文件预览")
        })
        alert.addAction(UIAlertAction(title: "取消", style: .cancel))
        present(alert, animated: true)
    }

    func playVoiceMessage(_ message: ChatMessage) {
        guard let url = message.attachmentURL else {
            showNotice("这条语音没有可播放音频")
            return
        }
        toggleVoicePlayback(for: message, url: url)
    }

    func toggleVoicePlayback(for message: ChatMessage, url: URL) {
        if playingVoiceMessageID == message.id {
            stopVoicePlayback(shouldShowNotice: true)
            return
        }

        let previousID = playingVoiceMessageID
        do {
            audioPlayer?.stop()
            try AVAudioSession.sharedInstance().setCategory(.playback, mode: .default)
            try AVAudioSession.sharedInstance().setActive(true)
            audioPlayer = try AVAudioPlayer(contentsOf: url)
            audioPlayer?.delegate = self
            audioPlayer?.prepareToPlay()
            guard audioPlayer?.play() == true else {
                audioPlayer = nil
                playingVoiceMessageID = nil
                refreshVoicePlaybackCells(messageIDs: [previousID, message.id])
                showNotice("语音无法播放")
                return
            }
            playingVoiceMessageID = message.id
            refreshVoicePlaybackCells(messageIDs: [previousID, message.id])
            showNotice("\u{6b63}\u{5728}\u{64ad}\u{653e}\u{8bed}\u{97f3}")
            syncIslandActivityIfNeeded()
        } catch {
            audioPlayer = nil
            playingVoiceMessageID = nil
            refreshVoicePlaybackCells(messageIDs: [previousID, message.id])
            showNotice("\u{8bed}\u{97f3}\u{65e0}\u{6cd5}\u{64ad}\u{653e}")
            syncIslandActivityIfNeeded()
        }
    }

    func stopVoicePlayback(shouldShowNotice: Bool) {
        let previousID = playingVoiceMessageID
        audioPlayer?.stop()
        audioPlayer?.delegate = nil
        audioPlayer = nil
        playingVoiceMessageID = nil
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
        refreshVoicePlaybackCells(messageIDs: [previousID])
        if shouldShowNotice {
            showNotice("已停止播放语音")
        }
        syncIslandActivityIfNeeded()
    }

    func refreshVoicePlaybackCells(messageIDs: [UUID?]) {
        let ids = Set(messageIDs.compactMap { $0 })
        guard !ids.isEmpty else { return }
        let indexPaths = renderedMessages.enumerated().compactMap { index, message -> IndexPath? in
            ids.contains(message.id) ? IndexPath(item: index, section: 0) : nil
        }
        guard !indexPaths.isEmpty else { return }
        messageCollectionView.reloadItems(at: indexPaths)
        scheduleConnectionUpdate()
    }

    func presentVideoPreview(url: URL) {
        let controller = MediaPreviewKit.videoController(
            url: url,
            onDragMedia: { [weak self] payload, state, screenPoint in
                self?.handlePreviewMediaDrag(payload: payload, state: state, screenPoint: screenPoint)
            }
        )
        present(controller, animated: true)
    }

    func presentFilePreview(url: URL) {
        if let controller = MediaPreviewKit.inlineFileControllerIfSupported(url: url) {
            present(controller, animated: true)
            return
        }
        previewURL = url
        let controller = QLPreviewController()
        controller.dataSource = self
        present(controller, animated: true)
    }

    @objc func dismissPresentedController() {
        dismiss(animated: true)
    }

    @objc func popTopController() {
        if let presentedNavigationController = presentedViewController as? UINavigationController,
           presentedNavigationController.viewControllers.count > 1 {
            presentedNavigationController.popViewController(animated: true)
            return
        }
        navigationController?.popViewController(animated: true)
    }

    func pulseSidebarItem(in collectionView: UICollectionView, at indexPath: IndexPath) {
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.28) {
            guard let cell = collectionView.cellForItem(at: indexPath) else { return }
            UIView.animate(withDuration: 0.12, animations: {
                cell.transform = CGAffineTransform(scaleX: 1.08, y: 1.08)
            }, completion: { _ in
                UIView.animate(withDuration: 0.18) {
                    cell.transform = .identity
                }
            })
            self.updateConnections()
        }
    }

    func updateConnections() {
        scheduleConnectionUpdate()
    }

    func performConnectionUpdate() {
        let signpostID = PerformanceSignpost.begin("UpdateConnections")
        defer { PerformanceSignpost.end("UpdateConnections", id: signpostID) }
        guard windowState != .minimized, isViewLoaded else {
            connectionOverlay.anchors = []
            return
        }

        let isTimelineScrolling = messageCollectionView.isDragging
            || messageCollectionView.isDecelerating
            || leftCollectionView.isDragging
            || leftCollectionView.isDecelerating
            || isLeftScrollPreviewGestureActive()
        let isConnectionScrolling = isTimelineScrolling
            || rightAccountCollectionView.isDragging
            || rightAccountCollectionView.isDecelerating
            || rightToolCollectionView.isDragging
            || rightToolCollectionView.isDecelerating
        if !isConnectionScrolling {
            let hasPendingLayout = messageCollectionView.layer.needsLayout()
                || leftCollectionView.layer.needsLayout()
                || rightAccountCollectionView.layer.needsLayout()
            if hasPendingLayout, deferredConnectionLayoutPassCount == 0 {
                // Draw with any already available visible geometry first. UIKit
                // gets one run-loop pass to settle the new collection layouts
                // before a fallback synchronous layout is allowed.
                deferredConnectionLayoutPassCount = 1
                connectionUpdateNeeded = true
            } else {
                deferredConnectionLayoutPassCount = 0
                if hasPendingLayout {
                    messageCollectionView.layoutIfNeeded()
                    leftCollectionView.layoutIfNeeded()
                    rightAccountCollectionView.layoutIfNeeded()
                }
            }
        } else {
            deferredConnectionLayoutPassCount = 0
        }

        var anchors: [ConnectionAnchor] = []
        let visibleMessages = renderedMessages
        let rightItems = visibleRightAccountItems()
        let rightIndexByParticipantID = Dictionary(
            rightItems.enumerated().compactMap { index, item -> (UUID, Int)? in
                guard let participantID = item.participantID else { return nil }
                return (participantID, index)
            },
            uniquingKeysWith: { current, _ in current }
        )
        let leftItems = visibleLeftItemsForSelectedAccount()
        let leftIndexByParticipantID = leftSidebarIndexByParticipantID()
        let homeSnapshot = isHomeTimeline ? unreadTimelineLayoutSnapshot() : nil
        var homeLeftAnchorByParticipantID: [UUID: CGPoint] = [:]
        let candidateIndexPaths: [IndexPath]
        if let homeSnapshot {
            candidateIndexPaths = unreadTimelineConnectionIndexPaths(
                snapshot: homeSnapshot,
                visibleBounds: messageCollectionView.bounds
            )
        } else {
            let preloadBounds = messageCollectionView.bounds
            candidateIndexPaths = (messageCollectionView.collectionViewLayout
                .layoutAttributesForElements(in: preloadBounds) ?? [])
                .filter { $0.representedElementCategory == .cell }
                .map(\.indexPath)
                .sorted { $0.item < $1.item }
        }

        let candidateLimit = isHomeTimeline ? candidateIndexPaths.count : (isTimelineScrolling ? 18 : 32)
        for indexPath in candidateIndexPaths.prefix(candidateLimit) {
            guard let message = visibleMessages[safe: indexPath.item] else { continue }
            let messageFrame = homeSnapshot?.messageFrames[safe: indexPath.item]
                ?? messageLayoutFrame(at: indexPath)
            guard let messageFrame else { continue }
            guard message.type != .system else { continue }
            let bubbleFrame = connectionFrameForMessage(at: indexPath, fallbackFrame: messageFrame)

            if message.isOutgoing {
                guard shouldDrawRightAccountConnection(for: message) else { continue }
                guard let accountIndex = rightIndexByParticipantID[message.sender.id],
                      let accountFrame = rightAccountCollectionView.layoutAttributesForItem(at: IndexPath(item: accountIndex, section: 0))?.frame
                else { continue }
                let from: CGPoint
                if isHomeTimeline,
                   let accountCell = rightAccountCollectionView.cellForItem(
                       at: IndexPath(item: accountIndex, section: 0)
                   ) as? SidebarCell {
                    from = accountCell.leadingConnectionAnchorPoint(in: connectionOverlay)
                } else {
                    from = connectionOverlay.convert(
                        clampedSideAnchor(
                            for: accountFrame,
                            in: rightAccountCollectionView,
                            horizontalEdge: .minX
                        ),
                        from: rightAccountCollectionView
                    )
                }
                let to = connectionOverlay.convert(
                    sideAnchor(for: bubbleFrame, horizontalEdge: .maxX),
                    from: messageCollectionView
                )
                anchors.append(
                    ConnectionAnchor(
                        from: from,
                        to: to,
                        color: messageConnectionColor(for: message),
                        direction: .rightAccountToMessage,
                        groupID: messageConnectionGroupID(for: message),
                        usesRoundedCorners: false
                    )
                )
            } else {
                let sidebarIndex = leftSidebarAnchorIndex(for: message, indexByParticipantID: leftIndexByParticipantID)
                guard let sidebarIndex,
                      let friendFrame = leftCollectionView.layoutAttributesForItem(at: IndexPath(item: sidebarIndex, section: 0))?.frame
                else { continue }
                let participantID = leftItems[safe: sidebarIndex]?.participantID
                let from: CGPoint
                if isHomeTimeline {
                    guard let participantID,
                          let homeSnapshot,
                          let groupIndex = homeSnapshot.groupIndexByParticipantID[participantID],
                          let group = homeSnapshot.groups[safe: groupIndex]
                    else { continue }
                    let sidebarIndexPath = IndexPath(item: sidebarIndex, section: 0)
                    let liveSidebarCell = leftCollectionView.cellForItem(
                        at: sidebarIndexPath
                    ) as? SidebarCell
                    if indexPath.item == group.messageIndices.first {
                        guard let sidebarCell = liveSidebarCell,
                              let messageCell = messageCollectionView.cellForItem(at: indexPath) as? ChatMessageCell,
                              let measuredBubbleFrame = messageCell.cachedConnectionFrame(
                                  in: messageCollectionView,
                                  for: message.id,
                                  usesOuterBubble: false
                              )
                        else {
                            // Do not briefly draw a fallback bent line while the
                            // first message and sidebar cell are still settling.
                            continue
                        }
                        let bubbleCenter = connectionOverlay.convert(
                            CGPoint(x: measuredBubbleFrame.midX, y: measuredBubbleFrame.midY),
                            from: messageCollectionView
                        )
                        let centerInSidebar = leftCollectionView.convert(
                            bubbleCenter,
                            from: connectionOverlay
                        )
                        let measuredNaturalTop = centerInSidebar.y
                            - UnreadTimelineLayoutMetrics.avatarSideLength / 2
                        let previousNaturalTop = unreadTimelineMeasuredAvatarTopByParticipantID[participantID]
                        if previousNaturalTop.map({ abs($0 - measuredNaturalTop) > 0.25 }) ?? true {
                            unreadTimelineMeasuredAvatarTopByParticipantID[participantID] = measuredNaturalTop
                            sidebarCell.updateUnreadTimelineTopOffset(
                                unreadTimelineAvatarTopOffset(for: group)
                            )
                            sidebarCell.layoutIfNeeded()
                        }
                    }
                    let resolvedHomeAnchor = homeLeftAnchorByParticipantID[participantID]
                        ?? liveSidebarCell?.connectionAnchorPoint(in: connectionOverlay)
                        ?? unreadTimelineLeftAnchor(
                            participantID: participantID,
                            sidebarIndex: sidebarIndex,
                            snapshot: homeSnapshot
                        )
                    guard let homeAnchor = resolvedHomeAnchor
                    else { continue }
                    homeLeftAnchorByParticipantID[participantID] = homeAnchor
                    from = homeAnchor
                } else if let floating = selectedLeftSidebarFloatingAnchor(for: participantID) {
                    from = floating
                } else if let sidebarCell = leftCollectionView.cellForItem(at: IndexPath(item: sidebarIndex, section: 0)) as? SidebarCell {
                    from = sidebarCell.connectionAnchorPoint(in: connectionOverlay)
                } else {
                    from = connectionOverlay.convert(
                        clampedSideAnchor(
                            for: friendFrame,
                            in: leftCollectionView,
                            horizontalEdge: .maxX
                        ),
                        from: leftCollectionView
                    )
                }
                let bubbleFrameInOverlay = connectionOverlay.convert(
                    bubbleFrame,
                    from: messageCollectionView
                )
                let showsGroupMemberAvatar = !isHomeTimeline
                    && message.isGroupConversation
                    && groupDisplaySettings(for: message).showsAvatar
                let to: CGPoint
                let preferredVerticalX: CGFloat?
                if showsGroupMemberAvatar {
                    let memberAvatarPoint = (messageCollectionView.cellForItem(at: indexPath) as? ChatMessageCell)?
                        .cachedGroupMemberAvatarConnectionPoint(
                            in: messageCollectionView,
                            for: message.id
                        ) ?? CGPoint(
                            x: bubbleFrame.minX - MessageConnectionLayoutMetrics.groupMemberOccupiedWidth,
                            y: bubbleFrame.midY
                        )
                    let memberAvatarPointInOverlay = connectionOverlay.convert(
                        memberAvatarPoint,
                        from: messageCollectionView
                    )
                    preferredVerticalX = memberAvatarPointInOverlay.x
                        - MessageConnectionLayoutMetrics.groupTrunkToMemberAvatarGap
                    to = memberAvatarPointInOverlay
                } else {
                    to = CGPoint(x: bubbleFrameInOverlay.minX, y: bubbleFrameInOverlay.midY)
                    preferredVerticalX = nil
                }
                anchors.append(
                    ConnectionAnchor(
                        from: from,
                        to: to,
                        color: messageConnectionColor(for: message),
                        direction: .leftToMessage,
                        groupID: messageConnectionGroupID(for: message),
                        usesRoundedCorners: false,
                        preferredVerticalX: preferredVerticalX
                    )
                )
                if isHomeTimeline,
                   let unansweredItem = unansweredItemForDisplayedMessage(message),
                   unansweredItem.id == selectedUnansweredItemID {
                    let targetIDs = Set(unansweredItem.targetAccountIDs)
                    let targetAccountID = unansweredAccountFilterID
                        ?? state.currentUsers.lazy.map(\.id).first(where: targetIDs.contains)
                    if let targetAccountID,
                       let accountIndex = rightIndexByParticipantID[targetAccountID],
                       let accountFrame = rightAccountCollectionView.layoutAttributesForItem(
                           at: IndexPath(item: accountIndex, section: 0)
                       )?.frame {
                        let accountAnchor: CGPoint
                        if let accountCell = rightAccountCollectionView.cellForItem(
                            at: IndexPath(item: accountIndex, section: 0)
                        ) as? SidebarCell {
                            accountAnchor = accountCell.leadingConnectionAnchorPoint(in: connectionOverlay)
                        } else {
                            accountAnchor = connectionOverlay.convert(
                                clampedSideAnchor(
                                    for: accountFrame,
                                    in: rightAccountCollectionView,
                                    horizontalEdge: .minX
                                ),
                                from: rightAccountCollectionView
                            )
                        }
                        let messageAreaRightEdge = connectionOverlay.convert(
                            CGPoint(x: messageCollectionView.bounds.maxX, y: messageCollectionView.bounds.minY),
                            from: messageCollectionView
                        ).x
                        let corridorX = max(
                            bubbleFrameInOverlay.maxX + 8,
                            min(accountAnchor.x - 8, messageAreaRightEdge + 4)
                        )
                        anchors.append(
                            ConnectionAnchor(
                                from: accountAnchor,
                                to: CGPoint(x: bubbleFrameInOverlay.maxX, y: bubbleFrameInOverlay.midY),
                                color: unansweredAccountColor(for: targetAccountID),
                                direction: .rightAccountToMessage,
                                groupID: nil,
                                usesRoundedCorners: true,
                                preferredVerticalX: corridorX
                            )
                        )
                    }
                }
            }
        }
        connectionOverlay.anchors = anchors
    }

    func messageConnectionColor(for message: ChatMessage) -> UIColor {
        if !isHomeTimeline, message.isGroupConversation {
            guard message.isOutgoing else { return .white }
            return outgoingGroupAccountID(for: message)
                .map { unansweredAccountColor(for: $0) } ?? .white
        }
        if isHomeTimeline { return .white }
        return message.sender.tintColor
    }

    func shouldDrawRightAccountConnection(for message: ChatMessage) -> Bool {
        !usesOutgoingGroupAccountPresentation(for: message)
    }

    func usesOutgoingGroupAccountPresentation(for message: ChatMessage) -> Bool {
        !isHomeTimeline
            && message.isOutgoing
            && state.activeFriend.map(isGroupConversation) == true
    }

    func outgoingGroupAccountID(for message: ChatMessage) -> UUID? {
        guard usesOutgoingGroupAccountPresentation(for: message),
              let group = state.activeFriend
        else { return nil }
        return groupAccountID(for: message, in: group)
    }

    func messageConnectionGroupID(for message: ChatMessage) -> UUID? {
        if !isHomeTimeline, message.isGroupConversation {
            return message.isOutgoing ? nil : message.conversationID
        }
        return isHomeTimeline && !message.isOutgoing ? message.conversationID : nil
    }

    func unreadTimelineLeftAnchor(
        participantID: UUID,
        sidebarIndex: Int,
        snapshot: UnreadTimelineLayoutSnapshot
    ) -> CGPoint? {
        guard let groupIndex = snapshot.groupIndexByParticipantID[participantID],
              let group = snapshot.groups[safe: groupIndex]
        else { return nil }
        let sidebarIndexPath = IndexPath(item: sidebarIndex, section: 0)
        if let sidebarCell = leftCollectionView.cellForItem(at: sidebarIndexPath) as? SidebarCell {
            sidebarCell.updateUnreadTimelineTopOffset(unreadTimelineAvatarTopOffset(for: group))
            sidebarCell.layoutIfNeeded()
            let liveAnchor = sidebarCell.connectionAnchorPoint(in: connectionOverlay)
            return liveAnchor
        }

        guard let sidebarFrame = leftCollectionView.layoutAttributesForItem(at: sidebarIndexPath)?.frame
        else { return nil }
        let resolvedAvatarFrame = CGRect(
            x: group.avatarFrame.minX,
            y: group.frame.minY + unreadTimelineAvatarTopOffset(for: group),
            width: group.avatarFrame.width,
            height: group.avatarFrame.height
        )
        let fallbackAnchor = connectionOverlay.convert(
            sideAnchor(for: resolvedAvatarFrame, horizontalEdge: .maxX),
            from: leftCollectionView
        )
        let sidebarEdge = connectionOverlay.convert(
            CGPoint(x: sidebarFrame.maxX, y: sidebarFrame.midY),
            from: leftCollectionView
        )
        return CGPoint(x: sidebarEdge.x.isFinite ? sidebarEdge.x : fallbackAnchor.x,
                       y: fallbackAnchor.y)
    }

    func unreadTimelineConnectionIndexPaths(
        snapshot: UnreadTimelineLayoutSnapshot,
        visibleBounds: CGRect
    ) -> [IndexPath] {
        var result: [IndexPath] = []
        let groups = snapshot.groups
        var lowerGroup = 0
        var upperGroup = groups.count
        while lowerGroup < upperGroup {
            let middle = (lowerGroup + upperGroup) / 2
            if groups[middle].frame.maxY < visibleBounds.minY {
                lowerGroup = middle + 1
            } else {
                upperGroup = middle
            }
        }

        for group in groups.dropFirst(lowerGroup) {
            guard group.frame.minY <= visibleBounds.maxY else { break }
            let indices = group.messageIndices
            guard !indices.isEmpty else { continue }

            var lowerMessage = 0
            var upperMessage = indices.count
            while lowerMessage < upperMessage {
                let middle = (lowerMessage + upperMessage) / 2
                let frame = snapshot.messageFrames[indices[middle]]
                if frame.maxY < visibleBounds.minY {
                    lowerMessage = middle + 1
                } else {
                    upperMessage = middle
                }
            }

            var firstVisible: Int?
            var lastVisible: Int?
            var position = lowerMessage
            while position < indices.count {
                let frame = snapshot.messageFrames[indices[position]]
                guard frame.minY <= visibleBounds.maxY else { break }
                if frame.maxY >= visibleBounds.minY {
                    firstVisible = firstVisible ?? position
                    lastVisible = position
                }
                position += 1
            }

            guard let firstVisible, let lastVisible else {
                let nearestPosition = min(max(lowerMessage, 0), indices.count - 1)
                result.append(IndexPath(item: indices[nearestPosition], section: 0))
                continue
            }
            let firstIncluded = max(0, firstVisible - 1)
            let lastIncluded = min(indices.count - 1, lastVisible + 1)
            result.reserveCapacity(result.count + lastIncluded - firstIncluded + 1)
            for includedPosition in firstIncluded...lastIncluded {
                result.append(IndexPath(item: indices[includedPosition], section: 0))
            }
        }
        return result
    }

    func selectedLeftSidebarFloatingAnchor(for participantID: UUID?) -> CGPoint? {
        guard let participantID,
              participantID == state.selectedFriendID,
              !selectedLeftSidebarFloatingView.isHidden,
              selectedLeftSidebarFloatingView.alpha > 0.01
        else { return nil }
        let frame = connectionOverlay.convert(
            selectedLeftSidebarFloatingView.bounds,
            from: selectedLeftSidebarFloatingView
        )
        return CGPoint(x: frame.maxX, y: frame.midY)
    }

    func messageLayoutFrame(at indexPath: IndexPath) -> CGRect? {
        messageCollectionView.layoutAttributesForItem(at: indexPath)?.frame
    }

    func connectionFrameForMessage(at indexPath: IndexPath, fallbackFrame: CGRect) -> CGRect {
        let messageWidth = messageCollectionView.bounds.width
        guard let message = renderedMessage(at: indexPath) else { return fallbackFrame }
        let usesOuterBubble = !isHomeTimeline
            && message.isGroupConversation
            && !message.isOutgoing
        if let cell = messageCollectionView.cellForItem(at: indexPath) as? ChatMessageCell,
           let cachedFrame = cell.cachedConnectionFrame(
               in: messageCollectionView,
               for: message.id,
               usesOuterBubble: usesOuterBubble
           ),
           cachedFrame.midY >= fallbackFrame.minY - 1,
           cachedFrame.midY <= fallbackFrame.maxY + 1,
           cachedFrame.minX >= -1,
           cachedFrame.maxX <= messageWidth + 1 {
            return cachedFrame
        }
        let groupAvatarAndSpacing: CGFloat
        if !isHomeTimeline,
           message.isGroupConversation,
           !message.isOutgoing,
           groupDisplaySettings(for: message).showsAvatar {
            groupAvatarAndSpacing = MessageConnectionLayoutMetrics.groupMemberOccupiedWidth
        } else {
            groupAvatarAndSpacing = 0
        }
        let unansweredWidthReserve: CGFloat = isHomeTimeline ? 11 : 0
        let bubbleWidth = MessageConnectionLayoutMetrics.maximumBubbleWidth(
            in: messageWidth,
            occupiedWidth: groupAvatarAndSpacing,
            reservedWidth: unansweredWidthReserve
        )
        let connectionSideInset = MessageConnectionLayoutMetrics.resolvedConnectionSideInset(
            in: messageWidth,
            occupiedWidth: groupAvatarAndSpacing
        )
        let x = message.isOutgoing
            ? messageWidth - bubbleWidth - connectionSideInset
            : fallbackFrame.minX
                + connectionSideInset
                + groupAvatarAndSpacing
        let topSpacing: CGFloat
        let bottomPadding: CGFloat
        if isHomeTimeline, let snapshot = unreadTimelineLayoutSnapshot() {
            topSpacing = snapshot.messageTopSpacings[safe: indexPath.item] ?? 0
            bottomPadding = snapshot.messageBottomPaddings[safe: indexPath.item] ?? 0
        } else {
            topSpacing = 4
            bottomPadding = 4
        }
        return CGRect(
            x: max(0, x),
            y: fallbackFrame.minY + topSpacing,
            width: min(
                bubbleWidth,
                messageWidth
                    - connectionSideInset
                    - MessageConnectionLayoutMetrics.bubbleOppositeSideInset
            ),
            height: max(1, fallbackFrame.height - topSpacing - bottomPadding)
        )
    }

    func leftSidebarAnchorIndex(for message: ChatMessage) -> Int? {
        leftSidebarAnchorIndex(for: message, indexByParticipantID: leftSidebarIndexByParticipantID())
    }

    func leftSidebarAnchorIndex(for message: ChatMessage, indexByParticipantID: [UUID: Int]) -> Int? {
        if isHomeTimeline {
            return indexByParticipantID[message.conversationID]
        }
        for participantID in leftSidebarAnchorCandidateIDs(for: message) {
            if let index = indexByParticipantID[participantID] {
                return index
            }
        }
        return nil
    }

    func leftSidebarAnchorCandidateIDs(for message: ChatMessage) -> [UUID] {
        if message.isGroupConversation {
            if state.selectedFriendID == message.conversationID {
                return [message.conversationID]
            }
            return [message.conversationID, message.sender.id]
        }
        return [message.conversationID, message.sender.id]
    }

    enum SideAnchorHorizontalEdge {
        case minX
        case maxX
    }

    func sideAnchor(
        for frame: CGRect,
        horizontalEdge: SideAnchorHorizontalEdge
    ) -> CGPoint {
        let x: CGFloat
        switch horizontalEdge {
        case .minX:
            x = frame.minX
        case .maxX:
            x = frame.maxX
        }
        return CGPoint(
            x: x,
            y: frame.midY
        )
    }

    func clampedSideAnchor(
        for frame: CGRect,
        in collectionView: UICollectionView,
        horizontalEdge: SideAnchorHorizontalEdge
    ) -> CGPoint {
        let visibleBounds = collectionView.bounds.insetBy(dx: 0, dy: connectionViewportInset + 1)
        let anchor = sideAnchor(for: frame, horizontalEdge: horizontalEdge)
        return CGPoint(
            x: anchor.x,
            y: min(max(anchor.y, visibleBounds.minY), visibleBounds.maxY)
        )
    }

    func safeSidebarItemSize(for collectionView: UICollectionView, preferred: CGSize) -> CGSize {
        guard collectionView.bounds.width > 0 else {
            return CGSize(width: 1, height: max(1, preferred.height))
        }
        let layoutInset = (collectionView.collectionViewLayout as? UICollectionViewFlowLayout)?.sectionInset ?? .zero
        let availableWidth = collectionView.bounds.width
            - collectionView.adjustedContentInset.left
            - collectionView.adjustedContentInset.right
            - layoutInset.left
            - layoutInset.right
            - 1
        let width = min(preferred.width, max(1, floor(availableWidth)))
        return CGSize(width: width, height: max(1, preferred.height))
    }

    func invalidateSidebarLayoutsIfNeeded() {
        for collectionView in [leftCollectionView, rightAccountCollectionView, rightToolCollectionView] {
            let key = ObjectIdentifier(collectionView)
            let width = collectionView.bounds.width
            guard width > 0, abs((lastSidebarLayoutWidths[key] ?? 0) - width) > 0.5 else { continue }
            lastSidebarLayoutWidths[key] = width
            collectionView.collectionViewLayout.invalidateLayout()
        }
    }

    static func makeSidebarLayout() -> UICollectionViewLayout {
        let layout = ContentOffsetPreservingFlowLayout()
        layout.itemSize = sidebarItemSize
        layout.minimumLineSpacing = 10
        layout.sectionInset = UIEdgeInsets(top: 2, left: 0, bottom: 20, right: 0)
        return layout
    }

    static func makeMessageLayout() -> UICollectionViewLayout {
        let layout = ContentOffsetPreservingFlowLayout()
        layout.scrollDirection = .vertical
        layout.estimatedItemSize = .zero
        layout.minimumLineSpacing = 2
        layout.sectionInset = UIEdgeInsets(top: 8, left: 0, bottom: 28, right: 0)
        return layout
    }

    func ensureUnreadTimelineSidebarLayout() {
        guard let layout = leftCollectionView.collectionViewLayout as? UICollectionViewFlowLayout else { return }
        if isHomeTimeline {
            layout.minimumLineSpacing = 0
            layout.sectionInset = .zero
        } else {
            layout.minimumLineSpacing = 10
            layout.sectionInset = UIEdgeInsets(top: 2, left: 0, bottom: 20, right: 0)
        }
        layout.invalidateLayout()
    }

    func ensureVerticalMessageLayout(reloadData: Bool = false) {
        ensureUnreadTimelineSidebarLayout()
        let layout: UICollectionViewFlowLayout
        if let currentLayout = messageCollectionView.collectionViewLayout as? UICollectionViewFlowLayout {
            layout = currentLayout
        } else {
            layout = Self.makeMessageLayout() as! UICollectionViewFlowLayout
            messageCollectionView.setCollectionViewLayout(layout, animated: false)
        }
        let directionChanged = layout.scrollDirection != .vertical
        if directionChanged {
            layout.scrollDirection = .vertical
        }
        layout.estimatedItemSize = .zero
        layout.minimumLineSpacing = isHomeTimeline ? 0 : 2
        layout.sectionInset = isHomeTimeline
            ? .zero
            : UIEdgeInsets(top: 8, left: 0, bottom: 28, right: 0)
        layout.invalidateLayout()
        if reloadData || directionChanged {
            messageCollectionView.reloadData()
        }
    }
}
