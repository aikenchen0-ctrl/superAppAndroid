import UIKit

struct OpenApiContactPrivacySnapshot: Equatable {
    var availableLabels: [String]
    var selectedLabels: Set<String>
    var onlyChat: Bool?
    var notSeeFriendMoments: Bool?
    var notLetFriendSeeMyMoments: Bool?
}

struct OpenApiContactLabelsPermissionsService {
    typealias Request = (String, String, [URLQueryItem], Any?) async throws -> OpenApiHTTPResult
    typealias TaskValidator = (OpenApiHTTPResult, String) async throws -> String

    private let request: Request
    private let validateTask: TaskValidator

    init(
        request: @escaping Request = { method, path, query, body in
            try await OpenApiHTTPClient.request(method, path: path, query: query, body: body)
        },
        validateTask: @escaping TaskValidator = { result, action in
            try await OpenApiHTTPClient.validateTaskResult(result, action: action)
        }
    ) {
        self.request = request
        self.validateTask = validateTask
    }

    func load(contactID: String, friendID: String, weChatID: String) async throws -> OpenApiContactPrivacySnapshot {
        do {
            return try await loadSnapshot(contactID: contactID, weChatID: weChatID)
        } catch OpenApiHTTPClient.ClientError.badStatus(let status, _) where status == 404 {
            guard let latestID = try await resolveContactID(friendID: friendID, weChatID: weChatID) else {
                throw OpenApiSocialManagementError.friendNotFound(friendID)
            }
            return try await loadSnapshot(contactID: latestID, weChatID: weChatID)
        }
    }

    private func loadSnapshot(contactID: String, weChatID: String) async throws -> OpenApiContactPrivacySnapshot {
        async let labelsResult = request(
            "GET", "/openapi/v1/contact-labels",
            [URLQueryItem(name: "weChatId", value: weChatID)], nil
        )
        async let profile = OpenApiCustomerProfileService.load(
            contactID: contactID,
            friendID: "",
            weChatID: weChatID,
            request: request
        )
        let detail: OpenApiHTTPResult?
        do {
            detail = try await request(
                "GET", "/openapi/v1/contacts/\(safePathID(contactID))/detail",
                [URLQueryItem(name: "commonChatRoomLimit", value: "0"), URLQueryItem(name: "relationLogLimit", value: "0")], nil
            )
        } catch OpenApiHTTPClient.ClientError.badStatus(let status, _) where status == 404 {
            detail = nil
        }
        let (labels, customerProfile) = try await (labelsResult, profile)
        let permissionRoot = Self.findPermissionDictionary(in: detail?.jsonObject) ?? [:]
        let selectedLabels = Set(customerProfile.mappedLabelNames)
        let accountLabels = Self.labelNames(in: labels.jsonObject)
        let visibleLabels = accountLabels + selectedLabels.sorted().filter { !accountLabels.contains($0) }
        return OpenApiContactPrivacySnapshot(
            availableLabels: visibleLabels,
            selectedLabels: selectedLabels,
            onlyChat: Self.bool(in: permissionRoot, keys: ["onlyChat", "only_chat"]),
            notSeeFriendMoments: Self.bool(in: permissionRoot, keys: ["notSeeFriendMoments", "not_see_friend_moments"]),
            notLetFriendSeeMyMoments: Self.bool(in: permissionRoot, keys: ["notLetFriendSeeMyMoments", "not_let_friend_see_my_moments"])
        )
    }

    private func resolveContactID(friendID: String, weChatID: String) async throws -> String? {
        for page in 1...30 {
            let result = try await request("GET", "/openapi/v1/contacts", [
                URLQueryItem(name: "weChatId", value: weChatID),
                URLQueryItem(name: "page", value: "\(page)"),
                URLQueryItem(name: "pageSize", value: "100"),
                URLQueryItem(name: "includeDeleted", value: "false")
            ], nil)
            let contacts = Self.dictionaryArray(in: result.jsonObject)
            if let match = contacts.first(where: { dictionary in
                Self.string(in: dictionary, keys: ["wxid", "friendWxid", "userName", "username"]) == friendID
            }) {
                let id = Self.string(in: match, keys: ["id", "contactId", "contactID"])
                if !id.isEmpty { return id }
            }
            if contacts.count < 100 { return nil }
        }
        return nil
    }

