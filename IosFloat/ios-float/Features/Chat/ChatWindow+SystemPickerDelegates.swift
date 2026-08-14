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

enum AIStreamingError: LocalizedError {
    case invalidEndpoint
    case missingAPIKey
    case httpStatus(Int, String = "")
    case emptyResponse
    case serviceError(String)

    var errorDescription: String? {
        switch self {
        case .invalidEndpoint: return "AI 接口地址无效"
        case .missingAPIKey: return "AI API Key 未配置"
        case .httpStatus(let status, let detail):
            let text = detail.trimmingCharacters(in: .whitespacesAndNewlines)
            return text.isEmpty ? "AI 接口返回 HTTP \(status)" : "AI 接口返回 HTTP \(status)：\(text)"
        case .emptyResponse: return "AI 接口未返回有效正文"
        case .serviceError(let message): return message.isEmpty ? "AI 服务返回错误" : message
        }
    }
}


extension ChatWindowViewController: UIImagePickerControllerDelegate, UINavigationControllerDelegate {
    func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
        picker.dismiss(animated: true)
    }

    func imagePickerController(
        _ picker: UIImagePickerController,
        didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]
    ) {
        let mediaType = info[.mediaType] as? String
        picker.dismiss(animated: true) { [weak self] in
            guard let self else { return }
            if mediaType == UTType.movie.identifier {
                let sourceURL = (info[.mediaURL] as? URL).map {
                    self.persistedTemporaryURL(for: $0, fallbackExtension: "mov")
                }
                guard let sourceURL else { return }
                self.showNotice("正在处理视频为 MP4")
                Task { @MainActor in
                    do {
                        let url = try await self.normalizedMP4VideoURL(for: sourceURL)
                        self.presentAttachmentAccessScopePicker(
                            type: .video,
                            url: url,
                            title: url.lastPathComponent,
                            detail: "视频已添加 · MP4",
                            sourceView: self.inputBar
                        )
                    } catch {
                        self.showNotice("视频处理失败：\(error.localizedDescription)")
                    }
                }
            } else {
                let isCamera = picker.sourceType == .camera
                guard let image = (info[.editedImage] ?? info[.originalImage]) as? UIImage else { return }
                self.enqueueSelectedImages(
                    [
                        SelectedImageAttachment(
                            image: image,
                            type: isCamera ? .capturedPhoto : .image,
                            title: isCamera ? "\u{73b0}\u{573a}\u{62cd}\u{6444}\u{7167}\u{7247}" : "\u{5df2}\u{9009}\u{62e9}\u{56fe}\u{7247}",
                            detail: "\u{56fe}\u{7247}\u{5df2}\u{6dfb}\u{52a0}"
                        )
                    ],
                    appending: true
                )
            }
        }
    }
}

extension ChatWindowViewController: PHPickerViewControllerDelegate {
    func picker(_ picker: PHPickerViewController, didFinishPicking results: [PHPickerResult]) {
        picker.dismiss(animated: true)
        guard !results.isEmpty else { return }

        let group = DispatchGroup()
        let attachmentQueue = DispatchQueue(label: "local.ios-float.photo-picker.attachments")
        var sources = Array<SelectedImageAttachment?>(repeating: nil, count: results.count)

        for (index, result) in results.enumerated() {
            let provider = result.itemProvider
            guard provider.canLoadObject(ofClass: UIImage.self) else { continue }
            group.enter()
            provider.loadObject(ofClass: UIImage.self) { [weak self] object, _ in
                defer { group.leave() }
                guard self != nil, let image = object as? UIImage else { return }
                attachmentQueue.sync {
                    sources[index] = SelectedImageAttachment(
                        image: image,
                        type: .image,
                        title: results.count > 1 ? "已选择图片 \(index + 1)" : "已选择图片",
                        detail: "图片已添加"
                    )
                }
            }
        }

        group.notify(queue: .main) { [weak self] in
            let resolved = attachmentQueue.sync { sources.compactMap { $0 } }
            guard !resolved.isEmpty else {
                self?.showNotice("图片读取失败")
                return
            }
            self?.enqueueSelectedImages(resolved, appending: true)
        }
    }
}

extension ChatWindowViewController: UIDocumentPickerDelegate {
    func documentPicker(_ controller: UIDocumentPickerViewController, didPickDocumentsAt urls: [URL]) {
        guard let url = urls.first else { return }
        let localURL = persistedTemporaryURL(for: url, fallbackExtension: "dat")
        let displayName = url.lastPathComponent.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            ? localURL.lastPathComponent
            : url.lastPathComponent
        presentAttachmentAccessScopePicker(
            type: .file,
            url: localURL,
            title: displayName,
            detail: "\u{6587}\u{4ef6}\u{5df2}\u{6dfb}\u{52a0}",
            sourceView: inputBar
        )
    }
}

extension ChatWindowViewController: QLPreviewControllerDataSource {
    func numberOfPreviewItems(in controller: QLPreviewController) -> Int {
        previewURL == nil ? 0 : 1
    }

    func previewController(_ controller: QLPreviewController, previewItemAt index: Int) -> QLPreviewItem {
        (previewURL ?? FileManager.default.temporaryDirectory) as NSURL
    }
}


extension ChatWindowViewController {
    @objc func showAIConversation() {
        setEmojiPanelVisible(false, animated: true)
        view.endEditing(true)

        let controller = UIViewController()
        controller.view.backgroundColor = UIColor(white: 0.96, alpha: 1)
        controller.title = "AI"

        let providerControl = UISegmentedControl(items: AIProvider.allCases.map(\.title))
        providerControl.selectedSegmentIndex = selectedAIProvider.rawValue
        providerControl.translatesAutoresizingMaskIntoConstraints = false

        let titleLabel = UILabel()
        titleLabel.text = selectedAIProvider.title
        titleLabel.font = .systemFont(ofSize: 20, weight: .semibold)
        titleLabel.textColor = UIColor(red: 0.12, green: 0.14, blue: 0.15, alpha: 1)
        titleLabel.translatesAutoresizingMaskIntoConstraints = false

        let bodyLabel = UILabel()
        bodyLabel.text = aiDescription(for: selectedAIProvider)
        bodyLabel.font = .systemFont(ofSize: 15)
        bodyLabel.textColor = UIColor(red: 0.24, green: 0.27, blue: 0.30, alpha: 1)
        bodyLabel.numberOfLines = 0
        bodyLabel.translatesAutoresizingMaskIntoConstraints = false

        let modelButton = UIButton(type: .system)
        modelButton.contentHorizontalAlignment = .left
        modelButton.backgroundColor = .white
        modelButton.layer.cornerRadius = 9
        modelButton.layer.borderWidth = 1
        modelButton.layer.borderColor = UIColor.black.withAlphaComponent(0.10).cgColor
        modelButton.applyContentInsets(top: 0, leading: 12, bottom: 0, trailing: 12)
        modelButton.showsMenuAsPrimaryAction = true
        modelButton.translatesAutoresizingMaskIntoConstraints = false

        let configButton = UIButton(type: .system)
        configButton.contentHorizontalAlignment = .left
        configButton.backgroundColor = .white
        configButton.layer.cornerRadius = 9
        configButton.layer.borderWidth = 1
        configButton.layer.borderColor = UIColor.black.withAlphaComponent(0.10).cgColor
        configButton.applyContentInsets(top: 0, leading: 12, bottom: 0, trailing: 12)
        configButton.setTitle("AI配置：访问地址 / API Key", for: .normal)
        configButton.translatesAutoresizingMaskIntoConstraints = false

        let assistantButton = UIButton(type: .system)
        assistantButton.contentHorizontalAlignment = .left
        assistantButton.backgroundColor = .white
        assistantButton.layer.cornerRadius = 9
        assistantButton.layer.borderWidth = 1
        assistantButton.layer.borderColor = UIColor.black.withAlphaComponent(0.10).cgColor
        assistantButton.applyContentInsets(top: 0, leading: 12, bottom: 0, trailing: 12)
        assistantButton.setTitle("进入 AI 对话页", for: .normal)
        assistantButton.translatesAutoresizingMaskIntoConstraints = false

        let inputField = UITextField()
        inputField.placeholder = "\u{8f93}\u{5165} AI \u{6307}\u{4ee4}"
        inputField.borderStyle = .roundedRect
        inputField.translatesAutoresizingMaskIntoConstraints = false

        let sendButton = UIButton(type: .system)
        sendButton.setTitle("\u{751f}\u{6210}\u{8349}\u{7a3f}", for: .normal)
        sendButton.titleLabel?.font = .systemFont(ofSize: 16, weight: .semibold)
        sendButton.backgroundColor = UIColor(red: 0.47, green: 0.83, blue: 0.08, alpha: 1)
        sendButton.tintColor = .white
        sendButton.layer.cornerRadius = 10
        sendButton.translatesAutoresizingMaskIntoConstraints = false
        sendButton.addAction(UIAction { [weak self, weak inputField, weak controller] _ in
            guard let self else { return }
            let prompt = inputField?.text?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            switch self.selectedAIProvider {
            case .claude:
                self.startClaudeStreamingReply(prompt: prompt)
                controller?.dismiss(animated: true)
            case .codex:
                self.startCodexStreamingReply(prompt: prompt)
                controller?.dismiss(animated: true)
            }
        }, for: .touchUpInside)

        let directReplyButton = UIButton(type: .system)
        directReplyButton.setTitle("直接回复", for: .normal)
        directReplyButton.titleLabel?.font = .systemFont(ofSize: 16, weight: .semibold)
        directReplyButton.backgroundColor = UIColor(red: 0.10, green: 0.43, blue: 0.95, alpha: 1)
        directReplyButton.tintColor = .white
        directReplyButton.layer.cornerRadius = 10
        directReplyButton.translatesAutoresizingMaskIntoConstraints = false
        directReplyButton.addAction(UIAction { [weak self, weak inputField, weak controller] _ in
            guard let self else { return }
            let prompt = inputField?.text?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            switch self.selectedAIProvider {
            case .claude:
                self.startClaudeStreamingDirectReply(prompt: prompt)
                controller?.dismiss(animated: true)
            case .codex:
                self.startCodexStreamingDirectReply(prompt: prompt)
                controller?.dismiss(animated: true)
            }
        }, for: .touchUpInside)

        configButton.addAction(UIAction { [weak self] _ in
            guard let self else { return }
            switch self.selectedAIProvider {
            case .claude:
                self.presentClaudeConfigurationEditor()
            case .codex:
                self.presentCodexConfigurationEditor()
            }
        }, for: .touchUpInside)

        assistantButton.addAction(UIAction { [weak self, weak controller] _ in
            guard let self else { return }
            self.presentAIAssistantPage(for: self.selectedAIProvider, in: controller?.navigationController)
        }, for: .touchUpInside)

        let updateProviderUI = { [weak self, weak titleLabel, weak bodyLabel, weak modelButton, weak configButton, weak assistantButton] in
            guard let self else { return }
            titleLabel?.text = self.selectedAIProvider.title
            bodyLabel?.text = self.aiDescription(for: self.selectedAIProvider)
            let modelName = self.selectedAIProvider == .claude ? self.claudeConfiguration.model : self.codexConfiguration.model
            modelButton?.setTitle("模型：\(modelName)", for: .normal)
            configButton?.setTitle("\(self.selectedAIProvider.title)配置：访问地址 / API Key", for: .normal)
            assistantButton?.setTitle("进入 \(self.selectedAIProvider.title) 对话页", for: .normal)
        }

        let updateModelMenu = { [weak self, weak modelButton] in
            guard let self, let modelButton else { return }
            let configuration = self.selectedAIProvider == .claude ? self.claudeConfiguration : self.codexConfiguration
            let fallbackModel = self.selectedAIProvider == .claude ? Self.defaultClaudeModel : Self.defaultCodexModel
            let configuredModel = configuration.model.trimmingCharacters(in: .whitespacesAndNewlines)
            let defaultModel = configuredModel.isEmpty ? fallbackModel : configuredModel
            let provider = self.selectedAIProvider
            let configuredModels = provider == .claude ? self.claudeModels : self.codexModels
            let models = Array(Set(configuredModels + [defaultModel, fallbackModel])).sorted()
            let selectedTitle = defaultModel
            modelButton.setTitle("模型：\(selectedTitle)", for: .normal)
            modelButton.menu = UIMenu(children: models.map { model in
                UIAction(title: model, state: model == selectedTitle ? .on : .off) { [weak self, weak modelButton] _ in
                    if provider == .claude {
                        self?.claudeConfiguration.model = model
                        self?.saveClaudeConfiguration()
                    } else {
                        self?.codexConfiguration.model = model
                        self?.saveCodexConfiguration()
                    }
                    modelButton?.setTitle("模型：\(model)", for: .normal)
                }
            })
        }

        providerControl.addAction(UIAction { [weak self] _ in
            guard let provider = AIProvider(rawValue: providerControl.selectedSegmentIndex) else { return }
            self?.selectedAIProvider = provider
            updateProviderUI()
            updateModelMenu()
        }, for: .valueChanged)

        updateProviderUI()
        updateModelMenu()
        fetchCodexModels { [weak self] models in
            DispatchQueue.main.async {
                guard let self else { return }
                self.codexModels = models
                updateModelMenu()
            }
        }
        fetchClaudeModels { [weak self] models in
            DispatchQueue.main.async {
                guard let self else { return }
                self.claudeModels = models
                updateModelMenu()
            }
        }

        controller.view.addSubview(providerControl)
        controller.view.addSubview(titleLabel)
        controller.view.addSubview(bodyLabel)
        controller.view.addSubview(modelButton)
        controller.view.addSubview(configButton)
        controller.view.addSubview(assistantButton)
        controller.view.addSubview(inputField)
        controller.view.addSubview(sendButton)
        controller.view.addSubview(directReplyButton)

        NSLayoutConstraint.activate([
            providerControl.topAnchor.constraint(equalTo: controller.view.safeAreaLayoutGuide.topAnchor, constant: 18),
            providerControl.leadingAnchor.constraint(equalTo: controller.view.leadingAnchor, constant: 20),
            providerControl.trailingAnchor.constraint(equalTo: controller.view.trailingAnchor, constant: -20),
            providerControl.heightAnchor.constraint(equalToConstant: 34),

            titleLabel.topAnchor.constraint(equalTo: providerControl.bottomAnchor, constant: 20),
            titleLabel.leadingAnchor.constraint(equalTo: controller.view.leadingAnchor, constant: 20),
            titleLabel.trailingAnchor.constraint(equalTo: controller.view.trailingAnchor, constant: -20),

            bodyLabel.topAnchor.constraint(equalTo: titleLabel.bottomAnchor, constant: 12),
            bodyLabel.leadingAnchor.constraint(equalTo: titleLabel.leadingAnchor),
            bodyLabel.trailingAnchor.constraint(equalTo: titleLabel.trailingAnchor),

            modelButton.topAnchor.constraint(equalTo: bodyLabel.bottomAnchor, constant: 18),
            modelButton.leadingAnchor.constraint(equalTo: titleLabel.leadingAnchor),
            modelButton.trailingAnchor.constraint(equalTo: titleLabel.trailingAnchor),
            modelButton.heightAnchor.constraint(equalToConstant: 42),

            configButton.topAnchor.constraint(equalTo: modelButton.bottomAnchor, constant: 10),
            configButton.leadingAnchor.constraint(equalTo: titleLabel.leadingAnchor),
            configButton.trailingAnchor.constraint(equalTo: titleLabel.trailingAnchor),
            configButton.heightAnchor.constraint(equalToConstant: 42),

            assistantButton.topAnchor.constraint(equalTo: configButton.bottomAnchor, constant: 10),
            assistantButton.leadingAnchor.constraint(equalTo: titleLabel.leadingAnchor),
            assistantButton.trailingAnchor.constraint(equalTo: titleLabel.trailingAnchor),
            assistantButton.heightAnchor.constraint(equalToConstant: 42),

            inputField.topAnchor.constraint(equalTo: assistantButton.bottomAnchor, constant: 14),
            inputField.leadingAnchor.constraint(equalTo: titleLabel.leadingAnchor),
            inputField.trailingAnchor.constraint(equalTo: titleLabel.trailingAnchor),
            inputField.heightAnchor.constraint(equalToConstant: 44),

            sendButton.topAnchor.constraint(equalTo: inputField.bottomAnchor, constant: 14),
            sendButton.leadingAnchor.constraint(equalTo: titleLabel.leadingAnchor),
            sendButton.trailingAnchor.constraint(equalTo: titleLabel.trailingAnchor),
            sendButton.heightAnchor.constraint(equalToConstant: 44),

            directReplyButton.topAnchor.constraint(equalTo: sendButton.bottomAnchor, constant: 10),
            directReplyButton.leadingAnchor.constraint(equalTo: titleLabel.leadingAnchor),
            directReplyButton.trailingAnchor.constraint(equalTo: titleLabel.trailingAnchor),
            directReplyButton.heightAnchor.constraint(equalToConstant: 44)
        ])

        let navigationController = UINavigationController(rootViewController: controller)
        controller.navigationItem.rightBarButtonItem = UIBarButtonItem(
            barButtonSystemItem: .done,
            target: self,
            action: #selector(dismissPresentedController)
        )
        present(navigationController, animated: true)
    }

    func aiDescription(for provider: AIProvider) -> String {
        switch provider {
        case .claude:
            return "Claude AI 使用独立配置生成内容。当前模型：\(claudeConfiguration.model)。可手动填写访问地址、API Key，也可进入 Claude 对话页。"
        case .codex:
            return "Codex 使用全局配置生成内容。当前模型：\(codexConfiguration.model)。生成、直接回复、Codex 会话发送都会使用同一套配置。"
        }
    }

    func loadCodexConfiguration() {
        let defaults = UserDefaults.standard
        let baseURLText = normalizeCodexBaseURLText(
            ChatSQLiteStore.shared.string(forKey: Self.codexBaseURLStorageKey)
                ?? defaults.string(forKey: Self.codexBaseURLStorageKey)
                ?? Self.defaultCodexBaseURLText
        )
        let apiKey = normalizeCodexAPIKey(
            AppSecretStore.migrateLegacySecret(forKey: Self.codexAPIKeyStorageKey)
                ?? Self.defaultCodexAPIKey
        )
        let model = normalizeCodexModel(
            ChatSQLiteStore.shared.string(forKey: Self.codexModelStorageKey)
                ?? defaults.string(forKey: Self.codexModelStorageKey)
                ?? Self.defaultCodexModel
        )
        codexConfiguration = CodexConfiguration(
            baseURLText: baseURLText,
            apiKey: apiKey,
            model: model
        )
        saveCodexConfiguration()
    }

    func saveCodexConfiguration() {
        codexConfiguration = CodexConfiguration(
            baseURLText: normalizeCodexBaseURLText(codexConfiguration.baseURLText),
            apiKey: normalizeCodexAPIKey(codexConfiguration.apiKey),
            model: normalizeCodexModel(codexConfiguration.model)
        )
        ChatSQLiteStore.shared.setString(codexConfiguration.baseURLText, forKey: Self.codexBaseURLStorageKey)
        AppSecretStore.setString(codexConfiguration.apiKey, forKey: Self.codexAPIKeyStorageKey)
        AppSecretStore.clearLegacySecret(forKey: Self.codexAPIKeyStorageKey)
        ChatSQLiteStore.shared.setString(codexConfiguration.model, forKey: Self.codexModelStorageKey)
    }

    func loadClaudeConfiguration() {
        let defaults = UserDefaults.standard
        let baseURLText = normalizeClaudeBaseURLText(
            ChatSQLiteStore.shared.string(forKey: Self.claudeBaseURLStorageKey)
                ?? defaults.string(forKey: Self.claudeBaseURLStorageKey)
                ?? Self.defaultClaudeBaseURLText
        )
        let apiKey = normalizeClaudeAPIKey(
            AppSecretStore.migrateLegacySecret(forKey: Self.claudeAPIKeyStorageKey)
                ?? Self.defaultClaudeAPIKey
        )
        let model = normalizeClaudeModel(
            ChatSQLiteStore.shared.string(forKey: Self.claudeModelStorageKey)
                ?? defaults.string(forKey: Self.claudeModelStorageKey)
                ?? Self.defaultClaudeModel
        )
        claudeConfiguration = CodexConfiguration(
            baseURLText: baseURLText,
            apiKey: apiKey,
            model: model
        )
        saveClaudeConfiguration()
    }

