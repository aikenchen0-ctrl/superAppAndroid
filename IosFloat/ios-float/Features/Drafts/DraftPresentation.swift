import UIKit

final class DraftEditorViewController: UIViewController, UITextViewDelegate {
    var onSave: ((String) -> Void)?
    var onCancel: (() -> Void)?

    private let textView = UITextView()
    private let counterLabel = UILabel()
    private let initialText: String

    init(text: String) {
        initialText = text
        super.init(nibName: nil, bundle: nil)
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        title = "编辑 AI 草稿"
        view.backgroundColor = UIColor.systemGroupedBackground
        navigationItem.leftBarButtonItem = UIBarButtonItem(title: "取消", style: .plain, target: self, action: #selector(cancelTapped))
        navigationItem.rightBarButtonItem = UIBarButtonItem(title: "保存", style: .done, target: self, action: #selector(saveTapped))

        let titleLabel = UILabel()
        titleLabel.text = "可继续修改已生成的预测回复"
        titleLabel.font = .systemFont(ofSize: 14, weight: .medium)
        titleLabel.textColor = .secondaryLabel
        titleLabel.translatesAutoresizingMaskIntoConstraints = false

        textView.text = initialText
        textView.font = .systemFont(ofSize: 16)
        textView.textColor = .label
        textView.backgroundColor = .secondarySystemGroupedBackground
        textView.layer.cornerRadius = 14
        textView.layer.cornerCurve = .continuous
        textView.textContainerInset = UIEdgeInsets(top: 14, left: 12, bottom: 14, right: 12)
        textView.alwaysBounceVertical = true
        textView.delegate = self
        textView.translatesAutoresizingMaskIntoConstraints = false

        counterLabel.font = .monospacedDigitSystemFont(ofSize: 12, weight: .medium)
        counterLabel.textColor = .tertiaryLabel
        counterLabel.textAlignment = .right
        counterLabel.translatesAutoresizingMaskIntoConstraints = false

        view.addSubview(titleLabel)
        view.addSubview(textView)
        view.addSubview(counterLabel)

        NSLayoutConstraint.activate([
            titleLabel.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 16),
            titleLabel.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 20),
            titleLabel.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -20),

            textView.topAnchor.constraint(equalTo: titleLabel.bottomAnchor, constant: 12),
            textView.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 16),
            textView.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -16),
            textView.heightAnchor.constraint(greaterThanOrEqualToConstant: 220),
            textView.bottomAnchor.constraint(equalTo: counterLabel.topAnchor, constant: -10),

            counterLabel.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 20),
            counterLabel.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -20),
            counterLabel.bottomAnchor.constraint(lessThanOrEqualTo: view.safeAreaLayoutGuide.bottomAnchor, constant: -16)
        ])
        updateCounter()
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.25) {
            self.textView.becomeFirstResponder()
        }
    }

    func textViewDidChange(_ textView: UITextView) {
        updateCounter()
    }

    private func updateCounter() {
        counterLabel.text = "\(textView.text.count) 字"
    }

    @objc private func saveTapped() {
        onSave?(textView.text)
    }

    @objc private func cancelTapped() {
        onCancel?()
    }
}

final class AutoReplyPredictionActionSheetViewController: UIViewController {
    enum Action: CaseIterable {
        case segment
        case speech
        case edit
        case rewrite
        case expand
        case polish
        case executePlan
        case retryPlan
        case revokePlan
        case send
        case delete

        var title: String {
            switch self {
            case .segment: return "分词句"
            case .speech: return "转语音"
            case .edit: return "编辑"
            case .rewrite: return "重写"
            case .expand: return "增写"
            case .polish: return "润色"
            case .executePlan: return "执行计划"
            case .retryPlan: return "重试计划"
            case .revokePlan: return "撤销计划"
            case .send: return "发送"
            case .delete: return "删除"
            }
        }

