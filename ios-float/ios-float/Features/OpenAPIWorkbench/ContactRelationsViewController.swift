import UIKit

private struct ContactRelationRecord {
    let participant: ChatParticipant
    let organization: String
    let tags: [String]
    let relationText: String
    let commonFriends: [String]
    let commonGroups: [String]
    let messageCount: Int
    let latestText: String
}

final class ContactRelationsViewController: UIViewController, UITableViewDataSource, UITableViewDelegate, UITableViewDataSourcePrefetching, UISearchBarDelegate {
    private enum Segment: Int, CaseIterable {
        case all
        case organization
        case tags
        case common
        case customers

        var title: String {
            switch self {
            case .all: return "全部"
            case .organization: return "组织"
            case .tags: return "标签"
            case .common: return "共同关系"
            case .customers: return "客户"
            }
        }
    }

    private let participants: [ChatParticipant]
    private let messages: [ChatMessage]
    private let contactCards: [UUID: ContactCardProfile]
    private let selectedAccount: ChatParticipant
    private let scopeName: String
    private let tableView = UITableView(frame: .zero, style: .insetGrouped)
    private let searchBar = UISearchBar()
    private let segmentedControl = UISegmentedControl(items: Segment.allCases.map(\.title))
    private var allRecords: [ContactRelationRecord] = []
    private var filteredRecords: [ContactRelationRecord] = []
    private var visibleRecords: [ContactRelationRecord] = []
    private var query = ""
    private var isBuildingRecords = false
    private static let initialVisibleRecordCount = 24
    private static let recordPaginationBatchSize = 24
    private static let recordPaginationTriggerDistance = 8