    func saveClaudeConfiguration() {
        claudeConfiguration = CodexConfiguration(
            baseURLText: normalizeClaudeBaseURLText(claudeConfiguration.baseURLText),
            apiKey: normalizeClaudeAPIKey(claudeConfiguration.apiKey),
            model: normalizeClaudeModel(claudeConfiguration.model)
        )
        ChatSQLiteStore.shared.setString(claudeConfiguration.baseURLText, forKey: Self.claudeBaseURLStorageKey)
        AppSecretStore.setString(claudeConfiguration.apiKey, forKey: Self.claudeAPIKeyStorageKey)
        AppSecretStore.clearLegacySecret(forKey: Self.claudeAPIKeyStorageKey)
        ChatSQLiteStore.shared.setString(claudeConfiguration.model, forKey: Self.claudeModelStorageKey)
    }

    func loadAutoReplyConfiguration() {
        let defaults = UserDefaults.standard
        let baseURLText = normalizeCodexBaseURLText(
            ChatSQLiteStore.shared.string(forKey: Self.autoReplyBaseURLStorageKey)
                ?? defaults.string(forKey: Self.autoReplyBaseURLStorageKey)
                ?? codexConfiguration.baseURLText
        )
        let apiKey = normalizeCodexAPIKey(
            AppSecretStore.migrateLegacySecret(forKey: Self.autoReplyAPIKeyStorageKey)
                ?? codexConfiguration.apiKey
        )
        let model = normalizeCodexModel(
            ChatSQLiteStore.shared.string(forKey: Self.autoReplyModelStorageKey)
                ?? defaults.string(forKey: Self.autoReplyModelStorageKey)
                ?? codexConfiguration.model
        )
        autoReplyConfiguration = AutoReplyConfiguration(
            isEnabled: ChatSQLiteStore.shared.bool(forKey: Self.autoReplyEnabledStorageKey)
                ?? defaults.bool(forKey: Self.autoReplyEnabledStorageKey),
            baseURLText: baseURLText,
            apiKey: apiKey,
            model: model
        )
    }

    func saveAutoReplyConfiguration() {
        autoReplyConfiguration = AutoReplyConfiguration(
            isEnabled: autoReplyConfiguration.isEnabled,
            baseURLText: normalizeCodexBaseURLText(autoReplyConfiguration.baseURLText),
            apiKey: normalizeCodexAPIKey(autoReplyConfiguration.apiKey),
            model: normalizeCodexModel(autoReplyConfiguration.model)
        )
        ChatSQLiteStore.shared.setBool(autoReplyConfiguration.isEnabled, forKey: Self.autoReplyEnabledStorageKey)
        ChatSQLiteStore.shared.setString(autoReplyConfiguration.baseURLText, forKey: Self.autoReplyBaseURLStorageKey)
        AppSecretStore.setString(autoReplyConfiguration.apiKey, forKey: Self.autoReplyAPIKeyStorageKey)
        AppSecretStore.clearLegacySecret(forKey: Self.autoReplyAPIKeyStorageKey)
        ChatSQLiteStore.shared.setString(autoReplyConfiguration.model, forKey: Self.autoReplyModelStorageKey)
    }

    func loadSideEffectConfiguration() {
        let defaults = UserDefaults.standard
        let storedIndex = ChatSQLiteStore.shared.string(forKey: Self.sideEffectTemplateStorageKey).flatMap(Int.init)
            ?? defaults.integer(forKey: Self.sideEffectTemplateStorageKey)
        sideEffectTemplateIndex = min(max(storedIndex, 0), sideEffectTemplates.count - 1)
        if let fillHex = ChatSQLiteStore.shared.string(forKey: Self.sideEffectFillColorStorageKey)
            ?? defaults.string(forKey: Self.sideEffectFillColorStorageKey),
           let color = UIColor(hexString: fillHex) {
            sideEffectFillColor = color.withAlphaComponent(0.88)
        }
        if let strokeHex = ChatSQLiteStore.shared.string(forKey: Self.sideEffectStrokeColorStorageKey)
            ?? defaults.string(forKey: Self.sideEffectStrokeColorStorageKey),
           let color = UIColor(hexString: strokeHex) {
            sideEffectStrokeColor = color.withAlphaComponent(0.48)
        }
    }

    func saveSideEffectConfiguration() {
        ChatSQLiteStore.shared.setString(String(sideEffectTemplateIndex), forKey: Self.sideEffectTemplateStorageKey)
        ChatSQLiteStore.shared.setString(sideEffectFillColor.hexString, forKey: Self.sideEffectFillColorStorageKey)
        ChatSQLiteStore.shared.setString(sideEffectStrokeColor.hexString, forKey: Self.sideEffectStrokeColorStorageKey)
        rightToolCollectionView.reloadData()
    }

    func loadBubble3DAppearanceConfiguration() {
        bubble3DAppearanceEnabled = ChatSQLiteStore.shared.bool(
            forKey: Self.bubble3DAppearanceStorageKey
        ) ?? false
    }

    func loadLeftSidebarDisplayModeConfiguration() {
        let rawValue = ChatSQLiteStore.shared.string(forKey: Self.leftSidebarDisplayModeStorageKey)
            ?? UserDefaults.standard.string(forKey: Self.leftSidebarDisplayModeStorageKey)
        leftSidebarDisplayMode = rawValue.flatMap(LeftSidebarDisplayMode.init(rawValue:)) ?? .friendsAndGroups
    }

    func setLeftSidebarDisplayMode(_ mode: LeftSidebarDisplayMode) {
        guard leftSidebarDisplayMode != mode else { return }
        leftSidebarDisplayMode = mode
        ChatSQLiteStore.shared.setString(mode.rawValue, forKey: Self.leftSidebarDisplayModeStorageKey)
        cancelLeftScrollPreview()
        invalidateVisibleDataCaches()
        UIView.performWithoutAnimation {
            leftCollectionView.reloadData()
            rightToolCollectionView.reloadData()
            updateLeftCollectionBounceInsets()
            updateLeftFriendTotalBadge()
        }
        DispatchQueue.main.async { [weak self] in
            self?.prefetchCurrentSidebarAvatarWindow()
            self?.ensureSelectedLeftSidebarItemVisible(animated: false)
            self?.scheduleConnectionUpdate()
        }
    }

    func toggleBubble3DAppearance() {
        bubble3DAppearanceEnabled.toggle()
        ChatSQLiteStore.shared.setBool(
            bubble3DAppearanceEnabled,
            forKey: Self.bubble3DAppearanceStorageKey
        )
        clearMessageHeightCache()
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
        UIView.transition(
            with: messageCollectionView,
            duration: 0.24,
            options: [.transitionCrossDissolve, .allowAnimatedContent]
        ) { [weak self] in
            self?.messageCollectionView.reloadData()
        }
        rightToolCollectionView.reloadData()
        DispatchQueue.main.async { [weak self] in
            self?.updateConnections()
        }
        showNotice(bubble3DAppearanceEnabled ? "3D气泡已开启" : "已切换为2D气泡")
    }

