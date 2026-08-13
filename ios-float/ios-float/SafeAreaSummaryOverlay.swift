import UIKit
import Vision

extension Notification.Name {
    static let safeAreaSnapshotDragDidUpdate = Notification.Name("safeAreaSnapshotDragDidUpdate")
}

enum SafeAreaSnapshotDragUserInfoKey {
    static let image = "image"
    static let title = "title"
    static let state = "state"
    static let screenPoint = "screenPoint"
}

final class SafeAreaSummaryOverlayCoordinator: NSObject, UIGestureRecognizerDelegate {
    private weak var window: UIWindow?
    private var panGesture: UIPanGestureRecognizer?
    private let promptView = SafeAreaSummaryPromptView()
    private var hideWorkItem: DispatchWorkItem?
    private var lastShownAt = Date.distantPast

    func install(on window: UIWindow) {
        guard self.window !== window else { return }
        self.window = window

        promptView.translatesAutoresizingMaskIntoConstraints = false
        promptView.alpha = 0
        promptView.isHidden = true
        promptView.onSummary = { [weak self] in self?.presentSummary() }
        promptView.onSearch = { [weak self] in self?.presentSearch() }
        promptView.onSnapshot = { [weak self] in self?.presentSnapshot() }
        promptView.onReport = { [weak self] in self?.presentQualityReport() }
        window.addSubview(promptView)

        NSLayoutConstraint.activate([
            promptView.topAnchor.constraint(equalTo: window.safeAreaLayoutGuide.topAnchor, constant: 6),
            promptView.centerXAnchor.constraint(equalTo: window.centerXAnchor),
            promptView.leadingAnchor.constraint(greaterThanOrEqualTo: window.leadingAnchor, constant: 14),
            promptView.trailingAnchor.constraint(lessThanOrEqualTo: window.trailingAnchor, constant: -14),
            promptView.heightAnchor.constraint(equalToConstant: 40)
        ])

        let panGesture = UIPanGestureRecognizer(target: self, action: #selector(handleGlobalPan(_:)))
        panGesture.cancelsTouchesInView = false
        panGesture.delaysTouchesBegan = false
        panGesture.delaysTouchesEnded = false
        panGesture.delegate = self
        window.addGestureRecognizer(panGesture)
        self.panGesture = panGesture
    }

    func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldReceive touch: UITouch) -> Bool {
        guard let touchedView = touch.view else { return true }
        return !touchedView.isDescendant(of: promptView)
    }

    func gestureRecognizer(
        _ gestureRecognizer: UIGestureRecognizer,
        shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer
    ) -> Bool {
        true
    }

    @objc private func handleGlobalPan(_ gesture: UIPanGestureRecognizer) {
        guard let hostView = gesture.view else { return }
        let translation = gesture.translation(in: hostView)
        let velocity = gesture.velocity(in: hostView)
        let verticalMovement = abs(translation.y)
        let horizontalMovement = abs(translation.x)

        switch gesture.state {
        case .began, .changed:
            let isVerticalScroll = verticalMovement > 24 && verticalMovement > horizontalMovement * 1.15
            guard isVerticalScroll else { return }
            let isIntentionalUpScroll = translation.y < -18 || velocity.y < -360
            if isIntentionalUpScroll {
                hidePrompt()
                return
            }
            let isIntentionalDownScroll = translation.y > 18 || velocity.y > 360
            guard isIntentionalDownScroll else { return }
            AppQualityBaseline.shared.recordScrollEvent()
            showPrompt()
        case .ended, .cancelled, .failed:
            scheduleHide(after: 3.2)
        default:
            break
        }
    }

    private func showPrompt() {
        guard let window else { return }
        window.bringSubviewToFront(promptView)

        hideWorkItem?.cancel()
        let now = Date()
        let shouldAnimate = now.timeIntervalSince(lastShownAt) > 0.8 || promptView.isHidden
        lastShownAt = now

        promptView.isHidden = false
        if shouldAnimate {
            promptView.transform = CGAffineTransform(translationX: 0, y: -10).scaledBy(x: 0.98, y: 0.98)
            UIView.animate(
                withDuration: 0.2,
                delay: 0,
                options: [.curveEaseOut, .allowUserInteraction],
                animations: {
                    self.promptView.alpha = 1
                    self.promptView.transform = .identity
                }
            )
        } else {
            promptView.alpha = 1
        }

        scheduleHide(after: 4.0)
    }

    private func scheduleHide(after delay: TimeInterval) {
        hideWorkItem?.cancel()
        let item = DispatchWorkItem { [weak self] in
            self?.hidePrompt()
        }
        hideWorkItem = item
        DispatchQueue.main.asyncAfter(deadline: .now() + delay, execute: item)
    }

    private func hidePrompt() {
        hideWorkItem?.cancel()
        hideWorkItem = nil
        guard !promptView.isHidden || promptView.alpha > 0 else { return }
        UIView.animate(
            withDuration: 0.18,
            delay: 0,
            options: [.curveEaseIn, .allowUserInteraction],
            animations: {
                self.promptView.alpha = 0
                self.promptView.transform = CGAffineTransform(translationX: 0, y: -8)
            },
            completion: { _ in
                self.promptView.isHidden = true
                self.promptView.transform = .identity
            }
        )
    }

    private func presentSummary() {
        guard let window, let presenter = topViewController() else { return }
        presentInsightPanel(window: window, presenter: presenter, mode: .summary)
    }

    private func presentInsightPanel(
        window: UIWindow,
        presenter: UIViewController,
        mode: SafeAreaInsightPanelViewController.Mode
    ) {
        let panel = SafeAreaInsightPanelViewController(initialMode: mode)
        panel.modalPresentationStyle = .pageSheet
        if let sheet = panel.sheetPresentationController {
            sheet.detents = [.medium(), .large()]
            sheet.prefersGrabberVisible = true
        }
        presenter.present(panel, animated: true)

        SafeAreaPageInsightService.analyze(window: window, presenter: presenter) { [weak panel] insight in
            panel?.apply(insight: insight)
        }
    }

    private func presentSearch() {
        guard let window, let presenter = topViewController() else { return }
        presentInsightPanel(window: window, presenter: presenter, mode: .search)
    }

    private func presentSnapshot() {
        guard let window, let presenter = topViewController() else { return }
        let renderer = UIGraphicsImageRenderer(bounds: window.bounds)
        let image = renderer.image { _ in
            window.drawHierarchy(in: window.bounds, afterScreenUpdates: false)
        }
        let preview = PageSnapshotPreviewViewController(image: image)
        preview.modalPresentationStyle = .fullScreen
        presenter.present(preview, animated: true)
    }

    private func presentQualityReport() {
        guard let presenter = topViewController() else { return }
        let report = AppQualityReportViewController()
        report.modalPresentationStyle = .fullScreen
        presenter.present(report, animated: true)
    }

    private func topViewController() -> UIViewController? {
        guard let root = window?.rootViewController else { return nil }
        return root.topMostPresentedViewController()
    }

