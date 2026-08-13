import UIKit

extension String {
    func maskedSecret() -> String {
        guard count > 12 else { return isEmpty ? "未配置" : "****" }
        return "\(prefix(8))****\(suffix(6))"
    }
}

final class OpenApiFriendManagementViewController: UIViewController, UITableViewDataSource, UITableViewDelegate, UIGestureRecognizerDelegate, UITextFieldDelegate {
    private enum FriendAddTargetKind {
        case phone
        case wxid
        case wechatID
    }

    var onRefreshRequested: (() -> Void)?

    private var accounts: [OpenApiWeChatAccountContext]
    private var conversations: [OpenApiConversationContext]
    private var friendRequests: [OpenApiFriendRequestContext]
    private var selectedAccount: OpenApiWeChatAccountContext
    private let headerContainer = UIView()
    private let headerCard = UIView()
    private let accountButton = UIButton(type: .system)
    private let targetField = UITextField()
    private let messageField = UITextField()
    private let remarkField = UITextField()
    private let labelField = UITextField()
    private let sourceField = UITextField()
    private let customerLevelField = UITextField()
    private let profileKeyField = UITextField()
    private let verifyImageField = UITextField()
    private let findButton = UIButton(type: .system)
    private let addButton = UIButton(type: .system)
    private let refreshButton = UIButton(type: .system)
    private let formToggleButton = UIButton(type: .system)
    private let formFieldsStack = UIStackView()
    private let formActionRow = UIStackView()
    private let tableView = UITableView(frame: .zero, style: .insetGrouped)
    private let statusLabel = UILabel()
    private var isFriendFormExpanded = false

