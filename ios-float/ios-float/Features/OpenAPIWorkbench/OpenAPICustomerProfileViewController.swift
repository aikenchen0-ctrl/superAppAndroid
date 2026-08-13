import UIKit

final class OpenApiCustomerProfileViewController: UIViewController {
    private let ownerWxid: String
    private let contact: ChatParticipant?
    private let context: OpenApiConversationContext?
    private let scrollView = UIScrollView()
    private let contentStack = UIStackView()
    private var profile: OpenApiCustomerProfile
    private var requestTask: Task<Void, Never>?
    private var avatarLoadTask: MediaThumbnailRequest?

    init(ownerWxid: String, contact: ChatParticipant?, context: OpenApiConversationContext?) {
        self.ownerWxid = ownerWxid
        self.contact = contact
        self.context = context
        self.profile = OpenApiCustomerProfile(context: context)
        super.init(nibName: nil, bundle: nil)
        title = "客户档案"
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .systemGroupedBackground
        navigationItem.rightBarButtonItem = UIBarButtonItem(title: "编辑", style: .plain, target: self, action: #selector(editProfile))
        configureLayout()
        reloadContent()
        loadProfile()
    }

    deinit {
        requestTask?.cancel()
        avatarLoadTask?.cancel()
    }

    private func configureLayout() {
        scrollView.alwaysBounceVertical = true
        scrollView.translatesAutoresizingMaskIntoConstraints = false
        contentStack.axis = .vertical
        contentStack.spacing = 12
        contentStack.translatesAutoresizingMaskIntoConstraints = false

        view.addSubview(scrollView)
        scrollView.addSubview(contentStack)
        NSLayoutConstraint.activate([
            scrollView.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
            scrollView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            scrollView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            scrollView.bottomAnchor.constraint(equalTo: view.bottomAnchor),
            contentStack.topAnchor.constraint(equalTo: scrollView.contentLayoutGuide.topAnchor, constant: 14),
            contentStack.leadingAnchor.constraint(equalTo: scrollView.frameLayoutGuide.leadingAnchor, constant: 16),
            contentStack.trailingAnchor.constraint(equalTo: scrollView.frameLayoutGuide.trailingAnchor, constant: -16),
            contentStack.bottomAnchor.constraint(equalTo: scrollView.contentLayoutGuide.bottomAnchor, constant: -20)
        ])

    }

    private func reloadContent() {
        contentStack.arrangedSubviews.forEach {
            contentStack.removeArrangedSubview($0)
            $0.removeFromSuperview()
        }
        contentStack.addArrangedSubview(makeProfileHeaderCard())
        contentStack.addArrangedSubview(makeMetricGridCard())
        contentStack.addArrangedSubview(makeTagsCard())
        contentStack.addArrangedSubview(makeTimelineCard())
    }

    private func makeProfileHeaderCard() -> UIView {
        let card = makeCard()
        let contactName = contact?.displayName
            ?? context?.displayName
            ?? context?.wxid
            ?? "未知联系人"

        let avatar = UIView()
        avatar.backgroundColor = contact?.tintColor ?? UIColor(red: 0.12, green: 0.47, blue: 0.42, alpha: 1)
        avatar.layer.cornerRadius = 24
        avatar.layer.cornerCurve = .continuous
        avatar.clipsToBounds = true
        avatar.translatesAutoresizingMaskIntoConstraints = false
        let avatarFallback = UILabel()
        avatarFallback.text = String(contactName.prefix(1))
        avatarFallback.textAlignment = .center
        avatarFallback.textColor = .white
        avatarFallback.font = .systemFont(ofSize: 24, weight: .bold)
        avatarFallback.translatesAutoresizingMaskIntoConstraints = false
        let avatarImage = UIImageView()
        avatarImage.contentMode = .scaleAspectFill
        avatarImage.clipsToBounds = true
        avatarImage.isHidden = true
        avatarImage.translatesAutoresizingMaskIntoConstraints = false
        avatar.addSubview(avatarFallback)
        avatar.addSubview(avatarImage)

        let nameLabel = UILabel()
        nameLabel.text = contactName
        nameLabel.font = .systemFont(ofSize: 22, weight: .bold)
        nameLabel.textColor = .label

        let subtitleLabel = UILabel()
        let identityParts = [
            context?.friendNo.isEmpty == false ? "微信号：\(context?.friendNo ?? "")" : "",
            context?.wxid.isEmpty == false ? "WXID：\(context?.wxid ?? "")" : ""
        ].filter { !$0.isEmpty }
        subtitleLabel.text = identityParts.joined(separator: "\n")
        subtitleLabel.font = .systemFont(ofSize: 13, weight: .medium)
        subtitleLabel.textColor = .secondaryLabel
        subtitleLabel.numberOfLines = 2

        let nameStack = UIStackView(arrangedSubviews: [nameLabel, subtitleLabel])
        nameStack.axis = .vertical
        nameStack.spacing = 4
        nameStack.translatesAutoresizingMaskIntoConstraints = false

        let levelPill = makePill(profile.customerLevel.isEmpty ? "未分级" : "\(profile.customerLevel)级客户", color: UIColor(red: 0.10, green: 0.58, blue: 0.36, alpha: 1))
        levelPill.translatesAutoresizingMaskIntoConstraints = false

        card.addSubview(avatar)
        card.addSubview(nameStack)
        card.addSubview(levelPill)
        NSLayoutConstraint.activate([
            avatar.topAnchor.constraint(equalTo: card.topAnchor, constant: 18),
            avatar.leadingAnchor.constraint(equalTo: card.leadingAnchor, constant: 16),
            avatar.widthAnchor.constraint(equalToConstant: 48),
            avatar.heightAnchor.constraint(equalToConstant: 48),
            avatarFallback.topAnchor.constraint(equalTo: avatar.topAnchor),
            avatarFallback.leadingAnchor.constraint(equalTo: avatar.leadingAnchor),
            avatarFallback.trailingAnchor.constraint(equalTo: avatar.trailingAnchor),
            avatarFallback.bottomAnchor.constraint(equalTo: avatar.bottomAnchor),
            avatarImage.topAnchor.constraint(equalTo: avatar.topAnchor),
            avatarImage.leadingAnchor.constraint(equalTo: avatar.leadingAnchor),
            avatarImage.trailingAnchor.constraint(equalTo: avatar.trailingAnchor),
            avatarImage.bottomAnchor.constraint(equalTo: avatar.bottomAnchor),
            levelPill.topAnchor.constraint(equalTo: card.topAnchor, constant: 18),
            levelPill.trailingAnchor.constraint(equalTo: card.trailingAnchor, constant: -16),
            nameStack.topAnchor.constraint(equalTo: avatar.topAnchor, constant: 1),
            nameStack.leadingAnchor.constraint(equalTo: avatar.trailingAnchor, constant: 12),
            nameStack.trailingAnchor.constraint(lessThanOrEqualTo: levelPill.leadingAnchor, constant: -10),
            avatar.bottomAnchor.constraint(equalTo: card.bottomAnchor, constant: -18)
        ])
        loadAvatarIfNeeded(into: avatarImage, fallback: avatarFallback)
        return card
    }

    private func loadAvatarIfNeeded(into imageView: UIImageView, fallback: UILabel) {
        let url = contact?.avatarURL ?? context.flatMap { OpenApiDisplay.avatarURL(from: $0.avatarURL) }
        guard let url else { return }
        let targetSize = CGSize(width: 48, height: 48)
        if let cached = AvatarPipeline.shared.cachedImage(for: url, targetSize: targetSize) {
            imageView.image = cached
            imageView.isHidden = false
            fallback.isHidden = true
            return
        }
        avatarLoadTask = AvatarPipeline.shared.load(url, targetSize: targetSize) { [weak imageView, weak fallback] image in
            imageView?.image = image
            imageView?.isHidden = image == nil
            fallback?.isHidden = image != nil
        }
    }

    private func makeMetricGridCard() -> UIView {
        let card = makeCard()
        let title = sectionTitle("客户概览")
        title.translatesAutoresizingMaskIntoConstraints = false

        let firstRow = UIStackView(arrangedSubviews: [
            metricView(value: profile.sourceChannel.ifEmpty("未设置"), title: "来源渠道", symbol: "arrow.triangle.branch", color: .systemOrange),
            metricView(value: profile.profileKey.ifEmpty("未设置"), title: "画像标识", symbol: "person.text.rectangle", color: .systemBlue)
        ])
        let secondRow = UIStackView(arrangedSubviews: [
            metricView(value: profile.phone.ifEmpty("未设置"), title: "联系电话", symbol: "phone.fill", color: .systemPurple),
            metricView(value: profile.sourceDetail.ifEmpty("未设置"), title: "来源详情", symbol: "info.circle.fill", color: .systemGreen)
        ])
        [firstRow, secondRow].forEach {
            $0.axis = .horizontal
            $0.spacing = 10
            $0.distribution = .fillEqually
        }
        let rows = UIStackView(arrangedSubviews: [firstRow, secondRow])
        rows.axis = .vertical
        rows.spacing = 10
        rows.translatesAutoresizingMaskIntoConstraints = false

        card.addSubview(title)
        card.addSubview(rows)
        NSLayoutConstraint.activate([
            title.topAnchor.constraint(equalTo: card.topAnchor, constant: 16),
            title.leadingAnchor.constraint(equalTo: card.leadingAnchor, constant: 16),
            title.trailingAnchor.constraint(equalTo: card.trailingAnchor, constant: -16),
            rows.topAnchor.constraint(equalTo: title.bottomAnchor, constant: 12),
            rows.leadingAnchor.constraint(equalTo: card.leadingAnchor, constant: 16),
            rows.trailingAnchor.constraint(equalTo: card.trailingAnchor, constant: -16),
            rows.bottomAnchor.constraint(equalTo: card.bottomAnchor, constant: -16)
        ])
        return card
    }

    private func makeTagsCard() -> UIView {
        let card = makeCard()
        let title = sectionTitle("画像标签映射（非微信联系人标签）")
        title.translatesAutoresizingMaskIntoConstraints = false
        let tags = profile.mappedLabelNames.isEmpty ? ["暂无标签"] : profile.mappedLabelNames
        let tagStack = UIStackView()
        tagStack.axis = .vertical
        tagStack.spacing = 8
        tagStack.translatesAutoresizingMaskIntoConstraints = false
        for rowTags in stride(from: 0, to: tags.count, by: 2).map({ Array(tags[$0..<min($0 + 2, tags.count)]) }) {
            let row = UIStackView()
            row.axis = .horizontal
            row.spacing = 8
            row.distribution = .fillProportionally
            rowTags.forEach { row.addArrangedSubview(makePill($0, color: UIColor(red: 0.12, green: 0.39, blue: 0.86, alpha: 1))) }
            tagStack.addArrangedSubview(row)
        }
        card.addSubview(title)
        card.addSubview(tagStack)
        NSLayoutConstraint.activate([
            title.topAnchor.constraint(equalTo: card.topAnchor, constant: 16),
            title.leadingAnchor.constraint(equalTo: card.leadingAnchor, constant: 16),
            title.trailingAnchor.constraint(equalTo: card.trailingAnchor, constant: -16),
            tagStack.topAnchor.constraint(equalTo: title.bottomAnchor, constant: 12),
            tagStack.leadingAnchor.constraint(equalTo: card.leadingAnchor, constant: 16),
            tagStack.trailingAnchor.constraint(lessThanOrEqualTo: card.trailingAnchor, constant: -16),
            tagStack.bottomAnchor.constraint(equalTo: card.bottomAnchor, constant: -16)
        ])
        return card
    }

    private func makeTimelineCard() -> UIView {
        let card = makeCard()
        let title = sectionTitle("最近动态")
        title.translatesAutoresizingMaskIntoConstraints = false
        let stack = UIStackView(arrangedSubviews: [
            timelineRow(symbol: "cart.fill", title: "购买记录", subtitle: profile.purchaseHistory.ifEmpty("暂无购买记录")),
            timelineRow(symbol: "person.2.fill", title: "社交帐号", subtitle: profile.socialAccounts.ifEmpty("暂无社交帐号")),
            timelineRow(symbol: "note.text", title: "档案备注", subtitle: profile.notes.ifEmpty("暂无备注"))
        ])
        stack.axis = .vertical
        stack.spacing = 12
        stack.translatesAutoresizingMaskIntoConstraints = false
        card.addSubview(title)
        card.addSubview(stack)
        NSLayoutConstraint.activate([
            title.topAnchor.constraint(equalTo: card.topAnchor, constant: 16),
            title.leadingAnchor.constraint(equalTo: card.leadingAnchor, constant: 16),
            title.trailingAnchor.constraint(equalTo: card.trailingAnchor, constant: -16),
            stack.topAnchor.constraint(equalTo: title.bottomAnchor, constant: 14),
            stack.leadingAnchor.constraint(equalTo: card.leadingAnchor, constant: 16),
            stack.trailingAnchor.constraint(equalTo: card.trailingAnchor, constant: -16),
            stack.bottomAnchor.constraint(equalTo: card.bottomAnchor, constant: -16)
        ])
        return card
    }

    private func makeSuggestionCard() -> UIView {
        let card = makeCard()
        let title = sectionTitle("跟进建议")
        title.translatesAutoresizingMaskIntoConstraints = false
        let body = UILabel()
        body.text = profile.notes.isEmpty
            ? "客户档案尚未填写备注。保存来源、标签、购买记录和备注后，可据此生成跟进话术。"
            : profile.notes
        body.font = .systemFont(ofSize: 15)
        body.textColor = .label
        body.numberOfLines = 0
        body.translatesAutoresizingMaskIntoConstraints = false
        let button = UIButton(type: .system)
        button.setTitle("生成跟进话术", for: .normal)
        button.setImage(UIImage(systemName: "sparkles"), for: .normal)
        button.titleLabel?.font = .systemFont(ofSize: 15, weight: .semibold)
        button.tintColor = .white
        button.backgroundColor = UIColor(red: 0.12, green: 0.47, blue: 0.42, alpha: 1)
        button.layer.cornerRadius = 12
        button.layer.cornerCurve = .continuous
        button.translatesAutoresizingMaskIntoConstraints = false
        button.addTarget(self, action: #selector(generateFollowUp), for: .touchUpInside)
        card.addSubview(title)
        card.addSubview(body)
        card.addSubview(button)
        NSLayoutConstraint.activate([
            title.topAnchor.constraint(equalTo: card.topAnchor, constant: 16),
            title.leadingAnchor.constraint(equalTo: card.leadingAnchor, constant: 16),
            title.trailingAnchor.constraint(equalTo: card.trailingAnchor, constant: -16),
            body.topAnchor.constraint(equalTo: title.bottomAnchor, constant: 10),
            body.leadingAnchor.constraint(equalTo: title.leadingAnchor),
            body.trailingAnchor.constraint(equalTo: title.trailingAnchor),
            button.topAnchor.constraint(equalTo: body.bottomAnchor, constant: 14),
            button.leadingAnchor.constraint(equalTo: title.leadingAnchor),
            button.trailingAnchor.constraint(equalTo: title.trailingAnchor),
            button.bottomAnchor.constraint(equalTo: card.bottomAnchor, constant: -16),
            button.heightAnchor.constraint(equalToConstant: 46)
        ])
        return card
    }

    private func makeCard() -> UIView {
        let card = UIView()
        card.backgroundColor = .secondarySystemGroupedBackground
        card.layer.cornerRadius = 14
        card.layer.cornerCurve = .continuous
        return card
    }

    private func sectionTitle(_ text: String) -> UILabel {
        let label = UILabel()
        label.text = text
        label.font = .systemFont(ofSize: 17, weight: .bold)
        label.textColor = .label
        return label
    }

    private func makeQuickAction(symbol: String, title: String) -> UIButton {
        var configuration = UIButton.Configuration.filled()
        configuration.image = UIImage(systemName: symbol)
        configuration.title = title
        configuration.imagePlacement = .top
        configuration.imagePadding = 5
        configuration.baseForegroundColor = UIColor(red: 0.12, green: 0.47, blue: 0.42, alpha: 1)
        configuration.baseBackgroundColor = UIColor(red: 0.12, green: 0.47, blue: 0.42, alpha: 0.10)
        configuration.cornerStyle = .medium
        let button = UIButton(configuration: configuration)
        button.titleLabel?.font = .systemFont(ofSize: 12, weight: .semibold)
        return button
    }

    private func metricView(value: String, title: String, symbol: String, color: UIColor) -> UIView {
        let view = UIView()
        view.backgroundColor = .tertiarySystemGroupedBackground
        view.layer.cornerRadius = 12
        view.layer.cornerCurve = .continuous
        let icon = UIImageView(image: UIImage(systemName: symbol))
        icon.tintColor = color
        icon.contentMode = .scaleAspectFit
        icon.translatesAutoresizingMaskIntoConstraints = false
        let valueLabel = UILabel()
        valueLabel.text = value
        valueLabel.font = .systemFont(ofSize: 18, weight: .bold)
        valueLabel.textColor = .label
        let titleLabel = UILabel()
        titleLabel.text = title
        titleLabel.font = .systemFont(ofSize: 12, weight: .medium)
        titleLabel.textColor = .secondaryLabel
        let stack = UIStackView(arrangedSubviews: [valueLabel, titleLabel])
        stack.axis = .vertical
        stack.spacing = 2
        stack.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(icon)
        view.addSubview(stack)
        NSLayoutConstraint.activate([
            view.heightAnchor.constraint(equalToConstant: 74),
            icon.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 12),
            icon.centerYAnchor.constraint(equalTo: view.centerYAnchor),
            icon.widthAnchor.constraint(equalToConstant: 22),
            icon.heightAnchor.constraint(equalToConstant: 22),
            stack.leadingAnchor.constraint(equalTo: icon.trailingAnchor, constant: 10),
            stack.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -10),
            stack.centerYAnchor.constraint(equalTo: view.centerYAnchor)
        ])
        return view
    }

