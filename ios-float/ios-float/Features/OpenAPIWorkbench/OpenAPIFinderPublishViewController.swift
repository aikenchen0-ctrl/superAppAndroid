import UIKit

final class OpenApiFinderPublishViewController: UIViewController, UITableViewDataSource {
    private let accountName: String
    private let weChatId: String
    private let deviceUuid: String
    private let scrollView = UIScrollView()
    private let contentStack = UIStackView()
    private let textView = UITextView()
    private let mediaURLField = UITextField()
    private let coverURLField = UITextField()
    private let previewCard = UIView()
    private let resultBanner = UILabel()
    private let uploadProgressView = UIProgressView(progressViewStyle: .default)
    private let publishStatusLabel = UILabel()
    private let tableView = UITableView(frame: .zero, style: .insetGrouped)
    private var responseRows: [OpenApiFieldRow] = []
    private var publishTaskID = ""
    private var isPublishing = false

    init(accountName: String, weChatId: String, deviceUuid: String = "") {
        self.accountName = accountName
        self.weChatId = weChatId
        self.deviceUuid = deviceUuid
        super.init(nibName: nil, bundle: nil)
        title = "视频号发布"
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = UIColor(red: 0.95, green: 0.96, blue: 0.97, alpha: 1)
        navigationItem.rightBarButtonItems = [
            UIBarButtonItem(title: "发布", style: .done, target: self, action: #selector(publish)),
            UIBarButtonItem(title: "环境", style: .plain, target: self, action: #selector(openOpenApiEnvironment))
        ]

        scrollView.translatesAutoresizingMaskIntoConstraints = false
        contentStack.axis = .vertical
        contentStack.spacing = 12
        contentStack.translatesAutoresizingMaskIntoConstraints = false

        let titleCard = makePublishHeaderCard()
        let editorCard = makeEditorCard()
        let settingsCard = makePublishSettingsCard()
        let taskCard = makePublishTaskCard()
        configurePreviewCard()
        configureResultBanner()

        textView.text = ""
        textView.font = .systemFont(ofSize: 16)
        textView.backgroundColor = .clear
        textView.layer.cornerRadius = 12
        textView.layer.cornerCurve = .continuous
        textView.translatesAutoresizingMaskIntoConstraints = false

        view.addSubview(scrollView)
        scrollView.addSubview(contentStack)
        contentStack.addArrangedSubview(titleCard)
        contentStack.addArrangedSubview(previewCard)
        contentStack.addArrangedSubview(editorCard)
        contentStack.addArrangedSubview(settingsCard)
        contentStack.addArrangedSubview(taskCard)
        contentStack.addArrangedSubview(resultBanner)
        NSLayoutConstraint.activate([
            scrollView.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
            scrollView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            scrollView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            scrollView.bottomAnchor.constraint(equalTo: view.bottomAnchor),
            contentStack.topAnchor.constraint(equalTo: scrollView.contentLayoutGuide.topAnchor, constant: 14),
            contentStack.leadingAnchor.constraint(equalTo: scrollView.frameLayoutGuide.leadingAnchor, constant: 16),
            contentStack.trailingAnchor.constraint(equalTo: scrollView.frameLayoutGuide.trailingAnchor, constant: -16),
            contentStack.bottomAnchor.constraint(equalTo: scrollView.contentLayoutGuide.bottomAnchor, constant: -18),
            textView.heightAnchor.constraint(equalToConstant: 128)
        ])
        rebuildDraftRows(message: "尚未调用接口；填写真实媒体 URL 后点击右上角发布")
    }

    @objc private func openOpenApiEnvironment() {
        navigationController?.pushViewController(OpenApiEnvironmentViewController(), animated: true)
    }

    @objc private func publish() {
        guard !isPublishing else { return }
        let content = textView.text?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        let mediaURL = mediaURLField.text?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        let coverURL = coverURLField.text?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        guard !content.isEmpty, !mediaURL.isEmpty else {
            showAlert(title: "无法发布", message: "请填写正文和真实视频媒体 URL。不能使用演示地址或本地占位内容。")
            return
        }

        let alert = UIAlertController(
            title: "确认发布视频号",
            message: "将使用当前微信帐号调用真实视频号发布接口。发布成功后可能产生公开内容，是否继续？",
            preferredStyle: .alert
        )
        alert.addAction(UIAlertAction(title: "取消", style: .cancel))
        alert.addAction(UIAlertAction(title: "发布", style: .destructive) { [weak self] _ in
            self?.performPublish(content: content, mediaURL: mediaURL, coverURL: coverURL)
        })
        present(alert, animated: true)
    }

    private func performPublish(content: String, mediaURL: String, coverURL: String) {
        isPublishing = true
        publishTaskID = ""
        navigationItem.rightBarButtonItems?.first?.isEnabled = false
        uploadProgressView.progress = 0
        publishStatusLabel.text = "正在校验真实媒体和发布参数"
        resultBanner.text = "正在调用 /openapi/v1/finder/posts/template"
        resultBanner.backgroundColor = UIColor(red: 0.50, green: 0.36, blue: 0.86, alpha: 0.10)
        resultBanner.textColor = UIColor(red: 0.36, green: 0.24, blue: 0.68, alpha: 1)

        let account = OpenApiFinderAccount(deviceUUID: deviceUuid, weChatID: weChatId)
        let options = OpenApiFinderPostOptions(
            content: content,
            mediaURLs: [mediaURL],
            mediaType: "video",
            coverURL: coverURL,
            poiName: "",
            poiAddress: ""
        )
        responseRows = requestRows(
            options: options,
            endpoint: "/openapi/v1/finder/posts/template",
            status: "正在调用真实模板校验接口"
        )
        tableView.reloadData()

        Task { @MainActor in
            defer {
                self.isPublishing = false
                self.navigationItem.rightBarButtonItems?.first?.isEnabled = true
            }
            do {
                let templateResult = try await OpenApiFinderService().validatePost(account: account, options: options)
                self.responseRows = self.requestRows(
                    options: options,
                    endpoint: templateResult.endpoint,
                    result: templateResult,
                    status: "模板校验通过，正在提交真实发布任务"
                )
                self.publishStatusLabel.text = "模板校验通过 · 提交发布任务"
                self.resultBanner.text = "模板校验通过 · 正在调用 /openapi/v1/finder/posts"
                self.tableView.reloadData()

                let publishResult = try await OpenApiFinderService().publish(account: account, options: options)
                self.publishTaskID = OpenApiHTTPClient.taskID(from: publishResult.jsonObject) ?? ""
                self.uploadProgressView.setProgress(1, animated: true)
                self.publishStatusLabel.text = "服务端任务已完成"
                self.resultBanner.text = "真实发布任务已完成"
                self.resultBanner.backgroundColor = UIColor(red: 0.10, green: 0.58, blue: 0.36, alpha: 0.12)
                self.resultBanner.textColor = UIColor(red: 0.06, green: 0.40, blue: 0.24, alpha: 1)
                self.responseRows = self.requestRows(
                    options: options,
                    endpoint: publishResult.endpoint,
                    result: publishResult,
                    status: "服务端任务已确认完成"
                )
                self.tableView.reloadData()
            } catch {
                self.publishStatusLabel.text = "发布失败，未确认微信端结果"
                self.resultBanner.text = "真实接口调用失败 · 未生成本地成功状态"
                self.resultBanner.backgroundColor = UIColor.systemRed.withAlphaComponent(0.10)
                self.resultBanner.textColor = .systemRed
                self.responseRows = OpenApiHTTPClient.errorRows(endpoint: "/openapi/v1/finder/posts", error: error)
                    + self.requestRows(options: options, endpoint: "/openapi/v1/finder/posts", status: "失败")
                self.tableView.reloadData()
            }
        }
    }

    private func makePublishHeaderCard() -> UIView {
        let card = makeCard()
        let icon = UIImageView(image: UIImage(systemName: "video.badge.plus"))
        icon.tintColor = .white
        icon.backgroundColor = UIColor(red: 0.50, green: 0.36, blue: 0.86, alpha: 1)
        icon.layer.cornerRadius = 18
        icon.layer.cornerCurve = .continuous
        icon.contentMode = .scaleAspectFit
        icon.translatesAutoresizingMaskIntoConstraints = false

        let titleLabel = UILabel()
        titleLabel.text = "视频号发布"
        titleLabel.font = .systemFont(ofSize: 22, weight: .bold)
        titleLabel.textColor = UIColor(red: 0.10, green: 0.12, blue: 0.14, alpha: 1)

        let subtitleLabel = UILabel()
        subtitleLabel.text = "使用「\(accountName)」发布"
        subtitleLabel.font = .systemFont(ofSize: 13, weight: .medium)
        subtitleLabel.textColor = .secondaryLabel

        let accountLabel = UILabel()
        accountLabel.text = weChatId
        accountLabel.font = .systemFont(ofSize: 12, weight: .semibold)
        accountLabel.textColor = UIColor(red: 0.36, green: 0.24, blue: 0.68, alpha: 1)
        accountLabel.backgroundColor = UIColor(red: 0.50, green: 0.36, blue: 0.86, alpha: 0.10)
        accountLabel.layer.cornerRadius = 10
        accountLabel.layer.cornerCurve = .continuous
        accountLabel.clipsToBounds = true
        accountLabel.textAlignment = .center
        accountLabel.translatesAutoresizingMaskIntoConstraints = false

        let stack = UIStackView(arrangedSubviews: [titleLabel, subtitleLabel])
        stack.axis = .vertical
        stack.spacing = 4
        stack.translatesAutoresizingMaskIntoConstraints = false
        card.addSubview(icon)
        card.addSubview(stack)
        card.addSubview(accountLabel)
        NSLayoutConstraint.activate([
            icon.leadingAnchor.constraint(equalTo: card.leadingAnchor, constant: 16),
            icon.centerYAnchor.constraint(equalTo: card.centerYAnchor),
            icon.widthAnchor.constraint(equalToConstant: 36),
            icon.heightAnchor.constraint(equalToConstant: 36),
            stack.topAnchor.constraint(equalTo: card.topAnchor, constant: 16),
            stack.leadingAnchor.constraint(equalTo: icon.trailingAnchor, constant: 12),
            stack.trailingAnchor.constraint(equalTo: accountLabel.leadingAnchor, constant: -12),
            stack.bottomAnchor.constraint(equalTo: card.bottomAnchor, constant: -16),
            accountLabel.trailingAnchor.constraint(equalTo: card.trailingAnchor, constant: -16),
            accountLabel.centerYAnchor.constraint(equalTo: card.centerYAnchor),
            accountLabel.widthAnchor.constraint(greaterThanOrEqualToConstant: 86),
            accountLabel.heightAnchor.constraint(equalToConstant: 28)
        ])
        return card
    }

    private func makeEditorCard() -> UIView {
        let card = makeCard()
        let titleLabel = sectionTitle("正文内容")
        let hintLabel = UILabel()
        hintLabel.text = "可编辑"
        hintLabel.font = .systemFont(ofSize: 12, weight: .semibold)
        hintLabel.textColor = .secondaryLabel
        hintLabel.translatesAutoresizingMaskIntoConstraints = false
        titleLabel.translatesAutoresizingMaskIntoConstraints = false
        card.addSubview(titleLabel)
        card.addSubview(hintLabel)
        card.addSubview(textView)
        NSLayoutConstraint.activate([
            titleLabel.topAnchor.constraint(equalTo: card.topAnchor, constant: 14),
            titleLabel.leadingAnchor.constraint(equalTo: card.leadingAnchor, constant: 16),
            hintLabel.centerYAnchor.constraint(equalTo: titleLabel.centerYAnchor),
            hintLabel.trailingAnchor.constraint(equalTo: card.trailingAnchor, constant: -16),
            textView.topAnchor.constraint(equalTo: titleLabel.bottomAnchor, constant: 10),
            textView.leadingAnchor.constraint(equalTo: card.leadingAnchor, constant: 12),
            textView.trailingAnchor.constraint(equalTo: card.trailingAnchor, constant: -12),
            textView.bottomAnchor.constraint(equalTo: card.bottomAnchor, constant: -12)
        ])
        return card
    }

    private func configurePreviewCard() {
        previewCard.backgroundColor = .white
        previewCard.layer.cornerRadius = 16
        previewCard.layer.cornerCurve = .continuous

        let coverView = UIView()
        coverView.backgroundColor = UIColor(red: 0.09, green: 0.10, blue: 0.13, alpha: 1)
        coverView.layer.cornerRadius = 14
        coverView.layer.cornerCurve = .continuous
        coverView.translatesAutoresizingMaskIntoConstraints = false

        let playIcon = UIImageView(image: UIImage(systemName: "play.fill"))
        playIcon.tintColor = .white
        playIcon.contentMode = .scaleAspectFit
        playIcon.translatesAutoresizingMaskIntoConstraints = false

        let mediaLabel = UILabel()
        mediaLabel.text = "未选择真实视频"
        mediaLabel.font = .systemFont(ofSize: 13, weight: .semibold)
        mediaLabel.textColor = .white
        mediaLabel.translatesAutoresizingMaskIntoConstraints = false

        let titleLabel = sectionTitle("视频预览")
        let chipStack = UIStackView(arrangedSubviews: [
            chip("短视频"),
            chip("封面可选"),
            chip("位置未设置")
        ])
        chipStack.spacing = 8
        chipStack.translatesAutoresizingMaskIntoConstraints = false

        let poiLabel = UILabel()
        poiLabel.text = "发布位置：未设置（接口不会发送位置字段）"
        poiLabel.font = .systemFont(ofSize: 13)
        poiLabel.textColor = .secondaryLabel
        poiLabel.numberOfLines = 0
        poiLabel.translatesAutoresizingMaskIntoConstraints = false

        [titleLabel, coverView, chipStack, poiLabel].forEach {
            $0.translatesAutoresizingMaskIntoConstraints = false
            previewCard.addSubview($0)
        }
        coverView.addSubview(playIcon)
        coverView.addSubview(mediaLabel)

        NSLayoutConstraint.activate([
            titleLabel.topAnchor.constraint(equalTo: previewCard.topAnchor, constant: 14),
            titleLabel.leadingAnchor.constraint(equalTo: previewCard.leadingAnchor, constant: 16),
            coverView.topAnchor.constraint(equalTo: titleLabel.bottomAnchor, constant: 12),
            coverView.leadingAnchor.constraint(equalTo: previewCard.leadingAnchor, constant: 16),
            coverView.trailingAnchor.constraint(equalTo: previewCard.trailingAnchor, constant: -16),
            coverView.heightAnchor.constraint(equalToConstant: 190),
            playIcon.centerXAnchor.constraint(equalTo: coverView.centerXAnchor),
            playIcon.centerYAnchor.constraint(equalTo: coverView.centerYAnchor),
            playIcon.widthAnchor.constraint(equalToConstant: 42),
            playIcon.heightAnchor.constraint(equalToConstant: 42),
            mediaLabel.leadingAnchor.constraint(equalTo: coverView.leadingAnchor, constant: 14),
            mediaLabel.bottomAnchor.constraint(equalTo: coverView.bottomAnchor, constant: -12),
            chipStack.topAnchor.constraint(equalTo: coverView.bottomAnchor, constant: 12),
            chipStack.leadingAnchor.constraint(equalTo: previewCard.leadingAnchor, constant: 16),
            chipStack.trailingAnchor.constraint(lessThanOrEqualTo: previewCard.trailingAnchor, constant: -16),
            poiLabel.topAnchor.constraint(equalTo: chipStack.bottomAnchor, constant: 10),
            poiLabel.leadingAnchor.constraint(equalTo: previewCard.leadingAnchor, constant: 16),
            poiLabel.trailingAnchor.constraint(equalTo: previewCard.trailingAnchor, constant: -16),
            poiLabel.bottomAnchor.constraint(equalTo: previewCard.bottomAnchor, constant: -16)
        ])
    }

    private func makePublishSettingsCard() -> UIView {
        let card = makeCard()
        let titleLabel = sectionTitle("发布设置")
        titleLabel.translatesAutoresizingMaskIntoConstraints = false

        let stack = UIStackView(arrangedSubviews: [
            settingRow(symbol: "person.crop.circle", title: "发布账号", value: accountName),
            settingRow(symbol: "number", title: "话题", value: "未设置"),
            settingRow(symbol: "location.fill", title: "位置", value: "未设置"),
            settingRow(symbol: "eye.fill", title: "可见范围", value: "后端默认"),
            settingRow(symbol: "checkmark.seal", title: "审核方式", value: "后端决定"),
            makeURLField(mediaURLField, placeholder: "真实视频 URL（https://...）"),
            makeURLField(coverURLField, placeholder: "真实封面 URL（可选，https://...）")
        ])
        stack.axis = .vertical
        stack.spacing = 10
        stack.translatesAutoresizingMaskIntoConstraints = false

        card.addSubview(titleLabel)
        card.addSubview(stack)
        NSLayoutConstraint.activate([
            titleLabel.topAnchor.constraint(equalTo: card.topAnchor, constant: 14),
            titleLabel.leadingAnchor.constraint(equalTo: card.leadingAnchor, constant: 16),
            titleLabel.trailingAnchor.constraint(equalTo: card.trailingAnchor, constant: -16),
            stack.topAnchor.constraint(equalTo: titleLabel.bottomAnchor, constant: 12),
            stack.leadingAnchor.constraint(equalTo: card.leadingAnchor, constant: 16),
            stack.trailingAnchor.constraint(equalTo: card.trailingAnchor, constant: -16),
            stack.bottomAnchor.constraint(equalTo: card.bottomAnchor, constant: -16)
        ])
        return card
    }

    private func makeURLField(_ field: UITextField, placeholder: String) -> UIView {
        field.placeholder = placeholder
        field.font = .systemFont(ofSize: 14)
        field.textColor = .label
        field.borderStyle = .roundedRect
        field.backgroundColor = UIColor.secondarySystemBackground
        field.keyboardType = .URL
        field.autocapitalizationType = .none
        field.autocorrectionType = .no
        field.clearButtonMode = .whileEditing
        field.returnKeyType = .done
        field.heightAnchor.constraint(equalToConstant: 40).isActive = true
        let container = UIView()
        container.addSubview(field)
        field.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            field.leadingAnchor.constraint(equalTo: container.leadingAnchor),
            field.trailingAnchor.constraint(equalTo: container.trailingAnchor),
            field.topAnchor.constraint(equalTo: container.topAnchor),
            field.bottomAnchor.constraint(equalTo: container.bottomAnchor)
        ])
        return container
    }

