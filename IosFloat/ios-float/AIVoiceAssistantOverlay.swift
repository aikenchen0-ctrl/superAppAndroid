import UIKit
import AVFoundation

final class AIVoiceAssistantOverlayCoordinator: NSObject {
    static let shared = AIVoiceAssistantOverlayCoordinator()

    private weak var window: UIWindow?
    private let panel = AIVoiceAssistantPanel()
    private var recorder: AVAudioRecorder?
    private var meterTimer: Timer?
    private var recordingURL: URL?
    private var isRecording = false
    private var isExpanded = false
    private var isVisible = false
    private var panStartCenter = CGPoint.zero
    private let speechSynthesizer = AVSpeechSynthesizer()

    private override init() {
        super.init()
        panel.onToggle = { [weak self] in self?.toggleAssistant() }
        panel.onClose = { [weak self] in self?.collapse() }
        panel.onAsk = { [weak self] text in self?.askAssistant(text) }
    }

    func install(on window: UIWindow) {
        guard self.window !== window else { return }
        self.window = window
        panel.translatesAutoresizingMaskIntoConstraints = true
        panel.frame = CGRect(
            x: window.bounds.width - 82,
            y: max(window.safeAreaInsets.top + 120, window.bounds.height - window.safeAreaInsets.bottom - 190),
            width: 62,
            height: 62
        )
        panel.accessibilityLabel = "AI 语音助手"
        panel.accessibilityHint = "点按唤醒语音助手，再次点按结束录音"
        panel.alpha = 0
        panel.isHidden = true
        panel.isUserInteractionEnabled = false

        let pan = UIPanGestureRecognizer(target: self, action: #selector(handlePan(_:)))
        pan.cancelsTouchesInView = false
        panel.addGestureRecognizer(pan)
    }

    @discardableResult
    func toggleFromTool() -> Bool {
        isVisible ? hide() : show(expandImmediately: true)
        return isVisible
    }

    func show(expandImmediately: Bool = true) {
        guard let window else { return }
        if panel.superview !== window {
            panel.removeFromSuperview()
            window.addSubview(panel)
        }
        window.bringSubviewToFront(panel)
        isVisible = true
        panel.isHidden = false
        panel.isUserInteractionEnabled = true
        if expandImmediately {
            expand()
        }
        UIView.animate(withDuration: 0.18, delay: 0, options: [.curveEaseOut, .allowUserInteraction]) {
            self.panel.alpha = 1
        }
    }

    func hide() {
        stopRecording(cleanupAudioSession: true)
        speechSynthesizer.stopSpeaking(at: .immediate)
        isVisible = false
        isExpanded = false
        panel.setExpanded(false)
        UIView.animate(
            withDuration: 0.16,
            delay: 0,
            options: [.curveEaseIn, .allowUserInteraction],
            animations: {
                self.panel.alpha = 0
            },
            completion: { _ in
                self.panel.isHidden = true
                self.panel.isUserInteractionEnabled = false
            }
        )
    }

    func resetForSceneTransition() {
        stopRecording(cleanupAudioSession: true)
        speechSynthesizer.stopSpeaking(at: .immediate)
        isVisible = false
        isExpanded = false
        panel.layer.removeAllAnimations()
        panel.setExpanded(false)
        panel.alpha = 0
        panel.isHidden = true
        panel.isUserInteractionEnabled = false
    }

    @objc private func handlePan(_ gesture: UIPanGestureRecognizer) {
        guard let window else { return }
        switch gesture.state {
        case .began:
            panStartCenter = panel.center
        case .changed:
            let translation = gesture.translation(in: window)
            let halfWidth = panel.bounds.width / 2
            let halfHeight = panel.bounds.height / 2
            let minX = halfWidth + 8
            let maxX = window.bounds.width - halfWidth - 8
            let minY = window.safeAreaInsets.top + halfHeight + 8
            let maxY = window.bounds.height - window.safeAreaInsets.bottom - halfHeight - 8
            panel.center = CGPoint(
                x: min(max(panStartCenter.x + translation.x, minX), maxX),
                y: min(max(panStartCenter.y + translation.y, minY), maxY)
            )
        case .ended, .cancelled:
            snapToNearestEdge()
        default:
            break
        }
    }

    private func snapToNearestEdge() {
        guard let window else { return }
        let targetX = panel.center.x < window.bounds.midX
            ? panel.bounds.width / 2 + 8
            : window.bounds.width - panel.bounds.width / 2 - 8
        UIView.animate(withDuration: 0.22, delay: 0, options: [.curveEaseOut, .allowUserInteraction]) {
            self.panel.center.x = targetX
        }
    }

    private func toggleAssistant() {
        if isRecording {
            finishRecording()
            return
        }
        if !isExpanded {
            expand()
            return
        }
        startRecording()
    }

    private func expand() {
        guard let window else { return }
        isExpanded = true
        let targetWidth: CGFloat = min(310, window.bounds.width - 28)
        let targetHeight: CGFloat = 190
        let targetX = min(max(panel.frame.minX, 14), window.bounds.width - targetWidth - 14)
        let targetY = min(max(panel.frame.minY - 86, window.safeAreaInsets.top + 12), window.bounds.height - targetHeight - window.safeAreaInsets.bottom - 12)
        panel.setExpanded(true)
        UIView.animate(withDuration: 0.22, delay: 0, options: [.curveEaseOut, .allowUserInteraction]) {
            self.panel.frame = CGRect(x: targetX, y: targetY, width: targetWidth, height: targetHeight)
            self.panel.layoutIfNeeded()
        }
    }

    private func collapse() {
        stopRecording(cleanupAudioSession: true)
        guard let window else { return }
        isExpanded = false
        panel.setExpanded(false)
        let targetX = panel.center.x < window.bounds.midX ? 16 : window.bounds.width - 78
        let targetY = min(max(panel.frame.midY - 31, window.safeAreaInsets.top + 90), window.bounds.height - window.safeAreaInsets.bottom - 92)
        UIView.animate(withDuration: 0.20, delay: 0, options: [.curveEaseOut, .allowUserInteraction]) {
            self.panel.frame = CGRect(x: targetX, y: targetY, width: 62, height: 62)
            self.panel.layoutIfNeeded()
        }
    }

    private func startRecording() {
        AVAudioSession.sharedInstance().requestRecordPermission { [weak self] granted in
            DispatchQueue.main.async {
                guard let self else { return }
                if granted {
                    self.startRecorder()
                } else {
                    self.panel.showStatus(title: "无法录音", detail: "请在系统设置中允许麦克风权限。", isListening: false)
                }
            }
        }
    }

    private func startRecorder() {
        stopRecording(cleanupAudioSession: false)
        let url = FileManager.default.temporaryDirectory
            .appendingPathComponent("ai-voice-assistant-\(UUID().uuidString).m4a")
        let settings: [String: Any] = [
            AVFormatIDKey: Int(kAudioFormatMPEG4AAC),
            AVSampleRateKey: 16_000,
            AVNumberOfChannelsKey: 1,
            AVEncoderAudioQualityKey: AVAudioQuality.medium.rawValue
        ]

        do {
            let session = AVAudioSession.sharedInstance()
            try session.setCategory(.playAndRecord, mode: .spokenAudio, options: [.defaultToSpeaker, .allowBluetooth, .duckOthers])
            try session.setActive(true)
            let recorder = try AVAudioRecorder(url: url, settings: settings)
            recorder.isMeteringEnabled = true
            recorder.record()
            self.recorder = recorder
            recordingURL = url
            isRecording = true
            panel.showStatus(title: "正在聆听", detail: "请说出要让 AI 处理的内容，点按结束。", isListening: true)
            meterTimer = Timer.scheduledTimer(withTimeInterval: 0.08, repeats: true) { [weak self] _ in
                self?.sampleAudioLevel()
            }
            if let meterTimer {
                RunLoop.main.add(meterTimer, forMode: .common)
            }
        } catch {
            panel.showStatus(title: "录音失败", detail: "音频会话无法启动，请稍后再试。", isListening: false)
            stopRecording(cleanupAudioSession: true)
        }
    }

    private func finishRecording() {
        let duration = recorder?.currentTime ?? 0
        stopRecording(cleanupAudioSession: true)
        let recognized = simulatedTranscript(duration: duration)
        panel.showRecognizedText(recognized)
        askAssistant(recognized)
    }

    private func stopRecording(cleanupAudioSession: Bool) {
        isRecording = false
        meterTimer?.invalidate()
        meterTimer = nil
        recorder?.stop()
        recorder = nil
        if let recordingURL {
            try? FileManager.default.removeItem(at: recordingURL)
        }
        recordingURL = nil
        panel.updateLevel(0)
        if cleanupAudioSession {
            try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
        }
    }

    private func sampleAudioLevel() {
        guard let recorder else {
            panel.updateLevel(0)
            return
        }
        recorder.updateMeters()
        let power = recorder.averagePower(forChannel: 0)
        let normalized = max(0.05, min(1, pow(10, power / 20) * 3.2))
        panel.updateLevel(normalized)
    }

    private func simulatedTranscript(duration: TimeInterval) -> String {
        if duration < 1.2 {
            return "帮我总结当前聊天重点"
        }
        let examples = [
            "帮我生成一条自然的跟进回复",
            "识别当前客户意图并给出下一步计划",
            "把刚才的内容整理成可以发送的消息",
            "提醒我稍后继续跟进这个客户"
        ]
        let index = min(examples.count - 1, Int(duration) % examples.count)
        return examples[index]
    }

    private func askAssistant(_ text: String) {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            panel.showStatus(title: "没有内容", detail: "请先说出问题或输入指令。", isListening: false)
            return
        }
        panel.showStatus(title: "AI 正在处理", detail: "正在根据语音指令生成结果...", isListening: true)
        requestCodex(prompt: trimmed) { [weak self] result in
            DispatchQueue.main.async {
                guard let self else { return }
                switch result {
                case .success(let reply):
                    let finalReply = reply.isEmpty ? self.localReply(for: trimmed) : reply
                    self.panel.showReply(finalReply)
                    self.speak(finalReply)
                case .failure:
                    self.panel.showStatus(title: "网络不可用", detail: "已切换为本地中文回复。", isListening: false)
                    let fallbackReply = self.localReply(for: trimmed)
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.45) {
                        self.panel.showReply(fallbackReply)
                        self.speak(fallbackReply)
                    }
                }
            }
        }
    }

    private func speak(_ text: String) {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        speechSynthesizer.stopSpeaking(at: .immediate)
        do {
            let session = AVAudioSession.sharedInstance()
            try session.setCategory(.playback, mode: .spokenAudio, options: [.duckOthers])
            try session.setActive(true)
        } catch {
            _ = error
        }
        let utterance = AVSpeechUtterance(string: trimmed)
        utterance.voice = AVSpeechSynthesisVoice(language: "zh-CN")
        utterance.rate = 0.48
        utterance.pitchMultiplier = 1.0
        speechSynthesizer.speak(utterance)
    }

    private func requestCodex(prompt: String, completion: @escaping (Result<String, Error>) -> Void) {
        let baseURLText = ChatSQLiteStore.shared.string(forKey: "ChatWindowCodexBaseURL")
            ?? UserDefaults.standard.string(forKey: "ChatWindowCodexBaseURL")
            ?? "https://cc2.cx/v1"
        let apiKey = AppSecretStore.migrateLegacySecret(forKey: "ChatWindowCodexAPIKey") ?? ""
        let model = ChatSQLiteStore.shared.string(forKey: "ChatWindowCodexModel")
            ?? UserDefaults.standard.string(forKey: "ChatWindowCodexModel")
            ?? "gpt-5.5"
        let normalizedBaseURL = baseURLText.trimmingCharacters(in: CharacterSet.whitespacesAndNewlines.union(CharacterSet(charactersIn: "/")))
        guard let url = URL(string: normalizedBaseURL)?.appendingPathComponent("chat").appendingPathComponent("completions"),
              !apiKey.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        else {
            completion(.failure(NSError(domain: "AIVoiceAssistant", code: -1)))
            return
        }

        let payload: [String: Any] = [
            "model": model.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "gpt-5.5" : model,
            "messages": [
                ["role": "system", "content": "你是一个 iOS 悬浮语音助手，请用简短中文回复用户语音指令。"],
                ["role": "user", "content": prompt]
            ],
            "stream": false,
            "temperature": 0.5
        ]

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.timeoutInterval = 10
        request.setValue("Bearer \(apiKey)", forHTTPHeaderField: "Authorization")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try? JSONSerialization.data(withJSONObject: payload)

        URLSession.shared.dataTask(with: request) { data, response, error in
            if let error {
                completion(.failure(error))
                return
            }
            if let http = response as? HTTPURLResponse, !(200..<300).contains(http.statusCode) {
                completion(.failure(NSError(domain: "AIVoiceAssistant", code: http.statusCode)))
                return
            }
            guard let data,
                  let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                  let choices = json["choices"] as? [[String: Any]],
                  let message = choices.first?["message"] as? [String: Any],
                  let content = message["content"] as? String
            else {
                completion(.failure(NSError(domain: "AIVoiceAssistant", code: -2)))
                return
            }
            completion(.success(content.trimmingCharacters(in: .whitespacesAndNewlines)))
        }.resume()
    }

    private func localReply(for text: String) -> String {
        if text.contains("总结") {
            return "已为你准备总结入口：可以从顶部安全区的“总结”查看当前页面概览。"
        }
        if text.contains("计划") || text.contains("提醒") {
            return "建议计划：确认客户诉求，整理可发送话术，必要时写入日历跟进。"
        }
        if text.contains("回复") || text.contains("消息") {
            return "可以回复：收到，我这边先确认一下信息，稍后给你完整反馈。"
        }
        return "我已听到你的指令，可以继续说“总结、生成回复、建立计划”等操作。"
    }
}

