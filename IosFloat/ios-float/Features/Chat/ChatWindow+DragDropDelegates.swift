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


extension ChatWindowViewController: UICollectionViewDragDelegate, UICollectionViewDropDelegate {
    func collectionView(
        _ collectionView: UICollectionView,
        itemsForBeginning session: UIDragSession,
        at indexPath: IndexPath
    ) -> [UIDragItem] {
        guard collectionView === messageCollectionView,
              let message = renderedMessage(at: indexPath),
              canShowContextMenu(for: message)
        else { return [] }

        let provider = NSItemProvider(object: messageDragSummary(for: message) as NSString)
        let item = UIDragItem(itemProvider: provider)
        item.localObject = message.id
        return [item]
    }

    func collectionView(_ collectionView: UICollectionView, dragSessionWillBegin session: UIDragSession) {
        guard collectionView === messageCollectionView,
              session.items.contains(where: { $0.localObject is UUID })
        else { return }
        showMessageTrashDropTarget()
    }

    func collectionView(_ collectionView: UICollectionView, dragSessionDidEnd session: UIDragSession) {
        guard collectionView === messageCollectionView else { return }
        hideMessageTrashDropTarget(animated: true)
        clearMessageDropFeedback(animated: true)
    }

    func collectionView(
        _ collectionView: UICollectionView,
        dropSessionDidUpdate session: UIDropSession,
        withDestinationIndexPath destinationIndexPath: IndexPath?
    ) -> UICollectionViewDropProposal {
        guard collectionView === leftCollectionView else {
            clearMessageDropFeedback(animated: true)
            return UICollectionViewDropProposal(operation: .forbidden)
        }
        let target = dropTargetParticipantInfo(at: destinationIndexPath, session: session)
        guard collectionView === leftCollectionView,
              let messageID = draggedMessageID(from: session),
              let message = state.messages.first(where: { $0.id == messageID }),
              canForwardMessage(message),
              let target
        else {
            clearMessageDropFeedback(animated: true)
            return UICollectionViewDropProposal(operation: .forbidden)
        }
        updateMessageDropFeedback(target: target.participant, indexPath: target.indexPath, session: session)
        return UICollectionViewDropProposal(operation: .copy, intent: .insertIntoDestinationIndexPath)
    }

    func collectionView(_ collectionView: UICollectionView, dropSessionDidExit session: UIDropSession) {
        if collectionView === leftCollectionView {
            clearMessageDropFeedback(animated: true)
        }
    }

    func collectionView(_ collectionView: UICollectionView, dropSessionDidEnd session: UIDropSession) {
        if collectionView === leftCollectionView {
            clearMessageDropFeedback(animated: true)
        }
    }

    func collectionView(
        _ collectionView: UICollectionView,
        performDropWith coordinator: UICollectionViewDropCoordinator
    ) {
        clearMessageDropFeedback(animated: false)
        guard collectionView === leftCollectionView,
              let messageID = draggedMessageID(from: coordinator.session),
              let message = state.messages.first(where: { $0.id == messageID }),
              canForwardMessage(message),
              let target = dropTargetParticipantInfo(at: coordinator.destinationIndexPath, session: coordinator.session)?.participant
        else {
            showNotice("该类型消息不支持转发，可拖到垃圾桶删除记录")
            return
        }

        forwardDraggedMessage(message, to: target)
    }

    func draggedMessageID(from session: UIDropSession) -> UUID? {
        session.localDragSession?.items.compactMap { $0.localObject as? UUID }.first
    }

    func dropTargetParticipantInfo(at indexPath: IndexPath?, session: UIDropSession) -> (participant: ChatParticipant, indexPath: IndexPath)? {
        let resolvedIndexPath = indexPath ?? leftCollectionView.indexPathForItem(at: session.location(in: leftCollectionView))
        guard let resolvedIndexPath,
              let leftItem = visibleLeftItemsForSelectedAccount()[safe: resolvedIndexPath.item],
              let participantID = leftItem.participantID,
              let participant = state.participants.first(where: { $0.id == participantID }),
              isForwardTargetAllowed(participant)
        else { return nil }
        return (participant, resolvedIndexPath)
    }

    func updateMessageDropFeedback(target: ChatParticipant, indexPath: IndexPath, session: UIDropSession) {
        if messageDropHighlightedIndexPath != indexPath {
            clearMessageDropCellHighlight(animated: true)
            messageDropHighlightedIndexPath = indexPath
            if let cell = leftCollectionView.cellForItem(at: indexPath) {
                UIView.animate(withDuration: 0.14, delay: 0, options: [.allowUserInteraction, .beginFromCurrentState]) {
                    cell.transform = CGAffineTransform(scaleX: 1.16, y: 1.16)
                    cell.contentView.layer.borderWidth = 3
                    cell.contentView.layer.borderColor = UIColor(red: 0.47, green: 0.83, blue: 0.08, alpha: 1).cgColor
                    cell.layer.shadowColor = UIColor(red: 0.47, green: 0.83, blue: 0.08, alpha: 1).cgColor
                    cell.layer.shadowOpacity = 0.42
                    cell.layer.shadowRadius = 10
                    cell.layer.shadowOffset = .zero
                }
                UIImpactFeedbackGenerator(style: .light).impactOccurred()
            }
        }
        showMessageDropHint(target: target, indexPath: indexPath, session: session)
    }

    func showMessageDropHint(target: ChatParticipant, indexPath: IndexPath, session: UIDropSession) {
        guard let window = view.window else { return }

        let hintView: UIView
        let label: UILabel
        if let existingView = messageDropHintView, let existingLabel = messageDropHintLabel {
            hintView = existingView
            label = existingLabel
        } else {
            let blur = UIVisualEffectView(effect: UIBlurEffect(style: .systemThinMaterialLight))
            blur.layer.cornerRadius = 13
            blur.layer.cornerCurve = .continuous
            blur.clipsToBounds = true
            blur.layer.borderWidth = 1
            blur.layer.borderColor = UIColor(red: 0.47, green: 0.83, blue: 0.08, alpha: 0.68).cgColor

            let newLabel = UILabel()
            newLabel.font = .systemFont(ofSize: 13, weight: .bold)
            newLabel.textColor = UIColor(red: 0.07, green: 0.20, blue: 0.14, alpha: 1)
            newLabel.numberOfLines = 1
            newLabel.translatesAutoresizingMaskIntoConstraints = false
            blur.contentView.addSubview(newLabel)
            NSLayoutConstraint.activate([
                newLabel.leadingAnchor.constraint(equalTo: blur.contentView.leadingAnchor, constant: 12),
                newLabel.trailingAnchor.constraint(equalTo: blur.contentView.trailingAnchor, constant: -12),
                newLabel.topAnchor.constraint(equalTo: blur.contentView.topAnchor, constant: 8),
                newLabel.bottomAnchor.constraint(equalTo: blur.contentView.bottomAnchor, constant: -8)
            ])
            window.addSubview(blur)
            messageDropHintView = blur
            messageDropHintLabel = newLabel
            hintView = blur
            label = newLabel
        }

        label.text = "松手转发给 \(displayName(for: target))"
        label.sizeToFit()
        let locationInWindow = window.convert(session.location(in: leftCollectionView), from: leftCollectionView)
        let width = min(max(label.intrinsicContentSize.width + 24, 128), 210)
        let height: CGFloat = 34
        let x = min(max(locationInWindow.x + 18, 8), window.bounds.width - width - 8)
        let y = min(max(locationInWindow.y - height - 58, 8), window.bounds.height - height - 8)
        UIView.animate(withDuration: 0.1, delay: 0, options: [.allowUserInteraction, .beginFromCurrentState]) {
            hintView.frame = CGRect(x: x, y: y, width: width, height: height)
            hintView.alpha = 1
        }
    }

    func clearMessageDropFeedback(animated: Bool) {
        clearMessageDropCellHighlight(animated: animated)
        guard let hintView = messageDropHintView else { return }
        let cleanup = {
            hintView.removeFromSuperview()
            if self.messageDropHintView === hintView {
                self.messageDropHintView = nil
                self.messageDropHintLabel = nil
            }
        }
        if animated {
            UIView.animate(withDuration: 0.14, animations: {
                hintView.alpha = 0
            }, completion: { _ in cleanup() })
        } else {
            cleanup()
        }
    }

    func clearMessageDropCellHighlight(animated: Bool) {
        guard let indexPath = messageDropHighlightedIndexPath else { return }
        messageDropHighlightedIndexPath = nil
        guard let cell = leftCollectionView.cellForItem(at: indexPath) else { return }
        let reset = {
            cell.transform = .identity
            cell.layer.shadowOpacity = 0
            cell.layer.shadowRadius = 0
            if let item = self.visibleLeftItemsForSelectedAccount()[safe: indexPath.item] {
                cell.contentView.layer.borderWidth = item.isActive ? 2 : 1
                cell.contentView.layer.borderColor = item.isActive
                    ? UIColor.white.withAlphaComponent(0.92).cgColor
                    : UIColor.white.withAlphaComponent(0.58).cgColor
            }
        }
        if animated {
            UIView.animate(withDuration: 0.14, delay: 0, options: [.allowUserInteraction, .beginFromCurrentState], animations: reset)
        } else {
            reset()
        }
    }

    func showMessageTrashDropTarget() {
        guard let window = view.window else { return }
        let trashView: UIVisualEffectView
        if let existingView = messageTrashDropView {
            trashView = existingView
        } else {
            let blur = UIVisualEffectView(effect: UIBlurEffect(style: .systemUltraThinMaterialDark))
            blur.layer.cornerRadius = 20
            blur.layer.cornerCurve = .continuous
            blur.clipsToBounds = true
            blur.layer.borderWidth = 1
            blur.layer.borderColor = UIColor.white.withAlphaComponent(0.20).cgColor
            blur.backgroundColor = UIColor.black.withAlphaComponent(0.22)

            let iconView = UIImageView(image: makeTrashDropIcon(open: false))
            iconView.tintColor = .white
            iconView.contentMode = .scaleAspectFit
            iconView.translatesAutoresizingMaskIntoConstraints = false

            blur.contentView.addSubview(iconView)
            NSLayoutConstraint.activate([
                iconView.centerXAnchor.constraint(equalTo: blur.contentView.centerXAnchor),
                iconView.centerYAnchor.constraint(equalTo: blur.contentView.centerYAnchor),
                iconView.widthAnchor.constraint(equalToConstant: 40),
                iconView.heightAnchor.constraint(equalToConstant: 40)
            ])

            blur.addInteraction(UIDropInteraction(delegate: self))
            window.addSubview(blur)
            messageTrashDropView = blur
            messageTrashDropIconView = iconView
            trashView = blur
        }

        let size = CGSize(width: 68, height: 68)
        let safeInsets = window.safeAreaInsets
        trashView.frame = CGRect(
            x: window.bounds.maxX - safeInsets.right - size.width,
            y: window.bounds.maxY - safeInsets.bottom - size.height - 8,
            width: size.width,
            height: size.height
        )
        trashView.alpha = 0
        trashView.transform = CGAffineTransform(scaleX: 0.86, y: 0.86)
        setMessageTrashDropHighlighted(false, animated: false)
        UIView.animate(withDuration: 0.18, delay: 0, options: [.allowUserInteraction, .beginFromCurrentState]) {
            trashView.alpha = 1
            trashView.transform = .identity
        }
    }

    func hideMessageTrashDropTarget(animated: Bool) {
        guard let trashView = messageTrashDropView else { return }
        let cleanup = {
            trashView.removeFromSuperview()
            if self.messageTrashDropView === trashView {
                self.messageTrashDropView = nil
                self.messageTrashDropIconView = nil
                self.messageTrashDropLidView = nil
                self.messageTrashDropLabel = nil
            }
        }
        let changes = {
            trashView.alpha = 0
            trashView.transform = CGAffineTransform(scaleX: 0.86, y: 0.86)
        }
        if animated {
            UIView.animate(withDuration: 0.16, delay: 0, options: [.allowUserInteraction, .beginFromCurrentState], animations: changes) { _ in
                cleanup()
            }
        } else {
            changes()
            cleanup()
        }
    }

    func setMessageTrashDropHighlighted(_ highlighted: Bool, animated: Bool) {
        guard let trashView = messageTrashDropView else { return }
        messageTrashDropIconView?.image = makeTrashDropIcon(open: highlighted)
        let changes = {
            trashView.effect = UIBlurEffect(style: highlighted ? .systemThinMaterialDark : .systemUltraThinMaterialDark)
            trashView.backgroundColor = highlighted
                ? UIColor.systemRed.withAlphaComponent(0.36)
                : UIColor.black.withAlphaComponent(0.22)
            trashView.layer.borderColor = highlighted
                ? UIColor.systemRed.withAlphaComponent(0.72).cgColor
                : UIColor.white.withAlphaComponent(0.20).cgColor
            trashView.transform = highlighted ? CGAffineTransform(scaleX: 1.08, y: 1.08) : .identity
        }
        if animated {
            UIView.animate(withDuration: 0.12, delay: 0, options: [.allowUserInteraction, .beginFromCurrentState], animations: changes)
        } else {
            changes()
        }
    }

    func makeTrashDropIcon(open: Bool) -> UIImage {
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: 44, height: 44))
        return renderer.image { context in
            let cgContext = context.cgContext
            cgContext.setStrokeColor(UIColor.white.cgColor)
            cgContext.setFillColor(UIColor.white.withAlphaComponent(0.12).cgColor)
            cgContext.setLineWidth(2.7)
            cgContext.setLineCap(.round)
            cgContext.setLineJoin(.round)

            let body = UIBezierPath()
            body.move(to: CGPoint(x: 13.5, y: 16.5))
            body.addLine(to: CGPoint(x: 30.5, y: 16.5))
            body.addLine(to: CGPoint(x: 28.2, y: 35.5))
            body.addQuadCurve(to: CGPoint(x: 25.5, y: 38), controlPoint: CGPoint(x: 28, y: 37.2))
            body.addLine(to: CGPoint(x: 18.5, y: 38))
            body.addQuadCurve(to: CGPoint(x: 15.8, y: 35.5), controlPoint: CGPoint(x: 16, y: 37.2))
            body.close()
            body.fill()
            body.stroke()

            for x in [18.5, 22, 25.5] {
                let line = UIBezierPath()
                line.move(to: CGPoint(x: x, y: 21))
                line.addLine(to: CGPoint(x: x - 0.7, y: 33.2))
                line.stroke()
            }

            let lid = UIBezierPath()
            if open {
                lid.move(to: CGPoint(x: 12.5, y: 12.8))
                lid.addLine(to: CGPoint(x: 30.5, y: 6.6))
                lid.move(to: CGPoint(x: 18.3, y: 10.8))
                lid.addLine(to: CGPoint(x: 24.2, y: 8.8))
            } else {
                lid.move(to: CGPoint(x: 11.2, y: 13.5))
                lid.addLine(to: CGPoint(x: 32.8, y: 13.5))
                lid.move(to: CGPoint(x: 18.5, y: 10.5))
                lid.addLine(to: CGPoint(x: 25.5, y: 10.5))
            }
            lid.stroke()
        }.withRenderingMode(.alwaysOriginal)
    }
}

extension ChatWindowViewController: UIDropInteractionDelegate {
    func dropInteraction(_ interaction: UIDropInteraction, canHandle session: UIDropSession) -> Bool {
        draggedMessageID(from: session) != nil
    }

    func dropInteraction(_ interaction: UIDropInteraction, sessionDidEnter session: UIDropSession) {
        setMessageTrashDropHighlighted(true, animated: true)
        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
    }

    func dropInteraction(_ interaction: UIDropInteraction, sessionDidExit session: UIDropSession) {
        setMessageTrashDropHighlighted(false, animated: true)
    }

    func dropInteraction(_ interaction: UIDropInteraction, sessionDidEnd session: UIDropSession) {
        hideMessageTrashDropTarget(animated: true)
    }

    func dropInteraction(_ interaction: UIDropInteraction, sessionDidUpdate session: UIDropSession) -> UIDropProposal {
        guard draggedMessageID(from: session) != nil else {
            return UIDropProposal(operation: .forbidden)
        }
        return UIDropProposal(operation: .move)
    }

    func dropInteraction(_ interaction: UIDropInteraction, performDrop session: UIDropSession) {
        guard let messageID = draggedMessageID(from: session),
              let message = state.messages.first(where: { $0.id == messageID })
        else {
            hideMessageTrashDropTarget(animated: true)
            return
        }
        hideMessageTrashDropTarget(animated: false)
        clearMessageDropFeedback(animated: false)
        deleteMessage(message)
    }
}


extension ChatWindowViewController {
    func presentMomentsPage() {
        let controller = MomentsKit.makeMomentsViewController(
            context: MomentsKitContext(
                state: state,
                accountContextsByID: openApiAccountContextsByID,
                conversationContextsByID: openApiConversationContextsByID,
                displayNameForAccount: { [weak self] accountID in
                    self?.displayNameForAccount(id: accountID) ?? "当前帐号"
                },
                displayNameForParticipant: { [weak self] participant in
                    self?.displayName(for: participant) ?? participant.displayName
                }
            )
        )
        controller.onOpenLink = { [weak self] link in
            self?.presentLinkJumpConfirmation(urlText: link)
        }
        controller.onNotice = { [weak self] text in
            self?.showNotice(text)
        }
        pushDetailController(controller)
    }

    func presentRichMediaDetail(_ interaction: RichMediaInteraction, authorized: Bool) {
        if interaction.kind == .image,
           let url = URL(string: interaction.url),
           url.isFileURL,
           let image = UIImage(contentsOfFile: url.path) {
            let controller = FullscreenMediaPreviewController.image(
                image.trimmedTransparentCanvasIfNeeded(),
                title: interaction.title
            )
            controller.modalPresentationStyle = .fullScreen
            present(controller, animated: true)
            return
        }
        let mode: RichContentDetailViewController.Mode = .webLink(url: normalizedURL(from: interaction.url) ?? URL(string: "https://cc2.cx")!)
        let controller = RichContentDetailViewController(
            mode: mode,
            titleText: interaction.title,
            subtitleText: "\(interaction.kind == .image ? "高清图片" : "文件预览") · \(interaction.access.rawValue)",
            detailText: "\(authorized ? "已授权查看" : "未授权，仅显示模糊预览")\n\n\(interaction.preview)\n\n访问链接：\(interaction.url)\n\n这是本地模拟交互：不连接服务端，通过 App 内状态演示申请、授权、高清查看/文件预览流程。"
        )
        controller.onPrimaryAction = { [weak self] in
            self?.presentLinkJumpConfirmation(urlText: interaction.url)
        }
        pushDetailController(controller)
    }

    func profileForInlinePersonalCard(_ card: InlineCardInteraction) -> ContactCardProfile {
        if let participant = state.participants.first(where: {
            card.title.localizedCaseInsensitiveContains($0.displayName)
                || $0.displayName.localizedCaseInsensitiveContains(card.title)
        }) {
            return contactCardProfile(for: participant)
        }

        return ContactCardProfile(
            accountID: UUID(),
            displayName: card.title,
            role: card.subtitle.isEmpty ? "个人名片" : card.subtitle,
            company: "",
            wechatID: card.url,
            phone: "",
            bio: "来自聊天消息的个人名片，可通过链接继续查看：\(card.url)",
            styleIndex: 0
        )
    }

    func inlineMiniProgramMessage(from card: InlineCardInteraction) -> ChatMessage {
        let sender = outgoingSenderForSelectedAccount()
        return ChatMessage(
            id: UUID(),
            conversationID: state.activeFriend?.id ?? state.currentUser.id,
            type: .miniProgram,
            sender: sender,
            body: card.title,
            detail: "\(card.subtitle)\n\(card.url)",
            isOutgoing: true,
            presentation: .bare,
            timestamp: currentTimestamp(),
            sentAt: Date(),
            isGroupConversation: state.activeFriend.map(isGroupConversation) ?? false,
            recipientAccountID: sender.id
        )
    }

    func persistedTemporaryURL(for sourceURL: URL, fallbackExtension: String) -> URL {
        let fileExtension = sourceURL.pathExtension.isEmpty ? fallbackExtension : sourceURL.pathExtension
        let baseName = sourceURL.deletingPathExtension().lastPathComponent
            .trimmingCharacters(in: .whitespacesAndNewlines)
        let safeBaseName = baseName.isEmpty
            ? UUID().uuidString
            : String(baseName.prefix(80))
                .replacingOccurrences(of: "/", with: "_")
                .replacingOccurrences(of: "\\", with: "_")
        let destination = FileManager.default.temporaryDirectory
            .appendingPathComponent("\(safeBaseName)-\(UUID().uuidString)")
            .appendingPathExtension(fileExtension)

        do {
            if sourceURL.startAccessingSecurityScopedResource() {
                defer { sourceURL.stopAccessingSecurityScopedResource() }
                try FileManager.default.copyItem(at: sourceURL, to: destination)
            } else {
                try FileManager.default.copyItem(at: sourceURL, to: destination)
            }
            return destination
        } catch {
            return sourceURL
        }
    }