    private func visibleScrollViews(in view: UIView) -> [UIScrollView] {
        var result: [UIScrollView] = []
        if let scrollView = view as? UIScrollView, scrollView.window != nil {
            result.append(scrollView)
        }
        view.subviews.forEach { result.append(contentsOf: visibleScrollViews(in: $0)) }
        return result
    }

    private func scrollProgressText(for scrollView: UIScrollView) -> String {
        let adjustedTop = scrollView.adjustedContentInset.top
        let adjustedBottom = scrollView.adjustedContentInset.bottom
        let visibleHeight = max(1, scrollView.bounds.height - adjustedTop - adjustedBottom)
        let scrollableHeight = max(1, scrollView.contentSize.height - visibleHeight)
        let offset = min(max(0, scrollView.contentOffset.y + adjustedTop), scrollableHeight)
        let percent = Int((offset / scrollableHeight * 100).rounded())
        return "滚动进度：\(percent)%"
    }
}

private struct SafeAreaPageInsight {
    let pageTitle: String
    let visibleTexts: [String]
    let ocrTexts: [String]
    let scrollProgresses: [String]

    var mergedTexts: [String] {
        uniqueLines(visibleTexts + ocrTexts)
            .filter { !Self.isLowValueLine($0) }
    }

    var keyHighlights: [String] {
        let lines = mergedTexts
        let importantWords = ["AI", "Codex", "消息", "群", "好友", "红包", "转账", "收款", "视频", "图片", "文件", "链接", "素材", "发布", "配置", "申请", "审核", "错误", "失败", "生成", "登录", "帐号"]
        let scored = lines.map { line -> (String, Int) in
            var score = min(line.count / 12, 3)
            if importantWords.contains(where: { line.localizedCaseInsensitiveContains($0) }) {
                score += 4
            }
            if line.contains("：") || line.contains(":") {
                score += 1
            }
            if line.count >= 6 && line.count <= 48 {
                score += 1
            }
            return (line, score)
        }
        let sorted = scored.sorted {
            if $0.1 == $1.1 {
                return $0.0.count > $1.0.count
            }
            return $0.1 > $1.1
        }
        return Array(sorted.map(\.0).prefix(8))
    }

    var actionTexts: [String] {
        let actionWords = ["发送", "发布", "保存", "确认", "删除", "返回", "取消", "搜索", "选择", "设置", "查看", "预览", "申请", "同意", "领取", "转账", "收款", "生成", "编辑", "配置", "登录", "开始", "结束", "播放"]
        let actions = mergedTexts.filter { line in
            line.count <= 36 && actionWords.contains { line.localizedCaseInsensitiveContains($0) }
        }
        return Array(uniqueLines(actions).prefix(10))
    }

    var stats: (visible: Int, ocr: Int, merged: Int, actions: Int) {
        (visibleTexts.count, ocrTexts.count, mergedTexts.count, actionTexts.count)
    }

    var summaryText: String {
        let highlights = keyHighlights
        let highlightText = highlights.isEmpty
            ? "未在当前屏幕识别到可总结的文字内容。"
            : highlights.enumerated().map { "\($0.offset + 1). \($0.element)" }.joined(separator: "\n")
        let scrollText = scrollProgresses.isEmpty ? "暂无可滚动区域。" : scrollProgresses.joined(separator: "\n")
        let actionText = actionTexts.isEmpty
            ? "未识别到明显按钮或可操作文字。"
            : actionTexts.prefix(8).enumerated().map { "\($0.offset + 1). \($0.element)" }.joined(separator: "\n")
        return [
            "当前页面：\(pageTitle)",
            "识别结果：页面文本 \(stats.visible) 条，OCR 文本 \(stats.ocr) 条，去重后 \(stats.merged) 条。",
            "滚动位置：\n\(scrollText)",
            "重点内容：\n\(highlightText)",
            "可操作项：\n\(actionText)"
        ].joined(separator: "\n\n")
    }

    func searchText(keyword: String) -> String {
        let cleanedKeyword = keyword.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanedKeyword.isEmpty else { return "请输入关键词后再搜索。" }

        var hits: [String] = []
        appendHits(from: visibleTexts, source: "页面文本", keyword: cleanedKeyword, to: &hits)
        appendHits(from: ocrTexts, source: "OCR", keyword: cleanedKeyword, to: &hits)
        guard !hits.isEmpty else {
            return "未找到“\(cleanedKeyword)”。\n\n已搜索当前页面可见文本和屏幕 OCR 内容。"
        }
        return "找到 \(hits.count) 处“\(cleanedKeyword)”：\n\n" + hits.prefix(12).joined(separator: "\n")
    }

    func searchHits(keyword: String) -> [SafeAreaSearchHit] {
        let cleanedKeyword = keyword.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanedKeyword.isEmpty else { return [] }

        var hits: [SafeAreaSearchHit] = []
        appendSearchHits(from: visibleTexts, source: "页面文本", keyword: cleanedKeyword, to: &hits)
        appendSearchHits(from: ocrTexts, source: "OCR", keyword: cleanedKeyword, to: &hits)
        return Array(uniqueSearchHits(hits).prefix(30))
    }

    private func appendHits(from lines: [String], source: String, keyword: String, to hits: inout [String]) {
        for line in lines {
            if line.range(of: keyword, options: [.caseInsensitive, .diacriticInsensitive]) != nil {
                hits.append("【\(source)】\(line)")
            }
        }
    }

    private func appendSearchHits(from lines: [String], source: String, keyword: String, to hits: inout [SafeAreaSearchHit]) {
        for line in lines {
            guard line.range(of: keyword, options: [.caseInsensitive, .diacriticInsensitive]) != nil else { continue }
            hits.append(SafeAreaSearchHit(source: source, text: line))
        }
    }

    private func uniqueSearchHits(_ hits: [SafeAreaSearchHit]) -> [SafeAreaSearchHit] {
        var seen = Set<String>()
        return hits.compactMap { hit in
            let key = "\(hit.source)-\(hit.text)"
            guard !seen.contains(key) else { return nil }
            seen.insert(key)
            return hit
        }
    }

    private static func isLowValueLine(_ line: String) -> Bool {
        let trimmed = line.trimmingCharacters(in: .whitespacesAndNewlines)
        guard trimmed.count >= 2 else { return true }
        let lowValue = ["总结", "识搜", "快照", "性能报告", "完成", "取消", "知道了"]
        return lowValue.contains(trimmed)
    }
}

private struct SafeAreaSearchHit: Hashable {
    let source: String
    let text: String
}

private enum SafeAreaPageInsightService {
    static func analyze(window: UIWindow, presenter: UIViewController, completion: @escaping (SafeAreaPageInsight) -> Void) {
        let title = pageTitle(for: presenter)
        let visibleTexts = uniqueLines(collectVisibleText(in: presenter.view))
        let scrollProgresses = visibleScrollViews(in: presenter.view).map(scrollProgressText(for:))
        let image = snapshot(window: window)

        recognizeText(in: image) { ocrTexts in
            let insight = SafeAreaPageInsight(
                pageTitle: title,
                visibleTexts: visibleTexts,
                ocrTexts: uniqueLines(ocrTexts),
                scrollProgresses: scrollProgresses
            )
            DispatchQueue.main.async {
                completion(insight)
            }
        }
    }

