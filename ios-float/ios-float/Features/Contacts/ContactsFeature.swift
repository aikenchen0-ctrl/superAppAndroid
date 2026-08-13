import UIKit
import PhotosUI

final class MentionPickerViewController: UIViewController {
    var onSelect: ((ChatParticipant) -> Void)?
    var onDismiss: (() -> Void)?

    private let candidates: [ChatParticipant]
    private let initialQuery: String
    private var filteredCandidates: [ChatParticipant] = []
    private let searchController = UISearchController(searchResultsController: nil)
    private let tableView = UITableView(frame: .zero, style: .insetGrouped)
    private var didNotifyDismiss = false

    init(candidates: [ChatParticipant], initialQuery: String) {
        self.candidates = candidates
        self.initialQuery = initialQuery
        self.filteredCandidates = candidates
        super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        title = "@ 人"
        view.backgroundColor = UIColor.systemGroupedBackground
        navigationItem.leftBarButtonItem = UIBarButtonItem(
            title: "返回",
            style: .plain,
            target: self,
            action: #selector(close)
        )

        searchController.searchResultsUpdater = self
        searchController.obscuresBackgroundDuringPresentation = false
        searchController.searchBar.placeholder = "搜索名称"
        searchController.searchBar.text = initialQuery
        navigationItem.searchController = searchController
        navigationItem.hidesSearchBarWhenScrolling = false

        tableView.dataSource = self
        tableView.delegate = self
        tableView.register(UITableViewCell.self, forCellReuseIdentifier: "MentionCell")
        tableView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(tableView)

        NSLayoutConstraint.activate([
            tableView.topAnchor.constraint(equalTo: view.topAnchor),
            tableView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            tableView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            tableView.bottomAnchor.constraint(equalTo: view.bottomAnchor)
        ])

        applyFilter(initialQuery)
    }

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        searchController.searchBar.becomeFirstResponder()
    }

    override func viewDidDisappear(_ animated: Bool) {
        super.viewDidDisappear(animated)
        notifyDismissIfNeeded()
    }

    private func applyFilter(_ query: String) {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty {
            filteredCandidates = candidates
        } else {
            filteredCandidates = candidates.filter {
                $0.displayName.localizedCaseInsensitiveContains(trimmed)
                    || $0.initials.localizedCaseInsensitiveContains(trimmed)
            }
        }
        tableView.reloadData()
    }

    @objc private func close() {
        dismiss(animated: true)
    }

    private func notifyDismissIfNeeded() {
        guard !didNotifyDismiss else { return }
        didNotifyDismiss = true
        onDismiss?()
    }
}

extension MentionPickerViewController: UISearchResultsUpdating {
    func updateSearchResults(for searchController: UISearchController) {
        applyFilter(searchController.searchBar.text ?? "")
    }
}

extension MentionPickerViewController: UITableViewDataSource, UITableViewDelegate {
    func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        filteredCandidates.count
    }

    func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = tableView.dequeueReusableCell(withIdentifier: "MentionCell", for: indexPath)
        let participant = filteredCandidates[indexPath.row]
        var configuration = cell.defaultContentConfiguration()
        configuration.image = UIImage(systemName: participant.kind == .group ? "person.2.fill" : "person.crop.circle.fill")
        configuration.imageProperties.tintColor = participant.tintColor
        configuration.text = participant.displayName
        configuration.secondaryText = "@\(participant.displayName)"
        configuration.textProperties.font = .systemFont(ofSize: 16, weight: .semibold)
        configuration.secondaryTextProperties.color = UIColor.systemBlue
        cell.contentConfiguration = configuration
        return cell
    }

    func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        tableView.deselectRow(at: indexPath, animated: true)
        onSelect?(filteredCandidates[indexPath.row])
        dismiss(animated: true)
    }
}

final class AutoReplyTestPickerViewController: UIViewController {
    var onSend: (([ChatWindowViewController.AutoReplyTestCase]) -> Void)?
    var onClose: (() -> Void)?

    private let testCases: [ChatWindowViewController.AutoReplyTestCase]
    private var selectedIDs = Set<UUID>()
    private let tableView = UITableView(frame: .zero, style: .insetGrouped)
    private let sendButton = UIButton(type: .system)

    init(testCases: [ChatWindowViewController.AutoReplyTestCase]) {
        self.testCases = testCases
        self.selectedIDs = Set(testCases.prefix(3).map(\.id))
        super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        title = "选择测试消息"
        view.backgroundColor = UIColor.systemGroupedBackground
        navigationItem.leftBarButtonItem = UIBarButtonItem(title: "返回", style: .plain, target: self, action: #selector(close))
        navigationItem.rightBarButtonItem = UIBarButtonItem(title: "全选", style: .plain, target: self, action: #selector(toggleSelectAllTests))

        tableView.dataSource = self
        tableView.delegate = self
        tableView.register(UITableViewCell.self, forCellReuseIdentifier: "AutoReplyTestCell")
        tableView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(tableView)

        var config = UIButton.Configuration.filled()
        config.title = "发送选中测试"
        config.image = UIImage(systemName: "paperplane.fill")
        config.imagePadding = 6
        config.baseBackgroundColor = UIColor(red: 0.47, green: 0.83, blue: 0.08, alpha: 1)
        config.baseForegroundColor = .black
        sendButton.configuration = config
        sendButton.addTarget(self, action: #selector(sendSelected), for: .touchUpInside)
        sendButton.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(sendButton)

        NSLayoutConstraint.activate([
            tableView.topAnchor.constraint(equalTo: view.topAnchor),
            tableView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            tableView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            tableView.bottomAnchor.constraint(equalTo: sendButton.topAnchor, constant: -10),

            sendButton.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 16),
            sendButton.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -16),
            sendButton.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor, constant: -12),
            sendButton.heightAnchor.constraint(equalToConstant: 44)
        ])
        updateSendButton()
    }

    @objc private func close() {
        onClose?()
    }

    @objc private func toggleSelectAllTests() {
        if selectedIDs.count == testCases.count {
            selectedIDs.removeAll()
        } else {
            selectedIDs = Set(testCases.map(\.id))
        }
        navigationItem.rightBarButtonItem?.title = selectedIDs.count == testCases.count ? "清空" : "全选"
        tableView.reloadData()
        updateSendButton()
    }

    @objc private func sendSelected() {
        let selected = testCases.filter { selectedIDs.contains($0.id) }
        guard !selected.isEmpty else { return }
        onSend?(selected)
    }

    private func updateSendButton() {
        let count = selectedIDs.count
        sendButton.isEnabled = count > 0
        sendButton.alpha = count > 0 ? 1 : 0.45
        sendButton.configuration?.title = count > 0 ? "发送选中 \(count) 条测试" : "请选择测试消息"
    }
}

extension AutoReplyTestPickerViewController: UITableViewDataSource, UITableViewDelegate {
    func numberOfSections(in tableView: UITableView) -> Int {
        1
    }

    func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        testCases.count
    }

    func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = tableView.dequeueReusableCell(withIdentifier: "AutoReplyTestCell", for: indexPath)
        let testCase = testCases[indexPath.row]
        var configuration = cell.defaultContentConfiguration()
        configuration.image = UIImage(systemName: testCase.prefersGroup ? "person.3.fill" : "person.crop.circle.fill")
        configuration.imageProperties.tintColor = testCase.prefersGroup ? UIColor.systemBlue : UIColor.systemGreen
        configuration.text = testCase.title
        configuration.secondaryText = testCase.body
        configuration.secondaryTextProperties.numberOfLines = 2
        cell.contentConfiguration = configuration
        cell.accessoryType = selectedIDs.contains(testCase.id) ? .checkmark : .none
        return cell
    }

    func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        tableView.deselectRow(at: indexPath, animated: true)
        let testCase = testCases[indexPath.row]
        if selectedIDs.contains(testCase.id) {
            selectedIDs.remove(testCase.id)
        } else {
            selectedIDs.insert(testCase.id)
        }
        navigationItem.rightBarButtonItem?.title = selectedIDs.count == testCases.count ? "清空" : "全选"
        tableView.reloadRows(at: [indexPath], with: .automatic)
        updateSendButton()
    }
}

final class ForwardTargetPickerViewController: UIViewController {
    var onSelect: ((ChatParticipant) -> Void)?
    var onClose: (() -> Void)?

    private let targets: [ChatParticipant]
    private let messageCount: Int
    private let modeTitle: String
    private var filteredTargets: [ChatParticipant]
    private let searchController = UISearchController(searchResultsController: nil)
    private let tableView = UITableView(frame: .zero, style: .insetGrouped)
    private let summaryView = UIView()
    private let summaryIconView = UIImageView()
    private let summaryTitleLabel = UILabel()
    private let summaryDetailLabel = UILabel()

    init(targets: [ChatParticipant], messageCount: Int, modeTitle: String) {
        self.targets = targets
        self.messageCount = messageCount
        self.modeTitle = modeTitle
        self.filteredTargets = targets
        super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        title = "选择接收方"
        view.backgroundColor = UIColor.systemGroupedBackground
        navigationItem.leftBarButtonItem = UIBarButtonItem(
            title: "返回",
            style: .plain,
            target: self,
            action: #selector(close)
        )

        searchController.searchResultsUpdater = self
        searchController.obscuresBackgroundDuringPresentation = false
        searchController.searchBar.placeholder = "搜索好友或群聊"
        navigationItem.searchController = searchController
        navigationItem.hidesSearchBarWhenScrolling = false

        setupSummaryView()

        tableView.dataSource = self
        tableView.delegate = self
        tableView.register(UITableViewCell.self, forCellReuseIdentifier: "ForwardTargetCell")
        tableView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(tableView)

        NSLayoutConstraint.activate([
            tableView.topAnchor.constraint(equalTo: view.topAnchor),
            tableView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            tableView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            tableView.bottomAnchor.constraint(equalTo: view.bottomAnchor)
        ])

        tableView.tableHeaderView = summaryView
        applyFilter("")
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        let width = tableView.bounds.width
        guard width > 0 else { return }
        let targetSize = CGSize(width: width, height: UIView.layoutFittingCompressedSize.height)
        let height = summaryView.systemLayoutSizeFitting(targetSize).height
        if summaryView.frame.width != width || abs(summaryView.frame.height - height) > 0.5 {
            summaryView.frame = CGRect(x: 0, y: 0, width: width, height: height)
            tableView.tableHeaderView = summaryView
        }
    }

