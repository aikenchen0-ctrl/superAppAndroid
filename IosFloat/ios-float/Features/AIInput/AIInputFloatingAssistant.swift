import UIKit

final class AIInputFloatingAssistant: NSObject {
    static let shared = AIInputFloatingAssistant()
    static let allowedTextFieldIdentifier = "chat.main.message.input"

    private enum Mode: CaseIterable {
        case rewrite
        case polish
        case continueWriting

        var title: String {
            switch self {
            case .rewrite: return "AI重写"
            case .polish: return "AI润色"
            case .continueWriting: return "AI续写"
            }
        }

        func prompt(text: String, context: String) -> String {
            switch self {
            case .rewrite:
                return "请重写下面输入框里的内容，保留核心意思，适合直接填回输入框。只返回正文：\n\(text)"
            case .polish:
                return "请润色下面输入框里的内容，让它更自然、清楚、礼貌，适合直接填回输入框。只返回正文：\n\(text)"
            case .continueWriting:
                return """
                请基于下面输入框当前内容，从光标位置继续写后续内容，语气自然，简洁可直接使用。
                光标前内容：
                \(context)
                需要续写的参考内容：
                \(text)
                只返回续写内容，不要重复光标前已有文字。
                """
            }
        }
    }

    private struct Configuration {
        let baseURLText: String
        let apiKey: String
        let model: String

        var endpoint: URL? {
            guard var components = URLComponents(string: baseURLText.trimmingCharacters(in: .whitespacesAndNewlines)) else { return nil }
            if components.scheme == nil {
                components.scheme = "https"
            }
            guard let baseURL = components.url else { return nil }
            return baseURL.appendingPathComponent("chat").appendingPathComponent("completions")
        }
    }

    private weak var activeTextField: UITextField?
    private weak var activeTextView: UITextView?
    private weak var hostWindow: UIWindow?
    private let floatingView = UIVisualEffectView(effect: UIBlurEffect(style: .systemThinMaterialDark))
    private let stackView = UIStackView()
    private var installed = false
    private var generationID: UUID?
    private var targetStartOffset = 0
    private var targetEndOffset = 0
    private var currentGeneratedText = ""
    private var caretTrackingTimer: Timer?
    private var generationTimeoutWorkItem: DispatchWorkItem?

    private override init() {
        super.init()
        configureFloatingView()
    }