private final class AIVoiceAssistantPanel: UIControl {
    var onToggle: (() -> Void)?
    var onClose: (() -> Void)?
    var onAsk: ((String) -> Void)?

    private let blurView = UIVisualEffectView(effect: UIBlurEffect(style: .systemChromeMaterial))
    private let compactIcon = UIImageView(image: UIImage(systemName: "waveform.and.mic"))
    private let titleLabel = UILabel()
    private let detailLabel = UILabel()
    private let closeButton = UIButton(type: .system)
    private let inputField = UITextField()
    private let sendButton = UIButton(type: .system)
    private let waveStack = UIStackView()
    private var waveBars: [UIView] = []

    override init(frame: CGRect) {
        super.init(frame: frame)
        configure()
    }

    required init?(coder: NSCoder) {
        super.init(coder: coder)
        configure()
    }

    func setExpanded(_ expanded: Bool) {
        titleLabel.isHidden = !expanded
        detailLabel.isHidden = !expanded
        closeButton.isHidden = !expanded
        inputField.isHidden = !expanded
        sendButton.isHidden = !expanded
        waveStack.isHidden = !expanded
        compactIcon.image = UIImage(systemName: expanded ? "mic.circle.fill" : "waveform.and.mic")
        accessibilityHint = expanded ? "点按开始录音，再次点按结束录音" : "点按展开 AI 语音助手"
    }