        var subtitle: String {
            switch self {
            case .segment: return "像大爆炸一样选词"
            case .speech: return "朗读当前草稿"
            case .edit: return "手动调整内容"
            case .rewrite: return "换一种表达"
            case .expand: return "补充更多信息"
            case .polish: return "优化语气"
            case .executePlan: return "确认写入日历"
            case .retryPlan: return "失败后再次执行"
            case .revokePlan: return "撤销日历事件"
            case .send: return "确认发出"
            case .delete: return "移除草稿"
            }
        }

        var symbolName: String {
            switch self {
            case .segment: return "text.word.spacing"
            case .speech: return "speaker.wave.2.fill"
            case .edit: return "square.and.pencil"
            case .rewrite: return "arrow.triangle.2.circlepath"
            case .expand: return "text.badge.plus"
            case .polish: return "sparkles"
            case .executePlan: return "calendar.badge.plus"
            case .retryPlan: return "arrow.clockwise.circle.fill"
            case .revokePlan: return "calendar.badge.minus"
            case .send: return "paperplane.fill"
            case .delete: return "trash.fill"
            }
        }

        var tintColor: UIColor {
            switch self {
            case .send: return UIColor(red: 0.10, green: 0.56, blue: 0.31, alpha: 1)
            case .delete: return .systemRed
            case .executePlan: return UIColor(red: 0.15, green: 0.46, blue: 0.86, alpha: 1)
            case .retryPlan: return UIColor(red: 0.66, green: 0.42, blue: 0.10, alpha: 1)
            case .revokePlan: return UIColor(red: 0.78, green: 0.24, blue: 0.20, alpha: 1)
            case .rewrite, .expand, .polish: return UIColor(red: 0.62, green: 0.43, blue: 0.10, alpha: 1)
            case .speech: return .systemIndigo
            case .edit, .segment: return UIColor(red: 0.12, green: 0.39, blue: 0.86, alpha: 1)
            }
        }
    }

    var onSelect: ((Action) -> Void)?

    private let previewText: String
    private let draftStatus: String
    private let availablePlanActions: [Action]
    private let scrollView = UIScrollView()
    private let contentStack = UIStackView()

    init(previewText: String, planStatus: String, draftStatus: String = "ready") {
        let trimmed = previewText.trimmingCharacters(in: .whitespacesAndNewlines)
        self.previewText = trimmed.isEmpty ? "草稿为空，选择一个操作继续。" : trimmed
        self.draftStatus = draftStatus
        if planStatus.contains("待确认") {
            self.availablePlanActions = [.executePlan]
        } else if planStatus.contains("失败") || planStatus.contains("权限未开启") {
            self.availablePlanActions = [.retryPlan]
        } else if planStatus.contains("已写入") {
            self.availablePlanActions = [.revokePlan]
        } else {
            self.availablePlanActions = []
        }
        super.init(nibName: nil, bundle: nil)
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = UIColor.systemGroupedBackground
        configureContent()
    }

    private func configureContent() {
        scrollView.alwaysBounceVertical = true
        scrollView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(scrollView)

        contentStack.axis = .vertical
        contentStack.spacing = 14
        contentStack.translatesAutoresizingMaskIntoConstraints = false
        scrollView.addSubview(contentStack)

        NSLayoutConstraint.activate([
            scrollView.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
            scrollView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            scrollView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            scrollView.bottomAnchor.constraint(equalTo: view.bottomAnchor),

            contentStack.topAnchor.constraint(equalTo: scrollView.contentLayoutGuide.topAnchor, constant: 10),
            contentStack.leadingAnchor.constraint(equalTo: scrollView.frameLayoutGuide.leadingAnchor, constant: 16),
            contentStack.trailingAnchor.constraint(equalTo: scrollView.frameLayoutGuide.trailingAnchor, constant: -16),
            contentStack.bottomAnchor.constraint(equalTo: scrollView.contentLayoutGuide.bottomAnchor, constant: -18)
        ])

        contentStack.addArrangedSubview(makeHeaderView())
        contentStack.addArrangedSubview(makeActionSection(title: "草稿处理", actions: [.segment, .speech, .edit]))
        contentStack.addArrangedSubview(makeActionSection(title: "AI 改写", actions: [.rewrite, .expand, .polish]))
        if !availablePlanActions.isEmpty {
            contentStack.addArrangedSubview(makeActionSection(title: "计划执行", actions: availablePlanActions))
        }
        contentStack.addArrangedSubview(makePrimarySection())
    }