    private func makePill(_ text: String, color: UIColor) -> UILabel {
        let label = UILabel()
        label.text = text
        label.font = .systemFont(ofSize: 12, weight: .bold)
        label.textColor = color
        label.backgroundColor = color.withAlphaComponent(0.12)
        label.textAlignment = .center
        label.layer.cornerRadius = 12
        label.layer.cornerCurve = .continuous
        label.clipsToBounds = true
        label.widthAnchor.constraint(greaterThanOrEqualToConstant: 76).isActive = true
        label.heightAnchor.constraint(equalToConstant: 26).isActive = true
        return label
    }

    private func timelineRow(symbol: String, title: String, subtitle: String) -> UIView {
        let row = UIView()
        let icon = UIImageView(image: UIImage(systemName: symbol))
        icon.tintColor = UIColor(red: 0.12, green: 0.47, blue: 0.42, alpha: 1)
        icon.backgroundColor = UIColor(red: 0.12, green: 0.47, blue: 0.42, alpha: 0.10)
        icon.layer.cornerRadius = 15
        icon.layer.cornerCurve = .continuous
        icon.contentMode = .center
        icon.translatesAutoresizingMaskIntoConstraints = false
        let titleLabel = UILabel()
        titleLabel.text = title
        titleLabel.font = .systemFont(ofSize: 15, weight: .semibold)
        titleLabel.textColor = .label
        let subtitleLabel = UILabel()
        subtitleLabel.text = subtitle
        subtitleLabel.font = .systemFont(ofSize: 13)
        subtitleLabel.textColor = .secondaryLabel
        subtitleLabel.numberOfLines = 2
        let stack = UIStackView(arrangedSubviews: [titleLabel, subtitleLabel])
        stack.axis = .vertical
        stack.spacing = 3
        stack.translatesAutoresizingMaskIntoConstraints = false
        row.addSubview(icon)
        row.addSubview(stack)
        NSLayoutConstraint.activate([
            row.heightAnchor.constraint(greaterThanOrEqualToConstant: 42),
            icon.leadingAnchor.constraint(equalTo: row.leadingAnchor),
            icon.topAnchor.constraint(equalTo: row.topAnchor, constant: 2),
            icon.widthAnchor.constraint(equalToConstant: 30),
            icon.heightAnchor.constraint(equalToConstant: 30),
            stack.leadingAnchor.constraint(equalTo: icon.trailingAnchor, constant: 10),
            stack.trailingAnchor.constraint(equalTo: row.trailingAnchor),
            stack.topAnchor.constraint(equalTo: row.topAnchor),
            stack.bottomAnchor.constraint(equalTo: row.bottomAnchor)
        ])
        return row
    }