    func install() {
        guard !installed else { return }
        installed = true
        let center = NotificationCenter.default
        center.addObserver(self, selector: #selector(textFieldDidBegin(_:)), name: UITextField.textDidBeginEditingNotification, object: nil)
        center.addObserver(self, selector: #selector(textFieldDidEnd(_:)), name: UITextField.textDidEndEditingNotification, object: nil)
        center.addObserver(self, selector: #selector(textInputDidChange(_:)), name: UITextField.textDidChangeNotification, object: nil)
        center.addObserver(self, selector: #selector(textViewDidBegin(_:)), name: UITextView.textDidBeginEditingNotification, object: nil)
        center.addObserver(self, selector: #selector(textViewDidEnd(_:)), name: UITextView.textDidEndEditingNotification, object: nil)
        center.addObserver(self, selector: #selector(textInputDidChange(_:)), name: UITextView.textDidChangeNotification, object: nil)
        center.addObserver(self, selector: #selector(keyboardFrameChanged), name: UIResponder.keyboardWillChangeFrameNotification, object: nil)
    }

    private func configureFloatingView() {
        floatingView.alpha = 0
        floatingView.layer.cornerRadius = 16
        floatingView.layer.cornerCurve = .continuous
        floatingView.clipsToBounds = true
        floatingView.layer.borderWidth = 0.5
        floatingView.layer.borderColor = UIColor.white.withAlphaComponent(0.18).cgColor
        floatingView.translatesAutoresizingMaskIntoConstraints = true
        floatingView.frame = CGRect(x: 0, y: 0, width: 206, height: 34)

        stackView.axis = .horizontal
        stackView.alignment = .fill
        stackView.distribution = .fillEqually
        stackView.spacing = 0
        stackView.translatesAutoresizingMaskIntoConstraints = false
        floatingView.contentView.addSubview(stackView)
        NSLayoutConstraint.activate([
            stackView.topAnchor.constraint(equalTo: floatingView.contentView.topAnchor),
            stackView.leadingAnchor.constraint(equalTo: floatingView.contentView.leadingAnchor),
            stackView.trailingAnchor.constraint(equalTo: floatingView.contentView.trailingAnchor),
            stackView.bottomAnchor.constraint(equalTo: floatingView.contentView.bottomAnchor)
        ])

        Mode.allCases.forEach { mode in
            let button = UIButton(type: .system)
            button.setTitle(mode.title, for: .normal)
            button.setTitleColor(.white, for: .normal)
            button.titleLabel?.font = .systemFont(ofSize: 12, weight: .semibold)
            button.tag = modeTag(mode)
            button.addTarget(self, action: #selector(actionTapped(_:)), for: .touchUpInside)
            stackView.addArrangedSubview(button)
        }
    }

    @objc private func textFieldDidBegin(_ notification: Notification) {
        guard let textField = notification.object as? UITextField,
              !textField.isSecureTextEntry,
              textField.isEnabled,
              textField.window != nil,
              !shouldIgnoreInput(textField)
        else { return }
        activeTextView = nil
        activeTextField = textField
        showFloatingView(for: textField)
    }

    @objc private func textFieldDidEnd(_ notification: Notification) {
        guard notification.object as? UITextField === activeTextField else { return }
        hideFloatingView()
        activeTextField = nil
    }

    @objc private func textViewDidBegin(_ notification: Notification) {
        guard let textView = notification.object as? UITextView,
              textView.isEditable,
              textView.window != nil,
              !shouldIgnoreInput(textView)
        else { return }
        activeTextField = nil
        activeTextView = textView
        showFloatingView(for: textView)
    }

    @objc private func textViewDidEnd(_ notification: Notification) {
        guard notification.object as? UITextView === activeTextView else { return }
        hideFloatingView()
        activeTextView = nil
    }

    @objc private func textInputDidChange(_ notification: Notification) {
        guard isActiveInput(notification.object) else { return }
        if generationID != nil {
            updateFloatingPosition()
            return
        }
        generationID = nil
        updateFloatingPosition()
    }

    @objc private func textInputSelectionDidChange(_ notification: Notification) {
        guard isActiveInput(notification.object) else { return }
        updateFloatingPosition()
    }

    @objc private func keyboardFrameChanged() {
        DispatchQueue.main.async { [weak self] in
            self?.updateFloatingPosition()
        }
    }

    @objc private func actionTapped(_ sender: UIButton) {
        guard let mode = mode(for: sender.tag) else { return }
        generate(mode: mode)
    }

    private func showFloatingView(for view: UIView) {
        guard let window = view.window else { return }
        hostWindow = window
        if floatingView.superview !== window {
            floatingView.removeFromSuperview()
            window.addSubview(floatingView)
        }
        startCaretTracking()
        setButtonsEnabled(true, titleOverride: nil)
        updateFloatingPosition()
        UIView.animate(withDuration: 0.16) {
            self.floatingView.alpha = 1
        }
    }

    private func shouldIgnoreInput(_ view: UIView) -> Bool {
        if let textField = view as? UITextField {
            return textField.accessibilityIdentifier != Self.allowedTextFieldIdentifier
        }
        if view is UITextView {
            return true
        }
        if view.firstSuperview(of: UISearchBar.self) != nil {
            return true
        }
        if #available(iOS 13.0, *), view is UISearchTextField {
            return true
        }
        return false
    }

    private func hideFloatingView() {
        generationID = nil
        generationTimeoutWorkItem?.cancel()
        generationTimeoutWorkItem = nil
        setButtonsEnabled(true, titleOverride: nil)
        stopCaretTracking()
        UIView.animate(withDuration: 0.12, animations: {
            self.floatingView.alpha = 0
        }, completion: { _ in
            self.floatingView.removeFromSuperview()
        })
    }

    private func startCaretTracking() {
        caretTrackingTimer?.invalidate()
        caretTrackingTimer = Timer.scheduledTimer(withTimeInterval: 0.18, repeats: true) { [weak self] _ in
            self?.updateFloatingPosition()
        }
        if let caretTrackingTimer {
            RunLoop.main.add(caretTrackingTimer, forMode: .common)
        }
    }

    private func stopCaretTracking() {
        caretTrackingTimer?.invalidate()
        caretTrackingTimer = nil
    }

    private func updateFloatingPosition() {
        guard floatingView.superview != nil,
              let window = hostWindow,
              let inputView = activeInputView(),
              inputView.window === window
        else { return }

        let caretRect = currentCaretRect(in: inputView)
        let targetX = min(max(caretRect.midX - floatingView.bounds.width / 2, 8), window.bounds.width - floatingView.bounds.width - 8)
        var targetY = caretRect.minY - floatingView.bounds.height - 8
        if targetY < window.safeAreaInsets.top + 4 {
            targetY = caretRect.maxY + 8
        }
        floatingView.frame = CGRect(
            x: targetX,
            y: targetY,
            width: floatingView.bounds.width,
            height: floatingView.bounds.height
        )
        window.bringSubviewToFront(floatingView)
    }

    private func currentCaretRect(in inputView: UIView) -> CGRect {
        if let textField = inputView as? UITextField,
           let range = textField.selectedTextRange {
            return textField.convert(textField.caretRect(for: range.end), to: hostWindow)
        }
        if let textView = inputView as? UITextView,
           let range = textView.selectedTextRange {
            return textView.convert(textView.caretRect(for: range.end), to: hostWindow)
        }
        return inputView.convert(inputView.bounds, to: hostWindow)
    }

    private func generate(mode: Mode) {
        guard generationID == nil else { return }
        guard let snapshot = currentInputSnapshot(for: mode) else { return }
        let configuration = currentConfiguration()
        guard configuration.endpoint != nil,
              !configuration.apiKey.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        else {
            pulseFloatingTitle("先配置AI")
            return
        }

        let id = UUID()
        generationID = id
        targetStartOffset = snapshot.startOffset
        targetEndOffset = snapshot.endOffset
        currentGeneratedText = ""
        setButtonsEnabled(false, titleOverride: "生成中...")
        let prompt = mode.prompt(text: snapshot.targetText, context: snapshot.contextText)
        scheduleGenerationTimeout(id: id)

        streamChatCompletion(prompt: prompt, configuration: configuration) { [weak self] delta in
            DispatchQueue.main.async {
                guard let self, self.generationID == id else { return }
                self.scheduleGenerationTimeout(id: id)
                self.currentGeneratedText += delta
                self.replaceActiveText(start: self.targetStartOffset, end: self.targetEndOffset, replacement: self.currentGeneratedText)
                self.targetEndOffset = self.targetStartOffset + self.currentGeneratedText.count
                self.updateFloatingPosition()
            }
        } completion: { [weak self] result in
            DispatchQueue.main.async {
                guard let self else { return }
                if self.generationID != id {
                    if self.generationID == nil {
                        self.generationTimeoutWorkItem?.cancel()
                        self.generationTimeoutWorkItem = nil
                        self.setButtonsEnabled(true, titleOverride: nil)
                    }
                    return
                }
                self.generationTimeoutWorkItem?.cancel()
                self.generationTimeoutWorkItem = nil
                if case .failure = result, self.currentGeneratedText.isEmpty {
                    self.pulseFloatingTitle("生成失败")
                } else {
                    self.setButtonsEnabled(true, titleOverride: nil)
                }
                self.generationID = nil
            }
        }
    }

    private func scheduleGenerationTimeout(id: UUID) {
        generationTimeoutWorkItem?.cancel()
        let item = DispatchWorkItem { [weak self] in
            guard let self, self.generationID == id else { return }
            self.generationID = nil
            self.generationTimeoutWorkItem = nil
            if self.currentGeneratedText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                self.pulseFloatingTitle("生成超时")
            } else {
                self.setButtonsEnabled(true, titleOverride: nil)
            }
        }
        generationTimeoutWorkItem = item
        DispatchQueue.main.asyncAfter(deadline: .now() + 12, execute: item)
    }

    private func currentInputSnapshot(for mode: Mode) -> (targetText: String, contextText: String, startOffset: Int, endOffset: Int)? {
        let text = activeText()
        let selection = activeSelectionOffsets()
        let selectedText = substring(text, start: selection.start, end: selection.end)
        switch mode {
        case .rewrite, .polish:
            let target = selectedText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? text : selectedText
            let start = selectedText.isEmpty ? 0 : selection.start
            let end = selectedText.isEmpty ? text.count : selection.end
            guard !target.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
                pulseFloatingTitle("先输入内容")
                return nil
            }
            return (target, text, start, end)
        case .continueWriting:
            let beforeCursor = substring(text, start: 0, end: selection.end)
            let target = text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "生成一段适合当前输入框的简短内容。" : text
            return (target, beforeCursor, selection.end, selection.end)
        }
    }

    private func replaceActiveText(start: Int, end: Int, replacement: String) {
        if let textField = activeTextField,
           let range = textRange(in: textField, start: start, end: end) {
            textField.replace(range, withText: replacement)
            setSelection(in: textField, offset: start + replacement.count)
            textField.sendActions(for: .editingChanged)
        } else if let textView = activeTextView,
                  let range = textRange(in: textView, start: start, end: end) {
            textView.replace(range, withText: replacement)
            setSelection(in: textView, offset: start + replacement.count)
            NotificationCenter.default.post(name: UITextView.textDidChangeNotification, object: textView)
        }
    }

    private func streamChatCompletion(
        prompt: String,
        configuration: Configuration,
        onDelta: @escaping (String) -> Void,
        completion: @escaping (Result<Void, Error>) -> Void
    ) {
        guard let url = configuration.endpoint else {
            completion(.failure(NSError(domain: "AIInputFloatingAssistant", code: -1)))
            return
        }
        let payload: [String: Any] = [
            "model": configuration.model,
            "messages": [
                [
                    "role": "system",
                    "content": "你是输入框里的 AI 写作助手，只返回可直接放入输入框的正文，不要解释。"
                ],
                [
                    "role": "user",
                    "content": prompt
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
                    throw NSError(domain: "AIInputFloatingAssistant", code: httpResponse.statusCode)
                }
                for try await line in bytes.lines {
                    guard line.hasPrefix("data:") else { continue }
                    let payload = line.dropFirst(5).trimmingCharacters(in: .whitespacesAndNewlines)
                    if payload == "[DONE]" {
                        completion(.success(()))
                        return
                    }
                    guard let data = payload.data(using: .utf8),
                          let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                          let choices = json["choices"] as? [[String: Any]],
                          let firstChoice = choices.first
                    else { continue }
                    if let delta = firstChoice["delta"] as? [String: Any],
                       let content = delta["content"] as? String {
                        onDelta(content)
                    } else if let message = firstChoice["message"] as? [String: Any],
                              let content = message["content"] as? String {
                        onDelta(content)
                    }
                }
                completion(.success(()))
            } catch {
                completion(.failure(error))
            }
        }
    }

    private func currentConfiguration() -> Configuration {
        let baseURLKey = "ChatWindowCodexBaseURL"
        let apiKeyKey = "ChatWindowCodexAPIKey"
        let modelKey = "ChatWindowCodexModel"
        let baseURLText = ChatSQLiteStore.shared.string(forKey: baseURLKey)
            ?? UserDefaults.standard.string(forKey: baseURLKey)
            ?? "https://cc2.cx/v1"
        let apiKey = AppSecretStore.migrateLegacySecret(forKey: apiKeyKey) ?? ""
        let model = ChatSQLiteStore.shared.string(forKey: modelKey)
            ?? UserDefaults.standard.string(forKey: modelKey)
            ?? "gpt-5.5"
        return Configuration(
            baseURLText: normalizeBaseURLText(baseURLText),
            apiKey: apiKey.trimmingCharacters(in: .whitespacesAndNewlines),
            model: model.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "gpt-5.5" : model.trimmingCharacters(in: .whitespacesAndNewlines)
        )
    }

    private func normalizeBaseURLText(_ text: String) -> String {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return "https://cc2.cx/v1" }
        guard !trimmed.hasSuffix("/chat/completions") else {
            return String(trimmed.dropLast("/chat/completions".count)).trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        }
        return trimmed.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
    }

    private func activeInputView() -> UIView? {
        activeTextField ?? activeTextView
    }

    private func activeText() -> String {
        activeTextField?.text ?? activeTextView?.text ?? ""
    }

    private func activeSelectionOffsets() -> (start: Int, end: Int) {
        if let textField = activeTextField,
           let range = textField.selectedTextRange {
            return (
                textField.offset(from: textField.beginningOfDocument, to: range.start),
                textField.offset(from: textField.beginningOfDocument, to: range.end)
            )
        }
        if let textView = activeTextView,
           let range = textView.selectedTextRange {
            return (
                textView.offset(from: textView.beginningOfDocument, to: range.start),
                textView.offset(from: textView.beginningOfDocument, to: range.end)
            )
        }
        let count = activeText().count
        return (count, count)
    }

    private func textRange(in textField: UITextField, start: Int, end: Int) -> UITextRange? {
        guard let from = textField.position(from: textField.beginningOfDocument, offset: start),
              let to = textField.position(from: textField.beginningOfDocument, offset: end)
        else { return nil }
        return textField.textRange(from: from, to: to)
    }

    private func textRange(in textView: UITextView, start: Int, end: Int) -> UITextRange? {
        guard let from = textView.position(from: textView.beginningOfDocument, offset: start),
              let to = textView.position(from: textView.beginningOfDocument, offset: end)
        else { return nil }
        return textView.textRange(from: from, to: to)
    }

    private func setSelection(in textField: UITextField, offset: Int) {
        guard let position = textField.position(from: textField.beginningOfDocument, offset: offset) else { return }
        textField.selectedTextRange = textField.textRange(from: position, to: position)
    }

    private func setSelection(in textView: UITextView, offset: Int) {
        guard let position = textView.position(from: textView.beginningOfDocument, offset: offset) else { return }
        textView.selectedTextRange = textView.textRange(from: position, to: position)
    }

    private func substring(_ text: String, start: Int, end: Int) -> String {
        let safeStart = max(0, min(start, text.count))
        let safeEnd = max(safeStart, min(end, text.count))
        let startIndex = text.index(text.startIndex, offsetBy: safeStart)
        let endIndex = text.index(text.startIndex, offsetBy: safeEnd)
        return String(text[startIndex..<endIndex])
    }

    private func isActiveInput(_ object: Any?) -> Bool {
        if let textField = object as? UITextField {
            return textField === activeTextField
        }
        if let textView = object as? UITextView {
            return textView === activeTextView
        }
        return false
    }

    private func setButtonsEnabled(_ isEnabled: Bool, titleOverride: String?) {
        stackView.arrangedSubviews.enumerated().forEach { index, view in
            guard let button = view as? UIButton else { return }
            button.isEnabled = isEnabled
            button.alpha = isEnabled ? 1 : 0.65
            if let titleOverride {
                button.setTitle(index == 1 ? titleOverride : "", for: .normal)
            } else if let mode = mode(for: button.tag) {
                button.setTitle(mode.title, for: .normal)
            }
        }
    }

    private func pulseFloatingTitle(_ title: String) {
        setButtonsEnabled(false, titleOverride: title)
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.0) { [weak self] in
            guard self?.generationID == nil else { return }
            self?.setButtonsEnabled(true, titleOverride: nil)
        }
    }

    private func modeTag(_ mode: Mode) -> Int {
        switch mode {
        case .rewrite: return 1
        case .polish: return 2
        case .continueWriting: return 3
        }
    }

    private func mode(for tag: Int) -> Mode? {
        switch tag {
        case 1: return .rewrite
        case 2: return .polish
        case 3: return .continueWriting
        default: return nil
        }
    }
}