    var isAutomaticBackgroundRemovalSupported: Bool {
        if #available(iOS 17.0, *) {
            return true
        }
        return false
    }

    func loadAutomaticBackgroundRemovalConfiguration() {
        automaticBackgroundRemovalEnabled = ChatSQLiteStore.shared.bool(
            forKey: Self.automaticBackgroundRemovalStorageKey
        ) ?? false
    }

    func saveAutomaticBackgroundRemovalConfiguration() {
        ChatSQLiteStore.shared.setBool(
            automaticBackgroundRemovalEnabled,
            forKey: Self.automaticBackgroundRemovalStorageKey
        )
        rightToolCollectionView.reloadData()
    }

    func presentBackgroundRemovalConfigurationPage() {
        let controller = BackgroundRemovalConfigurationViewController(
            isEnabled: automaticBackgroundRemovalEnabled,
            isSupported: isAutomaticBackgroundRemovalSupported
        )
        controller.onEnabledChanged = { [weak self] isEnabled in
            guard let self else { return }
            self.automaticBackgroundRemovalEnabled = isEnabled
            self.saveAutomaticBackgroundRemovalConfiguration()
            self.showNotice(isEnabled ? "智能扣图已开启" : "智能扣图已关闭")
        }
        controller.onChooseImage = { [weak self, weak controller] in
            guard let self else { return }
            controller?.navigationController?.popViewController(animated: true)
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.28) {
                self.presentMediaLibrary(mediaTypes: [UTType.image.identifier])
            }
        }
        navigationController?.setNavigationBarHidden(false, animated: true)
        navigationController?.pushViewController(controller, animated: true)
    }

    func presentSideEffectConfigurationPage() {
        let controller = SideEffectConfigurationViewController(
            templates: sideEffectTemplates,
            selectedIndex: sideEffectTemplateIndex,
            fillColor: sideEffectFillColor,
            strokeColor: sideEffectStrokeColor
        )
        controller.onChange = { [weak self] selectedIndex, fillColor, strokeColor in
            guard let self else { return }
            self.sideEffectTemplateIndex = selectedIndex
            self.sideEffectFillColor = fillColor.withAlphaComponent(0.88)
            self.sideEffectStrokeColor = strokeColor.withAlphaComponent(0.48)
            self.saveSideEffectConfiguration()
        }
        navigationController?.setNavigationBarHidden(false, animated: true)
        navigationController?.pushViewController(controller, animated: true)
    }

    var sideEffectTemplates: [SideEffectTemplate] {
        [
            SideEffectTemplate(index: 0, title: "默认抛物线", subtitle: "当前边缘水珠效果", symbolName: "water.waves"),
            SideEffectTemplate(index: 1, title: "圆润水滴", subtitle: "更短更饱满", symbolName: "drop.fill"),
            SideEffectTemplate(index: 2, title: "长弧面板", subtitle: "Good Lock 长边", symbolName: "sidebar.right"),
            SideEffectTemplate(index: 3, title: "轻薄丝带", subtitle: "细长透明", symbolName: "scribble.variable"),
            SideEffectTemplate(index: 4, title: "胶囊浮层", subtitle: "圆角柱状", symbolName: "capsule.portrait.fill"),
            SideEffectTemplate(index: 5, title: "弹性波纹", subtitle: "更强拉伸感", symbolName: "waveform.path")
        ]
    }

    func normalizeCodexBaseURLText(_ text: String) -> String {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return Self.defaultCodexBaseURLText }
        guard var components = URLComponents(string: trimmed),
              components.host?.lowercased() == "cc2.cx"
        else {
            return trimmed
        }

        let path = components.path.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        if path.isEmpty {
            components.path = "/v1"
            return components.string ?? Self.defaultCodexBaseURLText
        }
        return trimmed
    }

    func normalizeCodexAPIKey(_ key: String) -> String {
        key.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    func normalizeCodexModel(_ model: String) -> String {
        let trimmed = model.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? Self.defaultCodexModel : trimmed
    }

    func normalizeClaudeBaseURLText(_ text: String) -> String {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return Self.defaultClaudeBaseURLText }
        return trimmed
    }

    func normalizeClaudeAPIKey(_ key: String) -> String {
        key.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    func normalizeClaudeModel(_ model: String) -> String {
        let trimmed = model.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? Self.defaultClaudeModel : trimmed
    }

    func presentCodexConfigurationEditor() {
        presentAIConfigurationEditor(for: .codex)
    }

    func presentClaudeConfigurationEditor() {
        presentAIConfigurationEditor(for: .claude)
    }

    func presentAIConfigurationEditor(for provider: AIProvider) {
        let currentConfiguration = provider == .claude ? claudeConfiguration : codexConfiguration
        let defaultModel = provider == .claude ? Self.defaultClaudeModel : Self.defaultCodexModel
        let controller = UIViewController()
        controller.view.backgroundColor = UIColor(white: 0.96, alpha: 1)
        controller.title = "\(provider.title)配置"

        let scrollView = UIScrollView()
        scrollView.keyboardDismissMode = .interactive
        scrollView.translatesAutoresizingMaskIntoConstraints = false

        let contentView = UIView()
        contentView.translatesAutoresizingMaskIntoConstraints = false
        scrollView.addSubview(contentView)

        let titleLabel = UILabel()
        titleLabel.text = "\(provider.title) 接口配置"
        titleLabel.font = .systemFont(ofSize: 22, weight: .semibold)
        titleLabel.textColor = UIColor(red: 0.10, green: 0.12, blue: 0.14, alpha: 1)
        titleLabel.translatesAutoresizingMaskIntoConstraints = false

        let descriptionLabel = UILabel()
        descriptionLabel.text = provider == .claude
            ? "这里填写 Claude AI 接口。Anthropic 地址会按 Claude 格式调用；其它地址按 OpenAI 兼容格式调用。"
            : "这里填写 OpenAI 格式接口。保存后，右下角机器人、Codex 会话、@codex 回复都会使用同一套访问地址和 API Key。"
        descriptionLabel.font = .systemFont(ofSize: 14)
        descriptionLabel.textColor = UIColor(red: 0.36, green: 0.39, blue: 0.42, alpha: 1)
        descriptionLabel.numberOfLines = 0
        descriptionLabel.translatesAutoresizingMaskIntoConstraints = false

        let baseURLLabel = makeConfigurationFieldLabel("访问地址")
        let baseURLField = UITextField()
        configureAutoReplyTextField(baseURLField, placeholder: provider == .claude ? "例如 https://api.anthropic.com/v1" : "例如 https://cc2.cx/v1")
        baseURLField.text = currentConfiguration.baseURLText
        baseURLField.keyboardType = .URL
        baseURLField.autocapitalizationType = .none

        let apiKeyLabel = makeConfigurationFieldLabel("API KEY")
        let apiKeyField = UITextField()
        configureAutoReplyTextField(apiKeyField, placeholder: "请输入 API Key")
        apiKeyField.text = currentConfiguration.apiKey
        apiKeyField.autocapitalizationType = .none
        apiKeyField.textContentType = .password

        let modelLabel = makeConfigurationFieldLabel("模型")
        let modelField = UITextField()
        configureAutoReplyTextField(modelField, placeholder: provider == .claude ? "例如 claude-sonnet-4-20250514" : "例如 gpt-5.5")
        modelField.text = currentConfiguration.model
        modelField.autocapitalizationType = .none

        let modelButton = UIButton(type: .system)
        modelButton.contentHorizontalAlignment = .left
        modelButton.backgroundColor = .white
        modelButton.layer.cornerRadius = 10
        modelButton.layer.borderWidth = 1
        modelButton.layer.borderColor = UIColor.black.withAlphaComponent(0.10).cgColor
        modelButton.applyContentInsets(top: 0, leading: 12, bottom: 0, trailing: 12)
        modelButton.showsMenuAsPrimaryAction = true
        modelButton.translatesAutoresizingMaskIntoConstraints = false

        let fetchButton = UIButton(type: .system)
        configureAutoReplyActionButton(fetchButton, title: "获取模型列表", color: UIColor(red: 0.10, green: 0.43, blue: 0.95, alpha: 1))

        let saveButton = UIButton(type: .system)
        configureAutoReplyActionButton(saveButton, title: "保存配置", color: UIColor(red: 0.12, green: 0.57, blue: 0.34, alpha: 1))

        let statusLabel = UILabel()
        statusLabel.text = "当前模型：\(currentConfiguration.model)"
        statusLabel.font = .systemFont(ofSize: 13)
        statusLabel.textColor = UIColor(red: 0.42, green: 0.46, blue: 0.49, alpha: 1)
        statusLabel.numberOfLines = 0
        statusLabel.translatesAutoresizingMaskIntoConstraints = false

        let updateModelMenu = { [weak self, weak modelButton, weak modelField] in
            guard let self, let modelButton else { return }
            let currentModel = (modelField?.text ?? currentConfiguration.model)
                .trimmingCharacters(in: .whitespacesAndNewlines)
            let selectedModel = currentModel.isEmpty ? defaultModel : currentModel
            let configuredModels = provider == .claude ? self.claudeModels : self.codexModels
            let models = Array(Set(configuredModels + [selectedModel, defaultModel])).sorted()
            modelButton.setTitle("选择模型：\(selectedModel)", for: .normal)
            modelButton.menu = UIMenu(children: models.map { model in
                UIAction(title: model, state: model == selectedModel ? .on : .off) { [weak modelField, weak modelButton, weak statusLabel] _ in
                    modelField?.text = model
                    modelButton?.setTitle("选择模型：\(model)", for: .normal)
                    statusLabel?.text = "当前模型：\(model)"
                }
            })
        }

        let saveCurrentForm = { [weak self, weak baseURLField, weak apiKeyField, weak modelField, weak statusLabel] (showSavedNotice: Bool) in
            guard let self else { return }
            if provider == .claude {
                self.claudeConfiguration = CodexConfiguration(
                    baseURLText: self.normalizeClaudeBaseURLText(baseURLField?.text ?? ""),
                    apiKey: self.normalizeClaudeAPIKey(apiKeyField?.text ?? ""),
                    model: self.normalizeClaudeModel(modelField?.text ?? "")
                )
                self.saveClaudeConfiguration()
                statusLabel?.text = "已保存：\(self.claudeConfiguration.baseURLText)\n当前模型：\(self.claudeConfiguration.model)"
            } else {
                self.codexConfiguration = CodexConfiguration(
                    baseURLText: self.normalizeCodexBaseURLText(baseURLField?.text ?? ""),
                    apiKey: self.normalizeCodexAPIKey(apiKeyField?.text ?? ""),
                    model: self.normalizeCodexModel(modelField?.text ?? "")
                )
                self.saveCodexConfiguration()
                statusLabel?.text = "已保存：\(self.codexConfiguration.baseURLText)\n当前模型：\(self.codexConfiguration.model)"
            }
            if showSavedNotice {
                self.showNotice("\(provider.title)配置已保存")
            }
        }

        fetchButton.addAction(UIAction { [weak self, weak statusLabel] _ in
            saveCurrentForm(false)
            statusLabel?.text = "正在获取模型列表..."
            let fetch = provider == .claude ? self?.fetchClaudeModels : self?.fetchCodexModels
            fetch? { models in
                DispatchQueue.main.async {
                    if provider == .claude {
                        self?.claudeModels = models
                    } else {
                        self?.codexModels = models
                    }
                    updateModelMenu()
                    statusLabel?.text = models.isEmpty ? "未获取到模型，请检查访问地址和 API Key。" : "已获取 \(models.count) 个模型。"
                }
            }
        }, for: .touchUpInside)

        saveButton.addAction(UIAction { [weak controller] _ in
            saveCurrentForm(true)
            controller?.navigationController?.popViewController(animated: true)
        }, for: .touchUpInside)

        updateModelMenu()

        [
            titleLabel, descriptionLabel,
            baseURLLabel, baseURLField,
            apiKeyLabel, apiKeyField,
            modelLabel, modelField,
            modelButton, fetchButton, saveButton, statusLabel
        ].forEach(contentView.addSubview)
        controller.view.addSubview(scrollView)

        NSLayoutConstraint.activate([
            scrollView.topAnchor.constraint(equalTo: controller.view.safeAreaLayoutGuide.topAnchor),
            scrollView.leadingAnchor.constraint(equalTo: controller.view.leadingAnchor),
            scrollView.trailingAnchor.constraint(equalTo: controller.view.trailingAnchor),
            scrollView.bottomAnchor.constraint(equalTo: controller.view.bottomAnchor),

            contentView.topAnchor.constraint(equalTo: scrollView.contentLayoutGuide.topAnchor),
            contentView.leadingAnchor.constraint(equalTo: scrollView.contentLayoutGuide.leadingAnchor),
            contentView.trailingAnchor.constraint(equalTo: scrollView.contentLayoutGuide.trailingAnchor),
            contentView.bottomAnchor.constraint(equalTo: scrollView.contentLayoutGuide.bottomAnchor),
            contentView.widthAnchor.constraint(equalTo: scrollView.frameLayoutGuide.widthAnchor),

            titleLabel.topAnchor.constraint(equalTo: contentView.topAnchor, constant: 22),
            titleLabel.leadingAnchor.constraint(equalTo: contentView.leadingAnchor, constant: 20),
            titleLabel.trailingAnchor.constraint(equalTo: contentView.trailingAnchor, constant: -20),

            descriptionLabel.topAnchor.constraint(equalTo: titleLabel.bottomAnchor, constant: 10),
            descriptionLabel.leadingAnchor.constraint(equalTo: titleLabel.leadingAnchor),
            descriptionLabel.trailingAnchor.constraint(equalTo: titleLabel.trailingAnchor),

            baseURLLabel.topAnchor.constraint(equalTo: descriptionLabel.bottomAnchor, constant: 24),
            baseURLLabel.leadingAnchor.constraint(equalTo: titleLabel.leadingAnchor),
            baseURLLabel.trailingAnchor.constraint(equalTo: titleLabel.trailingAnchor),

            baseURLField.topAnchor.constraint(equalTo: baseURLLabel.bottomAnchor, constant: 8),
            baseURLField.leadingAnchor.constraint(equalTo: titleLabel.leadingAnchor),
            baseURLField.trailingAnchor.constraint(equalTo: titleLabel.trailingAnchor),
            baseURLField.heightAnchor.constraint(equalToConstant: 44),

            apiKeyLabel.topAnchor.constraint(equalTo: baseURLField.bottomAnchor, constant: 14),
            apiKeyLabel.leadingAnchor.constraint(equalTo: titleLabel.leadingAnchor),
            apiKeyLabel.trailingAnchor.constraint(equalTo: titleLabel.trailingAnchor),

            apiKeyField.topAnchor.constraint(equalTo: apiKeyLabel.bottomAnchor, constant: 8),
            apiKeyField.leadingAnchor.constraint(equalTo: titleLabel.leadingAnchor),
            apiKeyField.trailingAnchor.constraint(equalTo: titleLabel.trailingAnchor),
            apiKeyField.heightAnchor.constraint(equalToConstant: 44),

            modelLabel.topAnchor.constraint(equalTo: apiKeyField.bottomAnchor, constant: 14),
            modelLabel.leadingAnchor.constraint(equalTo: titleLabel.leadingAnchor),
            modelLabel.trailingAnchor.constraint(equalTo: titleLabel.trailingAnchor),

            modelField.topAnchor.constraint(equalTo: modelLabel.bottomAnchor, constant: 8),
            modelField.leadingAnchor.constraint(equalTo: titleLabel.leadingAnchor),
            modelField.trailingAnchor.constraint(equalTo: titleLabel.trailingAnchor),
            modelField.heightAnchor.constraint(equalToConstant: 44),

            modelButton.topAnchor.constraint(equalTo: modelField.bottomAnchor, constant: 10),
            modelButton.leadingAnchor.constraint(equalTo: titleLabel.leadingAnchor),
            modelButton.trailingAnchor.constraint(equalTo: titleLabel.trailingAnchor),
            modelButton.heightAnchor.constraint(equalToConstant: 44),

            fetchButton.topAnchor.constraint(equalTo: modelButton.bottomAnchor, constant: 18),
            fetchButton.leadingAnchor.constraint(equalTo: titleLabel.leadingAnchor),
            fetchButton.trailingAnchor.constraint(equalTo: titleLabel.trailingAnchor),
            fetchButton.heightAnchor.constraint(equalToConstant: 44),

            saveButton.topAnchor.constraint(equalTo: fetchButton.bottomAnchor, constant: 10),
            saveButton.leadingAnchor.constraint(equalTo: titleLabel.leadingAnchor),
            saveButton.trailingAnchor.constraint(equalTo: titleLabel.trailingAnchor),
            saveButton.heightAnchor.constraint(equalToConstant: 44),

            statusLabel.topAnchor.constraint(equalTo: saveButton.bottomAnchor, constant: 14),
            statusLabel.leadingAnchor.constraint(equalTo: titleLabel.leadingAnchor),
            statusLabel.trailingAnchor.constraint(equalTo: titleLabel.trailingAnchor),
            statusLabel.bottomAnchor.constraint(equalTo: contentView.bottomAnchor, constant: -28)
        ])

        controller.navigationItem.leftBarButtonItem = UIBarButtonItem(title: "返回", style: .plain, target: self, action: #selector(popTopController))
        if let presentedNavigationController = presentedViewController as? UINavigationController {
            presentedNavigationController.pushViewController(controller, animated: true)
        } else if let navigationController {
            navigationController.setNavigationBarHidden(false, animated: false)
            navigationController.pushViewController(controller, animated: true)
        } else {
            let navigationController = UINavigationController(rootViewController: controller)
            navigationController.modalPresentationStyle = .fullScreen
            controller.navigationItem.leftBarButtonItem = UIBarButtonItem(
                title: "返回",
                style: .plain,
                target: self,
                action: #selector(dismissPresentedController)
            )
            present(navigationController, animated: true)
        }
    }

    func makeConfigurationFieldLabel(_ text: String) -> UILabel {
        let label = UILabel()
        label.text = text
        label.font = .systemFont(ofSize: 14, weight: .semibold)
        label.textColor = UIColor(red: 0.16, green: 0.18, blue: 0.20, alpha: 1)
        label.translatesAutoresizingMaskIntoConstraints = false
        return label
    }

    func presentAutoReplyConfigurationPage() {
        let controller = UIViewController()
        controller.view.backgroundColor = UIColor(white: 0.96, alpha: 1)
        controller.title = "AI自动回复"

        let scrollView = UIScrollView()
        scrollView.translatesAutoresizingMaskIntoConstraints = false
        let contentView = UIView()
        contentView.translatesAutoresizingMaskIntoConstraints = false
        scrollView.addSubview(contentView)

        let titleLabel = UILabel()
        titleLabel.text = "自动回复机器人"
        titleLabel.font = .systemFont(ofSize: 22, weight: .semibold)
        titleLabel.textColor = UIColor(red: 0.10, green: 0.12, blue: 0.14, alpha: 1)
        titleLabel.translatesAutoresizingMaskIntoConstraints = false

        let descriptionLabel = UILabel()
        descriptionLabel.text = "开启后，左侧好友或群成员发来新消息时，会用当前选中的右侧帐号自动生成并发送回复。"
        descriptionLabel.font = .systemFont(ofSize: 14)
        descriptionLabel.textColor = UIColor(red: 0.36, green: 0.39, blue: 0.42, alpha: 1)
        descriptionLabel.numberOfLines = 0
        descriptionLabel.translatesAutoresizingMaskIntoConstraints = false

        let enableLabel = UILabel()
        enableLabel.text = "启用自动回复"
        enableLabel.font = .systemFont(ofSize: 16, weight: .medium)
        enableLabel.translatesAutoresizingMaskIntoConstraints = false

        let enableSwitch = UISwitch()
        enableSwitch.isOn = autoReplyConfiguration.isEnabled
        enableSwitch.translatesAutoresizingMaskIntoConstraints = false

        let robotLabel = UILabel()
        robotLabel.text = "回复机器人：Codex（OpenAI 格式）"
        robotLabel.font = .systemFont(ofSize: 15, weight: .medium)
        robotLabel.textColor = UIColor(red: 0.18, green: 0.30, blue: 0.48, alpha: 1)
        robotLabel.translatesAutoresizingMaskIntoConstraints = false

        let baseURLField = UITextField()
        configureAutoReplyTextField(baseURLField, placeholder: "访问地址，例如 https://cc2.cx/v1")
        baseURLField.text = autoReplyConfiguration.baseURLText
        baseURLField.keyboardType = .URL

        let apiKeyField = UITextField()
        configureAutoReplyTextField(apiKeyField, placeholder: "API Key")
        apiKeyField.text = autoReplyConfiguration.apiKey
        apiKeyField.autocapitalizationType = .none

        let modelField = UITextField()
        configureAutoReplyTextField(modelField, placeholder: "模型，例如 gpt-5.5")
        modelField.text = autoReplyConfiguration.model
        modelField.autocapitalizationType = .none

        let modelButton = UIButton(type: .system)
        modelButton.contentHorizontalAlignment = .left
        modelButton.backgroundColor = .white
        modelButton.layer.cornerRadius = 10
        modelButton.layer.borderWidth = 1
        modelButton.layer.borderColor = UIColor.black.withAlphaComponent(0.10).cgColor
        modelButton.applyContentInsets(top: 0, leading: 12, bottom: 0, trailing: 12)
        modelButton.showsMenuAsPrimaryAction = true
        modelButton.translatesAutoresizingMaskIntoConstraints = false

        let fetchButton = UIButton(type: .system)
        configureAutoReplyActionButton(fetchButton, title: "获取模型列表", color: UIColor(red: 0.10, green: 0.43, blue: 0.95, alpha: 1))

        let saveButton = UIButton(type: .system)
        configureAutoReplyActionButton(saveButton, title: "保存配置", color: UIColor(red: 0.12, green: 0.57, blue: 0.34, alpha: 1))

        let testButton = UIButton(type: .system)
        configureAutoReplyActionButton(testButton, title: "模拟收到消息测试", color: UIColor(red: 0.50, green: 0.38, blue: 0.78, alpha: 1))

        let statusLabel = UILabel()
        statusLabel.font = .systemFont(ofSize: 13)
        statusLabel.textColor = UIColor(red: 0.42, green: 0.46, blue: 0.49, alpha: 1)
        statusLabel.numberOfLines = 0
        statusLabel.text = autoReplyConfiguration.isEnabled ? "状态：已开启，等待新消息。" : "状态：未开启。"
        statusLabel.translatesAutoresizingMaskIntoConstraints = false

        let updateModelMenu = { [weak self, weak modelButton, weak modelField] in
            guard let self, let modelButton else { return }
            let currentModel = (modelField?.text ?? self.autoReplyConfiguration.model)
                .trimmingCharacters(in: .whitespacesAndNewlines)
            let selectedModel = currentModel.isEmpty ? Self.defaultCodexModel : currentModel
            let models = Array(Set(self.autoReplyModels + [selectedModel, Self.defaultCodexModel])).sorted()
            modelButton.setTitle("选择模型：\(selectedModel)", for: .normal)
            modelButton.menu = UIMenu(children: models.map { model in
                UIAction(title: model, state: model == selectedModel ? .on : .off) { [weak modelField, weak modelButton] _ in
                    modelField?.text = model
                    modelButton?.setTitle("选择模型：\(model)", for: .normal)
                }
            })
        }

        let saveCurrentForm = { [weak self, weak enableSwitch, weak baseURLField, weak apiKeyField, weak modelField] (showSavedNotice: Bool) in
            guard let self else { return }
            let wasEnabled = self.autoReplyConfiguration.isEnabled
            self.autoReplyConfiguration = AutoReplyConfiguration(
                isEnabled: enableSwitch?.isOn == true,
                baseURLText: self.normalizeCodexBaseURLText(baseURLField?.text ?? ""),
                apiKey: self.normalizeCodexAPIKey(apiKeyField?.text ?? ""),
                model: self.normalizeCodexModel(modelField?.text ?? "")
            )
            self.saveAutoReplyConfiguration()
            if self.autoReplyConfiguration.isEnabled && !wasEnabled {
                self.markExistingIncomingMessagesAsHandledForAutoReply()
            }
            self.configureAutoReplyTimer()
            self.rightToolCollectionView.reloadData()
            if showSavedNotice {
                self.showNotice(self.autoReplyConfiguration.isEnabled ? "AI自动回复已开启" : "AI自动回复已关闭")
            }
        }

        enableSwitch.addAction(UIAction { [weak statusLabel] _ in
            statusLabel?.text = enableSwitch.isOn ? "状态：保存后开启，后续新消息会自动回复。" : "状态：保存后关闭。"
        }, for: .valueChanged)

        fetchButton.addAction(UIAction { [weak self, weak statusLabel] _ in
            saveCurrentForm(false)
            statusLabel?.text = "状态：正在获取模型列表..."
            self?.fetchAutoReplyModels { models in
                DispatchQueue.main.async {
                    self?.autoReplyModels = models
                    updateModelMenu()
                    statusLabel?.text = models.isEmpty ? "状态：未获取到模型，请检查访问地址和 API Key。" : "状态：已获取 \(models.count) 个模型。"
                }
            }
        }, for: .touchUpInside)

        saveButton.addAction(UIAction { [weak controller] _ in
            saveCurrentForm(true)
            controller?.navigationController?.popViewController(animated: true)
        }, for: .touchUpInside)

        testButton.addAction(UIAction { [weak self] _ in
            saveCurrentForm(false)
            self?.presentAutoReplyTestPicker()
        }, for: .touchUpInside)

        updateModelMenu()

        [
            titleLabel, descriptionLabel, enableLabel, enableSwitch, robotLabel,
            baseURLField, apiKeyField, modelField, modelButton,
            fetchButton, saveButton, testButton, statusLabel
        ].forEach(contentView.addSubview)
        controller.view.addSubview(scrollView)

        NSLayoutConstraint.activate([
            scrollView.topAnchor.constraint(equalTo: controller.view.safeAreaLayoutGuide.topAnchor),
            scrollView.leadingAnchor.constraint(equalTo: controller.view.leadingAnchor),
            scrollView.trailingAnchor.constraint(equalTo: controller.view.trailingAnchor),
            scrollView.bottomAnchor.constraint(equalTo: controller.view.bottomAnchor),

            contentView.topAnchor.constraint(equalTo: scrollView.contentLayoutGuide.topAnchor),
            contentView.leadingAnchor.constraint(equalTo: scrollView.contentLayoutGuide.leadingAnchor),
            contentView.trailingAnchor.constraint(equalTo: scrollView.contentLayoutGuide.trailingAnchor),
            contentView.bottomAnchor.constraint(equalTo: scrollView.contentLayoutGuide.bottomAnchor),
            contentView.widthAnchor.constraint(equalTo: scrollView.frameLayoutGuide.widthAnchor),

            titleLabel.topAnchor.constraint(equalTo: contentView.topAnchor, constant: 22),
            titleLabel.leadingAnchor.constraint(equalTo: contentView.leadingAnchor, constant: 20),
            titleLabel.trailingAnchor.constraint(equalTo: contentView.trailingAnchor, constant: -20),

            descriptionLabel.topAnchor.constraint(equalTo: titleLabel.bottomAnchor, constant: 10),
            descriptionLabel.leadingAnchor.constraint(equalTo: titleLabel.leadingAnchor),
            descriptionLabel.trailingAnchor.constraint(equalTo: titleLabel.trailingAnchor),

            enableLabel.topAnchor.constraint(equalTo: descriptionLabel.bottomAnchor, constant: 24),
            enableLabel.leadingAnchor.constraint(equalTo: titleLabel.leadingAnchor),
            enableSwitch.centerYAnchor.constraint(equalTo: enableLabel.centerYAnchor),
            enableSwitch.trailingAnchor.constraint(equalTo: titleLabel.trailingAnchor),

            robotLabel.topAnchor.constraint(equalTo: enableLabel.bottomAnchor, constant: 22),
            robotLabel.leadingAnchor.constraint(equalTo: titleLabel.leadingAnchor),
            robotLabel.trailingAnchor.constraint(equalTo: titleLabel.trailingAnchor),

            baseURLField.topAnchor.constraint(equalTo: robotLabel.bottomAnchor, constant: 14),
            baseURLField.leadingAnchor.constraint(equalTo: titleLabel.leadingAnchor),
            baseURLField.trailingAnchor.constraint(equalTo: titleLabel.trailingAnchor),
            baseURLField.heightAnchor.constraint(equalToConstant: 44),

            apiKeyField.topAnchor.constraint(equalTo: baseURLField.bottomAnchor, constant: 10),
            apiKeyField.leadingAnchor.constraint(equalTo: titleLabel.leadingAnchor),
            apiKeyField.trailingAnchor.constraint(equalTo: titleLabel.trailingAnchor),
            apiKeyField.heightAnchor.constraint(equalToConstant: 44),

            modelField.topAnchor.constraint(equalTo: apiKeyField.bottomAnchor, constant: 10),
            modelField.leadingAnchor.constraint(equalTo: titleLabel.leadingAnchor),
            modelField.trailingAnchor.constraint(equalTo: titleLabel.trailingAnchor),
            modelField.heightAnchor.constraint(equalToConstant: 44),

            modelButton.topAnchor.constraint(equalTo: modelField.bottomAnchor, constant: 10),
            modelButton.leadingAnchor.constraint(equalTo: titleLabel.leadingAnchor),
            modelButton.trailingAnchor.constraint(equalTo: titleLabel.trailingAnchor),
            modelButton.heightAnchor.constraint(equalToConstant: 44),

            fetchButton.topAnchor.constraint(equalTo: modelButton.bottomAnchor, constant: 18),
            fetchButton.leadingAnchor.constraint(equalTo: titleLabel.leadingAnchor),
            fetchButton.trailingAnchor.constraint(equalTo: titleLabel.trailingAnchor),
            fetchButton.heightAnchor.constraint(equalToConstant: 44),

            saveButton.topAnchor.constraint(equalTo: fetchButton.bottomAnchor, constant: 10),
            saveButton.leadingAnchor.constraint(equalTo: titleLabel.leadingAnchor),
            saveButton.trailingAnchor.constraint(equalTo: titleLabel.trailingAnchor),
            saveButton.heightAnchor.constraint(equalToConstant: 44),

            testButton.topAnchor.constraint(equalTo: saveButton.bottomAnchor, constant: 10),
            testButton.leadingAnchor.constraint(equalTo: titleLabel.leadingAnchor),
            testButton.trailingAnchor.constraint(equalTo: titleLabel.trailingAnchor),
            testButton.heightAnchor.constraint(equalToConstant: 44),

            statusLabel.topAnchor.constraint(equalTo: testButton.bottomAnchor, constant: 14),
            statusLabel.leadingAnchor.constraint(equalTo: titleLabel.leadingAnchor),
            statusLabel.trailingAnchor.constraint(equalTo: titleLabel.trailingAnchor),
            statusLabel.bottomAnchor.constraint(equalTo: contentView.bottomAnchor, constant: -28)
        ])

        controller.navigationItem.leftBarButtonItem = UIBarButtonItem(title: "返回", style: .plain, target: self, action: #selector(popTopController))
        navigationItem.backBarButtonItem = UIBarButtonItem(title: "返回", style: .plain, target: nil, action: nil)
        if let navigationController {
            navigationController.setNavigationBarHidden(false, animated: false)
            navigationController.pushViewController(controller, animated: true)
        } else {
            let navigationController = UINavigationController(rootViewController: controller)
            navigationController.modalPresentationStyle = .fullScreen
            controller.navigationItem.leftBarButtonItem = UIBarButtonItem(
                title: "返回",
                style: .plain,
                target: self,
                action: #selector(dismissPresentedController)
            )
            present(navigationController, animated: true)
        }
    }

    func configureAutoReplyTextField(_ textField: UITextField, placeholder: String) {
        textField.placeholder = placeholder
        textField.borderStyle = .roundedRect
        textField.clearButtonMode = .whileEditing
        textField.autocorrectionType = .no
        textField.translatesAutoresizingMaskIntoConstraints = false
    }

    func configureAutoReplyActionButton(_ button: UIButton, title: String, color: UIColor) {
        button.setTitle(title, for: .normal)
        button.titleLabel?.font = .systemFont(ofSize: 16, weight: .semibold)
        button.backgroundColor = color
        button.tintColor = .white
        button.layer.cornerRadius = 10
        button.translatesAutoresizingMaskIntoConstraints = false
    }

    func fetchCodexModels(completion: @escaping ([String]) -> Void) {
        fetchModels(baseURL: codexConfiguration.baseURL, apiKey: codexConfiguration.apiKey, completion: completion)
    }

    func fetchClaudeModels(completion: @escaping ([String]) -> Void) {
        if isAnthropicEndpoint(claudeConfiguration.baseURL) {
            fetchAnthropicModels(baseURL: claudeConfiguration.baseURL, apiKey: claudeConfiguration.apiKey, completion: completion)
        } else {
            fetchModels(baseURL: claudeConfiguration.baseURL, apiKey: claudeConfiguration.apiKey, completion: completion)
        }
    }

    func fetchAutoReplyModels(completion: @escaping ([String]) -> Void) {
        fetchModels(baseURL: autoReplyConfiguration.baseURL, apiKey: autoReplyConfiguration.apiKey, completion: completion)
    }

    func fetchModels(baseURL: URL?, apiKey: String, completion: @escaping ([String]) -> Void) {
        guard let url = endpoint(baseURL: baseURL, path: "models") else {
            completion([])
            return
        }
        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        request.setValue("Bearer \(apiKey)", forHTTPHeaderField: "Authorization")

        URLSession.shared.dataTask(with: request) { data, _, error in
            guard error == nil,
                  let data,
                  let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                  let items = json["data"] as? [[String: Any]]
            else {
                completion([])
                return
            }

            let models = items.compactMap { $0["id"] as? String }
                .filter { !$0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
                .sorted()
            completion(models)
        }.resume()
    }

    func fetchAnthropicModels(baseURL: URL?, apiKey: String, completion: @escaping ([String]) -> Void) {
        guard let url = endpoint(baseURL: baseURL, path: "models"),
              !apiKey.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        else {
            completion([])
            return
        }
        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        request.setValue(apiKey, forHTTPHeaderField: "x-api-key")
        request.setValue("2023-06-01", forHTTPHeaderField: "anthropic-version")

        URLSession.shared.dataTask(with: request) { data, _, error in
            guard error == nil,
                  let data,
                  let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                  let items = json["data"] as? [[String: Any]]
            else {
                completion([])
                return
            }

            let models = items.compactMap { $0["id"] as? String }
                .filter { !$0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
                .sorted()
            completion(models)
        }.resume()
    }

    func streamCodexMessage(
        prompt: String,
        onDelta: @escaping (String) -> Void,
        completion: @escaping (Result<Void, Error>) -> Void
    ) {
        streamChatCompletion(
            prompt: prompt,
            configuration: codexConfiguration,
            systemPrompt: "你是移动聊天应用里的 Codex AI。请直接给出可发送到聊天里的中文内容，简洁、明确。",
            onDelta: onDelta,
            completion: completion
        )
    }

    func streamClaudeMessage(
        prompt: String,
        onDelta: @escaping (String) -> Void,
        completion: @escaping (Result<Void, Error>) -> Void
    ) {
        let systemPrompt = "你是移动聊天应用里的 Claude AI。请直接给出可发送到聊天里的中文内容，自然、简洁、明确。"
        if isAnthropicEndpoint(claudeConfiguration.baseURL) {
            streamAnthropicMessage(
                prompt: prompt,
                configuration: claudeConfiguration,
                systemPrompt: systemPrompt,
                onDelta: onDelta,
                completion: completion
            )
        } else {
            streamChatCompletion(
                prompt: prompt,
                configuration: claudeConfiguration,
                systemPrompt: systemPrompt,
                defaultModel: Self.defaultClaudeModel,
                onDelta: onDelta,
                completion: completion
            )
        }
    }

    func streamAutoReplyMessage(
        prompt: String,
        onDelta: @escaping (String) -> Void,
        completion: @escaping (Result<Void, Error>) -> Void
    ) {
        let configuration = CodexConfiguration(
            baseURLText: autoReplyConfiguration.baseURLText,
            apiKey: autoReplyConfiguration.apiKey,
            model: autoReplyConfiguration.model
        )
        streamChatCompletion(
            prompt: prompt,
            configuration: configuration,
            systemPrompt: "你是聊天应用里的自动回复机器人。请以当前帐号身份回复好友消息，中文、自然、简短，不要解释你是 AI。",
            onDelta: onDelta,
            completion: completion
        )
    }

    func streamChatCompletion(
        prompt: String,
        configuration: CodexConfiguration,
        systemPrompt: String,
        defaultModel: String? = nil,
        onDelta: @escaping (String) -> Void,
        completion: @escaping (Result<Void, Error>) -> Void
    ) {
        let trimmedPrompt = prompt.trimmingCharacters(in: .whitespacesAndNewlines)
        let userPrompt = trimmedPrompt.isEmpty ? "生成一条适合聊天发送的简短回复。" : trimmedPrompt
        guard let url = endpoint(baseURL: configuration.baseURL, path: "chat/completions") else {
            completion(.failure(AIStreamingError.invalidEndpoint))
            return
        }
        guard !configuration.apiKey.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            completion(.failure(AIStreamingError.missingAPIKey))
            return
        }
        let fallbackModel = defaultModel ?? Self.defaultCodexModel
        let model = configuration.model.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            ? fallbackModel
            : configuration.model.trimmingCharacters(in: .whitespacesAndNewlines)
        let payload: [String: Any] = [
            "model": model,
            "messages": [
                [
                    "role": "system",
                    "content": systemPrompt
                ],
                [
                    "role": "user",
                    "content": userPrompt
                ]
            ],
            "temperature": 0.7,
            "stream": true
        ]

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("Bearer \(configuration.apiKey)", forHTTPHeaderField: "Authorization")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try? JSONSerialization.data(withJSONObject: payload)

        Task {
            do {
                let (bytes, response) = try await URLSession.shared.bytes(for: request)
                if let httpResponse = response as? HTTPURLResponse,
                   !(200..<300).contains(httpResponse.statusCode) {
                    var errorBody = ""
                    for try await line in bytes.lines {
                        errorBody += line
                        if errorBody.count >= 1_200 { break }
                    }
                    throw AIStreamingError.httpStatus(httpResponse.statusCode, errorBody)
                }
                var receivedContent = false
                for try await line in bytes.lines {
                    let trimmedLine = line.trimmingCharacters(in: .whitespacesAndNewlines)
                    let payload = trimmedLine.hasPrefix("data:")
                        ? String(trimmedLine.dropFirst(5)).trimmingCharacters(in: .whitespacesAndNewlines)
                        : trimmedLine
                    guard !payload.isEmpty else { continue }
                    if payload == "[DONE]" {
                        completion(receivedContent ? .success(()) : .failure(AIStreamingError.emptyResponse))
                        return
                    }
                    guard let data = payload.data(using: .utf8),
                          let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                          let choices = json["choices"] as? [[String: Any]], let firstChoice = choices.first
                    else {
                        if let data = payload.data(using: .utf8),
                           let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                           let error = json["error"] as? [String: Any] {
                            throw AIStreamingError.serviceError(error["message"] as? String ?? "")
                        }
                        continue
                    }

                    if let delta = firstChoice["delta"] as? [String: Any],
                       let content = delta["content"] as? String, !content.isEmpty {
                        receivedContent = true
                        onDelta(content)
                    } else if let message = firstChoice["message"] as? [String: Any],
                              let content = message["content"] as? String, !content.isEmpty {
                        receivedContent = true
                        onDelta(content)
                    }
                }
                completion(receivedContent ? .success(()) : .failure(AIStreamingError.emptyResponse))
            } catch {
                completion(.failure(error))
            }
        }
    }

    func streamAnthropicMessage(
        prompt: String,
        configuration: CodexConfiguration,
        systemPrompt: String,
        onDelta: @escaping (String) -> Void,
        completion: @escaping (Result<Void, Error>) -> Void
    ) {
        let trimmedPrompt = prompt.trimmingCharacters(in: .whitespacesAndNewlines)
        let userPrompt = trimmedPrompt.isEmpty ? "生成一条适合聊天发送的简短回复。" : trimmedPrompt
        guard let url = endpoint(baseURL: configuration.baseURL, path: "messages"),
              !configuration.apiKey.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        else {
            completion(.failure(configuration.apiKey.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                ? AIStreamingError.missingAPIKey
                : AIStreamingError.invalidEndpoint))
            return
        }
        let model = configuration.model.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            ? Self.defaultClaudeModel
            : configuration.model.trimmingCharacters(in: .whitespacesAndNewlines)
        let payload: [String: Any] = [
            "model": model,
            "max_tokens": 1024,
            "system": systemPrompt,
            "messages": [
                [
                    "role": "user",
                    "content": userPrompt
                ]
            ],
            "stream": true
        ]

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue(configuration.apiKey, forHTTPHeaderField: "x-api-key")
        request.setValue("2023-06-01", forHTTPHeaderField: "anthropic-version")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try? JSONSerialization.data(withJSONObject: payload)

        Task {
            do {
                let (bytes, response) = try await URLSession.shared.bytes(for: request)
                if let httpResponse = response as? HTTPURLResponse,
                   !(200..<300).contains(httpResponse.statusCode) {
                    var errorBody = ""
                    for try await line in bytes.lines {
                        errorBody += line
                        if errorBody.count >= 1_200 { break }
                    }
                    throw AIStreamingError.httpStatus(httpResponse.statusCode, errorBody)
                }
                var receivedContent = false
                for try await line in bytes.lines {
                    guard line.hasPrefix("data:") else { continue }
                    let payload = line.dropFirst(5).trimmingCharacters(in: .whitespacesAndNewlines)
                    guard let data = payload.data(using: .utf8),
                          let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any]
                    else { continue }
                    if let type = json["type"] as? String, type == "message_stop" {
                        completion(receivedContent ? .success(()) : .failure(AIStreamingError.emptyResponse))
                        return
                    }
                    if let type = json["type"] as? String, type == "error" {
                        let error = json["error"] as? [String: Any]
                        throw AIStreamingError.serviceError(error?["message"] as? String ?? "")
                    }
                    if let delta = json["delta"] as? [String: Any],
                       let text = delta["text"] as? String, !text.isEmpty {
                        receivedContent = true
                        onDelta(text)
                    } else if let contentBlock = json["content_block"] as? [String: Any],
                              let text = contentBlock["text"] as? String, !text.isEmpty {
                        receivedContent = true
                        onDelta(text)
                    }
                }
                completion(receivedContent ? .success(()) : .failure(AIStreamingError.emptyResponse))
            } catch {
                completion(.failure(error))
            }
        }
    }

    func codexEndpoint(path: String) -> URL? {
        endpoint(baseURL: codexConfiguration.baseURL, path: path)
    }

    func isAnthropicEndpoint(_ baseURL: URL?) -> Bool {
        baseURL?.host?.lowercased().contains("anthropic") == true
    }

    func endpoint(baseURL: URL?, path: String) -> URL? {
        guard let baseURL else { return nil }
        return path.split(separator: "/").reduce(baseURL) { url, component in
            url.appendingPathComponent(String(component))
        }
    }

    func configureAutoReplyTimer() {
        autoReplyTimer?.invalidate()
        guard autoReplyConfiguration.isEnabled else { return }
        autoReplyTimer = Timer.scheduledTimer(withTimeInterval: 2.0, repeats: true) { [weak self] _ in
            self?.processPendingAutoReplies()
        }
        RunLoop.main.add(autoReplyTimer!, forMode: .common)
    }

    func markExistingIncomingMessagesAsHandledForAutoReply() {
        autoReplyProcessedMessageIDs.formUnion(
            state.messages
                .filter(isEligibleIncomingMessageForAutoReply)
                .map(\.id)
        )
    }

    func isEligibleIncomingMessageForAutoReply(_ message: ChatMessage) -> Bool {
        !message.isOutgoing
            && !message.sender.isCurrentUser
            && !message.sender.isAIAccount
            && !message.isAI
            && message.type != .system
    }

    func processPendingAutoReplies() {
        guard autoReplyConfiguration.isEnabled else { return }
        let candidates = state.messages
            .filter(isEligibleIncomingMessageForAutoReply)
            .filter { !autoReplyProcessedMessageIDs.contains($0.id) }
            .sorted { $0.sentAt < $1.sentAt }

        for message in candidates {
            autoReplyProcessedMessageIDs.insert(message.id)
            guard !autoReplyActiveConversationIDs.contains(message.conversationID) else { continue }
            startAutoReply(for: message)
        }
    }

    func triggerAutoReplyForLatestCurrentMessage() {
        guard let incomingMessage = autoReplySourceForCurrentSelection() else {
            showNotice(
                isHomeTimeline
                    ? "请先选择一条包含对方消息的未回事项"
                    : "当前聊天没有可回复的对方消息"
            )
            return
        }
        guard !autoReplyActiveConversationIDs.contains(incomingMessage.conversationID) else {
            showNotice("AI 正在生成草稿")
            return
        }
        if let existingDraft = state.messages
            .filter({ message in
                isAutoReplyPrediction(message)
                    && autoReplySourceMessageID(from: message) == incomingMessage.id
            })
            .max(by: { $0.sentAt < $1.sentAt }) {
            revealAutoReplyPredictionForCurrentContext(existingDraft)
            showNotice("已定位到这条消息的 AI 草稿")
            return
        }
        autoReplyProcessedMessageIDs.insert(incomingMessage.id)
        startAutoReply(for: incomingMessage)
        showNotice("已触发 AI 回复草稿")
    }

    func autoReplySourceForCurrentSelection() -> ChatMessage? {
        if isHomeTimeline {
            guard let target = selectedUnansweredSendTarget() else { return nil }
            state.selectedAccountID = target.accountID
            let eligibleMessageIDs = Set(target.item.messageIDs)
            if let focusedMessageID = chatWindowRoute.unansweredFocus?.messageID,
               eligibleMessageIDs.contains(focusedMessageID),
               let focusedMessage = state.messages.first(where: { $0.id == focusedMessageID }),
               isEligibleIncomingMessageForAutoReply(focusedMessage) {
                return focusedMessage
            }
            return state.messages
                .filter { eligibleMessageIDs.contains($0.id) }
                .filter(isEligibleIncomingMessageForAutoReply)
                .max(by: { $0.sentAt < $1.sentAt })
        }

        guard let conversationID = state.selectedFriendID else { return nil }
        return state.messages
            .filter { $0.conversationID == conversationID }
            .filter(isEligibleIncomingMessageForAutoReply)
            .max(by: { $0.sentAt < $1.sentAt })
    }

    func revealAutoReplyPredictionForCurrentContext(_ message: ChatMessage) {
        guard isHomeTimeline else {
            revealAutoReplyPrediction(message)
            return
        }
        invalidateSelectionVisibleDataCaches(invalidatePendingPresentation: false)
        messageCollectionView.reloadData()
        DispatchQueue.main.async { [weak self] in
            guard let self,
                  let index = self.renderedIndex(of: message.id)
            else { return }
            let indexPath = IndexPath(item: index, section: 0)
            self.messageCollectionView.scrollToItem(
                at: indexPath,
                at: .centeredVertically,
                animated: true
            )
            self.pulseMessage(at: indexPath)
            self.scheduleConnectionUpdate()
        }
    }

    func startAutoReply(for incomingMessage: ChatMessage) {
        autoReplyActiveConversationIDs.insert(incomingMessage.conversationID)
        let receiverAccountID = autoReplyReceiverAccountID(for: incomingMessage)
        alignSelectedAccountForVisibleAutoReply(
            receiverAccountID,
            conversationID: incomingMessage.conversationID
        )
        let intentPlan = makeAutoReplyIntentPlan(for: incomingMessage)
        let initialExecutionStatus = autoReplyInitialExecutionStatus(for: intentPlan)
        let prompt = autoReplyPrompt(for: incomingMessage, receiverAccountID: receiverAccountID, intentPlan: intentPlan)
        let messageID = insertOutgoingAutoReplyStreamingMessage(
            body: autoReplyDisplayBody(reply: "AI自动回复生成中...", plan: intentPlan, calendarStatus: initialExecutionStatus),
            conversationID: incomingMessage.conversationID,
            sourceMessageID: incomingMessage.id,
            receiverAccountID: receiverAccountID,
            intentPlan: intentPlan,
            calendarStatus: initialExecutionStatus
        )
        var streamedText = ""

        streamAutoReplyMessage(prompt: prompt) { [weak self] delta in
            DispatchQueue.main.async {
                streamedText += delta
                self?.updateAutoReplyStreamingMessage(
                    id: messageID,
                    body: self?.autoReplyDisplayBody(reply: streamedText, plan: intentPlan, calendarStatus: self?.autoReplyCalendarStatus(fromMessageID: messageID) ?? "执行计划：待执行") ?? streamedText
                )
            }
        } completion: { [weak self] result in
            DispatchQueue.main.async {
                guard let self else { return }
                self.autoReplyActiveConversationIDs.remove(incomingMessage.conversationID)
                switch result {
                case .success:
                    self.updateAutoReplyStreamingMessage(
                        id: messageID,
                        body: self.autoReplyDisplayBody(
                            reply: streamedText,
                            plan: intentPlan,
                            calendarStatus: self.autoReplyCalendarStatus(fromMessageID: messageID)
                        )
                    )
                    self.finishAIStreamingMessage(id: messageID)
                    self.setAutoReplyDraftState(messageID: messageID, state: .ready)
                case .failure(let error):
                    let partial = streamedText.trimmingCharacters(in: .whitespacesAndNewlines)
                    self.updateAutoReplyStreamingMessage(
                        id: messageID,
                        body: partial.isEmpty ? "AI 草稿生成失败，请点击后重新生成。" : partial
                    )
                    self.finishAIStreamingMessage(id: messageID)
                    self.setAutoReplyDraftState(
                        messageID: messageID,
                        state: .generationFailed,
                        failureReason: error.localizedDescription
                    )
                    self.showNotice("AI 草稿生成失败：\(error.localizedDescription)")
                }
                self.reloadVisibleRightToolItems()
            }
        }
    }

    func autoReplyPrompt(
        for incomingMessage: ChatMessage,
        receiverAccountID: UUID? = nil,
        intentPlan: AutoReplyIntentPlan? = nil
    ) -> String {
        let conversationName = state.participants.first { $0.id == incomingMessage.conversationID }?.displayName ?? "当前聊天"
        let accountName = displayNameForAccount(id: receiverAccountID ?? state.selectedAccountID)
        let body = incomingMessage.body.isEmpty ? incomingMessage.type.title : incomingMessage.body
        let intentText = intentPlan.map {
            "识别意图：\($0.intent)\n推断目的：\($0.purpose)\n计划：\($0.steps.joined(separator: "；"))"
        } ?? ""
        return """
        当前发送帐号：\(accountName)
        会话：\(conversationName)
        发消息的人：\(incomingMessage.sender.displayName)
        对方消息：\(body)
        \(intentText)
        请只输出最终可发送给对方的一条回复正文，不要输出意图、计划或解释。
        """
    }

    @discardableResult
    func insertOutgoingAutoReplyStreamingMessage(
        body: String,
        conversationID: UUID,
        sourceMessageID: UUID,
        receiverAccountID: UUID,
        intentPlan: AutoReplyIntentPlan,
        calendarStatus: String
    ) -> UUID {
        let previousRenderedMessageIDs = renderedMessages.map(\.id)
        let sender = state.currentUsers.first { $0.id == receiverAccountID } ?? state.currentUser
        let message = ChatMessage(
            id: UUID(),
            conversationID: conversationID,
            type: .text,
            sender: sender,
            body: body,
            detail: autoReplyPredictionDetail(
                sourceMessageID: sourceMessageID,
                receiverAccountID: receiverAccountID,
                intentPlan: intentPlan,
                calendarStatus: calendarStatus,
                calendarEventID: nil,
                draftState: .generating
            ),
            isOutgoing: true,
            presentation: .avatarOnly,
            timestamp: currentTimestamp(),
            sentAt: Date(),
            isAI: true,
            isGroupConversation: state.participants.first(where: { $0.id == conversationID })
                .map(isGroupConversation) ?? false,
            richElements: richElements(from: body),
            recipientAccountID: receiverAccountID
        )
        state.messages.append(message)
        persistMessageMutations([.insert(message)])
        streamingAIMessageIDs.insert(message.id)
        reloadVisibleRightToolItems()
        if state.selectedFriendID == conversationID {
            insertRenderedMessageItemIfPossible(
                messageID: message.id,
                previousMessageIDs: previousRenderedMessageIDs,
                scrollToInsertedMessage: true
            )
        } else if state.selectedFriendID == nil {
            invalidateSelectionVisibleDataCaches(invalidatePendingPresentation: false)
            messageCollectionView.reloadData()
            refreshUnreadTimelineLayoutsAfterMessageHeightChange()
            if let visibleIndex = renderedIndex(of: message.id) {
                messageCollectionView.scrollToItem(at: IndexPath(item: visibleIndex, section: 0), at: .bottom, animated: true)
            }
            syncUnreadTimelineScroll(from: messageCollectionView)
            scheduleConnectionUpdate()
        }
        return message.id
    }

    func autoReplyPredictionDetail(
        sourceMessageID: UUID,
        receiverAccountID: UUID,
        intentPlan: AutoReplyIntentPlan,
        calendarStatus: String,
        calendarEventID: String?,
        draftState: AutoReplyDraftState = .ready,
        failureReason: String? = nil
    ) -> String {
        [
            "AI自动回复预测",
            "来源消息ID：\(sourceMessageID.uuidString)",
            "接收帐号ID：\(receiverAccountID.uuidString)",
            "意图：\(intentPlan.intent)",
            "推断目的：\(intentPlan.purpose)",
            "计划：\(intentPlan.steps.joined(separator: "｜"))",
            "日历标题：\(intentPlan.calendarTitle)",
            "日历时间：\(intentPlan.calendarDate.timeIntervalSince1970)",
            "日历状态：\(calendarStatus)",
            "草稿状态：\(draftState.rawValue)",
            failureReason.map {
                "草稿错误：\($0.replacingOccurrences(of: "\n", with: " "))"
            },
            calendarEventID.map { "日历事件ID：\($0)" }
        ]
            .compactMap { $0 }
            .joined(separator: "\n")
    }

    func isAutoReplyPrediction(_ message: ChatMessage) -> Bool {
        message.detail.hasPrefix("AI自动回复预测")
    }

    func autoReplySourceMessageID(from message: ChatMessage) -> UUID? {
        message.detail
            .components(separatedBy: .newlines)
            .first { $0.hasPrefix("来源消息ID：") }
            .map { String($0.dropFirst("来源消息ID：".count)).trimmingCharacters(in: .whitespacesAndNewlines) }
            .flatMap(UUID.init(uuidString:))
    }

    struct AutoReplyIntentPlan {
        let intent: String
        let purpose: String
        let steps: [String]
        let requiresCalendarEvent: Bool
        let calendarTitle: String
        let calendarNotes: String
        let calendarDate: Date
    }

    enum AutoReplyDraftState: String {
        case generating
        case ready
        case generationFailed
        case sending
        case sendUnconfirmed
        case sendFailed
    }

    func autoReplyDraftState(from message: ChatMessage) -> AutoReplyDraftState {
        message.detail
            .components(separatedBy: .newlines)
            .first { $0.hasPrefix("草稿状态：") }
            .map { String($0.dropFirst("草稿状态：".count)).trimmingCharacters(in: .whitespacesAndNewlines) }
            .flatMap(AutoReplyDraftState.init(rawValue:))
            ?? .ready
    }

    func canSendAutoReplyDraft(_ message: ChatMessage) -> Bool {
        switch autoReplyDraftState(from: message) {
        case .ready, .sendFailed:
            return true
        case .generating, .generationFailed, .sending, .sendUnconfirmed:
            return false
        }
    }

    func setAutoReplyDraftState(
        messageID: UUID,
        state draftState: AutoReplyDraftState,
        failureReason: String? = nil
    ) {
        flushPendingStreamingMessageUpdates(forcePersistence: true)
        guard let index = state.messages.firstIndex(where: { $0.id == messageID }) else { return }
        let current = state.messages[index]
        var lines = current.detail.components(separatedBy: .newlines)
            .filter { !$0.hasPrefix("草稿状态：") && !$0.hasPrefix("草稿错误：") }
        lines.append("草稿状态：\(draftState.rawValue)")
        if let failureReason {
            let cleaned = failureReason.replacingOccurrences(of: "\n", with: " ")
                .trimmingCharacters(in: .whitespacesAndNewlines)
            if !cleaned.isEmpty {
                lines.append("草稿错误：\(cleaned)")
            }
        }
        state.messages[index] = replacingMessage(current, detail: lines.joined(separator: "\n"))
        persistMessageMutations([.update(state.messages[index])])
        if let visibleIndex = renderedIndex(of: messageID),
           visibleIndex < messageCollectionView.numberOfItems(inSection: 0) {
            messageCollectionView.reloadItems(at: [IndexPath(item: visibleIndex, section: 0)])
        }
    }

    func autoReplyReceiverAccountID(for incomingMessage: ChatMessage) -> UUID {
        if let recipientAccountID = incomingMessage.recipientAccountID,
           state.currentUsers.contains(where: { $0.id == recipientAccountID }) {
            return recipientAccountID
        }
        if let conversation = state.participants.first(where: { $0.id == incomingMessage.conversationID }) {
            if isGroupConversation(conversation) {
                let groupAccountIDs = currentAccountIDsInGroup(conversation)
                if groupAccountIDs.contains(state.selectedAccountID) {
                    return state.selectedAccountID
                }
                if let first = state.currentUsers.first(where: { groupAccountIDs.contains($0.id) }) {
                    return first.id
                }
            } else if let accountID = accountIDForDirectConversation(
                conversation,
                preferredAccountID: state.selectedAccountID
            ) {
                return accountID
            }
        }
        return state.selectedAccountID
    }

    func alignSelectedAccountForVisibleAutoReply(_ accountID: UUID, conversationID: UUID) {
        guard state.selectedFriendID == conversationID,
              state.selectedAccountID != accountID
        else { return }
        state.selectedAccountID = accountID
        invalidateVisibleDataCaches()
        updateHeaderForSelection()
        rightAccountCollectionView.reloadData()
        messageCollectionView.reloadData()
        if let accountIndex = visibleRightAccountItems().firstIndex(where: { $0.participantID == accountID }) {
            rightAccountCollectionView.scrollToItem(
                at: IndexPath(item: accountIndex, section: 0),
                at: .centeredVertically,
                animated: true
            )
        }
    }

    func revealAutoReplyPrediction(_ message: ChatMessage) {
        if let receiverAccountID = autoReplyReceiverAccountID(from: message) {
            state.selectedAccountID = receiverAccountID
        }
        selectFriend(id: message.conversationID, focusMessageID: message.id)
    }

    func makeAutoReplyIntentPlan(for message: ChatMessage) -> AutoReplyIntentPlan {
        let conversationName = state.participants.first { $0.id == message.conversationID }?.displayName ?? "当前聊天"
        let body = (message.body.isEmpty ? message.type.title : message.body).lowercased()
        let hasScheduleIntent = body.contains("明天")
            || body.contains("今天")
            || body.contains("下午")
            || body.contains("上午")
            || body.contains("晚上")
            || body.contains("预约")
            || body.contains("时间")
            || body.contains("日程")
            || body.contains("开会")
            || body.contains("会议")
            || body.contains("跟进")
            || body.contains("提醒")
        let hasPaymentIntent = body.contains("转账")
            || body.contains("收款")
            || body.contains("红包")
            || body.contains("付款")
        let intent: String
        let purpose: String
        let steps: [String]
        if hasScheduleIntent {
            intent = "时间安排/跟进"
            purpose = "对方希望确认时间、预约事项或后续跟进。"
            steps = [
                "提取对方提到的时间与事项",
                "生成可直接发送的确认回复",
                "写入 iOS 日历提醒，避免遗漏后续跟进"
            ]
        } else if hasPaymentIntent {
            intent = "资金处理"
            purpose = "对方可能希望确认付款、转账或收款状态。"
            steps = [
                "确认资金动作和对象",
                "避免直接承诺付款结果",
                "生成简短确认回复"
            ]
        } else {
            intent = "普通沟通"
            purpose = "对方发来普通消息，需要自然回复并保持沟通。"
            steps = [
                "理解对方消息重点",
                "生成自然、简短的回复",
                "等待对方继续确认"
            ]
        }
        let startDate = inferredCalendarDate(from: body)
        return AutoReplyIntentPlan(
            intent: intent,
            purpose: purpose,
            steps: steps,
            requiresCalendarEvent: hasScheduleIntent,
            calendarTitle: "跟进：\(conversationName)",
            calendarNotes: "AI 根据聊天消息自动建立的跟进计划。\n原消息：\(message.body.isEmpty ? message.type.title : message.body)",
            calendarDate: startDate
        )
    }

    func inferredCalendarDate(from lowercasedText: String) -> Date {
        let calendar = Calendar.current
        let now = Date()
        var date = lowercasedText.contains("明天")
            ? (calendar.date(byAdding: .day, value: 1, to: now) ?? now)
            : now
        let hour: Int
        if lowercasedText.contains("上午") {
            hour = 10
        } else if lowercasedText.contains("晚上") {
            hour = 20
        } else if lowercasedText.contains("下午") {
            hour = 15
        } else {
            hour = calendar.component(.hour, from: now) < 18 ? calendar.component(.hour, from: now) + 1 : 10
            if hour == 10 && !lowercasedText.contains("今天") {
                date = calendar.date(byAdding: .day, value: 1, to: date) ?? date
            }
        }
        return calendar.date(
            bySettingHour: min(hour, 23),
            minute: 0,
            second: 0,
            of: date
        ) ?? now.addingTimeInterval(3600)
    }

    func autoReplyInitialExecutionStatus(for plan: AutoReplyIntentPlan) -> String {
        plan.requiresCalendarEvent
            ? "执行计划：待确认写入 iOS 日历"
            : "执行计划：无需写入日历"
    }

    func autoReplyDisplayBody(reply: String, plan: AutoReplyIntentPlan, calendarStatus: String) -> String {
        let cleanedReply = reply.trimmingCharacters(in: .whitespacesAndNewlines)
        return cleanedReply.isEmpty ? "AI自动回复生成中..." : cleanedReply
    }

    func suggestedReplyText(fromAutoReplyBody body: String) -> String {
        guard let range = body.range(of: "建议回复：") else {
            return body.trimmingCharacters(in: .whitespacesAndNewlines)
        }
        return String(body[range.upperBound...]).trimmingCharacters(in: .whitespacesAndNewlines)
    }

    func autoReplyCalendarStatus(fromMessageID messageID: UUID) -> String {
        guard let detail = state.messages.first(where: { $0.id == messageID })?.detail else {
            return "执行计划：待执行"
        }
        return detail
            .components(separatedBy: .newlines)
            .first { $0.hasPrefix("日历状态：") }
            .map { String($0.dropFirst("日历状态：".count)).trimmingCharacters(in: .whitespacesAndNewlines) }
            ?? "执行计划：待执行"
    }

    func executeAutoReplyPlanIfNeeded(messageID: UUID, plan: AutoReplyIntentPlan) {
        guard plan.requiresCalendarEvent else {
            updateAutoReplyCalendarStatus(messageID: messageID, status: "执行计划：无需写入日历", eventID: nil, plan: plan)
            return
        }
        requestCalendarAccess { [weak self] granted in
            guard let self else { return }
            guard granted else {
                self.updateAutoReplyCalendarStatus(messageID: messageID, status: "执行计划：日历权限未开启", eventID: nil, plan: plan)
                return
            }
            do {
                let event = EKEvent(eventStore: self.calendarEventStore)
                event.calendar = self.calendarEventStore.defaultCalendarForNewEvents
                event.title = plan.calendarTitle
                event.notes = plan.calendarNotes
                event.startDate = plan.calendarDate
                event.endDate = plan.calendarDate.addingTimeInterval(30 * 60)
                event.addAlarm(EKAlarm(relativeOffset: -10 * 60))
                try self.calendarEventStore.save(event, span: .thisEvent)
                self.updateAutoReplyCalendarStatus(
                    messageID: messageID,
                    status: "执行计划：已写入 iOS 日历",
                    eventID: event.eventIdentifier,
                    plan: plan
                )
            } catch {
                self.updateAutoReplyCalendarStatus(messageID: messageID, status: "执行计划：日历写入失败", eventID: nil, plan: plan)
            }
        }
    }

    func requestCalendarAccess(_ completion: @escaping (Bool) -> Void) {
        let status = EKEventStore.authorizationStatus(for: .event)
        if status == .authorized {
            completion(true)
            return
        }
        if #available(iOS 17.0, *) {
            if status == .fullAccess {
                completion(true)
                return
            }
            calendarEventStore.requestFullAccessToEvents { granted, _ in
                DispatchQueue.main.async { completion(granted) }
            }
        } else {
            calendarEventStore.requestAccess(to: .event) { granted, _ in
                DispatchQueue.main.async { completion(granted) }
            }
        }
    }

    func executeAutoReplyPlan(for message: ChatMessage) {
        guard let plan = autoReplyIntentPlan(from: message) else {
            showNotice("找不到计划内容")
            return
        }
        guard plan.requiresCalendarEvent else {
            updateAutoReplyCalendarStatus(messageID: message.id, status: "执行计划：无需写入日历", eventID: nil, plan: plan)
            showNotice("该计划无需写入日历")
            return
        }
        updateAutoReplyCalendarStatus(messageID: message.id, status: "执行计划：正在写入 iOS 日历...", eventID: autoReplyCalendarEventID(from: message), plan: plan)
        executeAutoReplyPlanIfNeeded(messageID: message.id, plan: plan)
    }

    func retryAutoReplyPlan(for message: ChatMessage) {
        executeAutoReplyPlan(for: message)
    }

    func revokeAutoReplyCalendarEvent(for message: ChatMessage) {
        guard let eventID = autoReplyCalendarEventID(from: message) else {
            showNotice("没有可撤销的日历事件")
            return
        }
        guard let plan = autoReplyIntentPlan(from: message) else {
            showNotice("找不到计划内容")
            return
        }
        requestCalendarAccess { [weak self] granted in
            guard let self else { return }
            guard granted else {
                self.updateAutoReplyCalendarStatus(messageID: message.id, status: "执行计划：日历权限未开启，无法撤销", eventID: eventID, plan: plan)
                return
            }
            if let event = self.calendarEventStore.event(withIdentifier: eventID) {
                do {
                    try self.calendarEventStore.remove(event, span: .thisEvent)
                    self.updateAutoReplyCalendarStatus(messageID: message.id, status: "执行计划：已撤销日历事件", eventID: nil, plan: plan)
                    self.showNotice("已撤销日历事件")
                } catch {
                    self.updateAutoReplyCalendarStatus(messageID: message.id, status: "执行计划：撤销失败，可重试", eventID: eventID, plan: plan)
                }
            } else {
                self.updateAutoReplyCalendarStatus(messageID: message.id, status: "执行计划：日历事件不存在或已被删除", eventID: nil, plan: plan)
            }
        }
    }

    func updateAutoReplyCalendarStatus(
        messageID: UUID,
        status: String,
        eventID: String?,
        plan: AutoReplyIntentPlan
    ) {
        guard let index = state.messages.firstIndex(where: { $0.id == messageID }) else { return }
        let current = state.messages[index]
        guard let sourceID = autoReplySourceMessageID(from: current),
              let receiverID = autoReplyReceiverAccountID(from: current)
        else { return }
        let detail = autoReplyPredictionDetail(
            sourceMessageID: sourceID,
            receiverAccountID: receiverID,
            intentPlan: plan,
            calendarStatus: status,
            calendarEventID: eventID,
            draftState: autoReplyDraftState(from: current),
            failureReason: current.detail.components(separatedBy: .newlines)
                .first { $0.hasPrefix("草稿错误：") }
                .map { String($0.dropFirst("草稿错误：".count)) }
        )
        let displayBody = autoReplyDisplayBody(
            reply: suggestedReplyText(fromAutoReplyBody: current.body),
            plan: plan,
            calendarStatus: status
        )
        state.messages[index] = ChatMessage(
            id: current.id,
            conversationID: current.conversationID,
            type: current.type,
            sender: current.sender,
            body: displayBody,
            detail: detail,
            isOutgoing: current.isOutgoing,
            presentation: current.presentation,
            timestamp: current.timestamp,
            sentAt: current.sentAt,
            attachmentURL: current.attachmentURL,
            isAI: current.isAI,
            isGroupConversation: current.isGroupConversation,
            richElements: richElements(from: displayBody),
            contactCard: current.contactCard,
            contactCardAccountID: current.contactCardAccountID,
            quotedMessageID: current.quotedMessageID,
            mergedForwardMessages: current.mergedForwardMessages,
            recipientAccountID: current.recipientAccountID ?? receiverID,
            unansweredMetadata: current.unansweredMetadata
        )
        persistMessageMutations([.update(state.messages[index])])
        reloadRenderedMessageItem(messageID: messageID)
    }

    func autoReplyReceiverAccountID(from message: ChatMessage) -> UUID? {
        message.recipientAccountID ?? message.detail
            .components(separatedBy: .newlines)
            .first { $0.hasPrefix("接收帐号ID：") }
            .map { String($0.dropFirst("接收帐号ID：".count)).trimmingCharacters(in: .whitespacesAndNewlines) }
            .flatMap(UUID.init(uuidString:))
    }

    func autoReplyIntentPlan(from message: ChatMessage) -> AutoReplyIntentPlan? {
        let lines = message.detail.components(separatedBy: .newlines)
        func value(prefix: String) -> String? {
            lines.first { $0.hasPrefix(prefix) }
                .map { String($0.dropFirst(prefix.count)).trimmingCharacters(in: .whitespacesAndNewlines) }
        }
        guard let intent = value(prefix: "意图："),
              let purpose = value(prefix: "推断目的：")
        else { return nil }
        let steps = value(prefix: "计划：")?
            .components(separatedBy: "｜")
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
            ?? ["生成可发送回复"]
        let calendarDate = value(prefix: "日历时间：")
            .flatMap(TimeInterval.init)
            .map(Date.init(timeIntervalSince1970:))
            ?? Date().addingTimeInterval(3600)
        return AutoReplyIntentPlan(
            intent: intent,
            purpose: purpose,
            steps: steps,
            requiresCalendarEvent: value(prefix: "日历标题：") != nil && !intent.contains("普通沟通"),
            calendarTitle: value(prefix: "日历标题：") ?? "聊天跟进",
            calendarNotes: "AI 根据聊天消息自动建立的跟进计划。",
            calendarDate: calendarDate
        )
    }

    func autoReplyCalendarEventID(from message: ChatMessage) -> String? {
        message.detail
            .components(separatedBy: .newlines)
            .first { $0.hasPrefix("日历事件ID：") }
            .map { String($0.dropFirst("日历事件ID：".count)).trimmingCharacters(in: .whitespacesAndNewlines) }
            .flatMap { $0.isEmpty ? nil : $0 }
    }

    func updateAutoReplyStreamingMessage(id: UUID, body: String) {
        enqueueStreamingMessageUpdate(
            id: id,
            body: body,
            rebuildsRichElements: true
        )
    }

    func presentAutoReplyTestPicker() {
        let testCases = autoReplyTestCases()
        let controller = AutoReplyTestPickerViewController(testCases: testCases)
        controller.onSend = { [weak self] selectedCases in
            guard let self else { return }
            self.navigationController?.popToViewController(self, animated: true)
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.24) {
                self.simulateIncomingMessagesForAutoReply(selectedCases)
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
            controller.onSend = { [weak self, weak navigationController] selectedCases in
                navigationController?.dismiss(animated: true)
                self?.simulateIncomingMessagesForAutoReply(selectedCases)
            }
            controller.onClose = { [weak navigationController] in
                navigationController?.dismiss(animated: true)
            }
            present(navigationController, animated: true)
        }
    }

    func pushOpenApiDemoPage(_ controller: UIViewController) {
        navigationItem.backBarButtonItem = UIBarButtonItem(title: "返回", style: .plain, target: nil, action: nil)
        if let navigationController {
            navigationController.setNavigationBarHidden(false, animated: false)
            navigationController.pushViewController(controller, animated: true)
        } else {
            let navigationController = UINavigationController(rootViewController: controller)
            navigationController.modalPresentationStyle = .fullScreen
            present(navigationController, animated: true)
        }
    }

    func presentPaymentStatusPage() {
        pushOpenApiDemoPage(
            PaymentKit.paymentStatusController(
                accountName: displayNameForAccount(id: state.selectedAccountID),
                weChatId: contactCardProfile(for: state.currentUsers.first { $0.id == state.selectedAccountID } ?? state.currentUser).wechatID
            )
        )
    }

    func presentFinderPublishPage() {
        pushOpenApiDemoPage(
            OpenApiFinderPublishViewController(
                accountName: displayNameForAccount(id: state.selectedAccountID),
                weChatId: contactCardProfile(for: state.currentUsers.first { $0.id == state.selectedAccountID } ?? state.currentUser).wechatID,
                deviceUuid: openApiAccountContextsByID[state.selectedAccountID]?.clientUuid ?? ""
            )
        )
    }

    func presentCustomerProfilePage() {
        let contact = state.activeFriend.flatMap { $0.isAIAccount || isGroupConversation($0) ? nil : $0 }
            ?? state.friends.first { !$0.isAIAccount && !isGroupConversation($0) && !isGroupParticipant($0) }
        let context = contact.flatMap { openApiConversationContextsByID[$0.id] }
        pushOpenApiDemoPage(
            OpenApiCustomerProfileViewController(
                ownerWxid: contactCardProfile(for: state.currentUsers.first { $0.id == state.selectedAccountID } ?? state.currentUser).wechatID,
                contact: contact,
                context: context
            )
        )
    }

    func presentContactRelationsPage() {
        let selectedAccount = state.currentUsers.first { $0.id == state.selectedAccountID } ?? state.currentUser
        let isAllAccountsScope = state.selectedFriendID == nil
        let selectedOwnerWxid = openApiAccountContextsByID[selectedAccount.id]?.wxid
            .trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        let relatedConversationIDs = Set(
            openApiConversationContextsByID.values
                .filter { isAllAccountsScope || (!selectedOwnerWxid.isEmpty && $0.ownerWxid == selectedOwnerWxid) }
                .map(\.participantID)
        )
        let scopedParticipants: [ChatParticipant]
        if relatedConversationIDs.isEmpty {
            scopedParticipants = state.participants.filter { participant in
                if isAllAccountsScope {
                    return participant.isCurrentUser || (!participant.isAIAccount && !participant.isCurrentUser)
                }
                return participant.id == selectedAccount.id
                    || (!participant.isCurrentUser && isConversation(participant, relatedToAccountID: selectedAccount.id))
            }
        } else {
            scopedParticipants = state.participants.filter {
                $0.id == selectedAccount.id || relatedConversationIDs.contains($0.id)
            }
        }
        let scopedParticipantIDs = Set(scopedParticipants.map(\.id))
        let scopedMessages = state.messages.filter {
            scopedParticipantIDs.contains($0.conversationID) || scopedParticipantIDs.contains($0.sender.id)
        }
        pushOpenApiDemoPage(
            ContactRelationsViewController(
                participants: scopedParticipants,
                messages: scopedMessages,
                contactCards: state.contactCards,
                selectedAccount: selectedAccount,
                scopeName: isAllAccountsScope ? "全部帐号" : selectedAccount.displayName
            )
        )
    }

    func presentMomentMaterialsPage() {
        let account = state.currentUsers.first { $0.id == state.selectedAccountID } ?? state.currentUser
        pushOpenApiDemoPage(
            OpenApiMomentMaterialsViewController(
                tenantId: "",
                accountName: displayNameForAccount(id: state.selectedAccountID),
                accountContext: openApiAccountContextsByID[account.id]
            )
        )
    }

    func presentOpenApiBusinessPage() {
        let account = state.currentUsers.first { $0.id == state.selectedAccountID } ?? state.currentUser
        pushOpenApiDemoPage(
            OpenApiAccountDeviceViewController(
                accountName: displayNameForAccount(id: state.selectedAccountID),
                weChatId: contactCardProfile(for: account).wechatID,
                deviceUuid: openApiAccountContextsByID[account.id]?.clientUuid ?? ""
            )
        )
    }

    func presentOpenApiFriendManagementPage() {
        if openApiAccountContextsByID.isEmpty {
            if isOpenApiIMSyncing {
                showNotice("真实好友正在同步，请稍后")
                return
            }
            showNotice("正在同步真实好友数据")
            syncOpenApiIMData(force: true) { [weak self] success in
                guard success else { return }
                self?.presentOpenApiFriendManagementPage()
            }
            return
        }
        let accounts = openApiAccountContextsByID.values.sorted { lhs, rhs in
            if lhs.isOnline != rhs.isOnline { return lhs.isOnline && !rhs.isOnline }
            return lhs.nickname.localizedStandardCompare(rhs.nickname) == .orderedAscending
        }
        let contacts = openApiConversationContextsByID.values.sorted { lhs, rhs in
            if lhs.ownerWxid != rhs.ownerWxid { return lhs.ownerWxid < rhs.ownerWxid }
            if lhs.kind != rhs.kind { return lhs.kind == .contact }
            return lhs.displayName.localizedStandardCompare(rhs.displayName) == .orderedAscending
        }
        let controller = OpenApiFriendManagementViewController(
            accounts: accounts,
            conversations: contacts,
            friendRequests: openApiFriendRequestContexts,
            selectedAccountID: state.selectedAccountID
        )
        controller.onRefreshRequested = { [weak self] in
            self?.syncOpenApiIMData(force: true) { [weak self, weak controller] success in
                guard let self, success else { return }
                let accounts = self.openApiAccountContextsByID.values.sorted { lhs, rhs in
                    if lhs.isOnline != rhs.isOnline { return lhs.isOnline && !rhs.isOnline }
                    return lhs.nickname.localizedStandardCompare(rhs.nickname) == .orderedAscending
                }
                let conversations = self.openApiConversationContextsByID.values.sorted { lhs, rhs in
                    if lhs.ownerWxid != rhs.ownerWxid { return lhs.ownerWxid < rhs.ownerWxid }
                    if lhs.kind != rhs.kind { return lhs.kind == .contact }
                    return lhs.displayName.localizedStandardCompare(rhs.displayName) == .orderedAscending
                }
                controller?.updateData(
                    accounts: accounts,
                    conversations: conversations,
                    friendRequests: self.openApiFriendRequestContexts,
                    selectedAccountID: self.state.selectedAccountID
                )
            }
        }
        pushOpenApiDemoPage(controller)
    }

    @objc func presentGlobalSearchPage() {
        guard navigationController?.topViewController === self else { return }
        let snapshot = makeGlobalSearchIndexSnapshot()
        let controller = GlobalSearchViewController(results: [], isLoadingIndex: true)
        controller.onSelectResult = { [weak self, weak controller] result in
            guard let self else { return }
            controller?.navigationController?.popViewController(animated: true)
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.22) {
                self.openGlobalSearchResult(result)
            }
        }
        pushOpenApiDemoPage(controller)
        DispatchQueue.global(qos: .userInitiated).async { [weak controller] in
            let results = Self.buildGlobalSearchResults(from: snapshot)
            DispatchQueue.main.async {
                controller?.updateResults(results)
            }
        }
    }

    func makeGlobalSearchIndexSnapshot() -> GlobalSearchIndexSnapshot {
        GlobalSearchIndexSnapshot(
            friends: state.friends,
            participants: state.participants,
            messages: state.messages,
            contactCards: state.contactCards,
            participantRemarks: participantRemarksByID,
            selectedAccountID: state.selectedAccountID,
            accountName: displayNameForAccount(id: state.selectedAccountID)
        )
    }

    static func buildGlobalSearchResults(from snapshot: GlobalSearchIndexSnapshot) -> [GlobalSearchResult] {
        var results: [GlobalSearchResult] = []
        let now = Date()
        let groupIDs = Set(snapshot.messages.filter(\.isGroupConversation).map(\.conversationID))
        let groupSenderCounts = groupMemberCounts(from: snapshot.messages)
        let latestMessages = latestMessagesByConversation(from: snapshot.messages, selectedAccountID: snapshot.selectedAccountID)
        var participantByID: [UUID: ChatParticipant] = [:]
        for participant in snapshot.participants {
            participantByID[participant.id] = participant
        }

        for participant in snapshot.friends where !participant.isAIAccount {
            let displayName = displayName(for: participant, remarks: snapshot.participantRemarks)
            let isGroup = participant.kind == .group || groupIDs.contains(participant.id)
            let latest = latestMessages[participant.id]
            let card = snapshot.contactCards[participant.id]
            results.append(
                GlobalSearchResult(
                    id: "participant-\(participant.id.uuidString)",
                    category: isGroup ? .group : .contact,
                    title: displayName,
                    subtitle: isGroup ? "群聊 · \(max(groupSenderCounts[participant.id] ?? 0, isGroup ? 3 : 1)) 位成员" : (card?.summary ?? "联系人 · \(participant.initials)"),
                    snippet: latest.map(searchSnippet(for:)) ?? (isGroup ? "点击进入群聊" : "点击进入联系人聊天"),
                    targetID: participant.id,
                    conversationID: participant.id,
                    messageID: latest?.id,
                    messageType: latest?.type,
                    detail: [
                        card?.summary,
                        latest.map { "最近消息：\(searchSnippet(for: $0))" }
                    ].compactMap { $0 }.joined(separator: "\n"),
                    updatedAt: latest?.sentAt ?? now
                )
            )
        }

        for message in snapshot.messages {
            let text = searchableText(for: message)
            let conversationParticipant = participantByID[message.conversationID]
            let conversationName = conversationParticipant.map { displayName(for: $0, remarks: snapshot.participantRemarks) } ?? "聊天"
            let isFile = message.type == .file || message.attachmentURL != nil
            let category: GlobalSearchCategory
            if isFile {
                category = .file
            } else if message.type == .channelsVideo || message.type == .channelsLive {
                category = .finder
            } else {
                category = .message
            }
            results.append(
                GlobalSearchResult(
                    id: "message-\(message.id.uuidString)-\(category.rawValue)",
                    category: category,
                    title: searchTitle(for: message),
                    subtitle: "\(conversationName) · \(message.sender.displayName) · \(message.timestamp)",
                    snippet: text.isEmpty ? message.type.title : text,
                    targetID: message.conversationID,
                    conversationID: message.conversationID,
                    messageID: message.id,
                    messageType: message.type,
                    detail: searchDetail(for: message),
                    updatedAt: message.sentAt
                )
            )
        }

        results.append(contentsOf: businessSearchFixtures(now: now, accountName: snapshot.accountName))
        return results.sorted {
            if $0.updatedAt == $1.updatedAt { return $0.title < $1.title }
            return $0.updatedAt > $1.updatedAt
        }
    }

    static func displayName(for participant: ChatParticipant, remarks: [UUID: String]) -> String {
        let remark = remarks[participant.id]?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        return remark.isEmpty ? participant.displayName : remark
    }

    static func groupMemberCounts(from messages: [ChatMessage]) -> [UUID: Int] {
        var sendersByConversation: [UUID: Set<UUID>] = [:]
        for message in messages where message.isGroupConversation {
            sendersByConversation[message.conversationID, default: []].insert(message.sender.id)
        }
        return sendersByConversation.mapValues(\.count)
    }

    static func latestMessagesByConversation(from messages: [ChatMessage], selectedAccountID: UUID) -> [UUID: ChatMessage] {
        var latest: [UUID: ChatMessage] = [:]
        for message in messages where canShowInGlobalSearch(message, selectedAccountID: selectedAccountID) {
            let existing = latest[message.conversationID]
            if existing == nil || isNewerSearchMessage(message, than: existing!) {
                latest[message.conversationID] = message
            }
            if !message.isGroupConversation {
                let senderID = message.sender.id
                let senderExisting = latest[senderID]
                if senderExisting == nil || isNewerSearchMessage(message, than: senderExisting!) {
                    latest[senderID] = message
                }
            }
        }
        return latest
    }

    static func isNewerSearchMessage(_ lhs: ChatMessage, than rhs: ChatMessage) -> Bool {
        if lhs.sentAt == rhs.sentAt {
            return lhs.id.uuidString > rhs.id.uuidString
        }
        return lhs.sentAt > rhs.sentAt
    }

    static func canShowInGlobalSearch(_ message: ChatMessage, selectedAccountID: UUID) -> Bool {
        guard message.detail.hasPrefix("AI自动回复预测") else { return true }
        guard let receiverID = message.recipientAccountID ?? autoReplyReceiverAccountID(in: message.detail) else { return true }
        return receiverID == selectedAccountID
    }

    static func autoReplyReceiverAccountID(in detail: String) -> UUID? {
        guard detail.hasPrefix("AI自动回复预测") else { return nil }
        return detail
            .components(separatedBy: .newlines)
            .first { $0.hasPrefix("接收帐号ID：") }
            .map { String($0.dropFirst("接收帐号ID：".count)).trimmingCharacters(in: .whitespacesAndNewlines) }
            .flatMap(UUID.init(uuidString:))
    }

    static func searchableText(for message: ChatMessage) -> String {
        [
            message.body,
            message.detail,
            searchRichElementsText(message.richElements),
            message.attachmentURL?.lastPathComponent ?? "",
            message.attachmentURL?.absoluteString ?? "",
            message.mergedForwardMessages.map(searchSnippet(for:)).joined(separator: " ")
        ]
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
            .joined(separator: " · ")
    }

    static func searchRichElementsText(_ elements: [BubbleRichElement]) -> String {
        elements.map { element in
            switch element {
            case .text(let text):
                return text
            case .blueLink(let label, let url, _):
                return "\(label) \(url)"
            case .taggedFile(let label, let url, let prefix):
                return "\(prefix)\(label) \(url)"
            case .mention(let name):
                return "@\(name)"
            case .aiToken(let token):
                return token
            case .image(let name, let url, let aspect, let access):
                return "图片 \(name) \(aspect == .vertical ? "竖图" : "横图") \(access.rawValue) \(url)"
            case .inlineCard(let kind, let title, let subtitle, let url):
                return "\(kind.rawValue) \(title) \(subtitle) \(url)"
            case .location(let title, let address, let url):
                return "位置 \(title) \(address) \(url)"
            case .quote(let author, let text):
                return "引用 \(author) \(text)"
            case .file(let name, let format, let url, let preview, let access):
                return "文件 \(format.rawValue) \(name) \(access.rawValue) \(url) \(preview)"
            }
        }
        .filter { !$0.isEmpty }
        .joined(separator: "\n")
    }

    static func searchSnippet(for message: ChatMessage) -> String {
        let text = searchableText(for: message)
        let fallback = message.type.title
        let raw = text.isEmpty ? fallback : text
        return raw.count > 92 ? "\(String(raw.prefix(92)))..." : raw
    }

    static func searchTitle(for message: ChatMessage) -> String {
        if message.type == .file, let url = message.attachmentURL {
            return url.lastPathComponent
        }
        let body = message.body.trimmingCharacters(in: .whitespacesAndNewlines)
        if !body.isEmpty {
            return body.count > 28 ? "\(String(body.prefix(28)))..." : body
        }
        return message.type.title
    }

    static func searchDetail(for message: ChatMessage) -> String {
        var lines = [
            "类型：\(message.type.title)",
            "发送人：\(message.sender.displayName)",
            "时间：\(message.timestamp)"
        ]
        let text = searchableText(for: message)
        if !text.isEmpty {
            lines.append("内容：\(text)")
        }
        return lines.joined(separator: "\n")
    }

    static func businessSearchFixtures(now: Date, accountName: String) -> [GlobalSearchResult] {
        return [
            GlobalSearchResult(
                id: "moment-materials",
                category: .moment,
                title: "朋友圈素材库",
                subtitle: "\(accountName) · 素材、草稿、评论话术",
                snippet: "图片九宫格、视频、链接、文档素材，可进入素材库继续编辑和发布。",
                targetID: nil,
                conversationID: nil,
                messageID: nil,
                messageType: nil,
                detail: "入口：素材库\n字段：tenantId、name、category、content、attachmentType、usageCount",
                updatedAt: now.addingTimeInterval(-90)
            ),
            GlobalSearchResult(
                id: "moment-feed",
                category: .moment,
                title: "朋友圈动态",
                subtitle: "\(accountName) · 点赞评论回复",
                snippet: "查看朋友圈动态、图片预览、点赞、评论和回复。",
                targetID: nil,
                conversationID: nil,
                messageID: nil,
                messageType: nil,
                detail: "入口：朋友圈\n包含动态列表、图片九宫格、评论和互动状态。",
                updatedAt: now.addingTimeInterval(-180)
            ),
            GlobalSearchResult(
                id: "finder-publish",
                category: .finder,
                title: "视频号发布",
                subtitle: "\(accountName) · 视频号内容草稿",
                snippet: "创建视频号视频，设置封面、位置、标题和发布状态。",
                targetID: nil,
                conversationID: nil,
                messageID: nil,
                messageType: nil,
                detail: "入口：视频号发布\n字段：content、medias、mediaType、cover、poi、taskId。",
                updatedAt: now.addingTimeInterval(-240)
            ),
            GlobalSearchResult(
                id: "customer-profile",
                category: .knowledge,
                title: "客户档案",
                subtitle: "SCRM · 客户信息与跟进记录",
                snippet: "客户资料、标签、交易摘要、沟通记录和跟进计划。",
                targetID: nil,
                conversationID: nil,
                messageID: nil,
                messageType: nil,
                detail: "入口：客户档案\n对齐接口中的客户资料、标签、订单、跟进任务。",
                updatedAt: now.addingTimeInterval(-320)
            ),
            GlobalSearchResult(
                id: "openapi-knowledge",
                category: .knowledge,
                title: "OpenAPI 接口说明",
                subtitle: "本地知识库 · SCRM_OpenAPI",
                snippet: "支付状态、朋友圈素材、视频号发布、客户档案、通讯录关系等接口字段说明。",
                targetID: nil,
                conversationID: nil,
                messageID: nil,
                messageType: nil,
                detail: "知识库保留当前前端页面与接口出参字段的对照说明。",
                updatedAt: now.addingTimeInterval(-520)
            )
        ]
    }

    func openGlobalSearchResult(_ result: GlobalSearchResult) {
        if let conversationID = result.conversationID, let messageID = result.messageID {
            selectFriend(id: conversationID, focusMessageID: messageID)
            if result.category == .file || result.messageType == .file {
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.55) { [weak self] in
                    guard let self,
                          let message = self.state.messages.first(where: { $0.id == messageID })
                    else { return }
                    self.performMessageAction(message)
                }
            }
            return
        }

        if let targetID = result.targetID {
            selectFriend(id: targetID)
            return
        }

        switch result.id {
        case "moment-materials":
            presentMomentMaterialsPage()
        case "moment-feed":
            presentMomentsPage()
        case "finder-publish":
            presentFinderPublishPage()
        case "customer-profile":
            presentCustomerProfilePage()
        default:
            presentGlobalSearchDetail(result)
        }
    }

    func presentGlobalSearchDetail(_ result: GlobalSearchResult) {
        let controller = GlobalSearchDetailViewController(result: result)
        controller.onOpenLink = { [weak self] urlText in
            self?.presentLinkJumpConfirmation(urlText: urlText)
        }
        pushOpenApiDemoPage(controller)
    }

    func autoReplyTestCases() -> [AutoReplyTestCase] {
        [
            AutoReplyTestCase(title: "普通咨询", body: "你现在方便帮我看一下这个安排吗？", prefersGroup: false),
            AutoReplyTestCase(title: "链接消息", body: "这个页面 https://cc2.cx/v1 你帮我确认下能不能访问。", prefersGroup: false),
            AutoReplyTestCase(title: "文件请求", body: "我发你的需求文档收到了吗？明天上午能给我反馈吗？", prefersGroup: false),
            AutoReplyTestCase(title: "链接权限", body: "这个内部链接我申请访问了，你帮我通过一下。", prefersGroup: false),
            AutoReplyTestCase(title: "群里 @", body: "@eason 今天群里的活动方案要不要同步给大家？", prefersGroup: true),
            AutoReplyTestCase(title: "群任务提醒", body: "大家下午三点前把各自负责的内容发一下。", prefersGroup: true)
        ]
    }

    func simulateIncomingMessagesForAutoReply(_ testCases: [AutoReplyTestCase]) {
        guard !testCases.isEmpty else {
            showNotice("请至少选择 1 条测试消息")
            return
        }
        let inserted = testCases.compactMap { testCase -> ChatMessage? in
            guard let conversation = autoReplyTestConversation(prefersGroup: testCase.prefersGroup) else { return nil }
            return insertSimulatedIncomingMessage(body: testCase.body, conversation: conversation)
        }

        guard let lastMessage = inserted.last else {
            showNotice("暂无可模拟的好友")
            return
        }

        selectFriend(id: lastMessage.conversationID, focusMessageID: lastMessage.id)
        rightAccountCollectionView.reloadData()
        rightToolCollectionView.reloadData()
        updateConnections()
        showNotice("已发送 \(inserted.count) 条测试消息")
        processPendingAutoReplies()
    }

    @discardableResult
    func insertSimulatedIncomingMessage(body: String, conversation: ChatParticipant) -> ChatMessage {
        let sender = simulatedIncomingSender(for: conversation)
        let recipientAccountID: UUID?
        if isGroupConversation(conversation) {
            let relatedIDs = currentAccountIDsInGroup(conversation)
            recipientAccountID = relatedIDs.contains(state.selectedAccountID)
                ? state.selectedAccountID
                : state.currentUsers.first(where: { relatedIDs.contains($0.id) })?.id
        } else {
            recipientAccountID = accountIDForDirectConversation(
                conversation,
                preferredAccountID: state.selectedAccountID
            )
        }
        let message = ChatMessage(
            id: UUID(),
            conversationID: conversation.id,
            type: .text,
            sender: sender,
            body: body,
            detail: "",
            isOutgoing: false,
            presentation: isGroupConversation(conversation) ? .avatarAndName : .avatarOnly,
            timestamp: currentTimestamp(),
            sentAt: Date(),
            isGroupConversation: isGroupConversation(conversation),
            richElements: richElements(from: body),
            recipientAccountID: recipientAccountID
        )
        state.messages.append(message)
        persistMessages()
        return message
    }

    func autoReplyTestConversation(prefersGroup: Bool) -> ChatParticipant? {
        if prefersGroup {
            return state.activeFriend.flatMap { isGroupConversation($0) ? $0 : nil }
                ?? state.friends.first { !$0.isAIAccount && isGroupConversation($0) }
                ?? state.friends.first { !$0.isAIAccount && !isGroupParticipant($0) }
        }
        return state.activeFriend.flatMap { isGroupConversation($0) ? nil : $0 }
            ?? state.friends.first { !$0.isAIAccount && !isGroupConversation($0) && !isGroupParticipant($0) }
            ?? state.friends.first { !$0.isAIAccount }
    }

    func simulatedIncomingSender(for conversation: ChatParticipant) -> ChatParticipant {
        if isGroupConversation(conversation),
           let member = groupCallParticipants(for: conversation).first {
            return member
        }
        return conversation
    }

    func handleTool(_ type: ChatMessageType) {
        executeMessageToolAction(ToolActionRouter.messageToolAction(for: type))
    }

    func executeMessageToolAction(_ action: MessageToolAction) {
        switch action {
        case .quickPhrases:
            presentQuickPhraseManager(sourceView: rightToolCollectionView)
        case .cameraPhoto:
            presentCamera(mode: .photo)
        case .mediaLibraryImages:
            presentMediaLibrary(mediaTypes: [UTType.image.identifier])
        case .videoOptions:
            presentVideoOptions()
        case .documentPicker:
            presentDocumentPicker()
        case .voiceHint:
            showNotice("按住底部语音按钮说话，松手发送")
        case .call(let type):
            presentCallTool(type: type)
        case .stickerSheet:
            showStickerSheet()
        case .stickerGifDemo:
            insertOutgoingMessage(type: .stickerGif, body: "\u{53d1}\u{9001}\u{4e86}\u{4e00}\u{5f20}\u{52a8}\u{6001}\u{8d34}\u{7eb8}", detail: "\u{672c}\u{5730}\u{6f14}\u{793a}\u{8d34}\u{7eb8}")
        case .location(let type):
            presentLocationPicker(messageType: type, seedMessage: nil)
        case .contactCardDesigner:
            presentContactCardDesigner(accountID: state.selectedAccountID)
        case .webLinkSheet:
            presentWebLinkToolSheet(sourceView: rightToolCollectionView)
        case .sendDemo(let type):
            sendDemoToolMessage(type)
        case .groupInviteComposer:
            presentGroupInviteComposer()
        case .couponWallet:
            presentCouponWalletPage()
        case .multiSelect:
            setMultiSelecting(true)
        case .relayComposer:
            presentNewRelayComposer()
        case .redPacket:
            performAccountCheckedPaymentAction(actionName: "发红包") { [weak self] in
                self?.presentRedPacketTool()
            }
        case .transfer:
            performAccountCheckedPaymentAction(actionName: "转账") { [weak self] in
                self?.presentTransferTool()
            }
        case .splitBill:
            performAccountCheckedPaymentAction(actionName: "AA 收款") { [weak self] in
                self?.presentSplitBillTool()
            }
        case .favoriteShare:
            presentFavoriteShareSheet(sourceView: rightToolCollectionView)
        }
    }

    func sendDemoToolMessage(_ type: ChatMessageType) {
        switch type {
        case .article:
            insertOutgoingMessage(
                type: .article,
                body: "公众号图文：周末城市活动",
                detail: """
                来源：城市活动观察
                摘要：活动路线、时间安排、报名入口与负责人已整理。
                url：https://cc2.cx/articles/weekend
                thumb：https://cc2.cx/cover.jpg
                appId：wx_demo_appid
                sourceName：城市活动观察
                source：official_article
                """
            )
        case .miniProgram:
            insertOutgoingMessage(
                type: .miniProgram,
                body: "微信小程序：\(Self.jdMiniProgramTitle)",
                detail: """
                appId：wx91d27dbf599dff74
                title：\(Self.jdMiniProgramTitle)
                pagePath：pages/index/index
                url：\(Self.jdMiniProgramShareLink)
                小程序链接：\(Self.jdMiniProgramShareLink)
                openUrl：weixin://
                source：jd_weapp
                sourceName：\(Self.jdMiniProgramSourceName)
                sourceUsername：gh_45b306365c3d
                scheme：weixin://
                version：0
                disForward：false
                """
            )
        case .channelsVideo:
            insertOutgoingMessage(
                type: .channelsVideo,
                body: "视频号：开业现场",
                detail: "视频号视频 · 00:18",
                attachmentURL: makeDemoVideoFile()
            )
        case .channelsLive:
            insertOutgoingMessage(
                type: .channelsLive,
                body: "视频号直播预约",
                detail: "直播中 · 可预约、可进入直播间、可查看互动状态。"
            )
        case .music:
            let seed = demoSeedMessage(type: .music, body: "音乐分享：路上听", detail: "演示歌手 · 本地可播放音乐")
            insertOutgoingMessage(
                type: .music,
                body: seed.body,
                detail: seed.detail,
                attachmentURL: makeDemoMusicAudio(for: seed)
            )
        case .coupon:
            insertOutgoingMessage(
                type: .coupon,
                body: "微信卡券：饮品券",
                detail: "门店：城市活动咖啡\n有效期：2026年7月31日\n状态：未领取"
            )
        case .groupInvite:
            insertOutgoingMessage(
                type: .groupInvite,
                body: "邀请加入周末活动群",
                detail: "群聊：周末活动群\n成员：李明、小王、陈浩、周敏\n点击可接受加入群聊。"
            )
        case .groupNotice:
            insertOutgoingMessage(
                type: .groupNotice,
                body: "群公告已更新",
                detail: "集合时间：周六 10:00\n集合地点：购物中心北门\n请携带物料清单并确认分工。"
            )
        case .quotedReply:
            insertOutgoingMessage(
                type: .quotedReply,
                body: "回复：下午 2 点可以",
                detail: "引用原消息：下午 2 点怎么样？"
            )
        case .system:
            insertSystemMessage("\(state.currentUser.displayName)撤回了一条消息", conversationID: currentConversationID())
        default:
            insertOutgoingMessage(type: type, body: type.title, detail: "本地模拟消息，可点击查看对应效果。")
        }
    }

    func sendWechatMiniProgramShareDemo() {
        insertOutgoingMessage(
            type: .miniProgram,
            body: "\(Self.jdMiniProgramTitle)｜微信小程序",
            detail: """
            样式：微信小程序转发卡片
            appId：wx91d27dbf599dff74
            title：\(Self.jdMiniProgramTitle)
            pagePath：pages/index/index
            url：\(Self.jdMiniProgramShareLink)
            小程序链接：\(Self.jdMiniProgramShareLink)
            openUrl：weixin://
            source：jd_weapp
            sourceName：\(Self.jdMiniProgramSourceName)
            sourceUsername：gh_45b306365c3d
            scheme：weixin://
            version：0
            disForward：false
            点击后跳转微信小程序打开。
            """
        )
    }

    func demoSeedMessage(type: ChatMessageType, body: String, detail: String) -> ChatMessage {
        MessageFlowCoordinator.outgoingMessage(
            conversationID: currentConversationID(),
            type: type,
            sender: state.currentUser,
            body: body,
            detail: detail,
            presentation: .avatarOnly,
            timestamp: currentTimestamp()
        )
    }

    func performAccountCheckedPaymentAction(actionName: String, action: @escaping () -> Void) {
        if let conversation = state.activeFriend, conversation.isAIAccount {
            showNotice("不能对 AI \(actionName)")
            return
        }
        guard let conversation = state.activeFriend,
              let ownerAccount = ownerAccountForCurrentConversation(),
              ownerAccount.id != state.selectedAccountID
        else {
            action()
            return
        }

        let selectedAccountName = displayNameForAccount(id: state.selectedAccountID)
        let ownerAccountName = displayNameForAccount(id: ownerAccount.id)
        let conversationKind = isGroupConversation(conversation) ? "当前群聊" : "当前好友"
        var didResolve = false
        let alert = UIAlertController(
            title: "帐号不匹配",
            message: "\(conversationKind)「\(conversation.displayName)」不属于当前帐号「\(selectedAccountName)」，属于「\(ownerAccountName)」。5 秒后自动切换到「\(ownerAccountName)」后继续\(actionName)。",
            preferredStyle: .alert
        )

        let switchAndContinue = { [weak self, weak alert] in
            guard let self, !didResolve else { return }
            didResolve = true
            self.switchSendingAccount(to: ownerAccount.id)
            let continueAction = action
            if self.presentedViewController === alert {
                alert?.dismiss(animated: true, completion: continueAction)
            } else {
                continueAction()
            }
        }

        alert.addAction(UIAlertAction(title: "取消", style: .cancel) { _ in
            didResolve = true
        })
        alert.addAction(UIAlertAction(title: "立即切换", style: .default) { _ in
            switchAndContinue()
        })
        present(alert, animated: true)

        DispatchQueue.main.asyncAfter(deadline: .now() + 5) {
            switchAndContinue()
        }
    }

    func ownerAccountForCurrentConversation() -> ChatParticipant? {
        guard let selectedFriendID = state.selectedFriendID else { return nil }
        let accountIDs = Set(state.currentUsers.map(\.id))
        return state.messages.last {
            $0.conversationID == selectedFriendID
                && $0.sender.isCurrentUser
                && accountIDs.contains($0.sender.id)
        }?.sender
    }

    func displayNameForAccount(id accountID: UUID) -> String {
        switch openApiIMBootstrapState {
        case .loading where !hasLoadedOpenApiIMSnapshot:
            return "同步中"
        case .failed where !hasLoadedOpenApiIMSnapshot:
            return "同步失败"
        case .empty:
            return "暂无帐号"
        default:
            break
        }
        if let account = state.participants.first(where: { $0.id == accountID }) {
            return contactCardProfile(for: account).displayName
        }
        return "当前帐号"
    }

    func displayName(for participant: ChatParticipant) -> String {
        let remark = participantRemarksByID[participant.id]?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        if !remark.isEmpty { return remark }
        let context = openApiConversationContextsByID[participant.id]
        let candidates = [context?.displayName, participant.displayName]
            .compactMap { $0?.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty && $0 != context?.wxid && !$0.hasPrefix("wxid_") && !$0.hasSuffix("@chatroom") }
        if let name = candidates.first { return name }
        if let friendNo = context?.friendNo.trimmingCharacters(in: .whitespacesAndNewlines), !friendNo.isEmpty {
            return friendNo
        }
        return isGroupConversation(participant) ? "未命名群聊" : "未命名好友"
    }

    func switchSendingAccount(to accountID: UUID) {
        state.selectedAccountID = accountID
        updateHeaderForSelection()
        rightAccountCollectionView.reloadData()
        if let index = state.rightAccountItems.firstIndex(where: { $0.participantID == accountID }) {
            rightAccountCollectionView.scrollToItem(
                at: IndexPath(item: index, section: 0),
                at: .centeredVertically,
                animated: true
            )
        }
    }

    func presentRedPacketTool() {
        let group = state.activeFriend.flatMap { isGroupConversation($0) ? $0 : nil }
        let maxPacketCount = group.map { max(1, groupMembers(for: $0).count) } ?? 1
        let controller = PaymentKit.redPacketComposer(
            conversationName: state.activeFriend?.displayName ?? "当前聊天",
            maxPacketCount: maxPacketCount
        )
        controller.onSend = { [weak self] amount, greeting, packetCount in
            guard let self else { return }
            let finalAmount = amount.isEmpty ? "66.00" : amount
            let finalGreeting = greeting.isEmpty ? "恭喜发财，大吉大利" : greeting
            self.presentPaymentConfirmation(
                title: "确认支付",
                amountText: "¥\(finalAmount)",
                subtitle: "发红包给 \(self.state.activeFriend?.displayName ?? "当前聊天")",
                confirmTitle: "塞钱进红包"
            ) { method, password in
                self.submitOpenApiPayment(
                    path: "/openapi/v1/payments/lucky-money",
                    action: "发送红包",
                    amountText: finalAmount,
                    password: password,
                    additionalBody: ["number": packetCount, "wish": finalGreeting]
                ) { [weak self] in
                    guard let self else { return }
                    let detail: String
                    if maxPacketCount > 1 {
                        detail = "红包金额：¥\(finalAmount)\n红包个数：\(packetCount)\n已领取：0/\(packetCount)\n状态：等待领取\n支付方式：\(method)\n领取明细："
                    } else {
                        detail = "红包金额：¥\(finalAmount)\n已领取：0/1\n状态：等待领取\n支付方式：\(method)\n领取明细："
                    }
                    self.insertOutgoingMessage(type: .redPacket, body: finalGreeting, detail: detail)
                    self.navigationController?.popToViewController(self, animated: true)
                }
            }
        }
        pushPaymentPage(controller)
    }

    func presentTransferTool() {
        if let friend = state.activeFriend, isGroupParticipant(friend) {
            presentGroupTransferReceiverPicker(group: friend)
            return
        }
        let receiver = state.activeFriend
        presentTransferAmountForm(receiverName: receiver?.displayName ?? "当前好友", receiverID: receiver?.id)
    }

    func presentGroupTransferReceiverPicker(group: ChatParticipant) {
        let candidates = groupTransferCandidates(for: group)
        guard !candidates.isEmpty else {
            showMessageActionNotice("当前群聊没有可转账成员")
            return
        }
        let controller = PaymentKit.memberSelectionController(
            titleText: "选择收款人",
            subtitleText: "向\(group.displayName)中的成员转账",
            members: candidates,
            allowsMultipleSelection: false,
            selectedIDs: []
        )
        controller.onConfirm = { [weak self] selectedMembers in
            guard let receiver = selectedMembers.first else { return }
            self?.presentTransferAmountForm(receiverName: receiver.displayName, receiverID: receiver.id)
        }
        pushPaymentPage(controller)
    }

    func groupTransferCandidates(for group: ChatParticipant) -> [ChatParticipant] {
        let names: [String]
        if group.displayName.contains("\u{8fd0}\u{8425}") {
            names = ["\u{674e}\u{660e}", "\u{5c0f}\u{738b}", "\u{9648}\u{6d69}", "\u{5468}\u{654f}", "\u{8d75}\u{78ca}", "\u{5b59}\u{7433}"]
        } else if group.displayName.contains("\u{8bbe}\u{8ba1}") {
            names = ["\u{5c0f}\u{738b}", "\u{9648}\u{6d69}", "\u{5468}\u{654f}", "\u{94b1}\u{96e8}", "\u{5434}\u{8fea}"]
        } else {
            names = state.friends
                .filter { !isGroupParticipant($0) && !$0.isAIAccount }
                .prefix(6)
                .map(\.displayName)
        }
        let candidates = names.compactMap { name in
            state.participants.first { $0.displayName == name && !$0.isCurrentUser }
        }
        return candidates.isEmpty
            ? state.friends.filter { !isGroupParticipant($0) && !$0.isAIAccount }
            : candidates
    }

    func presentTransferAmountForm(receiverName: String, receiverID: UUID? = nil) {
        let controller = PaymentKit.transferComposer(receiverName: receiverName)
        controller.onSend = { [weak self] amount, note in
            guard let self else { return }
            let finalAmount = amount.isEmpty ? "88.00" : amount
            let receiverIDLine = receiverID.map { "\n收款方ID：\($0.uuidString)" } ?? ""
            self.presentPaymentConfirmation(
                title: "确认转账",
                amountText: "¥\(finalAmount)",
                subtitle: "转账给 \(receiverName)",
                confirmTitle: "确认转账"
            ) { method, password in
                self.submitOpenApiPayment(
                    path: "/openapi/v1/payments/remittance",
                    action: "发起转账",
                    amountText: finalAmount,
                    password: password,
                    additionalBody: ["memo": note]
                ) { [weak self] in
                    guard let self else { return }
                    self.insertOutgoingMessage(
                        type: .transfer,
                        body: "转账给\(receiverName) ¥\(finalAmount)",
                        detail: "收款方：\(receiverName)\(receiverIDLine)\n金额：¥\(finalAmount)\n状态：待收款\n支付方式：\(method)\n备注：\(note.isEmpty ? "无" : note)"
                    )
                    self.navigationController?.popToViewController(self, animated: true)
                }
            }
        }
        pushPaymentPage(controller)
    }

    func presentSplitBillTool() {
        guard let group = state.activeFriend, isGroupConversation(group) else {
            showMessageActionNotice("AA 收款只能在群聊中发起")
            return
        }
        let members = groupTransferCandidates(for: group)
        guard !members.isEmpty else {
            showMessageActionNotice("当前群聊没有可收款成员")
            return
        }
        let controller = PaymentKit.memberSelectionController(
            titleText: "选择 AA 成员",
            subtitleText: "\(group.displayName) · 可选择指定成员，也可以全选",
            members: members,
            allowsMultipleSelection: true,
            selectedIDs: Set(members.map(\.id))
        )
        controller.onConfirm = { [weak self] selectedMembers in
            self?.presentSplitBillAmountForm(group: group, selectedMembers: selectedMembers)
        }
        pushPaymentPage(controller)
    }

    func presentSplitBillAmountForm(group: ChatParticipant, selectedMembers: [ChatParticipant]) {
        guard !selectedMembers.isEmpty else {
            showMessageActionNotice("请至少选择 1 个收款成员")
            return
        }
        let controller = PaymentKit.splitBillComposer(groupName: group.displayName, selectedMembers: selectedMembers)
        controller.onSend = { [weak self] amount in
            let finalAmount = amount.isEmpty ? "320.00" : amount
            let names = selectedMembers.map(\.displayName).joined(separator: "、")
            let ids = selectedMembers.map { $0.id.uuidString }.joined(separator: "、")
            self?.insertOutgoingMessage(
                type: .splitBill,
                body: "群收款 \(selectedMembers.count) 人",
                detail: "群收款总额：¥\(finalAmount)\n收款群：\(group.displayName)\n已收 0/\(selectedMembers.count) 人\n收款成员：\(names)\n收款成员ID：\(ids)\n未支付：\(names)"
            )
            if let self {
                self.navigationController?.popToViewController(self, animated: true)
            }
        }
        pushPaymentPage(controller)
    }

    func presentGroupInviteComposer() {
        let accounts = state.currentUsers
        guard !accounts.isEmpty else {
            showMessageActionNotice("暂无可选择的右侧帐号")
            return
        }
        let controller = GroupInviteComposerViewController(accounts: accounts, currentAccountID: state.selectedAccountID)
        controller.onSend = { [weak self] draft in
            self?.sendGroupInviteCard(draft)
        }
        navigationController?.setNavigationBarHidden(false, animated: false)
        navigationItem.backBarButtonItem = UIBarButtonItem(title: "返回", style: .plain, target: nil, action: nil)
        navigationController?.pushViewController(controller, animated: true)
    }

    func sendGroupInviteCard(_ draft: GroupInviteDraft) {
        let groupName = draft.groupName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            ? "新活动群"
            : draft.groupName.trimmingCharacters(in: .whitespacesAndNewlines)
        let memberNames = draft.members.map(\.displayName).joined(separator: "、")
        let groupNicknames = draft.groupNicknames
            .map { "\($0.key.displayName)：\($0.value.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? $0.key.displayName : $0.value)" }
            .joined(separator: "\n")
        let detail = [
            "群聊：\(groupName)",
            "备注：\(draft.remark.trimmingCharacters(in: .whitespacesAndNewlines))",
            "活动：\(draft.activity.trimmingCharacters(in: .whitespacesAndNewlines))",
            "成员：\(memberNames)",
            "群中名字：",
            groupNicknames,
            "点击可接受加入群聊。"
        ].filter { !$0.isEmpty }.joined(separator: "\n")

        insertOutgoingMessage(
            type: .groupInvite,
            body: "邀请加入「\(groupName)」",
            detail: detail
        )
        navigationController?.popToViewController(self, animated: true)
    }

    func pushPaymentPage(_ controller: UIViewController) {
        navigationController?.setNavigationBarHidden(false, animated: false)
        navigationItem.backBarButtonItem = UIBarButtonItem(title: "返回", style: .plain, target: nil, action: nil)
        navigationController?.pushViewController(controller, animated: true)
    }

    func presentPaymentConfirmation(
        title: String,
        amountText: String,
        subtitle: String,
        confirmTitle: String,
        onCancel: (() -> Void)? = nil,
        completion: @escaping (String, String) -> Void
    ) {
        let controller = PaymentKit.confirmationController(
            title: title,
            amountText: amountText,
            subtitle: subtitle,
            confirmTitle: confirmTitle
        )
        controller.onConfirm = { [weak controller] method, password in
            controller?.dismiss(animated: true) {
                completion(method, password)
            }
        }
        controller.onCancel = onCancel
        let presenter = navigationController?.topViewController ?? self
        presenter.present(controller, animated: false)
        controller.updateWalletBalance("余额查询中…")
        Task { @MainActor in
            do {
                let balanceText = try await self.loadOpenApiWalletBalanceText()
                controller.updateWalletBalance(balanceText)
            } catch {
                controller.updateWalletBalance("余额暂不可用")
            }
        }
    }

    func loadOpenApiWalletBalanceText() async throws -> String {
        guard let context = openApiSendContextForCurrentSelection() else {
            throw OpenApiMediaSendError.missingRequiredFields("查询钱包余额", "当前帐号与会话不匹配")
        }
        let result = try await OpenApiHTTPClient.request(
            "POST",
            path: "/openapi/v1/payments/wallet-balance",
            body: [
                "deviceUuid": context.account.clientUuid,
                "weChatId": context.account.wxid,
                "flag": 1
            ]
        )
        let payload = try await awaitOpenApiTaskPayloadIfNeeded(result, action: "查询钱包余额")
        guard let amount = openApiWalletBalance(from: payload) else {
            throw OpenApiMediaSendError.taskResultUnknown("查询钱包余额", "任务成功但未返回余额字段")
        }
        return "可用余额 ¥\(NSDecimalNumber(decimal: amount).stringValue)"
    }

    func openApiWalletBalance(from object: Any?) -> Decimal? {
        if let dictionary = object as? [String: Any] {
            let fenKeys = ["balanceFen", "availableBalanceFen", "walletBalanceFen", "moneyFen", "amountFen"]
            for key in fenKeys {
                if let value = openApiDecimalValue(openApiValue(in: dictionary, matching: key)) {
                    return value / 100
                }
            }
            let yuanKeys = ["balance", "availableBalance", "walletBalance", "money", "amount"]
            for key in yuanKeys {
                if let value = openApiDecimalValue(openApiValue(in: dictionary, matching: key)) {
                    return value
                }
            }
            for key in ["data", "result", "payload", "task", "taskResult", "wallet", "account"] {
                if let value = openApiWalletBalance(from: openApiValue(in: dictionary, matching: key)) {
                    return value
                }
            }
        } else if let array = object as? [Any] {
            for item in array {
                if let value = openApiWalletBalance(from: item) {
                    return value
                }
            }
        } else if let text = object as? String,
                  let data = text.data(using: .utf8),
                  let decoded = try? JSONSerialization.jsonObject(with: data) {
            return openApiWalletBalance(from: decoded)
        }
        return nil
    }

    func openApiValue(in dictionary: [String: Any], matching key: String) -> Any? {
        if let value = dictionary[key] { return value }
        return dictionary.first { $0.key.caseInsensitiveCompare(key) == .orderedSame }?.value
    }

    func openApiDecimalValue(_ value: Any?) -> Decimal? {
        if let number = value as? NSNumber {
            return number.decimalValue
        }
        if let text = value as? String {
            let cleaned = text
                .replacingOccurrences(of: "¥", with: "")
                .replacingOccurrences(of: ",", with: "")
                .trimmingCharacters(in: .whitespacesAndNewlines)
            return Decimal(string: cleaned)
        }
        return nil
    }

    func submitOpenApiPayment(
        path: String,
        action: String,
        amountText: String,
        password: String,
        additionalBody: [String: Any],
        completion: @escaping () -> Void
    ) {
        guard let context = openApiSendContextForCurrentSelection() else {
            showNotice("当前帐号与会话不匹配，无法\(action)")
            return
        }
        guard let amount = Decimal(string: amountText), amount > 0 else {
            showNotice("请输入正确的支付金额")
            return
        }
        let moneyFen = NSDecimalNumber(decimal: amount * 100).intValue
        var body: [String: Any] = [
            "deviceUuid": context.account.clientUuid,
            "weChatId": context.account.wxid,
            "moneyFen": moneyFen,
            "paymentPassword": password
        ]
        if context.conversation.kind == .chatroom {
            if path.hasSuffix("/remittance") {
                body["roomId"] = context.conversation.wxid
            } else {
                body["friendId"] = context.conversation.wxid
            }
        } else {
            body["friendId"] = context.conversation.wxid
        }
        additionalBody.forEach { body[$0.key] = $0.value }
        showNotice("正在\(action)…")
        Task { @MainActor in
            do {
                let result = try await OpenApiHTTPClient.request("POST", path: path, body: body)
                _ = try await self.validateOpenApiTaskResult(result, action: action)
                completion()
                self.showNotice("\(action)成功")
            } catch {
                self.showNotice("\(action)失败：\(error.localizedDescription)")
            }
        }
    }

    func presentCallTool(type: ChatMessageType) {
        guard let conversation = state.activeFriend else {
            showMessageActionNotice("请先进入具体好友或群聊后再发起通话")
            return
        }
        guard !conversation.isAIAccount else {
            showMessageActionNotice("AI 不能发起语音或视频通话")
            return
        }
        let isGroup = isGroupConversation(conversation)
        let participants = isGroup ? groupCallParticipants(for: conversation) : []
        let remoteParticipants = isGroup
            ? participants
            : [conversation]

        guard !remoteParticipants.isEmpty else {
            showMessageActionNotice("当前聊天没有可通话对象")
            return
        }

        let controller: UIViewController
        let onEndCall: (@escaping (TimeInterval) -> Void) -> Void
        if type == .voiceCall {
            let voiceController = VoiceCallViewController(
                localParticipant: state.currentUser,
                remoteParticipants: remoteParticipants,
                isGroup: isGroup
            )
            controller = voiceController
            onEndCall = { voiceController.onEndCall = $0 }
        } else {
            let videoController = VideoCallViewController(
                localParticipant: state.currentUser,
                remoteParticipants: remoteParticipants,
                isGroup: isGroup
            )
            controller = videoController
            onEndCall = { videoController.onEndCall = $0 }
        }

        onEndCall { [weak self] duration in
            self?.sendCallToolMessage(
                type: type,
                isGroup: isGroup,
                participants: participants,
                duration: duration
            )
        }
        controller.modalPresentationStyle = .fullScreen
        present(controller, animated: true)
    }

    func sendCallToolMessage(type: ChatMessageType, isGroup: Bool, participants: [ChatParticipant], duration: TimeInterval) {
        let durationText = formattedCallDuration(duration)
        let isVideo = type == .videoCall
        if isGroup {
            let names = participants.map(\.displayName).joined(separator: "、")
            insertOutgoingMessage(
                type: type,
                body: isVideo ? "群视频通话 \(durationText)" : "群语音通话 \(durationText)",
                detail: names.isEmpty ? "已邀请群聊相关成员" : "参与成员：\(names)"
            )
            showNotice(isVideo ? "群视频通话已结束" : "群语音通话已结束")
        } else {
            let name = state.activeFriend?.displayName ?? "当前好友"
            insertOutgoingMessage(
                type: type,
                body: isVideo ? "视频通话 \(durationText)" : "语音通话 \(durationText)",
                detail: "通话对象：\(name)"
            )
            showNotice(isVideo ? "视频通话已结束" : "语音通话已结束")
        }
    }

    func presentVideoCallToolConfirmation() {
        let conversation = state.activeFriend
        let isGroup = isGroupConversation(conversation)
        let participants = isGroup ? groupCallParticipants(for: conversation) : []
        let remoteParticipants = isGroup
            ? participants
            : [conversation].compactMap { $0 }
        let controller = VideoCallViewController(
            localParticipant: state.currentUser,
            remoteParticipants: remoteParticipants,
            isGroup: isGroup
        )
        controller.onEndCall = { [weak self] duration in
            self?.sendVideoCallToolMessage(
                isGroup: isGroup,
                participants: participants,
                duration: duration
            )
        }
        controller.modalPresentationStyle = .fullScreen
        present(controller, animated: true)
    }

    func sendVideoCallToolMessage(isGroup: Bool, participants: [ChatParticipant], duration: TimeInterval) {
        let durationText = formattedCallDuration(duration)
        if isGroup {
            let names = participants.map(\.displayName).joined(separator: "、")
            insertOutgoingMessage(
                type: .videoCall,
                body: "群视频通话 \(durationText)",
                detail: names.isEmpty ? "已邀请群聊相关成员" : "参与成员：\(names)"
            )
            showNotice("群视频通话已结束")
        } else {
            let name = state.activeFriend?.displayName ?? "当前好友"
            insertOutgoingMessage(
                type: .videoCall,
                body: "视频通话 \(durationText)",
                detail: "通话对象：\(name)"
            )
            showNotice("视频通话已结束")
        }
    }

    func formattedCallDuration(_ duration: TimeInterval) -> String {
        let seconds = max(1, Int(duration.rounded()))
        return String(format: "%02d:%02d", seconds / 60, seconds % 60)
    }

    func isGroupConversation(_ participant: ChatParticipant?) -> Bool {
        guard let participant else { return false }
        if let context = openApiConversationContextsByID[participant.id],
           context.kind == .chatroom {
            return true
        }
        if participant.kind == .group {
            return true
        }
        ensureGroupConversationMetadataCache()
        return groupConversationIDsCache.contains(participant.id)
    }

    func groupCallParticipants(for group: ChatParticipant?) -> [ChatParticipant] {
        guard let group else { return [] }
        var participants = state.messages
            .filter { $0.conversationID == group.id && $0.isGroupConversation }
            .map(\.sender)
            .filter { !$0.isCurrentUser && !$0.isAIAccount }

        if participants.isEmpty {
            participants = state.friends.filter {
                !$0.isCurrentUser
                    && !$0.isAIAccount
                    && $0.id != group.id
                    && $0.kind != .group
                    && $0.kind != .groupMember
            }
        }

        return participants.reduce(into: [ChatParticipant]()) { result, participant in
            guard !result.contains(where: { $0.id == participant.id }) else { return }
            result.append(participant)
        }
    }

    func groupMembers(for group: ChatParticipant) -> [ChatParticipant] {
        ensureGroupConversationMetadataCache()
        if let cached = resolvedGroupMembersCache[group.id] {
            return cached
        }

        let members: [ChatParticipant]
        if let explicitIDs = explicitGroupMemberIDsByGroupID[group.id], !explicitIDs.isEmpty {
            let removedIDs = removedGroupMemberIDsByGroupID[group.id] ?? []
            let participantsByID = participantLookupByID()
            members = explicitIDs.compactMap { id in
                guard !removedIDs.contains(id) else { return nil }
                return participantsByID[id]
            }
        } else {
            var candidates = inferredGroupMembersCache[group.id] ?? []
            candidates.append(contentsOf: state.currentUsers)
            let removedIDs = removedGroupMemberIDsByGroupID[group.id] ?? []
            var seenIDs = Set<UUID>()
            members = candidates.filter { member in
                !removedIDs.contains(member.id) && seenIDs.insert(member.id).inserted
            }
        }
        resolvedGroupMembersCache[group.id] = members
        return members
    }

    func ensureGroupConversationMetadataCache() {
        guard groupConversationMetadataCacheRevision != visibleDataRevision else { return }
        var conversationIDs = Set<UUID>()
        var inferredMembers: [UUID: [ChatParticipant]] = [:]
        inferredMembers.reserveCapacity(state.participants.count / 4)
        for message in state.messages where message.isGroupConversation {
            conversationIDs.insert(message.conversationID)
            if !message.sender.isAIAccount {
                inferredMembers[message.conversationID, default: []].append(message.sender)
            }
        }
        groupConversationIDsCache = conversationIDs
        inferredGroupMembersCache = inferredMembers
        resolvedGroupMembersCache.removeAll(keepingCapacity: true)
        groupConversationMetadataCacheRevision = visibleDataRevision
    }

    func groupDisplaySettings(for group: ChatParticipant) -> GroupDisplaySettings {
        var settings = groupDisplaySettingsByGroupID[group.id] ?? GroupDisplaySettings()
        if settings.myGroupName.isEmpty {
            settings.myGroupName = state.contactCards[state.selectedAccountID]?.displayName ?? state.currentUser.displayName
        }
        return settings
    }

    func loadGroupDisplaySettings() {
        let persisted = ChatSQLiteStore.shared.codable(
            [String: GroupDisplaySettings].self,
            forKey: Self.groupDisplaySettingsStorageKey
        ) ?? [:]
        groupDisplaySettingsByGroupID = persisted.reduce(into: [:]) { result, entry in
            guard let id = UUID(uuidString: entry.key) else { return }
            result[id] = entry.value
        }
    }

    func persistGroupDisplaySettings() {
        let persisted = Dictionary(
            uniqueKeysWithValues: groupDisplaySettingsByGroupID.map { ($0.key.uuidString, $0.value) }
        )
        ChatSQLiteStore.shared.setCodable(persisted, forKey: Self.groupDisplaySettingsStorageKey)
    }

    func groupDisplaySettings(for message: ChatMessage) -> GroupDisplaySettings {
        guard message.isGroupConversation,
              let group = state.participants.first(where: { $0.id == message.conversationID })
        else { return GroupDisplaySettings() }
        return groupDisplaySettings(for: group)
    }

    func applyMyGroupNameToLocalMessages(in group: ChatParticipant) {
        let sender = outgoingSenderForSelectedAccount(in: group)
        var didChange = false
        for index in state.messages.indices {
            let message = state.messages[index]
            guard message.conversationID == group.id,
                  message.isGroupConversation,
                  message.isOutgoing,
                  message.sender.id == state.selectedAccountID,
                  message.sender.displayName != sender.displayName
            else { continue }
            state.messages[index] = replacingMessage(message, sender: sender)
            didChange = true
        }
        guard didChange else { return }
        clearMessageHeightCache()
        persistMessages()
    }

    func presentGroupSidebarSettings(for group: ChatParticipant, sourceView: UIView) {
        let settings = groupDisplaySettings(for: group)
        let alert = UIAlertController(title: group.displayName, message: "群内用户展示设置", preferredStyle: .actionSheet)
        alert.addAction(UIAlertAction(title: "查看群聊信息", style: .default) { [weak self] _ in
            self?.selectFriend(id: group.id)
            self?.presentGroupInfoPage(for: group)
        })
        alert.addAction(UIAlertAction(title: settings.showsAvatar ? "停用群成员头像" : "启用群成员头像", style: .default) { [weak self] _ in
            self?.updateGroupDisplaySettings(groupID: group.id, showsAvatar: !settings.showsAvatar, showsName: settings.showsName)
        })
        alert.addAction(UIAlertAction(title: settings.showsName ? "停用群成员名称" : "启用群成员名称", style: .default) { [weak self] _ in
            self?.updateGroupDisplaySettings(groupID: group.id, showsAvatar: settings.showsAvatar, showsName: !settings.showsName)
        })
        alert.addAction(UIAlertAction(title: "取消", style: .cancel))
        presentSheet(alert, sourceView: sourceView)
    }

    func updateGroupDisplaySettings(groupID: UUID, showsAvatar: Bool, showsName: Bool) {
        guard showsAvatar || showsName else {
            showNotice("群成员头像和名称必须至少启用一个")
            return
        }
        var settings = groupDisplaySettingsByGroupID[groupID] ?? GroupDisplaySettings()
        settings.showsAvatar = showsAvatar
        settings.showsName = showsName
        groupDisplaySettingsByGroupID[groupID] = settings
        persistGroupDisplaySettings()
        messageCollectionView.reloadData()
        updateConnections()
        showNotice("群内用户展示设置已更新")
    }

    func sendNameEnabled(for accountID: UUID) -> Bool {
        sendNameEnabledByAccountID[accountID] ?? true
    }

    func outgoingPresentationForSelectedAccount(default presentation: BubblePresentation = .avatarOnly) -> BubblePresentation {
        guard sendNameEnabled(for: state.selectedAccountID) else { return .avatarOnly }
        return presentation == .bare ? .bare : .avatarAndName
    }

    func outgoingSenderForSelectedAccount() -> ChatParticipant {
        outgoingSenderForSelectedAccount(in: state.activeFriend)
    }

    func outgoingSenderForSelectedAccount(in conversation: ChatParticipant?) -> ChatParticipant {
        let sender = state.currentUsers.first { $0.id == state.selectedAccountID } ?? state.currentUser
        guard let conversation,
              isGroupParticipant(conversation)
        else { return sender }

        let alias = groupDisplaySettings(for: conversation)
            .myGroupName
            .trimmingCharacters(in: .whitespacesAndNewlines)
        guard !alias.isEmpty, alias != sender.displayName else { return sender }
        return ChatParticipant(
            id: sender.id,
            displayName: alias,
            tintColor: sender.tintColor,
            initials: String(alias.prefix(1)),
            isCurrentUser: sender.isCurrentUser,
            avatarURL: sender.avatarURL,
            kind: sender.kind
        )
    }

    func presentSendNameSetting(sourceView: UIView) {
        let account = state.currentUsers.first { $0.id == state.selectedAccountID } ?? state.currentUser
        let enabled = sendNameEnabled(for: account.id)
        let alert = UIAlertController(
            title: "发送名字设置",
            message: "\(displayNameForAccount(id: account.id)) 发送消息前是否携带名字",
            preferredStyle: .actionSheet
        )
        alert.addAction(UIAlertAction(title: enabled ? "关闭携带名字" : "开启携带名字", style: .default) { [weak self] _ in
            guard let self else { return }
            self.sendNameEnabledByAccountID[account.id] = !enabled
            self.rightToolCollectionView.reloadData()
            self.messageCollectionView.reloadData()
            self.updateConnections()
        })
        alert.addAction(UIAlertAction(title: "取消", style: .cancel))
        presentSheet(alert, sourceView: sourceView)
    }

    func presentLeftSidebarDisplayModeSheet(sourceView: UIView) {
        let contextText = state.selectedFriendID == nil
            ? "主页会显示所有对象，再按这里选择的类型筛选。"
            : "当前右侧帐号会先筛选相关好友和群聊，再按这里选择的类型显示。"
        let alert = UIAlertController(
            title: "左侧显示",
            message: contextText,
            preferredStyle: .actionSheet
        )
        for mode in LeftSidebarDisplayMode.allCases {
            let prefix = mode == leftSidebarDisplayMode ? "✓ " : ""
            alert.addAction(UIAlertAction(title: "\(prefix)\(mode.title)", style: .default) { [weak self] _ in
                self?.setLeftSidebarDisplayMode(mode)
            })
        }
        alert.addAction(UIAlertAction(title: "取消", style: .cancel))
        presentSheet(alert, sourceView: sourceView)
    }

    func presentLeftParticipantActions(for participant: ChatParticipant, sourceView: UIView) {
        let isGroup = isGroupConversation(participant)
        let title = displayName(for: participant)
        let alert = UIAlertController(title: title, message: isGroup ? "群聊管理" : "好友管理", preferredStyle: .actionSheet)

        alert.addAction(UIAlertAction(title: isGroup ? "修改备注群" : "修改备注", style: .default) { [weak self] _ in
            self?.presentRemarkEditor(for: participant)
        })
        alert.addAction(UIAlertAction(title: "隐藏显示", style: .default) { [weak self] _ in
            self?.hideParticipant(participant)
        })

        if isGroup {
            alert.addAction(UIAlertAction(title: "群聊设置", style: .default) { [weak self, weak sourceView] _ in
                guard let self else { return }
                self.presentGroupSidebarSettings(for: participant, sourceView: sourceView ?? self.leftCollectionView)
            })
        } else {
            alert.addAction(UIAlertAction(title: "拍一拍", style: .default) { [weak self] _ in
                self?.sendPatPat(to: participant)
            })
            alert.addAction(UIAlertAction(title: "删除好友", style: .destructive) { [weak self] _ in
                self?.confirmDeleteParticipant(participant)
            })
        }

        alert.addAction(UIAlertAction(title: "取消", style: .cancel))
        presentSheet(alert, sourceView: sourceView)
    }

    func presentRemarkEditor(for participant: ChatParticipant, from presenter: UIViewController? = nil) {
        let isGroup = isGroupConversation(participant)
        let alert = UIAlertController(title: isGroup ? "修改备注群" : "修改备注", message: participant.displayName, preferredStyle: .alert)
        alert.addTextField { [weak self] field in
            field.placeholder = isGroup ? "填写备注群名称" : "填写好友备注"
            field.text = self?.participantRemarksByID[participant.id] ?? ""
            field.clearButtonMode = .whileEditing
        }
        alert.addAction(UIAlertAction(title: "取消", style: .cancel))
        alert.addAction(UIAlertAction(title: "保存", style: .default) { [weak self, weak alert] _ in
            guard let self else { return }
            let text = alert?.textFields?.first?.text?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            if text.isEmpty {
                self.participantRemarksByID.removeValue(forKey: participant.id)
            } else {
                self.participantRemarksByID[participant.id] = text
            }
            self.refreshParticipantDisplay()
        })
        (presenter ?? self).present(alert, animated: true)
    }

    func hideParticipant(_ participant: ChatParticipant) {
        hiddenParticipantIDs.insert(participant.id)
        if state.selectedFriendID == participant.id {
            selectFriend(id: nil)
        } else {
            refreshParticipantDisplay()
        }
    }

    func confirmDeleteParticipant(_ participant: ChatParticipant) {
        let alert = UIAlertController(
            title: "删除好友",
            message: "确定删除「\(displayName(for: participant))」？相关模拟聊天记录也会从当前列表移除。",
            preferredStyle: .alert
        )
        alert.addAction(UIAlertAction(title: "取消", style: .cancel))
        alert.addAction(UIAlertAction(title: "删除", style: .destructive) { [weak self] _ in
            self?.deleteParticipant(participant)
        })
        present(alert, animated: true)
    }

    func deleteParticipant(_ participant: ChatParticipant) {
        let participantID = participant.id
        state.participants.removeAll { $0.id == participantID }
        state.messages.removeAll { $0.conversationID == participantID || $0.sender.id == participantID }
        removeOpenApiConversationFromPersistedSnapshot(participantID: participantID)
        persistMessages()
        participantRemarksByID.removeValue(forKey: participantID)
        hiddenParticipantIDs.remove(participantID)
        if state.selectedFriendID == participantID {
            state.selectedFriendID = nil
        }
        refreshParticipantDisplay()
    }

    func refreshParticipantDisplay() {
        cancelLeftScrollPreview()
        invalidateVisibleDataCaches()
        updateHeaderForSelection()
        leftCollectionView.reloadData()
        updateLeftCollectionBounceInsets()
        messageCollectionView.reloadData()
        DispatchQueue.main.async { [weak self] in
            self?.updateLeftCollectionBounceInsets()
            self?.updateConnections()
        }
    }

}
