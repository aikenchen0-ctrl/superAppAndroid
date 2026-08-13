import UIKit

final class GroupInfoViewController: UIViewController {
    struct Settings {
        let groupName: String
        let remark: String
        let myGroupName: String
        let showsAvatar: Bool
        let showsName: Bool
    }

    var onRemoveMember: ((ChatParticipant, @escaping (Bool) -> Void) -> Void)?
    var onRenameGroup: ((String, @escaping (Bool) -> Void) -> Void)?
    var onSaveSettings: ((Settings) -> Void)?
    var onSendGroupNotice: ((String) -> Void)?
    var onRefreshGroup: (() -> Void)?
    var onInviteMembers: (() -> Void)?
    var onPullQRCode: (() -> Void)?
    var onExitGroup: (() -> Void)?
    var onAddFriendFromGroup: ((ChatParticipant) -> Void)?

    private let group: ChatParticipant
    private var groupName: String
    private var remark: String
    private var myGroupName: String
    private var showsAvatar: Bool
    private var showsName: Bool
    private var members: [ChatParticipant]
    private var friendMemberIDs: Set<UUID>
    private var pendingRemovalMemberIDs = Set<UUID>()
    private var memberStatusText: String?
    private let tableView = UITableView(frame: .zero, style: .insetGrouped)

    init(
        group: ChatParticipant,
        remark: String,
        myGroupName: String,
        showsAvatar: Bool,
        showsName: Bool,
        members: [ChatParticipant],
        friendMemberIDs: Set<UUID>
    ) {
        self.group = group
        self.groupName = group.displayName
        self.remark = remark
        self.myGroupName = myGroupName
        self.showsAvatar = showsAvatar
        self.showsName = showsName
        self.members = members
        self.friendMemberIDs = friendMemberIDs
        super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        title = "群聊信息"
        view.backgroundColor = UIColor.systemGroupedBackground
        navigationItem.leftBarButtonItem = UIBarButtonItem(
            title: "返回",
            style: .plain,
            target: self,
            action: #selector(popBack)
        )
        navigationItem.rightBarButtonItem = UIBarButtonItem(
            title: "保存",
            style: .done,
            target: self,
            action: #selector(saveSettings)
        )
        tableView.backgroundColor = .clear
        tableView.dataSource = self
        tableView.delegate = self
        tableView.register(UITableViewCell.self, forCellReuseIdentifier: "GroupInfoCell")
        tableView.register(UITableViewCell.self, forCellReuseIdentifier: "GroupMemberCell")
        tableView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(tableView)

        NSLayoutConstraint.activate([
            tableView.topAnchor.constraint(equalTo: view.topAnchor),
            tableView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            tableView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            tableView.bottomAnchor.constraint(equalTo: view.bottomAnchor)
        ])
    }

    @objc private func popBack() {
        navigationController?.popViewController(animated: true)
    }

    @objc private func saveSettings() {
        guard showsAvatar || showsName else {
            let alert = UIAlertController(title: "无法保存", message: "群成员头像和名称必须至少启用一个。", preferredStyle: .alert)
            alert.addAction(UIAlertAction(title: "知道了", style: .default))
            present(alert, animated: true)
            return
        }
        onSaveSettings?(
            Settings(
                groupName: groupName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? group.displayName : groupName,
                remark: remark,
                myGroupName: myGroupName,
                showsAvatar: showsAvatar,
                showsName: showsName
            )
        )
        navigationController?.popViewController(animated: true)
    }

    @objc private func removeMember(_ sender: UIButton) {
        guard members.indices.contains(sender.tag) else { return }
        let member = members[sender.tag]
        let alert = UIAlertController(
            title: "移出群聊",
            message: "确定将\(member.displayName)移出\(group.displayName)？",
            preferredStyle: .alert
        )
        alert.addAction(UIAlertAction(title: "取消", style: .cancel))
        alert.addAction(UIAlertAction(title: "移出", style: .destructive) { [weak self] _ in
            guard let self else { return }
            self.setMemberRemovalPending(member.id, true)
            self.onRemoveMember?(member) { [weak self] success in
                guard let self else { return }
                self.setMemberRemovalPending(member.id, false)
                if !success {
                    self.tableView.reloadSections(IndexSet(integer: 1), with: .automatic)
                }
            }
        })
        present(alert, animated: true)
    }