    private func makePublishTaskCard() -> UIView {
        let card = makeCard()
        let titleLabel = sectionTitle("上传与后台任务")
        titleLabel.translatesAutoresizingMaskIntoConstraints = false

        publishStatusLabel.text = "草稿状态 · 未上传"
        publishStatusLabel.font = .systemFont(ofSize: 14, weight: .semibold)
        publishStatusLabel.textColor = UIColor(red: 0.36, green: 0.24, blue: 0.68, alpha: 1)
        publishStatusLabel.translatesAutoresizingMaskIntoConstraints = false

        uploadProgressView.progress = 0
        uploadProgressView.progressTintColor = UIColor(red: 0.50, green: 0.36, blue: 0.86, alpha: 1)
        uploadProgressView.trackTintColor = UIColor(red: 0.88, green: 0.86, blue: 0.94, alpha: 1)
        uploadProgressView.translatesAutoresizingMaskIntoConstraints = false

        let taskStack = UIStackView(arrangedSubviews: [
            settingRow(symbol: "arrow.up.circle", title: "上传队列", value: "等待发布"),
            settingRow(symbol: "clock.arrow.circlepath", title: "后台任务", value: "TaskResult"),
            settingRow(symbol: "shield.lefthalf.filled", title: "发布审核", value: "待提交")
        ])
        taskStack.axis = .vertical
        taskStack.spacing = 8
        taskStack.translatesAutoresizingMaskIntoConstraints = false

        [titleLabel, publishStatusLabel, uploadProgressView, taskStack].forEach(card.addSubview)
        NSLayoutConstraint.activate([
            titleLabel.topAnchor.constraint(equalTo: card.topAnchor, constant: 14),
            titleLabel.leadingAnchor.constraint(equalTo: card.leadingAnchor, constant: 16),
            titleLabel.trailingAnchor.constraint(equalTo: card.trailingAnchor, constant: -16),

            publishStatusLabel.topAnchor.constraint(equalTo: titleLabel.bottomAnchor, constant: 12),
            publishStatusLabel.leadingAnchor.constraint(equalTo: titleLabel.leadingAnchor),
            publishStatusLabel.trailingAnchor.constraint(equalTo: titleLabel.trailingAnchor),

            uploadProgressView.topAnchor.constraint(equalTo: publishStatusLabel.bottomAnchor, constant: 10),
            uploadProgressView.leadingAnchor.constraint(equalTo: titleLabel.leadingAnchor),
            uploadProgressView.trailingAnchor.constraint(equalTo: titleLabel.trailingAnchor),
            uploadProgressView.heightAnchor.constraint(equalToConstant: 6),

            taskStack.topAnchor.constraint(equalTo: uploadProgressView.bottomAnchor, constant: 14),
            taskStack.leadingAnchor.constraint(equalTo: titleLabel.leadingAnchor),
            taskStack.trailingAnchor.constraint(equalTo: titleLabel.trailingAnchor),
            taskStack.bottomAnchor.constraint(equalTo: card.bottomAnchor, constant: -16)
        ])
        return card
    }