    @objc private func editProfile() {
        guard let context, !context.backendID.isEmpty else {
            showError("当前联系人没有后端 contactId，无法保存客户档案。请先完成联系人同步。")
            return
        }
        let alert = UIAlertController(title: "编辑客户档案", message: "保存后会通过真实 OpenAPI 更新当前联系人档案。", preferredStyle: .alert)
        [
            ("客户等级", profile.customerLevel),
            ("来源渠道", profile.sourceChannel),
            ("来源详情", profile.sourceDetail),
            ("画像标识", profile.profileKey),
            ("购买记录", profile.purchaseHistory),
            ("社交帐号", profile.socialAccounts),
            ("备注", profile.notes),
            ("画像标签映射（逗号分隔）", profile.mappedLabelNames.joined(separator: ","))
        ].forEach { title, value in
            alert.addTextField { field in
                field.placeholder = title
                field.text = value
                field.clearButtonMode = .whileEditing
            }
        }
        alert.addAction(UIAlertAction(title: "取消", style: .cancel))
        alert.addAction(UIAlertAction(title: "保存", style: .default) { [weak self, weak alert] _ in
            guard let self, let fields = alert?.textFields, fields.count == 8 else { return }
            let values = fields.map { $0.text?.trimmingCharacters(in: .whitespacesAndNewlines) ?? "" }
            let labels = values[7].split(separator: ",").map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }.filter { !$0.isEmpty }
            let updated = OpenApiCustomerProfile(
                customerLevel: values[0], sourceChannel: values[1], sourceDetail: values[2], profileKey: values[3],
                purchaseHistory: values[4], socialAccounts: values[5], notes: values[6], phone: self.profile.phone,
                mappedLabelNames: labels
            )
            self.saveProfile(updated)
        })
        present(alert, animated: true)
    }

    private func loadProfile() {
        guard let context, !context.backendID.isEmpty else {
            showError("当前联系人没有后端 contactId，无法读取客户档案。请先完成联系人同步。")
            return
        }
        showCircularPageLoading(title: "加载客户档案")
        requestTask?.cancel()
        requestTask = Task { [weak self] in
            do {
                let profile = try await OpenApiCustomerProfileService.load(
                    contactID: context.backendID,
                    friendID: context.wxid,
                    weChatID: ownerWxid
                )
                guard !Task.isCancelled else { return }
                await MainActor.run {
                    self?.profile = profile
                    self?.reloadContent()
                }
            } catch {
                guard !Task.isCancelled else { return }
                await MainActor.run { self?.showError("客户档案读取失败：\(error.localizedDescription)") }
            }
        }
    }

    private func saveProfile(_ profile: OpenApiCustomerProfile) {
        guard let context else { return }
        showCircularPageLoading(title: "保存客户档案")
        requestTask?.cancel()
        requestTask = Task { [weak self] in
            do {
                try await OpenApiCustomerProfileService.save(
                    profile,
                    contactID: context.backendID,
                    friendID: context.wxid,
                    weChatID: ownerWxid
                )
                guard !Task.isCancelled else { return }
                let persisted = try await OpenApiCustomerProfileService.load(
                    contactID: context.backendID,
                    friendID: context.wxid,
                    weChatID: ownerWxid
                )
                guard !Task.isCancelled else { return }
                await MainActor.run {
                    guard let self else { return }
                    self.profile = persisted
                    self.reloadContent()
                    if persisted == profile {
                        self.showError("客户档案已保存并回读确认。画像标签映射只保存在客户档案中，不会创建或修改微信联系人标签。")
                    } else {
                        self.showError("保存接口已返回，但回读内容与提交内容不一致，后端可能忽略了部分字段。请以当前页面重新显示的内容为准。")
                    }
                }
            } catch {
                guard !Task.isCancelled else { return }
                await MainActor.run { self?.showError("客户档案保存失败：\(error.localizedDescription)") }
            }
        }
    }

    private func showError(_ message: String) {
        let alert = UIAlertController(title: "客户档案", message: message, preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "知道了", style: .default))
        present(alert, animated: true)
    }

    @objc private func generateFollowUp() {
        let alert = UIAlertController(title: "AI 跟进话术", message: "您好，今晚门店活动还有预约名额，我把时间、地点和权益发您，您方便的话可以直接点链接预约。", preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "知道了", style: .default))
        present(alert, animated: true)
    }
}