    @objc private func addFriendFromMember(_ sender: UIButton) {
        guard members.indices.contains(sender.tag) else { return }
        let member = members[sender.tag]
        let alert = UIAlertController(
            title: "添加好友",
            message: "从「\(group.displayName)」添加「\(member.displayName)」为好友？",
            preferredStyle: .alert
        )
        alert.addAction(UIAlertAction(title: "取消", style: .cancel))
        alert.addAction(UIAlertAction(title: "添加", style: .default) { [weak self] _ in
            self?.onAddFriendFromGroup?(member)
        })
        present(alert, animated: true)
    }

    func updateMembers(_ members: [ChatParticipant], friendMemberIDs: Set<UUID>? = nil, status: String? = nil) {
        self.members = members
        pendingRemovalMemberIDs = pendingRemovalMemberIDs.intersection(Set(members.map(\.id)))
        if let friendMemberIDs {
            self.friendMemberIDs = friendMemberIDs
        }
        memberStatusText = status
        tableView.reloadSections(IndexSet(integer: 1), with: .automatic)
    }

    func markMemberAsFriend(_ member: ChatParticipant) {
        friendMemberIDs.insert(member.id)
        if let index = members.firstIndex(where: { $0.id == member.id }) {
            tableView.reloadRows(at: [IndexPath(row: index, section: 1)], with: .automatic)
        } else {
            tableView.reloadSections(IndexSet(integer: 1), with: .automatic)
        }
    }

    func setMembersStatus(_ text: String?) {
        memberStatusText = text
        tableView.reloadSections(IndexSet(integer: 1), with: .automatic)
    }

    private func setMemberRemovalPending(_ memberID: UUID, _ isPending: Bool) {
        if isPending {
            pendingRemovalMemberIDs.insert(memberID)
        } else {
            pendingRemovalMemberIDs.remove(memberID)
        }
        if let index = members.firstIndex(where: { $0.id == memberID }) {
            tableView.reloadRows(at: [IndexPath(row: index, section: 1)], with: .automatic)
        } else {
            tableView.reloadSections(IndexSet(integer: 1), with: .automatic)
        }
    }
}

extension GroupInfoViewController: UITableViewDataSource, UITableViewDelegate {
    func numberOfSections(in tableView: UITableView) -> Int {
        2
    }