    func persistedVoiceRecordingURL(for sourceURL: URL) -> URL? {
        let fileExtension = sourceURL.pathExtension.isEmpty ? "m4a" : sourceURL.pathExtension
        do {
            let directory = try voiceRecordingDirectory()
            let destination = directory
                .appendingPathComponent("voice-\(UUID().uuidString)")
                .appendingPathExtension(fileExtension)
            if FileManager.default.fileExists(atPath: destination.path) {
                try FileManager.default.removeItem(at: destination)
            }
            try FileManager.default.copyItem(at: sourceURL, to: destination)
            try? FileManager.default.removeItem(at: sourceURL)
            return destination
        } catch {
            return nil
        }
    }

    func mediaConversionDirectory() throws -> URL {
        let directory = FileManager.default
            .urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("MediaConversions", isDirectory: true)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true, attributes: nil)
        return directory
    }

    func normalizedMP4VideoURL(for sourceURL: URL) async throws -> URL {
        if isRemoteURL(sourceURL) || sourceURL.pathExtension.lowercased() == "mp4" {
            return sourceURL
        }

        let didAccessSecurityScope = sourceURL.startAccessingSecurityScopedResource()
        defer {
            if didAccessSecurityScope {
                sourceURL.stopAccessingSecurityScopedResource()
            }
        }

        let outputURL = try mediaConversionDirectory()
            .appendingPathComponent("video-\(UUID().uuidString)")
            .appendingPathExtension("mp4")
        if FileManager.default.fileExists(atPath: outputURL.path) {
            try FileManager.default.removeItem(at: outputURL)
        }

        let asset = AVURLAsset(url: sourceURL)
        let exportSession = [
            AVAssetExportPresetMediumQuality,
            AVAssetExportPresetHighestQuality,
            AVAssetExportPresetPassthrough
        ]
        .compactMap { AVAssetExportSession(asset: asset, presetName: $0) }
        .first { $0.supportedFileTypes.contains(.mp4) }
        guard let exportSession else {
            throw OpenApiMediaSendError.taskResultUnknown("处理视频", "当前视频编码不支持导出 MP4。")
        }

        exportSession.outputURL = outputURL
        exportSession.outputFileType = .mp4
        exportSession.shouldOptimizeForNetworkUse = true

        let exportBox = UnsafeSendableBox(exportSession)
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            exportBox.value.exportAsynchronously {
                let exportSession = exportBox.value
                switch exportSession.status {
                case .completed:
                    continuation.resume()
                case .failed:
                    continuation.resume(throwing: exportSession.error ?? OpenApiMediaSendError.taskResultUnknown("处理视频", "MP4 转换失败。"))
                case .cancelled:
                    continuation.resume(throwing: OpenApiMediaSendError.taskResultUnknown("处理视频", "MP4 转换已取消。"))
                default:
                    continuation.resume(throwing: OpenApiMediaSendError.taskResultUnknown("处理视频", "MP4 转换未完成。"))
                }
            }
        }

        return outputURL
    }

    func voiceRecordingDirectory() throws -> URL {
        let directory = FileManager.default
            .urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("VoiceRecordings", isDirectory: true)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true, attributes: nil)
        return directory
    }

    func saveImageToTemporaryFile(_ image: UIImage) -> URL? {
        guard let data = image.jpegData(compressionQuality: 0.88) else { return nil }
        let destination = FileManager.default.temporaryDirectory
            .appendingPathComponent(UUID().uuidString)
            .appendingPathExtension("jpg")
        do {
            try data.write(to: destination, options: .atomic)
            return destination
        } catch {
            return nil
        }
    }

    func saveTransparentImageToTemporaryFile(_ image: UIImage) -> URL? {
        guard let data = image.pngData() else { return nil }
        let destination = FileManager.default.temporaryDirectory
            .appendingPathComponent(UUID().uuidString)
            .appendingPathExtension("png")
        do {
            try data.write(to: destination, options: .atomic)
            return destination
        } catch {
            return nil
        }
    }

    func startVoiceRecording() {
        requestMicrophoneAccess { [weak self] granted in
            guard let self else { return }
            guard self.isVoicePressActive else {
                self.hideVoiceRecordingPrompt()
                return
            }
            guard granted else {
                self.hideVoiceRecordingPrompt()
                self.showSettingsNotice("\u{9700}\u{8981}\u{9ea6}\u{514b}\u{98ce}\u{6743}\u{9650}")
                return
            }

            do {
                let session = AVAudioSession.sharedInstance()
                try session.setCategory(.playAndRecord, mode: .default, options: [.defaultToSpeaker])
                try session.setActive(true)
                let recording = try self.makeVoiceRecorderForVoiceUpload()
                self.audioRecorder = recording.recorder
                if self.audioRecorder?.record() != true {
                    try? FileManager.default.removeItem(at: recording.url)
                    let fallback = try self.makeVoiceRecorderForVoiceUpload()
                    self.audioRecorder = fallback.recorder
                    guard self.audioRecorder?.record() == true else {
                        throw OpenApiMediaSendError.taskResultUnknown("录音", "录音启动失败。")
                    }
                    self.audioRecordingURL = fallback.url
                } else {
                    self.audioRecordingURL = recording.url
                }
                self.audioRecordingStartedAt = Date()
                self.inputBar.voiceButton.tintColor = .white
                self.inputBar.voiceButton.backgroundColor = UIColor.systemRed.withAlphaComponent(0.78)
                self.inputBar.voiceButton.setImage(UIImage(systemName: "mic.fill"), for: .normal)
                self.showVoiceRecordingPrompt()
            } catch {
                self.hideVoiceRecordingPrompt()
                self.showNotice("\u{5f55}\u{97f3}\u{5931}\u{8d25}\u{ff1a}\(error.localizedDescription)")
            }
        }
    }

    func makeVoiceRecorderForVoiceUpload() throws -> (recorder: AVAudioRecorder, url: URL) {
        let wavURL = FileManager.default.temporaryDirectory
            .appendingPathComponent("voice-\(UUID().uuidString)")
            .appendingPathExtension("wav")
        let wavSettings: [String: Any] = [
            AVFormatIDKey: Int(kAudioFormatLinearPCM),
            AVSampleRateKey: 16_000,
            AVNumberOfChannelsKey: 1,
            AVLinearPCMBitDepthKey: 16,
            AVLinearPCMIsFloatKey: false,
            AVLinearPCMIsBigEndianKey: false,
            AVLinearPCMIsNonInterleaved: false
        ]
        return (try preparedVoiceRecorder(url: wavURL, settings: wavSettings), wavURL)
    }

    func preparedVoiceRecorder(url: URL, settings: [String: Any]) throws -> AVAudioRecorder {
        let recorder = try AVAudioRecorder(url: url, settings: settings)
        guard recorder.prepareToRecord() else {
            throw OpenApiMediaSendError.taskResultUnknown("录音", "当前音频格式无法准备录制。")
        }
        return recorder
    }

    func finishVoiceRecording() {
        audioRecorder?.stop()
        audioRecorder = nil
        resetVoiceButtonAppearance()
        hideVoiceRecordingPrompt()

        let elapsed = Date().timeIntervalSince(audioRecordingStartedAt ?? Date())
        let duration = max(1, Int(elapsed.rounded()))
        let recordedURL = audioRecordingURL
        audioRecordingURL = nil
        audioRecordingStartedAt = nil
        guard let recordedURL else {
            showNotice("没有录到语音")
            return
        }
        if elapsed < 0.8 {
            try? FileManager.default.removeItem(at: recordedURL)
            showNotice("说话时间太短")
            return
        }
        let finalURL = persistedVoiceRecordingURL(for: recordedURL) ?? recordedURL
        insertOutgoingMessage(
            type: .voice,
            body: "\(duration) \u{79d2}\u{8bed}\u{97f3}",
            detail: "\u{672c}\u{5730}\u{5f55}\u{5236}\u{7684}\u{8bed}\u{97f3}\n\u{65f6}\u{957f}\u{ff1a}\(duration)\u{79d2}",
            attachmentURL: finalURL
        )
    }

    func cancelVoiceRecording(shouldShowNotice: Bool) {
        let recordedURL = audioRecordingURL
        audioRecorder?.stop()
        audioRecorder = nil
        audioRecordingURL = nil
        audioRecordingStartedAt = nil
        resetVoiceButtonAppearance()
        hideVoiceRecordingPrompt()
        try? recordedURL.map { try FileManager.default.removeItem(at: $0) }
        if shouldShowNotice {
            self.showNotice("已取消语音")
        }
    }

    func showVoiceRecordingPrompt() {
        voiceRecordingPromptView.layer.removeAllAnimations()
        voiceRecordingPromptView.isHidden = false
        UIView.animate(withDuration: 0.15) {
            self.voiceRecordingPromptView.alpha = 1
        }
    }

    func hideVoiceRecordingPrompt() {
        guard !voiceRecordingPromptView.isHidden || voiceRecordingPromptView.alpha > 0 else { return }
        voiceRecordingPromptView.layer.removeAllAnimations()
        UIView.animate(withDuration: 0.15, animations: {
            self.voiceRecordingPromptView.alpha = 0
        }, completion: { _ in
            self.voiceRecordingPromptView.isHidden = true
        })
    }

    func resetVoiceButtonAppearance() {
        inputBar.voiceButton.setImage(UIImage(systemName: "mic"), for: .normal)
        inputBar.voiceButton.tintColor = UIColor(red: 0.12, green: 0.14, blue: 0.15, alpha: 1)
        inputBar.voiceButton.backgroundColor = .clear
    }

    func requestCameraAccess(_ completion: @escaping (Bool) -> Void) {
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized:
            completion(true)
        case .notDetermined:
            AVCaptureDevice.requestAccess(for: .video) { granted in
                DispatchQueue.main.async { completion(granted) }
            }
        default:
            completion(false)
        }
    }

    func requestMicrophoneAccess(_ completion: @escaping (Bool) -> Void) {
        AVAudioSession.sharedInstance().requestRecordPermission { granted in
            DispatchQueue.main.async { completion(granted) }
        }
    }

    func presentSheet(_ alert: UIAlertController, sourceView: UIView) {
        if let popover = alert.popoverPresentationController {
            popover.sourceView = sourceView
            popover.sourceRect = sourceView.bounds
            popover.permittedArrowDirections = [.down, .up]
        }
        present(alert, animated: true)
    }

    func showNotice(_ message: String) {
        guard isViewLoaded else { return }
        activeNoticeDismissWorkItem?.cancel()
        activeNoticeView?.removeFromSuperview()

        let hostView = navigationController?.view ?? view!
        let noticeView = UIVisualEffectView(effect: UIBlurEffect(style: .systemThinMaterialDark))
        noticeView.layer.cornerRadius = 16
        noticeView.layer.cornerCurve = .continuous
        noticeView.clipsToBounds = true
        noticeView.layer.borderWidth = 0.5
        noticeView.layer.borderColor = UIColor.white.withAlphaComponent(0.18).cgColor
        noticeView.translatesAutoresizingMaskIntoConstraints = false

        let label = UILabel()
        label.text = message
        label.textColor = .white
        label.font = .systemFont(ofSize: 14, weight: .semibold)
        label.textAlignment = .center
        label.numberOfLines = 2
        label.translatesAutoresizingMaskIntoConstraints = false

        noticeView.contentView.addSubview(label)
        hostView.addSubview(noticeView)
        activeNoticeView = noticeView

        NSLayoutConstraint.activate([
            noticeView.topAnchor.constraint(equalTo: hostView.safeAreaLayoutGuide.topAnchor, constant: 14),
            noticeView.centerXAnchor.constraint(equalTo: hostView.centerXAnchor),
            noticeView.leadingAnchor.constraint(greaterThanOrEqualTo: hostView.leadingAnchor, constant: 24),
            noticeView.trailingAnchor.constraint(lessThanOrEqualTo: hostView.trailingAnchor, constant: -24),
            label.topAnchor.constraint(equalTo: noticeView.contentView.topAnchor, constant: 12),
            label.leadingAnchor.constraint(equalTo: noticeView.contentView.leadingAnchor, constant: 18),
            label.trailingAnchor.constraint(equalTo: noticeView.contentView.trailingAnchor, constant: -18),
            label.bottomAnchor.constraint(equalTo: noticeView.contentView.bottomAnchor, constant: -12),
            label.widthAnchor.constraint(lessThanOrEqualToConstant: 286)
        ])

        hostView.layoutIfNeeded()
        noticeView.alpha = 0
        noticeView.transform = CGAffineTransform(translationX: 0, y: -16)
        UIView.animate(withDuration: 0.18, delay: 0, options: [.curveEaseOut]) {
            noticeView.alpha = 1
            noticeView.transform = .identity
        }

        let dismissWorkItem = DispatchWorkItem { [weak self, weak noticeView] in
            guard let noticeView else { return }
            UIView.animate(withDuration: 0.2, delay: 0, options: [.curveEaseIn]) {
                noticeView.alpha = 0
                noticeView.transform = CGAffineTransform(translationX: 0, y: -12)
            } completion: { _ in
                if self?.activeNoticeView === noticeView {
                    self?.activeNoticeView = nil
                }
                noticeView.removeFromSuperview()
            }
        }
        activeNoticeDismissWorkItem = dismissWorkItem
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.6, execute: dismissWorkItem)
    }

    func showMessageActionNotice(_ message: String) {
        let alert = UIAlertController(title: message, message: nil, preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "知道了", style: .default))
        present(alert, animated: true)
    }

    func presentBlinkVoiceTest() {
        let controller = BlinkVoiceKitTestViewController()
        let navigationController = UINavigationController(rootViewController: controller)
        navigationController.modalPresentationStyle = .formSheet
        presentTopMost(navigationController)
    }

    func startBlinkVoiceCapture() {
        let presenter = topMostPresenter()
        let options = BlinkCaptureOptions(
            autoFinishOnEvent: true,
            eventTypes: Set(BlinkEventType.allCases)
        )
        BlinkVoiceCapture.start(from: presenter, options: options) { [weak self] result in
            DispatchQueue.main.async {
                guard let result else {
                    self?.showMessageActionNotice(
                        """
                        SDK 已返回空结果
                        result = nil

                        可能原因：
                        用户取消、相机权限拒绝、相机不可用，或 SDK 未识别到事件。
                        """
                    )
                    return
                }

                let eventName: String
                switch result.eventType {
                case .singleBlink:
                    eventName = "单次眨眼"
                case .doubleBlink:
                    eventName = "快速眨两下"
                case .longClose:
                    eventName = "闭眼"
                }

                self?.showMessageActionNotice(
                    """
                    SDK 返回识别结果

                    识别类型：\(eventName)
                    eventType：\(result.eventType.rawValue)
                    startTimeMs：\(result.startTimeMs)
                    endTimeMs：\(result.endTimeMs)
                    durationMs：\(result.durationMs)
                    confidence：\(result.confidence)
                    """
                )
            }
        }
    }

    func presentTopMost(_ controller: UIViewController) {
        topMostPresenter().present(controller, animated: true)
    }

    func topMostPresenter() -> UIViewController {
        var presenter: UIViewController = self
        while let presented = presenter.presentedViewController,
              !presented.isBeingDismissed {
            presenter = presented
        }
        if let navigationController = presenter as? UINavigationController,
           let visibleViewController = navigationController.visibleViewController {
            return visibleViewController
        }
        if let tabBarController = presenter as? UITabBarController,
           let selectedViewController = tabBarController.selectedViewController {
            return selectedViewController
        }
        return presenter
    }

    func presentNoticeDetail(for message: ChatMessage) {
        let title = message.type == .groupNotice ? "群公告详情" : "通知详情"
        let details = [
            message.body,
            message.detail.isEmpty ? nil : message.detail,
            "时间：\(message.displayTimestamp)"
        ].compactMap { $0 }
        let alert = UIAlertController(
            title: title,
            message: details.joined(separator: "\n\n"),
            preferredStyle: .alert
        )
        alert.addAction(UIAlertAction(title: "知道了", style: .default))
        present(alert, animated: true)
    }

    func presentCallConfirmation(for message: ChatMessage, isVideo: Bool) {
        if isVideo {
            let controller = VideoCallViewController(
                localParticipant: state.currentUser,
                remoteParticipants: [message.sender],
                isGroup: message.isGroupConversation
            )
            controller.onEndCall = { [weak self] duration in
                guard let self else { return }
                self.insertOutgoingMessage(
                    type: .videoCall,
                    body: "视频通话 \(self.formattedCallDuration(duration))",
                    detail: "通话对象：\(message.sender.displayName)"
                )
            }
            controller.modalPresentationStyle = .fullScreen
            present(controller, animated: true)
            return
        }

        let controller = VoiceCallViewController(
            localParticipant: state.currentUser,
            remoteParticipants: [message.sender],
            isGroup: message.isGroupConversation
        )
        controller.onEndCall = { [weak self] duration in
            guard let self else { return }
            self.insertOutgoingMessage(
                type: .voiceCall,
                body: "语音通话 \(self.formattedCallDuration(duration))",
                detail: "通话对象：\(message.sender.displayName)"
            )
        }
        controller.modalPresentationStyle = .fullScreen
        present(controller, animated: true)
    }

    func showSettingsNotice(_ message: String) {
        let alert = UIAlertController(title: message, message: "\u{8bf7}\u{5728}\u{7cfb}\u{7edf}\u{8bbe}\u{7f6e}\u{4e2d}\u{5141}\u{8bb8}\u{8bbf}\u{95ee}", preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "\u{53d6}\u{6d88}", style: .cancel))
        alert.addAction(UIAlertAction(title: "\u{53bb}\u{8bbe}\u{7f6e}", style: .default) { _ in
            guard let url = URL(string: UIApplication.openSettingsURLString) else { return }
            UIApplication.shared.open(url)
        })
        present(alert, animated: true)
    }

    func presentQuickPhraseSheet(for sourceText: String) {
        let alert = UIAlertController(title: "快捷语", message: "选择快捷语，或把当前文字保存为快捷语", preferredStyle: .actionSheet)

        quickPhrases.prefix(6).forEach { phrase in
            alert.addAction(UIAlertAction(title: phrase, style: .default) { [weak self] _ in
                self?.applyQuickPhrase(phrase)
            })
        }

        alert.addAction(UIAlertAction(title: "设置为快捷语", style: .default) { [weak self] _ in
            self?.saveQuickPhrase(sourceText)
        })
        alert.addAction(UIAlertAction(title: "新增快捷语", style: .default) { [weak self] _ in
            self?.presentAddQuickPhraseAlert(prefilledText: "")
        })
        alert.addAction(UIAlertAction(title: "取消", style: .cancel))
        presentSheet(alert, sourceView: messageCollectionView)
    }

    func presentQuickPhraseManager(sourceView: UIView) {
        let alert = UIAlertController(title: "快捷语管理", message: "选择快捷语填入输入框，或新增、删除快捷语", preferredStyle: .actionSheet)

        quickPhrases.prefix(8).forEach { phrase in
            alert.addAction(UIAlertAction(title: phrase, style: .default) { [weak self] _ in
                self?.applyQuickPhrase(phrase)
            })
        }

        alert.addAction(UIAlertAction(title: "新增快捷语", style: .default) { [weak self] _ in
            self?.presentAddQuickPhraseAlert(prefilledText: "")
        })
        alert.addAction(UIAlertAction(title: "删除快捷语", style: quickPhrases.isEmpty ? .default : .destructive) { [weak self] _ in
            self?.presentDeleteQuickPhraseSheet(sourceView: sourceView)
        })
        alert.addAction(UIAlertAction(title: "取消", style: .cancel))
        presentSheet(alert, sourceView: sourceView)
    }

    func presentDeleteQuickPhraseSheet(sourceView: UIView) {
        let alert = UIAlertController(title: "删除快捷语", message: quickPhrases.isEmpty ? "暂无快捷语" : "选择要删除的快捷语", preferredStyle: .actionSheet)

        quickPhrases.forEach { phrase in
            alert.addAction(UIAlertAction(title: phrase, style: .destructive) { [weak self] _ in
                self?.deleteQuickPhrase(phrase)
            })
        }

        alert.addAction(UIAlertAction(title: "取消", style: .cancel))
        presentSheet(alert, sourceView: sourceView)
    }

    func loadQuickPhrases() {
        let saved = ChatSQLiteStore.shared.stringArray(forKey: Self.quickPhraseStorageKey)
            ?? UserDefaults.standard.stringArray(forKey: Self.quickPhraseStorageKey)
            ?? []
        let merged = saved.reduce(into: [String]()) { phrases, phrase in
            let trimmed = phrase.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !trimmed.isEmpty, !phrases.contains(trimmed) else { return }
            phrases.append(trimmed)
        }
        quickPhrases = merged
    }

    func persistQuickPhrases() {
        ChatSQLiteStore.shared.setStringArray(quickPhrases, forKey: Self.quickPhraseStorageKey)
    }

    func loadUserFavoriteItems() {
        let decoded = ChatSQLiteStore.shared.codable([String: [FavoriteShareItem]].self, forKey: Self.userFavoriteItemsStorageKey)
            ?? UserDefaults.standard.data(forKey: Self.userFavoriteItemsStorageKey)
                .flatMap { try? JSONDecoder().decode([String: [FavoriteShareItem]].self, from: $0) }
            ?? [:]
        userFavoriteItemsByAccountID = decoded.reduce(into: [UUID: [FavoriteShareItem]]()) { result, entry in
            guard let accountID = UUID(uuidString: entry.key) else { return }
            result[accountID] = entry.value
        }
    }

    func persistUserFavoriteItems() {
        let encoded = userFavoriteItemsByAccountID.reduce(into: [String: [FavoriteShareItem]]()) { result, entry in
            result[entry.key.uuidString] = entry.value
        }
        ChatSQLiteStore.shared.setCodable(encoded, forKey: Self.userFavoriteItemsStorageKey)
    }

    func applyQuickPhrase(_ phrase: String) {
        inputBar.textField.text = phrase
        inputBar.textField.becomeFirstResponder()
    }

    func saveQuickPhrase(_ phrase: String) {
        let trimmed = phrase.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        if let existingIndex = quickPhrases.firstIndex(of: trimmed) {
            quickPhrases.remove(at: existingIndex)
        }
        quickPhrases.insert(trimmed, at: 0)
        persistQuickPhrases()
        showNotice("已设为快捷语")
    }

    func deleteQuickPhrase(_ phrase: String) {
        quickPhrases.removeAll { $0 == phrase }
        persistQuickPhrases()
        showNotice("已删除快捷语")
    }

    func presentAddQuickPhraseAlert(prefilledText: String) {
        let alert = UIAlertController(title: "新增快捷语", message: nil, preferredStyle: .alert)
        alert.addTextField { textField in
            textField.placeholder = "输入快捷语"
            textField.text = prefilledText
        }
        alert.addAction(UIAlertAction(title: "取消", style: .cancel))
        alert.addAction(UIAlertAction(title: "保存", style: .default) { [weak self, weak alert] _ in
            let text = alert?.textFields?.first?.text ?? ""
            self?.saveQuickPhrase(text)
        })
        present(alert, animated: true)
    }

    @discardableResult
    func setRightAccountHeightRatio(_ ratio: CGFloat, animated: Bool) -> Bool {
        let clampedRatio = min(max(ratio, minimumRightAccountHeightRatio()), maximumRightAccountHeightRatio())
        guard abs(clampedRatio - rightAccountHeightRatio) > 0.002 else { return false }
        rightAccountHeightRatio = clampedRatio
        updateRightRailHeightConstraint()

        let changes = {
            self.rightRailContainer.layoutIfNeeded()
            self.updateConnections()
        }
        if animated {
            UIView.animate(withDuration: 0.22, delay: 0, options: [.curveEaseOut], animations: changes)
        } else {
            UIView.performWithoutAnimation(changes)
        }
        return true
    }

    func updateRightRailHeightConstraint() {
        let railHeight = max(rightRailContainer.bounds.height, 1)
        let contentHeight = rightAccountContentHeight(maxHeight: railHeight)
        let fixedControlsHeight = rightRailFixedControlsHeight()
        let minimumAccountHeight = rightAccountListHeight(forVisibleItems: minimumRightAccountItemsVisible)
        let minimumToolHeight = rightRailListHeight(forVisibleItems: minimumRightToolItemsVisible)
        let maximumAccountHeight = max(minimumAccountHeight, railHeight - fixedControlsHeight - minimumToolHeight)
        let proportionalHeight = railHeight * rightAccountHeightRatio
        let targetHeight = min(max(proportionalHeight, minimumAccountHeight), maximumAccountHeight, contentHeight)

        accountRailHeightConstraint?.constant = targetHeight
        if !rightAccountCollectionView.isDragging && !rightAccountCollectionView.isDecelerating {
            DispatchQueue.main.async { [weak self] in
                self?.ensureSelectedRightAccountItemVisible(animated: false)
            }
        }
    }

    func rightAccountContentHeight(maxHeight: CGFloat) -> CGFloat {
        let itemCount = CGFloat(max(visibleRightAccountItems().count, 1))
        let itemHeight = Self.rightAccountItemSize.height
        let lineSpacing: CGFloat = 10
        let topInset: CGFloat = 6
        let bottomInset: CGFloat = 2
        let height = topInset + itemCount * itemHeight + max(0, itemCount - 1) * lineSpacing + bottomInset
        return height
    }

    func rightRailListHeight(forVisibleItems visibleItems: CGFloat) -> CGFloat {
        let itemHeight = Self.sidebarItemSize.height
        let lineSpacing: CGFloat = 10
        let topInset: CGFloat = 2
        let bottomInset: CGFloat = 20
        return topInset + visibleItems * itemHeight + max(0, visibleItems - 1) * lineSpacing + bottomInset
    }

    func rightAccountListHeight(forVisibleItems visibleItems: CGFloat) -> CGFloat {
        let itemHeight = Self.rightAccountItemSize.height
        let lineSpacing: CGFloat = 10
        let topInset: CGFloat = 6
        let bottomInset: CGFloat = 2
        return topInset + visibleItems * itemHeight + max(0, visibleItems - 1) * lineSpacing + bottomInset
    }

    func rightRailFixedControlsHeight() -> CGFloat {
        40
    }

    func minimumRightAccountHeightRatio() -> CGFloat {
        let railHeight = max(rightRailContainer.bounds.height, 1)
        return min(0.65, rightAccountListHeight(forVisibleItems: minimumRightAccountItemsVisible) / railHeight)
    }

    func maximumRightAccountHeightRatio() -> CGFloat {
        let railHeight = max(rightRailContainer.bounds.height, 1)
        let available = railHeight - rightRailFixedControlsHeight() - rightRailListHeight(forVisibleItems: minimumRightToolItemsVisible)
        return max(minimumRightAccountHeightRatio(), min(0.92, available / railHeight))
    }

    func updateRightRailHeightForScroll(_ scrollView: UIScrollView) -> Bool {
        guard scrollView === rightAccountCollectionView || scrollView === rightToolCollectionView,
              scrollView.isDragging
        else { return false }

        let translation = scrollView.panGestureRecognizer.translation(in: rightRailContainer).y
        guard shouldResizeRightRail(for: scrollView, translationY: translation) else { return false }

        if scrollView === rightAccountCollectionView {
            return setRightAccountHeightRatio(maximumRightAccountHeightRatio(), animated: true)
        }

        // Match the account rail interaction: the first upward swipe gives the
        // tool list all available space while keeping the account minimum visible.
        return setRightAccountHeightRatio(minimumRightAccountHeightRatio(), animated: true)
    }

    func shouldResizeRightRail(for scrollView: UIScrollView, translationY: CGFloat) -> Bool {
        let triggerDistance: CGFloat = 6
        guard abs(translationY) > triggerDistance else { return false }

        if scrollView === rightAccountCollectionView {
            return translationY < 0
        }
        return translationY < 0
    }

    @objc func handleWindowPan(_ gesture: UIPanGestureRecognizer) {
        switch gesture.state {
        case .began:
            lastDragCenter = chromeView.center
        case .changed:
            let translation = gesture.translation(in: view)
            chromeView.center = CGPoint(
                x: lastDragCenter.x + translation.x,
                y: lastDragCenter.y + translation.y
            )
            frameConstraints.forEach { $0.isActive = false }
        case .ended, .cancelled:
            clampChromeFrame(animated: true)
        default:
            break
        }
    }

    @objc func handleResizePan(_ gesture: UIPanGestureRecognizer) {
        switch gesture.state {
        case .began:
            lastSize = chromeView.bounds.size
            frameConstraints.forEach { $0.isActive = false }
        case .changed:
            let translation = gesture.translation(in: view)
            let safe = view.safeAreaLayoutGuide.layoutFrame.insetBy(dx: 8, dy: 8)
            var frame = chromeView.frame
            frame.size.width = min(max(lastSize.width + translation.x, 310), safe.width)
            frame.size.height = min(max(lastSize.height + translation.y, 360), safe.height)
            frame.origin.x = min(frame.origin.x, safe.maxX - frame.width)
            frame.origin.y = min(frame.origin.y, safe.maxY - frame.height)
            chromeView.frame = frame
            updateConnections()
        case .ended, .cancelled:
            clampChromeFrame(animated: true)
        default:
            break
        }
    }

    func applyWindowState(_ state: WindowState, animated: Bool) {
        windowState = state
        frameConstraints.forEach { $0.isActive = false }

        switch state {
        case .expanded:
            chromeView.isHidden = false
            chromeView.alpha = 1
            chromeView.transform = .identity
            chromeView.visualEffectView.alpha = 1
            chromeView.resizeHandle.alpha = 0
            widthConstraint = chromeView.widthAnchor.constraint(equalTo: view.widthAnchor)
            heightConstraint = chromeView.heightAnchor.constraint(equalTo: view.heightAnchor)
            heightConstraint?.priority = .defaultHigh
            centerXConstraint = chromeView.centerXAnchor.constraint(equalTo: view.centerXAnchor)
            topConstraint = chromeView.topAnchor.constraint(equalTo: view.topAnchor)
            bottomConstraint = chromeView.bottomAnchor.constraint(equalTo: view.bottomAnchor)
            frameConstraints = [widthConstraint, heightConstraint, centerXConstraint, topConstraint, bottomConstraint].compactMap { $0 }
            NSLayoutConstraint.activate(frameConstraints)
        case .compact:
            chromeView.isHidden = false
            chromeView.alpha = 1
            chromeView.transform = .identity
            chromeView.visualEffectView.alpha = 1
            chromeView.resizeHandle.alpha = 0
            widthConstraint = chromeView.widthAnchor.constraint(equalTo: view.widthAnchor)
            heightConstraint = chromeView.heightAnchor.constraint(equalTo: view.heightAnchor)
            heightConstraint?.priority = .defaultHigh
            centerXConstraint = chromeView.centerXAnchor.constraint(equalTo: view.centerXAnchor)
            topConstraint = chromeView.topAnchor.constraint(equalTo: view.topAnchor)
            bottomConstraint = chromeView.bottomAnchor.constraint(equalTo: view.bottomAnchor)
            frameConstraints = [widthConstraint, heightConstraint, centerXConstraint, topConstraint, bottomConstraint].compactMap { $0 }
            NSLayoutConstraint.activate(frameConstraints)
        case .minimized:
            chromeView.isHidden = false
            chromeView.alpha = 1
            chromeView.transform = .identity
            chromeView.visualEffectView.alpha = 1
            chromeView.resizeHandle.alpha = 0
            widthConstraint = chromeView.widthAnchor.constraint(equalToConstant: 148)
            heightConstraint = chromeView.heightAnchor.constraint(equalToConstant: 36)
            centerXConstraint = chromeView.centerXAnchor.constraint(equalTo: view.centerXAnchor)
            topConstraint = chromeView.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 0)
            frameConstraints = [widthConstraint, heightConstraint, centerXConstraint, topConstraint].compactMap { $0 }
            NSLayoutConstraint.activate(frameConstraints)
        }

        let changes = {
            self.updateMinimizedChromeVisibility()
            self.view.layoutIfNeeded()
        }
        if animated {
            UIView.animate(withDuration: 0.28, delay: 0, usingSpringWithDamping: 0.86, initialSpringVelocity: 0.5) {
                changes()
            } completion: { _ in
                self.updateConnections()
            }
        } else {
            changes()
        }
    }

    func updateMinimizedChromeVisibility() {
        let isMinimized = windowState == .minimized
        headerView.alpha = isMinimized ? 0 : 1
        leftCollectionView.alpha = isMinimized ? 0 : 1
        messageCollectionView.alpha = isMinimized ? 0 : 1
        rightRailContainer.alpha = isMinimized ? 0 : 1
        connectionOverlay.alpha = isMinimized ? 0 : 1
        inputBar.alpha = isMinimized ? 0 : 1
        inputBarBottomFillView.alpha = isMinimized ? 0 : 1
        localIslandIconView.isHidden = !isMinimized
        localIslandIconView.alpha = isMinimized ? 1 : 0
        chromeView.visualEffectView.contentView.backgroundColor = isMinimized ? .black : .clear
        chromeView.layer.borderColor = isMinimized
            ? UIColor.black.cgColor
            : UIColor.white.withAlphaComponent(0.55).cgColor
        chromeView.closeButton.isUserInteractionEnabled = !isMinimized
        chromeView.resizeHandle.isUserInteractionEnabled = !isMinimized
        if !isMinimized {
            chromeView.closeButton.isHidden = true
            chromeView.resizeHandle.isHidden = true
        }
    }

    func clampChromeFrame(animated: Bool) {
        let safe = view.safeAreaLayoutGuide.layoutFrame.insetBy(dx: 8, dy: 8)
        var frame = chromeView.frame
        frame.size.width = min(max(frame.width, 310), safe.width)
        frame.size.height = min(max(frame.height, 360), safe.height)
        frame.origin.x = min(max(frame.origin.x, safe.minX), safe.maxX - frame.width)
        frame.origin.y = min(max(frame.origin.y, safe.minY), safe.maxY - frame.height)

        let updates = {
            self.chromeView.frame = frame
            self.updateConnections()
        }
        if animated {
            UIView.animate(withDuration: 0.22, delay: 0, options: [.curveEaseOut], animations: updates)
        } else {
            updates()
        }
    }

    func scrollToMessage(id: UUID, animated: Bool = true) {
        if isHomeTimeline, renderedIndex(of: id) == nil {
            homeRenderedMessageLimit = homeRenderableMessageCount()
            messageCollectionView.reloadData()
            messageCollectionView.layoutIfNeeded()
        }
        guard let index = renderedIndex(of: id) else { return }
        let indexPath = IndexPath(item: index, section: 0)
        messageCollectionView.scrollToItem(at: indexPath, at: .centeredVertically, animated: animated)
        DispatchQueue.main.asyncAfter(deadline: .now() + (animated ? 0.28 : 0)) {
            self.pulseMessage(at: indexPath)
            self.scheduleConnectionUpdate()
        }
    }

    func updateHeaderForSelection() {
        setMessageHeaderCompact(false, animated: false)
        let selectedAccountName = displayNameForAccount(id: state.selectedAccountID)
        accountButton.setTitle(selectedAccountName, for: .normal)
        accountButton.setImage(nil, for: .normal)
        accountButton.titleLabel?.font = .systemFont(ofSize: 11, weight: .semibold)
        accountButton.titleLabel?.lineBreakMode = .byTruncatingTail
        accountButton.accessibilityLabel = localized("当前帐号：\(selectedAccountName)", "Current account: \(selectedAccountName)")
        titleSubtitleLabel.text = nil
        titleSubtitleLabel.isHidden = true
        titleSubtitleLabel.textColor = UIColor.white.withAlphaComponent(0.68)
        headerNameButton.setTitle(localized("返回", "Back"), for: .normal)
        headerNameButton.isHidden = false
        headerUnreadBadgeView.isHidden = true
        headerConversationEditButton.isHidden = true
        if let friend = state.activeFriend {
            headerConversationEditButton.isHidden = false
            let friendName = displayName(for: friend)
            if isGroupConversation(friend) {
                let remark = participantRemarksByID[friend.id]?
                    .trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
                let realGroupName = openApiConversationContextsByID[friend.id]?.displayName
                    .trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
                let resolvedRealName = realGroupName.isEmpty ? friend.displayName : realGroupName
                let memberCount = openApiConversationContextsByID[friend.id]?.memberCount
                    ?? groupMembers(for: friend).count
                let countSuffix = memberCount > 0 ? "（\(memberCount)）" : ""
                titleLabel.text = "\(remark.isEmpty ? resolvedRealName : remark)\(countSuffix)"
                if !remark.isEmpty, !resolvedRealName.isEmpty, resolvedRealName != remark {
                    titleSubtitleLabel.text = resolvedRealName
                    titleSubtitleLabel.isHidden = false
                }
            } else {
                titleLabel.text = friendName
                if !isHomeTimeline {
                    let unreadCount = pendingReplyHomePresentation().items.filter {
                        $0.conversationID == friend.id
                    }.count
                    headerNameButton.setTitle(
                        localized("未回\(unreadCount > 0 ? " \(unreadCount)" : "")", "Unreplied\(unreadCount > 0 ? " \(unreadCount)" : "")"),
                        for: .normal
                    )
                    headerUnreadBadgeView.isHidden = unreadCount == 0
                }
            }
            inputBar.textField.placeholder = localized("发送给\(friendName)", "Send to \(friendName)")
        } else if isAccountDirectory {
            let accountName = displayNameForAccount(id: state.selectedAccountID)
            titleLabel.text = localized("\(accountName)的好友与群聊", "\(accountName)'s Chats")
            inputBar.textField.placeholder = localized(
                "选择好友或群聊后即可主动发送",
                "Choose a chat to send a message"
            )
        } else {
            if let accountID = unansweredAccountFilterID {
                let accountName = displayNameForAccount(id: accountID)
                titleLabel.text = localized("\(accountName)的未回消息", "\(accountName)'s Unreplied")
            } else {
                titleLabel.text = localized("全部未回消息", "All Unreplied Messages")
            }
            switch openApiIMBootstrapState {
            case .loading where !hasLoadedOpenApiIMSnapshot:
                titleSubtitleLabel.text = localized("同步中", "Syncing")
                titleSubtitleLabel.isHidden = false
                inputBar.textField.placeholder = localized("正在拉取真实帐号和好友", "Loading real accounts and chats")
            case .failed:
                titleSubtitleLabel.text = localized("同步失败", "Sync Failed")
                titleSubtitleLabel.isHidden = false
                inputBar.textField.placeholder = localized("点击右侧重试同步", "Tap retry on the right")
            case .empty:
                titleSubtitleLabel.text = localized("暂无帐号", "No Accounts")
                titleSubtitleLabel.isHidden = false
                inputBar.textField.placeholder = localized("请先添加或同步帐号", "Add or sync an account first")
            default:
                let presentation = pendingReplyHomePresentation()
                let pendingCount = presentation.pendingConversationAccountCount
                titleSubtitleLabel.text = localized(
                    "\(pendingCount) 条待回复",
                    "\(pendingCount) Pending"
                )
                titleSubtitleLabel.textColor = UIColor.white.withAlphaComponent(0.88)
                titleSubtitleLabel.isHidden = false
                if let target = selectedUnansweredSendTarget() {
                    let conversationName = displayName(for: target.conversation)
                    inputBar.textField.placeholder = localized(
                        "回复给\(conversationName)",
                        "Reply to \(conversationName)"
                    )
                } else {
                    inputBar.textField.placeholder = localized(
                        "请选择一条未回消息后再发送",
                        "Select an unreplied message to send"
                    )
                }
            }
        }
        updateLeftFriendTotalBadge()
        updateComposerAvailability()
    }

    @objc func returnToUnreadMessages() {
        if state.selectedFriendID == nil {
            if isAccountDirectory {
                setUnansweredTimelineRoute(scope: .all, focus: nil)
                selectFriend(id: nil)
                return
            }
            if isHomeTimeline {
                guard let scope = chatWindowRoute.unansweredScope else { return }
                guard let destination = UnansweredRoutePlanner.destinationAfterTimelineBack(
                    from: scope
                ) else {
                    // The all-account timeline is the root of this navigation flow.
                    return
                }
                guard
                      case .accountDirectory(let accountID) = destination
                else { return }
                state.selectedAccountID = accountID
                chatWindowRoute = destination
                selectFriend(id: nil)
                return
            }
            return
        }
        if let directoryAccountID = chatWindowRoute.accountDirectoryID {
            state.selectedAccountID = directoryAccountID
            chatWindowRoute = .accountDirectory(accountID: directoryAccountID)
            selectFriend(id: nil)
        } else if let origin = unansweredNavigationOrigin {
            chatWindowRoute = UnansweredRoutePlanner.timelineAfterBack(from: chatWindowRoute)
            selectFriend(
                id: nil,
                focusMessageID: origin.sourceFocus.messageID,
                restoreHomeOffsetY: origin.contentOffsetY
            )
        } else {
            setUnansweredTimelineRoute(scope: .all, focus: nil)
            selectFriend(id: nil)
        }
    }


    func currentConversationID() -> UUID {
        state.selectedFriendID
            ?? selectedUnansweredSendTarget()?.item.conversationID
            ?? state.friends.first?.id
            ?? state.currentUser.id
    }

    func selectedUnansweredSendTarget() -> (item: UnansweredItem, accountID: UUID, conversation: ChatParticipant)? {
        guard isHomeTimeline,
              let selectedItemID = selectedUnansweredItemID,
              let item = pendingReplyHomePresentation().itemByID[selectedItemID],
              let conversation = participantLookupByID()[item.conversationID]
        else { return nil }

        let targetAccountIDs = Set(item.targetAccountIDs)
        let accountID = [state.selectedAccountID, unansweredAccountFilterID]
            .compactMap { $0 }
            .first(where: targetAccountIDs.contains)
            ?? state.currentUsers.lazy.map(\.id).first(where: targetAccountIDs.contains)
        guard let accountID else { return nil }
        return (item, accountID, conversation)
    }

    func unansweredReplyOriginForCurrentSelection() -> UnansweredNavigationOrigin? {
        guard let target = selectedUnansweredSendTarget(),
              let focus = chatWindowRoute.unansweredFocus
        else { return nil }
        let presentation = pendingReplyHomePresentation()
        let orderedItemIDs = presentation.items.map(\.id)
        let selectedIndex = orderedItemIDs.firstIndex(of: target.item.id) ?? 0
        return UnansweredNavigationOrigin(
            scope: chatWindowRoute.unansweredScope ?? .all,
            sourceFocus: focus,
            accountID: target.accountID,
            candidateItemIDs: Array(orderedItemIDs.dropFirst(selectedIndex + 1))
                + Array(orderedItemIDs.prefix(selectedIndex)),
            contentOffsetY: messageCollectionView.contentOffset.y,
            completesItemOnSend: true
        )
    }

    func registerUnansweredReplyOriginIfNeeded(for messageID: UUID) {
        guard unansweredReplyOriginsByOutgoingMessageID[messageID] == nil,
              let origin = unansweredReplyOriginForCurrentSelection()
        else { return }
        unansweredReplyOriginsByOutgoingMessageID[messageID] = origin
        if isHomeTimeline {
            unansweredCompletionSnapshotsByOutgoingMessageID[messageID] =
                UnansweredCompletionAnimationSnapshot(
                    messageIDs: renderedMessages.map(\.id),
                    leftParticipantIDs: visibleLeftItemsForSelectedAccount().compactMap(\.participantID),
                    selectedAccountID: state.selectedAccountID,
                    contentOffsetY: messageCollectionView.contentOffset.y
                )
        }
    }

    func selectRightAccount(accountID: UUID) {
        let signpostID = PerformanceSignpost.begin("SelectRightAccount")
        defer {
            PerformanceSignpost.end("SelectRightAccount", id: signpostID)
        }
        cancelLeftScrollPreview()

        if isHomeTimeline {
            let oldMessages = renderedMessages
            let oldLeftItems = visibleLeftItemsForSelectedAccount()
            let previousFilterID = unansweredAccountFilterID
            state.selectedAccountID = accountID
            chatWindowRoute = UnansweredRoutePlanner.timelineSelectingAccount(
                tappedAccountID: accountID
            )
            resetHomeMessageWindow()
            invalidateSelectionVisibleDataCaches()
            updateHeaderForSelection()
            if previousFilterID == nil || unansweredAccountFilterID == nil {
                rightAccountCollectionView.reloadData()
            } else {
                reloadRightAccountItems(
                    accountIDs: Set([previousFilterID, unansweredAccountFilterID].compactMap { $0 })
                )
            }
            applyUnreadTimelineFilterTransition(
                oldMessages: oldMessages,
                oldLeftItems: oldLeftItems
            ) { [weak self] in
                guard let self else { return }
                self.syncUnreadTimelineScroll(from: self.messageCollectionView)
                self.ensureSelectedRightAccountItemVisible(animated: true)
                self.scheduleConnectionUpdate()
            }
            return
        }

        if isAccountDirectory {
            state.selectedAccountID = accountID
            chatWindowRoute = .accountDirectory(accountID: accountID)
            selectFriend(id: nil)
            return
        }

        guard accountID != state.selectedAccountID else { return }
        if let currentFriend = state.activeFriend,
           isConversation(currentFriend, relatedToAccountID: accountID) {
            state.selectedAccountID = accountID
            selectFriend(id: currentFriend.id)
            return
        }
        let hasUnansweredMessages = pendingReplyHomePresentation().items.contains {
            $0.targetAccountIDs.contains(accountID)
        }
        state.selectedAccountID = accountID
        chatWindowRoute = UnansweredRoutePlanner.destinationSelectingAccount(
            tappedAccountID: accountID,
            hasUnansweredMessages: hasUnansweredMessages
        )
        selectFriend(id: nil)
    }

    func isRightAccountAvailable(_ accountID: UUID) -> Bool {
        guard let currentConversation = state.activeFriend else { return true }
        return isConversation(currentConversation, relatedToAccountID: accountID)
    }

    @discardableResult
    func requireActiveConversationForSending(showNotice shouldShowNotice: Bool = true) -> Bool {
        guard state.selectedFriendID != nil || (isHomeTimeline && selectedUnansweredItemID != nil) else {
            setEmojiPanelVisible(false, animated: true)
            setMentionPanelVisible(false, animated: true)
            view.endEditing(true)
            if shouldShowNotice {
                showNotice(isHomeTimeline ? "请先选择一条未回消息" : "请先选择好友或群聊")
            }
            return false
        }
        return true
    }

    func updateComposerAvailability() {
        let canSend = state.selectedFriendID != nil || (isHomeTimeline && selectedUnansweredItemID != nil)
        inputBar.textField.isEnabled = canSend
        inputBar.voiceButton.isEnabled = canSend
        inputBar.giftButton.isEnabled = canSend
        inputBar.minimizeButton.isEnabled = canSend
        inputBar.alpha = canSend ? 1 : 0.62
        if !canSend {
            inputBar.textField.text = nil
            clearPendingAttachmentIfNeeded()
        }
    }

    func clearPendingAttachmentIfNeeded() {
        guard !pendingAttachments.isEmpty || quotedMessage != nil else { return }
        pendingAttachments.removeAll()
        quotedMessage = nil
        quotePreviewLabel.text = nil
        quoteThumbnailView.image = nil
        quoteThumbnailView.isHidden = true
        quotePreviewHeightConstraint?.constant = 0
        quotePreviewBar.isHidden = true
    }

    var isActiveCodexConversation: Bool {
        state.activeFriend?.displayName == AIProvider.codex.title
    }

    func codexPromptIfMentioned(in text: String) -> String? {
        let codexMentionPattern = #"(?i)@codex(?![A-Za-z0-9_])"#
        guard text.range(of: codexMentionPattern, options: .regularExpression) != nil else { return nil }
        let prompt = text
            .replacingOccurrences(of: codexMentionPattern, with: "", options: .regularExpression)
            .trimmingCharacters(in: .whitespacesAndNewlines)
        return prompt.isEmpty ? "请根据当前聊天上下文回复。" : prompt
    }

    func isGroupParticipant(_ participant: ChatParticipant) -> Bool {
        participant.kind == .group
    }

    func setMultiSelecting(_ enabled: Bool, initialMessage: ChatMessage? = nil) {
        isMultiSelecting = enabled
        selectedMessageIDs.removeAll()
        if enabled, let initialMessage, initialMessage.type != .system {
            selectedMessageIDs.insert(initialMessage.id)
        }
        inputBar.isHidden = enabled
        multiSelectBar.isHidden = !enabled
        setEmojiPanelVisible(false, animated: false)
        setMentionPanelVisible(false, animated: false)
        updateMultiSelectButtons()
        messageCollectionView.reloadData()
    }

    func toggleSelectedMessage(_ message: ChatMessage) {
        guard message.type != .system else { return }
        if selectedMessageIDs.contains(message.id) {
            selectedMessageIDs.remove(message.id)
        } else {
            selectedMessageIDs.insert(message.id)
        }
        updateMultiSelectButtons()
        if let index = renderedIndex(of: message.id) {
            messageCollectionView.reloadItems(at: [IndexPath(item: index, section: 0)])
        }
    }

    func selectedMessagesInVisibleOrder() -> [ChatMessage] {
        state.visibleMessages.filter { selectedMessageIDs.contains($0.id) }
    }

    func updateMultiSelectButtons() {
        let hasSelection = !selectedMessageIDs.isEmpty
        let selected = selectedMessagesInVisibleOrder()
        let canForwardSelection = hasSelection && selected.allSatisfy(canForwardMessage)
        [multiForwardButton, multiMergeButton].forEach {
            $0.isEnabled = canForwardSelection
            $0.alpha = canForwardSelection ? 1 : 0.35
        }
        multiDeleteButton.isEnabled = hasSelection
        multiDeleteButton.alpha = hasSelection ? 1 : 0.35
    }

    func toggleSelectionAssistOverlay(sourceView: UIView) {
        if selectionAssistOverlayView != nil {
            hideSelectionAssistOverlay()
            return
        }

        let contentView = chromeView.visualEffectView.contentView
        let overlay = UIVisualEffectView(effect: UIBlurEffect(style: .systemThinMaterialLight))
        overlay.layer.cornerRadius = 18
        overlay.layer.cornerCurve = .continuous
        overlay.clipsToBounds = true
        overlay.layer.borderWidth = 1 / UIScreen.main.scale
        overlay.layer.borderColor = UIColor.black.withAlphaComponent(0.12).cgColor
        overlay.translatesAutoresizingMaskIntoConstraints = true
        let width: CGFloat = 236
        let height: CGFloat = 172
        let safeTop = contentView.safeAreaInsets.top
        overlay.frame = CGRect(
            x: max(12, contentView.bounds.width - width - 70),
            y: max(safeTop + 66, 84),
            width: width,
            height: height
        )

        let titleLabel = UILabel()
        titleLabel.text = "浮动选取"
        titleLabel.font = .systemFont(ofSize: 14, weight: .semibold)
        titleLabel.textColor = UIColor(red: 0.12, green: 0.15, blue: 0.17, alpha: 1)
        titleLabel.translatesAutoresizingMaskIntoConstraints = false

        let subtitleLabel = UILabel()
        subtitleLabel.text = "摇杆移动光标，页面不跟随滚动"
        subtitleLabel.font = .systemFont(ofSize: 11, weight: .medium)
        subtitleLabel.textColor = UIColor(red: 0.34, green: 0.42, blue: 0.46, alpha: 1)
        subtitleLabel.translatesAutoresizingMaskIntoConstraints = false

        let joystickView = SelectionAssistJoystickView()
        joystickView.translatesAutoresizingMaskIntoConstraints = false
        joystickView.onPan = { [weak self] translation in
            self?.moveSelectionAssistCursor(by: translation)
        }

        let selectButton = makeSelectionAssistButton(title: "选择", symbolName: "checkmark.circle", action: #selector(selectionAssistSelectTapped))
        let lassoButton = makeSelectionAssistButton(title: "套索", symbolName: "scope", action: #selector(selectionAssistLassoTapped))
        let closeButton = makeSelectionAssistButton(title: "关闭", symbolName: "xmark", action: #selector(selectionAssistCloseTapped))
        let buttonStack = UIStackView(arrangedSubviews: [selectButton, lassoButton, closeButton])
        buttonStack.axis = .horizontal
        buttonStack.distribution = .fillEqually
        buttonStack.spacing = 8
        buttonStack.translatesAutoresizingMaskIntoConstraints = false

        overlay.contentView.addSubview(titleLabel)
        overlay.contentView.addSubview(subtitleLabel)
        overlay.contentView.addSubview(joystickView)
        overlay.contentView.addSubview(buttonStack)
        contentView.addSubview(overlay)
        contentView.bringSubviewToFront(overlay)

        NSLayoutConstraint.activate([
            titleLabel.leadingAnchor.constraint(equalTo: overlay.contentView.leadingAnchor, constant: 14),
            titleLabel.topAnchor.constraint(equalTo: overlay.contentView.topAnchor, constant: 12),
            subtitleLabel.leadingAnchor.constraint(equalTo: titleLabel.leadingAnchor),
            subtitleLabel.trailingAnchor.constraint(lessThanOrEqualTo: joystickView.leadingAnchor, constant: -10),
            subtitleLabel.topAnchor.constraint(equalTo: titleLabel.bottomAnchor, constant: 4),

            joystickView.trailingAnchor.constraint(equalTo: overlay.contentView.trailingAnchor, constant: -14),
            joystickView.topAnchor.constraint(equalTo: overlay.contentView.topAnchor, constant: 12),
            joystickView.widthAnchor.constraint(equalToConstant: 74),
            joystickView.heightAnchor.constraint(equalToConstant: 74),

            buttonStack.leadingAnchor.constraint(equalTo: overlay.contentView.leadingAnchor, constant: 12),
            buttonStack.trailingAnchor.constraint(equalTo: overlay.contentView.trailingAnchor, constant: -12),
            buttonStack.bottomAnchor.constraint(equalTo: overlay.contentView.bottomAnchor, constant: -12),
            buttonStack.heightAnchor.constraint(equalToConstant: 38)
        ])

        let pan = UIPanGestureRecognizer(target: self, action: #selector(handleSelectionAssistPanelPan(_:)))
        pan.delegate = self
        overlay.addGestureRecognizer(pan)
        selectionAssistOverlayView = overlay
        showSelectionAssistCursorIfNeeded()
        rightToolCollectionView.reloadData()
        showMessageActionNotice("摇杆移动选点，点选择选中当前消息")
    }

    func makeSelectionAssistButton(title: String, symbolName: String, action: Selector) -> UIButton {
        let button = UIButton(type: .system)
        button.setTitle(title, for: .normal)
        button.setImage(UIImage(systemName: symbolName), for: .normal)
        button.tintColor = UIColor(red: 0.13, green: 0.36, blue: 0.52, alpha: 1)
        button.setTitleColor(UIColor(red: 0.13, green: 0.36, blue: 0.52, alpha: 1), for: .normal)
        button.titleLabel?.font = .systemFont(ofSize: 12, weight: .semibold)
        button.backgroundColor = UIColor.white.withAlphaComponent(0.76)
        button.layer.cornerRadius = 11
        button.layer.cornerCurve = .continuous
        button.semanticContentAttribute = .forceLeftToRight
        button.applyImagePadding(4)
        button.addTarget(self, action: action, for: .touchUpInside)
        return button
    }

    func hideSelectionAssistOverlay() {
        selectionAssistOverlayView?.removeFromSuperview()
        selectionAssistOverlayView = nil
        selectionAssistCursorView?.removeFromSuperview()
        selectionAssistCursorView = nil
        rightToolCollectionView.reloadData()
    }

    @objc func selectionAssistSelectTapped() {
        selectMessageAtSelectionAssistCursor()
    }

    @objc func selectionAssistLassoTapped() {
        hideSelectionAssistOverlay()
        beginSelectionLasso()
    }

    @objc func selectionAssistCloseTapped() {
        hideSelectionAssistOverlay()
    }

    @objc func handleSelectionAssistPanelPan(_ gesture: UIPanGestureRecognizer) {
        guard let panel = selectionAssistOverlayView,
              let host = panel.superview
        else { return }
        let translation = gesture.translation(in: host)
        panel.center = CGPoint(x: panel.center.x + translation.x, y: panel.center.y + translation.y)
        gesture.setTranslation(.zero, in: host)

        if gesture.state == .ended || gesture.state == .cancelled {
            let inset = host.safeAreaInsets
            let halfWidth = panel.bounds.width / 2
            let halfHeight = panel.bounds.height / 2
            let minX = inset.left + halfWidth + 8
            let maxX = host.bounds.width - inset.right - halfWidth - 8
            let minY = inset.top + halfHeight + 8
            let maxY = host.bounds.height - inset.bottom - halfHeight - 8
            let targetCenter = CGPoint(
                x: min(max(panel.center.x, minX), maxX),
                y: min(max(panel.center.y, minY), maxY)
            )
            UIView.animate(withDuration: 0.18, delay: 0, options: [.curveEaseOut, .allowUserInteraction]) {
                panel.center = targetCenter
            }
        }
    }

    func showSelectionAssistCursorIfNeeded() {
        guard selectionAssistCursorView == nil else { return }
        let host = messageCollectionView.superview ?? chromeView.visualEffectView.contentView
        let cursor = UIView(frame: CGRect(x: 0, y: 0, width: 48, height: 48))
        cursor.backgroundColor = UIColor(red: 0.16, green: 0.48, blue: 0.68, alpha: 0.16)
        cursor.layer.cornerRadius = 24
        cursor.layer.cornerCurve = .continuous
        cursor.layer.borderWidth = 2
        cursor.layer.borderColor = UIColor(red: 0.10, green: 0.38, blue: 0.56, alpha: 0.92).cgColor
        cursor.isUserInteractionEnabled = false

        let dot = UIView()
        dot.backgroundColor = UIColor(red: 0.10, green: 0.38, blue: 0.56, alpha: 1)
        dot.layer.cornerRadius = 4
        dot.translatesAutoresizingMaskIntoConstraints = false
        cursor.addSubview(dot)
        NSLayoutConstraint.activate([
            dot.centerXAnchor.constraint(equalTo: cursor.centerXAnchor),
            dot.centerYAnchor.constraint(equalTo: cursor.centerYAnchor),
            dot.widthAnchor.constraint(equalToConstant: 8),
            dot.heightAnchor.constraint(equalToConstant: 8)
        ])

        host.addSubview(cursor)
        host.bringSubviewToFront(cursor)
        let centerInHost = messageCollectionView.convert(
            CGPoint(x: messageCollectionView.bounds.midX, y: messageCollectionView.bounds.midY),
            to: host
        )
        cursor.center = centerInHost
        selectionAssistCursorView = cursor
    }

    func moveSelectionAssistCursor(by translation: CGPoint) {
        showSelectionAssistCursorIfNeeded()
        guard let cursor = selectionAssistCursorView,
              let host = cursor.superview
        else { return }
        let messageFrame = messageCollectionView.convert(messageCollectionView.bounds, to: host)
        let speed: CGFloat = 1.45
        let halfWidth = cursor.bounds.width / 2
        let halfHeight = cursor.bounds.height / 2
        let target = CGPoint(
            x: min(max(cursor.center.x + translation.x * speed, messageFrame.minX + halfWidth), messageFrame.maxX - halfWidth),
            y: min(max(cursor.center.y + translation.y * speed, messageFrame.minY + halfHeight), messageFrame.maxY - halfHeight)
        )
        cursor.center = target
    }

    func selectMessageAtSelectionAssistCursor() {
        showSelectionAssistCursorIfNeeded()
        guard let cursor = selectionAssistCursorView,
              let host = cursor.superview
        else {
            setMultiSelecting(true)
            showMessageActionNotice("已进入多选，点击消息进行选择")
            return
        }
        let pointInCollection = messageCollectionView.convert(cursor.center, from: host)
        let candidateIndexPath = messageCollectionView.indexPathForItem(at: pointInCollection)
            ?? messageCollectionView.collectionViewLayout
                .layoutAttributesForElements(in: CGRect(x: pointInCollection.x - 24, y: pointInCollection.y - 24, width: 48, height: 48))?
                .filter { $0.representedElementCategory == .cell }
                .min { abs($0.frame.midY - pointInCollection.y) < abs($1.frame.midY - pointInCollection.y) }?
                .indexPath

        guard let indexPath = candidateIndexPath,
              let message = renderedMessage(at: indexPath),
              message.type != .system
        else {
            showMessageActionNotice("当前光标下没有可选择消息")
            return
        }
        applySelectionAssistSelection(messageIDs: [message.id])
        UISelectionFeedbackGenerator().selectionChanged()
        showMessageActionNotice("已选择当前消息")
    }

    func beginSelectionLasso() {
        selectionAssistCursorView?.removeFromSuperview()
        selectionAssistCursorView = nil
        selectionLassoOverlayView?.removeFromSuperview()
        let overlay = SelectionLassoOverlayView()
        overlay.translatesAutoresizingMaskIntoConstraints = false
        overlay.onFinish = { [weak self] selectionRect in
            self?.finishSelectionLasso(selectionRect)
        }
        overlay.onCancel = { [weak self] in
            self?.cancelSelectionLasso()
        }

        let host = messageCollectionView.superview ?? chromeView.visualEffectView.contentView
        host.addSubview(overlay)
        NSLayoutConstraint.activate([
            overlay.leadingAnchor.constraint(equalTo: messageCollectionView.leadingAnchor),
            overlay.trailingAnchor.constraint(equalTo: messageCollectionView.trailingAnchor),
            overlay.topAnchor.constraint(equalTo: messageCollectionView.topAnchor),
            overlay.bottomAnchor.constraint(equalTo: messageCollectionView.bottomAnchor)
        ])
        selectionLassoOverlayView = overlay
        rightToolCollectionView.reloadData()
        showMessageActionNotice("在消息区域拖动框选")
    }

    func cancelSelectionLasso() {
        selectionLassoOverlayView?.removeFromSuperview()
        selectionLassoOverlayView = nil
        rightToolCollectionView.reloadData()
    }

    func finishSelectionLasso(_ rectInOverlay: CGRect) {
        guard let overlay = selectionLassoOverlayView else { return }
        let rectInCollection = messageCollectionView.convert(rectInOverlay, from: overlay)
        let attributes = messageCollectionView.collectionViewLayout.layoutAttributesForElements(in: rectInCollection) ?? []
        let ids = Set(attributes.compactMap { attributes -> UUID? in
            guard attributes.representedElementCategory == .cell,
                  let message = renderedMessage(at: attributes.indexPath),
                  message.type != .system,
                  rectInCollection.intersects(attributes.frame)
            else { return nil }
            return message.id
        })

        cancelSelectionLasso()
        guard !ids.isEmpty else {
            showMessageActionNotice("没有框选到可选择消息")
            return
        }
        applySelectionAssistSelection(messageIDs: ids)
        showMessageActionNotice("已选择 \(ids.count) 条消息")
    }

    func applySelectionAssistSelection(messageIDs: Set<UUID>) {
        if !isMultiSelecting {
            isMultiSelecting = true
            inputBar.isHidden = true
            multiSelectBar.isHidden = false
            setEmojiPanelVisible(false, animated: false)
            setMentionPanelVisible(false, animated: false)
        }
        selectedMessageIDs.formUnion(messageIDs)
        updateMultiSelectButtons()
        messageCollectionView.reloadData()
    }

    @objc func cancelMultiSelect() {
        setMultiSelecting(false)
    }

    @objc func forwardSelectedMessages() {
        let selected = selectedMessagesInVisibleOrder()
        guard !selected.isEmpty else { return }
        guard selected.allSatisfy(canForwardMessage) else {
            showNotice("选中的消息包含不能转发的类型，可删除记录")
            return
        }
        presentForwardTargetPicker(mode: .individual, messages: selected)
    }

    @objc func mergeForwardSelectedMessages() {
        let selected = selectedMessagesInVisibleOrder()
        guard !selected.isEmpty else { return }
        guard selected.allSatisfy(canForwardMessage) else {
            showNotice("选中的消息包含不能转发的类型，可删除记录")
            return
        }
        presentForwardTargetPicker(mode: .merged, messages: selected)
    }

    func presentForwardTargetPicker(mode: ForwardMode, messages: [ChatMessage]) {
        guard !messages.isEmpty, messages.allSatisfy(canForwardMessage) else {
            showNotice("该类型消息不支持转发，可拖到垃圾桶删除记录")
            return
        }
        let targets = forwardTargets()
        guard !targets.isEmpty else {
            showNotice("暂无可转发的好友或群聊")
            return
        }

        let controller = ForwardTargetPickerViewController(
            targets: targets,
            messageCount: messages.count,
            modeTitle: mode == .individual ? "逐条转发" : "合并转发"
        )
        controller.onSelect = { [weak self] target in
            guard let self else { return }
            self.navigationController?.popViewController(animated: true)
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.18) {
                self.resolveForwardModeAndSend(messages: messages, to: target, mode: mode)
            }
        }
        controller.onClose = { [weak self] in
            self?.navigationController?.popViewController(animated: true)
        }

        navigationItem.backBarButtonItem = UIBarButtonItem(title: "返回", style: .plain, target: nil, action: nil)
        if let navigationController {
            navigationController.setNavigationBarHidden(false, animated: false)
            navigationController.pushViewController(controller, animated: true)
        } else {
            let navigationController = UINavigationController(rootViewController: controller)
            navigationController.modalPresentationStyle = .fullScreen
            controller.onSelect = { [weak self, weak navigationController] target in
                navigationController?.dismiss(animated: true)
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.18) {
                    self?.resolveForwardModeAndSend(messages: messages, to: target, mode: mode)
                }
            }
            controller.onClose = { [weak navigationController] in
                navigationController?.dismiss(animated: true)
            }
            present(navigationController, animated: true)
        }
    }

    func resolveForwardModeAndSend(messages: [ChatMessage], to target: ChatParticipant, mode: ForwardMode) {
        forward(messages: messages, to: target, mode: mode)
    }

    func forwardTargets() -> [ChatParticipant] {
        forwardTargetItemsForSelectedAccount().compactMap { item in
            guard let participantID = item.participantID else { return nil }
            return state.participants.first { $0.id == participantID && !$0.isCurrentUser }
        }
    }

    func forwardTargetItemsForSelectedAccount() -> [SidebarItem] {
        let allItems = baseVisibleLeftItems()
        let accountID = state.selectedAccountID
        let assignableIDs = Set(allItems.compactMap { item -> UUID? in
            guard let participantID = item.participantID,
                  let participant = state.participants.first(where: { $0.id == participantID }),
                  !participant.isAIAccount
            else { return nil }
            return isConversation(participant, relatedToAccountID: accountID) ? participantID : nil
        })
        return allItems.filter { item in
            guard let participantID = item.participantID else { return false }
            return assignableIDs.contains(participantID)
        }
    }

    func isForwardTargetAllowed(_ participant: ChatParticipant) -> Bool {
        forwardTargetItemsForSelectedAccount().contains { $0.participantID == participant.id }
    }

    func isConversation(_ participant: ChatParticipant, relatedToAccountID accountID: UUID) -> Bool {
        if participant.isAIAccount {
            return true
        }
        if isGroupConversation(participant) || isGroupParticipant(participant) {
            return isGroup(participant, relatedToAccountID: accountID)
        }
        return isDirectConversation(participant, relatedToAccountID: accountID)
    }

    func isGroup(_ group: ChatParticipant, relatedToAccountID accountID: UUID) -> Bool {
        if let relatedAccountIDs = openApiRelatedAccountIDsByConversationID[group.id],
           !relatedAccountIDs.isEmpty {
            return relatedAccountIDs.contains(accountID)
        }
        let currentAccountIDs = Set(state.currentUsers.map(\.id))
        if let conversation = openApiConversationContextsByID[group.id],
           conversation.kind == .chatroom,
           let account = openApiAccountContextsByID[accountID] {
            return conversation.ownerWxid == account.wxid
        }
        if let explicitIDs = explicitGroupMemberIDsByGroupID[group.id], !explicitIDs.isEmpty {
            let removedIDs = removedGroupMemberIDsByGroupID[group.id] ?? []
            let explicitAccountIDs = Set(explicitIDs)
                .intersection(currentAccountIDs)
                .subtracting(removedIDs)
            return explicitAccountIDs.contains(accountID)
        }
        return state.messages.contains {
            $0.conversationID == group.id
                && $0.isGroupConversation
                && $0.sender.id == accountID
        }
    }

    func isDirectConversation(_ participant: ChatParticipant, relatedToAccountID accountID: UUID) -> Bool {
        if let relatedAccountIDs = openApiRelatedAccountIDsByConversationID[participant.id],
           !relatedAccountIDs.isEmpty {
            return relatedAccountIDs.contains(accountID)
        }
        if let conversation = openApiConversationContextsByID[participant.id],
           let account = openApiAccountContextsByID[accountID] {
            return conversation.ownerWxid == account.wxid
        }
        return FloatingIMConversationResolver.relatedAccountIDs(
            for: participant.id,
            messages: state.messages,
            accounts: state.currentUsers
        ).contains(accountID)
    }

    func accountIDForDirectConversation(_ participant: ChatParticipant, preferredAccountID: UUID) -> UUID? {
        guard !participant.isAIAccount, !isGroupConversation(participant) else { return nil }
        let relatedAccountIDs = directConversationAccountIDs(for: participant)
        guard !relatedAccountIDs.isEmpty else { return nil }
        if relatedAccountIDs.contains(preferredAccountID) {
            return preferredAccountID
        }
        return state.currentUsers.map(\.id).first { relatedAccountIDs.contains($0) }
            ?? relatedAccountIDs.sorted { $0.uuidString < $1.uuidString }.first
    }

    func directConversationAccountIDs(for participant: ChatParticipant) -> Set<UUID> {
        var resolvedAccountIDs = FloatingIMConversationResolver.relatedAccountIDs(
            for: participant.id,
            messages: state.messages,
            accounts: state.currentUsers
        )
        if let relatedAccountIDs = openApiRelatedAccountIDsByConversationID[participant.id],
           !relatedAccountIDs.isEmpty {
            resolvedAccountIDs.formUnion(relatedAccountIDs)
        }
        if let conversation = openApiConversationContextsByID[participant.id] {
            let accountIDs = openApiAccountContextsByID.values
                .filter { $0.wxid == conversation.ownerWxid }
                .map(\.participantID)
            resolvedAccountIDs.formUnion(accountIDs)
        }
        return resolvedAccountIDs
    }

    func isDirectMessage(_ message: ChatMessage, visibleForAccountID accountID: UUID) -> Bool {
        guard !message.isGroupConversation else { return false }
        if let recipientAccountID = message.recipientAccountID {
            return recipientAccountID == accountID
        }
        if message.sender.id == accountID {
            return true
        }
        if let conversation = state.participants.first(where: { $0.id == message.conversationID }) {
            return directConversationAccountIDs(for: conversation).contains(accountID)
        }
        return false
    }

    func detailValue(in detail: String, prefixes: [String]) -> String? {
        for line in detail.components(separatedBy: .newlines) {
            let trimmed = line.trimmingCharacters(in: .whitespacesAndNewlines)
            guard let prefix = prefixes.first(where: { trimmed.hasPrefix($0) }) else { continue }
            let value = String(trimmed.dropFirst(prefix.count)).trimmingCharacters(in: .whitespacesAndNewlines)
            if !value.isEmpty {
                return value
            }
        }
        return nil
    }

    func forward(messages: [ChatMessage], to target: ChatParticipant, mode: ForwardMode) {
        let newMessageIDs: [UUID]
        switch mode {
        case .individual:
            newMessageIDs = appendIndividualForwardMessages(messages, to: target)
        case .merged:
            newMessageIDs = [appendMergedForwardMessage(messages, to: target)]
        }
        persistMessages()
        sendOpenApiForwardedMessages(messageIDs: newMessageIDs)

        setMultiSelecting(false)
        focusForwardedMessages(in: target, messageID: newMessageIDs.last)
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.35) {
            self.showNotice(mode == .individual ? "已逐条转发给\(target.displayName)" : "已合并转发给\(target.displayName)")
        }
    }

    func sendOpenApiForwardedMessages(messageIDs: [UUID]) {
        let forwardedMessages = messageIDs.compactMap { id in
            state.messages.first { $0.id == id }
        }
        let jobs = forwardedMessages.compactMap { message -> (message: ChatMessage, context: (account: OpenApiWeChatAccountContext, conversation: OpenApiConversationContext))? in
            guard shouldAttemptOpenApiSend(message) else {
                setOpenApiDeliveryStatus(
                    for: message,
                    taskIDs: [],
                    state: .failed,
                    note: "该消息类型暂不支持 OpenAPI 批量转发真实发送。"
                )
                return nil
            }
            guard let context = openApiSendContext(for: message) else {
                setOpenApiDeliveryStatus(
                    for: message,
                    taskIDs: [],
                    state: .failed,
                    note: "当前转发目标没有匹配的 OpenAPI 帐号/会话，未发起真实批量转发。"
                )
                return nil
            }
            setOpenApiDeliveryStatus(
                for: message,
                taskIDs: [],
                state: .pending,
                note: message.type == .mergedForward
                    ? "合并转发已提交 /openapi/v1/messages/batch 聊天记录类型，等待任务终态。"
                    : "转发已提交 /openapi/v1/messages/batch，等待任务终态。"
            )
            return (message, context)
        }
        guard !jobs.isEmpty else { return }

        Task { @MainActor in
            for job in jobs {
                do {
                    let outcome = try await self.performOpenApiBatchForwardSend(
                        message: job.message,
                        account: job.context.account,
                        conversation: job.context.conversation
                    )
                    guard !outcome.sentKinds.isEmpty else {
                        self.setOpenApiDeliveryStatus(
                            for: job.message,
                            taskIDs: outcome.taskIDs,
                            state: .failed,
                            note: "该消息没有可通过批量接口发送的内容。"
                        )
                        continue
                    }
                    self.setOpenApiDeliveryStatus(
                        for: job.message,
                        taskIDs: outcome.taskIDs,
                        state: .succeeded,
                        note: "真实批量转发\(outcome.sentKinds.joined(separator: "、"))已完成。"
                    )
                } catch {
                    self.recordOpenApiSendFailure(error, for: job.message, retrying: false)
                }
            }
        }
    }

    func presentChannelsVideoForwardPicker(payload: ChannelsVideoForwardPayload) {
        let targets = forwardTargets()
        guard !targets.isEmpty else {
            showNotice("暂无可转发的好友或群聊")
            return
        }

        let controller = ForwardTargetPickerViewController(
            targets: targets,
            messageCount: 1,
            modeTitle: "转发视频号"
        )
        controller.onSelect = { [weak self] target in
            guard let self else { return }
            self.navigationController?.popViewController(animated: true)
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.18) {
                self.forwardChannelsVideo(payload, to: target)
            }
        }
        controller.onClose = { [weak self] in
            self?.navigationController?.popViewController(animated: true)
        }

        navigationItem.backBarButtonItem = UIBarButtonItem(title: "返回", style: .plain, target: nil, action: nil)
        if let navigationController {
            navigationController.setNavigationBarHidden(false, animated: false)
            navigationController.pushViewController(controller, animated: true)
        } else {
            let navigationController = UINavigationController(rootViewController: controller)
            navigationController.modalPresentationStyle = .fullScreen
            controller.onSelect = { [weak self, weak navigationController] target in
                navigationController?.dismiss(animated: true)
                self?.forwardChannelsVideo(payload, to: target)
            }
            controller.onClose = { [weak navigationController] in
                navigationController?.dismiss(animated: true)
            }
            present(navigationController, animated: true)
        }
    }

    func forwardChannelsVideo(_ payload: ChannelsVideoForwardPayload, to target: ChatParticipant) {
        let sender = outgoingSenderForSelectedAccount(in: target)
        let message = ChatMessage(
            id: UUID(),
            conversationID: target.id,
            type: .channelsVideo,
            sender: sender,
            body: payload.title,
            detail: payload.detail.isEmpty ? "视频号 · 转发内容" : payload.detail,
            isOutgoing: true,
            presentation: outgoingPresentationForSelectedAccount(),
            timestamp: currentTimestamp(),
            sentAt: Date(),
            attachmentURL: payload.videoURL,
            isGroupConversation: isGroupParticipant(target),
            recipientAccountID: sender.id
        )
        state.messages.append(message)
        persistMessages()
        sendOpenApiMessageIfPossible(message)
        focusForwardedMessages(in: target, messageID: message.id)
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.35) {
            self.showNotice("已转发视频号给\(target.displayName)")
        }
    }

    func focusForwardedMessages(in target: ChatParticipant, messageID: UUID?) {
        cancelLeftScrollPreview()
        state.selectedFriendID = target.id
        setQuotedMessage(nil)
        updateHeaderForSelection()
        leftCollectionView.reloadData()
        updateLeftCollectionBounceInsets()
        rightAccountCollectionView.reloadData()
        rightToolCollectionView.reloadData()
        messageCollectionView.reloadData()

        DispatchQueue.main.async {
            self.updateLeftCollectionBounceInsets()
            self.leftCollectionView.layoutIfNeeded()
            self.messageCollectionView.layoutIfNeeded()

            if let leftIndex = self.visibleLeftItemsForSelectedAccount().firstIndex(where: { $0.participantID == target.id }) {
                let leftIndexPath = IndexPath(item: leftIndex, section: 0)
                self.leftCollectionView.scrollToItem(at: leftIndexPath, at: .centeredVertically, animated: true)
                self.pulseSidebarItem(in: self.leftCollectionView, at: leftIndexPath)
            }

            if let messageID, let index = self.renderedIndex(of: messageID) {
                let messageIndexPath = IndexPath(item: index, section: 0)
                self.messageCollectionView.scrollToItem(at: messageIndexPath, at: .bottom, animated: true)
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.25) {
                    self.pulseMessage(at: messageIndexPath)
                    self.scheduleConnectionUpdate()
                }
            } else if let indexPath = self.lastRenderedMessageIndexPath() {
                self.messageCollectionView.scrollToItem(at: indexPath, at: .bottom, animated: true)
                self.scheduleConnectionUpdate()
            }
        }
    }

    func appendIndividualForwardMessages(_ messages: [ChatMessage], to target: ChatParticipant) -> [UUID] {
        let sender = outgoingSenderForSelectedAccount(in: target)
        let presentation = outgoingPresentationForSelectedAccount()
        return messages.map { original in
            let message = ChatMessage(
                id: UUID(),
                conversationID: target.id,
                type: original.type,
                sender: sender,
                body: original.body,
                detail: original.detail,
                isOutgoing: true,
                presentation: presentation,
                timestamp: currentTimestamp(),
                sentAt: Date(),
                attachmentURL: original.attachmentURL,
                isGroupConversation: isGroupParticipant(target),
                richElements: original.richElements,
                contactCard: original.contactCard,
                contactCardAccountID: original.contactCardAccountID,
                quotedMessageID: original.quotedMessageID,
                mergedForwardMessages: original.mergedForwardMessages,
                recipientAccountID: sender.id
            )
            state.messages.append(message)
            return message.id
        }
    }

    func appendMergedForwardMessage(_ messages: [ChatMessage], to target: ChatParticipant) -> UUID {
        let sender = outgoingSenderForSelectedAccount(in: target)
        let preview = messages.prefix(4)
            .map { "\($0.sender.displayName)：\($0.body)" }
            .joined(separator: "\n")
        let message = ChatMessage(
            id: UUID(),
            conversationID: target.id,
            type: .mergedForward,
            sender: sender,
            body: "\(state.activeFriend?.displayName ?? "当前会话")的聊天记录",
            detail: preview.isEmpty ? "聊天记录 \(messages.count) 条" : preview,
            isOutgoing: true,
            presentation: outgoingPresentationForSelectedAccount(),
            timestamp: currentTimestamp(),
            sentAt: Date(),
            isGroupConversation: isGroupParticipant(target),
            mergedForwardMessages: messages,
            recipientAccountID: sender.id
        )
        state.messages.append(message)
        persistMessages()
        return message.id
    }

    @objc func deleteSelectedMessages() {
        let count = selectedMessageIDs.count
        guard count > 0 else { return }
        let alert = UIAlertController(title: "删除消息", message: "确定删除选中的 \(count) 条消息？", preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "取消", style: .cancel))
        alert.addAction(UIAlertAction(title: "删除", style: .destructive) { [weak self] _ in
            self?.deleteMessages(ids: self?.selectedMessageIDs ?? [])
        })
        present(alert, animated: true)
    }

    func deleteMessage(_ message: ChatMessage) {
        deleteMessages(ids: [message.id])
    }

    func deleteMessages(ids: Set<UUID>) {
        guard !ids.isEmpty else { return }
        cleanupStateForDeletedMessages(ids: ids)
        state.messages.removeAll { ids.contains($0.id) }
        ids.forEach { openApiDeliveryStatusesByMessageID.removeValue(forKey: $0) }
        ChatSQLiteStore.shared.deleteMessages(ids: ids)
        ChatSQLiteStore.shared.deleteOpenApiDeliveryStatuses(messageIDs: ids)
        persistMessages()
        setMultiSelecting(false)
        messageCollectionView.reloadData()
        rightAccountCollectionView.reloadData()
        rightToolCollectionView.reloadData()
        updateConnections()
        syncIslandActivityIfNeeded()
        showNotice("已删除")
    }

    func cleanupStateForDeletedMessages(ids: Set<UUID>) {
        let messagesToDelete = state.messages.filter { ids.contains($0.id) }
        guard !messagesToDelete.isEmpty else { return }

        selectedMessageIDs.subtract(ids)
        streamingAIMessageIDs.subtract(ids)

        for message in messagesToDelete {
            autoReplyActiveConversationIDs.remove(message.conversationID)
            if isAutoReplyPrediction(message),
               let sourceMessageID = autoReplySourceMessageID(from: message) {
                autoReplyProcessedMessageIDs.remove(sourceMessageID)
            }
            if quotedMessage?.id == message.id {
                setQuotedMessage(nil)
            }
        }
    }

    @discardableResult
    func insertOutgoingMessage(
        type: ChatMessageType,
        body: String,
        detail: String = "",
        presentation: BubblePresentation = .avatarOnly,
        attachmentURL: URL? = nil,
        contactCard: ContactCardProfile? = nil,
        contactCardAccountID: UUID? = nil,
        quotedMessageID: UUID? = nil,
        richElements: [BubbleRichElement] = []
    ) -> ChatMessage? {
        guard requireActiveConversationForSending() else { return nil }
        let previousRenderedMessageIDs = renderedMessages.map(\.id)
        let isNotification = type == .system || type == .groupNotice
        let activeConversation = state.activeFriend
            ?? selectedUnansweredSendTarget()?.conversation
        let resolvedPresentation = isNotification ? BubblePresentation.bare : outgoingPresentationForSelectedAccount(default: presentation)
        let sender = isNotification ? state.currentUser : outgoingSenderForSelectedAccount(in: activeConversation)
        let isActiveGroupConversation = activeConversation.map { isGroupConversation($0) } ?? false
        let unansweredContextMessage = [quotedMessageID, selectedUnansweredMessageID]
            .compactMap { $0 }
            .compactMap { messageID in state.messages.first { $0.id == messageID } }
            .first
        let message = ChatMessage(
            id: UUID(),
            conversationID: currentConversationID(),
            type: type,
            sender: sender,
            body: body,
            detail: detail,
            isOutgoing: !isNotification,
            presentation: resolvedPresentation,
            timestamp: currentTimestamp(),
            sentAt: Date(),
            attachmentURL: attachmentURL,
            isGroupConversation: isNotification ? true : isActiveGroupConversation,
            richElements: richElements,
            contactCard: contactCard,
            contactCardAccountID: contactCardAccountID,
            quotedMessageID: quotedMessageID,
            recipientAccountID: isNotification ? nil : sender.id,
            unansweredMetadata: isActiveGroupConversation
                ? unansweredContextMessage?.unansweredMetadata
                : nil
        )

        registerUnansweredReplyOriginIfNeeded(for: message.id)

        state.messages.append(message)
        persistMessageMutations([.insert(message)])
        markOutgoingLinksRestrictedIfNeeded(body: body, detail: detail, richElements: richElements, attachmentURL: attachmentURL)
        reloadVisibleRightToolItems()
        insertRenderedMessageItemIfPossible(
            messageID: message.id,
            previousMessageIDs: previousRenderedMessageIDs,
            scrollToInsertedMessage: true
        )
        if type == .redPacket || type == .transfer || type == .splitBill {
            refreshInsertedPaymentMessageLayout(messageID: message.id, delay: 0.08)
            refreshInsertedPaymentMessageLayout(messageID: message.id, delay: 0.38)
        }
        syncIslandActivityIfNeeded()
        let isSendingRemotely = sendOpenApiMessageIfPossible(message) { [weak self] result in
            guard case .success = result else { return }
            self?.completeUnansweredReplyAfterSuccessfulSend(message)
        }
        if !isSendingRemotely,
           openApiAccountContextsByID.isEmpty,
           openApiConversationContextsByID.isEmpty {
            completeUnansweredReplyAfterSuccessfulSend(message)
        }
        return message
    }

    func openApiSendContextForCurrentSelection() -> (account: OpenApiWeChatAccountContext, conversation: OpenApiConversationContext)? {
        let selectedAccountID: UUID
        let selectedConversationID: UUID
        if let unansweredTarget = selectedUnansweredSendTarget() {
            selectedAccountID = unansweredTarget.accountID
            selectedConversationID = unansweredTarget.item.conversationID
        } else {
            selectedAccountID = state.selectedAccountID
            guard let selectedFriendID = state.selectedFriendID else { return nil }
            selectedConversationID = selectedFriendID
        }
        guard let account = openApiAccountContextsByID[selectedAccountID],
              let conversation = openApiConversationContext(
                conversationID: selectedConversationID,
                accountID: account.participantID
              ),
              let normalizedConversation = normalizedOpenApiConversationContext(
                conversation,
                participantID: selectedConversationID
              ),
              !account.wxid.isEmpty,
              !normalizedConversation.wxid.isEmpty,
              normalizedConversation.ownerWxid == account.wxid
        else { return nil }
        return (account, normalizedConversation)
    }

    func openApiSendContext(
        for message: ChatMessage,
        allowsCurrentSelectionFallback: Bool = true
    ) -> (account: OpenApiWeChatAccountContext, conversation: OpenApiConversationContext)? {
        if let account = openApiAccountContextsByID[message.sender.id],
           let conversation = openApiConversationContext(
            conversationID: message.conversationID,
            accountID: account.participantID
           ),
           let normalizedConversation = normalizedOpenApiConversationContext(conversation, participantID: message.conversationID),
           !account.wxid.isEmpty,
           !normalizedConversation.wxid.isEmpty,
           normalizedConversation.ownerWxid == account.wxid {
            return (account, normalizedConversation)
        }
        if let recipientAccountID = message.recipientAccountID,
           let account = openApiAccountContextsByID[recipientAccountID],
           let conversation = openApiConversationContext(
            conversationID: message.conversationID,
            accountID: recipientAccountID
           ),
           let normalizedConversation = normalizedOpenApiConversationContext(conversation, participantID: message.conversationID),
           !account.wxid.isEmpty,
           !normalizedConversation.wxid.isEmpty {
            return (account, normalizedConversation)
        }
        return allowsCurrentSelectionFallback
            ? openApiSendContextForCurrentSelection()
            : nil
    }

    func openApiConversationContext(conversationID: UUID, accountID: UUID) -> OpenApiConversationContext? {
        if let scoped = openApiConversationContextsByAccountID[conversationID]?[accountID] {
            return scoped
        }
        guard let context = openApiConversationContextsByID[conversationID] else { return nil }
        if let account = openApiAccountContextsByID[accountID], context.ownerWxid != account.wxid {
            return nil
        }
        return context
    }

    func normalizedOpenApiConversationContext(
        _ conversation: OpenApiConversationContext,
        participantID: UUID? = nil
    ) -> OpenApiConversationContext? {
        guard conversation.kind == .chatroom else {
            return conversation.wxid.isEmpty ? nil : conversation
        }
        let resolvedID = resolvedOpenApiChatRoomID(
            participantID: participantID ?? conversation.participantID,
            context: conversation
        )
        guard let resolvedID else { return nil }
        if resolvedID == conversation.wxid {
            return conversation
        }
        let normalized = conversation.replacingWxid(resolvedID)
        openApiConversationContextsByID[participantID ?? conversation.participantID] = normalized
        return normalized
    }

    func resolvedOpenApiChatRoomID(participantID: UUID, context: OpenApiConversationContext) -> String? {
        if let valid = OpenApiDisplay.validChatRoomID(context.wxid) {
            return valid
        }
        for text in [context.backendID, context.notes, context.source, context.sourceExt, context.displayName] {
            if let valid = OpenApiDisplay.validChatRoomID(text) {
                return valid
            }
        }
        if let card = state.contactCards[participantID] {
            for text in [card.wechatID, card.bio, card.company, card.role] {
                if let valid = OpenApiDisplay.validChatRoomID(text) {
                    return valid
                }
            }
        }
        return openApiConversationContextsByID.values.first { candidate in
            guard candidate.kind == .chatroom,
                  candidate.ownerWxid == context.ownerWxid,
                  candidate.participantID != participantID,
                  OpenApiDisplay.validChatRoomID(candidate.wxid) != nil
            else { return false }
            if !context.backendID.isEmpty, candidate.backendID == context.backendID { return true }
            if !context.displayName.isEmpty, candidate.displayName == context.displayName { return true }
            return false
        }.flatMap { OpenApiDisplay.validChatRoomID($0.wxid) }
    }

    @discardableResult
    func sendOpenApiMessageIfPossible(
        _ message: ChatMessage,
        localMessageAlreadyCommitted: Bool = true,
        completion: ((Result<OpenApiSendOutcome, Error>) -> Void)? = nil
    ) -> Bool {
        guard shouldAttemptOpenApiSend(message) else { return false }
        guard let context = openApiSendContext(for: message) else {
            if !openApiAccountContextsByID.isEmpty || !openApiConversationContextsByID.isEmpty {
                setOpenApiDeliveryStatus(
                    for: message,
                    taskIDs: [],
                    state: .failed,
                    note: "当前消息没有匹配的 OpenAPI 帐号/会话，未发起真实发送。请确认右侧帐号与左侧好友或群聊属于同一微信。"
                )
            }
            return false
        }

        let existingTaskIDs = openApiDeliveryStatusesByMessageID[message.id]?.taskIDs ?? []
        setOpenApiDeliveryStatus(
            for: message,
            taskIDs: existingTaskIDs,
            state: .pending,
            note: "等待 OpenAPI 任务终态，HTTP 受理不会标记为已发送。"
        )
        Task { @MainActor in
            do {
                let outcome = try await self.performOpenApiSend(
                    message: message,
                    account: context.account,
                    conversation: context.conversation
                )
                guard !outcome.sentKinds.isEmpty else {
                    throw OpenApiMediaSendError.taskResultUnknown(
                        "真实发送",
                        "没有可发送的消息正文"
                    )
                }
                self.setOpenApiDeliveryStatus(
                    for: message,
                    taskIDs: OpenApiDeliveryStatusPresentation.mergedTaskIDs(
                        existingTaskIDs,
                        outcome.taskIDs
                    ),
                    state: .succeeded,
                    note: "真实\(outcome.sentKinds.joined(separator: "、"))已发送。"
                )
                self.showNotice("真实\(outcome.sentKinds.joined(separator: "、"))已发送")
                completion?(.success(outcome))
            } catch {
                self.recordOpenApiSendFailure(
                    error,
                    for: message,
                    retrying: false,
                    localMessageAlreadyCommitted: localMessageAlreadyCommitted
                )
                completion?(.failure(error))
            }
        }
        return true
    }

    func shouldAttemptOpenApiSend(_ message: ChatMessage) -> Bool {
        switch message.type {
        case .text:
            return shouldSendOpenApiTextBody(message)
                || message.richElements.contains { element in
                    if case .image = element { return true }
                    if case .file = element { return true }
                    return false
                }
        case .voice, .image, .capturedPhoto, .stickerGif, .video:
            return message.attachmentURL != nil
        case .file:
            return message.attachmentURL != nil || openApiFirstRichFileURL(in: message) != nil
        case .webLink, .article, .miniProgram:
            return true
        case .mergedForward:
            return true
        default:
            return false
        }
    }

    func setOpenApiDeliveryStatus(
        for sourceMessage: ChatMessage,
        taskIDs: [String],
        state: OpenApiVisibleTaskState,
        note: String,
        reload: Bool = true
    ) {
        setOpenApiDeliveryStatus(
            forMessageID: sourceMessage.id,
            conversationID: sourceMessage.conversationID,
            taskIDs: taskIDs,
            state: state,
            note: note,
            reload: reload
        )
    }

    func retryOpenApiSend(for message: ChatMessage) {
        guard let context = openApiSendContext(for: message) else {
            showNotice("当前会话没有匹配的 OpenAPI 帐号，无法重试真实发送")
            return
        }
        let existingTaskIDs = openApiDeliveryStatusesByMessageID[message.id]?.taskIDs ?? []
        setOpenApiDeliveryStatus(
            for: message,
            taskIDs: existingTaskIDs,
            state: .pending,
            note: "正在重试真实发送，等待任务终态。"
        )
        Task { @MainActor in
            do {
                let outcome = try await self.performOpenApiSend(
                    message: message,
                    account: context.account,
                    conversation: context.conversation
                )
                self.setOpenApiDeliveryStatus(
                    for: message,
                    taskIDs: OpenApiDeliveryStatusPresentation.mergedTaskIDs(
                        existingTaskIDs,
                        outcome.taskIDs
                    ),
                    state: .succeeded,
                    note: outcome.sentKinds.isEmpty ? "无需真实发送。" : "重试成功：真实\(outcome.sentKinds.joined(separator: "、"))已发送。"
                )
                self.showNotice("重试成功")
            } catch {
                self.recordOpenApiSendFailure(error, for: message, retrying: true)
            }
        }
    }

    func recordOpenApiSendFailure(
        _ error: Error,
        for message: ChatMessage,
        retrying: Bool,
        localMessageAlreadyCommitted: Bool = true
    ) {
        let existingTaskIDs = openApiDeliveryStatusesByMessageID[message.id]?.taskIDs ?? []
        if let mediaError = error as? OpenApiMediaSendError,
           case .taskResultUnknown = mediaError {
            setOpenApiDeliveryStatus(
                for: message,
                taskIDs: existingTaskIDs,
                state: .pending,
                note: "\(retrying ? "重试" : "真实发送")已提交，但后台任务暂未返回终态：\(error.localizedDescription)"
            )
            showNotice("\(retrying ? "重试" : "真实发送")已提交，等待后台回执")
            return
        }
        setOpenApiDeliveryStatus(
            for: message,
            taskIDs: existingTaskIDs,
            state: .failed,
            note: isOpenApiDeviceOfflineError(error)
                ? "微信设备离线，真实发送未执行；草稿或本地消息已保留。"
                : "\(retrying ? "重试" : "真实发送")失败：\(error.localizedDescription)。长按原消息可重试。"
        )
        if isOpenApiDeviceOfflineError(error) {
            showNotice("微信设备离线，真实发送未执行")
        } else {
            showNotice(
                localMessageAlreadyCommitted
                    ? "本地\(message.type.title)已发送，真实发送失败：\(error.localizedDescription)"
                    : "真实发送失败，草稿已保留：\(error.localizedDescription)"
            )
        }
    }

    func setOpenApiDeliveryStatus(
        forMessageID messageID: UUID,
        conversationID: UUID,
        taskIDs: [String],
        state: OpenApiVisibleTaskState,
        note: String,
        reload: Bool
    ) {
        let uniqueTaskIDs = taskIDs.reduce(into: [String]()) { result, taskID in
            let cleaned = taskID.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !cleaned.isEmpty, !result.contains(cleaned) else { return }
            result.append(cleaned)
        }
        let record = OpenApiDeliveryStatusRecord(
            messageID: messageID,
            conversationID: conversationID,
            taskIDs: uniqueTaskIDs,
            stateRawValue: state.rawValue,
            note: note,
            updatedAt: Date()
        )
        openApiDeliveryStatusesByMessageID[messageID] = record
        ChatSQLiteStore.shared.upsertOpenApiDeliveryStatus(record)
        refreshPendingReplyIndexForDeliveryStatusChange(messageID: messageID)
        if state == .succeeded,
           let confirmedMessage = self.state.messages.first(where: { $0.id == messageID }) {
            completeUnansweredReplyAfterSuccessfulSend(confirmedMessage)
        }
        guard reload else { return }
        guard self.state.messages.contains(where: { $0.id == messageID }) else { return }
        reloadRenderedMessageItem(messageID: messageID)
    }

    func latestOpenApiSendState(for message: ChatMessage) -> OpenApiVisibleTaskState? {
        openApiDeliveryStatusesByMessageID[message.id]
            .flatMap { OpenApiVisibleTaskState(rawValue: $0.stateRawValue) }
    }

    func refreshPendingReplyIndexForDeliveryStatusChange(messageID: UUID) {
        guard pendingReplyIndex.isInitialized,
              let message = state.messages.first(where: { $0.id == messageID })
        else { return }
        var index = pendingReplyIndex
        index.apply(
            [.update(message)],
            keyForMessage: { [unowned self] candidate in
                self.unansweredRecipientAccountID(for: candidate).map { accountID in
                    PendingReplyConversationAccountKey(
                        conversationID: candidate.conversationID,
                        accountID: accountID
                    )
                }
            },
            isRelevant: { [unowned self] in self.isHomeReplyRelevantMessage($0) },
            isIncoming: { [unowned self] in self.isIncomingHomeReplyMessage($0) }
        )
        pendingReplyIndex = index
        invalidateVisibleDataCaches(invalidatePendingReplyIndex: false)
    }

    func openApiDeliveryStatusText(for message: ChatMessage) -> String? {
        guard message.isOutgoing || isAutoReplyPrediction(message),
              let record = openApiDeliveryStatusesByMessageID[message.id],
              let state = OpenApiVisibleTaskState(rawValue: record.stateRawValue)
        else { return nil }
        return OpenApiDeliveryStatusPresentation.text(state: state, note: record.note)
    }

    struct OpenApiSendOutcome {
        let sentKinds: [String]
        let taskIDs: [String]
    }

    func performOpenApiSend(
        message: ChatMessage,
        account: OpenApiWeChatAccountContext,
        conversation: OpenApiConversationContext
    ) async throws -> OpenApiSendOutcome {
        var sentKinds: [String] = []
        var taskIDs: [String] = []

        switch message.type {
        case .text:
            if shouldSendOpenApiTextBody(message) {
                taskIDs.append(try await sendOpenApiText(
                    message.body,
                    account: account,
                    conversation: conversation
                ))
                sentKinds.append("消息")
            }
            try await sendOpenApiRichMedia(
                in: message,
                account: account,
                conversation: conversation,
                sentKinds: &sentKinds,
                taskIDs: &taskIDs
            )
        case .voice:
            guard let attachmentURL = message.attachmentURL else {
                throw OpenApiMediaSendError.missingAttachmentURL("语音")
            }
            let uploadedVoice = try await openApiUploadedVoice(for: attachmentURL)
            taskIDs.append(try await sendOpenApiVoice(
                voiceURL: uploadedVoice.url,
                durationSeconds: uploadedVoice.durationSeconds ?? voiceDurationSeconds(for: message, fileURL: attachmentURL),
                account: account,
                conversation: conversation
            ))
            sentKinds.append("语音")
        case .image, .capturedPhoto, .stickerGif:
            guard let attachmentURL = message.attachmentURL else {
                throw OpenApiMediaSendError.missingAttachmentURL("图片")
            }
            try await sendOpenApiAttachmentCaptionIfNeeded(
                message,
                account: account,
                conversation: conversation,
                sentKinds: &sentKinds,
                taskIDs: &taskIDs
            )
            let imageURL = try await openApiUploadedURL(for: attachmentURL, isVoice: false)
            taskIDs.append(try await sendOpenApiImage(imageURL, account: account, conversation: conversation))
            sentKinds.append("图片")
        case .video:
            guard let attachmentURL = message.attachmentURL else {
                throw OpenApiMediaSendError.missingAttachmentURL("视频")
            }
            let preparedVideoURL = try await normalizedMP4VideoURL(for: attachmentURL)
            try await sendOpenApiAttachmentCaptionIfNeeded(
                message,
                account: account,
                conversation: conversation,
                sentKinds: &sentKinds,
                taskIDs: &taskIDs
            )
            let videoURL = try await openApiUploadedURL(for: preparedVideoURL, isVoice: false)
            taskIDs.append(try await sendOpenApiVideo(videoURL, account: account, conversation: conversation))
            sentKinds.append("视频")
        case .file:
            guard let attachmentURL = message.attachmentURL ?? openApiFirstRichFileURL(in: message) else {
                throw OpenApiMediaSendError.missingAttachmentURL("文件")
            }
            try await sendOpenApiAttachmentCaptionIfNeeded(
                message,
                account: account,
                conversation: conversation,
                sentKinds: &sentKinds,
                taskIDs: &taskIDs
            )
            let fileURL = try await openApiUploadedURL(for: attachmentURL, isVoice: false)
            taskIDs.append(try await sendOpenApiFile(
                fileName: openApiFileName(for: message, fallbackURL: attachmentURL),
                fileURL: fileURL,
                account: account,
                conversation: conversation
            ))
            sentKinds.append("文件")
        case .webLink:
            taskIDs.append(try await sendOpenApiLinkCard(message, account: account, conversation: conversation))
            sentKinds.append("网页卡片")
        case .article:
            taskIDs.append(try await sendOpenApiOfficialArticleCard(message, account: account, conversation: conversation))
            sentKinds.append("公众号文章卡片")
        case .miniProgram:
            taskIDs.append(try await sendOpenApiWeAppCard(message, account: account, conversation: conversation))
            sentKinds.append("小程序卡片")
        case .mergedForward:
            taskIDs.append(contentsOf: try await sendOpenApiMergedForwardChatRecord(message, account: account, conversation: conversation))
            sentKinds.append("合并转发")
        case .quotedReply:
            guard let quotedMessageID = message.quotedMessageID,
                  let quotedMessage = state.messages.first(where: { $0.id == quotedMessageID }),
                  let quoteMessageServerID = quotedMessage.backendMessageID,
                  !quoteMessageServerID.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
                  quoteMessageServerID != "0"
            else {
                throw OpenApiMediaSendError.missingRequiredFields(
                    "发送引用消息",
                    "原消息 msgSvrId"
                )
            }
            taskIDs.append(try await OpenApiMessageService().sendQuote(
                account: OpenApiSocialAccount(
                    deviceUUID: account.clientUuid,
                    weChatID: account.wxid
                ),
                conversationID: conversation.wxid,
                content: message.body,
                quoteMessageServerID: quoteMessageServerID
            ))
            sentKinds.append("引用消息")
        default:
            break
        }

        return OpenApiSendOutcome(sentKinds: sentKinds, taskIDs: taskIDs)
    }

    func performOpenApiBatchForwardSend(
        message: ChatMessage,
        account: OpenApiWeChatAccountContext,
        conversation: OpenApiConversationContext
    ) async throws -> OpenApiSendOutcome {
        var sentKinds: [String] = []
        var taskIDs: [String] = []

        switch message.type {
        case .text:
            if shouldSendOpenApiTextBody(message) {
                taskIDs.append(contentsOf: try await sendOpenApiBatchMessage(
                    fields: [
                        "messageType": "text",
                        "content": message.body
                    ],
                    account: account,
                    conversation: conversation,
                    action: "批量转发文本"
                ))
                sentKinds.append("消息")
            }
            try await sendOpenApiBatchRichMedia(
                in: message,
                account: account,
                conversation: conversation,
                sentKinds: &sentKinds,
                taskIDs: &taskIDs
            )
        case .voice:
            guard let attachmentURL = message.attachmentURL else {
                throw OpenApiMediaSendError.missingAttachmentURL("语音")
            }
            let uploadedVoice = try await openApiUploadedVoice(for: attachmentURL)
            taskIDs.append(contentsOf: try await sendOpenApiBatchMessage(
                fields: [
                    "messageType": "voice",
                    "voiceUrl": uploadedVoice.url,
                    "durationSeconds": max(1, uploadedVoice.durationSeconds ?? voiceDurationSeconds(for: message, fileURL: attachmentURL))
                ],
                account: account,
                conversation: conversation,
                action: "批量转发语音"
            ))
            sentKinds.append("语音")
        case .image, .capturedPhoto, .stickerGif:
            guard let attachmentURL = message.attachmentURL else {
                throw OpenApiMediaSendError.missingAttachmentURL("图片")
            }
            try await sendOpenApiBatchAttachmentCaptionIfNeeded(
                message,
                account: account,
                conversation: conversation,
                sentKinds: &sentKinds,
                taskIDs: &taskIDs
            )
            let imageURL = try await openApiUploadedURL(for: attachmentURL, isVoice: false)
            taskIDs.append(contentsOf: try await sendOpenApiBatchMessage(
                fields: [
                    "messageType": "image",
                    "url": imageURL
                ],
                account: account,
                conversation: conversation,
                action: "批量转发图片"
            ))
            sentKinds.append("图片")
        case .video:
            guard let attachmentURL = message.attachmentURL else {
                throw OpenApiMediaSendError.missingAttachmentURL("视频")
            }
            let preparedVideoURL = try await normalizedMP4VideoURL(for: attachmentURL)
            try await sendOpenApiBatchAttachmentCaptionIfNeeded(
                message,
                account: account,
                conversation: conversation,
                sentKinds: &sentKinds,
                taskIDs: &taskIDs
            )
            let videoURL = try await openApiUploadedURL(for: preparedVideoURL, isVoice: false)
            taskIDs.append(contentsOf: try await sendOpenApiBatchMessage(
                fields: [
                    "messageType": "video",
                    "url": videoURL
                ],
                account: account,
                conversation: conversation,
                action: "批量转发视频"
            ))
            sentKinds.append("视频")
        case .file:
            guard let attachmentURL = message.attachmentURL ?? openApiFirstRichFileURL(in: message) else {
                throw OpenApiMediaSendError.missingAttachmentURL("文件")
            }
            try await sendOpenApiBatchAttachmentCaptionIfNeeded(
                message,
                account: account,
                conversation: conversation,
                sentKinds: &sentKinds,
                taskIDs: &taskIDs
            )
            let fileURL = try await openApiUploadedURL(for: attachmentURL, isVoice: false)
            let fileName = openApiFileName(for: message, fallbackURL: attachmentURL)
            taskIDs.append(contentsOf: try await sendOpenApiBatchMessage(
                fields: [
                    "messageType": "file",
                    "content": fileName,
                    "title": fileName,
                    "url": fileURL
                ],
                account: account,
                conversation: conversation,
                action: "批量转发文件"
            ))
            sentKinds.append("文件")
        case .webLink:
            var body = openApiLinkCardBody(
                from: message,
                fallbackTitle: "网页链接",
                fallbackSourceName: "只发",
                fallbackSource: "ios_link_card"
            )
            body["messageType"] = "link-card"
            taskIDs.append(contentsOf: try await sendOpenApiBatchMessage(
                fields: body,
                account: account,
                conversation: conversation,
                action: "批量转发网页卡片"
            ))
            sentKinds.append("网页卡片")
        case .article:
            var body = openApiLinkCardBody(
                from: message,
                fallbackTitle: "公众号文章",
                fallbackSourceName: "公众号",
                fallbackSource: "official_article"
            )
            body["messageType"] = "official-article-card"
            taskIDs.append(contentsOf: try await sendOpenApiBatchMessage(
                fields: body,
                account: account,
                conversation: conversation,
                action: "批量转发公众号文章卡片"
            ))
            sentKinds.append("公众号文章卡片")
        case .miniProgram:
            var body = try await openApiWeAppCardBody(from: message, account: account, conversation: conversation)
            body["messageType"] = "weapp-card"
            taskIDs.append(contentsOf: try await sendOpenApiBatchMessage(
                fields: body,
                account: account,
                conversation: conversation,
                action: "批量转发小程序卡片"
            ))
            sentKinds.append("小程序卡片")
        case .mergedForward:
            taskIDs.append(contentsOf: try await sendOpenApiMergedForwardChatRecord(message, account: account, conversation: conversation))
            sentKinds.append("合并转发")
        default:
            break
        }

        return OpenApiSendOutcome(sentKinds: sentKinds, taskIDs: taskIDs)
    }

    func sendOpenApiBatchAttachmentCaptionIfNeeded(
        _ message: ChatMessage,
        account: OpenApiWeChatAccountContext,
        conversation: OpenApiConversationContext,
        sentKinds: inout [String],
        taskIDs: inout [String]
    ) async throws {
        guard let caption = openApiAttachmentCaptionText(message) else { return }
        taskIDs.append(contentsOf: try await sendOpenApiBatchMessage(
            fields: [
                "messageType": "text",
                "content": caption
            ],
            account: account,
            conversation: conversation,
            action: "批量转发附件说明"
        ))
        sentKinds.append("消息")
    }

    func sendOpenApiBatchRichMedia(
        in message: ChatMessage,
        account: OpenApiWeChatAccountContext,
        conversation: OpenApiConversationContext,
        sentKinds: inout [String],
        taskIDs: inout [String]
    ) async throws {
        for element in message.richElements {
            if let imageURLString = openApiImageURLString(from: element),
               let localOrRemoteURL = openApiURL(from: imageURLString) {
                let imageURL = try await openApiUploadedURL(for: localOrRemoteURL, isVoice: false)
                taskIDs.append(contentsOf: try await sendOpenApiBatchMessage(
                    fields: [
                        "messageType": "image",
                        "url": imageURL
                    ],
                    account: account,
                    conversation: conversation,
                    action: "批量转发图片"
                ))
                sentKinds.append("图片")
            } else if case let .file(name, _, url, _, _) = element,
                      let localOrRemoteURL = openApiURL(from: url) {
                let fileURL = try await openApiUploadedURL(for: localOrRemoteURL, isVoice: false)
                let fileName = name.isEmpty ? localOrRemoteURL.lastPathComponent : name
                taskIDs.append(contentsOf: try await sendOpenApiBatchMessage(
                    fields: [
                        "messageType": "file",
                        "content": fileName,
                        "title": fileName,
                        "url": fileURL
                    ],
                    account: account,
                    conversation: conversation,
                    action: "批量转发文件"
                ))
                sentKinds.append("文件")
            }
        }
    }

    func sendOpenApiBatchMessage(
        fields: [String: Any],
        account: OpenApiWeChatAccountContext,
        conversation: OpenApiConversationContext,
        action: String
    ) async throws -> [String] {
        var body = openApiCleanBatchMessageFields(fields)
        body["deviceUuid"] = account.clientUuid
        body["weChatId"] = account.wxid
        body["conversationIds"] = [conversation.wxid]
        let result = try await OpenApiHTTPClient.request(
            "POST",
            path: "/openapi/v1/messages/batch",
            body: body
        )
        return try await validateOpenApiBatchResult(result, action: action)
    }

    func openApiCleanBatchMessageFields(_ fields: [String: Any]) -> [String: Any] {
        fields.reduce(into: [String: Any]()) { result, pair in
            if let value = pair.value as? String {
                let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
                guard !trimmed.isEmpty else { return }
                result[pair.key] = trimmed
                return
            }
            result[pair.key] = pair.value
        }
    }

    func shouldSendOpenApiTextBody(_ message: ChatMessage) -> Bool {
        let text = message.body.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { return false }
        if message.detail.hasPrefix("多图消息"),
           !message.richElements.compactMap(openApiImageURLString(from:)).isEmpty,
           text.range(of: #"^图片\s*\d+\s*张$"#, options: .regularExpression) != nil {
            return false
        }
        return true
    }

    func sendOpenApiAttachmentCaptionIfNeeded(
        _ message: ChatMessage,
        account: OpenApiWeChatAccountContext,
        conversation: OpenApiConversationContext,
        sentKinds: inout [String],
        taskIDs: inout [String]
    ) async throws {
        guard let caption = openApiAttachmentCaptionText(message) else { return }
        taskIDs.append(try await sendOpenApiText(caption, account: account, conversation: conversation))
        sentKinds.append("消息")
    }

    func openApiAttachmentCaptionText(_ message: ChatMessage) -> String? {
        guard message.detail.contains("附件说明消息") else { return nil }
        let text = message.body.trimmingCharacters(in: .whitespacesAndNewlines)
        return text.isEmpty ? nil : text
    }

    func sendOpenApiRichMedia(
        in message: ChatMessage,
        account: OpenApiWeChatAccountContext,
        conversation: OpenApiConversationContext,
        sentKinds: inout [String],
        taskIDs: inout [String]
    ) async throws {
        for element in message.richElements {
            if let imageURLString = openApiImageURLString(from: element),
               let localOrRemoteURL = openApiURL(from: imageURLString) {
                let imageURL = try await openApiUploadedURL(for: localOrRemoteURL, isVoice: false)
                taskIDs.append(try await sendOpenApiImage(imageURL, account: account, conversation: conversation))
                sentKinds.append("图片")
            } else if case let .file(name, _, url, _, _) = element,
                      let localOrRemoteURL = openApiURL(from: url) {
                let fileURL = try await openApiUploadedURL(for: localOrRemoteURL, isVoice: false)
                taskIDs.append(try await sendOpenApiFile(
                    fileName: name.isEmpty ? localOrRemoteURL.lastPathComponent : name,
                    fileURL: fileURL,
                    account: account,
                    conversation: conversation
                ))
                sentKinds.append("文件")
            }
        }
    }

    func sendOpenApiText(
        _ text: String,
        account: OpenApiWeChatAccountContext,
        conversation: OpenApiConversationContext
    ) async throws -> String {
        let result = try await OpenApiHTTPClient.request(
            "POST",
            path: "/openapi/v1/messages/text",
            body: [
                "deviceUuid": account.clientUuid,
                "weChatId": account.wxid,
                "conversationId": conversation.wxid,
                "content": text,
                "atIds": ""
            ]
        )
        return try await validateOpenApiTaskResult(result, action: "发送文本")
    }

    func sendOpenApiImage(
        _ imageURL: String,
        account: OpenApiWeChatAccountContext,
        conversation: OpenApiConversationContext
    ) async throws -> String {
        let result = try await OpenApiHTTPClient.request(
            "POST",
            path: "/openapi/v1/messages/image",
            body: [
                "deviceUuid": account.clientUuid,
                "weChatId": account.wxid,
                "conversationId": conversation.wxid,
                "imageUrl": imageURL
            ]
        )
        return try await validateOpenApiTaskResult(result, action: "发送图片")
    }

    func sendOpenApiVideo(
        _ videoURL: String,
        account: OpenApiWeChatAccountContext,
        conversation: OpenApiConversationContext
    ) async throws -> String {
        let result = try await OpenApiHTTPClient.request(
            "POST",
            path: "/openapi/v1/messages/video",
            body: [
                "deviceUuid": account.clientUuid,
                "weChatId": account.wxid,
                "conversationId": conversation.wxid,
                "videoUrl": videoURL
            ]
        )
        return try await validateOpenApiTaskResult(result, action: "发送视频")
    }

    func sendOpenApiVoice(
        voiceURL: String,
        durationSeconds: Int,
        account: OpenApiWeChatAccountContext,
        conversation: OpenApiConversationContext
    ) async throws -> String {
        let result = try await OpenApiHTTPClient.request(
            "POST",
            path: "/openapi/v1/messages/voice",
            body: [
                "deviceUuid": account.clientUuid,
                "weChatId": account.wxid,
                "conversationId": conversation.wxid,
                "voiceUrl": voiceURL,
                "durationSeconds": max(1, durationSeconds)
            ]
        )
        return try await validateOpenApiTaskResult(result, action: "发送语音")
    }

    func sendOpenApiFile(
        fileName: String,
        fileURL: String,
        account: OpenApiWeChatAccountContext,
        conversation: OpenApiConversationContext
    ) async throws -> String {
        let normalizedFileName = openApiSanitizedFileName(fileName)
        guard !normalizedFileName.isEmpty else {
            throw OpenApiMediaSendError.missingRequiredFields("发送文件", "fileName")
        }
        guard let normalizedFileURL = openApiNormalizedRemoteURLText(fileURL) else {
            throw OpenApiMediaSendError.missingRequiredFields("发送文件", "fileUrl 必须是上传后可访问的 http/https URL")
        }
        let result = try await OpenApiHTTPClient.request(
            "POST",
            path: "/openapi/v1/messages/file",
            body: [
                "deviceUuid": account.clientUuid,
                "weChatId": account.wxid,
                "conversationId": conversation.wxid,
                "fileName": normalizedFileName,
                "fileUrl": normalizedFileURL
            ]
        )
        return try await validateOpenApiTaskResult(result, action: "发送文件")
    }

    func sendOpenApiLinkCard(
        _ message: ChatMessage,
        account: OpenApiWeChatAccountContext,
        conversation: OpenApiConversationContext
    ) async throws -> String {
        var body = openApiLinkCardBody(
            from: message,
            fallbackTitle: "网页链接",
            fallbackSourceName: "只发",
            fallbackSource: "ios_link_card"
        )
        body["deviceUuid"] = account.clientUuid
        body["weChatId"] = account.wxid
        body["conversationId"] = conversation.wxid
        let result = try await OpenApiHTTPClient.request(
            "POST",
            path: "/openapi/v1/messages/link-card",
            body: body
        )
        return try await validateOpenApiTaskResult(result, action: "发送网页卡片")
    }

    func sendOpenApiOfficialArticleCard(
        _ message: ChatMessage,
        account: OpenApiWeChatAccountContext,
        conversation: OpenApiConversationContext
    ) async throws -> String {
        var body = openApiLinkCardBody(
            from: message,
            fallbackTitle: "公众号文章",
            fallbackSourceName: "公众号",
            fallbackSource: "official_article"
        )
        body["deviceUuid"] = account.clientUuid
        body["weChatId"] = account.wxid
        body["conversationId"] = conversation.wxid
        let result = try await OpenApiHTTPClient.request(
            "POST",
            path: "/openapi/v1/messages/official-article-card",
            body: body
        )
        return try await validateOpenApiTaskResult(result, action: "发送公众号文章卡片")
    }

    func sendOpenApiMergedForwardChatRecord(
        _ message: ChatMessage,
        account: OpenApiWeChatAccountContext,
        conversation: OpenApiConversationContext
    ) async throws -> [String] {
        let title = message.body.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            ? "聊天记录"
            : message.body.trimmingCharacters(in: .whitespacesAndNewlines)
        let description = mergedForwardDescription(for: message)
        return try await sendOpenApiBatchMessage(
            fields: [
                "messageType": "chat-record",
                "title": title,
                "content": description,
                "description": description,
                "recordItem": openApiRecordItemXML(forMergedForward: message)
            ],
            account: account,
            conversation: conversation,
            action: "合并转发聊天记录"
        )
    }

    func sendOpenApiNoteCard(
        _ message: ChatMessage,
        account: OpenApiWeChatAccountContext,
        conversation: OpenApiConversationContext
    ) async throws -> String {
        let title = message.body.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            ? "聊天记录"
            : message.body.trimmingCharacters(in: .whitespacesAndNewlines)
        let description = mergedForwardDescription(for: message)
        let result = try await OpenApiHTTPClient.request(
            "POST",
            path: "/openapi/v1/messages/note-card",
            body: [
                "deviceUuid": account.clientUuid,
                "weChatId": account.wxid,
                "conversationId": conversation.wxid,
                "title": title,
                "description": description,
                "thumb": "",
                "recordItem": openApiRecordItemXML(forMergedForward: message)
            ]
        )
        return try await validateOpenApiTaskResult(result, action: "发送合并转发")
    }

    func sendOpenApiWeAppCard(
        _ message: ChatMessage,
        account: OpenApiWeChatAccountContext,
        conversation: OpenApiConversationContext
    ) async throws -> String {
        var body = try await openApiWeAppCardBody(from: message, account: account, conversation: conversation)
        body["deviceUuid"] = account.clientUuid
        body["weChatId"] = account.wxid
        body["conversationId"] = conversation.wxid
        let result = try await OpenApiHTTPClient.request(
            "POST",
            path: "/openapi/v1/messages/weapp-card",
            body: body
        )
        return try await validateOpenApiTaskResult(result, action: "发送小程序卡片")
    }

    func openApiLinkCardBody(
        from message: ChatMessage,
        fallbackTitle: String,
        fallbackSourceName: String,
        fallbackSource: String
    ) -> [String: Any] {
        let combined = [message.body, message.detail].joined(separator: "\n")
        let rawURL = openApiDetailValue(in: message, keys: ["url", "链接", "网页链接", "文章链接"])
            ?? firstDetectedURLText(in: combined)
            ?? "https://cc2.cx"
        let url = normalizedURL(from: rawURL)?.absoluteString ?? rawURL
        let title = openApiDetailValue(in: message, keys: ["title", "标题"])
            ?? cardTitleText(from: message.body, fallback: fallbackTitle)
        let description = openApiDetailValue(in: message, keys: ["description", "描述", "摘要"])
            ?? cardDescriptionText(from: message.detail, fallback: message.body)
        return [
            "url": url,
            "title": title,
            "description": description,
            "thumb": openApiDetailValue(in: message, keys: ["thumb", "封面", "缩略图"]) ?? "",
            "appId": openApiDetailValue(in: message, keys: ["appId", "appid", "应用AppId", "小程序AppId"]) ?? "",
            "sourceName": openApiDetailValue(in: message, keys: ["sourceName", "来源名称", "来源"]) ?? fallbackSourceName,
            "source": openApiDetailValue(in: message, keys: ["source", "来源标识"]) ?? fallbackSource
        ]
    }

    func mergedForwardDescription(for message: ChatMessage) -> String {
        let messages = message.mergedForwardMessages.isEmpty
            ? decodeMergedForwardMessages(from: message.detail)
            : message.mergedForwardMessages
        let countText = messages.isEmpty ? "聊天记录" : "聊天记录 \(messages.count) 条"
        let preview = messages
            .prefix(3)
            .map { "\($0.sender.displayName)：\(mergedForwardDetailText(for: $0))" }
            .joined(separator: "\n")
        return preview.isEmpty ? countText : "\(countText)\n\(preview)"
    }

    func openApiRecordItemXML(forMergedForward message: ChatMessage) -> String {
        let messages = message.mergedForwardMessages.isEmpty
            ? decodeMergedForwardMessages(from: message.detail)
            : message.mergedForwardMessages
        let entries = messages.isEmpty ? [message] : messages
        let items = entries.prefix(80).enumerated().map { index, item in
            let title = xmlEscaped(item.sender.displayName)
            let detail = xmlEscaped(mergedForwardDetailText(for: item))
            let time = xmlEscaped(item.displayTimestamp)
            let type = xmlEscaped(item.type.title)
            return """
            <dataitem index="\(index)" datatype="1">
            <srcname>\(title)</srcname>
            <datatitle>\(type)</datatitle>
            <datadesc>\(detail)</datadesc>
            <sourcetime>\(time)</sourcetime>
            </dataitem>
            """
        }.joined()
        return """
        <recordinfo>
        <title>\(xmlEscaped(message.body.isEmpty ? "聊天记录" : message.body))</title>
        <desc>\(xmlEscaped(mergedForwardDescription(for: message)))</desc>
        <datalist count="\(entries.count)">\(items)</datalist>
        </recordinfo>
        """
    }

    func xmlEscaped(_ text: String) -> String {
        text
            .replacingOccurrences(of: "&", with: "&amp;")
            .replacingOccurrences(of: "<", with: "&lt;")
            .replacingOccurrences(of: ">", with: "&gt;")
            .replacingOccurrences(of: "\"", with: "&quot;")
            .replacingOccurrences(of: "'", with: "&apos;")
    }

    func openApiWeAppCardBody(
        from message: ChatMessage,
        account: OpenApiWeChatAccountContext,
        conversation: OpenApiConversationContext
    ) async throws -> [String: Any] {
        let explicitBody = openApiExplicitWeAppCardBody(from: message)

        var templateError: Error?
        do {
            if var templateBody = try await openApiWeAppCardTemplateBody(
                from: message,
                account: account,
                conversation: conversation
            ) {
                for (key, value) in explicitBody where openApiCanMergeExplicitWeAppValue(key: key, value: value) {
                    templateBody[key] = value
                }
                if openApiWeAppCardBodyIsSendable(templateBody) {
                    return templateBody
                }
            }
        } catch {
            templateError = error
        }

        if openApiWeAppCardBodyIsSendable(explicitBody) {
            return explicitBody
        }

        if let templateError {
            throw OpenApiMediaSendError.taskResultUnknown(
                "获取小程序卡片模板",
                "\(templateError.localizedDescription)。请先确认 /openapi/v1/messages/card-templates 可返回真实 payload。"
            )
        }

        let missing = openApiWeAppMissingFields(in: explicitBody).joined(separator: "、")
        throw OpenApiMediaSendError.missingRequiredFields(
            "发送小程序卡片",
            "缺少真实字段：\(missing)。请提供真实 appId/pagePath/url，或先调用 /openapi/v1/messages/card-templates 并使用返回 payload。"
        )
    }

    func openApiExplicitWeAppCardBody(from message: ChatMessage) -> [String: Any] {
        var body = openApiWeAppPayloadBodyFromDetail(message) ?? [:]
        let title = openApiDetailValue(in: message, keys: ["title", "标题"])
            ?? cardTitleText(from: message.body, fallback: "")
        openApiSetString(&body, key: "appId", value: openApiDetailValue(in: message, keys: ["appId", "appid", "小程序AppId"]))
        openApiSetString(&body, key: "title", value: title)
        openApiSetString(&body, key: "pagePath", value: openApiDetailValue(in: message, keys: ["pagePath", "页面路径", "页面"]))
        openApiSetString(
            &body,
            key: "url",
            value: openApiDetailValue(in: message, keys: ["url", "链接", "小程序页面链接"])
                ?? firstMiniProgramShortcutText(in: [message.body, message.detail].joined(separator: "\n"))
                ?? firstDetectedURLText(in: [message.body, message.detail].joined(separator: "\n"))
        )
        if let shortcut = firstMiniProgramShortcutText(in: [message.body, message.detail].joined(separator: "\n")) {
            openApiSetString(&body, key: "shortLink", value: shortcut)
            openApiSetString(&body, key: "weAppShortLink", value: shortcut)
            if openApiStringValue(body["title"]).isEmpty {
                openApiSetString(&body, key: "title", value: miniProgramShortcutTitle(from: shortcut))
            }
            if openApiStringValue(body["sourceName"]).isEmpty {
                openApiSetString(&body, key: "sourceName", value: miniProgramShortcutSourceName(from: shortcut))
            }
        }
        openApiSetString(&body, key: "thumb", value: openApiDetailValue(in: message, keys: ["thumb", "封面", "缩略图", "thumbUrl"]))
        openApiSetString(&body, key: "icon", value: openApiDetailValue(in: message, keys: ["icon", "图标", "iconUrl"]))
        openApiSetString(&body, key: "source", value: openApiDetailValue(in: message, keys: ["source", "来源标识"]))
        openApiSetString(&body, key: "sourceName", value: openApiDetailValue(in: message, keys: ["sourceName", "来源名称", "来源"]))
        openApiSetString(&body, key: "sourceUsername", value: openApiDetailValue(in: message, keys: ["sourceUsername", "原始ID", "gh"]))
        body["version"] = openApiDetailIntValue(in: message, keys: ["version", "版本"]) ?? (body["version"] ?? 0)
        body["disForward"] = openApiDetailBoolValue(in: message, keys: ["disForward", "禁止转发"]) ?? (body["disForward"] ?? false)
        return openApiNormalizedWeAppCardBody(from: body)
    }

    func openApiWeAppPayloadBodyFromDetail(_ message: ChatMessage) -> [String: Any]? {
        guard let payloadText = openApiDetailValue(in: message, keys: ["payloadJson", "payload", "模板payload", "卡片payload"]),
              let data = payloadText.data(using: .utf8),
              let payload = try? JSONSerialization.jsonObject(with: data) as? [String: Any]
        else { return nil }
        return openApiNormalizedWeAppCardBody(from: payload)
    }

    func openApiWeAppCardTemplateBody(
        from message: ChatMessage,
        account: OpenApiWeChatAccountContext,
        conversation: OpenApiConversationContext
    ) async throws -> [String: Any]? {
        let thumbURL = openApiDetailValue(in: message, keys: ["thumb", "封面", "缩略图", "thumbUrl"]) ?? ""
        let result = try await OpenApiHTTPClient.request(
            "GET",
            path: "/openapi/v1/messages/card-templates",
            query: [
                URLQueryItem(name: "deviceUuid", value: account.clientUuid),
                URLQueryItem(name: "weChatId", value: account.wxid),
                URLQueryItem(name: "conversationId", value: conversation.wxid),
                URLQueryItem(name: "thumbUrl", value: thumbURL)
            ]
        )
        for item in openApiCardTemplateItems(from: result.jsonObject) {
            let descriptor = [
                openApiStringValue(item["kind"]),
                openApiStringValue(item["route"]),
                openApiStringValue(item["title"]),
                openApiStringValue(item["description"])
            ].joined(separator: " ").lowercased()
            guard descriptor.contains("weapp")
                    || descriptor.contains("mini")
                    || descriptor.contains("小程序")
                    || descriptor.contains("we-app")
                    || openApiWeAppCandidatePayload(from: item) != nil
            else { continue }
            guard let payload = openApiWeAppCandidatePayload(from: item) else { continue }
            return payload
        }
        return nil
    }

    func openApiCardTemplateItems(from object: Any?) -> [[String: Any]] {
        if let items = object as? [[String: Any]] {
            return items
        }
        guard let dictionary = object as? [String: Any] else { return [] }
        if let items = dictionary["items"] as? [[String: Any]] {
            return items
        }
        for key in ["data", "result", "payload"] {
            let nestedItems = openApiCardTemplateItems(from: dictionary[key])
            if !nestedItems.isEmpty {
                return nestedItems
            }
        }
        return []
    }

    func openApiWeAppCandidatePayload(from item: [String: Any]) -> [String: Any]? {
        if let payload = item["payload"] as? [String: Any] {
            let normalized = openApiNormalizedWeAppCardBody(from: payload)
            if openApiWeAppCardBodyHasCoreFields(normalized) {
                return normalized
            }
        }
        if let payloadJSON = item["payloadJson"] as? String,
           let data = payloadJSON.data(using: .utf8),
           let payload = try? JSONSerialization.jsonObject(with: data) as? [String: Any] {
            let normalized = openApiNormalizedWeAppCardBody(from: payload)
            if openApiWeAppCardBodyHasCoreFields(normalized) {
                return normalized
            }
        }
        let normalized = openApiNormalizedWeAppCardBody(from: item)
        return openApiWeAppCardBodyHasCoreFields(normalized) ? normalized : nil
    }

    func openApiNormalizedWeAppCardBody(from dictionary: [String: Any]) -> [String: Any] {
        var body: [String: Any] = [:]
        openApiSetString(&body, key: "appId", value: openApiFirstString(in: dictionary, keys: ["appId", "appid", "weAppAppId", "weappAppId", "linkAppId"]))
        openApiSetString(&body, key: "title", value: openApiFirstString(in: dictionary, keys: ["title", "weAppTitle", "linkTitle", "name"]))
        openApiSetString(&body, key: "pagePath", value: openApiFirstString(in: dictionary, keys: ["pagePath", "weAppPagePath", "weappPagePath", "path"]))
        openApiSetString(&body, key: "url", value: openApiFirstString(in: dictionary, keys: ["url", "weAppUrl", "weappUrl", "linkUrl"]))
        openApiSetString(&body, key: "thumb", value: openApiFirstString(in: dictionary, keys: ["thumb", "thumbUrl", "weAppThumb", "weappThumb", "linkThumb"]))
        openApiSetString(&body, key: "icon", value: openApiFirstString(in: dictionary, keys: ["icon", "iconUrl", "weAppIcon", "weappIcon"]))
        openApiSetString(&body, key: "source", value: openApiFirstString(in: dictionary, keys: ["source", "sourceId", "weAppSource"]))
        openApiSetString(&body, key: "sourceName", value: openApiFirstString(in: dictionary, keys: ["sourceName", "weAppSourceName", "appName"]))
        openApiSetString(&body, key: "sourceUsername", value: openApiFirstString(in: dictionary, keys: ["sourceUsername", "sourceUserName", "username", "gh", "originalId"]))
        openApiSetString(&body, key: "shortLink", value: openApiFirstString(in: dictionary, keys: ["shortLink", "weAppShortLink", "miniProgramLink", "小程序链接"]))
        openApiSetString(&body, key: "weAppShortLink", value: openApiFirstString(in: dictionary, keys: ["weAppShortLink", "shortLink", "miniProgramLink", "小程序链接"]))
        body["version"] = openApiIntFromAny(dictionary["version"] ?? dictionary["weAppVersion"]) ?? 0
        body["disForward"] = openApiBoolValue(dictionary["disForward"] ?? dictionary["disableForward"]) ?? false
        return body
    }

    func openApiSetString(_ body: inout [String: Any], key: String, value: String?) {
        let cleaned = (value ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleaned.isEmpty else { return }
        body[key] = cleaned
    }

    func openApiIntFromAny(_ value: Any?) -> Int? {
        if let int = value as? Int { return int }
        if let number = value as? NSNumber { return number.intValue }
        if let string = value as? String { return Int(string.trimmingCharacters(in: .whitespacesAndNewlines)) }
        return nil
    }

    func openApiCanMergeExplicitWeAppValue(key: String, value: Any) -> Bool {
        if key == "version" || key == "disForward" { return true }
        let string = openApiStringValue(value)
        guard !string.isEmpty else { return false }
        return !openApiWeAppFieldIsDemo(key: key, value: string)
    }

    func openApiWeAppCardBodyHasCoreFields(_ body: [String: Any]) -> Bool {
        !openApiStringValue(body["appId"]).isEmpty
            && !openApiStringValue(body["pagePath"]).isEmpty
            && !openApiStringValue(body["url"]).isEmpty
    }

    func openApiWeAppCardBodyIsSendable(_ body: [String: Any]) -> Bool {
        openApiWeAppMissingFields(in: body).isEmpty
    }

    func openApiWeAppMissingFields(in body: [String: Any]) -> [String] {
        var missing: [String] = []
        let appID = openApiStringValue(body["appId"])
        let pagePath = openApiStringValue(body["pagePath"])
        let url = openApiStringValue(body["url"])
        if appID.isEmpty || openApiWeAppFieldIsDemo(key: "appId", value: appID) {
            missing.append("真实 appId")
        }
        if pagePath.isEmpty {
            missing.append("真实 pagePath")
        }
        if url.isEmpty || openApiWeAppFieldIsDemo(key: "url", value: url) {
            missing.append("真实 url")
        }
        return missing
    }

    func openApiWeAppFieldIsDemo(key: String, value: String) -> Bool {
        let cleaned = value.trimmingCharacters(in: .whitespacesAndNewlines)
        let lowered = cleaned.lowercased()
        if key == "appId" {
            return lowered == "wx_demo_appid" || lowered == "wx1234567890abcdef"
        }
        if key == "url" {
            if let host = normalizedURL(from: cleaned)?.host?.lowercased() {
                return host == "cc2.cx" || host.hasSuffix(".cc2.cx") || host == "example.com" || host.hasSuffix(".example.com")
            }
            return lowered.contains("cc2.cx") || lowered.contains("example.com")
        }
        return false
    }

    func cardTitleText(from text: String, fallback: String) -> String {
        var cleaned = text.trimmingCharacters(in: .whitespacesAndNewlines)
        if let urlText = firstDetectedURLText(in: cleaned) {
            cleaned = cleaned.replacingOccurrences(of: urlText, with: "")
                .trimmingCharacters(in: .whitespacesAndNewlines)
        }
        let prefixes = ["网页链接：", "公众号图文：", "公众号文章：", "小程序页面："]
        for prefix in prefixes where cleaned.hasPrefix(prefix) {
            cleaned = String(cleaned.dropFirst(prefix.count)).trimmingCharacters(in: .whitespacesAndNewlines)
        }
        return cleaned.isEmpty ? fallback : cleaned
    }

    func cardDescriptionText(from text: String, fallback: String) -> String {
        let lines = text.components(separatedBy: .newlines)
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty && firstDetectedURLText(in: $0) == nil }
        if let summary = lines.first(where: { !$0.localizedCaseInsensitiveContains("appId") && !$0.localizedCaseInsensitiveContains("pagePath") }) {
            return summary
        }
        let trimmed = fallback.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? "来自只发 iOS 的卡片消息" : trimmed
    }

    func openApiDetailValue(in message: ChatMessage, keys: [String]) -> String? {
        let separators = ["：", ":"]
        let lines = message.detail.components(separatedBy: .newlines)
        for line in lines {
            let trimmed = line.trimmingCharacters(in: .whitespacesAndNewlines)
            for key in keys {
                for separator in separators {
                    let prefix = "\(key)\(separator)"
                    if trimmed.localizedCaseInsensitiveContains(prefix),
                       let range = trimmed.range(of: prefix, options: [.caseInsensitive]) {
                        let value = String(trimmed[range.upperBound...]).trimmingCharacters(in: .whitespacesAndNewlines)
                        if !value.isEmpty { return value }
                    }
                }
            }
        }
        return nil
    }

    func openApiDetailIntValue(in message: ChatMessage, keys: [String]) -> Int? {
        openApiDetailValue(in: message, keys: keys).flatMap { Int($0.trimmingCharacters(in: .whitespacesAndNewlines)) }
    }

    func openApiDetailBoolValue(in message: ChatMessage, keys: [String]) -> Bool? {
        guard let value = openApiDetailValue(in: message, keys: keys)?.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() else {
            return nil
        }
        switch value {
        case "true", "1", "yes", "是", "禁止": return true
        case "false", "0", "no", "否", "允许": return false
        default: return nil
        }
    }

    enum OpenApiTaskPollState {
        case pending(String)
        case succeeded(String)
        case failed(String)
    }

    func validateOpenApiTaskResult(_ result: OpenApiHTTPResult, action: String) async throws -> String {
        guard let root = result.jsonObject as? [String: Any] else {
            throw OpenApiMediaSendError.taskResultUnknown(
                action,
                "响应不是 JSON；HTTP \(result.statusCode)：\(openApiDiagnosticBody(result.rawText))"
            )
        }
        if let taskID = openApiTaskID(from: root) {
            try await awaitOpenApiTaskCompletion(taskID: taskID, action: action)
            return taskID
        }
        let responseMessage = openApiFirstString(
            in: root,
            keys: ["message", "error", "errorMessage", "detail", "resultCode", "code"]
        )
        if openApiBoolValue(root["resultUnknown"]) == true
            || openApiTaskResultMessageIsUnknown(responseMessage) {
            throw OpenApiMediaSendError.taskResultUnknown(
                action,
                responseMessage.isEmpty ? "后端返回结果未知，不能确认微信端是否执行" : responseMessage
            )
        }
        if let success = openApiBoolValue(root["success"]), !success {
            throw OpenApiMediaSendError.taskFailed(
                action,
                openApiFriendlyTaskFailureMessage(
                    responseMessage.isEmpty ? "后端返回 success=false" : responseMessage,
                    action: action
                )
            )
        }
        throw OpenApiMediaSendError.taskResultUnknown(
            action,
            "后端仅返回受理结果，未返回 taskId/taskResultUrl，不能标记为真实已发送；HTTP \(result.statusCode)：\(openApiDiagnosticBody(result.rawText))"
        )
    }

    func openApiTaskResultMessageIsUnknown(_ message: String) -> Bool {
        let normalized = message.lowercased()
        return normalized.contains("结果未知")
            || normalized.contains("result unknown")
            || normalized.contains("resultunknown")
            || normalized.contains("可能仍在继续执行")
            || normalized.contains("请勿立即重复发送")
    }

    func validateOpenApiBatchResult(_ result: OpenApiHTTPResult, action: String) async throws -> [String] {
        guard let root = result.jsonObject as? [String: Any] else {
            throw OpenApiMediaSendError.taskResultUnknown(
                action,
                "响应不是 JSON；HTTP \(result.statusCode)：\(openApiDiagnosticBody(result.rawText))"
            )
        }
        let payload = openApiBatchPayload(from: root)
        if let success = openApiBoolValue(payload["success"]), !success {
            let message = openApiFirstString(in: payload, keys: ["message", "error", "errorMessage", "detail", "resultCode", "code"])
            throw OpenApiMediaSendError.taskFailed(
                action,
                openApiFriendlyTaskFailureMessage(message.isEmpty ? "后端返回 success=false" : message, action: action)
            )
        }

        let items = openApiBatchItems(from: root)
        var taskIDs: [String] = []
        var failureMessages: [String] = []
        var unknownMessages: [String] = []

        for item in items {
            let targetID = openApiFirstString(in: item, keys: ["targetId", "conversationId", "wxid"])
            let targetPrefix = targetID.isEmpty ? "" : "\(targetID)："
            let itemMessage = openApiFirstString(in: item, keys: ["message", "error", "errorMessage", "detail", "resultCode", "code"])
            let resultUnknown = openApiBoolValue(item["resultUnknown"]) ?? false

            if let itemTaskID = openApiTaskID(from: item) {
                do {
                    try await awaitOpenApiTaskCompletion(taskID: itemTaskID, action: action)
                    taskIDs.append(itemTaskID)
                } catch let error as OpenApiMediaSendError {
                    if case .taskResultUnknown = error {
                        unknownMessages.append("\(targetPrefix)\(error.localizedDescription)")
                    } else {
                        failureMessages.append("\(targetPrefix)\(error.localizedDescription)")
                    }
                } catch {
                    unknownMessages.append("\(targetPrefix)\(error.localizedDescription)")
                }
                continue
            }

            if let itemSuccess = openApiBoolValue(item["success"]), !itemSuccess {
                if resultUnknown || openApiBatchStatusLooksPending(item) {
                    unknownMessages.append("\(targetPrefix)\(itemMessage.isEmpty ? "未返回 taskId，无法确认最终状态" : itemMessage)")
                } else {
                    failureMessages.append("\(targetPrefix)\(itemMessage.isEmpty ? "批量发送项失败" : itemMessage)")
                }
                continue
            }
            if openApiBatchStatusLooksPending(item) {
                unknownMessages.append("\(targetPrefix)\(itemMessage.isEmpty ? "批量发送项仍在处理中，未返回 taskId" : itemMessage)")
                continue
            }
            if openApiBatchStatusLooksSucceeded(item) {
                continue
            }
            if openApiBoolValue(item["success"]) == true {
                unknownMessages.append("\(targetPrefix)\(itemMessage.isEmpty ? "批量发送项仅返回 success=true，未返回 taskId 或明确终态" : itemMessage)")
                continue
            }

            switch openApiTaskPollState(from: item) {
            case .succeeded:
                continue
            case .pending(let message):
                let text = message.isEmpty ? itemMessage : message
                unknownMessages.append("\(targetPrefix)\(text.isEmpty ? "未返回 taskId，无法确认最终状态" : text)")
            case .failed(let message):
                let text = message.isEmpty ? itemMessage : message
                failureMessages.append("\(targetPrefix)\(text.isEmpty ? "批量发送项失败" : text)")
            }
        }

        if !failureMessages.isEmpty {
            throw OpenApiMediaSendError.taskFailed(
                action,
                openApiFriendlyTaskFailureMessage(failureMessages.prefix(3).joined(separator: "；"), action: action)
            )
        }
        if !unknownMessages.isEmpty {
            throw OpenApiMediaSendError.taskResultUnknown(
                action,
                unknownMessages.prefix(3).joined(separator: "；")
            )
        }

        if !items.isEmpty {
            return uniqueOpenApiTaskIDs(taskIDs)
        }

        if let taskID = openApiTaskID(from: payload) {
            try await awaitOpenApiTaskCompletion(taskID: taskID, action: action)
            return [taskID]
        }

        let failCount = openApiIntFromAny(payload["failCount"]) ?? 0
        if failCount > 0 {
            let message = openApiFirstString(in: payload, keys: ["message", "error", "errorMessage", "detail"])
            throw OpenApiMediaSendError.taskFailed(
                action,
                openApiFriendlyTaskFailureMessage(message.isEmpty ? "批量接口返回 failCount=\(failCount)" : message, action: action)
            )
        }
        let unknownCount = openApiIntFromAny(payload["unknownCount"]) ?? 0
        if unknownCount > 0 {
            throw OpenApiMediaSendError.taskResultUnknown(action, "批量接口返回 unknownCount=\(unknownCount)，未返回可轮询 taskId。")
        }
        let successCount = openApiIntFromAny(payload["successCount"]) ?? 0
        let acceptedCount = openApiIntFromAny(payload["acceptedCount"]) ?? 0
        let requestedCount = openApiIntFromAny(payload["requestedCount"]) ?? 0
        if successCount > 0 {
            return []
        }
        if acceptedCount > 0 || requestedCount > 0 {
            throw OpenApiMediaSendError.taskResultUnknown(
                action,
                "批量接口仅返回受理数量 acceptedCount=\(acceptedCount)，未返回 taskId/taskResultUrl，不能标记为真实已发送。"
            )
        }
        throw OpenApiMediaSendError.taskResultUnknown(
            action,
            "批量接口未返回 items/taskId/successCount；HTTP \(result.statusCode)：\(openApiDiagnosticBody(result.rawText))"
        )
    }

}
