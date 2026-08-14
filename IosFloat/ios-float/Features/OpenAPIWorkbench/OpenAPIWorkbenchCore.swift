import UIKit

final class OpenApiFieldCell: UITableViewCell {
    static let reuseIdentifier = "OpenApiFieldCell"

    private let keyLabel = UILabel()
    private let valueLabel = UILabel()
    private let subtitleLabel = UILabel()
    private let stackView = UIStackView()

    override init(style: UITableViewCell.CellStyle, reuseIdentifier: String?) {
        super.init(style: style, reuseIdentifier: reuseIdentifier)
        selectionStyle = .none
        backgroundColor = .clear
        contentView.backgroundColor = .clear

        keyLabel.font = .monospacedSystemFont(ofSize: 12, weight: .semibold)
        keyLabel.textColor = UIColor(red: 0.10, green: 0.22, blue: 0.34, alpha: 1)

        valueLabel.font = .systemFont(ofSize: 15, weight: .semibold)
        valueLabel.textColor = UIColor(red: 0.10, green: 0.12, blue: 0.14, alpha: 1)
        valueLabel.numberOfLines = 0

        subtitleLabel.font = .systemFont(ofSize: 12)
        subtitleLabel.textColor = .secondaryLabel
        subtitleLabel.numberOfLines = 0

        stackView.axis = .vertical
        stackView.spacing = 5
        stackView.translatesAutoresizingMaskIntoConstraints = false
        contentView.addSubview(stackView)
        stackView.addArrangedSubview(keyLabel)
        stackView.addArrangedSubview(valueLabel)
        stackView.addArrangedSubview(subtitleLabel)

        NSLayoutConstraint.activate([
            stackView.topAnchor.constraint(equalTo: contentView.topAnchor, constant: 10),
            stackView.leadingAnchor.constraint(equalTo: contentView.leadingAnchor, constant: 18),
            stackView.trailingAnchor.constraint(equalTo: contentView.trailingAnchor, constant: -18),
            stackView.bottomAnchor.constraint(equalTo: contentView.bottomAnchor, constant: -10)
        ])
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    func configure(_ row: OpenApiFieldRow) {
        keyLabel.text = row.key
        valueLabel.text = row.value
        subtitleLabel.text = row.subtitle
        subtitleLabel.isHidden = row.subtitle?.isEmpty ?? true
    }
}

struct OpenApiEditableRequest {
    let method: String
    let path: String
    let query: [URLQueryItem]
    let body: Any?
}

final class OpenApiRequestEditorViewController: UIViewController, UITextFieldDelegate {
    private let actionTitle: String
    private let actionSubtitle: String
    private let method: String
    private let isRisky: Bool
    private let initialPath: String
    private let initialQuery: [URLQueryItem]
    private let initialBody: Any?
    private let onSubmit: (OpenApiEditableRequest) -> Void

    private let scrollView = UIScrollView()
    private let stackView = UIStackView()
    private let pathField = UITextField()
    private let queryTextView = UITextView()
    private let bodyTextView = UITextView()
    private let hintLabel = UILabel()