    func saveLabels(
        _ labels: Set<String>,
        account: OpenApiSocialAccount,
        friendID: String
    ) async throws {
        let result = try await request("POST", "/openapi/v1/contacts/labels", [], [
            "deviceUuid": account.deviceUUID,
            "weChatId": account.weChatID,
            "friendId": friendID,
            "labelNames": labels.sorted()
        ])
        _ = try await validateTask(result, "设置联系人标签")
    }

    func createLabel(_ labelName: String, account: OpenApiSocialAccount) async throws {
        let clean = labelName.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !clean.isEmpty else { throw OpenApiSocialManagementError.missingField("labelName") }
        let result = try await request("POST", "/openapi/v1/contact-labels", [], [
            "deviceUuid": account.deviceUUID,
            "weChatId": account.weChatID,
            "labelName": clean
        ])
        if OpenApiHTTPClient.taskID(from: result.jsonObject) != nil {
            _ = try await validateTask(result, "新建联系人标签")
        } else if Self.explicitSuccess(in: result.jsonObject) == false {
            throw NSError(
                domain: "OpenApiContactLabelsPermissionsService",
                code: 1,
                userInfo: [NSLocalizedDescriptionKey: "新建标签失败：HTTP \(result.statusCode)，\(result.rawText.isEmpty ? "后端返回 success=false" : result.rawText)"]
            )
        }
    }

    func savePermissions(
        onlyChat: Bool,
        notSeeFriendMoments: Bool,
        notLetFriendSeeMyMoments: Bool,
        account: OpenApiSocialAccount,
        friendID: String
    ) async throws {
        let result = try await request("POST", "/openapi/v1/friends/permissions", [], [
            "deviceUuid": account.deviceUUID,
            "weChatId": account.weChatID,
            "friendId": friendID,
            "permissionMask": 0,
            "onlyChat": onlyChat,
            "notSeeFriendMoments": notSeeFriendMoments,
            "notLetFriendSeeMyMoments": notLetFriendSeeMyMoments
        ])
        _ = try await validateTask(result, "设置朋友圈权限")
    }

    private func safePathID(_ value: String) -> String {
        value.trimmingCharacters(in: .whitespacesAndNewlines).replacingOccurrences(of: "/", with: "")
    }

    static func labelNames(in object: Any?) -> [String] {
        var names: [String] = []
        func visit(_ value: Any?) {
            if let dictionary = value as? [String: Any] {
                for key in ["labelName", "name"] {
                    if let name = dictionary[key] as? String {
                        let clean = name.trimmingCharacters(in: .whitespacesAndNewlines)
                        if !clean.isEmpty { names.append(clean); break }
                    }
                }
                for (key, nested) in dictionary where ["data", "result", "payload", "items", "list", "records", "labels", "contactLabels"].contains(key) {
                    visit(nested)
                }
            } else if let array = value as? [Any] {
                array.forEach(visit)
            } else if let name = value as? String {
                let clean = name.trimmingCharacters(in: .whitespacesAndNewlines)
                if !clean.isEmpty { names.append(clean) }
            }
        }
        visit(object)
        return Array(NSOrderedSet(array: names)) as? [String] ?? names
    }

    static func findPermissionDictionary(in object: Any?) -> [String: Any]? {
        if let dictionary = object as? [String: Any] {
            let keys = Set(dictionary.keys)
            if !keys.isDisjoint(with: ["onlyChat", "only_chat", "notSeeFriendMoments", "not_see_friend_moments", "notLetFriendSeeMyMoments", "not_let_friend_see_my_moments"]) {
                return dictionary
            }
            for key in ["permissions", "permission", "momentsPermission", "friendPermission", "data", "result", "payload", "contact", "detail"] {
                if let found = findPermissionDictionary(in: dictionary[key]) { return found }
            }
        } else if let array = object as? [Any] {
            for value in array {
                if let found = findPermissionDictionary(in: value) { return found }
            }
        }
        return nil
    }