    func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        section == 0 ? 10 : members.count
    }

    func tableView(_ tableView: UITableView, titleForHeaderInSection section: Int) -> String? {
        section == 0 ? "群资料" : "群成员 \(members.count)"
    }

    func tableView(_ tableView: UITableView, titleForFooterInSection section: Int) -> String? {
        section == 1 ? memberStatusText : nil
    }

    func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        if indexPath.section == 0 {
            let cell = tableView.dequeueReusableCell(withIdentifier: "GroupInfoCell", for: indexPath)
            var configuration = cell.defaultContentConfiguration()
            switch indexPath.row {
            case 0:
                configuration.text = "群聊名字"
                configuration.secondaryText = groupName
                configuration.image = UIImage(systemName: "person.3.fill")
                configuration.imageProperties.tintColor = group.tintColor
            case 1:
                configuration.text = "备注群"
                configuration.secondaryText = remark.isEmpty ? "未设置（仅保存在本机）" : remark
                configuration.image = UIImage(systemName: "tag.fill")
                configuration.imageProperties.tintColor = UIColor.systemOrange
            case 2:
                configuration.text = "我在群中的名字"
                configuration.secondaryText = myGroupName.isEmpty ? "未设置（服务端暂未提供修改接口）" : "\(myGroupName)（仅本机显示）"
                configuration.image = UIImage(systemName: "person.text.rectangle")
                configuration.imageProperties.tintColor = UIColor.systemBlue
            case 3:
                configuration.text = "设置群公告"
                configuration.secondaryText = "发布后显示在当前群聊消息中"
                configuration.image = UIImage(systemName: "megaphone.fill")
                configuration.imageProperties.tintColor = UIColor.systemRed
            case 4:
                configuration.text = "群内显示成员头像"
                configuration.secondaryText = showsAvatar ? "已启用" : "已停用"
                configuration.image = UIImage(systemName: "person.crop.circle")
                configuration.imageProperties.tintColor = UIColor.systemGreen
            case 5:
                configuration.text = "群内显示成员名称"
                configuration.secondaryText = showsName ? "已启用" : "已停用"
                configuration.image = UIImage(systemName: "textformat")
                configuration.imageProperties.tintColor = UIColor.systemPurple
            case 6:
                configuration.text = "刷新群资料"
                configuration.secondaryText = "同步群资料和群成员"
                configuration.image = UIImage(systemName: "arrow.triangle.2.circlepath")
                configuration.imageProperties.tintColor = UIColor.systemBlue
            case 7:
                configuration.text = "邀请好友入群"
                configuration.secondaryText = "从当前帐号好友中选择并邀请"
                configuration.image = UIImage(systemName: "person.badge.plus")
                configuration.imageProperties.tintColor = UIColor.systemGreen
            case 8:
                configuration.text = "拉取群二维码"
                configuration.secondaryText = "调用群二维码接口"
                configuration.image = UIImage(systemName: "qrcode")
                configuration.imageProperties.tintColor = UIColor.systemIndigo
            default:
                configuration.text = "退出群聊"
                configuration.secondaryText = "当前微信帐号退出这个群"
                configuration.image = UIImage(systemName: "rectangle.portrait.and.arrow.right")
                configuration.imageProperties.tintColor = UIColor.systemRed
            }
            configuration.textProperties.font = .systemFont(ofSize: 15, weight: .semibold)
            configuration.secondaryTextProperties.color = .secondaryLabel
            cell.contentConfiguration = configuration
            cell.accessoryType = (indexPath.row <= 3 || indexPath.row >= 6) ? .disclosureIndicator : .none
            if indexPath.row == 4 || indexPath.row == 5 {
                let toggle = UISwitch()
                toggle.isOn = indexPath.row == 4 ? showsAvatar : showsName
                toggle.tag = indexPath.row
                toggle.addTarget(self, action: #selector(toggleDisplayOption(_:)), for: .valueChanged)
                cell.accessoryView = toggle
            } else {
                cell.accessoryView = nil
            }
            cell.selectionStyle = .default
            return cell
        }

        let cell = tableView.dequeueReusableCell(withIdentifier: "GroupMemberCell", for: indexPath)
        let member = members[indexPath.row]
        let isRemoving = pendingRemovalMemberIDs.contains(member.id)
        var configuration = cell.defaultContentConfiguration()
        configuration.image = memberAvatarImage(for: member)
        configuration.imageProperties.maximumSize = CGSize(width: 40, height: 40)
        configuration.text = member.isCurrentUser ? "\(member.displayName)（我）" : member.displayName
        let isFriend = friendMemberIDs.contains(member.id)
        configuration.secondaryText = member.isCurrentUser ? "当前帐号" : (isRemoving ? "正在移出..." : (isFriend ? "群成员 · 已是好友" : "群成员"))
        configuration.textProperties.font = .systemFont(ofSize: 16, weight: .semibold)
        configuration.secondaryTextProperties.color = .secondaryLabel
        cell.contentConfiguration = configuration
        loadMemberAvatarIfNeeded(for: member, at: indexPath)
        cell.selectionStyle = .none
        cell.accessoryType = .none
        cell.alpha = isRemoving ? 0.68 : 1
        if member.isCurrentUser {
            cell.accessoryView = nil
        } else {
            let containerWidth: CGFloat = isFriend ? 64 : 136
            let container = UIView(frame: CGRect(x: 0, y: 0, width: containerWidth, height: 36))
            let stack = UIStackView()
            stack.axis = .horizontal
            stack.alignment = .center
            stack.spacing = 6
            stack.distribution = .fill
            stack.translatesAutoresizingMaskIntoConstraints = false
            container.addSubview(stack)
            if !isFriend {
                let addButton = UIButton(type: .system)
                addButton.setTitle("加好友", for: .normal)
                addButton.titleLabel?.font = .systemFont(ofSize: 13, weight: .semibold)
                addButton.tintColor = UIColor.systemGreen
                addButton.backgroundColor = UIColor.systemGreen.withAlphaComponent(0.10)
                addButton.layer.cornerRadius = 12
                addButton.layer.cornerCurve = .continuous
                addButton.applyContentInsets(top: 6, leading: 10, bottom: 6, trailing: 10)
                addButton.tag = indexPath.row
                addButton.addTarget(self, action: #selector(addFriendFromMember(_:)), for: .touchUpInside)
                addButton.isEnabled = !isRemoving
                addButton.translatesAutoresizingMaskIntoConstraints = false
                addButton.widthAnchor.constraint(equalToConstant: 72).isActive = true
                addButton.heightAnchor.constraint(equalToConstant: 30).isActive = true
                stack.addArrangedSubview(addButton)
            }
            let removeButton = UIButton(type: .system)
            removeButton.setTitle(isRemoving ? "移出中" : "移出", for: .normal)
            removeButton.titleLabel?.font = .systemFont(ofSize: 13, weight: .semibold)
            removeButton.tintColor = UIColor.systemRed
            removeButton.backgroundColor = UIColor.systemRed.withAlphaComponent(0.10)
            removeButton.layer.cornerRadius = 12
            removeButton.layer.cornerCurve = .continuous
            removeButton.applyContentInsets(top: 6, leading: 10, bottom: 6, trailing: 10)
            removeButton.tag = indexPath.row
            removeButton.addTarget(self, action: #selector(removeMember(_:)), for: .touchUpInside)
            removeButton.isEnabled = !isRemoving
            removeButton.translatesAutoresizingMaskIntoConstraints = false
            removeButton.widthAnchor.constraint(equalToConstant: isRemoving ? 64 : 54).isActive = true
            removeButton.heightAnchor.constraint(equalToConstant: 30).isActive = true
            stack.addArrangedSubview(removeButton)
            NSLayoutConstraint.activate([
                stack.centerYAnchor.constraint(equalTo: container.centerYAnchor),
                stack.trailingAnchor.constraint(equalTo: container.trailingAnchor),
                stack.leadingAnchor.constraint(greaterThanOrEqualTo: container.leadingAnchor)
            ])
            cell.accessoryView = container
        }
        return cell
    }

    private func memberAvatarImage(for member: ChatParticipant) -> UIImage {
        if let avatarURL = member.avatarURL,
           let cached = AvatarPipeline.shared.cachedImage(for: avatarURL, targetSize: CGSize(width: 40, height: 40)) {
            return Self.roundedAvatarImage(cached, size: CGSize(width: 40, height: 40))
        }
        return UIImage.sidebarPlaceholderAvatar(title: member.displayName, color: member.tintColor, size: CGSize(width: 40, height: 40))
    }

    private func loadMemberAvatarIfNeeded(for member: ChatParticipant, at indexPath: IndexPath) {
        guard let avatarURL = member.avatarURL,
              AvatarPipeline.shared.cachedImage(for: avatarURL, targetSize: CGSize(width: 40, height: 40)) == nil
        else { return }
        _ = AvatarPipeline.shared.load(
            avatarURL,
            targetSize: CGSize(width: 40, height: 40),
            priority: URLSessionTask.lowPriority
        ) { [weak self] image in
            guard let self, image != nil,
                  self.members.indices.contains(indexPath.row),
                  self.members[indexPath.row].id == member.id
            else { return }
            self.tableView.reloadRows(at: [indexPath], with: .none)
        }
    }

    private static func roundedAvatarImage(_ image: UIImage, size: CGSize) -> UIImage {
        let format = UIGraphicsImageRendererFormat()
        format.scale = UIScreen.main.scale
        return UIGraphicsImageRenderer(size: size, format: format).image { _ in
            let rect = CGRect(origin: .zero, size: size)
            UIBezierPath(roundedRect: rect, cornerRadius: min(size.width, size.height) * 0.26).addClip()
            image.draw(in: rect)
        }
    }

    func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        tableView.deselectRow(at: indexPath, animated: true)
        guard indexPath.section == 0 else { return }
        switch indexPath.row {
        case 0:
            presentEditField(title: "修改群聊名字", value: groupName) { [weak self] value in
                guard let self else { return }
                self.memberStatusText = "正在同步群聊名字..."
                self.tableView.reloadSections(IndexSet(integer: 0), with: .none)
                self.onRenameGroup?(value) { [weak self] success in
                    guard let self else { return }
                    if success { self.groupName = value }
                    self.memberStatusText = success ? "群聊名字已同步到微信" : "群聊名字修改失败，原名称已保留"
                    self.tableView.reloadData()
                }
            }
        case 1:
            presentEditField(title: "修改备注群", value: remark) { [weak self] value in
                self?.remark = value
                self?.tableView.reloadRows(at: [indexPath], with: .automatic)
            }
        case 2:
            presentEditField(title: "修改我在群中的名字", value: myGroupName) { [weak self] value in
                self?.myGroupName = value
                self?.tableView.reloadRows(at: [indexPath], with: .automatic)
            }
        case 3:
            presentGroupNoticeEditor()
        case 6:
            onRefreshGroup?()
        case 7:
            onInviteMembers?()
        case 8:
            onPullQRCode?()
        case 9:
            onExitGroup?()
        default:
            break
        }
    }

    @objc private func toggleDisplayOption(_ sender: UISwitch) {
        if sender.tag == 4 {
            guard sender.isOn || showsName else {
                sender.setOn(true, animated: true)
                showDisplayRequirementAlert()
                return
            }
            showsAvatar = sender.isOn
        } else {
            guard sender.isOn || showsAvatar else {
                sender.setOn(true, animated: true)
                showDisplayRequirementAlert()
                return
            }
            showsName = sender.isOn
        }
        tableView.reloadRows(at: [IndexPath(row: sender.tag, section: 0)], with: .automatic)
    }

    private func presentGroupNoticeEditor() {
        let alert = UIAlertController(title: "设置群公告", message: "公告会作为群聊消息发送到当前群。", preferredStyle: .alert)
        alert.addTextField { textField in
            textField.placeholder = "请输入群公告内容"
            textField.text = "请所有成员今天 18:00 前确认到场时间、物料负责人和收款状态。"
            textField.clearButtonMode = .whileEditing
        }
        alert.addAction(UIAlertAction(title: "取消", style: .cancel))
        alert.addAction(UIAlertAction(title: "发送", style: .default) { [weak self, weak alert] _ in
            let text = alert?.textFields?.first?.text ?? ""
            self?.onSendGroupNotice?(text)
        })
        present(alert, animated: true)
    }

    private func presentEditField(title: String, value: String, onSave: @escaping (String) -> Void) {
        let alert = UIAlertController(title: title, message: nil, preferredStyle: .alert)
        alert.addTextField { textField in
            textField.text = value
            textField.clearButtonMode = .whileEditing
        }
        alert.addAction(UIAlertAction(title: "取消", style: .cancel))
        alert.addAction(UIAlertAction(title: "保存", style: .default) { [weak alert] _ in
            let newValue = alert?.textFields?.first?.text?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            if !newValue.isEmpty {
                onSave(newValue)
            }
        })
        present(alert, animated: true)
    }

    private func showDisplayRequirementAlert() {
        let alert = UIAlertController(title: "至少保留一项", message: "群成员头像和名称必须启用一个。", preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "知道了", style: .default))
        present(alert, animated: true)
    }
}