    private func setupSummaryView() {
        summaryView.backgroundColor = .clear

        let cardView = UIView()
        cardView.backgroundColor = UIColor.secondarySystemGroupedBackground
        cardView.layer.cornerRadius = 12
        cardView.layer.cornerCurve = .continuous
        cardView.translatesAutoresizingMaskIntoConstraints = false
        summaryView.addSubview(cardView)

        summaryIconView.image = UIImage(systemName: "arrowshape.turn.up.right.fill")
        summaryIconView.tintColor = UIColor.systemGreen
        summaryIconView.contentMode = .scaleAspectFit
        summaryIconView.translatesAutoresizingMaskIntoConstraints = false
        cardView.addSubview(summaryIconView)

        summaryTitleLabel.text = modeTitle
        summaryTitleLabel.font = .systemFont(ofSize: 17, weight: .semibold)
        summaryTitleLabel.textColor = .label
        summaryTitleLabel.translatesAutoresizingMaskIntoConstraints = false
        cardView.addSubview(summaryTitleLabel)

        summaryDetailLabel.text = "已选择 \(messageCount) 条消息，点击好友或群聊后发送"
        summaryDetailLabel.font = .systemFont(ofSize: 13, weight: .regular)
        summaryDetailLabel.textColor = .secondaryLabel
        summaryDetailLabel.numberOfLines = 2
        summaryDetailLabel.translatesAutoresizingMaskIntoConstraints = false
        cardView.addSubview(summaryDetailLabel)

        NSLayoutConstraint.activate([
            cardView.topAnchor.constraint(equalTo: summaryView.topAnchor, constant: 12),
            cardView.leadingAnchor.constraint(equalTo: summaryView.leadingAnchor, constant: 16),
            cardView.trailingAnchor.constraint(equalTo: summaryView.trailingAnchor, constant: -16),
            cardView.bottomAnchor.constraint(equalTo: summaryView.bottomAnchor, constant: -8),

            summaryIconView.leadingAnchor.constraint(equalTo: cardView.leadingAnchor, constant: 16),
            summaryIconView.centerYAnchor.constraint(equalTo: cardView.centerYAnchor),
            summaryIconView.widthAnchor.constraint(equalToConstant: 28),
            summaryIconView.heightAnchor.constraint(equalToConstant: 28),

            summaryTitleLabel.topAnchor.constraint(equalTo: cardView.topAnchor, constant: 14),
            summaryTitleLabel.leadingAnchor.constraint(equalTo: summaryIconView.trailingAnchor, constant: 14),
            summaryTitleLabel.trailingAnchor.constraint(equalTo: cardView.trailingAnchor, constant: -16),

            summaryDetailLabel.topAnchor.constraint(equalTo: summaryTitleLabel.bottomAnchor, constant: 4),
            summaryDetailLabel.leadingAnchor.constraint(equalTo: summaryTitleLabel.leadingAnchor),
            summaryDetailLabel.trailingAnchor.constraint(equalTo: summaryTitleLabel.trailingAnchor),
            summaryDetailLabel.bottomAnchor.constraint(equalTo: cardView.bottomAnchor, constant: -14)
        ])
    }

    private func applyFilter(_ query: String) {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty {
            filteredTargets = targets
        } else {
            filteredTargets = targets.filter {
                $0.displayName.localizedCaseInsensitiveContains(trimmed)
                    || $0.initials.localizedCaseInsensitiveContains(trimmed)
            }
        }
        tableView.reloadData()
    }

    @objc private func close() {
        onClose?()
    }

    private func isGroup(_ participant: ChatParticipant) -> Bool {
        participant.kind == .group
    }
}

extension ForwardTargetPickerViewController: UISearchResultsUpdating {
    func updateSearchResults(for searchController: UISearchController) {
        applyFilter(searchController.searchBar.text ?? "")
    }
}

extension ForwardTargetPickerViewController: UITableViewDataSource, UITableViewDelegate {
    func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        filteredTargets.count
    }

    func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = tableView.dequeueReusableCell(withIdentifier: "ForwardTargetCell", for: indexPath)
        let target = filteredTargets[indexPath.row]
        var configuration = cell.defaultContentConfiguration()
        configuration.image = UIImage(systemName: isGroup(target) ? "person.2.fill" : "person.crop.circle.fill")
        configuration.imageProperties.tintColor = target.tintColor
        configuration.text = target.displayName
        configuration.secondaryText = isGroup(target) ? "群聊" : "好友"
        configuration.textProperties.font = .systemFont(ofSize: 16, weight: .semibold)
        configuration.secondaryTextProperties.color = .secondaryLabel
        cell.contentConfiguration = configuration
        cell.accessoryType = .disclosureIndicator
        return cell
    }

    func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        tableView.deselectRow(at: indexPath, animated: true)
        onSelect?(filteredTargets[indexPath.row])
    }
}


final class MyAccountsViewController: UIViewController {
    var onSelectAccount: ((UUID) -> Void)?
    var onSaveProfile: ((ContactCardProfile) -> Void)?
    var onAddAccount: ((ContactCardProfile) -> ChatParticipant?)?
    var onRemoveAccount: ((UUID) -> UUID?)?
    var onToggleLanguage: (() -> Void)?

    private var accounts: [ChatParticipant]
    private var contactCards: [UUID: ContactCardProfile]
    private var selectedAccountID: UUID
    private var language: AppLanguage
    private let tableView = UITableView(frame: .zero, style: .insetGrouped)
    private let addAccountButton = UIButton(type: .system)
    private let languageButton = UIButton(type: .system)

    init(
        accounts: [ChatParticipant],
        contactCards: [UUID: ContactCardProfile],
        selectedAccountID: UUID,
        language: AppLanguage
    ) {
        self.accounts = accounts
        self.contactCards = contactCards
        self.selectedAccountID = selectedAccountID
        self.language = language
        super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = UIColor.systemGroupedBackground
        applyLanguage()
        configureLanguageButton()
        configureAddAccountButton()
        configureTable()
        showCircularPageLoading(title: language.text("加载帐号", "Loading Accounts"))
    }

    private func configureTable() {
        tableView.backgroundColor = .clear
        tableView.rowHeight = 76
        tableView.estimatedRowHeight = 76
        tableView.dataSource = self
        tableView.delegate = self
        tableView.register(UITableViewCell.self, forCellReuseIdentifier: "AccountCell")
        tableView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(tableView)

        NSLayoutConstraint.activate([
            tableView.topAnchor.constraint(equalTo: view.topAnchor),
            tableView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            tableView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            tableView.bottomAnchor.constraint(equalTo: addAccountButton.topAnchor, constant: -10)
        ])
    }