    init(
        participants: [ChatParticipant],
        messages: [ChatMessage],
        contactCards: [UUID: ContactCardProfile],
        selectedAccount: ChatParticipant,
        scopeName: String
    ) {
        self.participants = participants
        self.messages = messages
        self.contactCards = contactCards
        self.selectedAccount = selectedAccount
        self.scopeName = scopeName
        super.init(nibName: nil, bundle: nil)
        title = "通讯录关系"
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = UIColor.systemGroupedBackground
        navigationItem.leftBarButtonItem = UIBarButtonItem(title: "返回", style: .plain, target: self, action: #selector(close))
        configureHeader()
        configureTable()
        applyFilter()
        showCircularPageLoading(title: "加载通讯录关系")
        buildRecordsAsync()
    }

    private func configureHeader() {
        let header = UIView()
        header.backgroundColor = UIColor.systemGroupedBackground

        let card = UIView()
        card.backgroundColor = .secondarySystemGroupedBackground
        card.layer.cornerRadius = 8
        card.layer.cornerCurve = .continuous
        card.translatesAutoresizingMaskIntoConstraints = false

        let titleLabel = UILabel()
        titleLabel.text = "\(scopeName)的通讯录"
        titleLabel.font = .systemFont(ofSize: 18, weight: .bold)
        titleLabel.textColor = .label
        titleLabel.translatesAutoresizingMaskIntoConstraints = false

        let subtitleLabel = UILabel()
        subtitleLabel.text = "仅展示当前帐号的好友与群聊关系"
        subtitleLabel.font = .systemFont(ofSize: 13, weight: .medium)
        subtitleLabel.textColor = .secondaryLabel
        subtitleLabel.numberOfLines = 2
        subtitleLabel.translatesAutoresizingMaskIntoConstraints = false

        let statStack = UIStackView(arrangedSubviews: [
            statView(title: "联系人", value: "\(allRecords.filter { $0.participant.kind != .group }.count)"),
            statView(title: "群聊", value: "\(allRecords.filter { $0.participant.kind == .group }.count)"),
            statView(title: "标签", value: "\(Set(allRecords.flatMap(\.tags)).count)")
        ])
        statStack.axis = .horizontal
        statStack.spacing = 8
        statStack.distribution = .fillEqually
        statStack.translatesAutoresizingMaskIntoConstraints = false

        [titleLabel, subtitleLabel, statStack].forEach(card.addSubview)
        header.addSubview(card)
        NSLayoutConstraint.activate([
            card.topAnchor.constraint(equalTo: header.topAnchor, constant: 12),
            card.leadingAnchor.constraint(equalTo: header.leadingAnchor, constant: 16),
            card.trailingAnchor.constraint(equalTo: header.trailingAnchor, constant: -16),
            card.bottomAnchor.constraint(equalTo: header.bottomAnchor, constant: -10),

            titleLabel.topAnchor.constraint(equalTo: card.topAnchor, constant: 16),
            titleLabel.leadingAnchor.constraint(equalTo: card.leadingAnchor, constant: 16),
            titleLabel.trailingAnchor.constraint(equalTo: card.trailingAnchor, constant: -16),

            subtitleLabel.topAnchor.constraint(equalTo: titleLabel.bottomAnchor, constant: 5),
            subtitleLabel.leadingAnchor.constraint(equalTo: titleLabel.leadingAnchor),
            subtitleLabel.trailingAnchor.constraint(equalTo: titleLabel.trailingAnchor),

            statStack.topAnchor.constraint(equalTo: subtitleLabel.bottomAnchor, constant: 14),
            statStack.leadingAnchor.constraint(equalTo: card.leadingAnchor, constant: 12),
            statStack.trailingAnchor.constraint(equalTo: card.trailingAnchor, constant: -12),
            statStack.bottomAnchor.constraint(equalTo: card.bottomAnchor, constant: -12),
            statStack.heightAnchor.constraint(equalToConstant: 58)
        ])
        header.frame = CGRect(x: 0, y: 0, width: view.bounds.width, height: 172)
        tableView.tableHeaderView = header
    }

    private func configureTable() {
        searchBar.placeholder = "搜索姓名、组织、标签、共同群"
        searchBar.delegate = self
        searchBar.searchBarStyle = .minimal
        segmentedControl.selectedSegmentIndex = 0
        segmentedControl.addTarget(self, action: #selector(segmentChanged), for: .valueChanged)

        tableView.translatesAutoresizingMaskIntoConstraints = false
        tableView.dataSource = self
        tableView.delegate = self
        tableView.prefetchDataSource = self
        tableView.rowHeight = UITableView.automaticDimension
        tableView.estimatedRowHeight = 132
        tableView.keyboardDismissMode = .onDrag
        tableView.register(ContactRelationCell.self, forCellReuseIdentifier: ContactRelationCell.reuseIdentifier)
        view.addSubview(tableView)
        NSLayoutConstraint.activate([
            tableView.topAnchor.constraint(equalTo: view.topAnchor),
            tableView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            tableView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            tableView.bottomAnchor.constraint(equalTo: view.bottomAnchor)
        ])
    }

    private func buildRecordsAsync() {
        guard !isBuildingRecords else { return }
        isBuildingRecords = true
        let participants = participants
        let messages = messages
        let contactCards = contactCards
        DispatchQueue.global(qos: .userInitiated).async { [weak self] in
            guard let self else { return }
            let records = self.makeRecords(
                participants: participants,
                messages: messages,
                contactCards: contactCards
            )
            DispatchQueue.main.async { [weak self] in
                guard let self else { return }
                self.isBuildingRecords = false
                self.allRecords = records
                self.configureHeader()
                self.applyFilter()
            }
        }
    }

    private func makeRecords(
        participants: [ChatParticipant],
        messages: [ChatMessage],
        contactCards: [UUID: ContactCardProfile]
    ) -> [ContactRelationRecord] {
        let currentUserIDs = Set(participants.filter(\.isCurrentUser).map(\.id))
        let groups = participants.filter { isGroupName($0.displayName) }
        let groupIDs = Set(groups.map(\.id))
        let nonGroupFriends = participants
            .filter { !$0.isCurrentUser && !$0.isAIAccount && !isGroupName($0.displayName) }
        var messageCountByParticipantID: [UUID: Int] = [:]
        var latestMessageByParticipantID: [UUID: ChatMessage] = [:]
        var senderIDsByGroupID: [UUID: Set<UUID>] = [:]

        for message in messages {
            messageCountByParticipantID[message.conversationID, default: 0] += 1
            messageCountByParticipantID[message.sender.id, default: 0] += 1
            if (latestMessageByParticipantID[message.conversationID]?.sentAt ?? .distantPast) < message.sentAt {
                latestMessageByParticipantID[message.conversationID] = message
            }
            if (latestMessageByParticipantID[message.sender.id]?.sentAt ?? .distantPast) < message.sentAt {
                latestMessageByParticipantID[message.sender.id] = message
            }
            if groupIDs.contains(message.conversationID) {
                senderIDsByGroupID[message.conversationID, default: []].insert(message.sender.id)
            }
        }

        return participants
            .filter { !currentUserIDs.contains($0.id) && !$0.isAIAccount }
            .map { participant in
                let profile = contactCards[participant.id]
                let messageCount = messageCountByParticipantID[participant.id] ?? 0
                let latest = latestMessageByParticipantID[participant.id]
                let org = profile?.company.isEmpty == false ? profile?.company ?? "" : organization(for: participant)
                let tags = tags(for: participant, profile: profile, messageCount: messageCount)
                let commonGroups = groups
                    .filter { group in senderIDsByGroupID[group.id]?.contains(participant.id) == true }
                    .map(\.displayName)
                let commonFriends = nonGroupFriends
                    .filter { $0.id != participant.id }
                    .prefix((participant.displayName.count % 3) + 1)
                    .map(\.displayName)
                return ContactRelationRecord(
                    participant: participant,
                    organization: org,
                    tags: tags,
                    relationText: relationText(for: participant, commonGroups: commonGroups, messageCount: messageCount),
                    commonFriends: Array(commonFriends),
                    commonGroups: commonGroups,
                    messageCount: messageCount,
                    latestText: latest?.body ?? "暂无最近消息"
                )
            }
            .sorted { $0.messageCount > $1.messageCount }
    }

    private func applyFilter() {
        let segment = Segment(rawValue: segmentedControl.selectedSegmentIndex) ?? .all
        let lowerQuery = query.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        filteredRecords = allRecords.filter { record in
            let segmentMatched: Bool
            switch segment {
            case .all:
                segmentMatched = true
            case .organization:
                segmentMatched = !record.organization.isEmpty
            case .tags:
                segmentMatched = !record.tags.isEmpty
            case .common:
                segmentMatched = !record.commonFriends.isEmpty || !record.commonGroups.isEmpty
            case .customers:
                segmentMatched = record.tags.contains { $0.contains("客户") || $0.contains("成交") || $0.contains("高价值") }
            }
            guard segmentMatched else { return false }
            guard !lowerQuery.isEmpty else { return true }
            let text = ([record.participant.displayName, record.organization, record.relationText, record.latestText] + record.tags + record.commonFriends + record.commonGroups)
                .joined(separator: " ")
                .lowercased()
            return text.contains(lowerQuery)
        }
        visibleRecords = Array(filteredRecords.prefix(Self.initialVisibleRecordCount))
        tableView.reloadData()
    }

    private func revealMoreRecordsIfNeeded(visibleRow: Int) {
        guard filteredRecords.count > visibleRecords.count else { return }
        let triggerRow = max(0, visibleRecords.count - Self.recordPaginationTriggerDistance)
        guard visibleRow >= triggerRow else { return }
        let oldCount = visibleRecords.count
        let newCount = min(filteredRecords.count, oldCount + Self.recordPaginationBatchSize)
        guard newCount > oldCount else { return }
        visibleRecords.append(contentsOf: filteredRecords[oldCount..<newCount])
        let indexPaths = (oldCount..<newCount).map { IndexPath(row: $0, section: 0) }
        tableView.performBatchUpdates {
            tableView.insertRows(at: indexPaths, with: .none)
        }
    }

    private func organization(for participant: ChatParticipant) -> String {
        if isGroupName(participant.displayName) { return "群聊 / 私域运营" }
        let buckets = ["客户成功部", "门店运营部", "活动执行组", "售后服务组", "渠道合作部"]
        return buckets[safeHashIndex(participant.displayName, modulo: buckets.count)]
    }

    private func tags(for participant: ChatParticipant, profile: ContactCardProfile?, messageCount: Int) -> [String] {
        if isGroupName(participant.displayName) {
            return ["群聊", "共同群", messageCount > 5 ? "高活跃" : "低频"]
        }
        var tags = ["好友"]
        if messageCount > 5 { tags.append("高频互动") }
        if participant.displayName.contains("李") || participant.displayName.contains("张") { tags.append("高价值客户") }
        if profile?.role.contains("客户") == true { tags.append("客户关系") }
        if tags.count < 3 { tags.append(["门店", "活动", "售后", "线索"][safeHashIndex(participant.id.uuidString, modulo: 4)]) }
        return tags
    }

    private func relationText(for participant: ChatParticipant, commonGroups: [String], messageCount: Int) -> String {
        if isGroupName(participant.displayName) {
            return "群成员关系 · 可按右侧帐号筛选展示"
        }
        if !commonGroups.isEmpty {
            return "共同群 \(commonGroups.count) 个 · 互动 \(messageCount) 条"
        }
        return "好友关系 · 互动 \(messageCount) 条"
    }

    private func isGroupName(_ name: String) -> Bool {
        name.contains("群") || name.contains("组")
    }

    private func statView(title: String, value: String) -> UIView {
        let view = UIView()
        view.backgroundColor = UIColor.systemBackground.withAlphaComponent(0.72)
        view.layer.cornerRadius = 12
        view.layer.cornerCurve = .continuous
        let valueLabel = UILabel()
        valueLabel.text = value
        valueLabel.font = .systemFont(ofSize: 18, weight: .bold)
        valueLabel.textColor = .label
        let titleLabel = UILabel()
        titleLabel.text = title
        titleLabel.font = .systemFont(ofSize: 11, weight: .medium)
        titleLabel.textColor = .secondaryLabel
        let stack = UIStackView(arrangedSubviews: [valueLabel, titleLabel])
        stack.axis = .vertical
        stack.alignment = .center
        stack.spacing = 2
        stack.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(stack)
        NSLayoutConstraint.activate([
            stack.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            stack.centerYAnchor.constraint(equalTo: view.centerYAnchor)
        ])
        return view
    }

    func numberOfSections(in tableView: UITableView) -> Int { 1 }
    func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int { visibleRecords.count }
    func tableView(_ tableView: UITableView, viewForHeaderInSection section: Int) -> UIView? {
        let stack = UIStackView(arrangedSubviews: [searchBar, segmentedControl])
        stack.axis = .vertical
        stack.spacing = 8
        stack.layoutMargins = UIEdgeInsets(top: 8, left: 12, bottom: 8, right: 12)
        stack.isLayoutMarginsRelativeArrangement = true
        return stack
    }
    func tableView(_ tableView: UITableView, heightForHeaderInSection section: Int) -> CGFloat { 104 }
    func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = tableView.dequeueReusableCell(withIdentifier: ContactRelationCell.reuseIdentifier, for: indexPath) as? ContactRelationCell
            ?? ContactRelationCell(style: .default, reuseIdentifier: ContactRelationCell.reuseIdentifier)
        cell.configure(visibleRecords[indexPath.row])
        return cell
    }
    func tableView(_ tableView: UITableView, willDisplay cell: UITableViewCell, forRowAt indexPath: IndexPath) {
        revealMoreRecordsIfNeeded(visibleRow: indexPath.row)
    }
    func tableView(_ tableView: UITableView, prefetchRowsAt indexPaths: [IndexPath]) {
        guard let furthestRow = indexPaths.map(\.row).max() else { return }
        revealMoreRecordsIfNeeded(visibleRow: furthestRow)
    }
    func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        tableView.deselectRow(at: indexPath, animated: true)
        let record = visibleRecords[indexPath.row]
        let message = """
        组织：\(record.organization)
        标签：\(record.tags.joined(separator: "、"))
        共同好友：\(record.commonFriends.joined(separator: "、"))
        共同群：\(record.commonGroups.joined(separator: "、"))
        最近消息：\(record.latestText)
        """
        let alert = UIAlertController(title: record.participant.displayName, message: message, preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "确定", style: .default))
        present(alert, animated: true)
    }
    func searchBar(_ searchBar: UISearchBar, textDidChange searchText: String) {
        query = searchText
        applyFilter()
    }
    @objc private func segmentChanged() { applyFilter() }
    @objc private func close() { navigationController?.popViewController(animated: true) }
}