    init(
        title: String,
        subtitle: String,
        method: String,
        path: String,
        query: [URLQueryItem],
        body: Any?,
        isRisky: Bool,
        onSubmit: @escaping (OpenApiEditableRequest) -> Void
    ) {
        self.actionTitle = title
        self.actionSubtitle = subtitle
        self.method = method
        self.initialPath = path
        self.initialQuery = query
        self.initialBody = body
        self.isRisky = isRisky
        self.onSubmit = onSubmit
        super.init(nibName: nil, bundle: nil)
        self.title = title
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = UIColor(red: 0.95, green: 0.96, blue: 0.97, alpha: 1)
        navigationItem.rightBarButtonItem = UIBarButtonItem(title: "调用", style: .done, target: self, action: #selector(submitRequest))
        configureLayout()
    }

    private func configureLayout() {
        scrollView.keyboardDismissMode = .interactive
        scrollView.alwaysBounceVertical = true
        scrollView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(scrollView)

        stackView.axis = .vertical
        stackView.spacing = 14
        stackView.translatesAutoresizingMaskIntoConstraints = false
        scrollView.addSubview(stackView)

        hintLabel.text = [method, actionSubtitle].filter { !$0.isEmpty }.joined(separator: "\n")
        hintLabel.font = .systemFont(ofSize: 13, weight: .medium)
        hintLabel.textColor = .secondaryLabel
        hintLabel.numberOfLines = 0

        pathField.text = initialPath
        pathField.placeholder = "/openapi/v1/..."
        pathField.borderStyle = .none
        pathField.backgroundColor = .systemBackground
        pathField.layer.cornerRadius = 13
        pathField.layer.cornerCurve = .continuous
        pathField.font = .monospacedSystemFont(ofSize: 13, weight: .medium)
        pathField.autocapitalizationType = .none
        pathField.autocorrectionType = .no
        pathField.clearButtonMode = .whileEditing
        pathField.returnKeyType = .done
        pathField.delegate = self
        pathField.leftView = UIView(frame: CGRect(x: 0, y: 0, width: 12, height: 1))
        pathField.leftViewMode = .always
        pathField.heightAnchor.constraint(equalToConstant: 44).isActive = true

        configureTextView(queryTextView, minHeight: 86)
        queryTextView.text = Self.queryString(from: initialQuery)

        configureTextView(bodyTextView, minHeight: 280)
        bodyTextView.text = Self.prettyJSON(initialBody)

        stackView.addArrangedSubview(card(title: "接口", subtitle: "可以把 path 中的 demo ID 改成真实 wxid、群 ID 或素材 ID。", content: pathField))
        stackView.addArrangedSubview(card(title: "Query", subtitle: "格式：deviceUuid=xxx&weChatId=xxx。没有参数可留空。", content: queryTextView))
        stackView.addArrangedSubview(card(title: "JSON Body", subtitle: "发送前可编辑真实参数；图片、语音、文件和缩略图需要填手机可访问的 http/https URL。", content: bodyTextView))
        stackView.addArrangedSubview(hintLabel)

        NSLayoutConstraint.activate([
            scrollView.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
            scrollView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            scrollView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            scrollView.bottomAnchor.constraint(equalTo: view.bottomAnchor),

            stackView.topAnchor.constraint(equalTo: scrollView.contentLayoutGuide.topAnchor, constant: 16),
            stackView.leadingAnchor.constraint(equalTo: scrollView.frameLayoutGuide.leadingAnchor, constant: 16),
            stackView.trailingAnchor.constraint(equalTo: scrollView.frameLayoutGuide.trailingAnchor, constant: -16),
            stackView.bottomAnchor.constraint(equalTo: scrollView.contentLayoutGuide.bottomAnchor, constant: -24)
        ])
    }

    private func configureTextView(_ textView: UITextView, minHeight: CGFloat) {
        textView.font = .monospacedSystemFont(ofSize: 13, weight: .regular)
        textView.textColor = UIColor(red: 0.10, green: 0.13, blue: 0.16, alpha: 1)
        textView.backgroundColor = .systemBackground
        textView.layer.cornerRadius = 13
        textView.layer.cornerCurve = .continuous
        textView.textContainerInset = UIEdgeInsets(top: 12, left: 10, bottom: 12, right: 10)
        textView.autocapitalizationType = .none
        textView.autocorrectionType = .no
        textView.isScrollEnabled = true
        textView.heightAnchor.constraint(greaterThanOrEqualToConstant: minHeight).isActive = true
    }

    private func card(title: String, subtitle: String, content: UIView) -> UIView {
        let container = UIView()
        container.backgroundColor = UIColor.secondarySystemGroupedBackground
        container.layer.cornerRadius = 18
        container.layer.cornerCurve = .continuous

        let titleLabel = UILabel()
        titleLabel.text = title
        titleLabel.font = .systemFont(ofSize: 16, weight: .bold)
        titleLabel.textColor = .label

        let subtitleLabel = UILabel()
        subtitleLabel.text = subtitle
        subtitleLabel.font = .systemFont(ofSize: 12.5, weight: .medium)
        subtitleLabel.textColor = .secondaryLabel
        subtitleLabel.numberOfLines = 0

        let stack = UIStackView(arrangedSubviews: [titleLabel, subtitleLabel, content])
        stack.axis = .vertical
        stack.spacing = 10
        stack.translatesAutoresizingMaskIntoConstraints = false
        container.addSubview(stack)

        NSLayoutConstraint.activate([
            stack.topAnchor.constraint(equalTo: container.topAnchor, constant: 14),
            stack.leadingAnchor.constraint(equalTo: container.leadingAnchor, constant: 14),
            stack.trailingAnchor.constraint(equalTo: container.trailingAnchor, constant: -14),
            stack.bottomAnchor.constraint(equalTo: container.bottomAnchor, constant: -14)
        ])
        return container
    }

    @objc private func submitRequest() {
        view.endEditing(true)
        let path = pathField.text?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        guard path.hasPrefix("/openapi/v1/") || path.hasPrefix("/openapi/docs/") else {
            showError("接口路径需要以 /openapi/v1/ 或 /openapi/docs/ 开头。")
            return
        }

        do {
            let request = OpenApiEditableRequest(
                method: method,
                path: path,
                query: try parsedQuery(),
                body: try parsedBody()
            )
            if isRisky {
                let alert = UIAlertController(
                    title: "确认调用",
                    message: "\(actionTitle)\n\(method) \(path)\n这个接口可能产生真实业务动作。",
                    preferredStyle: .alert
                )
                alert.addAction(UIAlertAction(title: "取消", style: .cancel))
                alert.addAction(UIAlertAction(title: "确认调用", style: .destructive) { [weak self] _ in
                    self?.finish(request)
                })
                present(alert, animated: true)
            } else {
                finish(request)
            }
        } catch {
            showError(error.localizedDescription)
        }
    }

    private func finish(_ request: OpenApiEditableRequest) {
        navigationController?.popViewController(animated: true)
        DispatchQueue.main.async { [onSubmit] in
            onSubmit(request)
        }
    }

    private func parsedQuery() throws -> [URLQueryItem] {
        let text = queryTextView.text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { return [] }
        let compact = text
            .replacingOccurrences(of: "\n", with: "&")
            .replacingOccurrences(of: "&&", with: "&")
            .trimmingCharacters(in: CharacterSet(charactersIn: "?& \n\t"))
        guard !compact.isEmpty else { return [] }
        var components = URLComponents()
        components.percentEncodedQuery = compact
        if let items = components.queryItems {
            return items
        }
        throw NSError(domain: "OpenApiRequestEditor", code: 1, userInfo: [NSLocalizedDescriptionKey: "Query 格式不正确。"])
    }

    private func parsedBody() throws -> Any? {
        let text = bodyTextView.text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { return nil }
        guard let data = text.data(using: .utf8) else {
            throw NSError(domain: "OpenApiRequestEditor", code: 2, userInfo: [NSLocalizedDescriptionKey: "JSON Body 格式不正确。"])
        }
        let object = try JSONSerialization.jsonObject(with: data)
        if JSONSerialization.isValidJSONObject(object) {
            return object
        }
        throw NSError(domain: "OpenApiRequestEditor", code: 3, userInfo: [NSLocalizedDescriptionKey: "JSON Body 顶层需要是对象或数组。"])
    }

    private func showError(_ message: String) {
        let alert = UIAlertController(title: "无法调用", message: message, preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "知道了", style: .default))
        present(alert, animated: true)
    }