    private func makeHeaderView() -> UIView {
        let container = UIView()
        container.backgroundColor = .secondarySystemGroupedBackground
        container.layer.cornerRadius = 18
        container.layer.cornerCurve = .continuous
        container.translatesAutoresizingMaskIntoConstraints = false

        let iconHost = UIView()
        iconHost.backgroundColor = UIColor(red: 0.98, green: 0.79, blue: 0.27, alpha: 0.22)
        iconHost.layer.cornerRadius = 18
        iconHost.layer.cornerCurve = .continuous
        iconHost.translatesAutoresizingMaskIntoConstraints = false

        let iconView = UIImageView(image: UIImage(systemName: "sparkles"))
        iconView.tintColor = UIColor(red: 0.73, green: 0.46, blue: 0.04, alpha: 1)
        iconView.contentMode = .scaleAspectFit
        iconView.translatesAutoresizingMaskIntoConstraints = false
        iconHost.addSubview(iconView)

        let titleLabel = UILabel()
        titleLabel.text = "AI 预测草稿"
        titleLabel.font = .systemFont(ofSize: 18, weight: .bold)
        titleLabel.textColor = .label
        titleLabel.translatesAutoresizingMaskIntoConstraints = false

        let badgeLabel = AutoReplyPillLabel(insets: UIEdgeInsets(top: 4, left: 9, bottom: 4, right: 9))
        switch draftStatus {
        case "generating": badgeLabel.text = "生成中"
        case "generationFailed": badgeLabel.text = "生成失败"
        case "sending": badgeLabel.text = "发送中"
        case "sendUnconfirmed": badgeLabel.text = "等待回执"
        case "sendFailed": badgeLabel.text = "发送失败"
        default: badgeLabel.text = "待确认"
        }
        badgeLabel.font = .systemFont(ofSize: 11, weight: .bold)
        badgeLabel.textColor = UIColor(red: 0.45, green: 0.29, blue: 0.03, alpha: 1)
        badgeLabel.backgroundColor = UIColor(red: 1, green: 0.87, blue: 0.45, alpha: 0.55)
        badgeLabel.layer.cornerRadius = 10
        badgeLabel.layer.cornerCurve = .continuous
        badgeLabel.clipsToBounds = true
        badgeLabel.translatesAutoresizingMaskIntoConstraints = false

        let subtitleLabel = UILabel()
        subtitleLabel.text = "点击下方功能处理这条还未发送的回复"
        subtitleLabel.font = .systemFont(ofSize: 13, weight: .medium)
        subtitleLabel.textColor = .secondaryLabel
        subtitleLabel.translatesAutoresizingMaskIntoConstraints = false

        let previewLabel = UILabel()
        previewLabel.text = previewText
        previewLabel.font = .systemFont(ofSize: 15)
        previewLabel.textColor = .label
        previewLabel.numberOfLines = 5
        previewLabel.lineBreakMode = .byTruncatingTail
        previewLabel.translatesAutoresizingMaskIntoConstraints = false

        let previewContainer = UIView()
        previewContainer.backgroundColor = UIColor.systemBackground.withAlphaComponent(0.82)
        previewContainer.layer.cornerRadius = 14
        previewContainer.layer.cornerCurve = .continuous
        previewContainer.layer.borderWidth = 1
        previewContainer.layer.borderColor = UIColor.separator.withAlphaComponent(0.45).cgColor
        previewContainer.translatesAutoresizingMaskIntoConstraints = false
        previewContainer.addSubview(previewLabel)

        container.addSubview(iconHost)
        container.addSubview(titleLabel)
        container.addSubview(badgeLabel)
        container.addSubview(subtitleLabel)
        container.addSubview(previewContainer)

        NSLayoutConstraint.activate([
            container.heightAnchor.constraint(greaterThanOrEqualToConstant: 158),

            iconHost.topAnchor.constraint(equalTo: container.topAnchor, constant: 16),
            iconHost.leadingAnchor.constraint(equalTo: container.leadingAnchor, constant: 16),
            iconHost.widthAnchor.constraint(equalToConstant: 36),
            iconHost.heightAnchor.constraint(equalToConstant: 36),

            iconView.centerXAnchor.constraint(equalTo: iconHost.centerXAnchor),
            iconView.centerYAnchor.constraint(equalTo: iconHost.centerYAnchor),
            iconView.widthAnchor.constraint(equalToConstant: 19),
            iconView.heightAnchor.constraint(equalToConstant: 19),

            titleLabel.topAnchor.constraint(equalTo: container.topAnchor, constant: 16),
            titleLabel.leadingAnchor.constraint(equalTo: iconHost.trailingAnchor, constant: 12),
            titleLabel.trailingAnchor.constraint(lessThanOrEqualTo: badgeLabel.leadingAnchor, constant: -8),

            badgeLabel.centerYAnchor.constraint(equalTo: titleLabel.centerYAnchor),
            badgeLabel.trailingAnchor.constraint(equalTo: container.trailingAnchor, constant: -16),

            subtitleLabel.topAnchor.constraint(equalTo: titleLabel.bottomAnchor, constant: 3),
            subtitleLabel.leadingAnchor.constraint(equalTo: titleLabel.leadingAnchor),
            subtitleLabel.trailingAnchor.constraint(equalTo: container.trailingAnchor, constant: -16),

            previewContainer.topAnchor.constraint(equalTo: iconHost.bottomAnchor, constant: 14),
            previewContainer.leadingAnchor.constraint(equalTo: container.leadingAnchor, constant: 14),
            previewContainer.trailingAnchor.constraint(equalTo: container.trailingAnchor, constant: -14),
            previewContainer.bottomAnchor.constraint(equalTo: container.bottomAnchor, constant: -14),

            previewLabel.topAnchor.constraint(equalTo: previewContainer.topAnchor, constant: 12),
            previewLabel.leadingAnchor.constraint(equalTo: previewContainer.leadingAnchor, constant: 12),
            previewLabel.trailingAnchor.constraint(equalTo: previewContainer.trailingAnchor, constant: -12),
            previewLabel.bottomAnchor.constraint(equalTo: previewContainer.bottomAnchor, constant: -12)
        ])

        return container
    }