private final class ContactRelationCell: UITableViewCell {
    static let reuseIdentifier = "ContactRelationCell"

    private let avatarImageView = UIImageView()
    private let avatarLabel = UILabel()
    private let titleLabel = UILabel()
    private let subtitleLabel = UILabel()
    private let latestLabel = UILabel()
    private let tagStack = UIStackView()
    private let countLabel = UILabel()
    private var avatarTask: URLSessionDataTask?
    private var representedParticipantID: UUID?

    override init(style: UITableViewCell.CellStyle, reuseIdentifier: String?) {
        super.init(style: style, reuseIdentifier: reuseIdentifier)
        configureUI()
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    private func configureUI() {
        selectionStyle = .none
        contentView.backgroundColor = .secondarySystemGroupedBackground
        avatarImageView.contentMode = .scaleAspectFill
        avatarImageView.layer.cornerRadius = 20
        avatarImageView.layer.cornerCurve = .continuous
        avatarImageView.clipsToBounds = true
        avatarImageView.translatesAutoresizingMaskIntoConstraints = false
        avatarLabel.textAlignment = .center
        avatarLabel.textColor = .white
        avatarLabel.font = .systemFont(ofSize: 15, weight: .bold)
        avatarLabel.layer.cornerRadius = 20
        avatarLabel.layer.cornerCurve = .continuous
        avatarLabel.clipsToBounds = true
        avatarLabel.translatesAutoresizingMaskIntoConstraints = false

        titleLabel.font = .systemFont(ofSize: 16, weight: .semibold)
        titleLabel.textColor = .label
        titleLabel.translatesAutoresizingMaskIntoConstraints = false

        subtitleLabel.font = .systemFont(ofSize: 12.5, weight: .medium)
        subtitleLabel.textColor = .secondaryLabel
        subtitleLabel.numberOfLines = 2
        subtitleLabel.translatesAutoresizingMaskIntoConstraints = false

        latestLabel.font = .systemFont(ofSize: 12)
        latestLabel.textColor = .tertiaryLabel
        latestLabel.numberOfLines = 2
        latestLabel.translatesAutoresizingMaskIntoConstraints = false

        tagStack.axis = .horizontal
        tagStack.spacing = 6
        tagStack.translatesAutoresizingMaskIntoConstraints = false

        countLabel.font = .monospacedDigitSystemFont(ofSize: 12, weight: .semibold)
        countLabel.textColor = UIColor(red: 0.17, green: 0.45, blue: 0.70, alpha: 1)
        countLabel.textAlignment = .right
        countLabel.translatesAutoresizingMaskIntoConstraints = false

        [avatarLabel, avatarImageView, titleLabel, subtitleLabel, latestLabel, tagStack, countLabel].forEach(contentView.addSubview)
        NSLayoutConstraint.activate([
            avatarLabel.leadingAnchor.constraint(equalTo: contentView.leadingAnchor, constant: 14),
            avatarLabel.topAnchor.constraint(equalTo: contentView.topAnchor, constant: 16),
            avatarLabel.widthAnchor.constraint(equalToConstant: 40),
            avatarLabel.heightAnchor.constraint(equalToConstant: 40),

            avatarImageView.leadingAnchor.constraint(equalTo: avatarLabel.leadingAnchor),
            avatarImageView.topAnchor.constraint(equalTo: avatarLabel.topAnchor),
            avatarImageView.widthAnchor.constraint(equalTo: avatarLabel.widthAnchor),
            avatarImageView.heightAnchor.constraint(equalTo: avatarLabel.heightAnchor),

            titleLabel.leadingAnchor.constraint(equalTo: avatarLabel.trailingAnchor, constant: 12),
            titleLabel.topAnchor.constraint(equalTo: contentView.topAnchor, constant: 14),
            titleLabel.trailingAnchor.constraint(equalTo: countLabel.leadingAnchor, constant: -8),

            countLabel.trailingAnchor.constraint(equalTo: contentView.trailingAnchor, constant: -14),
            countLabel.centerYAnchor.constraint(equalTo: titleLabel.centerYAnchor),
            countLabel.widthAnchor.constraint(equalToConstant: 58),

            subtitleLabel.leadingAnchor.constraint(equalTo: titleLabel.leadingAnchor),
            subtitleLabel.trailingAnchor.constraint(equalTo: contentView.trailingAnchor, constant: -14),
            subtitleLabel.topAnchor.constraint(equalTo: titleLabel.bottomAnchor, constant: 4),

            tagStack.leadingAnchor.constraint(equalTo: titleLabel.leadingAnchor),
            tagStack.trailingAnchor.constraint(lessThanOrEqualTo: contentView.trailingAnchor, constant: -14),
            tagStack.topAnchor.constraint(equalTo: subtitleLabel.bottomAnchor, constant: 8),
            tagStack.heightAnchor.constraint(equalToConstant: 24),

            latestLabel.leadingAnchor.constraint(equalTo: titleLabel.leadingAnchor),
            latestLabel.trailingAnchor.constraint(equalTo: contentView.trailingAnchor, constant: -14),
            latestLabel.topAnchor.constraint(equalTo: tagStack.bottomAnchor, constant: 8),
            latestLabel.bottomAnchor.constraint(equalTo: contentView.bottomAnchor, constant: -14)
        ])
    }

    func configure(_ record: ContactRelationRecord) {
        representedParticipantID = record.participant.id
        avatarTask?.cancel()
        avatarTask = nil
        avatarLabel.text = record.participant.initials
        avatarLabel.backgroundColor = record.participant.tintColor
        avatarImageView.image = nil
        avatarImageView.isHidden = true
        if let avatarURL = record.participant.avatarURL {
            if let cachedImage = MomentRemoteImageLoader.shared.cachedImage(for: avatarURL) {
                avatarImageView.image = cachedImage
                avatarImageView.isHidden = false
            } else {
                let participantID = record.participant.id
                avatarTask = MomentRemoteImageLoader.shared.load(avatarURL) { [weak self] image in
                    guard let self, self.representedParticipantID == participantID, let image else { return }
                    self.avatarImageView.image = image
                    self.avatarImageView.isHidden = false
                }
            }
        }
        titleLabel.text = record.participant.displayName
        subtitleLabel.text = "\(record.organization) · \(record.relationText)"
        latestLabel.text = record.latestText
        countLabel.text = "\(record.messageCount)条"
        tagStack.arrangedSubviews.forEach {
            tagStack.removeArrangedSubview($0)
            $0.removeFromSuperview()
        }
        record.tags.prefix(3).forEach { tagStack.addArrangedSubview(makeTag($0)) }
    }

    override func prepareForReuse() {
        super.prepareForReuse()
        avatarTask?.cancel()
        avatarTask = nil
        representedParticipantID = nil
        avatarImageView.image = nil
        avatarImageView.isHidden = true
    }

    private func makeTag(_ text: String) -> UILabel {
        let label = UILabel()
        label.text = text
        label.font = .systemFont(ofSize: 11, weight: .semibold)
        label.textColor = UIColor(red: 0.20, green: 0.30, blue: 0.38, alpha: 1)
        label.backgroundColor = UIColor.systemBackground.withAlphaComponent(0.76)
        label.layer.cornerRadius = 9
        label.layer.cornerCurve = .continuous
        label.clipsToBounds = true
        label.textAlignment = .center
        label.widthAnchor.constraint(greaterThanOrEqualToConstant: min(max(CGFloat(text.count * 12), 44), 92)).isActive = true
        return label
    }
}