    private func configureAddAccountButton() {
        addAccountButton.titleLabel?.font = .systemFont(ofSize: 16, weight: .semibold)
        addAccountButton.tintColor = .white
        addAccountButton.backgroundColor = UIColor(red: 0.47, green: 0.83, blue: 0.08, alpha: 1)
        addAccountButton.layer.cornerRadius = 16
        addAccountButton.layer.cornerCurve = .continuous
        addAccountButton.setImage(UIImage(systemName: "plus.circle.fill"), for: .normal)
        addAccountButton.applyImagePadding(4)
        addAccountButton.addTarget(self, action: #selector(addAccount), for: .touchUpInside)
        addAccountButton.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(addAccountButton)

        NSLayoutConstraint.activate([
            addAccountButton.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 18),
            addAccountButton.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -18),
            addAccountButton.bottomAnchor.constraint(equalTo: languageButton.topAnchor, constant: -10),
            addAccountButton.heightAnchor.constraint(equalToConstant: 48)
        ])
        updateAddAccountButtonTitle()
    }

    private func configureLanguageButton() {
        languageButton.titleLabel?.font = .systemFont(ofSize: 16, weight: .semibold)
        languageButton.tintColor = UIColor.systemBlue
        languageButton.backgroundColor = UIColor.secondarySystemGroupedBackground
        languageButton.layer.cornerRadius = 16
        languageButton.layer.cornerCurve = .continuous
        languageButton.addTarget(self, action: #selector(toggleLanguage), for: .touchUpInside)
        languageButton.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(languageButton)

        NSLayoutConstraint.activate([
            languageButton.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 18),
            languageButton.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -18),
            languageButton.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor, constant: -12),
            languageButton.heightAnchor.constraint(equalToConstant: 48)
        ])
        updateLanguageButtonTitle()
    }

    func updateLanguage(_ language: AppLanguage) {
        self.language = language
        applyLanguage()
        tableView.reloadData()
        updateAddAccountButtonTitle()
        updateLanguageButtonTitle()
    }

    private func applyLanguage() {
        title = language.text("我的帐号", "My Accounts")
        navigationItem.leftBarButtonItem = UIBarButtonItem(
            title: language.text("返回", "Back"),
            style: .plain,
            target: self,
            action: #selector(popBack)
        )
    }

    private func updateLanguageButtonTitle() {
        languageButton.setTitle(language.toggleTitle, for: .normal)
    }

    private func updateAddAccountButtonTitle() {
        addAccountButton.setTitle(language.text(" 添加帐号", " Add Account"), for: .normal)
    }

    private func profile(for account: ChatParticipant) -> ContactCardProfile {
        contactCards[account.id] ?? ContactCardProfile(
            accountID: account.id,
            displayName: account.displayName,
            role: "个人名片",
            company: "",
            wechatID: "",
            phone: "",
            bio: language.text("这是\(account.displayName)的个人名片。", "This is \(account.displayName)'s contact card."),
            styleIndex: 0
        )
    }

    private func defaultNewAccountProfile() -> ContactCardProfile {
        let number = accounts.count + 1
        let displayName = language.text("新帐号\(number)", "New Account \(number)")
        return ContactCardProfile(
            accountID: UUID(),
            displayName: displayName,
            role: language.text("个人名片", "Contact Card"),
            company: "",
            wechatID: "account_\(number)",
            phone: "",
            bio: language.text("这是\(displayName)的个人名片。", "This is \(displayName)'s contact card."),
            styleIndex: number % 4
        )
    }

    @objc private func addAccount() {
        let controller = AccountLoginViewController(language: language)
        controller.onLogin = { [weak self] profile in
            self?.appendLoggedInAccount(profile)
        }
        navigationController?.pushViewController(controller, animated: true)
    }

    private func appendLoggedInAccount(_ profile: ContactCardProfile) {
        guard let account = onAddAccount?(profile) else { return }
        accounts.append(account)
        contactCards[account.id] = profile
        selectedAccountID = account.id
        tableView.reloadData()
        onSelectAccount?(account.id)
        let indexPath = IndexPath(row: accounts.count - 1, section: 0)
        navigationController?.popToViewController(self, animated: true)
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.25) { [weak self] in
            self?.tableView.scrollToRow(at: indexPath, at: .middle, animated: true)
        }
    }

    @objc private func configureAccount(_ sender: UIButton) {
        guard accounts.indices.contains(sender.tag) else { return }
        let account = accounts[sender.tag]
        let controller = ContactCardDesignerViewController(
            account: account,
            profile: profile(for: account),
            canEdit: true,
            showsSendButton: false,
            language: language
        )
        controller.onSave = { [weak self] updatedProfile in
            guard let self else { return }
            self.contactCards[updatedProfile.accountID] = updatedProfile
            self.tableView.reloadData()
            self.onSaveProfile?(updatedProfile)
        }
        navigationController?.pushViewController(controller, animated: true)
    }

    @objc private func removeAccount(_ sender: UIButton) {
        guard accounts.indices.contains(sender.tag) else { return }
        guard accounts.count > 1 else {
            let alert = UIAlertController(
                title: language.text("至少保留一个帐号", "Keep at least one account"),
                message: nil,
                preferredStyle: .alert
            )
            alert.addAction(UIAlertAction(title: language.text("知道了", "OK"), style: .default))
            present(alert, animated: true)
            return
        }

        let account = accounts[sender.tag]
        guard let nextSelectedID = onRemoveAccount?(account.id) else { return }
        accounts.remove(at: sender.tag)
        contactCards[account.id] = nil
        selectedAccountID = nextSelectedID
        tableView.reloadData()
    }

    @objc private func close() {
        dismiss(animated: true)
    }

    @objc private func toggleLanguage() {
        onToggleLanguage?()
    }

    @objc private func popBack() {
        navigationController?.popViewController(animated: true)
    }
}

extension MyAccountsViewController: UITableViewDataSource, UITableViewDelegate {
    func numberOfSections(in tableView: UITableView) -> Int {
        1
    }

    func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        accounts.count
    }

    func tableView(_ tableView: UITableView, titleForHeaderInSection section: Int) -> String? {
        language.text("主屏幕右侧上方帐号", "Accounts on the main screen")
    }

    func tableView(_ tableView: UITableView, titleForFooterInSection section: Int) -> String? {
        language.text(
            "点击帐号切换当前发送身份，点击配置可编辑帐号名片资料。",
            "Tap an account to switch sender identity. Tap Configure to edit its contact card."
        )
    }

    func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = tableView.dequeueReusableCell(withIdentifier: "AccountCell", for: indexPath)
        let account = accounts[indexPath.row]
        let profile = profile(for: account)
        let isSelected = account.id == selectedAccountID

        var configuration = UIListContentConfiguration.subtitleCell()
        configuration.text = profile.displayName
        configuration.secondaryText = [
            profile.role,
            profile.company,
            profile.wechatID.isEmpty ? "" : "\(language.text("微信", "WeChat")) \(profile.wechatID)"
        ].filter { !$0.isEmpty }.joined(separator: " · ")
        if configuration.secondaryText?.isEmpty != false {
            configuration.secondaryText = language.text("未配置帐号资料", "No account profile configured")
        }
        configuration.image = UIImage(systemName: isSelected ? "checkmark.circle.fill" : "person.crop.circle.fill")
        configuration.imageProperties.tintColor = isSelected ? UIColor.systemGreen : account.tintColor
        configuration.textProperties.font = .systemFont(ofSize: 16, weight: .semibold)
        configuration.secondaryTextProperties.font = .systemFont(ofSize: 12.5, weight: .regular)
        configuration.secondaryTextProperties.color = .secondaryLabel
        cell.contentConfiguration = configuration

        let actionStack = UIStackView()
        actionStack.axis = .horizontal
        actionStack.alignment = .center
        actionStack.spacing = 10

        let configureButton = UIButton(type: .system)
        configureButton.setTitle(language.text("配置", "Configure"), for: .normal)
        configureButton.titleLabel?.font = .systemFont(ofSize: 14, weight: .semibold)
        configureButton.tintColor = .systemBlue
        configureButton.tag = indexPath.row
        configureButton.addTarget(self, action: #selector(configureAccount(_:)), for: .touchUpInside)

        let removeButton = UIButton(type: .system)
        removeButton.setTitle(language.text("移除", "Remove"), for: .normal)
        removeButton.titleLabel?.font = .systemFont(ofSize: 14, weight: .semibold)
        removeButton.tintColor = .systemRed
        removeButton.tag = indexPath.row
        removeButton.addTarget(self, action: #selector(removeAccount(_:)), for: .touchUpInside)
        removeButton.isEnabled = accounts.count > 1
        removeButton.alpha = accounts.count > 1 ? 1 : 0.35

        actionStack.addArrangedSubview(configureButton)
        actionStack.addArrangedSubview(removeButton)
        actionStack.frame = CGRect(x: 0, y: 0, width: language == .zh ? 86 : 140, height: 34)
        cell.accessoryView = actionStack
        cell.selectionStyle = .default
        return cell
    }

    func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        tableView.deselectRow(at: indexPath, animated: true)
        selectedAccountID = accounts[indexPath.row].id
        tableView.reloadData()
        onSelectAccount?(selectedAccountID)
    }
}

final class AccountLoginViewController: UIViewController {
    var onLogin: ((ContactCardProfile) -> Void)?

    private let language: AppLanguage
    private let scrollView = UIScrollView()
    private let contentStack = UIStackView()
    private let titleLabel = UILabel()
    private let subtitleLabel = UILabel()
    private let modeControl = UISegmentedControl()
    private let accountField = UITextField()
    private let secretField = UITextField()
    private let codeButton = UIButton(type: .system)
    private let loginButton = UIButton(type: .system)
    private var countdownTimer: Timer?
    private var countdownRemaining = 0