    private func makeActionSection(title: String, actions: [Action]) -> UIView {
        let sectionStack = UIStackView()
        sectionStack.axis = .vertical
        sectionStack.spacing = 8

        let titleLabel = UILabel()
        titleLabel.text = title
        titleLabel.font = .systemFont(ofSize: 13, weight: .bold)
        titleLabel.textColor = .secondaryLabel
        sectionStack.addArrangedSubview(titleLabel)

        let row = UIStackView()
        row.axis = .horizontal
        row.spacing = 10
        row.distribution = .fillEqually
        actions.forEach { row.addArrangedSubview(makeGridButton(for: $0)) }
        sectionStack.addArrangedSubview(row)
        return sectionStack
    }

    private func makePrimarySection() -> UIView {
        let stack = UIStackView()
        stack.axis = .vertical
        stack.spacing = 10

        stack.addArrangedSubview(makeWideButton(for: .send, style: .filled))
        stack.addArrangedSubview(makeWideButton(for: .delete, style: .destructive))
        return stack
    }

    private enum ButtonStyle {
        case filled
        case tinted
        case destructive
    }

    private func makeGridButton(for action: Action) -> UIButton {
        var configuration = UIButton.Configuration.filled()
        configuration.title = action.title
        configuration.subtitle = action.subtitle
        configuration.image = UIImage(systemName: action.symbolName)
        configuration.imagePlacement = .top
        configuration.imagePadding = 7
        configuration.titleAlignment = .center
        configuration.baseForegroundColor = action.tintColor
        configuration.baseBackgroundColor = action.tintColor.withAlphaComponent(0.12)
        configuration.cornerStyle = .medium
        configuration.contentInsets = NSDirectionalEdgeInsets(top: 12, leading: 8, bottom: 11, trailing: 8)

        let button = UIButton(configuration: configuration)
        button.tag = actionTag(action)
        button.addTarget(self, action: #selector(actionTapped(_:)), for: .touchUpInside)
        button.titleLabel?.numberOfLines = 1
        button.heightAnchor.constraint(equalToConstant: 88).isActive = true
        return button
    }

    private func makeWideButton(for action: Action, style: ButtonStyle) -> UIButton {
        var configuration = UIButton.Configuration.filled()
        configuration.title = action.title
        configuration.subtitle = action.subtitle
        configuration.image = UIImage(systemName: action.symbolName)
        configuration.imagePadding = 8
        configuration.cornerStyle = .medium
        configuration.contentInsets = NSDirectionalEdgeInsets(top: 10, leading: 14, bottom: 10, trailing: 14)

        switch style {
        case .filled:
            configuration.baseBackgroundColor = action.tintColor
            configuration.baseForegroundColor = .white
        case .tinted:
            configuration.baseBackgroundColor = action.tintColor.withAlphaComponent(0.13)
            configuration.baseForegroundColor = action.tintColor
        case .destructive:
            configuration.baseBackgroundColor = UIColor.systemRed.withAlphaComponent(0.11)
            configuration.baseForegroundColor = .systemRed
        }

        let button = UIButton(configuration: configuration)
        button.tag = actionTag(action)
        button.addTarget(self, action: #selector(actionTapped(_:)), for: .touchUpInside)
        button.heightAnchor.constraint(equalToConstant: 58).isActive = true
        return button
    }

    @objc private func actionTapped(_ sender: UIButton) {
        guard let action = action(for: sender.tag) else { return }
        onSelect?(action)
    }

    private func actionTag(_ action: Action) -> Int {
        switch action {
        case .segment: return 1
        case .speech: return 2
        case .edit: return 3
        case .rewrite: return 4
        case .expand: return 5
        case .polish: return 6
        case .executePlan: return 7
        case .retryPlan: return 8
        case .revokePlan: return 9
        case .send: return 10
        case .delete: return 11
        }
    }

    private func action(for tag: Int) -> Action? {
        switch tag {
        case 1: return .segment
        case 2: return .speech
        case 3: return .edit
        case 4: return .rewrite
        case 5: return .expand
        case 6: return .polish
        case 7: return .executePlan
        case 8: return .retryPlan
        case 9: return .revokePlan
        case 10: return .send
        case 11: return .delete
        default: return nil
        }
    }
}

private final class AutoReplyPillLabel: UILabel {
    private let insets: UIEdgeInsets

    init(insets: UIEdgeInsets) {
        self.insets = insets
        super.init(frame: .zero)
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func drawText(in rect: CGRect) {
        super.drawText(in: rect.inset(by: insets))
    }

    override var intrinsicContentSize: CGSize {
        let size = super.intrinsicContentSize
        return CGSize(width: size.width + insets.left + insets.right, height: size.height + insets.top + insets.bottom)
    }
}

final class DraftSegmentSelectionViewController: UIViewController, UICollectionViewDataSource, UICollectionViewDelegateFlowLayout {
    var onReplace: ((String) -> Void)?
    var onAppend: ((String) -> Void)?
    var onCopy: ((String) -> Void)?
    var onClose: (() -> Void)?

    private let segments: [String]
    private var selectedIndexes = Set<Int>()
    private let collectionView: UICollectionView
    private let previewLabel = UILabel()
    private let replaceButton = UIButton(type: .system)
    private let appendButton = UIButton(type: .system)
    private let copyButton = UIButton(type: .system)

    init(segments: [String]) {
        self.segments = segments
        let layout = UICollectionViewFlowLayout()
        layout.minimumInteritemSpacing = 8
        layout.minimumLineSpacing = 10
        layout.sectionInset = UIEdgeInsets(top: 12, left: 16, bottom: 18, right: 16)
        collectionView = UICollectionView(frame: .zero, collectionViewLayout: layout)
        super.init(nibName: nil, bundle: nil)
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        title = "分词句选中"
        view.backgroundColor = UIColor.systemGroupedBackground
        navigationItem.leftBarButtonItem = UIBarButtonItem(title: "关闭", style: .plain, target: self, action: #selector(closeTapped))

        let descriptionLabel = UILabel()
        descriptionLabel.text = "点选词句块后，可以替换、追加或复制到剪贴板。"
        descriptionLabel.font = .systemFont(ofSize: 13, weight: .medium)
        descriptionLabel.textColor = .secondaryLabel
        descriptionLabel.numberOfLines = 0
        descriptionLabel.translatesAutoresizingMaskIntoConstraints = false

        previewLabel.font = .systemFont(ofSize: 15, weight: .semibold)
        previewLabel.textColor = .label
        previewLabel.numberOfLines = 3
        previewLabel.backgroundColor = .secondarySystemGroupedBackground
        previewLabel.layer.cornerRadius = 12
        previewLabel.layer.cornerCurve = .continuous
        previewLabel.clipsToBounds = true
        previewLabel.translatesAutoresizingMaskIntoConstraints = false

        collectionView.backgroundColor = .clear
        collectionView.dataSource = self
        collectionView.delegate = self
        collectionView.allowsMultipleSelection = true
        collectionView.register(DraftSegmentChipCell.self, forCellWithReuseIdentifier: DraftSegmentChipCell.reuseIdentifier)
        collectionView.translatesAutoresizingMaskIntoConstraints = false

        let buttonStack = UIStackView(arrangedSubviews: [replaceButton, appendButton, copyButton])
        buttonStack.axis = .horizontal
        buttonStack.spacing = 10
        buttonStack.distribution = .fillEqually
        buttonStack.translatesAutoresizingMaskIntoConstraints = false

        configureActionButton(replaceButton, title: "替换草稿", color: .systemBlue, action: #selector(replaceTapped))
        configureActionButton(appendButton, title: "追加", color: .systemGreen, action: #selector(appendTapped))
        configureActionButton(copyButton, title: "复制", color: .systemGray, action: #selector(copyTapped))

        view.addSubview(descriptionLabel)
        view.addSubview(previewLabel)
        view.addSubview(collectionView)
        view.addSubview(buttonStack)

        NSLayoutConstraint.activate([
            descriptionLabel.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 14),
            descriptionLabel.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 20),
            descriptionLabel.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -20),

            previewLabel.topAnchor.constraint(equalTo: descriptionLabel.bottomAnchor, constant: 12),
            previewLabel.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 16),
            previewLabel.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -16),
            previewLabel.heightAnchor.constraint(greaterThanOrEqualToConstant: 52),

            collectionView.topAnchor.constraint(equalTo: previewLabel.bottomAnchor, constant: 10),
            collectionView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            collectionView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            collectionView.bottomAnchor.constraint(equalTo: buttonStack.topAnchor, constant: -12),

            buttonStack.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 16),
            buttonStack.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -16),
            buttonStack.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor, constant: -14),
            buttonStack.heightAnchor.constraint(equalToConstant: 44)
        ])