struct OpenApiCustomerProfile: Hashable {
    let customerLevel: String
    let sourceChannel: String
    let sourceDetail: String
    let profileKey: String
    let purchaseHistory: String
    let socialAccounts: String
    let notes: String
    let phone: String
    let mappedLabelNames: [String]

    init(
        customerLevel: String = "",
        sourceChannel: String = "",
        sourceDetail: String = "",
        profileKey: String = "",
        purchaseHistory: String = "",
        socialAccounts: String = "",
        notes: String = "",
        phone: String = "",
        mappedLabelNames: [String] = []
    ) {
        self.customerLevel = customerLevel
        self.sourceChannel = sourceChannel
        self.sourceDetail = sourceDetail
        self.profileKey = profileKey
        self.purchaseHistory = purchaseHistory
        self.socialAccounts = socialAccounts
        self.notes = notes
        self.phone = phone
        self.mappedLabelNames = mappedLabelNames
    }

    init(context: OpenApiConversationContext?) {
        self.init(
            customerLevel: context?.customerLevel ?? "",
            sourceChannel: context?.sourceChannel ?? "",
            profileKey: context?.profileKey ?? "",
            notes: context?.notes ?? "",
            phone: context?.phone ?? ""
        )
    }
}

enum OpenApiCustomerProfileService {
    typealias Request = (String, String, [URLQueryItem], Any?) async throws -> OpenApiHTTPResult
    static let liveRequest: Request = { method, path, query, body in
        try await OpenApiHTTPClient.request(method, path: path, query: query, body: body)
    }