    private func settingRow(symbol: String, title: String, value: String) -> UIView {
        let row = UIView()
        row.translatesAutoresizingMaskIntoConstraints = false

        let iconView = UIImageView(image: UIImage(systemName: symbol))
        iconView.tintColor = UIColor(red: 0.50, green: 0.36, blue: 0.86, alpha: 1)
        iconView.contentMode = .scaleAspectFit
        iconView.translatesAutoresizingMaskIntoConstraints = false

        let titleLabel = UILabel()
        titleLabel.text = title
        titleLabel.font = .systemFont(ofSize: 13, weight: .medium)
        titleLabel.textColor = .secondaryLabel

        let valueLabel = UILabel()
        valueLabel.text = value
        valueLabel.font = .systemFont(ofSize: 14, weight: .semibold)
        valueLabel.textColor = UIColor(red: 0.13, green: 0.15, blue: 0.18, alpha: 1)
        valueLabel.textAlignment = .right
        valueLabel.lineBreakMode = .byTruncatingTail

        [titleLabel, valueLabel].forEach { $0.translatesAutoresizingMaskIntoConstraints = false }
        row.addSubview(iconView)
        row.addSubview(titleLabel)
        row.addSubview(valueLabel)
        NSLayoutConstraint.activate([
            row.heightAnchor.constraint(equalToConstant: 32),
            iconView.leadingAnchor.constraint(equalTo: row.leadingAnchor),
            iconView.centerYAnchor.constraint(equalTo: row.centerYAnchor),
            iconView.widthAnchor.constraint(equalToConstant: 20),
            iconView.heightAnchor.constraint(equalToConstant: 20),
            titleLabel.leadingAnchor.constraint(equalTo: iconView.trailingAnchor, constant: 10),
            titleLabel.centerYAnchor.constraint(equalTo: row.centerYAnchor),
            valueLabel.leadingAnchor.constraint(greaterThanOrEqualTo: titleLabel.trailingAnchor, constant: 12),
            valueLabel.trailingAnchor.constraint(equalTo: row.trailingAnchor),
            valueLabel.centerYAnchor.constraint(equalTo: row.centerYAnchor),
            valueLabel.widthAnchor.constraint(lessThanOrEqualTo: row.widthAnchor, multiplier: 0.58)
        ])
        return row
    }