    func textFieldShouldReturn(_ textField: UITextField) -> Bool {
        textField.resignFirstResponder()
        return true
    }

    private static func queryString(from items: [URLQueryItem]) -> String {
        guard !items.isEmpty else { return "" }
        var components = URLComponents()
        components.queryItems = items
        return components.percentEncodedQuery ?? items.map { item in
            "\(item.name)=\(item.value ?? "")"
        }.joined(separator: "&")
    }

    private static func prettyJSON(_ object: Any?) -> String {
        guard let object else { return "" }
        guard JSONSerialization.isValidJSONObject(object),
              let data = try? JSONSerialization.data(withJSONObject: object, options: [.prettyPrinted]),
              let text = String(data: data, encoding: .utf8)
        else { return "" }
        return text.replacingOccurrences(of: "\\/", with: "/")
    }
}

class OpenApiRowsViewController: UIViewController, UITableViewDataSource, UITableViewDelegate {
    let tableView = UITableView(frame: .zero, style: .insetGrouped)
    var sections: [(title: String, rows: [OpenApiFieldRow])] = []

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = UIColor(red: 0.95, green: 0.96, blue: 0.97, alpha: 1)
        navigationItem.rightBarButtonItem = UIBarButtonItem(title: "环境", style: .plain, target: self, action: #selector(openRuntimeEnvironment))
        tableView.backgroundColor = .clear
        tableView.dataSource = self
        tableView.delegate = self
        tableView.register(OpenApiFieldCell.self, forCellReuseIdentifier: OpenApiFieldCell.reuseIdentifier)
        tableView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(tableView)
        NSLayoutConstraint.activate([
            tableView.topAnchor.constraint(equalTo: view.topAnchor),
            tableView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            tableView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            tableView.bottomAnchor.constraint(equalTo: view.bottomAnchor)
        ])
        showCircularPageLoading(title: "加载数据")
    }