    init(
        accounts: [OpenApiWeChatAccountContext],
        conversations: [OpenApiConversationContext],
        friendRequests: [OpenApiFriendRequestContext],
        selectedAccountID: UUID
    ) {
        self.accounts = accounts
        self.conversations = conversations
        self.friendRequests = friendRequests
        self.selectedAccount = accounts.first { $0.participantID == selectedAccountID }
            ?? accounts.first
            ?? OpenApiWeChatAccountContext(
                participantID: UUID(),
                wxid: "",
                nickname: "未同步帐号",
                clientUuid: "",
                accountStatus: 0,
                avatarURL: ""
            )
        super.init(nibName: nil, bundle: nil)
        title = "好友管理"
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = UIColor.systemGroupedBackground
        navigationItem.rightBarButtonItems = [
            UIBarButtonItem(title: "环境", style: .plain, target: self, action: #selector(openEnvironment)),
            UIBarButtonItem(title: "拉取申请", style: .done, target: self, action: #selector(pullFriendRequests))
        ]
        configureKeyboardDismissal()
        configureHeader()
        configureTable()
        updateAccountButton()
        updateStatus()
        showCircularPageLoading(title: "好友接口")
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        updateTableHeaderSizeIfNeeded()
    }

    func updateData(
        accounts: [OpenApiWeChatAccountContext],
        conversations: [OpenApiConversationContext],
        friendRequests: [OpenApiFriendRequestContext],
        selectedAccountID: UUID
    ) {
        self.accounts = accounts
        self.conversations = conversations
        self.friendRequests = friendRequests
        selectedAccount = accounts.first { $0.participantID == selectedAccountID }
            ?? accounts.first { $0.wxid == selectedAccount.wxid }
            ?? accounts.first
            ?? selectedAccount
        updateAccountButton()
        updateStatus()
        tableView.reloadData()
    }

    private var filteredContacts: [OpenApiConversationContext] {
        conversations.filter { $0.ownerWxid == selectedAccount.wxid && $0.kind == .contact }
    }

    private var filteredFriendRequests: [OpenApiFriendRequestContext] {
        friendRequests
            .filter { selectedAccount.wxid.isEmpty || $0.ownerWxid == selectedAccount.wxid }
            .sorted { lhs, rhs in
                if lhs.status != rhs.status { return lhs.status < rhs.status }
                return (lhs.requestTime ?? .distantPast) > (rhs.requestTime ?? .distantPast)
            }
    }

    private var pendingFriendRequestCount: Int {
        filteredFriendRequests.filter { $0.status == 0 }.count
    }

    private var filteredGroups: [OpenApiConversationContext] {
        conversations.filter { $0.ownerWxid == selectedAccount.wxid && $0.kind == .chatroom }
    }

    private func configureKeyboardDismissal() {
        let tapGesture = UITapGestureRecognizer(target: self, action: #selector(dismissKeyboard))
        tapGesture.cancelsTouchesInView = false
        tapGesture.delegate = self
        view.addGestureRecognizer(tapGesture)
    }

    @objc private func dismissKeyboard() {
        view.endEditing(true)
    }

    private func configureHeader() {
        headerContainer.backgroundColor = .clear
        headerCard.backgroundColor = UIColor.secondarySystemGroupedBackground
        headerCard.layer.cornerRadius = 18
        headerCard.layer.cornerCurve = .continuous
        headerCard.translatesAutoresizingMaskIntoConstraints = false
        headerContainer.addSubview(headerCard)

        let titleLabel = UILabel()
        titleLabel.text = "真实好友操作"
        titleLabel.font = .systemFont(ofSize: 20, weight: .bold)
        titleLabel.textColor = .label

        statusLabel.font = .systemFont(ofSize: 12.5, weight: .medium)
        statusLabel.textColor = .secondaryLabel
        statusLabel.numberOfLines = 0

        accountButton.titleLabel?.font = .systemFont(ofSize: 14, weight: .semibold)
        accountButton.tintColor = .systemGreen
        accountButton.backgroundColor = UIColor.systemGreen.withAlphaComponent(0.10)
        accountButton.layer.cornerRadius = 13
        accountButton.layer.cornerCurve = .continuous
        accountButton.applyContentInsets(top: 7, leading: 12, bottom: 7, trailing: 12)
        accountButton.addTarget(self, action: #selector(selectAccount), for: .touchUpInside)

        [targetField, messageField, remarkField, labelField, sourceField, customerLevelField, profileKeyField, verifyImageField].forEach(configureField)
        targetField.placeholder = "微信号 / wxid / 手机号"
        messageField.placeholder = "验证语，例如：你好，我是..."
        remarkField.placeholder = "备注，可为空"
        labelField.placeholder = "标签，可为空"
        sourceField.placeholder = "来源，例如 ios-app"
        customerLevelField.placeholder = "客户等级，例如 A / B / C"
        profileKeyField.placeholder = "画像 key，例如 ios_openapi_demo"
        verifyImageField.placeholder = "验证图片 URL，可为空"
        messageField.text = "你好，我是只发 iOS 联调号。"
        sourceField.text = "ios-app"
        customerLevelField.text = "A"
        profileKeyField.text = "ios_openapi_demo"

        configureActionButton(findButton, title: "查找", symbol: "magnifyingglass", color: .systemBlue)
        configureActionButton(addButton, title: "添加", symbol: "person.badge.plus", color: .systemGreen)
        configureActionButton(refreshButton, title: "刷新", symbol: "arrow.clockwise", color: .systemOrange)
        updateUnifiedLookupUI()
        findButton.addTarget(self, action: #selector(findContact), for: .touchUpInside)
        addButton.addTarget(self, action: #selector(confirmAddFriend), for: .touchUpInside)
        refreshButton.addTarget(self, action: #selector(refreshData), for: .touchUpInside)

        let topRow = UIStackView(arrangedSubviews: [titleLabel, accountButton])
        topRow.axis = .horizontal
        topRow.alignment = .center
        topRow.spacing = 10
        topRow.distribution = .fill

        [targetField, messageField, remarkField, labelField, sourceField, customerLevelField, profileKeyField, verifyImageField].forEach {
            formFieldsStack.addArrangedSubview($0)
        }
        formFieldsStack.axis = .vertical
        formFieldsStack.spacing = 9

        [findButton, addButton, refreshButton].forEach {
            formActionRow.addArrangedSubview($0)
        }
        formActionRow.axis = .horizontal
        formActionRow.alignment = .fill
        formActionRow.distribution = .fillEqually
        formActionRow.spacing = 9

        var toggleConfiguration = UIButton.Configuration.gray()
        toggleConfiguration.title = "添加或查询好友"
        toggleConfiguration.image = UIImage(systemName: "chevron.down")
        toggleConfiguration.imagePlacement = .trailing
        toggleConfiguration.imagePadding = 8
        toggleConfiguration.cornerStyle = .medium
        formToggleButton.configuration = toggleConfiguration
        formToggleButton.contentHorizontalAlignment = .leading
        formToggleButton.heightAnchor.constraint(equalToConstant: 40).isActive = true
        formToggleButton.addTarget(self, action: #selector(toggleFriendForm), for: .touchUpInside)

        formFieldsStack.isHidden = true
        formActionRow.isHidden = true

        let stack = UIStackView(arrangedSubviews: [topRow, statusLabel, formToggleButton, formFieldsStack, formActionRow])
        stack.axis = .vertical
        stack.spacing = 12
        stack.translatesAutoresizingMaskIntoConstraints = false
        headerCard.addSubview(stack)

        NSLayoutConstraint.activate([
            headerCard.topAnchor.constraint(equalTo: headerContainer.topAnchor, constant: 12),
            headerCard.leadingAnchor.constraint(equalTo: headerContainer.leadingAnchor, constant: 14),
            headerCard.trailingAnchor.constraint(equalTo: headerContainer.trailingAnchor, constant: -14),
            headerCard.bottomAnchor.constraint(equalTo: headerContainer.bottomAnchor, constant: -8),

            stack.topAnchor.constraint(equalTo: headerCard.topAnchor, constant: 14),
            stack.leadingAnchor.constraint(equalTo: headerCard.leadingAnchor, constant: 14),
            stack.trailingAnchor.constraint(equalTo: headerCard.trailingAnchor, constant: -14),
            stack.bottomAnchor.constraint(equalTo: headerCard.bottomAnchor, constant: -14)
        ])
    }

    private func configureTable() {
        tableView.backgroundColor = .clear
        tableView.keyboardDismissMode = .interactive
        tableView.dataSource = self
        tableView.delegate = self
        tableView.rowHeight = UITableView.automaticDimension
        tableView.estimatedRowHeight = 76
        let refreshControl = UIRefreshControl()
        refreshControl.addTarget(self, action: #selector(refreshList), for: .valueChanged)
        tableView.refreshControl = refreshControl
        tableView.register(UITableViewCell.self, forCellReuseIdentifier: "OpenApiFriendCell")
        tableView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(tableView)
        tableView.tableHeaderView = headerContainer
        NSLayoutConstraint.activate([
            tableView.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
            tableView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            tableView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            tableView.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor)
        ])
    }

    private func updateTableHeaderSizeIfNeeded() {
        let width = tableView.bounds.width
        guard width > 0 else { return }

        headerContainer.frame.size.width = width
        let fittingSize = headerContainer.systemLayoutSizeFitting(
            CGSize(width: width, height: UIView.layoutFittingCompressedSize.height),
            withHorizontalFittingPriority: .required,
            verticalFittingPriority: .fittingSizeLevel
        )
        guard abs(headerContainer.frame.height - fittingSize.height) > 0.5 else { return }

        headerContainer.frame.size.height = fittingSize.height
        tableView.tableHeaderView = headerContainer
    }

    @objc private func toggleFriendForm() {
        isFriendFormExpanded.toggle()
        formFieldsStack.isHidden = !isFriendFormExpanded
        formActionRow.isHidden = !isFriendFormExpanded

        var configuration = formToggleButton.configuration ?? .gray()
        configuration.title = isFriendFormExpanded ? "收起添加好友" : "添加或查询好友"
        configuration.image = UIImage(systemName: isFriendFormExpanded ? "chevron.up" : "chevron.down")
        formToggleButton.configuration = configuration

        updateTableHeaderSizeIfNeeded()
        if isFriendFormExpanded {
            tableView.setContentOffset(.zero, animated: true)
        } else {
            view.endEditing(true)
        }
    }

    @objc private func refreshList() {
        tableView.refreshControl?.endRefreshing()
        refreshData()
    }

    private func configureField(_ field: UITextField) {
        field.borderStyle = .none
        field.backgroundColor = UIColor.systemBackground
        field.layer.cornerRadius = 12
        field.layer.cornerCurve = .continuous
        field.clearButtonMode = .whileEditing
        field.font = .systemFont(ofSize: 14.5, weight: .medium)
        field.delegate = self
        field.returnKeyType = .done
        field.leftView = UIView(frame: CGRect(x: 0, y: 0, width: 12, height: 1))
        field.leftViewMode = .always
        field.heightAnchor.constraint(equalToConstant: 40).isActive = true
    }

    private func configureActionButton(_ button: UIButton, title: String, symbol: String, color: UIColor) {
        var config = UIButton.Configuration.filled()
        config.title = title
        config.image = UIImage(systemName: symbol)
        config.imagePadding = 4
        config.baseBackgroundColor = color
        config.baseForegroundColor = .white
        config.cornerStyle = .medium
        button.configuration = config
        button.heightAnchor.constraint(equalToConstant: 40).isActive = true
    }

    private func updateActionButton(_ button: UIButton, title: String, symbol: String, color: UIColor) {
        var config = button.configuration ?? .filled()
        config.title = title
        config.image = UIImage(systemName: symbol)
        config.imagePadding = 4
        config.baseBackgroundColor = color
        config.baseForegroundColor = .white
        config.cornerStyle = .medium
        button.configuration = config
    }

    private func updateUnifiedLookupUI() {
        targetField.placeholder = "微信号 / wxid / 手机号"
        targetField.keyboardType = .default
        targetField.textContentType = .username
        targetField.autocapitalizationType = .none
        targetField.autocorrectionType = .no
        updateActionButton(
            findButton,
            title: "查询联系人",
            symbol: "magnifyingglass",
            color: .systemBlue
        )
        updateActionButton(
            addButton,
            title: "添加好友",
            symbol: "person.badge.plus",
            color: .systemGreen
        )
    }

    private func updateAccountButton() {
        accountButton.setTitle(" \(selectedAccount.nickname)", for: .normal)
        accountButton.setImage(UIImage(systemName: selectedAccount.isOnline ? "checkmark.circle.fill" : "person.crop.circle"), for: .normal)
    }

    private func updateStatus() {
        statusLabel.text = "当前帐号：\(selectedAccount.wxid.isEmpty ? "未同步" : selectedAccount.wxid)\n好友 \(filteredContacts.count) 个 · 群聊 \(filteredGroups.count) 个 · 待处理申请 \(pendingFriendRequestCount) 条"
    }

    @objc private func openEnvironment() {
        view.endEditing(true)
        navigationController?.pushViewController(OpenApiEnvironmentViewController(), animated: true)
    }

    @objc private func selectAccount() {
        view.endEditing(true)
        let alert = UIAlertController(title: "选择微信帐号", message: nil, preferredStyle: .actionSheet)
        accounts.forEach { account in
            let status = account.isOnline ? "在线" : "离线"
            alert.addAction(UIAlertAction(title: "\(account.nickname) · \(status)", style: .default) { [weak self] _ in
                self?.selectedAccount = account
                self?.updateAccountButton()
                self?.updateStatus()
                self?.tableView.reloadData()
                self?.refreshFriendRequestsFromServer(showsSuccessAlert: false)
            })
        }
        alert.addAction(UIAlertAction(title: "取消", style: .cancel))
        present(alert, animated: true)
    }

    @objc private func pullFriendRequests() {
        view.endEditing(true)
        runSocialTask(title: "拉取好友申请") {
            try await OpenApiSocialManagementService().pullFriendRequests(
                account: OpenApiSocialAccount(
                    deviceUUID: self.selectedAccount.clientUuid,
                    weChatID: self.selectedAccount.wxid
                ),
                onlyNew: false
            )
        } completion: { [weak self] in
            self?.refreshFriendRequestsFromServer(showsSuccessAlert: false)
            self?.onRefreshRequested?()
        }
    }

    private func refreshFriendRequestsFromServer(showsSuccessAlert: Bool) {
        guard !selectedAccount.wxid.isEmpty else {
            if showsSuccessAlert {
                showSimpleAlert(title: "无法刷新申请", message: "当前没有可用微信帐号。")
            }
            return
        }
        setActionsEnabled(false)
        Task { @MainActor in
            do {
                let result = try await OpenApiSocialManagementService().listFriendRequests(
                    account: OpenApiSocialAccount(
                        deviceUUID: self.selectedAccount.clientUuid,
                        weChatID: self.selectedAccount.wxid
                    ),
                    count: 100,
                    pendingOnly: false
                )
                let parsed = self.parseFriendRequests(from: result.jsonObject, fallbackAccount: self.selectedAccount)
                self.friendRequests.removeAll { $0.ownerWxid == self.selectedAccount.wxid }
                self.friendRequests.append(contentsOf: parsed)
                self.setActionsEnabled(true)
                self.updateStatus()
                self.tableView.reloadSections(IndexSet(integer: 0), with: .automatic)
                if showsSuccessAlert {
                    self.showSimpleAlert(title: "好友申请已刷新", message: "当前帐号共 \(self.filteredFriendRequests.count) 条申请，待处理 \(self.pendingFriendRequestCount) 条。")
                }
            } catch {
                self.setActionsEnabled(true)
                if showsSuccessAlert {
                    self.showSimpleAlert(title: "刷新申请失败", message: error.localizedDescription)
                }
            }
        }
    }

    @objc private func findContact() {
        view.endEditing(true)
        let target = targetField.text?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        guard !target.isEmpty else {
            showSimpleAlert(title: "请输入联系人", message: "可填写微信号、wxid 或手机号。")
            return
        }
        submitFindContact(target: target, title: "联系人查询")
    }

    @objc private func confirmAddFriend() {
        view.endEditing(true)
        let target = targetField.text?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        guard !target.isEmpty else {
            showSimpleAlert(title: "请输入联系人", message: "可填写要添加的微信号、wxid 或手机号。")
            return
        }
        let targetKind = friendAddTargetKind(for: target)
        let alert = UIAlertController(
            title: "确认添加好友",
            message: "将使用「\(selectedAccount.nickname)」\(friendAddDescription(for: targetKind))：\(target)",
            preferredStyle: .alert
        )
        alert.addAction(UIAlertAction(title: "取消", style: .cancel))
        alert.addAction(UIAlertAction(title: "添加", style: .default) { [weak self] _ in
            self?.addFriend(target)
        })
        present(alert, animated: true)
    }

    private func addFriend(_ target: String) {
        guard selectedAccountReadyForFriendAction() else { return }
        let verify = messageField.text?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        let remark = remarkField.text?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        let label = labelField.text?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        let source = sourceField.text?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        let customerLevel = customerLevelField.text?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        let profileKey = profileKeyField.text?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        let verifyImageURL = verifyImageField.text?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        let mappedLabels = label
            .split(separator: ",")
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
        let targetKind = friendAddTargetKind(for: target)
        let isPhoneTarget = targetKind == .phone
        runSocialTask(title: "添加好友") {
            let account = OpenApiSocialAccount(
                deviceUUID: self.selectedAccount.clientUuid,
                weChatID: self.selectedAccount.wxid
            )
            var options = OpenApiFriendAddOptions()
            options.verificationMessage = verify
            options.remark = remark
            options.labelNames = mappedLabels
            options.sourceChannel = source.isEmpty ? "ios-app" : source
            options.customerLevel = customerLevel
            options.profileKey = profileKey
            options.verificationImageURL = verifyImageURL
            if isPhoneTarget {
                return try await OpenApiSocialManagementService().addFriendByPhone(
                    account: account,
                    phone: target,
                    options: options
                )
            }
            let resolvedTarget = targetKind == .wxid
                ? target
                : try await self.resolveFriendWxidForAdding(target)
            return try await OpenApiSocialManagementService().addFriend(
                account: account,
                friendWxid: resolvedTarget,
                options: options
            )
        } completion: { [weak self] in
            self?.onRefreshRequested?()
        }
    }

    private func resolveFriendWxidForAdding(_ target: String) async throws -> String {
        try await OpenApiSocialManagementService().findFriendWxid(
            account: OpenApiSocialAccount(
                deviceUUID: selectedAccount.clientUuid,
                weChatID: selectedAccount.wxid
            ),
            target: target
        )
    }

    private func openApiTaskID(from object: Any?) -> String? {
        if let dictionary = object as? [String: Any] {
            for key in ["taskId", "taskID", "task_id"] {
                if let value = cleanedString(dictionary[key]), !value.isEmpty {
                    if isValidOpenApiTaskID(value) {
                        return value
                    }
                }
            }
            for key in ["taskResultUrl", "taskResultURL", "taskUrl", "resultUrl"] {
                guard let value = cleanedString(dictionary[key]),
                      let url = OpenApiDisplay.url(from: value)
                else { continue }
                let last = url.pathComponents.last?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
                if isValidOpenApiTaskID(last), last != "tasks" {
                    return last
                }
            }
            for key in ["data", "result", "payload", "task", "taskResult"] {
                if let nested = openApiTaskID(from: dictionary[key]) {
                    return nested
                }
            }
        }
        if let array = object as? [Any] {
            for item in array {
                if let nested = openApiTaskID(from: item) {
                    return nested
                }
            }
        }
        return nil
    }

    private func isValidOpenApiTaskID(_ value: String) -> Bool {
        let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return false }
        if let number = Int64(trimmed) {
            return number > 0
        }
        return trimmed.range(of: #"^[A-Za-z0-9][A-Za-z0-9_-]*$"#, options: .regularExpression) != nil
    }

    private func friendFindFailureMessage(from object: Any?) -> String? {
        guard let dictionary = object as? [String: Any] else { return nil }
        let message = [
            cleanedString(dictionary["message"]),
            cleanedString(dictionary["error"]),
            cleanedString(dictionary["errorMessage"]),
            cleanedString(dictionary["status"]),
            cleanedString(dictionary["state"]),
            cleanedString(dictionary["resultCode"])
        ].compactMap { $0 }.joined(separator: " ")
        let lower = message.lowercased()
        let failureTerms = ["fail", "error", "not found", "invalid", "失败", "错误", "不存在", "无效", "未找到", "风控", "限制"]
        if failureTerms.contains(where: lower.contains) {
            return message.isEmpty ? "未找到该联系人" : message
        }
        for key in ["data", "result", "payload", "task", "taskResult"] {
            if let nested = friendFindFailureMessage(from: dictionary[key]) {
                return nested
            }
        }
        return nil
    }

    private func submitFindContact(target: String, title: String) {
        guard selectedAccountReadyForFriendAction() else { return }
        setActionsEnabled(false)
        Task { @MainActor in
            do {
                let wxid = try await OpenApiSocialManagementService().findFriendWxid(
                    account: OpenApiSocialAccount(
                        deviceUUID: self.selectedAccount.clientUuid,
                        weChatID: self.selectedAccount.wxid
                    ),
                    target: target
                )
                self.setActionsEnabled(true)
                self.targetField.text = wxid
                self.updateUnifiedLookupUI()
                self.showSimpleAlert(title: "已找到 wxid", message: wxid)
            } catch {
                self.setActionsEnabled(true)
                self.showSimpleAlert(title: "\(title)失败", message: error.localizedDescription)
            }
        }
    }

    private func addFriendRequestBody(
        target: String,
        isPhoneTarget: Bool,
        verify: String,
        remark: String,
        label: String,
        source: String,
        customerLevel: String,
        profileKey: String,
        verifyImageURL: String,
        mappedLabels: [String]
    ) -> [String: Any] {
        var body: [String: Any] = [
            "weChatId": selectedAccount.wxid,
            "message": verify.isEmpty ? "你好" : verify,
            "permission": 0
        ]
        let deviceUuid = selectedAccount.clientUuid.trimmingCharacters(in: .whitespacesAndNewlines)
        if !deviceUuid.isEmpty { body["deviceUuid"] = deviceUuid }
        if !remark.isEmpty { body["remark"] = remark }
        if !label.isEmpty { body["label"] = label }
        if isPhoneTarget {
            body["phones"] = [target]
            return body
        }
        body["friendWxid"] = target
        body["scene"] = 1
        if !customerLevel.isEmpty { body["customerLevel"] = customerLevel }
        body["sourceChannel"] = source.isEmpty ? "ios-app" : source
        body["sourceDetail"] = "只发 iOS 好友管理页"
        if !profileKey.isEmpty { body["profileKey"] = profileKey }
        body["notes"] = "只发 iOS 好友管理页发起添加"
        if !verifyImageURL.isEmpty {
            body["faceImageUrl"] = verifyImageURL
            body["verifyImageUrl"] = verifyImageURL
        }
        if !mappedLabels.isEmpty { body["mappedLabelNames"] = mappedLabels }
        return body
    }

    private func findContactRequestBody(content: String) -> [String: Any] {
        var body: [String: Any] = [
            "weChatId": selectedAccount.wxid,
            "content": content
        ]
        let deviceUuid = selectedAccount.clientUuid.trimmingCharacters(in: .whitespacesAndNewlines)
        if !deviceUuid.isEmpty {
            body["deviceUuid"] = deviceUuid
        }
        return body
    }

    private func selectedAccountReadyForFriendAction() -> Bool {
        let wxid = selectedAccount.wxid.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !wxid.isEmpty else {
            showSimpleAlert(title: "当前帐号不可用", message: "当前微信帐号没有 weChatId，请先刷新帐号列表后再添加好友。")
            return false
        }
        return true
    }

    private func extractFriendWxid(from object: Any?) -> String? {
        if let dictionary = object as? [String: Any] {
            let preferredKeys = [
                "friendWxid", "friend_wxid", "contactWxid", "contact_wxid",
                "wxid", "userName", "username", "wechatId", "weChatId"
            ]
            for key in preferredKeys {
                if let value = cleanedString(dictionary[key]),
                   isLikelyFriendWxid(value) {
                    return value
                }
            }
            for key in ["data", "result", "payload", "contact", "friend", "user", "profile"] {
                if let nested = extractFriendWxid(from: dictionary[key]) {
                    return nested
                }
            }
            for value in dictionary.values {
                if let nested = extractFriendWxid(from: value) {
                    return nested
                }
            }
        }
        if let array = object as? [Any] {
            for item in array {
                if let nested = extractFriendWxid(from: item) {
                    return nested
                }
            }
        }
        return nil
    }

    private func cleanedString(_ value: Any?) -> String? {
        if let text = value as? String {
            let cleaned = text.trimmingCharacters(in: .whitespacesAndNewlines)
            return cleaned.isEmpty ? nil : cleaned
        }
        if let number = value as? NSNumber {
            return number.stringValue
        }
        return nil
    }

    private func isLikelyPhoneNumber(_ text: String) -> Bool {
        let normalized = text
            .replacingOccurrences(of: "+", with: "")
            .replacingOccurrences(of: " ", with: "")
            .replacingOccurrences(of: "-", with: "")
        let digits = normalized.filter(\.isNumber)
        return digits.count >= 7 && digits.count <= 15 && normalized.allSatisfy { $0.isNumber }
    }

    private func isLikelyFriendWxid(_ text: String) -> Bool {
        let normalized = text.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        return normalized.hasPrefix("wxid_")
            || normalized.hasPrefix("gh_")
            || normalized.hasSuffix("@chatroom")
            || normalized.contains("@stranger")
    }

    private func friendAddTargetKind(for text: String) -> FriendAddTargetKind {
        if isLikelyPhoneNumber(text) {
            return .phone
        }
        if isLikelyFriendWxid(text) {
            return .wxid
        }
        return .wechatID
    }

    private func friendAddDescription(for kind: FriendAddTargetKind) -> String {
        switch kind {
        case .phone:
            return "通过手机号添加"
        case .wxid:
            return "通过 wxid 添加"
        case .wechatID:
            return "先查询微信号并添加"
        }
    }

    @objc private func refreshData() {
        view.endEditing(true)
        onRefreshRequested?()
        showSimpleAlert(title: "已开始刷新", message: "正在重新拉取真实帐号、好友和群聊，稍后回到主页面可看到更新。")
    }

    private func deleteFriend(_ contact: OpenApiConversationContext) {
        view.endEditing(true)
        let deleteID = contact.backendID.isEmpty ? contact.wxid : contact.backendID
        let alert = UIAlertController(
            title: "删除好友",
            message: "确认从「\(selectedAccount.nickname)」删除「\(contact.displayName)」？",
            preferredStyle: .alert
        )
        alert.addAction(UIAlertAction(title: "取消", style: .cancel))
        alert.addAction(UIAlertAction(title: "删除", style: .destructive) { [weak self] _ in
            guard let self else { return }
            self.runSocialTask(title: "删除好友") {
                try await OpenApiSocialManagementService().deleteFriend(
                    account: OpenApiSocialAccount(
                        deviceUUID: self.selectedAccount.clientUuid,
                        weChatID: self.selectedAccount.wxid
                    ),
                    friendID: deleteID
                )
            } completion: { [weak self] in
                self?.onRefreshRequested?()
            }
        })
        present(alert, animated: true)
    }

    private func presentFriendRequestActions(_ request: OpenApiFriendRequestContext) {
        view.endEditing(true)
        let detail = [
            request.requestWxid,
            request.source.isEmpty ? nil : "来源：\(request.source)",
            request.requestMessage.isEmpty ? nil : "验证语：\(request.requestMessage)",
            "状态：\(request.statusText)"
        ].compactMap { $0 }.joined(separator: "\n")
        let alert = UIAlertController(title: request.displayName, message: detail, preferredStyle: .actionSheet)
        if request.status == 0 {
            alert.addAction(UIAlertAction(title: "通过申请", style: .default) { [weak self] _ in
                self?.handleFriendRequest(request, operation: 1)
            })
            alert.addAction(UIAlertAction(title: "拒绝申请", style: .destructive) { [weak self] _ in
                self?.handleFriendRequest(request, operation: 2)
            })
        }
        alert.addAction(UIAlertAction(title: "复制 wxid", style: .default) { _ in
            UIPasteboard.general.string = request.requestWxid
        })
        alert.addAction(UIAlertAction(title: "取消", style: .cancel))
        present(alert, animated: true)
    }

    private func handleFriendRequest(_ request: OpenApiFriendRequestContext, operation: Int) {
        let isAccepting = operation == 1
        let title = isAccepting ? "通过好友申请" : "拒绝好友申请"
        let alert = UIAlertController(
            title: title,
            message: "将处理「\(request.displayName)」的好友申请。",
            preferredStyle: .alert
        )
        if isAccepting {
            alert.addTextField { field in
                field.placeholder = "好友备注，可为空"
                field.text = OpenApiDisplay.cleaned(self.remarkField.text)
            }
        }
        alert.addTextField { field in
            field.placeholder = isAccepting ? "回复语：你好，已通过" : "拒绝说明，可为空"
            field.text = isAccepting ? "你好，已通过" : ""
        }
        alert.addAction(UIAlertAction(title: "取消", style: .cancel))
        alert.addAction(UIAlertAction(title: isAccepting ? "通过" : "拒绝", style: isAccepting ? .default : .destructive) { [weak self, weak alert] _ in
            guard let self else { return }
            let remark = isAccepting ? (alert?.textFields?.first?.text ?? "") : ""
            let reply = isAccepting
                ? (alert?.textFields?.dropFirst().first?.text ?? "你好，已通过")
                : (alert?.textFields?.first?.text ?? "")
            self.runSocialTask(title: title) {
                try await OpenApiSocialManagementService().handleFriendRequest(
                    account: OpenApiSocialAccount(
                        deviceUUID: self.selectedAccount.clientUuid,
                        weChatID: self.selectedAccount.wxid
                    ),
                    friendWxid: request.requestWxid,
                    friendNickname: request.displayName,
                    decision: isAccepting ? .accept : .reject,
                    remark: remark.trimmingCharacters(in: .whitespacesAndNewlines),
                    reply: reply.trimmingCharacters(in: .whitespacesAndNewlines)
                )
            } completion: { [weak self] in
                self?.refreshFriendRequestsFromServer(showsSuccessAlert: false)
                self?.onRefreshRequested?()
            }
        })
        present(alert, animated: true)
    }

    private func runAction(
        title: String,
        validatesTask: Bool = false,
        operation: @escaping () async throws -> OpenApiHTTPResult,
        completion: (() -> Void)? = nil
    ) {
        setActionsEnabled(false)
        Task { @MainActor in
            do {
                let result = try await operation()
                let taskID = validatesTask
                    ? try await OpenApiHTTPClient.validateTaskResult(result, action: title)
                    : nil
                self.setActionsEnabled(true)
                completion?()
                self.showResult(title: validatesTask ? "\(title)已完成" : "\(title)成功", result: result, taskID: taskID)
            } catch {
                self.setActionsEnabled(true)
                self.showSimpleAlert(title: "\(title)失败", message: error.localizedDescription)
            }
        }
    }

    private func runSocialTask(
        title: String,
        operation: @escaping () async throws -> String,
        completion: (() -> Void)? = nil
    ) {
        setActionsEnabled(false)
        Task { @MainActor in
            do {
                let taskID = try await operation()
                self.setActionsEnabled(true)
                completion?()
                let suffix = taskID.isEmpty ? "" : "\n任务 ID：\(taskID)"
                self.showSimpleAlert(title: "\(title)已完成", message: "接口任务已确认完成。\(suffix)")
            } catch {
                self.setActionsEnabled(true)
                self.showSimpleAlert(title: "\(title)失败", message: error.localizedDescription)
            }
        }
    }

    private func setActionsEnabled(_ enabled: Bool) {
        [findButton, addButton, refreshButton, accountButton].forEach {
            $0.isEnabled = enabled
            $0.alpha = enabled ? 1 : 0.55
        }
        navigationItem.rightBarButtonItems?.forEach { $0.isEnabled = enabled }
    }

    private func showResult(title: String, result: OpenApiHTTPResult, taskID: String? = nil) {
        var rows = OpenApiHTTPClient.rows(from: result, maxItems: 8)
        if let taskID, !taskID.isEmpty {
            rows.insert(
                OpenApiFieldRow(key: "taskId", value: taskID, subtitle: "已轮询到后台任务终态成功。"),
                at: min(3, rows.count)
            )
        }
        let message = rows.map { row in
            [row.key, row.value].filter { !$0.isEmpty }.joined(separator: "：")
        }.joined(separator: "\n")
        showSimpleAlert(title: title, message: message.isEmpty ? "接口已返回成功。" : message)
    }

    private func showSimpleAlert(title: String, message: String?) {
        let alert = UIAlertController(title: title, message: message, preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "知道了", style: .default))
        present(alert, animated: true)
    }

    func numberOfSections(in tableView: UITableView) -> Int {
        3
    }

    func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        switch section {
        case 0: return filteredFriendRequests.count
        case 1: return filteredContacts.count
        default: return filteredGroups.count
        }
    }

    func tableView(_ tableView: UITableView, titleForHeaderInSection section: Int) -> String? {
        switch section {
        case 0: return "好友申请"
        case 1: return "当前帐号好友"
        default: return "当前帐号群聊"
        }
    }

    func tableView(_ tableView: UITableView, titleForFooterInSection section: Int) -> String? {
        if section == 0, filteredFriendRequests.isEmpty {
            return "暂无好友申请，点右上角“拉取申请”从手机端同步。"
        }
        if section == 1, filteredContacts.isEmpty {
            return "当前帐号暂未同步到好友，点刷新重新拉取。"
        }
        return nil
    }

    func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = tableView.dequeueReusableCell(withIdentifier: "OpenApiFriendCell", for: indexPath)
        if indexPath.section == 0 {
            let request = filteredFriendRequests[indexPath.row]
            var content = UIListContentConfiguration.subtitleCell()
            content.text = request.displayName
            let summary = [
                request.requestWxid,
                request.statusText,
                request.source.isEmpty ? nil : request.source,
                request.requestMessage.isEmpty ? nil : request.requestMessage
            ].compactMap { $0 }.joined(separator: " · ")
            content.secondaryText = summary
            content.image = UIImage(systemName: request.status == 0 ? "person.crop.circle.badge.plus" : "person.crop.circle")
            content.imageProperties.tintColor = request.status == 0 ? .systemOrange : .secondaryLabel
            content.textProperties.font = .systemFont(ofSize: 15.5, weight: .semibold)
            content.secondaryTextProperties.font = .systemFont(ofSize: 12, weight: .regular)
            content.secondaryTextProperties.color = .secondaryLabel
            content.secondaryTextProperties.numberOfLines = 2
            cell.contentConfiguration = content
            cell.selectionStyle = .default
            if request.status == 0 {
                let handleButton = UIButton(type: .system)
                handleButton.setTitle("处理", for: .normal)
                handleButton.titleLabel?.font = .systemFont(ofSize: 13, weight: .semibold)
                handleButton.tintColor = .white
                handleButton.backgroundColor = .systemGreen
                handleButton.layer.cornerRadius = 12
                handleButton.layer.cornerCurve = .continuous
                handleButton.applyContentInsets(top: 6, leading: 10, bottom: 6, trailing: 10)
                handleButton.tag = indexPath.row
                handleButton.addTarget(self, action: #selector(friendRequestButtonTapped(_:)), for: .touchUpInside)
                handleButton.frame = CGRect(x: 0, y: 0, width: 54, height: 32)
                cell.accessoryView = handleButton
            } else {
                let statusLabel = UILabel(frame: CGRect(x: 0, y: 0, width: 58, height: 28))
                statusLabel.text = request.statusText
                statusLabel.font = .systemFont(ofSize: 12, weight: .semibold)
                statusLabel.textColor = .secondaryLabel
                statusLabel.textAlignment = .center
                statusLabel.backgroundColor = UIColor.secondarySystemFill
                statusLabel.layer.cornerRadius = 10
                statusLabel.layer.masksToBounds = true
                cell.accessoryView = statusLabel
            }
            return cell
        }

        let item = indexPath.section == 1 ? filteredContacts[indexPath.row] : filteredGroups[indexPath.row]
        var content = UIListContentConfiguration.subtitleCell()
        content.text = item.displayName
        content.secondaryText = indexPath.section == 1
            ? [item.wxid, item.friendNo].filter { !$0.isEmpty }.joined(separator: " · ")
            : "\(item.memberCount ?? 0) 人 · \(item.wxid)"
        content.image = UIImage(systemName: indexPath.section == 1 ? "person.crop.circle.fill" : "person.2.circle.fill")
        content.imageProperties.tintColor = item.tintColor
        content.textProperties.font = .systemFont(ofSize: 15.5, weight: .semibold)
        content.secondaryTextProperties.font = .systemFont(ofSize: 12, weight: .regular)
        content.secondaryTextProperties.color = .secondaryLabel
        cell.contentConfiguration = content
        cell.selectionStyle = .none

        if indexPath.section == 1 {
            let deleteButton = UIButton(type: .system)
            deleteButton.setTitle("删除", for: .normal)
            deleteButton.titleLabel?.font = .systemFont(ofSize: 13, weight: .semibold)
            deleteButton.tintColor = .systemRed
            deleteButton.backgroundColor = UIColor.systemRed.withAlphaComponent(0.10)
            deleteButton.layer.cornerRadius = 12
            deleteButton.layer.cornerCurve = .continuous
            deleteButton.applyContentInsets(top: 6, leading: 10, bottom: 6, trailing: 10)
            deleteButton.tag = indexPath.row
            deleteButton.addTarget(self, action: #selector(deleteButtonTapped(_:)), for: .touchUpInside)
            deleteButton.frame = CGRect(x: 0, y: 0, width: 54, height: 32)
            cell.accessoryView = deleteButton
        } else {
            cell.accessoryView = nil
        }
        return cell
    }

    @objc private func friendRequestButtonTapped(_ sender: UIButton) {
        view.endEditing(true)
        let requests = filteredFriendRequests
        guard requests.indices.contains(sender.tag) else { return }
        presentFriendRequestActions(requests[sender.tag])
    }

    @objc private func deleteButtonTapped(_ sender: UIButton) {
        view.endEditing(true)
        let contacts = filteredContacts
        guard contacts.indices.contains(sender.tag) else { return }
        deleteFriend(contacts[sender.tag])
    }

    func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        tableView.deselectRow(at: indexPath, animated: true)
        guard indexPath.section == 0 else { return }
        let requests = filteredFriendRequests
        guard requests.indices.contains(indexPath.row) else { return }
        presentFriendRequestActions(requests[indexPath.row])
    }

    private func parseFriendRequests(
        from object: Any?,
        fallbackAccount: OpenApiWeChatAccountContext
    ) -> [OpenApiFriendRequestContext] {
        arrayPayload(from: object).compactMap { raw -> OpenApiFriendRequestContext? in
            let requestWxid = string(raw["requestWxid"])
            guard !requestWxid.isEmpty else { return nil }
            let ownerWxid = OpenApiDisplay.cleaned(string(raw["ownerWxid"]))
            let resolvedOwnerWxid = ownerWxid.isEmpty ? fallbackAccount.wxid : ownerWxid
            let nickname = OpenApiDisplay.cleaned(string(raw["nickname"]))
            return OpenApiFriendRequestContext(
                participantID: OpenApiStableID.uuid(namespace: "openapi-contact", key: "\(resolvedOwnerWxid)-\(requestWxid)"),
                ownerWxid: resolvedOwnerWxid,
                requestWxid: requestWxid,
                backendID: string(raw["id"]),
                nickname: nickname.isEmpty ? requestWxid : nickname,
                avatarURL: OpenApiDisplay.avatarURLString(from: raw),
                source: string(raw["source"]),
                requestMessage: OpenApiDisplay.cleaned(string(raw["requestMessage"])),
                status: int(raw["status"]),
                requestTime: date(raw["requestTime"]) ?? date(raw["createdAt"])
            )
        }
    }

    private func arrayPayload(from object: Any?) -> [[String: Any]] {
        if let array = object as? [[String: Any]] {
            return array
        }
        guard let root = object as? [String: Any] else {
            return []
        }
        for key in ["items", "data", "records", "rows", "list"] {
            if let items = root[key] as? [[String: Any]] {
                return items
            }
        }
        if let data = root["data"] as? [String: Any] {
            for key in ["items", "records", "rows", "list", "data"] {
                if let items = data[key] as? [[String: Any]] {
                    return items
                }
            }
        }
        return []
    }

    private func string(_ value: Any?) -> String {
        if let string = value as? String { return string }
        if let number = value as? NSNumber { return number.stringValue }
        return ""
    }

    private func int(_ value: Any?) -> Int {
        if let int = value as? Int { return int }
        if let number = value as? NSNumber { return number.intValue }
        if let string = value as? String, let int = Int(string) { return int }
        return 0
    }

    private func date(_ value: Any?) -> Date? {
        if let date = value as? Date { return date }
        if let number = value as? NSNumber {
            let seconds = number.doubleValue > 9_999_999_999 ? number.doubleValue / 1000 : number.doubleValue
            return Date(timeIntervalSince1970: seconds)
        }
        guard let string = value as? String,
              !string.isEmpty,
              string != "0001-01-01T00:00:00"
        else { return nil }
        let isoFormatter = ISO8601DateFormatter()
        isoFormatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        if let parsed = isoFormatter.date(from: string) {
            return parsed
        }
        isoFormatter.formatOptions = [.withInternetDateTime]
        if let parsed = isoFormatter.date(from: string) {
            return parsed
        }
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "yyyy-MM-dd HH:mm:ss"
        return formatter.date(from: string)
    }

    func textFieldShouldReturn(_ textField: UITextField) -> Bool {
        textField.resignFirstResponder()
        return true
    }

    func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldReceive touch: UITouch) -> Bool {
        var touchedView: UIView? = touch.view
        while let view = touchedView {
            if view is UIControl {
                return false
            }
            touchedView = view.superview
        }
        return true
    }
}