    private func configureResultBanner() {
        resultBanner.text = "尚未调用真实接口 · 填写媒体 URL 后发布"
        resultBanner.font = .systemFont(ofSize: 14, weight: .semibold)
        resultBanner.textAlignment = .center
        resultBanner.backgroundColor = UIColor(red: 0.50, green: 0.36, blue: 0.86, alpha: 0.10)
        resultBanner.textColor = UIColor(red: 0.36, green: 0.24, blue: 0.68, alpha: 1)
        resultBanner.layer.cornerRadius = 12
        resultBanner.layer.cornerCurve = .continuous
        resultBanner.clipsToBounds = true
        resultBanner.heightAnchor.constraint(equalToConstant: 44).isActive = true
    }

    private func makeCard() -> UIView {
        let card = UIView()
        card.backgroundColor = .white
        card.layer.cornerRadius = 16
        card.layer.cornerCurve = .continuous
        return card
    }

    private func sectionTitle(_ text: String) -> UILabel {
        let label = UILabel()
        label.text = text
        label.font = .systemFont(ofSize: 16, weight: .bold)
        label.textColor = UIColor(red: 0.10, green: 0.12, blue: 0.14, alpha: 1)
        return label
    }

    private func chip(_ text: String) -> UILabel {
        let label = UILabel()
        label.text = text
        label.font = .systemFont(ofSize: 12, weight: .semibold)
        label.textColor = UIColor(red: 0.26, green: 0.28, blue: 0.32, alpha: 1)
        label.backgroundColor = UIColor(red: 0.93, green: 0.94, blue: 0.96, alpha: 1)
        label.layer.cornerRadius = 10
        label.layer.cornerCurve = .continuous
        label.clipsToBounds = true
        label.textAlignment = .center
        label.widthAnchor.constraint(greaterThanOrEqualToConstant: 78).isActive = true
        label.heightAnchor.constraint(equalToConstant: 28).isActive = true
        return label
    }