    private static func pageTitle(for presenter: UIViewController) -> String {
        let navTitle = presenter.navigationItem.title?.trimmingCharacters(in: .whitespacesAndNewlines)
        if let navTitle, !navTitle.isEmpty {
            return navTitle
        }
        let title = presenter.title?.trimmingCharacters(in: .whitespacesAndNewlines)
        if let title, !title.isEmpty {
            return title
        }
        return String(describing: type(of: presenter))
    }

    private static func snapshot(window: UIWindow) -> UIImage {
        let renderer = UIGraphicsImageRenderer(bounds: window.bounds)
        return renderer.image { _ in
            window.drawHierarchy(in: window.bounds, afterScreenUpdates: false)
        }
    }

    private static func recognizeText(in image: UIImage, completion: @escaping ([String]) -> Void) {
        guard let cgImage = image.cgImage else {
            completion([])
            return
        }
        DispatchQueue.global(qos: .userInitiated).async {
            let request = VNRecognizeTextRequest { request, _ in
                let texts = (request.results as? [VNRecognizedTextObservation])?
                    .compactMap { $0.topCandidates(1).first?.string.trimmingCharacters(in: .whitespacesAndNewlines) }
                    .filter { !$0.isEmpty } ?? []
                completion(texts)
            }
            request.recognitionLevel = .accurate
            request.usesLanguageCorrection = true
            request.recognitionLanguages = ["zh-Hans", "en-US"]
            let handler = VNImageRequestHandler(cgImage: cgImage, options: [:])
            do {
                try handler.perform([request])
            } catch {
                completion([])
            }
        }
    }

    private static func collectVisibleText(in view: UIView) -> [String] {
        guard !view.isHidden, view.alpha > 0.02, view.window != nil else { return [] }
        var texts: [String] = []

        if let label = view as? UILabel {
            append(label.text, to: &texts)
            append(label.attributedText?.string, to: &texts)
        } else if let button = view as? UIButton {
            append(button.title(for: .normal), to: &texts)
            append(button.currentAttributedTitle?.string, to: &texts)
            append(button.accessibilityLabel, to: &texts)
        } else if let textField = view as? UITextField {
            append(textField.text, to: &texts)
            append(textField.placeholder, to: &texts)
        } else if let textView = view as? UITextView {
            append(textView.text, to: &texts)
        } else if let searchBar = view as? UISearchBar {
            append(searchBar.text, to: &texts)
            append(searchBar.placeholder, to: &texts)
        } else {
            append(view.accessibilityLabel, to: &texts)
            append(view.accessibilityValue, to: &texts)
        }

        for subview in view.subviews {
            texts.append(contentsOf: collectVisibleText(in: subview))
        }
        return texts
    }

    private static func append(_ text: String?, to texts: inout [String]) {
        let cleaned = text?
            .replacingOccurrences(of: "\n", with: " ")
            .trimmingCharacters(in: .whitespacesAndNewlines)
        guard let cleaned, !cleaned.isEmpty, cleaned.count > 1 else { return }
        texts.append(cleaned)
    }

    private static func visibleScrollViews(in view: UIView) -> [UIScrollView] {
        var result: [UIScrollView] = []
        if let scrollView = view as? UIScrollView, scrollView.window != nil {
            result.append(scrollView)
        }
        view.subviews.forEach { result.append(contentsOf: visibleScrollViews(in: $0)) }
        return result
    }

    private static func scrollProgressText(for scrollView: UIScrollView) -> String {
        let adjustedTop = scrollView.adjustedContentInset.top
        let adjustedBottom = scrollView.adjustedContentInset.bottom
        let visibleHeight = max(1, scrollView.bounds.height - adjustedTop - adjustedBottom)
        let scrollableHeight = max(1, scrollView.contentSize.height - visibleHeight)
        let offset = min(max(0, scrollView.contentOffset.y + adjustedTop), scrollableHeight)
        let percent = Int((offset / scrollableHeight * 100).rounded())
        return "滚动进度：\(percent)%"
    }
}

private func uniqueLines(_ lines: [String]) -> [String] {
    var seen = Set<String>()
    return lines.compactMap { line in
        let cleaned = line.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleaned.isEmpty, !seen.contains(cleaned) else { return nil }
        seen.insert(cleaned)
        return cleaned
    }
}

private final class SafeAreaInsightPanelViewController: UIViewController, UITextFieldDelegate {
    enum Mode: Int {
        case summary
        case actions
        case search
    }

    private let initialMode: Mode
    private var mode: Mode
    private var insight: SafeAreaPageInsight?

    private let headerCard = UIView()
    private let iconContainer = UIView()
    private let iconView = UIImageView(image: UIImage(systemName: "sparkle.magnifyingglass"))
    private let titleLabel = UILabel()
    private let subtitleLabel = UILabel()
    private let statStack = UIStackView()
    private let segmentedControl = UISegmentedControl(items: ["重点", "操作", "识搜"])
    private let searchContainer = UIView()
    private let searchField = UITextField()
    private let scrollView = UIScrollView()
    private let stackView = UIStackView()
    private let footerStack = UIStackView()
    private let copyButton = UIButton(type: .system)
    private let closeButton = UIButton(type: .system)
    private let loadingIndicator = UIActivityIndicatorView(style: .medium)

    init(initialMode: Mode) {
        self.initialMode = initialMode
        self.mode = initialMode
        super.init(nibName: nil, bundle: nil)
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        configureView()
        configureHeader()
        configureControls()
        configureContent()
        reloadContent()
    }

    func apply(insight: SafeAreaPageInsight) {
        self.insight = insight
        loadingIndicator.stopAnimating()
        subtitleLabel.text = "\(insight.pageTitle) · 已识别 \(insight.stats.merged) 条内容"
        rebuildStats()
        reloadContent()
    }