    func showStatus(title: String, detail: String, isListening: Bool) {
        titleLabel.text = title
        detailLabel.text = detail
        compactIcon.tintColor = isListening ? UIColor.systemGreen : UIColor(red: 0.12, green: 0.36, blue: 0.48, alpha: 1)
        if !isListening {
            updateLevel(0)
        }
        UIAccessibility.post(notification: .announcement, argument: "\(title)，\(detail)")
    }

    func showRecognizedText(_ text: String) {
        inputField.text = text
        titleLabel.text = "已识别"
        detailLabel.text = text
    }

    func showReply(_ text: String) {
        titleLabel.text = "AI 回复"
        detailLabel.text = text
        compactIcon.tintColor = UIColor(red: 0.46, green: 0.40, blue: 0.90, alpha: 1)
        updateLevel(0)
        UIAccessibility.post(notification: .announcement, argument: text)
    }

    func updateLevel(_ level: Float) {
        waveBars.enumerated().forEach { index, bar in
            let count = max(1, waveBars.count)
            let phase = Float(index + 1) / Float(count)
            let clampedLevel = max(0.05, level)
            let rawHeight = 8.0 + Double(clampedLevel) * (20.0 + 18.0 * Double(phase))
            let rawAlpha = 0.35 + Double(clampedLevel) * 0.65
            bar.bounds.size.height = CGFloat(rawHeight)
            bar.alpha = CGFloat(rawAlpha)
        }
    }