        if !segments.isEmpty {
            selectedIndexes.insert(0)
        }
        updatePreview()
    }

    func collectionView(_ collectionView: UICollectionView, numberOfItemsInSection section: Int) -> Int {
        segments.count
    }

    func collectionView(_ collectionView: UICollectionView, cellForItemAt indexPath: IndexPath) -> UICollectionViewCell {
        let cell = collectionView.dequeueReusableCell(
            withReuseIdentifier: DraftSegmentChipCell.reuseIdentifier,
            for: indexPath
        ) as? DraftSegmentChipCell ?? DraftSegmentChipCell()
        cell.configure(text: segments[indexPath.item], isPicked: selectedIndexes.contains(indexPath.item))
        return cell
    }

    func collectionView(_ collectionView: UICollectionView, didSelectItemAt indexPath: IndexPath) {
        if selectedIndexes.contains(indexPath.item) {
            selectedIndexes.remove(indexPath.item)
        } else {
            selectedIndexes.insert(indexPath.item)
        }
        collectionView.reloadItems(at: [indexPath])
        updatePreview()
    }

    func collectionView(
        _ collectionView: UICollectionView,
        layout collectionViewLayout: UICollectionViewLayout,
        sizeForItemAt indexPath: IndexPath
    ) -> CGSize {
        let maxWidth = max(120, collectionView.bounds.width - 32)
        let text = segments[indexPath.item] as NSString
        let bounding = text.boundingRect(
            with: CGSize(width: maxWidth - 28, height: 200),
            options: [.usesLineFragmentOrigin, .usesFontLeading],
            attributes: [.font: UIFont.systemFont(ofSize: 15, weight: .semibold)],
            context: nil
        )
        return CGSize(width: min(maxWidth, ceil(bounding.width) + 28), height: min(86, max(36, ceil(bounding.height) + 16)))
    }

    private func configureActionButton(_ button: UIButton, title: String, color: UIColor, action: Selector) {
        button.setTitle(title, for: .normal)
        button.titleLabel?.font = .systemFont(ofSize: 15, weight: .semibold)
        button.tintColor = .white
        button.backgroundColor = color
        button.layer.cornerRadius = 12
        button.layer.cornerCurve = .continuous
        button.addTarget(self, action: action, for: .touchUpInside)
    }

    private func selectedText() -> String {
        selectedIndexes.sorted().map { segments[$0] }.joined(separator: " ")
    }

    private func updatePreview() {
        let text = selectedText()
        previewLabel.text = text.isEmpty ? "  未选择内容" : "  \(text)"
        let hasSelection = !text.isEmpty
        replaceButton.isEnabled = hasSelection
        appendButton.isEnabled = hasSelection
        copyButton.isEnabled = hasSelection
        [replaceButton, appendButton, copyButton].forEach { $0.alpha = hasSelection ? 1 : 0.45 }
    }

    @objc private func replaceTapped() {
        onReplace?(selectedText())
    }

    @objc private func appendTapped() {
        onAppend?(selectedText())
    }

    @objc private func copyTapped() {
        onCopy?(selectedText())
    }

    @objc private func closeTapped() {
        onClose?()
    }
}