    static func load(
        contactID: String,
        friendID: String = "",
        weChatID: String,
        request: Request = liveRequest
    ) async throws -> OpenApiCustomerProfile {
        do {
            return try await load(contactID: contactID, weChatID: weChatID, request: request)
        } catch OpenApiHTTPClient.ClientError.badStatus(let status, _) where status == 404 && !friendID.isEmpty {
            guard let latestID = try await resolveContactID(friendID: friendID, weChatID: weChatID, request: request) else {
                throw OpenApiSocialManagementError.friendNotFound(friendID)
            }
            return try await load(contactID: latestID, weChatID: weChatID, request: request)
        }
    }

    static func save(
        _ profile: OpenApiCustomerProfile,
        contactID: String,
        friendID: String = "",
        weChatID: String,
        request: Request = liveRequest
    ) async throws {
        do {
            try await save(profile, contactID: contactID, weChatID: weChatID, request: request)
        } catch OpenApiHTTPClient.ClientError.badStatus(let status, _) where status == 404 && !friendID.isEmpty {
            guard let latestID = try await resolveContactID(friendID: friendID, weChatID: weChatID, request: request) else {
                throw OpenApiSocialManagementError.friendNotFound(friendID)
            }
            try await save(profile, contactID: latestID, weChatID: weChatID, request: request)
        }
    }