    private func rebuildDraftRows(message: String) {
        let options = OpenApiFinderPostOptions(
            content: textView.text ?? "",
            mediaURLs: [mediaURLField.text ?? ""],
            coverURL: coverURLField.text ?? ""
        )
        responseRows = requestRows(options: options, endpoint: "/openapi/v1/finder/posts", status: message)
        tableView.reloadData()
    }

    private func requestRows(
        options: OpenApiFinderPostOptions,
        endpoint: String,
        result: OpenApiHTTPResult? = nil,
        status: String
    ) -> [OpenApiFieldRow] {
        var rows = [
            OpenApiFieldRow(key: "request.path", value: endpoint, subtitle: "真实视频号接口请求路径。"),
            OpenApiFieldRow(key: "deviceUuid", value: deviceUuid.isEmpty ? "未提供" : deviceUuid, subtitle: "当前账号绑定的真实设备。"),
            OpenApiFieldRow(key: "weChatId", value: weChatId, subtitle: "当前账号：\(accountName)。"),
            OpenApiFieldRow(key: "content", value: options.content, subtitle: "发布正文。"),
            OpenApiFieldRow(key: "medias", value: options.mediaURLs.joined(separator: ", "), subtitle: "真实媒体 URL。"),
            OpenApiFieldRow(key: "mediaType", value: options.mediaType, subtitle: "视频号媒体类型。"),
            OpenApiFieldRow(key: "cover", value: options.coverURL.isEmpty ? "未提供" : options.coverURL, subtitle: "可选真实封面 URL。"),
            OpenApiFieldRow(key: "status", value: status, subtitle: "不会使用本地模拟成功状态。")
        ]
        if !publishTaskID.isEmpty {
            rows.append(OpenApiFieldRow(key: "taskId", value: publishTaskID, subtitle: "服务端 TaskResult.taskId。"))
        }
        if let result {
            rows.append(contentsOf: OpenApiHTTPClient.rows(from: result, maxItems: 28))
        }
        return rows
    }

    private func showAlert(title: String, message: String) {
        let alert = UIAlertController(title: title, message: message, preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "知道了", style: .default))
        present(alert, animated: true)
    }

    func numberOfSections(in tableView: UITableView) -> Int { 1 }
    func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int { responseRows.count }
    func tableView(_ tableView: UITableView, titleForHeaderInSection section: Int) -> String? { "OpenApiFinderPostRequest / TaskResult" }
    func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = tableView.dequeueReusableCell(withIdentifier: OpenApiFieldCell.reuseIdentifier, for: indexPath) as? OpenApiFieldCell
            ?? OpenApiFieldCell(style: .default, reuseIdentifier: OpenApiFieldCell.reuseIdentifier)
        cell.configure(responseRows[indexPath.row])
        return cell
    }
}