    private func configureView() {
        view.backgroundColor = UIColor(red: 0.95, green: 0.97, blue: 0.96, alpha: 1)
        title = "页面洞察"

        closeButton.setTitle("完成", for: .normal)
        closeButton.titleLabel?.font = .systemFont(ofSize: 15, weight: .semibold)
        closeButton.addTarget(self, action: #selector(closeTapped), for: .touchUpInside)
        closeButton.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(closeButton)

        loadingIndicator.startAnimating()
        loadingIndicator.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(loadingIndicator)
    }

    private func configureHeader() {
        headerCard.backgroundColor = .white
        headerCard.layer.cornerRadius = 22
        headerCard.layer.cornerCurve = .continuous
        headerCard.layer.shadowColor = UIColor.black.cgColor
        headerCard.layer.shadowOpacity = 0.06
        headerCard.layer.shadowRadius = 18
        headerCard.layer.shadowOffset = CGSize(width: 0, height: 8)
        headerCard.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(headerCard)

        iconContainer.backgroundColor = UIColor(red: 0.10, green: 0.48, blue: 0.36, alpha: 0.12)
        iconContainer.layer.cornerRadius = 16
        iconContainer.layer.cornerCurve = .continuous
        iconContainer.translatesAutoresizingMaskIntoConstraints = false
        headerCard.addSubview(iconContainer)

        iconView.tintColor = UIColor(red: 0.08, green: 0.42, blue: 0.30, alpha: 1)
        iconView.contentMode = .scaleAspectFit
        iconView.translatesAutoresizingMaskIntoConstraints = false
        iconContainer.addSubview(iconView)

        titleLabel.text = "当前页面洞察"
        titleLabel.font = .systemFont(ofSize: 20, weight: .bold)
        titleLabel.textColor = UIColor(red: 0.08, green: 0.10, blue: 0.12, alpha: 1)
        titleLabel.translatesAutoresizingMaskIntoConstraints = false
        headerCard.addSubview(titleLabel)

        subtitleLabel.text = "正在识别当前页面文本、按钮和截图 OCR..."
        subtitleLabel.font = .systemFont(ofSize: 13, weight: .medium)
        subtitleLabel.textColor = .secondaryLabel
        subtitleLabel.numberOfLines = 2
        subtitleLabel.translatesAutoresizingMaskIntoConstraints = false
        headerCard.addSubview(subtitleLabel)

        statStack.axis = .horizontal
        statStack.alignment = .fill
        statStack.distribution = .fillEqually
        statStack.spacing = 8
        statStack.translatesAutoresizingMaskIntoConstraints = false
        headerCard.addSubview(statStack)
        rebuildStats()

        NSLayoutConstraint.activate([
            closeButton.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 12),
            closeButton.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -18),

            headerCard.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 48),
            headerCard.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 16),
            headerCard.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -16),

            iconContainer.topAnchor.constraint(equalTo: headerCard.topAnchor, constant: 16),
            iconContainer.leadingAnchor.constraint(equalTo: headerCard.leadingAnchor, constant: 16),
            iconContainer.widthAnchor.constraint(equalToConstant: 48),
            iconContainer.heightAnchor.constraint(equalToConstant: 48),

            iconView.centerXAnchor.constraint(equalTo: iconContainer.centerXAnchor),
            iconView.centerYAnchor.constraint(equalTo: iconContainer.centerYAnchor),
            iconView.widthAnchor.constraint(equalToConstant: 24),
            iconView.heightAnchor.constraint(equalToConstant: 24),

            titleLabel.topAnchor.constraint(equalTo: iconContainer.topAnchor, constant: 1),
            titleLabel.leadingAnchor.constraint(equalTo: iconContainer.trailingAnchor, constant: 12),
            titleLabel.trailingAnchor.constraint(equalTo: headerCard.trailingAnchor, constant: -16),

            subtitleLabel.topAnchor.constraint(equalTo: titleLabel.bottomAnchor, constant: 5),
            subtitleLabel.leadingAnchor.constraint(equalTo: titleLabel.leadingAnchor),
            subtitleLabel.trailingAnchor.constraint(equalTo: titleLabel.trailingAnchor),

            statStack.topAnchor.constraint(equalTo: iconContainer.bottomAnchor, constant: 16),
            statStack.leadingAnchor.constraint(equalTo: headerCard.leadingAnchor, constant: 16),
            statStack.trailingAnchor.constraint(equalTo: headerCard.trailingAnchor, constant: -16),
            statStack.bottomAnchor.constraint(equalTo: headerCard.bottomAnchor, constant: -16),
            statStack.heightAnchor.constraint(equalToConstant: 54)
        ])
    }

    private func configureControls() {
        segmentedControl.selectedSegmentIndex = initialMode.rawValue
        segmentedControl.backgroundColor = UIColor.white.withAlphaComponent(0.8)
        segmentedControl.selectedSegmentTintColor = UIColor(red: 0.10, green: 0.48, blue: 0.36, alpha: 1)
        segmentedControl.setTitleTextAttributes([.foregroundColor: UIColor.white, .font: UIFont.systemFont(ofSize: 13, weight: .semibold)], for: .selected)
        segmentedControl.setTitleTextAttributes([.foregroundColor: UIColor.secondaryLabel, .font: UIFont.systemFont(ofSize: 13, weight: .medium)], for: .normal)
        segmentedControl.addTarget(self, action: #selector(modeChanged), for: .valueChanged)
        segmentedControl.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(segmentedControl)

        searchContainer.backgroundColor = .white
        searchContainer.layer.cornerRadius = 16
        searchContainer.layer.cornerCurve = .continuous
        searchContainer.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(searchContainer)

        let searchIcon = UIImageView(image: UIImage(systemName: "magnifyingglass"))
        searchIcon.tintColor = .secondaryLabel
        searchIcon.contentMode = .scaleAspectFit
        searchIcon.translatesAutoresizingMaskIntoConstraints = false
        searchContainer.addSubview(searchIcon)

        searchField.placeholder = "输入关键词搜索当前页面"
        searchField.font = .systemFont(ofSize: 15, weight: .medium)
        searchField.returnKeyType = .search
        searchField.clearButtonMode = .whileEditing
        searchField.delegate = self
        searchField.addTarget(self, action: #selector(searchChanged), for: .editingChanged)
        searchField.translatesAutoresizingMaskIntoConstraints = false
        searchContainer.addSubview(searchField)

        NSLayoutConstraint.activate([
            segmentedControl.topAnchor.constraint(equalTo: headerCard.bottomAnchor, constant: 14),
            segmentedControl.leadingAnchor.constraint(equalTo: headerCard.leadingAnchor),
            segmentedControl.trailingAnchor.constraint(equalTo: headerCard.trailingAnchor),
            segmentedControl.heightAnchor.constraint(equalToConstant: 36),

            searchContainer.topAnchor.constraint(equalTo: segmentedControl.bottomAnchor, constant: 10),
            searchContainer.leadingAnchor.constraint(equalTo: segmentedControl.leadingAnchor),
            searchContainer.trailingAnchor.constraint(equalTo: segmentedControl.trailingAnchor),
            searchContainer.heightAnchor.constraint(equalToConstant: 46),

            searchIcon.leadingAnchor.constraint(equalTo: searchContainer.leadingAnchor, constant: 14),
            searchIcon.centerYAnchor.constraint(equalTo: searchContainer.centerYAnchor),
            searchIcon.widthAnchor.constraint(equalToConstant: 17),
            searchIcon.heightAnchor.constraint(equalToConstant: 17),

            searchField.leadingAnchor.constraint(equalTo: searchIcon.trailingAnchor, constant: 9),
            searchField.trailingAnchor.constraint(equalTo: searchContainer.trailingAnchor, constant: -12),
            searchField.topAnchor.constraint(equalTo: searchContainer.topAnchor),
            searchField.bottomAnchor.constraint(equalTo: searchContainer.bottomAnchor)
        ])
    }

    private func configureContent() {
        scrollView.alwaysBounceVertical = true
        scrollView.showsVerticalScrollIndicator = false
        scrollView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(scrollView)

        stackView.axis = .vertical
        stackView.spacing = 10
        stackView.translatesAutoresizingMaskIntoConstraints = false
        scrollView.addSubview(stackView)

        footerStack.axis = .horizontal
        footerStack.spacing = 10
        footerStack.distribution = .fillEqually
        footerStack.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(footerStack)

        configureFooterButton(copyButton, title: "复制摘要", symbolName: "doc.on.doc")
        copyButton.addTarget(self, action: #selector(copySummaryTapped), for: .touchUpInside)
        footerStack.addArrangedSubview(copyButton)

        NSLayoutConstraint.activate([
            scrollView.topAnchor.constraint(equalTo: searchContainer.bottomAnchor, constant: 12),
            scrollView.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 16),
            scrollView.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -16),
            scrollView.bottomAnchor.constraint(equalTo: footerStack.topAnchor, constant: -12),

            stackView.topAnchor.constraint(equalTo: scrollView.contentLayoutGuide.topAnchor),
            stackView.leadingAnchor.constraint(equalTo: scrollView.contentLayoutGuide.leadingAnchor),
            stackView.trailingAnchor.constraint(equalTo: scrollView.contentLayoutGuide.trailingAnchor),
            stackView.bottomAnchor.constraint(equalTo: scrollView.contentLayoutGuide.bottomAnchor),
            stackView.widthAnchor.constraint(equalTo: scrollView.frameLayoutGuide.widthAnchor),

            footerStack.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 16),
            footerStack.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -16),
            footerStack.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor, constant: -12),
            footerStack.heightAnchor.constraint(equalToConstant: 48),

            loadingIndicator.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            loadingIndicator.centerYAnchor.constraint(equalTo: scrollView.centerYAnchor)
        ])
    }

    private func reloadContent() {
        mode = Mode(rawValue: segmentedControl.selectedSegmentIndex) ?? mode
        searchContainer.isHidden = mode != .search
        searchContainer.alpha = mode == .search ? 1 : 0

        stackView.arrangedSubviews.forEach {
            stackView.removeArrangedSubview($0)
            $0.removeFromSuperview()
        }

        guard let insight else {
            stackView.addArrangedSubview(makeEmptyCard(
                title: "正在分析当前页面",
                message: "会读取当前可见控件文字、截图 OCR 和滚动位置，完成后自动展示结果。",
                symbolName: "hourglass"
            ))
            return
        }

        switch mode {
        case .summary:
            buildSummary(insight)
        case .actions:
            buildActions(insight)
        case .search:
            buildSearch(insight)
        }
    }

    private func buildSummary(_ insight: SafeAreaPageInsight) {
        if insight.keyHighlights.isEmpty {
            stackView.addArrangedSubview(makeEmptyCard(
                title: "没有识别到足够内容",
                message: "当前页面可能是图片、视频或自绘内容较多。可以用快照保存，再用识图功能继续处理。",
                symbolName: "text.viewfinder"
            ))
        } else {
            stackView.addArrangedSubview(makeSectionHeader("重点内容", subtitle: "按当前页面出现频率和业务关键词排序"))
            insight.keyHighlights.prefix(8).enumerated().forEach { index, text in
                stackView.addArrangedSubview(makeInsightRow(
                    title: text,
                    subtitle: "重点 \(index + 1)",
                    symbolName: index < 3 ? "star.fill" : "text.alignleft",
                    tintColor: index < 3 ? UIColor.systemOrange : UIColor(red: 0.10, green: 0.48, blue: 0.36, alpha: 1)
                ))
            }
        }

        stackView.addArrangedSubview(makeSectionHeader("页面位置", subtitle: "帮助判断当前在列表中的位置"))
        if insight.scrollProgresses.isEmpty {
            stackView.addArrangedSubview(makeInsightRow(title: "当前页没有明显滚动区域", subtitle: "页面位置", symbolName: "rectangle", tintColor: .systemGray))
        } else {
            insight.scrollProgresses.prefix(3).forEach {
                stackView.addArrangedSubview(makeInsightRow(title: $0, subtitle: "滚动进度", symbolName: "arrow.up.and.down", tintColor: .systemBlue))
            }
        }

        stackView.addArrangedSubview(makeSuggestionCard(insight))
    }

    private func buildActions(_ insight: SafeAreaPageInsight) {
        stackView.addArrangedSubview(makeSectionHeader("可操作内容", subtitle: "从按钮、输入框、菜单文字中提取"))
        if insight.actionTexts.isEmpty {
            stackView.addArrangedSubview(makeEmptyCard(
                title: "暂无明显操作项",
                message: "如果当前是视频、图片或地图页面，可以使用快照先保存上下文。",
                symbolName: "hand.tap"
            ))
        } else {
            insight.actionTexts.enumerated().forEach { index, text in
                stackView.addArrangedSubview(makeInsightRow(
                    title: text,
                    subtitle: "可点击或可继续操作 \(index + 1)",
                    symbolName: actionSymbol(for: text),
                    tintColor: UIColor(red: 0.12, green: 0.42, blue: 0.78, alpha: 1)
                ))
            }
        }
    }

    private func buildSearch(_ insight: SafeAreaPageInsight) {
        let keyword = searchField.text?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        guard !keyword.isEmpty else {
            stackView.addArrangedSubview(makeEmptyCard(
                title: "输入关键词后搜索",
                message: "会同时搜索当前页面控件文字和屏幕 OCR 文本，适合找聊天内容、按钮、金额、文件名。",
                symbolName: "magnifyingglass"
            ))
            return
        }

        let hits = insight.searchHits(keyword: keyword)
        stackView.addArrangedSubview(makeSectionHeader("搜索结果", subtitle: hits.isEmpty ? "没有找到“\(keyword)”" : "找到 \(hits.count) 处“\(keyword)”"))
        if hits.isEmpty {
            stackView.addArrangedSubview(makeEmptyCard(title: "没有匹配内容", message: "换一个更短的关键词试试。", symbolName: "text.magnifyingglass"))
            return
        }

        hits.forEach { hit in
            stackView.addArrangedSubview(makeInsightRow(
                title: hit.text,
                subtitle: hit.source,
                symbolName: hit.source == "OCR" ? "viewfinder" : "textformat",
                tintColor: hit.source == "OCR" ? UIColor.systemPurple : UIColor.systemGreen
            ))
        }
    }

    private func rebuildStats() {
        statStack.arrangedSubviews.forEach {
            statStack.removeArrangedSubview($0)
            $0.removeFromSuperview()
        }

        let stats = insight?.stats ?? (visible: 0, ocr: 0, merged: 0, actions: 0)
        [
            ("文本", "\(stats.visible)", "text.alignleft"),
            ("OCR", "\(stats.ocr)", "viewfinder"),
            ("重点", "\(stats.merged)", "sparkles"),
            ("操作", "\(stats.actions)", "hand.tap")
        ].forEach { item in
            statStack.addArrangedSubview(makeStatView(title: item.0, value: item.1, symbolName: item.2))
        }
    }

    private func makeStatView(title: String, value: String, symbolName: String) -> UIView {
        let view = UIView()
        view.backgroundColor = UIColor(red: 0.96, green: 0.98, blue: 0.97, alpha: 1)
        view.layer.cornerRadius = 14
        view.layer.cornerCurve = .continuous

        let icon = UIImageView(image: UIImage(systemName: symbolName))
        icon.tintColor = UIColor(red: 0.10, green: 0.48, blue: 0.36, alpha: 1)
        icon.contentMode = .scaleAspectFit
        icon.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(icon)

        let valueLabel = UILabel()
        valueLabel.text = value
        valueLabel.font = .systemFont(ofSize: 17, weight: .bold)
        valueLabel.textColor = .label
        valueLabel.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(valueLabel)

        let titleLabel = UILabel()
        titleLabel.text = title
        titleLabel.font = .systemFont(ofSize: 10.5, weight: .medium)
        titleLabel.textColor = .secondaryLabel
        titleLabel.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(titleLabel)

        NSLayoutConstraint.activate([
            icon.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 10),
            icon.centerYAnchor.constraint(equalTo: view.centerYAnchor),
            icon.widthAnchor.constraint(equalToConstant: 15),
            icon.heightAnchor.constraint(equalToConstant: 15),
            valueLabel.topAnchor.constraint(equalTo: view.topAnchor, constant: 8),
            valueLabel.leadingAnchor.constraint(equalTo: icon.trailingAnchor, constant: 5),
            valueLabel.trailingAnchor.constraint(lessThanOrEqualTo: view.trailingAnchor, constant: -6),
            titleLabel.topAnchor.constraint(equalTo: valueLabel.bottomAnchor, constant: 1),
            titleLabel.leadingAnchor.constraint(equalTo: valueLabel.leadingAnchor),
            titleLabel.trailingAnchor.constraint(lessThanOrEqualTo: view.trailingAnchor, constant: -6)
        ])
        return view
    }

    private func makeSectionHeader(_ title: String, subtitle: String) -> UIView {
        let container = UIView()
        let titleLabel = UILabel()
        titleLabel.text = title
        titleLabel.font = .systemFont(ofSize: 16, weight: .bold)
        titleLabel.textColor = .label
        titleLabel.translatesAutoresizingMaskIntoConstraints = false

        let subtitleLabel = UILabel()
        subtitleLabel.text = subtitle
        subtitleLabel.font = .systemFont(ofSize: 12, weight: .medium)
        subtitleLabel.textColor = .secondaryLabel
        subtitleLabel.numberOfLines = 2
        subtitleLabel.translatesAutoresizingMaskIntoConstraints = false

        container.addSubview(titleLabel)
        container.addSubview(subtitleLabel)
        NSLayoutConstraint.activate([
            titleLabel.topAnchor.constraint(equalTo: container.topAnchor, constant: 4),
            titleLabel.leadingAnchor.constraint(equalTo: container.leadingAnchor, constant: 2),
            titleLabel.trailingAnchor.constraint(equalTo: container.trailingAnchor, constant: -2),
            subtitleLabel.topAnchor.constraint(equalTo: titleLabel.bottomAnchor, constant: 2),
            subtitleLabel.leadingAnchor.constraint(equalTo: titleLabel.leadingAnchor),
            subtitleLabel.trailingAnchor.constraint(equalTo: titleLabel.trailingAnchor),
            subtitleLabel.bottomAnchor.constraint(equalTo: container.bottomAnchor, constant: -4)
        ])
        return container
    }

    private func makeInsightRow(title: String, subtitle: String, symbolName: String, tintColor: UIColor) -> UIView {
        let card = UIView()
        card.backgroundColor = .white
        card.layer.cornerRadius = 16
        card.layer.cornerCurve = .continuous

        let iconBox = UIView()
        iconBox.backgroundColor = tintColor.withAlphaComponent(0.12)
        iconBox.layer.cornerRadius = 12
        iconBox.layer.cornerCurve = .continuous
        iconBox.translatesAutoresizingMaskIntoConstraints = false
        card.addSubview(iconBox)

        let icon = UIImageView(image: UIImage(systemName: symbolName))
        icon.tintColor = tintColor
        icon.contentMode = .scaleAspectFit
        icon.translatesAutoresizingMaskIntoConstraints = false
        iconBox.addSubview(icon)

        let subtitleLabel = UILabel()
        subtitleLabel.text = subtitle
        subtitleLabel.font = .systemFont(ofSize: 11.5, weight: .semibold)
        subtitleLabel.textColor = tintColor
        subtitleLabel.translatesAutoresizingMaskIntoConstraints = false
        card.addSubview(subtitleLabel)

        let titleLabel = UILabel()
        titleLabel.text = title
        titleLabel.font = .systemFont(ofSize: 14.5, weight: .medium)
        titleLabel.textColor = UIColor(red: 0.09, green: 0.11, blue: 0.13, alpha: 1)
        titleLabel.numberOfLines = 0
        titleLabel.translatesAutoresizingMaskIntoConstraints = false
        card.addSubview(titleLabel)

        NSLayoutConstraint.activate([
            iconBox.topAnchor.constraint(equalTo: card.topAnchor, constant: 14),
            iconBox.leadingAnchor.constraint(equalTo: card.leadingAnchor, constant: 14),
            iconBox.widthAnchor.constraint(equalToConstant: 36),
            iconBox.heightAnchor.constraint(equalToConstant: 36),
            icon.centerXAnchor.constraint(equalTo: iconBox.centerXAnchor),
            icon.centerYAnchor.constraint(equalTo: iconBox.centerYAnchor),
            icon.widthAnchor.constraint(equalToConstant: 18),
            icon.heightAnchor.constraint(equalToConstant: 18),

            subtitleLabel.topAnchor.constraint(equalTo: card.topAnchor, constant: 13),
            subtitleLabel.leadingAnchor.constraint(equalTo: iconBox.trailingAnchor, constant: 12),
            subtitleLabel.trailingAnchor.constraint(equalTo: card.trailingAnchor, constant: -14),

            titleLabel.topAnchor.constraint(equalTo: subtitleLabel.bottomAnchor, constant: 5),
            titleLabel.leadingAnchor.constraint(equalTo: subtitleLabel.leadingAnchor),
            titleLabel.trailingAnchor.constraint(equalTo: subtitleLabel.trailingAnchor),
            titleLabel.bottomAnchor.constraint(equalTo: card.bottomAnchor, constant: -14)
        ])
        return card
    }

    private func makeSuggestionCard(_ insight: SafeAreaPageInsight) -> UIView {
        let message: String
        if !insight.actionTexts.isEmpty {
            message = "建议先处理“操作”页签中的关键按钮或输入项；如果要找具体内容，切到“识搜”输入关键词。"
        } else if insight.stats.ocr > insight.stats.visible {
            message = "当前页可见控件文字较少，OCR 内容更多；适合用识搜查找图片或自绘区域中的文字。"
        } else {
            message = "当前页信息已经整理完成，可以复制摘要发给 AI 或用于记录当前页面状态。"
        }
        return makeEmptyCard(title: "下一步建议", message: message, symbolName: "lightbulb")
    }

    private func makeEmptyCard(title: String, message: String, symbolName: String) -> UIView {
        makeInsightRow(
            title: message,
            subtitle: title,
            symbolName: symbolName,
            tintColor: UIColor(red: 0.44, green: 0.48, blue: 0.54, alpha: 1)
        )
    }

    private func configureFooterButton(_ button: UIButton, title: String, symbolName: String) {
        var configuration = UIButton.Configuration.filled()
        configuration.title = title
        configuration.image = UIImage(systemName: symbolName)
        configuration.imagePadding = 6
        configuration.baseForegroundColor = .white
        configuration.baseBackgroundColor = UIColor(red: 0.10, green: 0.48, blue: 0.36, alpha: 1)
        configuration.cornerStyle = .large
        button.configuration = configuration
    }

    private func actionSymbol(for text: String) -> String {
        if text.contains("搜索") { return "magnifyingglass" }
        if text.contains("发送") { return "paperplane.fill" }
        if text.contains("删除") { return "trash" }
        if text.contains("播放") { return "play.fill" }
        if text.contains("保存") || text.contains("确认") { return "checkmark.circle.fill" }
        if text.contains("返回") || text.contains("取消") { return "arrow.uturn.left" }
        if text.contains("设置") || text.contains("配置") { return "gearshape.fill" }
        return "hand.tap.fill"
    }

    @objc private func modeChanged() {
        reloadContent()
        if mode == .search {
            searchField.becomeFirstResponder()
        } else {
            searchField.resignFirstResponder()
        }
    }

    @objc private func searchChanged() {
        guard mode == .search else { return }
        reloadContent()
    }

    @objc private func copySummaryTapped() {
        guard let insight else { return }
        UIPasteboard.general.string = insight.summaryText
        let generator = UINotificationFeedbackGenerator()
        generator.notificationOccurred(.success)

        let previousTitle = copyButton.configuration?.title
        copyButton.configuration?.title = "已复制"
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.2) { [weak self] in
            self?.copyButton.configuration?.title = previousTitle
        }
    }

    @objc private func closeTapped() {
        dismiss(animated: true)
    }

    func textFieldShouldReturn(_ textField: UITextField) -> Bool {
        textField.resignFirstResponder()
        return true
    }
}