    private static func load(contactID: String, weChatID: String, request: Request) async throws -> OpenApiCustomerProfile {
        let result = try await request(
            "GET", "/openapi/v1/contacts/\(validatedID(contactID))/customer-profile",
            [URLQueryItem(name: "weChatId", value: weChatID)], nil
        )
        return profile(from: result.jsonObject)
    }

    private static func save(_ profile: OpenApiCustomerProfile, contactID: String, weChatID: String, request: Request) async throws {
        _ = try await request(
            "PUT",
            "/openapi/v1/contacts/\(validatedID(contactID))/customer-profile",
            [],
            [
                "weChatId": weChatID,
                "customerLevel": profile.customerLevel,
                "sourceChannel": profile.sourceChannel,
                "sourceDetail": profile.sourceDetail,
                "profileKey": profile.profileKey,
                "purchaseHistory": profile.purchaseHistory,
                "socialAccounts": profile.socialAccounts,
                "notes": profile.notes,
                "phone": profile.phone,
                "mappedLabelNames": profile.mappedLabelNames
            ]
        )
    }

    private static func resolveContactID(friendID: String, weChatID: String, request: Request) async throws -> String? {
        for page in 1...30 {
            let result = try await request("GET", "/openapi/v1/contacts", [
                URLQueryItem(name: "weChatId", value: weChatID),
                URLQueryItem(name: "page", value: "\(page)"),
                URLQueryItem(name: "pageSize", value: "100"),
                URLQueryItem(name: "includeDeleted", value: "false")
            ], nil)
            let contacts = OpenApiContactLabelsPermissionsService.dictionaryArray(in: result.jsonObject)
            if let match = contacts.first(where: {
                OpenApiContactLabelsPermissionsService.string(in: $0, keys: ["wxid", "friendWxid", "userName", "username"]) == friendID
            }) {
                let id = OpenApiContactLabelsPermissionsService.string(in: match, keys: ["id", "contactId", "contactID"])
                if !id.isEmpty { return id }
            }
            if contacts.count < 100 { return nil }
        }
        return nil
    }