final class OpenApiGroupInvitePickerViewController: UIViewController, UITableViewDataSource, UITableViewDelegate, UISearchBarDelegate {
    var onInvite: (([String]) -> Void)?
    var onManualInput: (() -> Void)?

    private let groupName: String
    private let accountName: String
    private var contacts: [OpenApiConversationContext]
    private var filteredContacts: [OpenApiConversationContext] = []
    private var selectedWxids = Set<String>()
    private let searchBar = UISearchBar()
    private let statusLabel = UILabel()
    private let tableView = UITableView(frame: .zero, style: .insetGrouped)

    init(groupName: String, accountName: String, contacts: [OpenApiConversationContext]) {
        self.groupName = groupName
        self.accountName = accountName
        self.contacts = contacts
        self.filteredContacts = contacts
        super.init(nibName: nil, bundle: nil)
        title = "邀请好友"
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = UIColor.systemGroupedBackground
        navigationItem.rightBarButtonItem = UIBarButtonItem(title: "邀请", style: .done, target: self, action: #selector(inviteSelected))
        navigationItem.rightBarButtonItem?.isEnabled = false
        configure()
        applyFilter()
        updateStatus("正在加载完整好友，当前可选 \(contacts.count) 个")
    }

    func updateContacts(_ contacts: [OpenApiConversationContext], status: String) {
        let selected = selectedWxids
        self.contacts = contacts
        selectedWxids = Set(contacts.map(\.wxid)).intersection(selected)
        applyFilter()
        updateStatus(status)
        updateInviteButton()
    }

    func updateStatus(_ text: String) {
        statusLabel.text = "\(accountName) · \(groupName)\n\(text)"
    }

    private func configure() {
        searchBar.placeholder = "搜索昵称、备注、微信号或 wxid"
        searchBar.delegate = self
        searchBar.searchBarStyle = .minimal
        searchBar.translatesAutoresizingMaskIntoConstraints = false

        statusLabel.font = .systemFont(ofSize: 13, weight: .medium)
        statusLabel.textColor = .secondaryLabel
        statusLabel.numberOfLines = 2
        statusLabel.translatesAutoresizingMaskIntoConstraints = false

        let manualButton = UIButton(type: .system)
        manualButton.setImage(UIImage(systemName: "keyboard"), for: .normal)
        manualButton.setTitle(" 输入 wxid 邀请", for: .normal)
        manualButton.titleLabel?.font = .systemFont(ofSize: 14, weight: .semibold)
        manualButton.backgroundColor = UIColor.systemBlue.withAlphaComponent(0.10)
        manualButton.layer.cornerRadius = 12
        manualButton.layer.cornerCurve = .continuous
        manualButton.applyContentInsets(top: 9, leading: 12, bottom: 9, trailing: 12)
        manualButton.addTarget(self, action: #selector(openManualInput), for: .touchUpInside)
        manualButton.translatesAutoresizingMaskIntoConstraints = false

        tableView.backgroundColor = .clear
        tableView.dataSource = self
        tableView.delegate = self
        tableView.rowHeight = 64
        tableView.keyboardDismissMode = .onDrag
        tableView.register(UITableViewCell.self, forCellReuseIdentifier: "InviteContactCell")
        tableView.translatesAutoresizingMaskIntoConstraints = false

        [searchBar, statusLabel, manualButton, tableView].forEach(view.addSubview)
        NSLayoutConstraint.activate([
            searchBar.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 6),
            searchBar.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 10),
            searchBar.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -10),

            statusLabel.topAnchor.constraint(equalTo: searchBar.bottomAnchor, constant: 2),
            statusLabel.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 22),
            statusLabel.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -22),