    static func bool(in root: [String: Any], keys: [String]) -> Bool? {
        for key in keys {
            if let value = root[key] as? Bool { return value }
            if let value = root[key] as? NSNumber { return value.boolValue }
            if let value = root[key] as? String {
                switch value.lowercased() {
                case "true", "1", "yes": return true
                case "false", "0", "no": return false
                default: break
                }
            }
        }
        return nil
    }

    static func dictionaryArray(in object: Any?) -> [[String: Any]] {
        if let values = object as? [[String: Any]] { return values }
        guard let root = object as? [String: Any] else { return [] }
        for key in ["items", "records", "rows", "list", "data"] {
            if let values = root[key] as? [[String: Any]] { return values }
            if let nested = root[key] as? [String: Any] {
                let values = dictionaryArray(in: nested)
                if !values.isEmpty { return values }
            }
        }
        return []
    }

    static func string(in root: [String: Any], keys: [String]) -> String {
        for key in keys {
            if let value = root[key] as? String { return value.trimmingCharacters(in: .whitespacesAndNewlines) }
            if let value = root[key] as? NSNumber { return value.stringValue }
        }
        return ""
    }

    static func explicitSuccess(in object: Any?) -> Bool? {
        guard let root = object as? [String: Any] else { return nil }
        for key in ["success", "isSuccess", "succeeded"] {
            if let value = root[key] as? Bool { return value }
            if let value = root[key] as? NSNumber { return value.boolValue }
        }
        for key in ["data", "result", "payload"] {
            if let value = explicitSuccess(in: root[key]) { return value }
        }
        return nil
    }
}

final class OpenApiContactLabelsPermissionsViewController: UITableViewController {
    private let contactName: String
    private let contactID: String
    private let friendID: String
    private let avatarURL: URL?
    private let account: OpenApiSocialAccount
    private let service: OpenApiContactLabelsPermissionsService
    private var snapshot: OpenApiContactPrivacySnapshot?
    private var selectedLabels: Set<String> = []
    private var permissionValues: [Bool?] = [nil, nil, nil]
    private var requestTask: Task<Void, Never>?
    private var avatarLoadTask: MediaThumbnailRequest?