private final class SafeAreaSummaryPromptView: UIControl {
    var onSummary: (() -> Void)?
    var onSearch: (() -> Void)?
    var onSnapshot: (() -> Void)?
    var onReport: (() -> Void)?

    private let effectView = UIVisualEffectView(effect: UIBlurEffect(style: .systemChromeMaterial))
    private let stackView = UIStackView()

    override init(frame: CGRect) {
        super.init(frame: frame)
        configure()
    }

    required init?(coder: NSCoder) {
        super.init(coder: coder)
        configure()
    }

    private func configure() {
        layer.shadowColor = UIColor.black.cgColor
        layer.shadowOpacity = 0.12
        layer.shadowRadius = 12
        layer.shadowOffset = CGSize(width: 0, height: 5)

        effectView.translatesAutoresizingMaskIntoConstraints = false
        effectView.clipsToBounds = true
        effectView.layer.cornerRadius = 20
        effectView.layer.borderWidth = 0.7
        effectView.layer.borderColor = UIColor.white.withAlphaComponent(0.55).cgColor
        addSubview(effectView)

        stackView.axis = .horizontal
        stackView.alignment = .center
        stackView.spacing = 4
        stackView.translatesAutoresizingMaskIntoConstraints = false
        effectView.contentView.addSubview(stackView)

        stackView.addArrangedSubview(makeIconButton(systemName: "slider.horizontal.3", action: #selector(summaryTapped), label: "工具"))

        stackView.addArrangedSubview(makeButton(title: "总结", systemName: "text.page", action: #selector(summaryTapped)))
        stackView.addArrangedSubview(makeButton(title: "识搜", systemName: "magnifyingglass", action: #selector(searchTapped)))
        stackView.addArrangedSubview(makeButton(title: "快照", systemName: "camera.viewfinder", action: #selector(snapshotTapped)))
        stackView.addArrangedSubview(makeIconButton(systemName: "speedometer", action: #selector(reportTapped), label: "性能报告"))

        NSLayoutConstraint.activate([
            effectView.topAnchor.constraint(equalTo: topAnchor),
            effectView.leadingAnchor.constraint(equalTo: leadingAnchor),
            effectView.trailingAnchor.constraint(equalTo: trailingAnchor),
            effectView.bottomAnchor.constraint(equalTo: bottomAnchor),
            stackView.topAnchor.constraint(equalTo: effectView.contentView.topAnchor, constant: 5),
            stackView.leadingAnchor.constraint(equalTo: effectView.contentView.leadingAnchor, constant: 10),
            stackView.trailingAnchor.constraint(equalTo: effectView.contentView.trailingAnchor, constant: -10),
            stackView.bottomAnchor.constraint(equalTo: effectView.contentView.bottomAnchor, constant: -5)
        ])
    }

    private func makeButton(title: String, systemName: String, action: Selector) -> UIButton {
        var configuration = UIButton.Configuration.filled()
        configuration.title = title
        configuration.image = UIImage(systemName: systemName)
        configuration.imagePadding = 2
        configuration.titleLineBreakMode = .byClipping
        configuration.baseForegroundColor = UIColor(red: 0.08, green: 0.18, blue: 0.24, alpha: 1)
        configuration.baseBackgroundColor = UIColor.white.withAlphaComponent(0.72)
        configuration.contentInsets = NSDirectionalEdgeInsets(top: 5, leading: 6, bottom: 5, trailing: 7)
        let button = UIButton(configuration: configuration)
        button.titleLabel?.font = .systemFont(ofSize: 12, weight: .medium)
        button.titleLabel?.numberOfLines = 1
        button.titleLabel?.lineBreakMode = .byClipping
        button.titleLabel?.adjustsFontSizeToFitWidth = true
        button.titleLabel?.minimumScaleFactor = 0.9
        button.setContentCompressionResistancePriority(.required, for: .horizontal)
        button.setContentHuggingPriority(.required, for: .horizontal)
        button.layer.cornerRadius = 13
        button.widthAnchor.constraint(greaterThanOrEqualToConstant: 54).isActive = true
        button.addTarget(self, action: action, for: .touchUpInside)
        return button
    }

    private func makeIconButton(systemName: String, action: Selector, label: String) -> UIButton {
        var configuration = UIButton.Configuration.filled()
        configuration.image = UIImage(systemName: systemName)
        configuration.baseForegroundColor = UIColor(red: 0.08, green: 0.18, blue: 0.24, alpha: 1)
        configuration.baseBackgroundColor = UIColor.white.withAlphaComponent(0.72)
        configuration.contentInsets = NSDirectionalEdgeInsets(top: 6, leading: 7, bottom: 6, trailing: 7)
        let button = UIButton(configuration: configuration)
        button.accessibilityLabel = label
        button.layer.cornerRadius = 13
        button.addTarget(self, action: action, for: .touchUpInside)
        return button
    }

    @objc private func summaryTapped() {
        onSummary?()
    }

    @objc private func searchTapped() {
        onSearch?()
    }

    @objc private func snapshotTapped() {
        onSnapshot?()
    }

    @objc private func reportTapped() {
        onReport?()
    }
}

private final class PageSnapshotPreviewViewController: UIViewController {
    private let image: UIImage
    private weak var dragForwardButton: UIButton?
    private weak var shareButton: UIButton?

    init(image: UIImage) {
        self.image = image
        super.init(nibName: nil, bundle: nil)
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = UIColor.black

        let imageView = UIImageView(image: image)
        imageView.contentMode = .scaleAspectFit
        imageView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(imageView)

        let closeButton = UIButton(type: .system)
        closeButton.setImage(UIImage(systemName: "xmark"), for: .normal)
        closeButton.tintColor = .white
        closeButton.backgroundColor = UIColor.black.withAlphaComponent(0.38)
        closeButton.layer.cornerRadius = 18
        closeButton.translatesAutoresizingMaskIntoConstraints = false
        closeButton.addTarget(self, action: #selector(closeTapped), for: .touchUpInside)
        view.addSubview(closeButton)

        let shareButton = UIButton(type: .system)
        shareButton.setImage(UIImage(systemName: "square.and.arrow.up"), for: .normal)
        shareButton.tintColor = .white
        shareButton.backgroundColor = UIColor.black.withAlphaComponent(0.38)
        shareButton.layer.cornerRadius = 18
        shareButton.translatesAutoresizingMaskIntoConstraints = false
        shareButton.addTarget(self, action: #selector(shareTapped), for: .touchUpInside)
        shareButton.accessibilityLabel = "分享快照"
        let shareDragGesture = UILongPressGestureRecognizer(target: self, action: #selector(handleSnapshotDragGesture(_:)))
        shareDragGesture.minimumPressDuration = 0.35
        shareButton.addGestureRecognizer(shareDragGesture)
        view.addSubview(shareButton)
        self.shareButton = shareButton

        let dragForwardButton = UIButton(type: .system)
        dragForwardButton.setImage(UIImage(systemName: "arrowshape.turn.up.left.fill"), for: .normal)
        dragForwardButton.tintColor = .white
        dragForwardButton.backgroundColor = UIColor.black.withAlphaComponent(0.38)
        dragForwardButton.layer.cornerRadius = 18
        dragForwardButton.translatesAutoresizingMaskIntoConstraints = false
        dragForwardButton.accessibilityLabel = "拖拽转发快照"
        dragForwardButton.addTarget(self, action: #selector(showDragForwardHint), for: .touchUpInside)
        let dragGesture = UILongPressGestureRecognizer(target: self, action: #selector(handleSnapshotDragGesture(_:)))
        dragGesture.minimumPressDuration = 0.35
        dragForwardButton.addGestureRecognizer(dragGesture)
        view.addSubview(dragForwardButton)
        self.dragForwardButton = dragForwardButton

        NSLayoutConstraint.activate([
            imageView.topAnchor.constraint(equalTo: view.topAnchor),
            imageView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            imageView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            imageView.bottomAnchor.constraint(equalTo: view.bottomAnchor),
            closeButton.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 12),
            closeButton.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 16),
            closeButton.widthAnchor.constraint(equalToConstant: 36),
            closeButton.heightAnchor.constraint(equalToConstant: 36),
            shareButton.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 12),
            shareButton.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -16),
            shareButton.widthAnchor.constraint(equalToConstant: 36),
            shareButton.heightAnchor.constraint(equalToConstant: 36),
            dragForwardButton.topAnchor.constraint(equalTo: shareButton.topAnchor),
            dragForwardButton.trailingAnchor.constraint(equalTo: shareButton.leadingAnchor, constant: -10),
            dragForwardButton.widthAnchor.constraint(equalToConstant: 36),
            dragForwardButton.heightAnchor.constraint(equalToConstant: 36)
        ])
    }

    @objc private func closeTapped() {
        dismiss(animated: true)
    }

    @objc private func shareTapped() {
        let activity = UIActivityViewController(activityItems: [image], applicationActivities: nil)
        present(activity, animated: true)
    }

    @objc private func showDragForwardHint() {
        let alert = UIAlertController(
            title: "拖拽转发快照",
            message: "长按这个按钮，拖到左侧好友或群聊后松手即可转发快照。",
            preferredStyle: .alert
        )
        alert.addAction(UIAlertAction(title: "知道了", style: .default))
        present(alert, animated: true)
    }

    @objc private func handleSnapshotDragGesture(_ gesture: UILongPressGestureRecognizer) {
        guard let sourceView = gesture.view else { return }
        let point = sourceView.convert(gesture.location(in: sourceView), to: nil)
        postSnapshotDrag(state: gesture.state, screenPoint: point)

        switch gesture.state {
        case .began:
            UIImpactFeedbackGenerator(style: .medium).impactOccurred()
            UIView.animate(withDuration: 0.16, delay: 0, options: [.curveEaseOut, .allowUserInteraction]) {
                sourceView.transform = CGAffineTransform(scaleX: 0.82, y: 0.82)
                sourceView.alpha = 0.72
            }
        case .changed:
            break
        case .ended, .cancelled, .failed:
            UIView.animate(withDuration: 0.18, delay: 0, options: [.curveEaseOut, .allowUserInteraction]) {
                sourceView.transform = .identity
                sourceView.alpha = 1
            }
        default:
            break
        }
    }

    private func postSnapshotDrag(state: UIGestureRecognizer.State, screenPoint: CGPoint) {
        NotificationCenter.default.post(
            name: .safeAreaSnapshotDragDidUpdate,
            object: self,
            userInfo: [
                SafeAreaSnapshotDragUserInfoKey.image: image,
                SafeAreaSnapshotDragUserInfoKey.title: "页面快照",
                SafeAreaSnapshotDragUserInfoKey.state: state.rawValue,
                SafeAreaSnapshotDragUserInfoKey.screenPoint: NSValue(cgPoint: screenPoint)
            ]
        )
    }
}

private extension UIViewController {
    func topMostPresentedViewController() -> UIViewController {
        if let presentedViewController {
            return presentedViewController.topMostPresentedViewController()
        }
        if let navigationController = self as? UINavigationController {
            return navigationController.visibleViewController?.topMostPresentedViewController() ?? navigationController
        }
        if let tabBarController = self as? UITabBarController {
            return tabBarController.selectedViewController?.topMostPresentedViewController() ?? tabBarController
        }
        return self
    }
}