    init(language: AppLanguage) {
        self.language = language
        super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    deinit {
        countdownTimer?.invalidate()
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        title = language.text("登录帐号", "Login Account")
        view.backgroundColor = UIColor.systemGroupedBackground
        navigationItem.leftBarButtonItem = UIBarButtonItem(
            title: language.text("返回", "Back"),
            style: .plain,
            target: self,
            action: #selector(back)
        )
        configureLayout()
        configureContent()
    }

    private func configureLayout() {
        scrollView.keyboardDismissMode = .interactive
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
            contentStack.topAnchor.constraint(equalTo: scrollView.contentLayoutGuide.topAnchor, constant: 24),
            contentStack.leadingAnchor.constraint(equalTo: scrollView.frameLayoutGuide.leadingAnchor, constant: 18),
            contentStack.trailingAnchor.constraint(equalTo: scrollView.frameLayoutGuide.trailingAnchor, constant: -18),
            contentStack.bottomAnchor.constraint(equalTo: scrollView.contentLayoutGuide.bottomAnchor, constant: -28)
        ])
    }

    private func configureContent() {
        titleLabel.text = language.text("添加登录帐号", "Add Login Account")
        titleLabel.font = .systemFont(ofSize: 26, weight: .bold)
        titleLabel.textColor = .label

        subtitleLabel.text = language.text(
            "使用微信号或手机号登录。当前为本地模拟登录，后续可替换为真实接口。",
            "Log in with WeChat ID or phone. This is a local simulated login for now."
        )
        subtitleLabel.font = .systemFont(ofSize: 14)
        subtitleLabel.textColor = .secondaryLabel
        subtitleLabel.numberOfLines = 0

        modeControl.insertSegment(withTitle: language.text("密码", "Password"), at: 0, animated: false)
        modeControl.insertSegment(withTitle: language.text("验证码", "Code"), at: 1, animated: false)
        modeControl.selectedSegmentIndex = 0
        modeControl.addTarget(self, action: #selector(loginModeChanged), for: .valueChanged)

        configureField(
            accountField,
            placeholder: language.text("微信号 / 手机号", "WeChat ID / Phone"),
            keyboardType: .default,
            isSecure: false
        )
        configureField(
            secretField,
            placeholder: language.text("请输入密码", "Enter password"),
            keyboardType: .default,
            isSecure: true
        )

        codeButton.setTitle(language.text("获取验证码", "Get Code"), for: .normal)
        codeButton.titleLabel?.font = .systemFont(ofSize: 14, weight: .semibold)
        codeButton.tintColor = .systemBlue
        codeButton.backgroundColor = .secondarySystemGroupedBackground
        codeButton.layer.cornerRadius = 12
        codeButton.layer.cornerCurve = .continuous
        codeButton.isHidden = true
        codeButton.addTarget(self, action: #selector(requestVerificationCode), for: .touchUpInside)
        codeButton.heightAnchor.constraint(equalToConstant: 44).isActive = true

        loginButton.setTitle(language.text("登录并添加帐号", "Login and Add Account"), for: .normal)
        loginButton.titleLabel?.font = .systemFont(ofSize: 16, weight: .semibold)
        loginButton.tintColor = .white
        loginButton.backgroundColor = UIColor(red: 0.47, green: 0.83, blue: 0.08, alpha: 1)
        loginButton.layer.cornerRadius = 18
        loginButton.layer.cornerCurve = .continuous
        loginButton.addTarget(self, action: #selector(login), for: .touchUpInside)
        loginButton.heightAnchor.constraint(equalToConstant: 50).isActive = true

        [titleLabel, subtitleLabel, modeControl, accountField, secretField, codeButton, loginButton].forEach {
            contentStack.addArrangedSubview($0)
        }
    }

    private func configureField(
        _ field: UITextField,
        placeholder: String,
        keyboardType: UIKeyboardType,
        isSecure: Bool
    ) {
        field.placeholder = placeholder
        field.keyboardType = keyboardType
        field.isSecureTextEntry = isSecure
        field.autocorrectionType = .no
        field.autocapitalizationType = .none
        field.clearButtonMode = .whileEditing
        field.font = .systemFont(ofSize: 15)
        field.textColor = .label
        field.backgroundColor = .secondarySystemGroupedBackground
        field.layer.cornerRadius = 12
        field.layer.cornerCurve = .continuous
        field.leftView = UIView(frame: CGRect(x: 0, y: 0, width: 12, height: 1))
        field.leftViewMode = .always
        field.heightAnchor.constraint(equalToConstant: 48).isActive = true
    }

    @objc private func loginModeChanged() {
        let usesCode = modeControl.selectedSegmentIndex == 1
        secretField.text = nil
        secretField.placeholder = usesCode
            ? language.text("请输入验证码", "Enter verification code")
            : language.text("请输入密码", "Enter password")
        secretField.keyboardType = usesCode ? .numberPad : .default
        secretField.isSecureTextEntry = !usesCode
        codeButton.isHidden = !usesCode
    }

    @objc private func requestVerificationCode() {
        let account = accountField.text?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        guard !account.isEmpty else {
            showInlineNotice(language.text("请先输入微信号或手机号", "Enter WeChat ID or phone first"))
            return
        }
        countdownRemaining = 60
        updateCodeButtonForCountdown()
        countdownTimer?.invalidate()
        countdownTimer = Timer.scheduledTimer(withTimeInterval: 1, repeats: true) { [weak self] timer in
            guard let self else {
                timer.invalidate()
                return
            }
            self.countdownRemaining -= 1
            if self.countdownRemaining <= 0 {
                timer.invalidate()
                self.countdownTimer = nil
                self.codeButton.isEnabled = true
                self.codeButton.setTitle(self.language.text("重新获取验证码", "Resend Code"), for: .normal)
            } else {
                self.updateCodeButtonForCountdown()
            }
        }
        showInlineNotice(language.text("验证码已发送（模拟）", "Verification code sent (simulated)"))
    }

    private func updateCodeButtonForCountdown() {
        codeButton.isEnabled = false
        codeButton.setTitle(language.text("\(countdownRemaining) 秒后重发", "Resend in \(countdownRemaining)s"), for: .normal)
    }

    @objc private func login() {
        let accountText = accountField.text?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        let secret = secretField.text?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        guard !accountText.isEmpty else {
            showInlineNotice(language.text("请输入微信号或手机号", "Enter WeChat ID or phone"))
            return
        }
        guard !secret.isEmpty else {
            showInlineNotice(modeControl.selectedSegmentIndex == 1
                ? language.text("请输入验证码", "Enter verification code")
                : language.text("请输入密码", "Enter password"))
            return
        }

        let displayName = accountDisplayName(from: accountText)
        let profile = ContactCardProfile(
            accountID: UUID(),
            displayName: displayName,
            role: language.text("登录帐号", "Login Account"),
            company: "",
            wechatID: accountText,
            phone: accountText.allSatisfy(\.isNumber) ? accountText : "",
            bio: language.text("通过登录新增的帐号。", "Account added by login."),
            styleIndex: safeHashIndex(accountText, modulo: 4)
        )
        onLogin?(profile)
    }

    private func accountDisplayName(from account: String) -> String {
        let trimmed = account.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.count <= 4 {
            return trimmed.isEmpty ? language.text("新帐号", "New Account") : trimmed
        }
        return "wx_\(trimmed.suffix(4))"
    }

    private func showInlineNotice(_ message: String) {
        let alert = UIAlertController(title: message, message: nil, preferredStyle: .alert)
        present(alert, animated: true)
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.1) { [weak alert] in
            alert?.dismiss(animated: true)
        }
    }

    @objc private func back() {
        navigationController?.popViewController(animated: true)
    }
}

final class ContactCardDesignerViewController: UIViewController {
    var onSave: ((ContactCardProfile) -> Void)?
    var onSend: ((ContactCardProfile) -> Void)?

    private let account: ChatParticipant
    private let canEdit: Bool
    private let showsSendButton: Bool
    private let language: AppLanguage
    private var profile: ContactCardProfile
    private let scrollView = UIScrollView()
    private let contentStack = UIStackView()
    private let previewCard = UIView()
    private let accentView = UIView()
    private let avatarLabel = UILabel()
    private let namePreviewLabel = UILabel()
    private let rolePreviewLabel = UILabel()
    private let metaPreviewLabel = UILabel()
    private let bioPreviewLabel = UILabel()
    private let styleControl = UISegmentedControl(items: ["经典", "商务", "科技", "暖橙"])
    private let nameField = UITextField()
    private let roleField = UITextField()
    private let companyField = UITextField()
    private let wechatField = UITextField()
    private let phoneField = UITextField()
    private let bioField = UITextField()
    private weak var activeField: UITextField?
    private var keyboardObservers: [NSObjectProtocol] = []
    private var keyboardBottomInset: CGFloat = 0

    init(
        account: ChatParticipant,
        profile: ContactCardProfile,
        canEdit: Bool,
        showsSendButton: Bool = true,
        language: AppLanguage = .zh
    ) {
        self.account = account
        self.profile = profile
        self.canEdit = canEdit
        self.showsSendButton = showsSendButton
        self.language = language
        super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        title = showsSendButton
            ? (canEdit ? language.text("设计个人名片", "Design Contact Card") : language.text("个人名片", "Contact Card"))
            : language.text("配置帐号", "Configure Account")
        view.backgroundColor = UIColor.systemGroupedBackground
        navigationItem.leftBarButtonItem = UIBarButtonItem(
            title: language.text("取消", "Cancel"),
            style: .plain,
            target: self,
            action: #selector(close)
        )
        if canEdit {
            navigationItem.rightBarButtonItem = UIBarButtonItem(
                title: language.text("保存", "Save"),
                style: .done,
                target: self,
                action: #selector(saveProfile)
            )
        }
        configureLayout()
        configurePreview()
        configureForm()
        setupKeyboardHandling()
        reloadPreview()
    }

    deinit {
        keyboardObservers.forEach(NotificationCenter.default.removeObserver)
    }

    private func configureLayout() {
        scrollView.translatesAutoresizingMaskIntoConstraints = false
        scrollView.keyboardDismissMode = .interactive
        scrollView.alwaysBounceVertical = true
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
            contentStack.topAnchor.constraint(equalTo: scrollView.contentLayoutGuide.topAnchor, constant: 16),
            contentStack.leadingAnchor.constraint(equalTo: scrollView.frameLayoutGuide.leadingAnchor, constant: 16),
            contentStack.trailingAnchor.constraint(equalTo: scrollView.frameLayoutGuide.trailingAnchor, constant: -16),
            contentStack.bottomAnchor.constraint(equalTo: scrollView.contentLayoutGuide.bottomAnchor, constant: -22)
        ])
    }

    private func configurePreview() {
        previewCard.backgroundColor = UIColor.secondarySystemGroupedBackground
        previewCard.layer.cornerRadius = 18
        previewCard.layer.cornerCurve = .continuous
        previewCard.layer.shadowColor = UIColor.black.cgColor
        previewCard.layer.shadowOpacity = 0.10
        previewCard.layer.shadowRadius = 18
        previewCard.layer.shadowOffset = CGSize(width: 0, height: 8)
        previewCard.translatesAutoresizingMaskIntoConstraints = false
        contentStack.addArrangedSubview(previewCard)

        accentView.layer.cornerRadius = 3
        accentView.translatesAutoresizingMaskIntoConstraints = false
        avatarLabel.textAlignment = .center
        avatarLabel.textColor = .white
        avatarLabel.font = .systemFont(ofSize: 24, weight: .bold)
        avatarLabel.layer.cornerRadius = 28
        avatarLabel.layer.cornerCurve = .continuous
        avatarLabel.clipsToBounds = true
        avatarLabel.translatesAutoresizingMaskIntoConstraints = false
        namePreviewLabel.font = .systemFont(ofSize: 22, weight: .bold)
        namePreviewLabel.textColor = .label
        namePreviewLabel.translatesAutoresizingMaskIntoConstraints = false
        rolePreviewLabel.font = .systemFont(ofSize: 14, weight: .semibold)
        rolePreviewLabel.textColor = .secondaryLabel
        rolePreviewLabel.numberOfLines = 2
        rolePreviewLabel.translatesAutoresizingMaskIntoConstraints = false
        metaPreviewLabel.font = .systemFont(ofSize: 13)
        metaPreviewLabel.textColor = .secondaryLabel
        metaPreviewLabel.numberOfLines = 2
        metaPreviewLabel.translatesAutoresizingMaskIntoConstraints = false
        bioPreviewLabel.font = .systemFont(ofSize: 13)
        bioPreviewLabel.textColor = .label
        bioPreviewLabel.numberOfLines = 3
        bioPreviewLabel.translatesAutoresizingMaskIntoConstraints = false

        [accentView, avatarLabel, namePreviewLabel, rolePreviewLabel, metaPreviewLabel, bioPreviewLabel].forEach {
            previewCard.addSubview($0)
        }

        NSLayoutConstraint.activate([
            previewCard.heightAnchor.constraint(greaterThanOrEqualToConstant: 188),
            accentView.leadingAnchor.constraint(equalTo: previewCard.leadingAnchor, constant: 18),
            accentView.topAnchor.constraint(equalTo: previewCard.topAnchor, constant: 20),
            accentView.bottomAnchor.constraint(equalTo: previewCard.bottomAnchor, constant: -20),
            accentView.widthAnchor.constraint(equalToConstant: 5),
            avatarLabel.leadingAnchor.constraint(equalTo: accentView.trailingAnchor, constant: 18),
            avatarLabel.topAnchor.constraint(equalTo: previewCard.topAnchor, constant: 24),
            avatarLabel.widthAnchor.constraint(equalToConstant: 56),
            avatarLabel.heightAnchor.constraint(equalToConstant: 56),
            namePreviewLabel.leadingAnchor.constraint(equalTo: avatarLabel.trailingAnchor, constant: 14),
            namePreviewLabel.trailingAnchor.constraint(equalTo: previewCard.trailingAnchor, constant: -18),
            namePreviewLabel.topAnchor.constraint(equalTo: avatarLabel.topAnchor, constant: 2),
            rolePreviewLabel.leadingAnchor.constraint(equalTo: namePreviewLabel.leadingAnchor),
            rolePreviewLabel.trailingAnchor.constraint(equalTo: namePreviewLabel.trailingAnchor),
            rolePreviewLabel.topAnchor.constraint(equalTo: namePreviewLabel.bottomAnchor, constant: 7),
            metaPreviewLabel.leadingAnchor.constraint(equalTo: avatarLabel.leadingAnchor),
            metaPreviewLabel.trailingAnchor.constraint(equalTo: previewCard.trailingAnchor, constant: -18),
            metaPreviewLabel.topAnchor.constraint(equalTo: avatarLabel.bottomAnchor, constant: 18),
            bioPreviewLabel.leadingAnchor.constraint(equalTo: metaPreviewLabel.leadingAnchor),
            bioPreviewLabel.trailingAnchor.constraint(equalTo: metaPreviewLabel.trailingAnchor),
            bioPreviewLabel.topAnchor.constraint(equalTo: metaPreviewLabel.bottomAnchor, constant: 10),
            bioPreviewLabel.bottomAnchor.constraint(lessThanOrEqualTo: previewCard.bottomAnchor, constant: -18)
        ])
    }

    private func configureForm() {
        styleControl.selectedSegmentIndex = profile.styleIndex
        styleControl.addTarget(self, action: #selector(formChanged), for: .valueChanged)
        styleControl.isEnabled = canEdit
        contentStack.addArrangedSubview(styleControl)

        addField(nameField, title: language.text("名字", "Name"), text: profile.displayName)
        addField(roleField, title: language.text("身份", "Role"), text: profile.role)
        addField(companyField, title: language.text("公司 / 组织", "Company / Organization"), text: profile.company)
        addField(wechatField, title: language.text("微信号", "WeChat ID"), text: profile.wechatID)
        addField(phoneField, title: language.text("手机号", "Phone"), text: profile.phone)
        addField(bioField, title: language.text("简介", "Bio"), text: profile.bio)

        if showsSendButton {
            let sendButton = UIButton(type: .system)
            sendButton.setTitle(
                canEdit ? language.text("发送这张名片", "Send this card") : language.text("转发这张名片", "Forward this card"),
                for: .normal
            )
            sendButton.titleLabel?.font = .systemFont(ofSize: 16, weight: .semibold)
            sendButton.tintColor = .white
            sendButton.backgroundColor = profile.accentColor
            sendButton.layer.cornerRadius = 20
            sendButton.layer.cornerCurve = .continuous
            sendButton.heightAnchor.constraint(equalToConstant: 44).isActive = true
            sendButton.addTarget(self, action: #selector(sendProfile), for: .touchUpInside)
            contentStack.addArrangedSubview(sendButton)
        }
    }

    private func addField(_ field: UITextField, title: String, text: String) {
        field.placeholder = title
        field.text = text
        field.font = .systemFont(ofSize: 15)
        field.textColor = .label
        field.backgroundColor = .secondarySystemGroupedBackground
        field.layer.cornerRadius = 12
        field.layer.cornerCurve = .continuous
        field.clearButtonMode = .whileEditing
        field.delegate = self
        field.returnKeyType = field === bioField ? .done : .next
        field.isEnabled = canEdit
        field.leftView = UIView(frame: CGRect(x: 0, y: 0, width: 12, height: 1))
        field.leftViewMode = .always
        field.heightAnchor.constraint(equalToConstant: 46).isActive = true
        field.addTarget(self, action: #selector(formChanged), for: .editingChanged)
        field.addTarget(self, action: #selector(fieldEditingDidBegin(_:)), for: .editingDidBegin)
        field.addTarget(self, action: #selector(fieldEditingChanged(_:)), for: .editingChanged)
        contentStack.addArrangedSubview(field)
    }

    private func setupKeyboardHandling() {
        let center = NotificationCenter.default
        keyboardObservers.append(center.addObserver(
            forName: UIResponder.keyboardWillChangeFrameNotification,
            object: nil,
            queue: .main
        ) { [weak self] notification in
            self?.handleKeyboardChange(notification)
        })
        keyboardObservers.append(center.addObserver(
            forName: UIResponder.keyboardWillHideNotification,
            object: nil,
            queue: .main
        ) { [weak self] notification in
            self?.handleKeyboardHide(notification)
        })
    }

    private func handleKeyboardChange(_ notification: Notification) {
        guard isViewLoaded else { return }
        let keyboardFrame = (notification.userInfo?[UIResponder.keyboardFrameEndUserInfoKey] as? NSValue)?.cgRectValue ?? .zero
        let convertedFrame = view.convert(keyboardFrame, from: nil)
        let overlap = max(0, view.bounds.maxY - convertedFrame.minY)
        keyboardBottomInset = overlap + 22
        animateKeyboardInset(bottom: overlap + 18, notification: notification) { [weak self] in
            self?.scrollActiveFieldIntoView(animated: false)
        }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.05) { [weak self] in
            self?.scrollActiveFieldIntoView(animated: true)
        }
    }

    private func handleKeyboardHide(_ notification: Notification) {
        keyboardBottomInset = 0
        animateKeyboardInset(bottom: 0, notification: notification)
    }

    private func animateKeyboardInset(
        bottom: CGFloat,
        notification: Notification,
        animations: (() -> Void)? = nil
    ) {
        let duration = (notification.userInfo?[UIResponder.keyboardAnimationDurationUserInfoKey] as? NSNumber)?.doubleValue ?? 0.25
        let curveRaw = (notification.userInfo?[UIResponder.keyboardAnimationCurveUserInfoKey] as? NSNumber)?.uintValue ?? UInt(UIView.AnimationOptions.curveEaseInOut.rawValue)
        let options = UIView.AnimationOptions(rawValue: curveRaw << 16)
        UIView.animate(withDuration: duration, delay: 0, options: options) {
            self.scrollView.contentInset.bottom = bottom
            self.scrollView.verticalScrollIndicatorInsets.bottom = bottom
            animations?()
        }
    }

    private func scrollActiveFieldIntoView(animated: Bool) {
        guard let activeField else { return }
        view.layoutIfNeeded()
        scrollView.layoutIfNeeded()

        let fieldRect = activeField.convert(activeField.bounds, to: scrollView)
        let topPadding: CGFloat = 20
        let bottomPadding: CGFloat = 42
        let currentOffsetY = scrollView.contentOffset.y
        let minOffsetY = -scrollView.adjustedContentInset.top
        let maxOffsetY = max(
            minOffsetY,
            scrollView.contentSize.height - scrollView.bounds.height + scrollView.adjustedContentInset.bottom
        )
        let visibleBottom = currentOffsetY + scrollView.bounds.height - keyboardBottomInset

        var targetOffsetY = currentOffsetY
        if fieldRect.maxY + bottomPadding > visibleBottom {
            targetOffsetY = fieldRect.maxY + bottomPadding - (scrollView.bounds.height - keyboardBottomInset)
        } else if fieldRect.minY - topPadding < currentOffsetY + scrollView.adjustedContentInset.top {
            targetOffsetY = fieldRect.minY - topPadding - scrollView.adjustedContentInset.top
        }
        targetOffsetY = min(max(targetOffsetY, minOffsetY), maxOffsetY)

        guard abs(targetOffsetY - currentOffsetY) > 1 else { return }
        scrollView.setContentOffset(CGPoint(x: scrollView.contentOffset.x, y: targetOffsetY), animated: animated)
    }

    @objc private func formChanged() {
        profile = makeProfileFromFields()
        reloadPreview()
    }

    @objc private func fieldEditingDidBegin(_ field: UITextField) {
        activeField = field
        DispatchQueue.main.async { [weak self] in
            self?.scrollActiveFieldIntoView(animated: true)
        }
    }

    @objc private func fieldEditingChanged(_ field: UITextField) {
        activeField = field
        DispatchQueue.main.async { [weak self] in
            self?.scrollActiveFieldIntoView(animated: false)
        }
    }

    private func makeProfileFromFields() -> ContactCardProfile {
        let name = trimmed(nameField.text).isEmpty ? account.displayName : trimmed(nameField.text)
        return ContactCardProfile(
            accountID: account.id,
            displayName: name,
            role: trimmed(roleField.text),
            company: trimmed(companyField.text),
            wechatID: trimmed(wechatField.text),
            phone: trimmed(phoneField.text),
            bio: trimmed(bioField.text),
            styleIndex: max(styleControl.selectedSegmentIndex, 0)
        )
    }

    private func reloadPreview() {
        let updated = makeProfileFromFields()
        profile = updated
        accentView.backgroundColor = updated.accentColor
        avatarLabel.backgroundColor = updated.accentColor
        avatarLabel.text = String(updated.displayName.prefix(1))
        namePreviewLabel.text = updated.displayName
        rolePreviewLabel.text = [updated.role, updated.company].filter { !$0.isEmpty }.joined(separator: " · ")
        let meta = [
            updated.wechatID.isEmpty ? "" : "\(language.text("微信", "WeChat")) \(updated.wechatID)",
            updated.phone.isEmpty ? "" : "\(language.text("电话", "Phone")) \(updated.phone)"
        ].filter { !$0.isEmpty }.joined(separator: "   ")
        metaPreviewLabel.text = meta.isEmpty ? language.text("未填写联系方式", "No contact info") : meta
        bioPreviewLabel.text = updated.bio.isEmpty ? language.text("未填写简介", "No bio") : updated.bio
        if let sendButton = contentStack.arrangedSubviews.compactMap({ $0 as? UIButton }).last {
            sendButton.backgroundColor = updated.accentColor
        }
    }

    private func trimmed(_ text: String?) -> String {
        text?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
    }

    @objc private func saveProfile() {
        view.endEditing(true)
        let updated = makeProfileFromFields()
        onSave?(updated)
        if showsSendButton {
            showSavedNotice()
        } else {
            closeAfterAccountConfiguration()
        }
    }

    @objc private func sendProfile() {
        let updated = makeProfileFromFields()
        onSend?(updated)
        dismiss(animated: true)
    }

    @objc private func close() {
        if showsSendButton {
            dismiss(animated: true)
        } else {
            closeAfterAccountConfiguration()
        }
    }

    private func closeAfterAccountConfiguration() {
        if let navigationController, navigationController.viewControllers.first !== self {
            navigationController.popViewController(animated: true)
        } else {
            dismiss(animated: true)
        }
    }

    private func showSavedNotice() {
        let alert = UIAlertController(title: language.text("已保存", "Saved"), message: nil, preferredStyle: .alert)
        present(alert, animated: true)
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.7) {
            alert.dismiss(animated: true)
        }
    }
}

extension ContactCardDesignerViewController: UITextFieldDelegate {
    func textFieldDidBeginEditing(_ textField: UITextField) {
        activeField = textField
        DispatchQueue.main.async { [weak self] in
            self?.scrollActiveFieldIntoView(animated: true)
        }
    }

    func textFieldDidEndEditing(_ textField: UITextField) {
        if activeField === textField {
            activeField = nil
        }
    }

    func textFieldShouldReturn(_ textField: UITextField) -> Bool {
        let fields = [nameField, roleField, companyField, wechatField, phoneField, bioField]
        guard let index = fields.firstIndex(where: { $0 === textField }) else {
            textField.resignFirstResponder()
            return true
        }
        let nextIndex = fields.index(after: index)
        if nextIndex < fields.endIndex {
            fields[nextIndex].becomeFirstResponder()
        } else {
            textField.resignFirstResponder()
        }
        return true
    }
}

final class ContactCardDetailViewController: UIViewController {
    private let profile: ContactCardProfile
    private let avatarURL: URL?
    private let internalWxid: String
    private let stackView = UIStackView()
    private var avatarLoadTask: MediaThumbnailRequest?

    init(profile: ContactCardProfile, avatarURL: URL? = nil, internalWxid: String = "") {
        self.profile = profile
        self.avatarURL = avatarURL
        self.internalWxid = internalWxid
        super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    deinit { avatarLoadTask?.cancel() }

    override func viewDidLoad() {
        super.viewDidLoad()
        title = "个人名片详情"
        view.backgroundColor = UIColor.systemGroupedBackground
        configureLayout()
    }

    private func configureLayout() {
        let scrollView = UIScrollView()
        scrollView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(scrollView)

        stackView.axis = .vertical
        stackView.spacing = 14
        stackView.translatesAutoresizingMaskIntoConstraints = false
        scrollView.addSubview(stackView)

        NSLayoutConstraint.activate([
            scrollView.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
            scrollView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            scrollView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            scrollView.bottomAnchor.constraint(equalTo: view.bottomAnchor),
            stackView.topAnchor.constraint(equalTo: scrollView.contentLayoutGuide.topAnchor, constant: 16),
            stackView.leadingAnchor.constraint(equalTo: scrollView.frameLayoutGuide.leadingAnchor, constant: 16),
            stackView.trailingAnchor.constraint(equalTo: scrollView.frameLayoutGuide.trailingAnchor, constant: -16),
            stackView.bottomAnchor.constraint(equalTo: scrollView.contentLayoutGuide.bottomAnchor, constant: -22)
        ])

        stackView.addArrangedSubview(makeHeaderCard())
        stackView.addArrangedSubview(makeInfoRow(title: "微信号", value: profile.wechatID))
        stackView.addArrangedSubview(makeInfoRow(title: "WXID", value: internalWxid))
        stackView.addArrangedSubview(makeInfoRow(title: "手机号", value: profile.phone))
    }

    private func makeHeaderCard() -> UIView {
        let card = UIView()
        card.backgroundColor = UIColor.secondarySystemGroupedBackground
        card.layer.cornerRadius = 18
        card.layer.cornerCurve = .continuous
        card.translatesAutoresizingMaskIntoConstraints = false

        let accent = UIView()
        accent.backgroundColor = profile.accentColor
        accent.layer.cornerRadius = 3
        accent.translatesAutoresizingMaskIntoConstraints = false

        let avatar = UIView()
        avatar.backgroundColor = profile.accentColor
        avatar.layer.cornerRadius = 32
        avatar.layer.cornerCurve = .continuous
        avatar.clipsToBounds = true
        avatar.translatesAutoresizingMaskIntoConstraints = false
        let fallback = UILabel()
        fallback.text = String(profile.displayName.prefix(1))
        fallback.textAlignment = .center
        fallback.textColor = .white
        fallback.font = .systemFont(ofSize: 28, weight: .bold)
        fallback.translatesAutoresizingMaskIntoConstraints = false
        let imageView = UIImageView()
        imageView.contentMode = .scaleAspectFill
        imageView.clipsToBounds = true
        imageView.isHidden = true
        imageView.translatesAutoresizingMaskIntoConstraints = false
        avatar.addSubview(fallback)
        avatar.addSubview(imageView)

        let nameLabel = UILabel()
        nameLabel.text = profile.displayName
        nameLabel.font = .systemFont(ofSize: 24, weight: .bold)
        nameLabel.textColor = .label
        nameLabel.translatesAutoresizingMaskIntoConstraints = false

        let summaryLabel = UILabel()
        summaryLabel.text = profile.wechatID.isEmpty ? "好友资料" : "微信号：\(profile.wechatID)"
        summaryLabel.font = .systemFont(ofSize: 14, weight: .medium)
        summaryLabel.textColor = .secondaryLabel
        summaryLabel.numberOfLines = 2
        summaryLabel.translatesAutoresizingMaskIntoConstraints = false

        [accent, avatar, nameLabel, summaryLabel].forEach { card.addSubview($0) }

        NSLayoutConstraint.activate([
            card.heightAnchor.constraint(greaterThanOrEqualToConstant: 136),
            accent.leadingAnchor.constraint(equalTo: card.leadingAnchor, constant: 18),
            accent.topAnchor.constraint(equalTo: card.topAnchor, constant: 20),
            accent.bottomAnchor.constraint(equalTo: card.bottomAnchor, constant: -20),
            accent.widthAnchor.constraint(equalToConstant: 5),
            avatar.leadingAnchor.constraint(equalTo: accent.trailingAnchor, constant: 18),
            avatar.centerYAnchor.constraint(equalTo: card.centerYAnchor),
            avatar.widthAnchor.constraint(equalToConstant: 64),
            avatar.heightAnchor.constraint(equalToConstant: 64),
            fallback.topAnchor.constraint(equalTo: avatar.topAnchor), fallback.leadingAnchor.constraint(equalTo: avatar.leadingAnchor),
            fallback.trailingAnchor.constraint(equalTo: avatar.trailingAnchor), fallback.bottomAnchor.constraint(equalTo: avatar.bottomAnchor),
            imageView.topAnchor.constraint(equalTo: avatar.topAnchor), imageView.leadingAnchor.constraint(equalTo: avatar.leadingAnchor),
            imageView.trailingAnchor.constraint(equalTo: avatar.trailingAnchor), imageView.bottomAnchor.constraint(equalTo: avatar.bottomAnchor),
            nameLabel.leadingAnchor.constraint(equalTo: avatar.trailingAnchor, constant: 14),
            nameLabel.trailingAnchor.constraint(equalTo: card.trailingAnchor, constant: -18),
            nameLabel.topAnchor.constraint(equalTo: avatar.topAnchor, constant: 4),
            summaryLabel.leadingAnchor.constraint(equalTo: nameLabel.leadingAnchor),
            summaryLabel.trailingAnchor.constraint(equalTo: nameLabel.trailingAnchor),
            summaryLabel.topAnchor.constraint(equalTo: nameLabel.bottomAnchor, constant: 8)
        ])
        guard let avatarURL else { return card }
        let targetSize = CGSize(width: 64, height: 64)
        if let cached = AvatarPipeline.shared.cachedImage(for: avatarURL, targetSize: targetSize) {
            imageView.image = cached
            imageView.isHidden = false
            fallback.isHidden = true
        } else {
            avatarLoadTask = AvatarPipeline.shared.load(avatarURL, targetSize: targetSize) { [weak imageView, weak fallback] image in
                imageView?.image = image
                imageView?.isHidden = image == nil
                fallback?.isHidden = image != nil
            }
        }
        return card
    }

    private func makeInfoRow(title: String, value: String, allowsMultipleLines: Bool = false) -> UIView {
        let container = UIView()
        container.backgroundColor = UIColor.secondarySystemGroupedBackground
        container.layer.cornerRadius = 12
        container.layer.cornerCurve = .continuous
        container.translatesAutoresizingMaskIntoConstraints = false

        let titleLabel = UILabel()
        titleLabel.text = title
        titleLabel.font = .systemFont(ofSize: 13, weight: .semibold)
        titleLabel.textColor = .secondaryLabel
        titleLabel.translatesAutoresizingMaskIntoConstraints = false

        let valueLabel = UILabel()
        valueLabel.text = value.isEmpty ? "未填写" : value
        valueLabel.font = .systemFont(ofSize: 15, weight: .regular)
        valueLabel.textColor = value.isEmpty ? .tertiaryLabel : .label
        valueLabel.numberOfLines = allowsMultipleLines ? 0 : 2
        valueLabel.translatesAutoresizingMaskIntoConstraints = false

        container.addSubview(titleLabel)
        container.addSubview(valueLabel)

        NSLayoutConstraint.activate([
            container.heightAnchor.constraint(greaterThanOrEqualToConstant: allowsMultipleLines ? 74 : 58),
            titleLabel.leadingAnchor.constraint(equalTo: container.leadingAnchor, constant: 14),
            titleLabel.topAnchor.constraint(equalTo: container.topAnchor, constant: 11),
            titleLabel.trailingAnchor.constraint(equalTo: container.trailingAnchor, constant: -14),
            valueLabel.leadingAnchor.constraint(equalTo: titleLabel.leadingAnchor),
            valueLabel.trailingAnchor.constraint(equalTo: titleLabel.trailingAnchor),
            valueLabel.topAnchor.constraint(equalTo: titleLabel.bottomAnchor, constant: 5),
            valueLabel.bottomAnchor.constraint(lessThanOrEqualTo: container.bottomAnchor, constant: -11)
        ])
        return container
    }

    @objc private func close() {
        dismiss(animated: true)
    }
}

final class FriendInfoHomeViewController: UIViewController {
    var onOpenContactCard: ((ContactCardProfile) -> Void)?
    var onOpenCustomerProfile: (() -> Void)?
    var onOpenLabelsAndPermissions: (() -> Void)?

    private let participant: ChatParticipant
    private let displayName: String
    private let remark: String
    private let profile: ContactCardProfile
    private let avatarURL: URL?
    private let associatedRows: [(String, String)]
    private let ownerAccountName: String
    private let latestMessage: ChatMessage?
    private let internalWxid: String
    private let stackView = UIStackView()
    private var avatarLoadTask: MediaThumbnailRequest?
    private var representedAvatarURL: URL?

    init(
        participant: ChatParticipant,
        displayName: String,
        remark: String,
        profile: ContactCardProfile,
        avatarURL: URL?,
        associatedRows: [(String, String)],
        ownerAccountName: String,
        latestMessage: ChatMessage?,
        internalWxid: String = ""
    ) {
        self.participant = participant
        self.displayName = displayName
        self.remark = remark
        self.profile = profile
        self.avatarURL = avatarURL
        self.associatedRows = associatedRows
        self.ownerAccountName = ownerAccountName
        self.latestMessage = latestMessage
        self.internalWxid = internalWxid
        super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    deinit {
        avatarLoadTask?.cancel()
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        title = "用户主页"
        view.backgroundColor = UIColor.systemGroupedBackground
        configureLayout()
    }

    private func configureLayout() {
        let scrollView = UIScrollView()
        scrollView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(scrollView)

        stackView.axis = .vertical
        stackView.spacing = 14
        stackView.translatesAutoresizingMaskIntoConstraints = false
        scrollView.addSubview(stackView)

        NSLayoutConstraint.activate([
            scrollView.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
            scrollView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            scrollView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            scrollView.bottomAnchor.constraint(equalTo: view.bottomAnchor),
            stackView.topAnchor.constraint(equalTo: scrollView.contentLayoutGuide.topAnchor, constant: 12),
            stackView.leadingAnchor.constraint(equalTo: scrollView.frameLayoutGuide.leadingAnchor, constant: 16),
            stackView.trailingAnchor.constraint(equalTo: scrollView.frameLayoutGuide.trailingAnchor, constant: -16),
            stackView.bottomAnchor.constraint(equalTo: scrollView.contentLayoutGuide.bottomAnchor, constant: -24)
        ])

        stackView.addArrangedSubview(makeHeaderCard())
        stackView.addArrangedSubview(makeSectionCard(rows: [
            ("微信号", profile.wechatID.isEmpty ? "未填写" : profile.wechatID),
            ("WXID", internalWxid.isEmpty ? "未填写" : internalWxid),
            ("备注", remark.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "未设置" : remark)
        ]))
        stackView.addArrangedSubview(makeActionCard())
    }

    private func makeHeaderCard() -> UIView {
        let card = UIView()
        card.backgroundColor = UIColor.secondarySystemGroupedBackground
        card.layer.cornerRadius = 14
        card.layer.cornerCurve = .continuous
        card.translatesAutoresizingMaskIntoConstraints = false

        let avatarContainer = UIView()
        avatarContainer.backgroundColor = participant.tintColor
        avatarContainer.layer.cornerRadius = 20
        avatarContainer.layer.cornerCurve = .continuous
        avatarContainer.clipsToBounds = true
        avatarContainer.translatesAutoresizingMaskIntoConstraints = false

        let avatarFallback = UILabel()
        avatarFallback.text = participant.initials
        avatarFallback.textAlignment = .center
        avatarFallback.textColor = .white
        avatarFallback.font = .systemFont(ofSize: 28, weight: .bold)
        avatarFallback.translatesAutoresizingMaskIntoConstraints = false

        let avatarImageView = UIImageView()
        avatarImageView.contentMode = .scaleAspectFill
        avatarImageView.clipsToBounds = true
        avatarImageView.isHidden = true
        avatarImageView.translatesAutoresizingMaskIntoConstraints = false
        avatarContainer.addSubview(avatarFallback)
        avatarContainer.addSubview(avatarImageView)
        loadAvatarIfNeeded(into: avatarImageView, fallbackLabel: avatarFallback)

        let nameLabel = UILabel()
        nameLabel.text = displayName
        nameLabel.font = .systemFont(ofSize: 24, weight: .bold)
        nameLabel.textColor = .label
        nameLabel.numberOfLines = 1
        nameLabel.translatesAutoresizingMaskIntoConstraints = false

        let subtitleLabel = UILabel()
        subtitleLabel.text = profileSubtitle()
        subtitleLabel.font = .systemFont(ofSize: 14, weight: .medium)
        subtitleLabel.textColor = .secondaryLabel
        subtitleLabel.numberOfLines = 2
        subtitleLabel.lineBreakMode = .byTruncatingMiddle
        subtitleLabel.translatesAutoresizingMaskIntoConstraints = false

        let badge = UILabel()
        badge.text = "好友联系人"
        badge.font = .systemFont(ofSize: 12, weight: .medium)
        badge.textColor = .secondaryLabel
        badge.translatesAutoresizingMaskIntoConstraints = false

        [avatarContainer, nameLabel, subtitleLabel, badge].forEach(card.addSubview)

        NSLayoutConstraint.activate([
            card.heightAnchor.constraint(greaterThanOrEqualToConstant: 142),
            avatarContainer.leadingAnchor.constraint(equalTo: card.leadingAnchor, constant: 18),
            avatarContainer.topAnchor.constraint(equalTo: card.topAnchor, constant: 22),
            avatarContainer.widthAnchor.constraint(equalToConstant: 72),
            avatarContainer.heightAnchor.constraint(equalToConstant: 72),
            avatarFallback.topAnchor.constraint(equalTo: avatarContainer.topAnchor),
            avatarFallback.leadingAnchor.constraint(equalTo: avatarContainer.leadingAnchor),
            avatarFallback.trailingAnchor.constraint(equalTo: avatarContainer.trailingAnchor),
            avatarFallback.bottomAnchor.constraint(equalTo: avatarContainer.bottomAnchor),
            avatarImageView.topAnchor.constraint(equalTo: avatarContainer.topAnchor),
            avatarImageView.leadingAnchor.constraint(equalTo: avatarContainer.leadingAnchor),
            avatarImageView.trailingAnchor.constraint(equalTo: avatarContainer.trailingAnchor),
            avatarImageView.bottomAnchor.constraint(equalTo: avatarContainer.bottomAnchor),
            nameLabel.leadingAnchor.constraint(equalTo: avatarContainer.trailingAnchor, constant: 16),
            nameLabel.trailingAnchor.constraint(equalTo: card.trailingAnchor, constant: -18),
            nameLabel.topAnchor.constraint(equalTo: avatarContainer.topAnchor, constant: 3),
            subtitleLabel.leadingAnchor.constraint(equalTo: nameLabel.leadingAnchor),
            subtitleLabel.trailingAnchor.constraint(equalTo: nameLabel.trailingAnchor),
            subtitleLabel.topAnchor.constraint(equalTo: nameLabel.bottomAnchor, constant: 8),
            badge.leadingAnchor.constraint(equalTo: nameLabel.leadingAnchor),
            badge.topAnchor.constraint(equalTo: subtitleLabel.bottomAnchor, constant: 9),
            badge.bottomAnchor.constraint(lessThanOrEqualTo: card.bottomAnchor, constant: -18)
        ])
        return card
    }

    private func loadAvatarIfNeeded(into imageView: UIImageView, fallbackLabel: UILabel) {
        guard let avatarURL else { return }
        representedAvatarURL = avatarURL
        imageView.accessibilityIdentifier = avatarURL.absoluteString
        let targetSize = CGSize(width: 72, height: 72)
        if let cached = AvatarPipeline.shared.cachedImage(for: avatarURL, targetSize: targetSize) {
            imageView.image = cached
            imageView.isHidden = false
            fallbackLabel.isHidden = true
            return
        }
        avatarLoadTask = AvatarPipeline.shared.load(
            avatarURL,
            targetSize: targetSize
        ) { [weak self, weak imageView, weak fallbackLabel] image in
            guard self?.representedAvatarURL == avatarURL,
                  imageView?.accessibilityIdentifier == avatarURL.absoluteString
            else { return }
            imageView?.image = image
            imageView?.isHidden = image == nil
            fallbackLabel?.isHidden = image != nil
        }
    }

    private func profileSubtitle() -> String {
        let values = [
            profile.wechatID.isEmpty ? "" : "微信号 \(profile.wechatID)",
            internalWxid.isEmpty ? "" : "WXID \(internalWxid)"
        ].filter { !$0.isEmpty }
        if !values.isEmpty {
            return values.joined(separator: "\n")
        }
        return profile.summary.isEmpty ? "好友资料主页" : profile.summary
    }

    private func makeSectionCard(rows: [(String, String)]) -> UIView {
        let card = UIView()
        card.backgroundColor = UIColor.secondarySystemGroupedBackground
        card.layer.cornerRadius = 12
        card.layer.cornerCurve = .continuous
        card.translatesAutoresizingMaskIntoConstraints = false

        let stack = UIStackView()
        stack.axis = .vertical
        stack.spacing = 0
        stack.translatesAutoresizingMaskIntoConstraints = false
        card.addSubview(stack)

        rows.enumerated().forEach { index, row in
            stack.addArrangedSubview(makeInfoRow(title: row.0, value: row.1, showsDivider: index < rows.count - 1))
        }

        NSLayoutConstraint.activate([
            stack.topAnchor.constraint(equalTo: card.topAnchor),
            stack.leadingAnchor.constraint(equalTo: card.leadingAnchor),
            stack.trailingAnchor.constraint(equalTo: card.trailingAnchor),
            stack.bottomAnchor.constraint(equalTo: card.bottomAnchor)
        ])
        return card
    }

    private func makeInfoRow(title: String, value: String, showsDivider: Bool) -> UIView {
        let container = UIView()
        container.translatesAutoresizingMaskIntoConstraints = false

        let titleLabel = UILabel()
        titleLabel.text = title
        titleLabel.font = .systemFont(ofSize: 14, weight: .regular)
        titleLabel.textColor = .secondaryLabel
        titleLabel.translatesAutoresizingMaskIntoConstraints = false

        let valueLabel = UILabel()
        valueLabel.text = value
        valueLabel.font = .systemFont(ofSize: 15, weight: .medium)
        valueLabel.textColor = value == "未填写" || value == "未设置" ? .tertiaryLabel : .label
        valueLabel.textAlignment = .right
        valueLabel.numberOfLines = 1
        valueLabel.adjustsFontSizeToFitWidth = true
        valueLabel.minimumScaleFactor = 0.78
        valueLabel.lineBreakMode = .byTruncatingMiddle
        valueLabel.setContentCompressionResistancePriority(.defaultLow, for: .horizontal)
        valueLabel.translatesAutoresizingMaskIntoConstraints = false

        let divider = UIView()
        divider.backgroundColor = UIColor.separator.withAlphaComponent(showsDivider ? 0.55 : 0)
        divider.translatesAutoresizingMaskIntoConstraints = false

        [titleLabel, valueLabel, divider].forEach(container.addSubview)

        NSLayoutConstraint.activate([
            container.heightAnchor.constraint(greaterThanOrEqualToConstant: 50),
            titleLabel.leadingAnchor.constraint(equalTo: container.leadingAnchor, constant: 16),
            titleLabel.centerYAnchor.constraint(equalTo: container.centerYAnchor),
            titleLabel.widthAnchor.constraint(equalToConstant: 72),
            valueLabel.leadingAnchor.constraint(equalTo: titleLabel.trailingAnchor, constant: 12),
            valueLabel.trailingAnchor.constraint(equalTo: container.trailingAnchor, constant: -16),
            valueLabel.topAnchor.constraint(equalTo: container.topAnchor, constant: 10),
            valueLabel.bottomAnchor.constraint(equalTo: container.bottomAnchor, constant: -10),
            divider.leadingAnchor.constraint(equalTo: container.leadingAnchor, constant: 16),
            divider.trailingAnchor.constraint(equalTo: container.trailingAnchor, constant: -16),
            divider.bottomAnchor.constraint(equalTo: container.bottomAnchor),
            divider.heightAnchor.constraint(equalToConstant: 1 / UIScreen.main.scale)
        ])
        return container
    }

    private func makeLatestMessageCard() -> UIView {
        let senderName: String
        let messageText: String
        let timeText: String
        if let latestMessage {
            senderName = latestMessage.sender.isCurrentUser ? "我" : latestMessage.sender.displayName
            messageText = latestMessage.body.isEmpty ? latestMessage.type.title : latestMessage.body
            timeText = latestMessage.displayTimestamp
        } else {
            senderName = "暂无"
            messageText = "还没有聊天记录"
            timeText = ""
        }

        return makeSectionCard(rows: [
            ("最近消息", "\(senderName)：\(messageText)"),
            ("消息时间", timeText.isEmpty ? "暂无" : timeText)
        ])
    }

    private func makeActionCard() -> UIView {
        let card = UIView()
        card.backgroundColor = UIColor.secondarySystemGroupedBackground
        card.layer.cornerRadius = 12
        card.layer.cornerCurve = .continuous
        card.translatesAutoresizingMaskIntoConstraints = false

        let stack = UIStackView()
        stack.axis = .vertical
        stack.spacing = 0
        stack.translatesAutoresizingMaskIntoConstraints = false
        card.addSubview(stack)

        stack.addArrangedSubview(makeActionRow(
            title: "客户档案",
            subtitle: "查看客户等级、来源和画像资料",
            symbol: "person.crop.rectangle.badge.checkmark",
            action: #selector(openCustomerProfile),
            showsDivider: true
        ))
        stack.addArrangedSubview(makeActionRow(
            title: "标签与朋友圈权限",
            subtitle: "管理联系人标签和朋友圈可见范围",
            symbol: "tag.fill",
            action: #selector(openLabelsAndPermissions),
            showsDivider: true
        ))
        stack.addArrangedSubview(makeActionRow(
            title: "个人名片详情",
            subtitle: "查看身份、组织和联系方式",
            symbol: "person.text.rectangle",
            action: #selector(openContactCard),
            showsDivider: false
        ))

        NSLayoutConstraint.activate([
            stack.leadingAnchor.constraint(equalTo: card.leadingAnchor),
            stack.trailingAnchor.constraint(equalTo: card.trailingAnchor),
            stack.topAnchor.constraint(equalTo: card.topAnchor),
            stack.bottomAnchor.constraint(equalTo: card.bottomAnchor)
        ])
        return card
    }

    private func makeActionRow(
        title: String,
        subtitle: String,
        symbol: String,
        action: Selector,
        showsDivider: Bool
    ) -> UIView {
        let container = UIView()
        container.translatesAutoresizingMaskIntoConstraints = false

        var configuration = UIButton.Configuration.plain()
        configuration.title = title
        configuration.subtitle = subtitle
        configuration.image = UIImage(systemName: symbol)
        configuration.imagePadding = 12
        configuration.baseForegroundColor = .label
        configuration.imageColorTransformer = UIConfigurationColorTransformer { _ in .systemBlue }
        configuration.contentInsets = NSDirectionalEdgeInsets(top: 10, leading: 16, bottom: 10, trailing: 42)
        configuration.titleTextAttributesTransformer = UIConfigurationTextAttributesTransformer { attributes in
            var updated = attributes
            updated.font = .systemFont(ofSize: 16, weight: .semibold)
            return updated
        }
        configuration.subtitleTextAttributesTransformer = UIConfigurationTextAttributesTransformer { attributes in
            var updated = attributes
            updated.font = .systemFont(ofSize: 12, weight: .regular)
            updated.foregroundColor = .secondaryLabel
            return updated
        }
        let button = UIButton(configuration: configuration)
        button.contentHorizontalAlignment = .leading
        button.tintColor = .systemBlue
        button.addTarget(self, action: action, for: .touchUpInside)
        button.translatesAutoresizingMaskIntoConstraints = false

        let chevron = UIImageView(image: UIImage(systemName: "chevron.right"))
        chevron.tintColor = .tertiaryLabel
        chevron.contentMode = .scaleAspectFit
        chevron.translatesAutoresizingMaskIntoConstraints = false

        let divider = UIView()
        divider.backgroundColor = showsDivider ? UIColor.separator.withAlphaComponent(0.45) : .clear
        divider.translatesAutoresizingMaskIntoConstraints = false

        container.addSubview(button)
        container.addSubview(chevron)
        container.addSubview(divider)
        NSLayoutConstraint.activate([
            container.heightAnchor.constraint(greaterThanOrEqualToConstant: 64),
            button.topAnchor.constraint(equalTo: container.topAnchor),
            button.leadingAnchor.constraint(equalTo: container.leadingAnchor),
            button.trailingAnchor.constraint(equalTo: container.trailingAnchor),
            button.bottomAnchor.constraint(equalTo: container.bottomAnchor),
            chevron.trailingAnchor.constraint(equalTo: container.trailingAnchor, constant: -16),
            chevron.centerYAnchor.constraint(equalTo: container.centerYAnchor),
            chevron.widthAnchor.constraint(equalToConstant: 8),
            chevron.heightAnchor.constraint(equalToConstant: 14),
            divider.leadingAnchor.constraint(equalTo: container.leadingAnchor, constant: 52),
            divider.trailingAnchor.constraint(equalTo: container.trailingAnchor),
            divider.bottomAnchor.constraint(equalTo: container.bottomAnchor),
            divider.heightAnchor.constraint(equalToConstant: 1 / UIScreen.main.scale)
        ])
        return container
    }

    @objc private func openContactCard() {
        onOpenContactCard?(profile)
    }

    @objc private func openCustomerProfile() {
        onOpenCustomerProfile?()
    }

    @objc private func openLabelsAndPermissions() {
        onOpenLabelsAndPermissions?()
    }

}