private final class DraftSegmentChipCell: UICollectionViewCell {
    static let reuseIdentifier = "DraftSegmentChipCell"
    private let label = UILabel()

    override init(frame: CGRect) {
        super.init(frame: frame)
        contentView.layer.cornerRadius = 13
        contentView.layer.cornerCurve = .continuous
        contentView.layer.borderWidth = 1
        label.font = .systemFont(ofSize: 15, weight: .semibold)
        label.numberOfLines = 2
        label.textAlignment = .center
        label.translatesAutoresizingMaskIntoConstraints = false
        contentView.addSubview(label)
        NSLayoutConstraint.activate([
            label.topAnchor.constraint(equalTo: contentView.topAnchor, constant: 8),
            label.leadingAnchor.constraint(equalTo: contentView.leadingAnchor, constant: 12),
            label.trailingAnchor.constraint(equalTo: contentView.trailingAnchor, constant: -12),
            label.bottomAnchor.constraint(equalTo: contentView.bottomAnchor, constant: -8)
        ])
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    func configure(text: String, isPicked: Bool) {
        label.text = text
        label.textColor = isPicked ? .white : .label
        contentView.backgroundColor = isPicked ? UIColor.systemBlue : UIColor.secondarySystemGroupedBackground
        contentView.layer.borderColor = (isPicked ? UIColor.systemBlue : UIColor.separator).cgColor
    }
}