    init(
        contactName: String,
        contactID: String,
        friendID: String,
        avatarURL: URL?,
        account: OpenApiSocialAccount,
        service: OpenApiContactLabelsPermissionsService = .init()
    ) {
        self.contactName = contactName
        self.contactID = contactID
        self.friendID = friendID
        self.avatarURL = avatarURL
        self.account = account
        self.service = service
        super.init(style: .insetGrouped)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    deinit {
        requestTask?.cancel()
        avatarLoadTask?.cancel()
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        title = "标签与朋友圈权限"
        tableView.backgroundColor = .systemGroupedBackground
        tableView.register(UITableViewCell.self, forCellReuseIdentifier: "cell")
        tableView.rowHeight = UITableView.automaticDimension
        tableView.estimatedRowHeight = 58
        tableView.separatorInset = UIEdgeInsets(top: 0, left: 56, bottom: 0, right: 16)
        tableView.sectionHeaderTopPadding = 18
        tableView.tableHeaderView = makeContactHeader()
        let saveButton = UIBarButtonItem(title: "保存", style: .done, target: self, action: #selector(confirmSave))
        let createButton = UIBarButtonItem(image: UIImage(systemName: "plus"), style: .plain, target: self, action: #selector(promptCreateLabel))
        createButton.accessibilityLabel = "新建标签"
        navigationItem.rightBarButtonItems = [saveButton, createButton]
        saveButton.isEnabled = false
        configureContactHeader()
        loadSnapshot()
    }

    private func configureContactHeader() {
        let header = UIView(frame: CGRect(x: 0, y: 0, width: tableView.bounds.width, height: 92))
        let avatar = UIView()
        avatar.backgroundColor = UIColor(red: 0.12, green: 0.47, blue: 0.42, alpha: 1)
        avatar.layer.cornerRadius = 26
        avatar.clipsToBounds = true
        avatar.translatesAutoresizingMaskIntoConstraints = false
        let fallback = UILabel()
        fallback.text = String(contactName.prefix(1))
        fallback.textAlignment = .center
        fallback.textColor = .white
        fallback.font = .systemFont(ofSize: 24, weight: .bold)
        fallback.translatesAutoresizingMaskIntoConstraints = false
        let imageView = UIImageView()
        imageView.contentMode = .scaleAspectFill
        imageView.isHidden = true
        imageView.translatesAutoresizingMaskIntoConstraints = false
        avatar.addSubview(fallback)
        avatar.addSubview(imageView)
        let name = UILabel()
        name.text = contactName
        name.font = .systemFont(ofSize: 20, weight: .bold)
        name.translatesAutoresizingMaskIntoConstraints = false
        let wxid = UILabel()
        wxid.text = "WXID：\(friendID)"
        wxid.font = .systemFont(ofSize: 13)
        wxid.textColor = .secondaryLabel
        wxid.lineBreakMode = .byTruncatingMiddle
        wxid.translatesAutoresizingMaskIntoConstraints = false
        [avatar, name, wxid].forEach(header.addSubview)
        NSLayoutConstraint.activate([
            avatar.leadingAnchor.constraint(equalTo: header.leadingAnchor, constant: 20),
            avatar.centerYAnchor.constraint(equalTo: header.centerYAnchor),
            avatar.widthAnchor.constraint(equalToConstant: 52),
            avatar.heightAnchor.constraint(equalToConstant: 52),
            fallback.topAnchor.constraint(equalTo: avatar.topAnchor), fallback.leadingAnchor.constraint(equalTo: avatar.leadingAnchor),
            fallback.trailingAnchor.constraint(equalTo: avatar.trailingAnchor), fallback.bottomAnchor.constraint(equalTo: avatar.bottomAnchor),
            imageView.topAnchor.constraint(equalTo: avatar.topAnchor), imageView.leadingAnchor.constraint(equalTo: avatar.leadingAnchor),
            imageView.trailingAnchor.constraint(equalTo: avatar.trailingAnchor), imageView.bottomAnchor.constraint(equalTo: avatar.bottomAnchor),
            name.leadingAnchor.constraint(equalTo: avatar.trailingAnchor, constant: 14),
            name.trailingAnchor.constraint(equalTo: header.trailingAnchor, constant: -20),
            name.topAnchor.constraint(equalTo: avatar.topAnchor, constant: 4),
            wxid.leadingAnchor.constraint(equalTo: name.leadingAnchor), wxid.trailingAnchor.constraint(equalTo: name.trailingAnchor),
            wxid.topAnchor.constraint(equalTo: name.bottomAnchor, constant: 5)
        ])
        tableView.tableHeaderView = header
        guard let avatarURL else { return }
        let targetSize = CGSize(width: 52, height: 52)
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
    }

    private func makeContactHeader() -> UIView {
        let header = UIView(frame: CGRect(x: 0, y: 0, width: 0, height: 104))

        let avatar = UIImageView(image: UIImage(systemName: "person.crop.circle.fill"))
        avatar.tintColor = .systemBlue
        avatar.contentMode = .scaleAspectFit
        avatar.translatesAutoresizingMaskIntoConstraints = false

        let nameLabel = UILabel()
        nameLabel.text = contactName
        nameLabel.font = .systemFont(ofSize: 20, weight: .bold)
        nameLabel.textColor = .label

        let accountLabel = UILabel()
        accountLabel.text = "当前帐号：\(account.weChatID)"
        accountLabel.font = .systemFont(ofSize: 13)
        accountLabel.textColor = .secondaryLabel
        accountLabel.lineBreakMode = .byTruncatingMiddle

        let labels = UIStackView(arrangedSubviews: [nameLabel, accountLabel])
        labels.axis = .vertical
        labels.spacing = 4
        labels.translatesAutoresizingMaskIntoConstraints = false

        header.addSubview(avatar)
        header.addSubview(labels)
        NSLayoutConstraint.activate([
            avatar.leadingAnchor.constraint(equalTo: header.leadingAnchor, constant: 20),
            avatar.centerYAnchor.constraint(equalTo: header.centerYAnchor),
            avatar.widthAnchor.constraint(equalToConstant: 52),
            avatar.heightAnchor.constraint(equalToConstant: 52),
            labels.leadingAnchor.constraint(equalTo: avatar.trailingAnchor, constant: 14),
            labels.trailingAnchor.constraint(equalTo: header.trailingAnchor, constant: -20),
            labels.centerYAnchor.constraint(equalTo: avatar.centerYAnchor)
        ])
        return header
    }

    override func numberOfSections(in tableView: UITableView) -> Int { 3 }

    override func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        switch section {
        case 0: return max(snapshot?.availableLabels.count ?? 0, 1)
        case 1: return 3
        default: return 1
        }
    }

    override func tableView(_ tableView: UITableView, titleForHeaderInSection section: Int) -> String? {
        switch section {
        case 0: return "联系人标签"
        case 1: return "朋友圈权限"
        default: return "状态说明"
        }
    }

    override func tableView(_ tableView: UITableView, titleForFooterInSection section: Int) -> String? {
        guard section == 1 else { return nil }
        return "显示为“后端未返回”时，必须先手动选择明确状态，页面不会将未知状态当作关闭保存。"
    }

    override func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = tableView.dequeueReusableCell(withIdentifier: "cell", for: indexPath)
        var content = cell.defaultContentConfiguration()
        cell.accessoryView = nil
        cell.accessoryType = .none
        cell.selectionStyle = .default

        if indexPath.section == 0 {
            guard let labels = snapshot?.availableLabels, !labels.isEmpty else {
                content.text = snapshot == nil ? "正在读取标签..." : "当前账号暂无可用标签"
                content.secondaryText = snapshot == nil ? "正在同步当前帐号的联系人标签" : "点击右上角加号新建标签"
                content.textProperties.color = .secondaryLabel
                content.secondaryTextProperties.color = .tertiaryLabel
                if snapshot == nil {
                    let spinner = UIActivityIndicatorView(style: .medium)
                    spinner.startAnimating()
                    cell.accessoryView = spinner
                }
                cell.selectionStyle = .none
                cell.contentConfiguration = content
                return cell
            }
            let label = labels[indexPath.row]
            content.text = label
            content.image = UIImage(systemName: "tag")
            content.imageProperties.tintColor = selectedLabels.contains(label) ? .systemBlue : .tertiaryLabel
            cell.accessoryType = selectedLabels.contains(label) ? .checkmark : .none
        } else if indexPath.section == 1 {
            let titles = ["仅聊天", "不看他（她）的朋友圈", "不让他（她）看我的朋友圈"]
            let subtitles = [
                "开启后仅保留聊天相关互动",
                "开启后不再显示对方的朋友圈内容",
                "开启后对方无法查看我的朋友圈内容"
            ]
            let symbols = ["bubble.left.and.bubble.right", "eye.slash", "person.crop.circle.badge.xmark"]
            content.text = titles[indexPath.row]
            content.image = UIImage(systemName: symbols[indexPath.row])
            content.imageProperties.tintColor = .systemBlue
            let toggle = UISwitch()
            toggle.tag = indexPath.row
            toggle.isOn = permissionValues[indexPath.row] ?? false
            toggle.isEnabled = snapshot != nil
            toggle.addTarget(self, action: #selector(permissionChanged(_:)), for: .valueChanged)
            cell.accessoryView = toggle
            content.secondaryText = permissionValues[indexPath.row] == nil
                ? "后端未返回。请手动确认后再保存"
                : subtitles[indexPath.row]
            content.secondaryTextProperties.color = permissionValues[indexPath.row] == nil
                ? .systemOrange
                : .secondaryLabel
            cell.selectionStyle = .none
        } else {
            content.text = snapshot == nil ? "正在读取联系人设置" : "联系人设置已同步"
            content.secondaryText = snapshot == nil
                ? "完成后才可修改和保存"
                : "保存前会再次确认，并在成功后重新读取状态"
            content.image = UIImage(systemName: snapshot == nil ? "arrow.triangle.2.circlepath" : "checkmark.shield.fill")
            content.imageProperties.tintColor = snapshot == nil ? .secondaryLabel : .systemGreen
            content.secondaryTextProperties.color = .secondaryLabel
            cell.selectionStyle = .none
        }
        content.textProperties.font = .systemFont(ofSize: 16, weight: .medium)
        content.directionalLayoutMargins = NSDirectionalEdgeInsets(top: 10, leading: 0, bottom: 10, trailing: 0)
        cell.contentConfiguration = content
        return cell
    }

    override func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        tableView.deselectRow(at: indexPath, animated: true)
        guard indexPath.section == 0, let labels = snapshot?.availableLabels, !labels.isEmpty else { return }
        let label = labels[indexPath.row]
        if selectedLabels.contains(label) { selectedLabels.remove(label) } else { selectedLabels.insert(label) }
        tableView.reloadRows(at: [indexPath], with: .none)
        refreshSaveState()
    }

    @objc private func permissionChanged(_ sender: UISwitch) {
        permissionValues[sender.tag] = sender.isOn
        tableView.reloadRows(at: [IndexPath(row: sender.tag, section: 1)], with: .none)
        refreshSaveState()
    }

    @objc private func promptCreateLabel() {
        let alert = UIAlertController(title: "新建账号标签", message: "新标签会创建到当前微信账号，创建后仍需勾选并保存，才会应用到此好友。", preferredStyle: .alert)
        alert.addTextField { field in
            field.placeholder = "标签名称"
            field.clearButtonMode = .whileEditing
        }
        alert.addAction(UIAlertAction(title: "取消", style: .cancel))
        alert.addAction(UIAlertAction(title: "创建", style: .default) { [weak self, weak alert] _ in
            let name = alert?.textFields?.first?.text ?? ""
            self?.confirmCreateLabel(name)
        })
        present(alert, animated: true)
    }

    private func confirmCreateLabel(_ name: String) {
        let clean = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !clean.isEmpty else { showAlert(title: "无法创建", message: "请输入标签名称。"); return }
        let alert = UIAlertController(title: "确认新建标签", message: "将在账号 \(account.weChatID) 下创建“\(clean)”。", preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "取消", style: .cancel))
        alert.addAction(UIAlertAction(title: "确认创建", style: .default) { [weak self] _ in
            self?.performCreateLabel(clean)
        })
        present(alert, animated: true)
    }