    @objc func openRuntimeEnvironment() {
        navigationController?.pushViewController(OpenApiEnvironmentViewController(), animated: true)
    }

    func numberOfSections(in tableView: UITableView) -> Int {
        sections.count
    }

    func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        sections[section].rows.count
    }

    func tableView(_ tableView: UITableView, titleForHeaderInSection section: Int) -> String? {
        sections[section].title
    }

    func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = tableView.dequeueReusableCell(withIdentifier: OpenApiFieldCell.reuseIdentifier, for: indexPath) as? OpenApiFieldCell
            ?? OpenApiFieldCell(style: .default, reuseIdentifier: OpenApiFieldCell.reuseIdentifier)
        cell.configure(sections[indexPath.section].rows[indexPath.row])
        return cell
    }
}

final class OpenApiEnvironmentViewController: OpenApiRowsViewController {
    override func viewDidLoad() {
        super.viewDidLoad()
        title = "OpenAPI 环境"
        navigationItem.rightBarButtonItems = [
            UIBarButtonItem(title: "校验", style: .done, target: self, action: #selector(validateOpenApi)),
            UIBarButtonItem(title: "登录", style: .plain, target: self, action: #selector(loginAndCreateKey)),
            UIBarButtonItem(title: "配置", style: .plain, target: self, action: #selector(editConfig))
        ]
        rebuildSections()
    }

    @objc private func openModules() {
        navigationController?.pushViewController(AppKitModulesViewController(), animated: true)
    }

    @objc private func editConfig() {
        let config = OpenApiConfiguration.current
        let alert = UIAlertController(title: "OpenAPI 配置", message: "填写服务地址和 X-API-Key。", preferredStyle: .alert)
        alert.addTextField { field in
            field.placeholder = "服务地址"
            field.text = config.baseURL.absoluteString
            field.keyboardType = .URL
            field.autocapitalizationType = .none
        }
        alert.addTextField { field in
            field.placeholder = "X-API-Key"
            field.text = config.apiKey
            field.autocapitalizationType = .none
            field.autocorrectionType = .no
        }
        alert.addTextField { field in
            field.placeholder = "已验证上传字段名，可留空"
            field.text = OpenApiConfiguration.verifiedMediaMultipartFieldName ?? ""
            field.autocapitalizationType = .none
            field.autocorrectionType = .no
        }
        alert.addAction(UIAlertAction(title: "取消", style: .cancel))
        alert.addAction(UIAlertAction(title: "保存", style: .default) { [weak self, weak alert] _ in
            let baseURL = alert?.textFields?.first?.text?.trimmingCharacters(in: .whitespacesAndNewlines) ?? OpenApiConfiguration.defaultBaseURL
            let apiKey = alert?.textFields?.dropFirst().first?.text?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            let uploadField = alert?.textFields?.dropFirst(2).first?.text?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            OpenApiConfiguration.save(baseURL: baseURL, apiKey: apiKey)
            ChatSQLiteStore.shared.setString(uploadField.isEmpty ? nil : uploadField, forKey: OpenApiConfiguration.mediaMultipartFieldNameKey)
            UserDefaults.standard.set(uploadField, forKey: OpenApiConfiguration.mediaMultipartFieldNameKey)
            self?.rebuildSections()
            self?.validateOpenApi()
        })
        present(alert, animated: true)
    }

    @objc private func loginAndCreateKey() {
        let alert = UIAlertController(title: "登录获取 Key", message: "使用后台账号登录后会创建一个新的 OpenAPI Key，并把账号密码保存到本机 Keychain。", preferredStyle: .alert)
        alert.addTextField { field in
            field.placeholder = "帐号"
            field.text = OpenApiConfiguration.savedLoginUsername
            field.keyboardType = .emailAddress
            field.autocapitalizationType = .none
            field.autocorrectionType = .no
            field.textContentType = .username
        }
        alert.addTextField { field in
            field.placeholder = "密码"
            field.text = OpenApiConfiguration.savedLoginPassword
            field.isSecureTextEntry = true
            field.textContentType = .password
        }
        alert.addAction(UIAlertAction(title: "取消", style: .cancel))
        alert.addAction(UIAlertAction(title: "登录并创建", style: .default) { [weak self, weak alert] _ in
            guard let self else { return }
            let username = alert?.textFields?.first?.text?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            let password = alert?.textFields?.dropFirst().first?.text ?? ""
            self.sections = [(title: "登录中", rows: [OpenApiFieldRow(key: "status", value: "正在登录并创建 OpenAPI Key...", subtitle: nil)])]
            self.tableView.reloadData()
            Task { @MainActor in
                do {
                    _ = try await OpenApiHTTPClient.login(username: username, password: password)
                    let key = try await OpenApiHTTPClient.createAPIKey(name: "只发 iOS OpenAPI 联调", remark: "iOS 只发应用 OpenAPI 接口联调")
                    OpenApiConfiguration.saveLoginCredentials(username: username, password: password)
                    self.sections = [(
                        title: "创建成功",
                        rows: [
                            OpenApiFieldRow(key: "X-API-Key", value: key.maskedSecret(), subtitle: "完整 Key 已保存到本机配置。"),
                            OpenApiFieldRow(key: "帐号密码", value: "已保存", subtitle: "账号和密码已保存到本机 Keychain，下次登录弹窗会自动填入。"),
                            OpenApiFieldRow(key: "next", value: "正在校验 /openapi/v1/quick-start", subtitle: nil)
                        ]
                    )]
                    self.tableView.reloadData()
                    self.validateOpenApi()
                } catch {
                    self.sections = [(title: "创建失败", rows: OpenApiHTTPClient.errorRows(endpoint: "/api/auth/login + /api/openapi-keys", error: error))]
                    self.tableView.reloadData()
                }
            }
        })
        present(alert, animated: true)
    }

    @objc private func validateOpenApi() {
        sections = [(title: "校验中", rows: [OpenApiFieldRow(key: "request", value: "/openapi/v1/quick-start", subtitle: "正在用当前 X-API-Key 调用真实接口。")])]
        tableView.reloadData()
        Task { @MainActor in
            do {
                let result = try await OpenApiHTTPClient.request("GET", path: "/openapi/v1/quick-start")
                self.sections = [
                    (title: "当前配置", rows: OpenApiRuntime().authRows),
                    (title: "Quick Start", rows: OpenApiHTTPClient.rows(from: result, maxItems: 36))
                ]
            } catch {
                self.sections = [
                    (title: "当前配置", rows: OpenApiRuntime().authRows),
                    (title: "校验失败", rows: OpenApiHTTPClient.errorRows(endpoint: "/openapi/v1/quick-start", error: error))
                ]
            }
            self.tableView.reloadData()
        }
    }

    private func rebuildSections() {
        let runtime = OpenApiRuntime()
        sections = [
            (title: "网络层 / 鉴权", rows: runtime.authRows),
            (title: "分页 / 缓存 / 重试", rows: runtime.networkRows),
            (title: "Schema 校验 / 错误码", rows: runtime.validationRows),
            (
                title: "接口联调环境",
                rows: [
                    OpenApiFieldRow(key: "payment.status", value: "/openapi/v1/payments/red-packets/status-by-message", subtitle: "支付真实状态。"),
                    OpenApiFieldRow(key: "finder.publish", value: "/openapi/v1/finder/posts", subtitle: "视频号发布。"),
                    OpenApiFieldRow(key: "customer.profile", value: "/openapi/v1/customers/profile", subtitle: "客户档案。"),
                    OpenApiFieldRow(key: "moment.materials", value: "/openapi/v1/moments/materials", subtitle: "朋友圈素材库。")
                ]
            )
        ]
        tableView.reloadData()
    }
}

final class OpenApiAccountDeviceViewController: UIViewController, UITableViewDataSource, UITableViewDelegate {
    private enum Action: CaseIterable {
        case refreshOverview
        case syncContacts
        case syncChatrooms
        case startLogin
        case activeLogin
        case restartWeChat

        var title: String {
            switch self {
            case .refreshOverview: return "刷新帐号与设备状态"
            case .syncContacts: return "同步好友资料"
            case .syncChatrooms: return "同步群聊资料"
            case .startLogin: return "登录新的微信帐号"
            case .activeLogin: return "查看正在登录的帐号"
            case .restartWeChat: return "重启设备上的微信"
            }
        }

        var subtitle: String {
            switch self {
            case .refreshOverview: return "更新在线状态、设备和能力"
            case .syncContacts: return "从当前微信同步好友和头像"
            case .syncChatrooms: return "从当前微信同步群聊和成员"
            case .startLogin: return "创建扫码登录会话，有效期 5 分钟"
            case .activeLogin: return "查看登录二维码是否仍然有效"
            case .restartWeChat: return "微信异常时使用，聊天记录不会删除"
            }
        }

        var symbol: String {
            switch self {
            case .refreshOverview: return "arrow.clockwise"
            case .syncContacts: return "person.2"
            case .syncChatrooms: return "person.3"
            case .startLogin: return "qrcode.viewfinder"
            case .activeLogin: return "hourglass"
            case .restartWeChat: return "power"
            }
        }
    }

    private let accountName: String
    private let weChatId: String
    private let deviceUuid: String
    private let tableView = UITableView(frame: .zero, style: .insetGrouped)
    private var overviewRows: [OpenApiFieldRow] = []
    private var isRunning = false

    init(accountName: String, weChatId: String, deviceUuid: String) {
        self.accountName = accountName
        self.weChatId = weChatId
        self.deviceUuid = deviceUuid
        super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    override func viewDidLoad() {
        super.viewDidLoad()
        title = "帐号设备"
        view.backgroundColor = .systemGroupedBackground
        tableView.backgroundColor = .clear
        tableView.dataSource = self
        tableView.delegate = self
        tableView.register(UITableViewCell.self, forCellReuseIdentifier: "AccountDeviceAction")
        tableView.register(OpenApiFieldCell.self, forCellReuseIdentifier: OpenApiFieldCell.reuseIdentifier)
        tableView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(tableView)
        NSLayoutConstraint.activate([
            tableView.topAnchor.constraint(equalTo: view.topAnchor),
            tableView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            tableView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            tableView.bottomAnchor.constraint(equalTo: view.bottomAnchor)
        ])
        overviewRows = [
            OpenApiFieldRow(key: "当前帐号", value: accountName, subtitle: weChatId.isEmpty ? "等待同步微信帐号" : weChatId),
            OpenApiFieldRow(key: "当前设备", value: deviceUuid.isEmpty ? "等待同步设备" : "已连接", subtitle: deviceUuid)
        ]
        loadOverview()
    }

    func numberOfSections(in tableView: UITableView) -> Int { 2 }

    func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        section == 0 ? overviewRows.count : Action.allCases.count
    }

    func tableView(_ tableView: UITableView, titleForHeaderInSection section: Int) -> String? {
        section == 0 ? "当前状态" : "帐号操作"
    }

    func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        if indexPath.section == 0 {
            let cell = tableView.dequeueReusableCell(withIdentifier: OpenApiFieldCell.reuseIdentifier, for: indexPath) as? OpenApiFieldCell
                ?? OpenApiFieldCell(style: .default, reuseIdentifier: OpenApiFieldCell.reuseIdentifier)
            cell.configure(overviewRows[indexPath.row])
            cell.selectionStyle = .none
            return cell
        }
        let action = Action.allCases[indexPath.row]
        let cell = tableView.dequeueReusableCell(withIdentifier: "AccountDeviceAction", for: indexPath)
        var content = UIListContentConfiguration.subtitleCell()
        content.text = action.title
        content.secondaryText = action.subtitle
        content.image = UIImage(systemName: action.symbol)
        content.imageProperties.tintColor = action == .restartWeChat ? .systemRed : .systemGreen
        content.textProperties.font = .systemFont(ofSize: 16, weight: .semibold)
        content.secondaryTextProperties.color = .secondaryLabel
        cell.contentConfiguration = content
        cell.accessoryType = .disclosureIndicator
        cell.selectionStyle = .default
        cell.isUserInteractionEnabled = !isRunning
        cell.alpha = isRunning ? 0.55 : 1
        return cell
    }

    func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        tableView.deselectRow(at: indexPath, animated: true)
        guard indexPath.section == 1, !isRunning else { return }
        run(Action.allCases[indexPath.row])
    }

    private func loadOverview() {
        setRunning(true)
        Task { @MainActor in
            do {
                let identity = try await OpenApiHTTPClient.request("GET", path: "/openapi/v1/me")
                let capabilities = try await OpenApiHTTPClient.request(
                    "GET",
                    path: "/openapi/v1/capabilities",
                    query: [
                        URLQueryItem(name: "deviceUuid", value: deviceUuid),
                        URLQueryItem(name: "weChatId", value: weChatId)
                    ]
                )
                overviewRows = [
                    OpenApiFieldRow(key: "当前帐号", value: accountName, subtitle: weChatId),
                    OpenApiFieldRow(key: "设备连接", value: deviceUuid.isEmpty ? "未匹配" : "已匹配", subtitle: deviceUuid),
                    OpenApiFieldRow(key: "身份鉴权", value: identity.statusCode == 200 ? "正常" : "异常", subtitle: "OpenAPI 身份已验证"),
                    OpenApiFieldRow(key: "功能能力", value: capabilities.statusCode == 200 ? "已加载" : "不可用", subtitle: "点击下方操作可重新检查")
                ]
            } catch {
                overviewRows.append(OpenApiFieldRow(key: "连接状态", value: "加载失败", subtitle: error.localizedDescription))
            }
            setRunning(false)
        }
    }

    private func run(_ action: Action) {
        if action == .restartWeChat {
            let alert = UIAlertController(title: "重启微信", message: "确认重启当前设备上的微信？正在进行的微信操作会中断。", preferredStyle: .alert)
            alert.addAction(UIAlertAction(title: "取消", style: .cancel))
            alert.addAction(UIAlertAction(title: "重启", style: .destructive) { [weak self] _ in self?.perform(action) })
            present(alert, animated: true)
            return
        }
        perform(action)
    }

    private func perform(_ action: Action) {
        if action == .refreshOverview {
            loadOverview()
            return
        }
        setRunning(true)
        Task { @MainActor in
            do {
                let result: OpenApiHTTPResult
                switch action {
                case .syncContacts:
                    result = try await OpenApiHTTPClient.request("POST", path: "/openapi/v1/contacts/sync", body: commonBody)
                case .syncChatrooms:
                    result = try await OpenApiHTTPClient.request("POST", path: "/openapi/v1/chatrooms/sync", body: commonBody.merging(["flag": 1]) { _, new in new })
                case .startLogin:
                    guard !deviceUuid.isEmpty else { throw OpenApiMediaSendError.missingRequiredFields("登录微信", "当前帐号没有匹配设备") }
                    result = try await OpenApiHTTPClient.request("POST", path: "/openapi/v1/wechat-login/start", body: ["deviceUuid": deviceUuid, "loginMode": "qrcode", "expireSeconds": 300, "remark": "只发 iOS 登录"])
                case .activeLogin:
                    result = try await OpenApiHTTPClient.request("GET", path: "/openapi/v1/wechat-login/active")
                case .restartWeChat:
                    result = try await OpenApiHTTPClient.request("POST", path: "/openapi/v1/devices/restart-wechat", body: commonBody.merging(["reason": "用户从只发帐号设备页重启"]) { _, new in new })
                case .refreshOverview:
                    return
                }
                let taskActions: Set<Action> = [.syncContacts, .syncChatrooms, .restartWeChat]
                if taskActions.contains(action) {
                    _ = try await OpenApiHTTPClient.validateTaskResult(result, action: action.title)
                }
                showResult(title: action.title, result: result)
            } catch {
                showAlert(title: "操作失败", message: error.localizedDescription)
            }
            setRunning(false)
        }
    }

    private var commonBody: [String: Any] {
        var body: [String: Any] = ["weChatId": weChatId]
        if !deviceUuid.isEmpty { body["deviceUuid"] = deviceUuid }
        return body
    }

    private func setRunning(_ running: Bool) {
        isRunning = running
        tableView.reloadData()
    }

    private func showResult(title: String, result: OpenApiHTTPResult) {
        let rows = OpenApiHTTPClient.rows(from: result, maxItems: 12)
        let text = rows.map { "\($0.key)：\($0.value)" }.joined(separator: "\n")
        showAlert(title: title, message: text.isEmpty ? "操作已完成" : text)
    }

    private func showAlert(title: String, message: String) {
        let alert = UIAlertController(title: title, message: message, preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "知道了", style: .default))
        present(alert, animated: true)
    }
}