    private static func validatedID(_ value: String) -> String {
        value.trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: "/", with: "")
    }

    static func profile(from object: Any?) -> OpenApiCustomerProfile {
        let root = unwrap(object)
        let labels = firstArray(root, keys: ["mappedLabelNames", "labelNames", "labels"])
        return OpenApiCustomerProfile(
            customerLevel: firstString(root, keys: ["customerLevel", "level"]),
            sourceChannel: firstString(root, keys: ["sourceChannel", "source"]),
            sourceDetail: firstString(root, keys: ["sourceDetail", "sourceExt"]),
            profileKey: firstString(root, keys: ["profileKey", "profileId"]),
            purchaseHistory: firstString(root, keys: ["purchaseHistory", "purchases"]),
            socialAccounts: firstString(root, keys: ["socialAccounts", "socialAccount"]),
            notes: firstString(root, keys: ["notes", "remark"]),
            phone: firstString(root, keys: ["phone", "mobile", "mobilePhone"]),
            mappedLabelNames: labels
        )
    }

    private static func unwrap(_ object: Any?) -> [String: Any] {
        guard let dictionary = object as? [String: Any] else { return [:] }
        for key in ["data", "result", "payload", "item", "record", "customerProfile"] {
            if let nested = dictionary[key] as? [String: Any] {
                return unwrap(nested)
            }
        }
        return dictionary
    }

    private static func firstString(_ root: [String: Any], keys: [String]) -> String {
        for key in keys {
            if let value = root[key] as? String, !value.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                return value.trimmingCharacters(in: .whitespacesAndNewlines)
            }
            if let value = root[key] as? NSNumber { return value.stringValue }
        }
        return ""
    }

    private static func firstArray(_ root: [String: Any], keys: [String]) -> [String] {
        for key in keys {
            if let values = root[key] as? [String] { return values.filter { !$0.isEmpty } }
            if let values = root[key] as? [[String: Any]] {
                return values.compactMap { firstString($0, keys: ["name", "labelName", "value"]) }.filter { !$0.isEmpty }
            }
        }
        return []
    }
}

private extension String {
    func ifEmpty(_ fallback: String) -> String { isEmpty ? fallback : self }
}