    private func performCreateLabel(_ name: String) {
        requestTask?.cancel()
        requestTask = Task { [weak self] in
            guard let self else { return }
            do {
                try await service.createLabel(name, account: account)
                guard !Task.isCancelled else { return }
                let reloaded: OpenApiContactPrivacySnapshot?
                let reloadError: Error?
                do {
                    reloaded = try await service.load(contactID: contactID, friendID: friendID, weChatID: account.weChatID)
                    reloadError = nil
                } catch {
                    reloaded = nil
                    reloadError = error
                }
                await MainActor.run {
                    if let reloaded {
                        self.snapshot = reloaded
                        self.selectedLabels = reloaded.selectedLabels
                        self.permissionValues = [reloaded.onlyChat, reloaded.notSeeFriendMoments, reloaded.notLetFriendSeeMyMoments]
                        self.tableView.reloadData()
                        self.refreshSaveState()
                        self.showAlert(title: "标签已创建", message: "请确认“\(name)”已出现在列表中；勾选后点击保存，才会应用到此好友。")
                    } else {
                        self.showAlert(
                            title: "标签已创建，刷新失败",
                            message: "创建接口已经成功，但重新读取页面失败：\(reloadError?.localizedDescription ?? "未知错误")。请退出后重新进入查看。"
                        )
                    }
                }
            } catch {
                guard !Task.isCancelled else { return }
                await MainActor.run { self.showAlert(title: "创建失败", message: error.localizedDescription) }
            }
        }
    }