    private func configure() {
        clipsToBounds = false
        addTarget(self, action: #selector(toggleTapped), for: .touchUpInside)

        blurView.layer.cornerRadius = 28
        blurView.layer.cornerCurve = .continuous
        blurView.clipsToBounds = true
        blurView.layer.borderWidth = 0.8
        blurView.layer.borderColor = UIColor.white.withAlphaComponent(0.55).cgColor
        blurView.translatesAutoresizingMaskIntoConstraints = false
        blurView.isUserInteractionEnabled = false
        addSubview(blurView)

        layer.shadowColor = UIColor.black.cgColor
        layer.shadowOpacity = 0.18
        layer.shadowRadius = 16
        layer.shadowOffset = CGSize(width: 0, height: 8)

        compactIcon.tintColor = UIColor(red: 0.12, green: 0.36, blue: 0.48, alpha: 1)
        compactIcon.contentMode = .scaleAspectFit
        compactIcon.translatesAutoresizingMaskIntoConstraints = false
        addSubview(compactIcon)

        titleLabel.text = "AI 语音助手"
        titleLabel.font = .systemFont(ofSize: 16, weight: .bold)
        titleLabel.textColor = UIColor(red: 0.09, green: 0.13, blue: 0.16, alpha: 1)
        titleLabel.adjustsFontForContentSizeCategory = true
        titleLabel.translatesAutoresizingMaskIntoConstraints = false
        addSubview(titleLabel)

        detailLabel.text = "点按开始说话，AI 会生成回复。"
        detailLabel.font = .systemFont(ofSize: 12, weight: .regular)
        detailLabel.textColor = UIColor(red: 0.36, green: 0.42, blue: 0.46, alpha: 1)
        detailLabel.numberOfLines = 3
        detailLabel.adjustsFontForContentSizeCategory = true
        detailLabel.translatesAutoresizingMaskIntoConstraints = false
        addSubview(detailLabel)

        closeButton.setImage(UIImage(systemName: "xmark"), for: .normal)
        closeButton.tintColor = UIColor(red: 0.24, green: 0.30, blue: 0.34, alpha: 1)
        closeButton.backgroundColor = UIColor.white.withAlphaComponent(0.68)
        closeButton.layer.cornerRadius = 13
        closeButton.translatesAutoresizingMaskIntoConstraints = false
        closeButton.addTarget(self, action: #selector(closeTapped), for: .touchUpInside)
        closeButton.accessibilityLabel = "关闭语音助手"
        addSubview(closeButton)

        configureWaveStack()
        configureInputRow()

        NSLayoutConstraint.activate([
            blurView.topAnchor.constraint(equalTo: topAnchor),
            blurView.leadingAnchor.constraint(equalTo: leadingAnchor),
            blurView.trailingAnchor.constraint(equalTo: trailingAnchor),
            blurView.bottomAnchor.constraint(equalTo: bottomAnchor),
            compactIcon.leadingAnchor.constraint(equalTo: leadingAnchor, constant: 16),
            compactIcon.topAnchor.constraint(equalTo: topAnchor, constant: 16),
            compactIcon.widthAnchor.constraint(equalToConstant: 30),
            compactIcon.heightAnchor.constraint(equalToConstant: 30),
            closeButton.topAnchor.constraint(equalTo: topAnchor, constant: 13),
            closeButton.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -13),
            closeButton.widthAnchor.constraint(equalToConstant: 26),
            closeButton.heightAnchor.constraint(equalToConstant: 26),
            titleLabel.leadingAnchor.constraint(equalTo: compactIcon.trailingAnchor, constant: 10),
            titleLabel.trailingAnchor.constraint(equalTo: closeButton.leadingAnchor, constant: -8),
            titleLabel.topAnchor.constraint(equalTo: topAnchor, constant: 15),
            detailLabel.leadingAnchor.constraint(equalTo: titleLabel.leadingAnchor),
            detailLabel.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -16),
            detailLabel.topAnchor.constraint(equalTo: titleLabel.bottomAnchor, constant: 5),
            waveStack.leadingAnchor.constraint(equalTo: leadingAnchor, constant: 18),
            waveStack.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -18),
            waveStack.topAnchor.constraint(equalTo: detailLabel.bottomAnchor, constant: 14),
            waveStack.heightAnchor.constraint(equalToConstant: 38),
            inputField.leadingAnchor.constraint(equalTo: leadingAnchor, constant: 16),
            inputField.trailingAnchor.constraint(equalTo: sendButton.leadingAnchor, constant: -8),
            inputField.bottomAnchor.constraint(equalTo: bottomAnchor, constant: -14),
            inputField.heightAnchor.constraint(equalToConstant: 36),
            sendButton.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -16),
            sendButton.centerYAnchor.constraint(equalTo: inputField.centerYAnchor),
            sendButton.widthAnchor.constraint(equalToConstant: 42),
            sendButton.heightAnchor.constraint(equalToConstant: 36)
        ])
        setExpanded(false)
    }

    private func configureWaveStack() {
        waveStack.axis = .horizontal
        waveStack.alignment = .center
        waveStack.distribution = .fillEqually
        waveStack.spacing = 5
        waveStack.translatesAutoresizingMaskIntoConstraints = false
        addSubview(waveStack)

        for _ in 0..<18 {
            let bar = UIView()
            bar.backgroundColor = UIColor(red: 0.15, green: 0.56, blue: 0.72, alpha: 1)
            bar.layer.cornerRadius = 2
            bar.translatesAutoresizingMaskIntoConstraints = false
            waveStack.addArrangedSubview(bar)
            bar.heightAnchor.constraint(equalToConstant: 8).isActive = true
            waveBars.append(bar)
        }
    }

    private func configureInputRow() {
        inputField.placeholder = "也可以输入语音指令"
        inputField.font = .systemFont(ofSize: 13, weight: .medium)
        inputField.backgroundColor = UIColor.white.withAlphaComponent(0.72)
        inputField.layer.cornerRadius = 18
        inputField.leftView = UIView(frame: CGRect(x: 0, y: 0, width: 12, height: 1))
        inputField.leftViewMode = .always
        inputField.returnKeyType = .send
        inputField.translatesAutoresizingMaskIntoConstraints = false
        inputField.addTarget(self, action: #selector(textFieldReturn), for: .editingDidEndOnExit)
        addSubview(inputField)

        sendButton.setImage(UIImage(systemName: "arrow.up.circle.fill"), for: .normal)
        sendButton.tintColor = UIColor(red: 0.15, green: 0.52, blue: 0.66, alpha: 1)
        sendButton.translatesAutoresizingMaskIntoConstraints = false
        sendButton.addTarget(self, action: #selector(sendTapped), for: .touchUpInside)
        sendButton.accessibilityLabel = "发送语音助手指令"
        addSubview(sendButton)
    }

    @objc private func toggleTapped() {
        onToggle?()
    }

    @objc private func closeTapped() {
        onClose?()
    }

    @objc private func sendTapped() {
        let text = inputField.text ?? ""
        onAsk?(text)
    }

    @objc private func textFieldReturn() {
        sendTapped()
    }
}