            manualButton.topAnchor.constraint(equalTo: statusLabel.bottomAnchor, constant: 10),
            manualButton.leadingAnchor.constraint(equalTo: statusLabel.leadingAnchor),
            manualButton.trailingAnchor.constraint(lessThanOrEqualTo: statusLabel.trailingAnchor),

            tableView.topAnchor.constraint(equalTo: manualButton.bottomAnchor, constant: 8),
            tableView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            tableView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            tableView.bottomAnchor.constraint(equalTo: view.bottomAnchor)
        ])
    }

    private func applyFilter() {
        let query = (searchBar.text ?? "").trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        if query.isEmpty {
            filteredContacts = contacts
        } else {
            filteredContacts = contacts.filter { contact in
                [
                    contact.displayName,
                    contact.remark,
                    contact.friendNo,
                    contact.wxid,
                    contact.phone,
                    contact.sourceChannel,
                    contact.customerLevel
                ].joined(separator: " ").lowercased().contains(query)
            }
        }
        tableView.reloadData()
    }

    private func updateInviteButton() {
        let count = selectedWxids.count
        navigationItem.rightBarButtonItem?.title = count > 0 ? "邀请(\(count))" : "邀请"
        navigationItem.rightBarButtonItem?.isEnabled = count > 0
    }

    @objc private func openManualInput() {
        onManualInput?()
    }

    @objc private func inviteSelected() {
        let wxids = contacts.map(\.wxid).filter { selectedWxids.contains($0) }
        guard !wxids.isEmpty else { return }
        onInvite?(wxids)
    }

    func searchBar(_ searchBar: UISearchBar, textDidChange searchText: String) {
        applyFilter()
    }

    func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        filteredContacts.count
    }

    func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = tableView.dequeueReusableCell(withIdentifier: "InviteContactCell", for: indexPath)
        let contact = filteredContacts[indexPath.row]
        var configuration = cell.defaultContentConfiguration()
        configuration.text = contact.displayName
        configuration.secondaryText = [contact.remark, contact.friendNo, contact.wxid].filter { !$0.isEmpty }.joined(separator: " · ")
        configuration.textProperties.font = .systemFont(ofSize: 16, weight: .semibold)
        configuration.secondaryTextProperties.color = .secondaryLabel
        configuration.image = avatarImage(for: contact)
        configuration.imageProperties.maximumSize = CGSize(width: 40, height: 40)
        cell.contentConfiguration = configuration
        cell.accessoryType = selectedWxids.contains(contact.wxid) ? .checkmark : .none
        cell.tintColor = UIColor.systemGreen
        cell.selectionStyle = .default
        loadAvatarIfNeeded(for: contact, at: indexPath)
        return cell
    }

    func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        tableView.deselectRow(at: indexPath, animated: true)
        let contact = filteredContacts[indexPath.row]
        if selectedWxids.contains(contact.wxid) {
            selectedWxids.remove(contact.wxid)
        } else {
            selectedWxids.insert(contact.wxid)
        }
        tableView.reloadRows(at: [indexPath], with: .automatic)
        updateInviteButton()
    }

    private func avatarImage(for contact: OpenApiConversationContext) -> UIImage {
        if let url = contact.avatar,
           let cached = AvatarPipeline.shared.cachedImage(for: url, targetSize: CGSize(width: 40, height: 40)) {
            return Self.roundedAvatarImage(cached, size: CGSize(width: 40, height: 40))
        }
        return UIImage.sidebarPlaceholderAvatar(title: contact.displayName, color: contact.tintColor, size: CGSize(width: 40, height: 40))
    }

    private func loadAvatarIfNeeded(for contact: OpenApiConversationContext, at indexPath: IndexPath) {
        guard let url = contact.avatar,
              AvatarPipeline.shared.cachedImage(for: url, targetSize: CGSize(width: 40, height: 40)) == nil
        else { return }
        _ = AvatarPipeline.shared.load(
            url,
            targetSize: CGSize(width: 40, height: 40),
            priority: URLSessionTask.lowPriority
        ) { [weak self] image in
            guard let self, image != nil,
                  self.filteredContacts.indices.contains(indexPath.row),
                  self.filteredContacts[indexPath.row].participantID == contact.participantID
            else { return }
            self.tableView.reloadRows(at: [indexPath], with: .none)
        }
    }

    private static func roundedAvatarImage(_ image: UIImage, size: CGSize) -> UIImage {
        let format = UIGraphicsImageRendererFormat()
        format.scale = UIScreen.main.scale
        return UIGraphicsImageRenderer(size: size, format: format).image { _ in
            let rect = CGRect(origin: .zero, size: size)
            UIBezierPath(roundedRect: rect, cornerRadius: min(size.width, size.height) * 0.26).addClip()
            image.draw(in: rect)
        }
    }
}