    private func loadSnapshot() {
        requestTask?.cancel()
        requestTask = Task { [weak self] in
            guard let self else { return }
            do {
                let value = try await service.load(contactID: contactID, friendID: friendID, weChatID: account.weChatID)
                guard !Task.isCancelled else { return }
                await MainActor.run {
                    self.snapshot = value
                    self.selectedLabels = value.selectedLabels
                    self.permissionValues = [value.onlyChat, value.notSeeFriendMoments, value.notLetFriendSeeMyMoments]
                    self.tableView.reloadData()
                    self.refreshSaveState()
                }
            } catch {
                guard !Task.isCancelled else { return }
                await MainActor.run { self.showAlert(title: "读取失败", message: error.localizedDescription) }
            }
        }
    }

    private func refreshSaveState() {
        guard let snapshot else { navigationItem.rightBarButtonItems?.first?.isEnabled = false; return }
        let originalPermissions = [snapshot.onlyChat, snapshot.notSeeFriendMoments, snapshot.notLetFriendSeeMyMoments]
        navigationItem.rightBarButtonItems?.first?.isEnabled = selectedLabels != snapshot.selectedLabels || permissionValues != originalPermissions
    }

    @objc private func confirmSave() {
        guard let snapshot else { return }
        let labelsChanged = selectedLabels != snapshot.selectedLabels
        let permissionsChanged = permissionValues != [snapshot.onlyChat, snapshot.notSeeFriendMoments, snapshot.notLetFriendSeeMyMoments]
        if permissionsChanged && permissionValues.contains(where: { $0 == nil }) {
            showAlert(title: "无法保存", message: "朋友圈权限包含后端未返回的未知状态。请逐项切换为明确的开启或关闭后再保存。")
            return
        }
        var changes: [String] = []
        if labelsChanged {
            let labelText = selectedLabels.sorted().joined(separator: "、")
            changes.append("标签：\(labelText.isEmpty ? "无标签" : labelText)")
        }
        if permissionsChanged {
            changes.append("仅聊天：\(permissionValues[0] == true ? "开启" : "关闭")")
            changes.append("不看对方朋友圈：\(permissionValues[1] == true ? "开启" : "关闭")")
            changes.append("不让对方看我：\(permissionValues[2] == true ? "开启" : "关闭")")
        }
        let alert = UIAlertController(title: "确认修改 \(contactName)", message: changes.joined(separator: "\n"), preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "取消", style: .cancel))
        alert.addAction(UIAlertAction(title: "确认保存", style: .destructive) { [weak self] _ in
            self?.performSave(labelsChanged: labelsChanged, permissionsChanged: permissionsChanged)
        })
        present(alert, animated: true)
    }

    private func performSave(labelsChanged: Bool, permissionsChanged: Bool) {
        navigationItem.rightBarButtonItems?.first?.isEnabled = false
        requestTask?.cancel()
        requestTask = Task { [weak self] in
            guard let self else { return }
            do {
                if labelsChanged {
                    try await service.saveLabels(selectedLabels, account: account, friendID: friendID)
                }
                if permissionsChanged {
                    try await service.savePermissions(
                        onlyChat: permissionValues[0] == true,
                        notSeeFriendMoments: permissionValues[1] == true,
                        notLetFriendSeeMyMoments: permissionValues[2] == true,
                        account: account,
                        friendID: friendID
                    )
                }
                guard !Task.isCancelled else { return }
                let reloaded = try await service.load(contactID: contactID, friendID: friendID, weChatID: account.weChatID)
                await MainActor.run {
                    self.snapshot = reloaded
                    self.selectedLabels = reloaded.selectedLabels
                    self.permissionValues = [reloaded.onlyChat, reloaded.notSeeFriendMoments, reloaded.notLetFriendSeeMyMoments]
                    self.tableView.reloadData()
                    self.refreshSaveState()
                    self.showAlert(title: "保存成功", message: "后端任务已完成，并已重新读取联系人状态。")
                }
            } catch {
                guard !Task.isCancelled else { return }
                await MainActor.run {
                    self.refreshSaveState()
                    self.showAlert(title: "保存失败", message: error.localizedDescription)
                }
            }
        }
    }

    private func showAlert(title: String, message: String) {
        let alert = UIAlertController(title: title, message: message, preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "知道了", style: .default))
        present(alert, animated: true)
    }
}
