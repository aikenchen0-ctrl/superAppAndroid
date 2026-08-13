import UIKit

private enum OpenApiMomentMaterialDisplay {
    struct Kind {
        let title: String
        let shortTitle: String
        let symbol: String
        let color: UIColor
        let backgroundColor: UIColor
    }

    static func kind(for material: OpenApiMomentMaterial) -> Kind {
        let values = attachmentValues(for: material)
        let joined = ([material.category, material.scene, material.name] + values)
            .joined(separator: " ")
            .lowercased()
        if material.attachmentType == 1 || values.contains(where: isImageValue) {
            return Kind(
                title: "图片素材",
                shortTitle: "图片",
                symbol: "photo.fill",
                color: UIColor(red: 0.10, green: 0.48, blue: 0.52, alpha: 1),
                backgroundColor: UIColor(red: 0.92, green: 0.97, blue: 0.96, alpha: 1)
            )
        }
        if material.attachmentType == 2 || values.contains(where: isVideoValue) {
            return Kind(
                title: "视频素材",
                shortTitle: "视频",
                symbol: "play.rectangle.fill",
                color: UIColor(red: 0.70, green: 0.24, blue: 0.22, alpha: 1),
                backgroundColor: UIColor(red: 0.98, green: 0.94, blue: 0.93, alpha: 1)
            )
        }
        if joined.contains("小程序") || joined.contains("weapp") || joined.contains("miniapp") {
            return Kind(
                title: "小程序素材",
                shortTitle: "小程序",
                symbol: "appclip.fill",
                color: UIColor(red: 0.30, green: 0.36, blue: 0.72, alpha: 1),
                backgroundColor: UIColor(red: 0.94, green: 0.95, blue: 0.99, alpha: 1)
            )
        }
        if joined.contains("公众号") || joined.contains("article") || joined.contains("mp.weixin") {
            return Kind(
                title: "公众号素材",
                shortTitle: "公众号",
                symbol: "newspaper.fill",
                color: UIColor(red: 0.18, green: 0.43, blue: 0.69, alpha: 1),
                backgroundColor: UIColor(red: 0.93, green: 0.96, blue: 0.99, alpha: 1)
            )
        }
        if material.attachmentType == 3 || values.contains(where: { OpenApiDisplay.url(from: $0) != nil }) {
            return Kind(
                title: "链接素材",
                shortTitle: "链接",
                symbol: "link",
                color: UIColor(red: 0.22, green: 0.39, blue: 0.70, alpha: 1),
                backgroundColor: UIColor(red: 0.94, green: 0.96, blue: 0.99, alpha: 1)
            )
        }
        if material.attachmentType == 6 || values.contains(where: isDocumentValue) {
            return Kind(
                title: "文件素材",
                shortTitle: "文件",
                symbol: "doc.text.fill",
                color: UIColor(red: 0.34, green: 0.38, blue: 0.44, alpha: 1),
                backgroundColor: UIColor(red: 0.95, green: 0.96, blue: 0.97, alpha: 1)
            )
        }
        if material.attachmentType > 0 {
            return Kind(
                title: "类型 \(material.attachmentType)",
                shortTitle: "类型\(material.attachmentType)",
                symbol: "shippingbox.fill",
                color: UIColor(red: 0.44, green: 0.40, blue: 0.62, alpha: 1),
                backgroundColor: UIColor(red: 0.95, green: 0.94, blue: 0.98, alpha: 1)
            )
        }
        return Kind(
            title: "文案素材",
            shortTitle: "文案",
            symbol: "text.alignleft",
            color: UIColor(red: 0.62, green: 0.41, blue: 0.12, alpha: 1),
            backgroundColor: UIColor(red: 0.99, green: 0.97, blue: 0.91, alpha: 1)
        )
    }

    static func attachmentValues(for material: OpenApiMomentMaterial) -> [String] {
        unique(material.mediaTitles.map(cleaned).filter { !$0.isEmpty })
    }

    static func titleText(for material: OpenApiMomentMaterial) -> String {
        let name = cleaned(material.name)
        if !name.isEmpty { return name }
        if let first = attachmentValues(for: material).first {
            return compactTitle(for: first)
        }
        return "未命名素材"
    }

    static func bodyText(for material: OpenApiMomentMaterial) -> String {
        let content = cleaned(material.content)
        if !content.isEmpty { return content }
        if !material.contentHash.isEmpty {
            return "正文摘要：\(material.contentHash)"
        }
        if !material.attachmentHash.isEmpty {
            return "附件摘要：\(material.attachmentHash)"
        }
        return "接口未返回正文，点击进入详情或编辑页查看模板数据。"
    }

    static func categoryLine(for material: OpenApiMomentMaterial) -> String {
        let category = normalizedCategory(material.category)
        let scene = cleaned(material.scene)
        if scene.isEmpty { return category.isEmpty ? kind(for: material).title : category }
        if category.isEmpty || category == scene { return scene }
        return "\(scene) · \(category)"
    }

    static func detailText(for material: OpenApiMomentMaterial) -> String {
        var parts: [String] = []
        if !material.updatedAt.isEmpty {
            parts.append("更新 \(material.updatedAt)")
        }
        parts.append("附件 \(max(material.attachmentCount, attachmentValues(for: material).count))")
        if material.extCommentCount > 0 {
            parts.append("评论 \(material.extCommentCount)")
        }
        if material.usageCount > 0 {
            parts.append("使用 \(material.usageCount)")
        }
        let editor = cleaned(material.updatedBy).isEmpty ? cleaned(material.createdBy) : cleaned(material.updatedBy)
        if !editor.isEmpty {
            parts.append(editor)
        }
        return parts.joined(separator: "  ")
    }

    static func compactTitle(for value: String) -> String {
        let raw = cleaned(value)
        guard let url = OpenApiDisplay.url(from: raw) else {
            return raw.count > 30 ? "\(raw.prefix(30))..." : raw
        }
        let filename = (url.lastPathComponent.removingPercentEncoding ?? url.lastPathComponent)
            .trimmingCharacters(in: .whitespacesAndNewlines)
        if !filename.isEmpty, filename != "/" {
            return filename
        }
        return url.host ?? url.absoluteString
    }

    static func compactSubtitle(for value: String) -> String {
        let raw = cleaned(value)
        guard let url = OpenApiDisplay.url(from: raw) else {
            return raw.count > 44 ? "\(raw.prefix(44))..." : raw
        }
        let host = url.host ?? "链接"
        let ext = url.pathExtension.uppercased()
        if ext.isEmpty {
            return host
        }
        return "\(host) · \(ext)"
    }

    static func tags(for material: OpenApiMomentMaterial) -> [String] {
        let kindTitle = kind(for: material).shortTitle
        return unique([kindTitle, normalizedCategory(material.category), cleaned(material.statusName)] + material.tags)
            .filter { !$0.isEmpty }
    }

    static func filterTitle(for material: OpenApiMomentMaterial) -> String {
        kind(for: material).shortTitle
    }

    static func searchableText(for material: OpenApiMomentMaterial) -> String {
        ([
            material.name,
            material.category,
            material.scene,
            material.content,
            material.statusName,
            material.contentHash,
            material.attachmentHash,
            material.commentHash,
            material.createdBy,
            material.updatedBy
        ] + material.mediaTitles + material.tags).joined(separator: " ").lowercased()
    }

    static func normalizedCategory(_ value: String) -> String {
        switch cleaned(value) {
        case "image-grid": return "图片"
        case "video": return "视频"
        case "link-card": return "链接"
        case "text": return "文案"
        default: return cleaned(value)
        }
    }

    static func isImageValue(_ value: String) -> Bool {
        imageExtensions.contains(OpenApiDisplay.url(from: value)?.pathExtension.lowercased() ?? "")
    }

    static func isVideoValue(_ value: String) -> Bool {
        videoExtensions.contains(OpenApiDisplay.url(from: value)?.pathExtension.lowercased() ?? "")
    }

    static func isDocumentValue(_ value: String) -> Bool {
        documentExtensions.contains(OpenApiDisplay.url(from: value)?.pathExtension.lowercased() ?? "")
    }

    static func cleaned(_ value: String) -> String {
        value.replacingOccurrences(of: "\u{00a0}", with: " ")
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private static let imageExtensions: Set<String> = ["jpg", "jpeg", "png", "webp", "gif", "heic", "heif"]
    private static let videoExtensions: Set<String> = ["mp4", "mov", "m4v", "webm", "avi"]
    private static let documentExtensions: Set<String> = ["pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "html", "zip"]

    private static func unique(_ values: [String]) -> [String] {
        var seen = Set<String>()
        return values.filter { value in
            let cleanedValue = cleaned(value)
            guard !cleanedValue.isEmpty, seen.insert(cleanedValue).inserted else { return false }
            return true
        }
    }
}

private final class OpenApiMomentMaterialCell: UITableViewCell {
    static let reuseIdentifier = "OpenApiMomentMaterialCell"
    var onEdit: (() -> Void)?

    private let card = UIView()
    private let typePill = UIStackView()
    private let typeIcon = UIImageView()
    private let typeLabel = UILabel()
    private let titleLabel = UILabel()
    private let categoryLabel = UILabel()
    private let statusLabel = UILabel()
    private let contentLabel = UILabel()
    private let previewStack = UIStackView()
    private let detailLabel = UILabel()
    private let tagStack = UIStackView()
    private let actionButton = UIButton(type: .system)
    private var imageTasks: [MediaThumbnailRequest] = []

    override init(style: UITableViewCell.CellStyle, reuseIdentifier: String?) {
        super.init(style: style, reuseIdentifier: reuseIdentifier)
        selectionStyle = .none
        backgroundColor = .clear
        contentView.backgroundColor = .clear

        card.backgroundColor = .white
        card.layer.cornerRadius = 14
        card.layer.cornerCurve = .continuous
        card.layer.borderWidth = 1
        card.layer.borderColor = UIColor(red: 0.88, green: 0.91, blue: 0.90, alpha: 1).cgColor
        card.translatesAutoresizingMaskIntoConstraints = false
        contentView.addSubview(card)

        typePill.axis = .horizontal
        typePill.spacing = 5
        typePill.alignment = .center
        typePill.isLayoutMarginsRelativeArrangement = true
        typePill.layoutMargins = UIEdgeInsets(top: 5, left: 8, bottom: 5, right: 9)
        typePill.layer.cornerRadius = 13
        typePill.layer.cornerCurve = .continuous
        typePill.translatesAutoresizingMaskIntoConstraints = false

        typeIcon.contentMode = .scaleAspectFit
        typeIcon.translatesAutoresizingMaskIntoConstraints = false
        typeLabel.font = .systemFont(ofSize: 11.5, weight: .bold)
        typeLabel.setContentCompressionResistancePriority(.required, for: .horizontal)
        typePill.addArrangedSubview(typeIcon)
        typePill.addArrangedSubview(typeLabel)

        titleLabel.font = .systemFont(ofSize: 16.2, weight: .semibold)
        titleLabel.textColor = UIColor(red: 0.10, green: 0.12, blue: 0.14, alpha: 1)
        titleLabel.numberOfLines = 2
        titleLabel.lineBreakMode = .byTruncatingTail

        categoryLabel.font = .systemFont(ofSize: 12.2, weight: .medium)
        categoryLabel.textColor = UIColor(red: 0.40, green: 0.44, blue: 0.48, alpha: 1)
        categoryLabel.numberOfLines = 1

        statusLabel.font = .systemFont(ofSize: 11.2, weight: .bold)
        statusLabel.textAlignment = .center
        statusLabel.layer.cornerRadius = 10
        statusLabel.layer.cornerCurve = .continuous
        statusLabel.clipsToBounds = true
        statusLabel.adjustsFontSizeToFitWidth = true
        statusLabel.minimumScaleFactor = 0.78
        statusLabel.setContentCompressionResistancePriority(.required, for: .horizontal)

        contentLabel.font = .systemFont(ofSize: 13.8, weight: .regular)
        contentLabel.textColor = UIColor(red: 0.18, green: 0.21, blue: 0.24, alpha: 1)
        contentLabel.numberOfLines = 3
        contentLabel.lineBreakMode = .byTruncatingTail

        previewStack.axis = .vertical
        previewStack.spacing = 7

        detailLabel.font = .systemFont(ofSize: 11.5, weight: .medium)
        detailLabel.textColor = UIColor(red: 0.47, green: 0.51, blue: 0.55, alpha: 1)
        detailLabel.numberOfLines = 2

        tagStack.axis = .horizontal
        tagStack.spacing = 6
        tagStack.alignment = .center
        tagStack.distribution = .fill

        actionButton.setTitle("编辑", for: .normal)
        actionButton.setImage(UIImage(systemName: "square.and.pencil"), for: .normal)
        actionButton.titleLabel?.font = .systemFont(ofSize: 13, weight: .semibold)
        actionButton.tintColor = .white
        actionButton.backgroundColor = UIColor(red: 0.10, green: 0.42, blue: 0.38, alpha: 1)
        actionButton.layer.cornerRadius = 13
        actionButton.layer.cornerCurve = .continuous
        actionButton.applyContentInsets(top: 0, leading: 10, bottom: 0, trailing: 10)
        actionButton.applyImagePadding(5)
        actionButton.addTarget(self, action: #selector(editTapped), for: .touchUpInside)
        actionButton.setContentCompressionResistancePriority(.required, for: .horizontal)

        let titleStack = UIStackView(arrangedSubviews: [titleLabel, categoryLabel])
        titleStack.axis = .vertical
        titleStack.spacing = 3

        let headerRow = UIStackView(arrangedSubviews: [typePill, titleStack, statusLabel])
        headerRow.axis = .horizontal
        headerRow.alignment = .center
        headerRow.spacing = 9

        let bottomRow = UIStackView(arrangedSubviews: [tagStack, actionButton])
        bottomRow.axis = .horizontal
        bottomRow.alignment = .center
        bottomRow.spacing = 8

        let stack = UIStackView(arrangedSubviews: [headerRow, contentLabel, previewStack, detailLabel, bottomRow])
        stack.axis = .vertical
        stack.spacing = 10
        stack.translatesAutoresizingMaskIntoConstraints = false
        stack.setCustomSpacing(8, after: contentLabel)
        stack.setCustomSpacing(7, after: previewStack)
        card.addSubview(stack)

        NSLayoutConstraint.activate([
            card.topAnchor.constraint(equalTo: contentView.topAnchor, constant: 6),
            card.leadingAnchor.constraint(equalTo: contentView.leadingAnchor, constant: 14),
            card.trailingAnchor.constraint(equalTo: contentView.trailingAnchor, constant: -14),
            card.bottomAnchor.constraint(equalTo: contentView.bottomAnchor, constant: -6),
            stack.topAnchor.constraint(equalTo: card.topAnchor, constant: 13),
            stack.leadingAnchor.constraint(equalTo: card.leadingAnchor, constant: 13),
            stack.trailingAnchor.constraint(equalTo: card.trailingAnchor, constant: -13),
            stack.bottomAnchor.constraint(equalTo: card.bottomAnchor, constant: -12),
            typePill.heightAnchor.constraint(equalToConstant: 28),
            typeIcon.widthAnchor.constraint(equalToConstant: 13),
            typeIcon.heightAnchor.constraint(equalToConstant: 13),
            statusLabel.widthAnchor.constraint(greaterThanOrEqualToConstant: 52),
            statusLabel.heightAnchor.constraint(equalToConstant: 22),
            actionButton.widthAnchor.constraint(greaterThanOrEqualToConstant: 64),
            actionButton.heightAnchor.constraint(equalToConstant: 28)
        ])
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func prepareForReuse() {
        super.prepareForReuse()
        imageTasks.forEach { $0.cancel() }
        imageTasks.removeAll()
        onEdit = nil
    }

    @objc private func editTapped() {
        onEdit?()
    }

    func configure(_ material: OpenApiMomentMaterial) {
        let kind = OpenApiMomentMaterialDisplay.kind(for: material)
        titleLabel.text = OpenApiMomentMaterialDisplay.titleText(for: material)
        categoryLabel.text = OpenApiMomentMaterialDisplay.categoryLine(for: material)
        contentLabel.text = OpenApiMomentMaterialDisplay.bodyText(for: material)
        detailLabel.text = OpenApiMomentMaterialDisplay.detailText(for: material)
        typeIcon.image = UIImage(systemName: kind.symbol)
        typeIcon.tintColor = kind.color
        typeLabel.text = kind.shortTitle
        typeLabel.textColor = kind.color
        typePill.backgroundColor = kind.color.withAlphaComponent(0.12)

        let enabled = material.status == 1 || material.statusName.contains("启用")
        statusLabel.text = material.statusName.isEmpty ? (enabled ? "启用" : "草稿") : material.statusName
        statusLabel.backgroundColor = enabled
            ? UIColor(red: 0.10, green: 0.58, blue: 0.36, alpha: 0.12)
            : UIColor(red: 0.92, green: 0.62, blue: 0.12, alpha: 0.14)
        statusLabel.textColor = enabled
            ? UIColor(red: 0.06, green: 0.40, blue: 0.24, alpha: 1)
            : UIColor(red: 0.58, green: 0.37, blue: 0.04, alpha: 1)
        actionButton.backgroundColor = enabled
            ? UIColor(red: 0.10, green: 0.42, blue: 0.38, alpha: 1)
            : UIColor(red: 0.62, green: 0.43, blue: 0.20, alpha: 1)

        tagStack.arrangedSubviews.forEach {
            tagStack.removeArrangedSubview($0)
            $0.removeFromSuperview()
        }
        OpenApiMomentMaterialDisplay.tags(for: material).prefix(3).forEach {
            tagStack.addArrangedSubview(tagLabel($0))
        }

        previewStack.arrangedSubviews.forEach {
            previewStack.removeArrangedSubview($0)
            $0.removeFromSuperview()
        }
        makePreviewViews(for: material, kind: kind).forEach { previewStack.addArrangedSubview($0) }
    }

    private func makePreviewViews(for material: OpenApiMomentMaterial, kind: OpenApiMomentMaterialDisplay.Kind) -> [UIView] {
        let values = OpenApiMomentMaterialDisplay.attachmentValues(for: material)
        if values.isEmpty {
            return [textPreview(material, kind: kind)]
        }
        let imageValues = values.filter(OpenApiMomentMaterialDisplay.isImageValue)
        if !imageValues.isEmpty {
            return [imagePreview(values: imageValues, kind: kind, count: max(material.attachmentCount, imageValues.count))]
        }
        let first = values[0]
        var rows = [attachmentRow(value: first, kind: kind)]
        if values.count > 1 {
            let more = values.dropFirst().prefix(2).map(OpenApiMomentMaterialDisplay.compactTitle(for:)).joined(separator: "、")
            rows.append(compactInfoRow(title: "更多附件", subtitle: more.isEmpty ? "\(values.count - 1) 个" : more, symbol: "tray.full.fill", color: kind.color))
        }
        return rows
    }

    private func textPreview(_ material: OpenApiMomentMaterial, kind: OpenApiMomentMaterialDisplay.Kind) -> UIView {
        compactInfoRow(
            title: material.content.isEmpty ? "接口摘要" : "朋友圈正文",
            subtitle: OpenApiMomentMaterialDisplay.bodyText(for: material),
            symbol: kind.symbol,
            color: kind.color
        )
    }

    private func attachmentRow(value: String, kind: OpenApiMomentMaterialDisplay.Kind) -> UIView {
        compactInfoRow(
            title: OpenApiMomentMaterialDisplay.compactTitle(for: value),
            subtitle: OpenApiMomentMaterialDisplay.compactSubtitle(for: value),
            symbol: kind.symbol,
            color: kind.color
        )
    }

    private func compactInfoRow(title: String, subtitle: String, symbol: String, color: UIColor) -> UIView {
        let row = UIView()
        row.backgroundColor = color.withAlphaComponent(0.07)
        row.layer.cornerRadius = 12
        row.layer.cornerCurve = .continuous
        row.layer.borderWidth = 1
        row.layer.borderColor = color.withAlphaComponent(0.12).cgColor
        row.translatesAutoresizingMaskIntoConstraints = false

        let iconHost = UIView()
        iconHost.backgroundColor = color.withAlphaComponent(0.12)
        iconHost.layer.cornerRadius = 10
        iconHost.layer.cornerCurve = .continuous
        iconHost.translatesAutoresizingMaskIntoConstraints = false
        let icon = UIImageView(image: UIImage(systemName: symbol))
        icon.tintColor = color
        icon.contentMode = .scaleAspectFit
        icon.translatesAutoresizingMaskIntoConstraints = false
        iconHost.addSubview(icon)

        let titleLabel = UILabel()
        titleLabel.text = title
        titleLabel.font = .systemFont(ofSize: 13, weight: .semibold)
        titleLabel.textColor = UIColor(red: 0.11, green: 0.13, blue: 0.15, alpha: 1)
        titleLabel.numberOfLines = 1

        let subtitleLabel = UILabel()
        subtitleLabel.text = subtitle
        subtitleLabel.font = .systemFont(ofSize: 11.2, weight: .regular)
        subtitleLabel.textColor = UIColor(red: 0.42, green: 0.46, blue: 0.50, alpha: 1)
        subtitleLabel.numberOfLines = 2

        let textStack = UIStackView(arrangedSubviews: [titleLabel, subtitleLabel])
        textStack.axis = .vertical
        textStack.spacing = 2
        textStack.translatesAutoresizingMaskIntoConstraints = false

        row.addSubview(iconHost)
        row.addSubview(textStack)
        NSLayoutConstraint.activate([
            row.heightAnchor.constraint(greaterThanOrEqualToConstant: 54),
            iconHost.leadingAnchor.constraint(equalTo: row.leadingAnchor, constant: 9),
            iconHost.centerYAnchor.constraint(equalTo: row.centerYAnchor),
            iconHost.widthAnchor.constraint(equalToConstant: 34),
            iconHost.heightAnchor.constraint(equalToConstant: 34),
            icon.centerXAnchor.constraint(equalTo: iconHost.centerXAnchor),
            icon.centerYAnchor.constraint(equalTo: iconHost.centerYAnchor),
            icon.widthAnchor.constraint(equalToConstant: 17),
            icon.heightAnchor.constraint(equalToConstant: 17),
            textStack.leadingAnchor.constraint(equalTo: iconHost.trailingAnchor, constant: 10),
            textStack.trailingAnchor.constraint(equalTo: row.trailingAnchor, constant: -10),
            textStack.topAnchor.constraint(equalTo: row.topAnchor, constant: 9),
            textStack.bottomAnchor.constraint(equalTo: row.bottomAnchor, constant: -9)
        ])
        return row
    }

    private func imagePreview(values: [String], kind: OpenApiMomentMaterialDisplay.Kind, count: Int) -> UIView {
        let wrapper = UIView()
        wrapper.translatesAutoresizingMaskIntoConstraints = false
        let row = UIStackView()
        row.axis = .horizontal
        row.spacing = 6
        row.distribution = .fillEqually
        row.translatesAutoresizingMaskIntoConstraints = false
        wrapper.addSubview(row)

        let visible = Array(values.prefix(4))
        for value in visible {
            row.addArrangedSubview(imageTile(value: value, color: kind.color))
        }
        while row.arrangedSubviews.count < min(max(count, 1), 4) {
            row.addArrangedSubview(imageTile(value: "", color: kind.color))
        }
        let badge = UILabel()
        badge.text = "\(count) 张"
        badge.font = .systemFont(ofSize: 10.5, weight: .bold)
        badge.textColor = .white
        badge.textAlignment = .center
        badge.backgroundColor = UIColor.black.withAlphaComponent(0.36)
        badge.layer.cornerRadius = 9
        badge.layer.cornerCurve = .continuous
        badge.clipsToBounds = true
        badge.translatesAutoresizingMaskIntoConstraints = false
        wrapper.addSubview(badge)

        NSLayoutConstraint.activate([
            wrapper.heightAnchor.constraint(equalToConstant: 72),
            row.topAnchor.constraint(equalTo: wrapper.topAnchor),
            row.leadingAnchor.constraint(equalTo: wrapper.leadingAnchor),
            row.trailingAnchor.constraint(equalTo: wrapper.trailingAnchor),
            row.bottomAnchor.constraint(equalTo: wrapper.bottomAnchor),
            badge.trailingAnchor.constraint(equalTo: wrapper.trailingAnchor, constant: -7),
            badge.bottomAnchor.constraint(equalTo: wrapper.bottomAnchor, constant: -7),
            badge.widthAnchor.constraint(greaterThanOrEqualToConstant: 40),
            badge.heightAnchor.constraint(equalToConstant: 18)
        ])
        return wrapper
    }

    private func imageTile(value: String, color: UIColor) -> UIView {
        let tile = UIView()
        tile.backgroundColor = color.withAlphaComponent(0.12)
        tile.layer.cornerRadius = 10
        tile.layer.cornerCurve = .continuous
        tile.clipsToBounds = true

        let imageView = UIImageView()
        imageView.contentMode = .scaleAspectFill
        imageView.backgroundColor = color.withAlphaComponent(0.10)
        imageView.translatesAutoresizingMaskIntoConstraints = false
        tile.addSubview(imageView)

        let label = UILabel()
        label.text = value.isEmpty ? "图片" : OpenApiMomentMaterialDisplay.compactTitle(for: value)
        label.font = .systemFont(ofSize: 9.5, weight: .semibold)
        label.textColor = .white
        label.textAlignment = .center
        label.numberOfLines = 2
        label.backgroundColor = UIColor.black.withAlphaComponent(0.30)
        label.translatesAutoresizingMaskIntoConstraints = false
        tile.addSubview(label)

        NSLayoutConstraint.activate([
            imageView.topAnchor.constraint(equalTo: tile.topAnchor),
            imageView.leadingAnchor.constraint(equalTo: tile.leadingAnchor),
            imageView.trailingAnchor.constraint(equalTo: tile.trailingAnchor),
            imageView.bottomAnchor.constraint(equalTo: tile.bottomAnchor),
            label.leadingAnchor.constraint(equalTo: tile.leadingAnchor),
            label.trailingAnchor.constraint(equalTo: tile.trailingAnchor),
            label.bottomAnchor.constraint(equalTo: tile.bottomAnchor),
            label.heightAnchor.constraint(greaterThanOrEqualToConstant: 24)
        ])

        guard let url = OpenApiDisplay.url(from: value),
              OpenApiMomentMaterialDisplay.isImageValue(value)
        else {
            imageView.image = UIImage(systemName: "photo")
            imageView.tintColor = color
            imageView.contentMode = .center
            return tile
        }
        imageView.accessibilityIdentifier = url.absoluteString
        let targetSize = CGSize(width: 160, height: 120)
        let scale = window?.screen.scale ?? UIScreen.main.scale
        if let cached = MediaThumbnailPipeline.shared.cachedImage(
            for: url,
            kind: .image(trimsTransparentCanvas: false),
            targetSize: targetSize,
            scale: scale
        ) {
            imageView.image = cached
        } else {
            let task = MediaThumbnailPipeline.shared.load(
                url,
                kind: .image(trimsTransparentCanvas: false),
                targetSize: targetSize,
                scale: scale,
                priority: URLSessionTask.lowPriority
            ) { [weak imageView] image in
                guard imageView?.accessibilityIdentifier == url.absoluteString else { return }
                imageView?.image = image
            }
            imageTasks.append(task)
        }
        return tile
    }

    private func tagLabel(_ text: String) -> UILabel {
        let label = UILabel()
        label.text = text
        label.font = .systemFont(ofSize: 10.5, weight: .semibold)
        label.textColor = UIColor(red: 0.28, green: 0.34, blue: 0.38, alpha: 1)
        label.backgroundColor = UIColor(red: 0.94, green: 0.95, blue: 0.95, alpha: 1)
        label.layer.cornerRadius = 9
        label.layer.cornerCurve = .continuous
        label.clipsToBounds = true
        label.textAlignment = .center
        label.translatesAutoresizingMaskIntoConstraints = false
        label.widthAnchor.constraint(greaterThanOrEqualToConstant: 42).isActive = true
        label.heightAnchor.constraint(equalToConstant: 22).isActive = true
        return label
    }
}

private final class MomentMaterialCreateSheetViewController: UIViewController {
    private let onSelect: (Int) -> Void
    private let dimView = UIView()
    private let panel = UIView()

    init(onSelect: @escaping (Int) -> Void) {
        self.onSelect = onSelect
        super.init(nibName: nil, bundle: nil)
        modalPresentationStyle = .overFullScreen
        modalTransitionStyle = .crossDissolve
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .clear

        dimView.backgroundColor = UIColor.black.withAlphaComponent(0.28)
        dimView.translatesAutoresizingMaskIntoConstraints = false
        dimView.addGestureRecognizer(UITapGestureRecognizer(target: self, action: #selector(closeTapped)))
        view.addSubview(dimView)

        panel.backgroundColor = UIColor(red: 0.965, green: 0.975, blue: 0.970, alpha: 1)
        panel.layer.cornerRadius = 24
        panel.layer.cornerCurve = .continuous
        panel.layer.maskedCorners = [.layerMinXMinYCorner, .layerMaxXMinYCorner]
        panel.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(panel)

        let grabber = UIView()
        grabber.backgroundColor = UIColor(red: 0.78, green: 0.82, blue: 0.80, alpha: 1)
        grabber.layer.cornerRadius = 2
        grabber.translatesAutoresizingMaskIntoConstraints = false

        let titleLabel = UILabel()
        titleLabel.text = "选择生成素材"
        titleLabel.font = .systemFont(ofSize: 20, weight: .bold)
        titleLabel.textColor = UIColor(red: 0.10, green: 0.12, blue: 0.14, alpha: 1)

        let subtitleLabel = UILabel()
        subtitleLabel.text = "选择一种朋友圈素材格式，生成后可继续编辑内容和附件。"
        subtitleLabel.font = .systemFont(ofSize: 13, weight: .medium)
        subtitleLabel.textColor = .secondaryLabel
        subtitleLabel.numberOfLines = 2

        let titleStack = UIStackView(arrangedSubviews: [titleLabel, subtitleLabel])
        titleStack.axis = .vertical
        titleStack.spacing = 5
        titleStack.translatesAutoresizingMaskIntoConstraints = false

        let closeButton = UIButton(type: .system)
        closeButton.setImage(UIImage(systemName: "xmark"), for: .normal)
        closeButton.tintColor = UIColor(red: 0.24, green: 0.28, blue: 0.30, alpha: 1)
        closeButton.backgroundColor = .white
        closeButton.layer.cornerRadius = 16
        closeButton.layer.cornerCurve = .continuous
        closeButton.addTarget(self, action: #selector(closeTapped), for: .touchUpInside)
        closeButton.translatesAutoresizingMaskIntoConstraints = false

        let optionStack = UIStackView(arrangedSubviews: [
            optionView(type: 1, title: "图片 / 九宫格", subtitle: "门店图、活动图、产品图，一次生成多张组合。", symbol: "square.grid.3x3.fill", color: UIColor(red: 0.15, green: 0.50, blue: 0.60, alpha: 1)),
            optionView(type: 2, title: "短视频素材", subtitle: "适合视频号、朋友圈短视频和门店动态。", symbol: "play.rectangle.fill", color: UIColor(red: 0.78, green: 0.32, blue: 0.28, alpha: 1)),
            optionView(type: 3, title: "链接卡片", subtitle: "官网、活动页、小程序落地页都可以整理成卡片。", symbol: "link", color: UIColor(red: 0.25, green: 0.42, blue: 0.74, alpha: 1)),
            optionView(type: 0, title: "纯文本草稿", subtitle: "只生成文案内容，适合先整理话术。", symbol: "text.alignleft", color: UIColor(red: 0.76, green: 0.48, blue: 0.08, alpha: 1))
        ])
        optionStack.axis = .vertical
        optionStack.spacing = 10
        optionStack.translatesAutoresizingMaskIntoConstraints = false

        panel.addSubview(grabber)
        panel.addSubview(titleStack)
        panel.addSubview(closeButton)
        panel.addSubview(optionStack)

        NSLayoutConstraint.activate([
            dimView.topAnchor.constraint(equalTo: view.topAnchor),
            dimView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            dimView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            dimView.bottomAnchor.constraint(equalTo: view.bottomAnchor),

            panel.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            panel.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            panel.bottomAnchor.constraint(equalTo: view.bottomAnchor),

            grabber.topAnchor.constraint(equalTo: panel.topAnchor, constant: 10),
            grabber.centerXAnchor.constraint(equalTo: panel.centerXAnchor),
            grabber.widthAnchor.constraint(equalToConstant: 42),
            grabber.heightAnchor.constraint(equalToConstant: 4),

            titleStack.topAnchor.constraint(equalTo: grabber.bottomAnchor, constant: 18),
            titleStack.leadingAnchor.constraint(equalTo: panel.leadingAnchor, constant: 20),
            titleStack.trailingAnchor.constraint(equalTo: closeButton.leadingAnchor, constant: -12),
            closeButton.topAnchor.constraint(equalTo: titleStack.topAnchor),
            closeButton.trailingAnchor.constraint(equalTo: panel.trailingAnchor, constant: -20),
            closeButton.widthAnchor.constraint(equalToConstant: 32),
            closeButton.heightAnchor.constraint(equalToConstant: 32),

            optionStack.topAnchor.constraint(equalTo: titleStack.bottomAnchor, constant: 18),
            optionStack.leadingAnchor.constraint(equalTo: panel.leadingAnchor, constant: 16),
            optionStack.trailingAnchor.constraint(equalTo: panel.trailingAnchor, constant: -16),
            optionStack.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor, constant: -16)
        ])
    }

    private func optionView(type: Int, title: String, subtitle: String, symbol: String, color: UIColor) -> UIControl {
        let control = UIControl()
        control.tag = type
        control.backgroundColor = .white
        control.layer.cornerRadius = 16
        control.layer.cornerCurve = .continuous
        control.addTarget(self, action: #selector(optionTapped(_:)), for: .touchUpInside)
        control.translatesAutoresizingMaskIntoConstraints = false
        control.heightAnchor.constraint(equalToConstant: 76).isActive = true

        let iconHost = UIView()
        iconHost.backgroundColor = color.withAlphaComponent(0.13)
        iconHost.layer.cornerRadius = 14
        iconHost.layer.cornerCurve = .continuous
        iconHost.translatesAutoresizingMaskIntoConstraints = false

        let icon = UIImageView(image: UIImage(systemName: symbol))
        icon.tintColor = color
        icon.contentMode = .scaleAspectFit
        icon.translatesAutoresizingMaskIntoConstraints = false
        iconHost.addSubview(icon)

        let titleLabel = UILabel()
        titleLabel.text = title
        titleLabel.font = .systemFont(ofSize: 15.5, weight: .bold)
        titleLabel.textColor = UIColor(red: 0.10, green: 0.12, blue: 0.14, alpha: 1)
        titleLabel.numberOfLines = 1

        let subtitleLabel = UILabel()
        subtitleLabel.text = subtitle
        subtitleLabel.font = .systemFont(ofSize: 12.5, weight: .medium)
        subtitleLabel.textColor = .secondaryLabel
        subtitleLabel.numberOfLines = 2

        let textStack = UIStackView(arrangedSubviews: [titleLabel, subtitleLabel])
        textStack.axis = .vertical
        textStack.spacing = 4
        textStack.translatesAutoresizingMaskIntoConstraints = false

        let chevron = UIImageView(image: UIImage(systemName: "chevron.right"))
        chevron.tintColor = UIColor(red: 0.62, green: 0.66, blue: 0.68, alpha: 1)
        chevron.translatesAutoresizingMaskIntoConstraints = false

        control.addSubview(iconHost)
        control.addSubview(textStack)
        control.addSubview(chevron)
        NSLayoutConstraint.activate([
            iconHost.leadingAnchor.constraint(equalTo: control.leadingAnchor, constant: 12),
            iconHost.centerYAnchor.constraint(equalTo: control.centerYAnchor),
            iconHost.widthAnchor.constraint(equalToConstant: 48),
            iconHost.heightAnchor.constraint(equalToConstant: 48),
            icon.centerXAnchor.constraint(equalTo: iconHost.centerXAnchor),
            icon.centerYAnchor.constraint(equalTo: iconHost.centerYAnchor),
            icon.widthAnchor.constraint(equalToConstant: 23),
            icon.heightAnchor.constraint(equalToConstant: 23),
            textStack.leadingAnchor.constraint(equalTo: iconHost.trailingAnchor, constant: 12),
            textStack.trailingAnchor.constraint(equalTo: chevron.leadingAnchor, constant: -10),
            textStack.centerYAnchor.constraint(equalTo: control.centerYAnchor),
            chevron.trailingAnchor.constraint(equalTo: control.trailingAnchor, constant: -14),
            chevron.centerYAnchor.constraint(equalTo: control.centerYAnchor),
            chevron.widthAnchor.constraint(equalToConstant: 9)
        ])
        return control
    }

    @objc private func closeTapped() {
        dismiss(animated: true)
    }

    @objc private func optionTapped(_ sender: UIControl) {
        let type = sender.tag
        dismiss(animated: true) { [onSelect] in
            onSelect(type)
        }
    }
}

private final class OpenApiMomentMaterialEditorViewController: UIViewController, UITextViewDelegate {
    private var material: OpenApiMomentMaterial
    private let onSave: (OpenApiMomentMaterial) -> Void
    private let scrollView = UIScrollView()
    private let contentView = UIView()
    private let nameField = UITextField()
    private let sceneField = UITextField()
    private let categoryField = UITextField()
    private let contentTextView = UITextView()
    private let tagField = UITextField()
    private let statusControl = UISegmentedControl(items: ["草稿", "启用"])
    private let sendSlowSwitch = UISwitch()
    private let livePreviewContainer = UIView()
    private let attachmentStack = UIStackView()
    private let attachmentSummaryLabel = UILabel()
    private var mediaTitles: [String]
    private var attachmentCount: Int

    init(material: OpenApiMomentMaterial, onSave: @escaping (OpenApiMomentMaterial) -> Void) {
        self.material = material
        self.onSave = onSave
        self.mediaTitles = material.mediaTitles
        self.attachmentCount = max(material.attachmentCount, material.mediaTitles.count)
        super.init(nibName: nil, bundle: nil)
        title = "编辑素材"
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = UIColor(red: 0.94, green: 0.96, blue: 0.97, alpha: 1)
        navigationItem.rightBarButtonItem = UIBarButtonItem(title: "保存", style: .done, target: self, action: #selector(saveTapped))
        configureLayout()
        fillMaterial()
    }

    private func configureLayout() {
        scrollView.keyboardDismissMode = .interactive
        scrollView.translatesAutoresizingMaskIntoConstraints = false
        contentView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(scrollView)
        scrollView.addSubview(contentView)

        let headerCard = makeHeaderCard()
        let formCard = makeFormCard()
        let previewCard = makePreviewEditorCard()
        let publishCard = makePublishCard()
        let tipsCard = makeTipsCard()

        let mainStack = UIStackView(arrangedSubviews: [headerCard, formCard, previewCard, publishCard, tipsCard])
        mainStack.axis = .vertical
        mainStack.spacing = 12
        mainStack.translatesAutoresizingMaskIntoConstraints = false
        contentView.addSubview(mainStack)

        NSLayoutConstraint.activate([
            scrollView.topAnchor.constraint(equalTo: view.topAnchor),
            scrollView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            scrollView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            scrollView.bottomAnchor.constraint(equalTo: view.bottomAnchor),
            contentView.topAnchor.constraint(equalTo: scrollView.contentLayoutGuide.topAnchor),
            contentView.leadingAnchor.constraint(equalTo: scrollView.contentLayoutGuide.leadingAnchor),
            contentView.trailingAnchor.constraint(equalTo: scrollView.contentLayoutGuide.trailingAnchor),
            contentView.bottomAnchor.constraint(equalTo: scrollView.contentLayoutGuide.bottomAnchor),
            contentView.widthAnchor.constraint(equalTo: scrollView.frameLayoutGuide.widthAnchor),
            mainStack.topAnchor.constraint(equalTo: contentView.topAnchor, constant: 14),
            mainStack.leadingAnchor.constraint(equalTo: contentView.leadingAnchor, constant: 16),
            mainStack.trailingAnchor.constraint(equalTo: contentView.trailingAnchor, constant: -16),
            mainStack.bottomAnchor.constraint(equalTo: contentView.bottomAnchor, constant: -18)
        ])
    }

    private func makeHeaderCard() -> UIView {
        let card = roundedCard()
        let kind = OpenApiMomentMaterialDisplay.kind(for: material)
        let iconHost = UIView()
        iconHost.backgroundColor = kind.color.withAlphaComponent(0.13)
        iconHost.layer.cornerRadius = 18
        iconHost.layer.cornerCurve = .continuous
        iconHost.translatesAutoresizingMaskIntoConstraints = false

        let icon = UIImageView(image: UIImage(systemName: kind.symbol))
        icon.tintColor = kind.color
        icon.contentMode = .scaleAspectFit
        icon.translatesAutoresizingMaskIntoConstraints = false
        iconHost.addSubview(icon)

        let titleLabel = UILabel()
        titleLabel.text = OpenApiMomentMaterialDisplay.titleText(for: material)
        titleLabel.font = .systemFont(ofSize: 21, weight: .bold)
        titleLabel.textColor = UIColor(red: 0.10, green: 0.12, blue: 0.14, alpha: 1)
        titleLabel.numberOfLines = 2

        let subtitleLabel = UILabel()
        subtitleLabel.text = [
            material.id > 0 ? "ID \(material.id)" : "新建素材",
            kind.title,
            material.statusName.isEmpty ? nil : material.statusName,
            material.updatedAt.isEmpty ? nil : "更新 \(material.updatedAt)"
        ].compactMap { $0 }.joined(separator: " · ")
        subtitleLabel.font = .systemFont(ofSize: 13, weight: .medium)
        subtitleLabel.textColor = .secondaryLabel
        subtitleLabel.numberOfLines = 2

        let textStack = UIStackView(arrangedSubviews: [titleLabel, subtitleLabel])
        textStack.axis = .vertical
        textStack.spacing = 5
        textStack.translatesAutoresizingMaskIntoConstraints = false

        card.addSubview(iconHost)
        card.addSubview(textStack)
        NSLayoutConstraint.activate([
            card.heightAnchor.constraint(equalToConstant: 92),
            iconHost.leadingAnchor.constraint(equalTo: card.leadingAnchor, constant: 16),
            iconHost.centerYAnchor.constraint(equalTo: card.centerYAnchor),
            iconHost.widthAnchor.constraint(equalToConstant: 52),
            iconHost.heightAnchor.constraint(equalToConstant: 52),
            icon.centerXAnchor.constraint(equalTo: iconHost.centerXAnchor),
            icon.centerYAnchor.constraint(equalTo: iconHost.centerYAnchor),
            icon.widthAnchor.constraint(equalToConstant: 25),
            icon.heightAnchor.constraint(equalToConstant: 25),
            textStack.leadingAnchor.constraint(equalTo: iconHost.trailingAnchor, constant: 13),
            textStack.trailingAnchor.constraint(equalTo: card.trailingAnchor, constant: -16),
            textStack.centerYAnchor.constraint(equalTo: card.centerYAnchor)
        ])
        return card
    }

    private func makeFormCard() -> UIView {
        let card = roundedCard()
        nameField.placeholder = "素材名称"
        sceneField.placeholder = "使用场景，按接口返回值保存"
        categoryField.placeholder = "素材分类，按接口返回值保存"
        tagField.placeholder = "标签，用逗号分隔"
        [nameField, sceneField, categoryField, tagField].forEach(configureTextField)

        contentTextView.font = .systemFont(ofSize: 15)
        contentTextView.textColor = UIColor(red: 0.13, green: 0.15, blue: 0.17, alpha: 1)
        contentTextView.backgroundColor = UIColor(red: 0.95, green: 0.97, blue: 0.96, alpha: 1)
        contentTextView.layer.cornerRadius = 14
        contentTextView.layer.cornerCurve = .continuous
        contentTextView.textContainerInset = UIEdgeInsets(top: 12, left: 10, bottom: 12, right: 10)
        contentTextView.delegate = self
        contentTextView.translatesAutoresizingMaskIntoConstraints = false
        contentTextView.heightAnchor.constraint(equalToConstant: 132).isActive = true

        let stack = UIStackView(arrangedSubviews: [
            sectionTitle("基础字段", subtitle: "名称、分类、场景、正文会按 OpenAPI 素材字段保存"),
            nameField,
            sceneField,
            categoryField,
            contentTextView,
            tagField
        ])
        stack.axis = .vertical
        stack.spacing = 10
        stack.translatesAutoresizingMaskIntoConstraints = false
        card.addSubview(stack)
        NSLayoutConstraint.activate([
            stack.topAnchor.constraint(equalTo: card.topAnchor, constant: 16),
            stack.leadingAnchor.constraint(equalTo: card.leadingAnchor, constant: 14),
            stack.trailingAnchor.constraint(equalTo: card.trailingAnchor, constant: -14),
            stack.bottomAnchor.constraint(equalTo: card.bottomAnchor, constant: -16)
        ])
        return card
    }

    private func makePreviewEditorCard() -> UIView {
        let card = roundedCard()
        livePreviewContainer.backgroundColor = UIColor(red: 0.95, green: 0.97, blue: 0.96, alpha: 1)
        livePreviewContainer.layer.cornerRadius = 16
        livePreviewContainer.layer.cornerCurve = .continuous
        livePreviewContainer.layer.borderWidth = 1
        livePreviewContainer.layer.borderColor = editorColor.withAlphaComponent(0.16).cgColor
        livePreviewContainer.translatesAutoresizingMaskIntoConstraints = false
        livePreviewContainer.heightAnchor.constraint(equalToConstant: 132).isActive = true

        attachmentStack.axis = .vertical
        attachmentStack.spacing = 8
        attachmentStack.translatesAutoresizingMaskIntoConstraints = false

        attachmentSummaryLabel.font = .systemFont(ofSize: 13, weight: .semibold)
        attachmentSummaryLabel.textColor = .secondaryLabel

        let addButton = editorActionButton(title: "添加附件", symbol: "plus.circle.fill", color: UIColor(red: 0.12, green: 0.47, blue: 0.42, alpha: 1))
        addButton.addTarget(self, action: #selector(addAttachmentTapped), for: .touchUpInside)
        let removeButton = editorActionButton(title: "减少附件", symbol: "minus.circle.fill", color: UIColor(red: 0.78, green: 0.32, blue: 0.28, alpha: 1))
        removeButton.addTarget(self, action: #selector(removeAttachmentTapped), for: .touchUpInside)
        let buttonRow = UIStackView(arrangedSubviews: [addButton, removeButton])
        buttonRow.axis = .horizontal
        buttonRow.spacing = 10
        buttonRow.distribution = .fillEqually

        let stack = UIStackView(arrangedSubviews: [
            sectionTitle("素材预览", subtitle: "根据接口返回的正文、附件和类型实时生成"),
            livePreviewContainer,
            sectionTitle("附件字段", subtitle: "展示并编辑接口里的附件 URL、网页、小程序或文件地址"),
            attachmentSummaryLabel,
            attachmentStack,
            buttonRow
        ])
        stack.axis = .vertical
        stack.spacing = 12
        stack.translatesAutoresizingMaskIntoConstraints = false
        card.addSubview(stack)
        NSLayoutConstraint.activate([
            stack.topAnchor.constraint(equalTo: card.topAnchor, constant: 16),
            stack.leadingAnchor.constraint(equalTo: card.leadingAnchor, constant: 14),
            stack.trailingAnchor.constraint(equalTo: card.trailingAnchor, constant: -14),
            stack.bottomAnchor.constraint(equalTo: card.bottomAnchor, constant: -16)
        ])
        return card
    }

    private func makePublishCard() -> UIView {
        let card = roundedCard()
        statusControl.selectedSegmentIndex = material.status == 1 ? 1 : 0
        statusControl.selectedSegmentTintColor = UIColor(red: 0.12, green: 0.47, blue: 0.42, alpha: 1)
        statusControl.setTitleTextAttributes([.foregroundColor: UIColor.white], for: .selected)
        statusControl.setTitleTextAttributes([.foregroundColor: UIColor(red: 0.18, green: 0.22, blue: 0.25, alpha: 1)], for: .normal)

        let slowLabel = UILabel()
        slowLabel.text = "慢速发送"
        slowLabel.font = .systemFont(ofSize: 15, weight: .semibold)
        slowLabel.textColor = UIColor(red: 0.13, green: 0.15, blue: 0.17, alpha: 1)
        let slowSubtitle = UILabel()
        slowSubtitle.text = "适合批量朋友圈发布时错峰发送"
        slowSubtitle.font = .systemFont(ofSize: 12.5, weight: .medium)
        slowSubtitle.textColor = .secondaryLabel
        let slowText = UIStackView(arrangedSubviews: [slowLabel, slowSubtitle])
        slowText.axis = .vertical
        slowText.spacing = 3
        let slowRow = UIStackView(arrangedSubviews: [slowText, sendSlowSwitch])
        slowRow.axis = .horizontal
        slowRow.alignment = .center
        slowRow.spacing = 12

        let stack = UIStackView(arrangedSubviews: [
            sectionTitle("发布设置", subtitle: "控制当前素材是否启用以及发送策略"),
            statusControl,
            slowRow
        ])
        stack.axis = .vertical
        stack.spacing = 14
        stack.translatesAutoresizingMaskIntoConstraints = false
        card.addSubview(stack)
        NSLayoutConstraint.activate([
            statusControl.heightAnchor.constraint(equalToConstant: 36),
            stack.topAnchor.constraint(equalTo: card.topAnchor, constant: 16),
            stack.leadingAnchor.constraint(equalTo: card.leadingAnchor, constant: 14),
            stack.trailingAnchor.constraint(equalTo: card.trailingAnchor, constant: -14),
            stack.bottomAnchor.constraint(equalTo: card.bottomAnchor, constant: -16)
        ])
        return card
    }

    private func makeTipsCard() -> UIView {
        let card = roundedCard()
        let title = UILabel()
        title.text = "操作"
        title.font = .systemFont(ofSize: 16, weight: .bold)
        title.textColor = UIColor(red: 0.10, green: 0.12, blue: 0.14, alpha: 1)

        let polishButton = editorActionButton(title: "优化文案", symbol: "sparkles", color: UIColor(red: 0.76, green: 0.48, blue: 0.08, alpha: 1))
        polishButton.addTarget(self, action: #selector(polishContentTapped), for: .touchUpInside)
        let duplicateButton = editorActionButton(title: "生成副本", symbol: "doc.on.doc.fill", color: UIColor(red: 0.25, green: 0.42, blue: 0.74, alpha: 1))
        duplicateButton.addTarget(self, action: #selector(duplicateContentTapped), for: .touchUpInside)
        let row = UIStackView(arrangedSubviews: [polishButton, duplicateButton])
        row.axis = .horizontal
        row.spacing = 10
        row.distribution = .fillEqually

        let stack = UIStackView(arrangedSubviews: [title, row])
        stack.axis = .vertical
        stack.spacing = 12
        stack.translatesAutoresizingMaskIntoConstraints = false
        card.addSubview(stack)
        NSLayoutConstraint.activate([
            stack.topAnchor.constraint(equalTo: card.topAnchor, constant: 16),
            stack.leadingAnchor.constraint(equalTo: card.leadingAnchor, constant: 14),
            stack.trailingAnchor.constraint(equalTo: card.trailingAnchor, constant: -14),
            stack.bottomAnchor.constraint(equalTo: card.bottomAnchor, constant: -16)
        ])
        return card
    }

    private func fillMaterial() {
        nameField.text = material.name
        sceneField.text = material.scene
        categoryField.text = material.category
        contentTextView.text = material.content
        tagField.text = material.tags.joined(separator: "，")
        sendSlowSwitch.isOn = material.sendSlow
        renderAttachments()
        renderLivePreview()
    }

    private func renderAttachments() {
        attachmentStack.arrangedSubviews.forEach {
            attachmentStack.removeArrangedSubview($0)
            $0.removeFromSuperview()
        }
        let kind = OpenApiMomentMaterialDisplay.kind(for: material)
        attachmentSummaryLabel.text = attachmentCount == 0 ? "当前没有附件 · \(kind.title)" : "当前 \(attachmentCount) 个附件 · \(kind.title)"
        guard attachmentCount > 0 else {
            attachmentStack.addArrangedSubview(emptyAttachmentView())
            renderLivePreview()
            return
        }
        let visible = min(max(attachmentCount, mediaTitles.count), 12)
        for index in 0..<visible {
            attachmentStack.addArrangedSubview(editorAttachmentRow(index: index))
        }
        renderLivePreview()
    }

    private func renderLivePreview() {
        livePreviewContainer.subviews.forEach { $0.removeFromSuperview() }
        let kind = OpenApiMomentMaterialDisplay.kind(for: currentPreviewMaterial())
        livePreviewContainer.backgroundColor = kind.backgroundColor
        livePreviewContainer.layer.borderColor = kind.color.withAlphaComponent(0.16).cgColor

        let media = editorLivePreviewMedia()
        media.translatesAutoresizingMaskIntoConstraints = false

        let badge = UILabel()
        badge.text = kind.title
        badge.font = .systemFont(ofSize: 11, weight: .bold)
        badge.textColor = kind.color
        badge.backgroundColor = kind.color.withAlphaComponent(0.12)
        badge.textAlignment = .center
        badge.layer.cornerRadius = 9
        badge.layer.cornerCurve = .continuous
        badge.clipsToBounds = true
        badge.translatesAutoresizingMaskIntoConstraints = false

        let title = UILabel()
        title.text = nameField.text?.trimmingCharacters(in: .whitespacesAndNewlines).nonEmpty ?? material.name
        title.font = .systemFont(ofSize: 14.5, weight: .bold)
        title.textColor = UIColor(red: 0.10, green: 0.12, blue: 0.14, alpha: 1)
        title.numberOfLines = 1

        let body = UILabel()
        body.text = contentTextView.text.trimmingCharacters(in: .whitespacesAndNewlines).nonEmpty ?? material.content
        body.font = .systemFont(ofSize: 12.2, weight: .medium)
        body.textColor = UIColor(red: 0.18, green: 0.21, blue: 0.24, alpha: 1)
        body.numberOfLines = 3

        let scene = UILabel()
        scene.text = [
            sceneField.text?.trimmingCharacters(in: .whitespacesAndNewlines).nonEmpty ?? material.scene,
            categoryField.text?.trimmingCharacters(in: .whitespacesAndNewlines).nonEmpty ?? material.category,
            "\(attachmentCount) 个附件"
        ].filter { !$0.isEmpty }.joined(separator: " · ")
        scene.font = .systemFont(ofSize: 11.2, weight: .semibold)
        scene.textColor = .secondaryLabel
        scene.numberOfLines = 1

        let textStack = UIStackView(arrangedSubviews: [badge, title, body, scene])
        textStack.axis = .vertical
        textStack.alignment = .leading
        textStack.spacing = 5
        textStack.translatesAutoresizingMaskIntoConstraints = false

        livePreviewContainer.addSubview(media)
        livePreviewContainer.addSubview(textStack)
        NSLayoutConstraint.activate([
            media.leadingAnchor.constraint(equalTo: livePreviewContainer.leadingAnchor, constant: 12),
            media.topAnchor.constraint(equalTo: livePreviewContainer.topAnchor, constant: 12),
            media.bottomAnchor.constraint(equalTo: livePreviewContainer.bottomAnchor, constant: -12),
            media.widthAnchor.constraint(equalToConstant: 106),
            badge.heightAnchor.constraint(equalToConstant: 20),
            badge.widthAnchor.constraint(greaterThanOrEqualToConstant: 70),
            textStack.leadingAnchor.constraint(equalTo: media.trailingAnchor, constant: 12),
            textStack.trailingAnchor.constraint(equalTo: livePreviewContainer.trailingAnchor, constant: -12),
            textStack.centerYAnchor.constraint(equalTo: livePreviewContainer.centerYAnchor)
        ])
    }

    private func currentPreviewMaterial() -> OpenApiMomentMaterial {
        OpenApiMomentMaterial(
            id: material.id,
            tenantId: material.tenantId,
            name: nameField.text?.trimmingCharacters(in: .whitespacesAndNewlines).nonEmpty ?? material.name,
            category: categoryField.text?.trimmingCharacters(in: .whitespacesAndNewlines).nonEmpty ?? material.category,
            scene: sceneField.text?.trimmingCharacters(in: .whitespacesAndNewlines).nonEmpty ?? material.scene,
            content: contentTextView.text.trimmingCharacters(in: .whitespacesAndNewlines).nonEmpty ?? material.content,
            status: statusControl.selectedSegmentIndex == 1 ? 1 : 0,
            statusName: statusControl.selectedSegmentIndex == 1 ? "启用" : "草稿",
            attachmentType: material.attachmentType,
            attachmentCount: attachmentCount,
            extCommentCount: material.extCommentCount,
            sendSlow: sendSlowSwitch.isOn,
            mediaTitles: mediaTitles,
            tags: tagField.text?
                .replacingOccurrences(of: "，", with: ",")
                .split(separator: ",")
                .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
                .filter { !$0.isEmpty } ?? material.tags,
            usageCount: material.usageCount,
            updatedAt: material.updatedAt,
            contentHash: material.contentHash,
            attachmentHash: material.attachmentHash,
            commentHash: material.commentHash,
            createdBy: material.createdBy,
            updatedBy: material.updatedBy
        )
    }

    private func editorLivePreviewMedia() -> UIView {
        let previewMaterial = currentPreviewMaterial()
        let kind = OpenApiMomentMaterialDisplay.kind(for: previewMaterial)
        let firstAttachment = OpenApiMomentMaterialDisplay.attachmentValues(for: previewMaterial).first
        return editorIconPreview(
            symbol: kind.symbol,
            title: kind.shortTitle,
            subtitle: firstAttachment.map(OpenApiMomentMaterialDisplay.compactTitle(for:)) ?? "无附件"
        )
    }

    private func editorIconPreview(symbol: String, title: String, subtitle: String) -> UIView {
        let kind = OpenApiMomentMaterialDisplay.kind(for: currentPreviewMaterial())
        let view = UIView()
        view.backgroundColor = kind.color.withAlphaComponent(0.12)
        view.layer.cornerRadius = 13
        view.layer.cornerCurve = .continuous
        let icon = UIImageView(image: UIImage(systemName: symbol))
        icon.tintColor = kind.color
        icon.translatesAutoresizingMaskIntoConstraints = false
        let titleLabel = UILabel()
        titleLabel.text = title
        titleLabel.font = .systemFont(ofSize: 12, weight: .bold)
        titleLabel.textColor = UIColor(red: 0.12, green: 0.15, blue: 0.17, alpha: 1)
        titleLabel.textAlignment = .center
        let subtitleLabel = UILabel()
        subtitleLabel.text = subtitle
        subtitleLabel.font = .systemFont(ofSize: 9.5, weight: .semibold)
        subtitleLabel.textColor = .secondaryLabel
        subtitleLabel.textAlignment = .center
        subtitleLabel.numberOfLines = 2
        let stack = UIStackView(arrangedSubviews: [icon, titleLabel, subtitleLabel])
        stack.axis = .vertical
        stack.spacing = 5
        stack.alignment = .center
        stack.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(stack)
        NSLayoutConstraint.activate([
            icon.widthAnchor.constraint(equalToConstant: 24),
            icon.heightAnchor.constraint(equalToConstant: 24),
            stack.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 8),
            stack.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -8),
            stack.centerYAnchor.constraint(equalTo: view.centerYAnchor)
        ])
        return view
    }

    private func editorAttachmentRow(index: Int) -> UIView {
        let previewMaterial = currentPreviewMaterial()
        let kind = OpenApiMomentMaterialDisplay.kind(for: previewMaterial)
        let value = mediaTitles[safe: index] ?? ""
        let row = UIView()
        row.backgroundColor = kind.color.withAlphaComponent(0.07)
        row.layer.cornerRadius = 14
        row.layer.cornerCurve = .continuous
        row.layer.borderWidth = 1
        row.layer.borderColor = kind.color.withAlphaComponent(0.12).cgColor
        row.translatesAutoresizingMaskIntoConstraints = false

        let iconHost = UIView()
        iconHost.backgroundColor = kind.color.withAlphaComponent(0.13)
        iconHost.layer.cornerRadius = 11
        iconHost.layer.cornerCurve = .continuous
        iconHost.translatesAutoresizingMaskIntoConstraints = false

        let icon = UIImageView(image: UIImage(systemName: iconName(forAttachmentValue: value, fallback: kind.symbol)))
        icon.tintColor = kind.color
        icon.contentMode = .scaleAspectFit
        icon.translatesAutoresizingMaskIntoConstraints = false
        iconHost.addSubview(icon)

        let titleLabel = UILabel()
        titleLabel.text = value.isEmpty ? "附件 \(index + 1)" : OpenApiMomentMaterialDisplay.compactTitle(for: value)
        titleLabel.font = .systemFont(ofSize: 13.5, weight: .semibold)
        titleLabel.textColor = UIColor(red: 0.12, green: 0.14, blue: 0.16, alpha: 1)
        titleLabel.numberOfLines = 1

        let subtitleLabel = UILabel()
        subtitleLabel.text = value.isEmpty ? "接口未返回附件地址，可编辑补充" : OpenApiMomentMaterialDisplay.compactSubtitle(for: value)
        subtitleLabel.font = .systemFont(ofSize: 11.5, weight: .regular)
        subtitleLabel.textColor = .secondaryLabel
        subtitleLabel.numberOfLines = 1

        let stack = UIStackView(arrangedSubviews: [titleLabel, subtitleLabel])
        stack.axis = .vertical
        stack.spacing = 3
        stack.translatesAutoresizingMaskIntoConstraints = false

        let editButton = UIButton(type: .system)
        editButton.tag = index
        editButton.setImage(UIImage(systemName: "pencil"), for: .normal)
        editButton.tintColor = kind.color
        editButton.backgroundColor = UIColor.white.withAlphaComponent(0.74)
        editButton.layer.cornerRadius = 13
        editButton.layer.cornerCurve = .continuous
        editButton.addTarget(self, action: #selector(editAttachmentTapped(_:)), for: .touchUpInside)
        editButton.translatesAutoresizingMaskIntoConstraints = false

        row.addSubview(iconHost)
        row.addSubview(stack)
        row.addSubview(editButton)
        NSLayoutConstraint.activate([
            row.heightAnchor.constraint(equalToConstant: 62),
            iconHost.leadingAnchor.constraint(equalTo: row.leadingAnchor, constant: 10),
            iconHost.centerYAnchor.constraint(equalTo: row.centerYAnchor),
            iconHost.widthAnchor.constraint(equalToConstant: 40),
            iconHost.heightAnchor.constraint(equalToConstant: 40),
            icon.centerXAnchor.constraint(equalTo: iconHost.centerXAnchor),
            icon.centerYAnchor.constraint(equalTo: iconHost.centerYAnchor),
            icon.widthAnchor.constraint(equalToConstant: 20),
            icon.heightAnchor.constraint(equalToConstant: 20),
            stack.leadingAnchor.constraint(equalTo: iconHost.trailingAnchor, constant: 11),
            stack.trailingAnchor.constraint(equalTo: editButton.leadingAnchor, constant: -10),
            stack.centerYAnchor.constraint(equalTo: row.centerYAnchor),
            editButton.trailingAnchor.constraint(equalTo: row.trailingAnchor, constant: -10),
            editButton.centerYAnchor.constraint(equalTo: row.centerYAnchor),
            editButton.widthAnchor.constraint(equalToConstant: 30),
            editButton.heightAnchor.constraint(equalToConstant: 30)
        ])
        return row
    }

    private func iconName(forAttachmentValue value: String, fallback: String) -> String {
        if OpenApiMomentMaterialDisplay.isImageValue(value) { return "photo.fill" }
        if OpenApiMomentMaterialDisplay.isVideoValue(value) { return "play.rectangle.fill" }
        if OpenApiMomentMaterialDisplay.isDocumentValue(value) { return "doc.text.fill" }
        if OpenApiDisplay.url(from: value) != nil { return "link" }
        return fallback
    }

    @objc private func editAttachmentTapped(_ sender: UIButton) {
        let index = sender.tag
        let alert = UIAlertController(title: "编辑附件", message: "修改当前素材中的附件地址。", preferredStyle: .alert)
        alert.addTextField { [weak self] field in
            field.text = self?.mediaTitles[safe: index] ?? ""
            field.placeholder = "https://example.com/material"
            field.keyboardType = .URL
            field.autocapitalizationType = .none
            field.autocorrectionType = .no
        }
        alert.addAction(UIAlertAction(title: "取消", style: .cancel))
        alert.addAction(UIAlertAction(title: "删除", style: .destructive) { [weak self] _ in
            guard let self, self.mediaTitles.indices.contains(index) else { return }
            self.mediaTitles.remove(at: index)
            self.attachmentCount = self.mediaTitles.count
            self.renderAttachments()
        })
        alert.addAction(UIAlertAction(title: "保存", style: .default) { [weak self, weak alert] _ in
            guard let self else { return }
            let value = alert?.textFields?.first?.text?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            guard !value.isEmpty else { return }
            while self.mediaTitles.count <= index {
                self.mediaTitles.append("")
            }
            self.mediaTitles[index] = value
            self.attachmentCount = max(self.attachmentCount, self.mediaTitles.count)
            self.renderAttachments()
        })
        present(alert, animated: true)
    }

    private func emptyAttachmentView() -> UIView {
        let view = UIView()
        view.backgroundColor = UIColor(red: 0.95, green: 0.97, blue: 0.96, alpha: 1)
        view.layer.cornerRadius = 14
        view.layer.cornerCurve = .continuous
        view.heightAnchor.constraint(equalToConstant: 76).isActive = true
        let label = UILabel()
        label.text = "接口未返回附件。需要图片、视频、网页或小程序时，可以添加真实 URL。"
        label.font = .systemFont(ofSize: 13, weight: .medium)
        label.textColor = .secondaryLabel
        label.textAlignment = .center
        label.numberOfLines = 2
        label.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(label)
        NSLayoutConstraint.activate([
            label.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 16),
            label.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -16),
            label.centerYAnchor.constraint(equalTo: view.centerYAnchor)
        ])
        return view
    }

    private func roundedCard() -> UIView {
        let view = UIView()
        view.backgroundColor = .white
        view.layer.cornerRadius = 18
        view.layer.cornerCurve = .continuous
        view.translatesAutoresizingMaskIntoConstraints = false
        return view
    }

    private func sectionTitle(_ title: String, subtitle: String) -> UIView {
        let titleLabel = UILabel()
        titleLabel.text = title
        titleLabel.font = .systemFont(ofSize: 16, weight: .bold)
        titleLabel.textColor = UIColor(red: 0.10, green: 0.12, blue: 0.14, alpha: 1)
        let subtitleLabel = UILabel()
        subtitleLabel.text = subtitle
        subtitleLabel.font = .systemFont(ofSize: 12.5, weight: .medium)
        subtitleLabel.textColor = .secondaryLabel
        subtitleLabel.numberOfLines = 2
        let stack = UIStackView(arrangedSubviews: [titleLabel, subtitleLabel])
        stack.axis = .vertical
        stack.spacing = 4
        return stack
    }

    private func configureTextField(_ field: UITextField) {
        field.font = .systemFont(ofSize: 15)
        field.textColor = UIColor(red: 0.13, green: 0.15, blue: 0.17, alpha: 1)
        field.backgroundColor = UIColor(red: 0.95, green: 0.97, blue: 0.96, alpha: 1)
        field.layer.cornerRadius = 14
        field.layer.cornerCurve = .continuous
        field.leftView = UIView(frame: CGRect(x: 0, y: 0, width: 12, height: 1))
        field.leftViewMode = .always
        field.clearButtonMode = .whileEditing
        field.addTarget(self, action: #selector(editorInputChanged), for: .editingChanged)
        field.heightAnchor.constraint(equalToConstant: 46).isActive = true
    }

    private func editorActionButton(title: String, symbol: String, color: UIColor) -> UIButton {
        let button = UIButton(type: .system)
        button.setTitle(title, for: .normal)
        button.setImage(UIImage(systemName: symbol), for: .normal)
        button.titleLabel?.font = .systemFont(ofSize: 14, weight: .semibold)
        button.tintColor = color
        button.backgroundColor = color.withAlphaComponent(0.11)
        button.layer.cornerRadius = 14
        button.layer.cornerCurve = .continuous
        button.applyContentInsets(top: 0, leading: 10, bottom: 0, trailing: 10)
        button.heightAnchor.constraint(equalToConstant: 44).isActive = true
        return button
    }

    private var editorTypeTitle: String {
        switch material.attachmentType {
        case 0: return "文字草稿"
        case 1: return "图片 / 九宫格素材"
        case 2: return "短视频素材"
        case 3: return "链接卡片素材"
        default: return "文档素材"
        }
    }

    private var editorIconName: String {
        switch material.attachmentType {
        case 0: return "text.alignleft"
        case 1: return "photo.fill"
        case 2: return "play.rectangle.fill"
        case 3: return "link"
        default: return "doc.text.fill"
        }
    }

    private var editorColor: UIColor {
        switch material.attachmentType {
        case 0: return UIColor(red: 0.76, green: 0.48, blue: 0.08, alpha: 1)
        case 1: return UIColor(red: 0.15, green: 0.50, blue: 0.60, alpha: 1)
        case 2: return UIColor(red: 0.78, green: 0.32, blue: 0.28, alpha: 1)
        case 3: return UIColor(red: 0.25, green: 0.42, blue: 0.74, alpha: 1)
        default: return UIColor(red: 0.44, green: 0.46, blue: 0.52, alpha: 1)
        }
    }

    private var editorPreviewBackgroundColor: UIColor {
        switch material.attachmentType {
        case 0: return UIColor(red: 1.00, green: 0.985, blue: 0.93, alpha: 1)
        case 1: return UIColor(red: 0.93, green: 0.97, blue: 0.965, alpha: 1)
        case 2: return UIColor(red: 0.965, green: 0.945, blue: 0.94, alpha: 1)
        case 3: return UIColor(red: 0.94, green: 0.96, blue: 0.99, alpha: 1)
        default: return UIColor(red: 0.95, green: 0.96, blue: 0.97, alpha: 1)
        }
    }

    private func previewColor(index: Int) -> UIColor {
        let colors = [
            UIColor(red: 0.22, green: 0.54, blue: 0.72, alpha: 1),
            UIColor(red: 0.74, green: 0.46, blue: 0.30, alpha: 1),
            UIColor(red: 0.36, green: 0.62, blue: 0.44, alpha: 1),
            UIColor(red: 0.58, green: 0.48, blue: 0.74, alpha: 1),
            UIColor(red: 0.28, green: 0.50, blue: 0.72, alpha: 1)
        ]
        return colors[index % colors.count]
    }

    @objc private func addAttachmentTapped() {
        let alert = UIAlertController(title: "添加附件", message: "填写真实图片、视频、网页、小程序或文件 URL。", preferredStyle: .alert)
        alert.addTextField { field in
            field.placeholder = "https://example.com/file.jpg"
            field.keyboardType = .URL
            field.autocapitalizationType = .none
            field.autocorrectionType = .no
        }
        alert.addAction(UIAlertAction(title: "取消", style: .cancel))
        alert.addAction(UIAlertAction(title: "添加", style: .default) { [weak self, weak alert] _ in
            guard let self else { return }
            let value = alert?.textFields?.first?.text?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            guard !value.isEmpty else { return }
            self.mediaTitles.append(value)
            self.attachmentCount = self.mediaTitles.count
            self.renderAttachments()
        })
        present(alert, animated: true)
    }

    @objc private func removeAttachmentTapped() {
        attachmentCount = max(attachmentCount - 1, 0)
        if mediaTitles.count > attachmentCount {
            mediaTitles = Array(mediaTitles.prefix(attachmentCount))
        }
        renderAttachments()
    }

    @objc private func editorInputChanged() {
        renderLivePreview()
    }

    func textViewDidChange(_ textView: UITextView) {
        renderLivePreview()
    }

    @objc private func polishContentTapped() {
        let text = contentTextView.text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { return }
        contentTextView.text = "\(text)\n\n亮点已整理：限时福利、到店体验、老客专属权益。"
    }

    @objc private func duplicateContentTapped() {
        nameField.text = "\(nameField.text?.trimmingCharacters(in: .whitespacesAndNewlines).nonEmpty ?? material.name) 副本"
    }

    @objc private func saveTapped() {
        let parsedTags = tagField.text?
            .replacingOccurrences(of: "，", with: ",")
            .split(separator: ",")
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty } ?? material.tags
        let status = statusControl.selectedSegmentIndex == 1 ? 1 : 0
        let updated = OpenApiMomentMaterial(
            id: material.id,
            tenantId: material.tenantId,
            name: nameField.text?.trimmingCharacters(in: .whitespacesAndNewlines).nonEmpty ?? material.name,
            category: categoryField.text?.trimmingCharacters(in: .whitespacesAndNewlines).nonEmpty ?? material.category,
            scene: sceneField.text?.trimmingCharacters(in: .whitespacesAndNewlines).nonEmpty ?? material.scene,
            content: contentTextView.text.trimmingCharacters(in: .whitespacesAndNewlines).nonEmpty ?? material.content,
            status: status,
            statusName: status == 1 ? "启用" : "草稿",
            attachmentType: material.attachmentType,
            attachmentCount: attachmentCount,
            extCommentCount: material.extCommentCount,
            sendSlow: sendSlowSwitch.isOn,
            mediaTitles: mediaTitles,
            tags: parsedTags,
            usageCount: material.usageCount,
            updatedAt: "刚刚",
            contentHash: material.contentHash,
            attachmentHash: "att_edit_\(material.id)_local",
            commentHash: material.commentHash,
            createdBy: material.createdBy,
            updatedBy: material.updatedBy
        )
        onSave(updated)
        navigationController?.popViewController(animated: true)
    }
}

final class OpenApiMomentMaterialsViewController: UIViewController, UITableViewDataSource, UITableViewDelegate {
    private let tenantId: String
    private let accountName: String
    private let accountContext: OpenApiWeChatAccountContext?
    private let tableView = UITableView(frame: .zero, style: .plain)
    private let searchField = UITextField()
    private let categoryScrollView = UIScrollView()
    private let categoryStack = UIStackView()
    private let summaryCard = UIView()
    private let emptyStateCard = UIView()
    private let emptyLabel = UILabel()
    private var filters = ["全部"]
    private var selectedFilter = "全部"
    private var filterButtons: [UIButton] = []
    private var summaryStatValueLabels: [UILabel] = []
    private var materials: [OpenApiMomentMaterial] = []
    private var isLoadingMaterials = false
    private var lastLoadError: String?

    init(tenantId: String, accountName: String, accountContext: OpenApiWeChatAccountContext?) {
        self.tenantId = tenantId
        self.accountName = accountName
        self.accountContext = accountContext
        super.init(nibName: nil, bundle: nil)
        title = "朋友圈素材库"
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = UIColor(red: 0.94, green: 0.96, blue: 0.97, alpha: 1)
        navigationItem.rightBarButtonItems = [
            UIBarButtonItem(title: "新建", style: .plain, target: self, action: #selector(addMaterial)),
            UIBarButtonItem(title: "计划", style: .plain, target: self, action: #selector(openBatchPlans)),
            UIBarButtonItem(image: UIImage(systemName: "arrow.clockwise"), style: .plain, target: self, action: #selector(refreshMaterials))
        ]

        configureSummaryCard()
        configureSearchField()
        configureFilters()
        configureEmptyStateCard()

        tableView.backgroundColor = .clear
        tableView.separatorStyle = .none
        tableView.dataSource = self
        tableView.delegate = self
        tableView.rowHeight = UITableView.automaticDimension
        tableView.estimatedRowHeight = 236
        tableView.register(OpenApiMomentMaterialCell.self, forCellReuseIdentifier: OpenApiMomentMaterialCell.reuseIdentifier)
        tableView.translatesAutoresizingMaskIntoConstraints = false

        emptyLabel.text = "没有匹配的素材"
        emptyLabel.font = .systemFont(ofSize: 15, weight: .medium)
        emptyLabel.textColor = .secondaryLabel
        emptyLabel.textAlignment = .center
        emptyLabel.translatesAutoresizingMaskIntoConstraints = false

        view.addSubview(summaryCard)
        view.addSubview(searchField)
        view.addSubview(categoryScrollView)
        view.addSubview(tableView)
        view.addSubview(emptyStateCard)
        view.addSubview(emptyLabel)
        NSLayoutConstraint.activate([
            summaryCard.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 12),
            summaryCard.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 16),
            summaryCard.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -16),
            summaryCard.heightAnchor.constraint(equalToConstant: 136),
            searchField.topAnchor.constraint(equalTo: summaryCard.bottomAnchor, constant: 12),
            searchField.leadingAnchor.constraint(equalTo: summaryCard.leadingAnchor),
            searchField.trailingAnchor.constraint(equalTo: summaryCard.trailingAnchor),
            searchField.heightAnchor.constraint(equalToConstant: 42),
            categoryScrollView.topAnchor.constraint(equalTo: searchField.bottomAnchor, constant: 10),
            categoryScrollView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            categoryScrollView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            categoryScrollView.heightAnchor.constraint(equalToConstant: 38),
            tableView.topAnchor.constraint(equalTo: categoryScrollView.bottomAnchor, constant: 6),
            tableView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            tableView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            tableView.bottomAnchor.constraint(equalTo: view.bottomAnchor),
            emptyStateCard.topAnchor.constraint(equalTo: categoryScrollView.bottomAnchor, constant: 18),
            emptyStateCard.leadingAnchor.constraint(equalTo: summaryCard.leadingAnchor),
            emptyStateCard.trailingAnchor.constraint(equalTo: summaryCard.trailingAnchor),
            emptyStateCard.heightAnchor.constraint(equalToConstant: 356),
            emptyLabel.centerXAnchor.constraint(equalTo: tableView.centerXAnchor),
            emptyLabel.centerYAnchor.constraint(equalTo: tableView.centerYAnchor)
        ])
        updateEmptyState()
        showCircularPageLoading(title: "加载素材库")
        loadCachedMaterials()
        loadMaterials()
    }

    private func configureSummaryCard() {
        summaryCard.backgroundColor = .white
        summaryCard.layer.cornerRadius = 18
        summaryCard.layer.cornerCurve = .continuous
        summaryCard.translatesAutoresizingMaskIntoConstraints = false

        let titleLabel = UILabel()
        titleLabel.text = "朋友圈素材库"
        titleLabel.font = .systemFont(ofSize: 21, weight: .bold)
        titleLabel.textColor = UIColor(red: 0.10, green: 0.12, blue: 0.14, alpha: 1)
        titleLabel.setContentCompressionResistancePriority(.required, for: .vertical)

        let subtitleLabel = UILabel()
        subtitleLabel.text = "当前账号：\(accountName) · 素材可用于朋友圈快速发布"
        subtitleLabel.font = .systemFont(ofSize: 13, weight: .medium)
        subtitleLabel.textColor = .secondaryLabel
        subtitleLabel.numberOfLines = 2
        subtitleLabel.setContentCompressionResistancePriority(.required, for: .vertical)

        let titleStack = UIStackView(arrangedSubviews: [titleLabel, subtitleLabel])
        titleStack.axis = .vertical
        titleStack.spacing = 4
        titleStack.translatesAutoresizingMaskIntoConstraints = false

        summaryStatValueLabels.removeAll()
        let stats = UIStackView(arrangedSubviews: [
            statView(title: "素材", value: "\(materials.count)"),
            statView(title: "启用", value: "\(materials.filter { $0.status == 1 }.count)"),
            statView(title: "使用", value: "\(materials.map(\.usageCount).reduce(0, +))")
        ])
        stats.spacing = 8
        stats.distribution = .fillEqually
        stats.translatesAutoresizingMaskIntoConstraints = false

        summaryCard.addSubview(titleStack)
        summaryCard.addSubview(stats)
        NSLayoutConstraint.activate([
            titleStack.topAnchor.constraint(equalTo: summaryCard.topAnchor, constant: 14),
            titleStack.leadingAnchor.constraint(equalTo: summaryCard.leadingAnchor, constant: 16),
            titleStack.trailingAnchor.constraint(equalTo: summaryCard.trailingAnchor, constant: -16),
            stats.topAnchor.constraint(equalTo: titleStack.bottomAnchor, constant: 14),
            stats.leadingAnchor.constraint(equalTo: summaryCard.leadingAnchor, constant: 16),
            stats.trailingAnchor.constraint(equalTo: summaryCard.trailingAnchor, constant: -16),
            stats.bottomAnchor.constraint(equalTo: summaryCard.bottomAnchor, constant: -14),
            stats.heightAnchor.constraint(equalToConstant: 48)
        ])
    }

    private func configureEmptyStateCard() {
        emptyStateCard.backgroundColor = .white
        emptyStateCard.layer.cornerRadius = 20
        emptyStateCard.layer.cornerCurve = .continuous
        emptyStateCard.translatesAutoresizingMaskIntoConstraints = false

        let iconHost = UIView()
        iconHost.backgroundColor = UIColor(red: 0.12, green: 0.47, blue: 0.42, alpha: 0.12)
        iconHost.layer.cornerRadius = 28
        iconHost.layer.cornerCurve = .continuous
        iconHost.translatesAutoresizingMaskIntoConstraints = false

        let icon = UIImageView(image: UIImage(systemName: "photo.on.rectangle.angled"))
        icon.tintColor = UIColor(red: 0.12, green: 0.47, blue: 0.42, alpha: 1)
        icon.contentMode = .scaleAspectFit
        icon.translatesAutoresizingMaskIntoConstraints = false
        iconHost.addSubview(icon)

        let titleLabel = UILabel()
        titleLabel.text = "还没有朋友圈素材"
        titleLabel.font = .systemFont(ofSize: 19, weight: .bold)
        titleLabel.textColor = UIColor(red: 0.10, green: 0.12, blue: 0.14, alpha: 1)
        titleLabel.textAlignment = .center
        titleLabel.numberOfLines = 1
        titleLabel.setContentCompressionResistancePriority(.required, for: .vertical)

        let subtitleLabel = UILabel()
        subtitleLabel.text = lastLoadError ?? "素材会按 OpenAPI 返回的 attachmentType、template、附件和状态展示。"
        subtitleLabel.font = .systemFont(ofSize: 13.2, weight: .medium)
        subtitleLabel.textColor = .secondaryLabel
        subtitleLabel.textAlignment = .center
        subtitleLabel.numberOfLines = 2
        subtitleLabel.lineBreakMode = .byTruncatingTail
        subtitleLabel.setContentCompressionResistancePriority(.required, for: .vertical)

        let typeRow = UIStackView(arrangedSubviews: [
            emptyActionChip(symbol: "text.alignleft", title: "正文"),
            emptyActionChip(symbol: "photo.fill", title: "图片"),
            emptyActionChip(symbol: "appclip.fill", title: "小程序"),
            emptyActionChip(symbol: "doc.text.fill", title: "附件")
        ])
        typeRow.axis = .horizontal
        typeRow.spacing = 8
        typeRow.distribution = .fillEqually
        typeRow.translatesAutoresizingMaskIntoConstraints = false

        let createButton = UIButton(type: .system)
        createButton.setTitle("新建朋友圈素材", for: .normal)
        createButton.setImage(UIImage(systemName: "plus.circle.fill"), for: .normal)
        createButton.titleLabel?.font = .systemFont(ofSize: 16, weight: .semibold)
        createButton.tintColor = .white
        createButton.backgroundColor = UIColor(red: 0.12, green: 0.47, blue: 0.42, alpha: 1)
        createButton.layer.cornerRadius = 16
        createButton.layer.cornerCurve = .continuous
        createButton.addTarget(self, action: #selector(addMaterial), for: .touchUpInside)
        createButton.translatesAutoresizingMaskIntoConstraints = false

        let stack = UIStackView(arrangedSubviews: [titleLabel, subtitleLabel])
        stack.axis = .vertical
        stack.spacing = 7
        stack.translatesAutoresizingMaskIntoConstraints = false

        emptyStateCard.addSubview(iconHost)
        emptyStateCard.addSubview(stack)
        emptyStateCard.addSubview(typeRow)
        emptyStateCard.addSubview(createButton)

        NSLayoutConstraint.activate([
            iconHost.topAnchor.constraint(equalTo: emptyStateCard.topAnchor, constant: 22),
            iconHost.centerXAnchor.constraint(equalTo: emptyStateCard.centerXAnchor),
            iconHost.widthAnchor.constraint(equalToConstant: 54),
            iconHost.heightAnchor.constraint(equalToConstant: 54),
            icon.centerXAnchor.constraint(equalTo: iconHost.centerXAnchor),
            icon.centerYAnchor.constraint(equalTo: iconHost.centerYAnchor),
            icon.widthAnchor.constraint(equalToConstant: 26),
            icon.heightAnchor.constraint(equalToConstant: 26),
            stack.topAnchor.constraint(equalTo: iconHost.bottomAnchor, constant: 14),
            stack.leadingAnchor.constraint(equalTo: emptyStateCard.leadingAnchor, constant: 22),
            stack.trailingAnchor.constraint(equalTo: emptyStateCard.trailingAnchor, constant: -22),
            typeRow.topAnchor.constraint(equalTo: stack.bottomAnchor, constant: 16),
            typeRow.leadingAnchor.constraint(equalTo: emptyStateCard.leadingAnchor, constant: 16),
            typeRow.trailingAnchor.constraint(equalTo: emptyStateCard.trailingAnchor, constant: -16),
            typeRow.heightAnchor.constraint(equalToConstant: 66),
            createButton.topAnchor.constraint(equalTo: typeRow.bottomAnchor, constant: 16),
            createButton.leadingAnchor.constraint(equalTo: emptyStateCard.leadingAnchor, constant: 18),
            createButton.trailingAnchor.constraint(equalTo: emptyStateCard.trailingAnchor, constant: -18),
            createButton.bottomAnchor.constraint(equalTo: emptyStateCard.bottomAnchor, constant: -20),
            createButton.heightAnchor.constraint(equalToConstant: 48)
        ])
    }

    private func emptyActionChip(symbol: String, title: String) -> UIView {
        let icon = UIImageView(image: UIImage(systemName: symbol))
        icon.tintColor = UIColor(red: 0.12, green: 0.47, blue: 0.42, alpha: 1)
        icon.contentMode = .scaleAspectFit
        let label = UILabel()
        label.text = title
        label.font = .systemFont(ofSize: 12, weight: .semibold)
        label.textColor = UIColor(red: 0.18, green: 0.22, blue: 0.25, alpha: 1)
        label.textAlignment = .center
        let stack = UIStackView(arrangedSubviews: [icon, label])
        stack.axis = .vertical
        stack.spacing = 6
        stack.alignment = .center
        stack.backgroundColor = UIColor(red: 0.94, green: 0.96, blue: 0.95, alpha: 1)
        stack.layer.cornerRadius = 14
        stack.layer.cornerCurve = .continuous
        stack.isLayoutMarginsRelativeArrangement = true
        stack.layoutMargins = UIEdgeInsets(top: 10, left: 4, bottom: 9, right: 4)
        icon.widthAnchor.constraint(equalToConstant: 22).isActive = true
        icon.heightAnchor.constraint(equalToConstant: 22).isActive = true
        return stack
    }

    private func configureSearchField() {
        searchField.placeholder = "搜索名称、正文、附件 URL、创建人"
        searchField.font = .systemFont(ofSize: 15)
        searchField.backgroundColor = .white
        searchField.layer.cornerRadius = 13
        searchField.layer.cornerCurve = .continuous
        searchField.leftView = UIImageView(image: UIImage(systemName: "magnifyingglass"))
        searchField.leftView?.tintColor = .secondaryLabel
        searchField.leftViewMode = .always
        searchField.clearButtonMode = .whileEditing
        searchField.addTarget(self, action: #selector(searchChanged), for: .editingChanged)
        searchField.translatesAutoresizingMaskIntoConstraints = false
    }

    private func configureFilters() {
        categoryScrollView.showsHorizontalScrollIndicator = false
        categoryScrollView.translatesAutoresizingMaskIntoConstraints = false
        categoryStack.axis = .horizontal
        categoryStack.spacing = 8
        categoryStack.isLayoutMarginsRelativeArrangement = true
        categoryStack.layoutMargins = UIEdgeInsets(top: 0, left: 16, bottom: 0, right: 16)
        categoryStack.translatesAutoresizingMaskIntoConstraints = false
        categoryScrollView.addSubview(categoryStack)
        NSLayoutConstraint.activate([
            categoryStack.topAnchor.constraint(equalTo: categoryScrollView.contentLayoutGuide.topAnchor),
            categoryStack.leadingAnchor.constraint(equalTo: categoryScrollView.contentLayoutGuide.leadingAnchor),
            categoryStack.trailingAnchor.constraint(equalTo: categoryScrollView.contentLayoutGuide.trailingAnchor),
            categoryStack.bottomAnchor.constraint(equalTo: categoryScrollView.contentLayoutGuide.bottomAnchor),
            categoryStack.heightAnchor.constraint(equalTo: categoryScrollView.frameLayoutGuide.heightAnchor)
        ])
        rebuildFilterButtons()
    }

    private func rebuildFilterButtons() {
        categoryStack.arrangedSubviews.forEach {
            categoryStack.removeArrangedSubview($0)
            $0.removeFromSuperview()
        }
        filterButtons = filters.enumerated().map { index, title in
            let button = UIButton(type: .system)
            button.tag = index
            button.setTitle(title, for: .normal)
            button.titleLabel?.font = .systemFont(ofSize: 14, weight: .semibold)
            button.layer.cornerRadius = 15
            button.layer.cornerCurve = .continuous
            button.applyContentInsets(top: 0, leading: 14, bottom: 0, trailing: 14)
            button.addTarget(self, action: #selector(filterButtonTapped(_:)), for: .touchUpInside)
            button.heightAnchor.constraint(equalToConstant: 30).isActive = true
            button.widthAnchor.constraint(greaterThanOrEqualToConstant: 58).isActive = true
            categoryStack.addArrangedSubview(button)
            return button
        }
        updateFilterButtons()
    }

    @objc private func filterButtonTapped(_ sender: UIButton) {
        selectedFilter = filters[safe: sender.tag] ?? "全部"
        updateFilterButtons()
        tableView.reloadData()
        updateEmptyState()
    }

    @objc private func searchChanged() {
        tableView.reloadData()
        updateEmptyState()
    }

    private func updateFilterButtons() {
        for (index, button) in filterButtons.enumerated() {
            let selected = filters[safe: index] == selectedFilter
            button.backgroundColor = selected
                ? UIColor(red: 0.12, green: 0.47, blue: 0.42, alpha: 1)
                : .white
            button.tintColor = selected
                ? .white
                : UIColor(red: 0.18, green: 0.24, blue: 0.28, alpha: 1)
        }
    }

    private func updateEmptyState() {
        let isEmpty = visibleMaterials.isEmpty
        emptyLabel.isHidden = true
        emptyStateCard.isHidden = !isEmpty
        tableView.isHidden = isEmpty
    }

    private var cacheKey: String {
        "OpenApiMomentMaterials.\(accountContext?.wxid ?? "default").\(tenantId)"
    }

    private func loadCachedMaterials() {
        guard let cached = ChatSQLiteStore.shared.codable([OpenApiMomentMaterial].self, forKey: cacheKey),
              !cached.isEmpty
        else { return }
        materials = cached
        refreshFilters()
        updateSummaryStats()
        tableView.reloadData()
        updateEmptyState()
    }

    @objc private func refreshMaterials() {
        view.endEditing(true)
        showCircularPageLoading(title: "刷新素材库")
        loadMaterials()
    }

    private func loadMaterials() {
        guard !isLoadingMaterials else { return }
        isLoadingMaterials = true
        setToolbarEnabled(false)
        Task { [weak self] in
            guard let self else { return }
            do {
                let loaded = try await OpenApiMomentAPI.listMaterials(tenantId: self.tenantId, skip: 0, take: 80)
                await MainActor.run {
                    self.lastLoadError = nil
                    self.materials = loaded
                    self.persistMaterials()
                    self.isLoadingMaterials = false
                    self.setToolbarEnabled(true)
                    self.refreshFilters()
                    self.updateSummaryStats()
                    self.tableView.reloadData()
                    self.updateEmptyState()
                }
                await self.enrichMaterialDetails()
            } catch {
                await MainActor.run {
                    self.lastLoadError = "素材库加载失败：\(error.localizedDescription)"
                    self.isLoadingMaterials = false
                    self.setToolbarEnabled(true)
                    self.updateSummaryStats()
                    self.tableView.reloadData()
                    self.updateEmptyState()
                    self.showSimpleAlert(title: "素材库加载失败", message: error.localizedDescription)
                }
            }
        }
    }

    private func enrichMaterialDetails() async {
        let materialIDs = await MainActor.run { materials.prefix(30).map(\.id).filter { $0 > 0 } }
        for id in materialIDs {
            do {
                let detail = try await OpenApiMomentAPI.getMaterialDetail(id: id, tenantId: tenantId)
                await MainActor.run {
                    guard let index = self.materials.firstIndex(where: { $0.id == id }) else { return }
                    self.materials[index] = detail
                    self.persistMaterials()
                    self.refreshFilters()
                    self.updateSummaryStats()
                    self.tableView.reloadData()
                }
            } catch {
                continue
            }
        }
    }

    private func persistMaterials() {
        ChatSQLiteStore.shared.setCodable(materials, forKey: cacheKey)
    }

    private func refreshFilters() {
        var dynamic = ["全部"]
        let typeFilters = materials
            .map(OpenApiMomentMaterialDisplay.filterTitle(for:))
            .filter { !$0.isEmpty }
        for title in typeFilters where !dynamic.contains(title) {
            dynamic.append(title)
        }
        let statuses = materials.map(\.statusName).filter { !$0.isEmpty }
        for status in statuses where !dynamic.contains(status) {
            dynamic.append(status)
        }
        filters = dynamic
        if !filters.contains(selectedFilter) {
            selectedFilter = "全部"
        }
        rebuildFilterButtons()
    }

    private func setToolbarEnabled(_ enabled: Bool) {
        navigationItem.rightBarButtonItems?.forEach { $0.isEnabled = enabled }
    }

    @objc private func openBatchPlans() {
        let controller = OpenApiMomentBatchPlansViewController(
            tenantId: tenantId,
            accountContext: accountContext,
            materialsProvider: { [weak self] in self?.materials ?? [] }
        )
        navigationController?.pushViewController(controller, animated: true)
    }

    @objc private func addMaterial() {
        let sheet = MomentMaterialCreateSheetViewController { [weak self] type in
            guard let self else { return }
            self.openMaterialEditor(self.draftMaterial(type: type))
        }
        present(sheet, animated: true)
    }

    private func draftMaterial(type: Int) -> OpenApiMomentMaterial {
        let preset = draftMaterialPreset(type: type)
        return OpenApiMomentMaterial(
            id: 0,
            tenantId: tenantId,
            name: preset.name,
            category: preset.category,
            scene: preset.scene,
            content: preset.content,
            status: 0,
            statusName: "草稿",
            attachmentType: type,
            attachmentCount: preset.attachmentCount,
            extCommentCount: 0,
            sendSlow: preset.sendSlow,
            mediaTitles: preset.mediaTitles,
            tags: preset.tags,
            usageCount: 0,
            updatedAt: "刚刚",
            contentHash: "cnt_draft_\(type)_local",
            attachmentHash: "att_draft_\(type)_local",
            commentHash: "cmt_draft_\(type)_local",
            createdBy: accountName,
            updatedBy: accountName
        )
    }

    private func draftMaterialPreset(type: Int) -> (
        name: String,
        category: String,
        scene: String,
        content: String,
        attachmentCount: Int,
        sendSlow: Bool,
        mediaTitles: [String],
        tags: [String]
    ) {
        switch type {
        case 1:
            return (
                "门店活动图片素材",
                "九宫格",
                "朋友圈图片",
                "今日门店活动现场已准备好，欢迎到店体验新品和领取限定福利。",
                9,
                true,
                ["门头", "陈列", "海报", "福利", "人气", "合照", "产品", "收银", "打卡"],
                ["图片", "九宫格", "草稿"]
            )
        case 2:
            return (
                "短视频朋友圈素材",
                "视频素材",
                "视频种草",
                "15 秒门店动线短视频，适合搭配活动利益点发布。",
                1,
                false,
                ["00:15"],
                ["视频", "门店", "草稿"]
            )
        case 3:
            return (
                "官网链接引流素材",
                "链接素材",
                "官网引流",
                "把真实预约入口放在朋友圈评论区，适合给老客户二次触达。",
                0,
                false,
                [],
                ["链接", "官网", "草稿"]
            )
        default:
            return (
                "朋友圈文字草稿",
                "文案素材",
                "纯文本",
                "今天想发一条轻量朋友圈，可以先在这里整理正文和评论区话术。",
                0,
                true,
                ["正文草稿"],
                ["文本", "草稿"]
            )
        }
    }

    private var visibleMaterials: [OpenApiMomentMaterial] {
        let query = searchField.text?.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() ?? ""
        return materials.filter { material in
            let matchesFilter = selectedFilter == "全部"
                || OpenApiMomentMaterialDisplay.filterTitle(for: material) == selectedFilter
                || material.statusName == selectedFilter
            guard matchesFilter else { return false }
            guard !query.isEmpty else { return true }
            return OpenApiMomentMaterialDisplay.searchableText(for: material).contains(query)
        }
    }

    private func statView(title: String, value: String) -> UIView {
        let valueLabel = UILabel()
        valueLabel.text = value
        valueLabel.font = .systemFont(ofSize: 16, weight: .bold)
        valueLabel.textAlignment = .center
        valueLabel.adjustsFontSizeToFitWidth = true
        valueLabel.minimumScaleFactor = 0.75
        summaryStatValueLabels.append(valueLabel)
        let titleLabel = UILabel()
        titleLabel.text = title
        titleLabel.font = .systemFont(ofSize: 11, weight: .medium)
        titleLabel.textColor = .secondaryLabel
        titleLabel.textAlignment = .center
        titleLabel.adjustsFontSizeToFitWidth = true
        titleLabel.minimumScaleFactor = 0.82
        let stack = UIStackView(arrangedSubviews: [valueLabel, titleLabel])
        stack.axis = .vertical
        stack.spacing = 2
        stack.backgroundColor = UIColor(red: 0.94, green: 0.95, blue: 0.96, alpha: 1)
        stack.layer.cornerRadius = 10
        stack.layer.cornerCurve = .continuous
        stack.isLayoutMarginsRelativeArrangement = true
        stack.layoutMargins = UIEdgeInsets(top: 6, left: 4, bottom: 6, right: 4)
        return stack
    }

    private func updateSummaryStats() {
        let values = [
            "\(materials.count)",
            "\(materials.filter { $0.status == 1 }.count)",
            "\(materials.map(\.usageCount).reduce(0, +))"
        ]
        for (label, value) in zip(summaryStatValueLabels, values) {
            label.text = value
        }
    }

    func numberOfSections(in tableView: UITableView) -> Int { 1 }
    func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int { visibleMaterials.count }
    func tableView(_ tableView: UITableView, heightForRowAt indexPath: IndexPath) -> CGFloat { UITableView.automaticDimension }
    func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = tableView.dequeueReusableCell(withIdentifier: OpenApiMomentMaterialCell.reuseIdentifier, for: indexPath) as? OpenApiMomentMaterialCell
            ?? OpenApiMomentMaterialCell(style: .default, reuseIdentifier: OpenApiMomentMaterialCell.reuseIdentifier)
        let material = visibleMaterials[indexPath.row]
        cell.configure(material)
        cell.onEdit = { [weak self] in
            self?.openMaterialEditor(material)
        }
        return cell
    }

    func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        tableView.deselectRow(at: indexPath, animated: true)
        presentMaterialActions(visibleMaterials[indexPath.row], sourceView: tableView.cellForRow(at: indexPath) ?? tableView)
    }

    private func openMaterialEditor(_ material: OpenApiMomentMaterial) {
        let editor = OpenApiMomentMaterialEditorViewController(material: material) { [weak self] updated in
            self?.updateMaterial(updated)
        }
        navigationController?.pushViewController(editor, animated: true)
    }

    private func updateMaterial(_ material: OpenApiMomentMaterial) {
        if material.attachmentType != 0,
           OpenApiMomentAPI.materialRequestBody(material, account: accountContext, includeTenant: true)["attachments"] == nil,
           material.attachmentType != 3 {
            showSimpleAlert(title: "缺少附件链接", message: "图片或视频素材需要先添加真实图片/视频 URL，再保存到接口。")
            return
        }
        showCircularPageLoading(title: material.id > 0 ? "更新素材" : "创建素材")
        setToolbarEnabled(false)
        Task { [weak self] in
            guard let self else { return }
            do {
                let saved = material.id > 0
                    ? try await OpenApiMomentAPI.updateMaterial(material, account: self.accountContext)
                    : try await OpenApiMomentAPI.createMaterial(material, account: self.accountContext)
                await MainActor.run {
                    self.setToolbarEnabled(true)
                    if let index = self.materials.firstIndex(where: { $0.id == saved.id || $0.id == material.id }) {
                        self.materials[index] = saved
                    } else {
                        self.materials.insert(saved, at: 0)
                    }
                    self.selectedFilter = "全部"
                    self.refreshFilters()
                    self.persistMaterials()
                    self.updateSummaryStats()
                    self.tableView.reloadData()
                    self.updateEmptyState()
                    self.showSimpleAlert(title: material.id > 0 ? "素材已更新" : "素材已创建", message: "已同步到 OpenAPI 素材库。")
                }
            } catch {
                await MainActor.run {
                    self.setToolbarEnabled(true)
                    self.showSimpleAlert(title: material.id > 0 ? "更新失败" : "创建失败", message: error.localizedDescription)
                }
            }
        }
    }

    private func presentMaterialActions(_ material: OpenApiMomentMaterial, sourceView: UIView) {
        let alert = UIAlertController(title: material.name, message: material.content, preferredStyle: .actionSheet)
        alert.addAction(UIAlertAction(title: "编辑素材", style: .default) { [weak self] _ in
            self?.openMaterialEditor(material)
        })
        alert.addAction(UIAlertAction(title: "获取摘要", style: .default) { [weak self] _ in
            self?.fetchMaterialSummary(material)
        })
        alert.addAction(UIAlertAction(title: "获取详情", style: .default) { [weak self] _ in
            self?.fetchMaterialDetail(material)
        })
        alert.addAction(UIAlertAction(title: "构建发布模板", style: .default) { [weak self] _ in
            self?.buildTemplate(material)
        })
        if accountContext != nil {
            alert.addAction(UIAlertAction(title: "发布一次", style: .default) { [weak self] _ in
                self?.publishMaterial(material)
            })
        }
        alert.addAction(UIAlertAction(title: "渲染批量预览", style: .default) { [weak self] _ in
            self?.renderBatchPreview(material)
        })
        alert.addAction(UIAlertAction(title: "复制素材", style: .default) { [weak self] _ in
            self?.copyMaterial(material)
        })
        alert.addAction(UIAlertAction(title: "归档素材", style: .destructive) { [weak self] _ in
            self?.archiveMaterial(material)
        })
        alert.addAction(UIAlertAction(title: "取消", style: .cancel))
        if let popover = alert.popoverPresentationController {
            popover.sourceView = sourceView
            popover.sourceRect = sourceView.bounds
        }
        present(alert, animated: true)
    }

    private func fetchMaterialSummary(_ material: OpenApiMomentMaterial) {
        guard material.id > 0 else { return }
        runMaterialAction(title: "获取素材摘要") {
            let summary = try await OpenApiMomentAPI.getMaterial(id: material.id, tenantId: material.tenantId)
            await MainActor.run {
                if let index = self.materials.firstIndex(where: { $0.id == summary.id }) {
                    self.materials[index] = summary
                    self.persistMaterials()
                    self.refreshFilters()
                    self.tableView.reloadData()
                    self.updateSummaryStats()
                }
                self.showSimpleAlert(title: "素材摘要", message: self.materialSummary(summary))
            }
        }
    }

    private func fetchMaterialDetail(_ material: OpenApiMomentMaterial) {
        guard material.id > 0 else { return }
        runMaterialAction(title: "获取素材详情") {
            let detail = try await OpenApiMomentAPI.getMaterialDetail(id: material.id, tenantId: material.tenantId)
            await MainActor.run {
                if let index = self.materials.firstIndex(where: { $0.id == detail.id }) {
                    self.materials[index] = detail
                    self.persistMaterials()
                    self.refreshFilters()
                    self.tableView.reloadData()
                    self.updateSummaryStats()
                }
                self.showSimpleAlert(title: "素材详情", message: self.materialSummary(detail))
            }
        }
    }

    private func buildTemplate(_ material: OpenApiMomentMaterial) {
        runMaterialAction(title: "构建发布模板") {
            let result = try await OpenApiMomentAPI.buildTemplate(material, account: self.accountContext)
            await MainActor.run {
                self.showSimpleAlert(title: "模板已构建", message: self.responseSummary(result))
            }
        }
    }

    private func publishMaterial(_ material: OpenApiMomentMaterial) {
        guard let accountContext else {
            showSimpleAlert(title: "缺少帐号", message: "请先选择右上角真实微信帐号。")
            return
        }
        runMaterialAction(title: "发布朋友圈") {
            let taskID = try await OpenApiMomentAPI.publishMaterial(material, account: accountContext)
            await MainActor.run {
                self.showSimpleAlert(title: "朋友圈已发布", message: "任务已完成：\(taskID)")
            }
        }
    }

    private func renderBatchPreview(_ material: OpenApiMomentMaterial) {
        guard material.id > 0 else {
            showSimpleAlert(title: "请先保存素材", message: "新建素材需要保存后才有 materialId。")
            return
        }
        runMaterialAction(title: "渲染批量预览") {
            let preview = try await OpenApiMomentAPI.renderBatchPreview(
                materialId: material.id,
                tenantId: material.tenantId,
                account: self.accountContext,
                name: "\(material.name) 批量预览"
            )
            await MainActor.run {
                self.showSimpleAlert(
                    title: preview.success ? "预览可用" : "预览有问题",
                    message: [
                        preview.message,
                        "目标数：\(preview.itemCount)",
                        "警告：\(preview.warningCount)",
                        "错误：\(preview.errorCount)",
                        preview.previewSignature.isEmpty ? nil : "签名：\(preview.previewSignature)"
                    ].compactMap { $0 }.joined(separator: "\n")
                )
            }
        }
    }

    private func copyMaterial(_ material: OpenApiMomentMaterial) {
        guard material.id > 0 else { return }
        runMaterialAction(title: "复制素材") {
            let copied = try await OpenApiMomentAPI.copyMaterial(material)
            await MainActor.run {
                self.materials.insert(copied, at: 0)
                self.persistMaterials()
                self.refreshFilters()
                self.updateSummaryStats()
                self.tableView.reloadData()
                self.updateEmptyState()
                self.showSimpleAlert(title: "复制成功", message: copied.name)
            }
        }
    }

    private func archiveMaterial(_ material: OpenApiMomentMaterial) {
        guard material.id > 0 else { return }
        runMaterialAction(title: "归档素材") {
            let archived = try await OpenApiMomentAPI.archiveMaterial(material)
            await MainActor.run {
                if let index = self.materials.firstIndex(where: { $0.id == material.id }) {
                    self.materials[index] = archived
                }
                self.persistMaterials()
                self.refreshFilters()
                self.updateSummaryStats()
                self.tableView.reloadData()
                self.updateEmptyState()
                self.showSimpleAlert(title: "已归档", message: archived.name)
            }
        }
    }

    private func runMaterialAction(title: String, operation: @escaping () async throws -> Void) {
        showCircularPageLoading(title: title)
        setToolbarEnabled(false)
        Task { [weak self] in
            guard let self else { return }
            do {
                try await operation()
                await MainActor.run {
                    self.setToolbarEnabled(true)
                }
            } catch {
                await MainActor.run {
                    self.setToolbarEnabled(true)
                    self.showSimpleAlert(title: "\(title)失败", message: error.localizedDescription)
                }
            }
        }
    }

    private func responseSummary(_ result: OpenApiHTTPResult) -> String {
        OpenApiHTTPClient.rows(from: result, maxItems: 10)
            .map { row in "\(row.key)：\(row.value)" }
            .joined(separator: "\n")
    }

    private func materialSummary(_ material: OpenApiMomentMaterial) -> String {
        [
            "ID：\(material.id)",
            "分类：\(material.category)",
            "状态：\(material.statusName)",
            "附件：\(material.attachmentCount)",
            material.content.isEmpty ? nil : "正文：\(material.content)",
            material.mediaTitles.isEmpty ? nil : "附件：\(material.mediaTitles.prefix(5).joined(separator: "\n"))"
        ].compactMap { $0 }.joined(separator: "\n")
    }

    private func showSimpleAlert(title: String, message: String?) {
        let alert = UIAlertController(title: title, message: message, preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "知道了", style: .default))
        present(alert, animated: true)
    }
}

private final class OpenApiMomentBatchPlansViewController: UIViewController, UITableViewDataSource, UITableViewDelegate {
    private let tenantId: String
    private let accountContext: OpenApiWeChatAccountContext?
    private let materialsProvider: () -> [OpenApiMomentMaterial]
    private let tableView = UITableView(frame: .zero, style: .insetGrouped)
    private let emptyLabel = UILabel()
    private var plans: [OpenApiMomentBatchPlan] = []
    private var isLoading = false

    init(
        tenantId: String,
        accountContext: OpenApiWeChatAccountContext?,
        materialsProvider: @escaping () -> [OpenApiMomentMaterial]
    ) {
        self.tenantId = tenantId
        self.accountContext = accountContext
        self.materialsProvider = materialsProvider
        super.init(nibName: nil, bundle: nil)
        title = "朋友圈批量计划"
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = UIColor(red: 0.94, green: 0.96, blue: 0.97, alpha: 1)
        navigationItem.rightBarButtonItems = [
            UIBarButtonItem(title: "新建", style: .plain, target: self, action: #selector(createPlanTapped)),
            UIBarButtonItem(image: UIImage(systemName: "arrow.clockwise"), style: .plain, target: self, action: #selector(refreshTapped))
        ]
        configureTable()
        showCircularPageLoading(title: "加载批量计划")
        loadPlans()
    }

    private func configureTable() {
        tableView.dataSource = self
        tableView.delegate = self
        tableView.backgroundColor = .clear
        tableView.keyboardDismissMode = .interactive
        tableView.register(UITableViewCell.self, forCellReuseIdentifier: "PlanCell")
        tableView.translatesAutoresizingMaskIntoConstraints = false

        emptyLabel.text = "暂无批量计划"
        emptyLabel.font = .systemFont(ofSize: 15, weight: .medium)
        emptyLabel.textColor = .secondaryLabel
        emptyLabel.textAlignment = .center
        emptyLabel.translatesAutoresizingMaskIntoConstraints = false

        view.addSubview(tableView)
        view.addSubview(emptyLabel)
        NSLayoutConstraint.activate([
            tableView.topAnchor.constraint(equalTo: view.topAnchor),
            tableView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            tableView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            tableView.bottomAnchor.constraint(equalTo: view.bottomAnchor),
            emptyLabel.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            emptyLabel.centerYAnchor.constraint(equalTo: view.centerYAnchor)
        ])
    }

    @objc private func refreshTapped() {
        showCircularPageLoading(title: "刷新计划")
        loadPlans()
    }

    private func loadPlans() {
        guard !isLoading else { return }
        isLoading = true
        setActionsEnabled(false)
        Task { [weak self] in
            guard let self else { return }
            do {
                let loaded = try await OpenApiMomentAPI.listPlans(tenantId: self.tenantId, skip: 0, take: 80)
                await MainActor.run {
                    self.plans = loaded
                    self.isLoading = false
                    self.setActionsEnabled(true)
                    self.tableView.reloadData()
                    self.updateEmptyState()
                }
            } catch {
                await MainActor.run {
                    self.isLoading = false
                    self.setActionsEnabled(true)
                    self.updateEmptyState()
                    self.showSimpleAlert(title: "计划加载失败", message: error.localizedDescription)
                }
            }
        }
    }

    private func updateEmptyState() {
        emptyLabel.isHidden = !plans.isEmpty
        tableView.isHidden = plans.isEmpty
    }

    private func setActionsEnabled(_ enabled: Bool) {
        navigationItem.rightBarButtonItems?.forEach { $0.isEnabled = enabled }
    }

    @objc private func createPlanTapped() {
        let materials = materialsProvider().filter { $0.id > 0 }
        let defaultMaterial = materials.first
        let alert = UIAlertController(title: "创建批量计划", message: "会先调用预览接口，预览通过后再创建计划。", preferredStyle: .alert)
        alert.addTextField { field in
            field.placeholder = "计划名称"
            field.text = defaultMaterial.map { "\($0.name) 批量发布" } ?? "朋友圈批量发布"
        }
        alert.addTextField { field in
            field.placeholder = "素材 ID"
            field.keyboardType = .numberPad
            field.text = defaultMaterial.map { "\($0.id)" } ?? ""
        }
        alert.addAction(UIAlertAction(title: "取消", style: .cancel))
        alert.addAction(UIAlertAction(title: "预览并创建", style: .default) { [weak self, weak alert] _ in
            guard let self else { return }
            let name = alert?.textFields?.first?.text?.trimmingCharacters(in: .whitespacesAndNewlines).nonEmpty ?? "朋友圈批量发布"
            let materialIDText = alert?.textFields?.dropFirst().first?.text?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            guard let materialID = Int64(materialIDText), materialID > 0 else {
                self.showSimpleAlert(title: "缺少素材 ID", message: "请先在素材库保存素材，再填写 materialId。")
                return
            }
            self.previewAndCreatePlan(materialID: materialID, name: name)
        })
        present(alert, animated: true)
    }

    private func previewAndCreatePlan(materialID: Int64, name: String) {
        showCircularPageLoading(title: "预览计划")
        setActionsEnabled(false)
        Task { [weak self] in
            guard let self else { return }
            do {
                let preview = try await OpenApiMomentAPI.renderBatchPreview(
                    materialId: materialID,
                    tenantId: self.tenantId,
                    account: self.accountContext,
                    name: name
                )
                guard preview.success, preview.errorCount == 0 else {
                    throw OpenApiMediaSendError.taskFailed(
                        "渲染批量预览",
                        preview.message.isEmpty ? "预览存在错误：\(preview.errorCount)" : preview.message
                    )
                }
                let created = try await OpenApiMomentAPI.createBatchPlan(
                    materialId: materialID,
                    tenantId: self.tenantId,
                    account: self.accountContext,
                    name: name,
                    previewSignature: preview.previewSignature
                )
                await MainActor.run {
                    self.plans.insert(created, at: 0)
                    self.setActionsEnabled(true)
                    self.tableView.reloadData()
                    self.updateEmptyState()
                    self.showSimpleAlert(title: "计划已创建", message: "目标数：\(preview.itemCount)\n计划：\(created.name)")
                }
            } catch {
                await MainActor.run {
                    self.setActionsEnabled(true)
                    self.showSimpleAlert(title: "创建计划失败", message: error.localizedDescription)
                }
            }
        }
    }

    func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        plans.count
    }

    func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = tableView.dequeueReusableCell(withIdentifier: "PlanCell", for: indexPath)
        let plan = plans[indexPath.row]
        var content = UIListContentConfiguration.subtitleCell()
        content.text = plan.name
        content.secondaryText = [
            "素材 \(plan.materialId)",
            plan.statusName,
            plan.progressText.isEmpty ? "\(String(format: "%.0f", plan.progressPercent))%" : plan.progressText,
            "已发 \(plan.postedCount)/\(max(plan.itemCount, 0))",
            plan.failedCount > 0 ? "失败 \(plan.failedCount)" : nil,
            plan.updatedAt
        ].compactMap { $0 }.joined(separator: " · ")
        content.image = UIImage(systemName: plan.failedCount > 0 ? "exclamationmark.triangle.fill" : "calendar.badge.clock")
        content.imageProperties.tintColor = plan.failedCount > 0 ? .systemOrange : UIColor(red: 0.12, green: 0.47, blue: 0.42, alpha: 1)
        content.textProperties.font = .systemFont(ofSize: 16, weight: .semibold)
        content.secondaryTextProperties.font = .systemFont(ofSize: 12.5, weight: .regular)
        content.secondaryTextProperties.color = .secondaryLabel
        content.secondaryTextProperties.numberOfLines = 3
        cell.contentConfiguration = content
        cell.accessoryType = .disclosureIndicator
        return cell
    }

    func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        tableView.deselectRow(at: indexPath, animated: true)
        presentPlanActions(plans[indexPath.row], sourceView: tableView.cellForRow(at: indexPath) ?? tableView)
    }

    private func presentPlanActions(_ plan: OpenApiMomentBatchPlan, sourceView: UIView) {
        let alert = UIAlertController(title: plan.name, message: planSummary(plan), preferredStyle: .actionSheet)
        alert.addAction(UIAlertAction(title: "获取计划详情", style: .default) { [weak self] _ in
            self?.loadPlanDetail(plan)
        })
        alert.addAction(UIAlertAction(title: "暂停计划", style: .default) { [weak self] _ in
            self?.controlPlan(plan, action: "pause", reason: "iOS 手动暂停")
        })
        alert.addAction(UIAlertAction(title: "恢复计划", style: .default) { [weak self] _ in
            self?.controlPlan(plan, action: "resume", reason: "iOS 手动恢复")
        })
        alert.addAction(UIAlertAction(title: "取消计划", style: .destructive) { [weak self] _ in
            self?.controlPlan(plan, action: "cancel", reason: "iOS 手动取消")
        })
        alert.addAction(UIAlertAction(title: "取消", style: .cancel))
        if let popover = alert.popoverPresentationController {
            popover.sourceView = sourceView
            popover.sourceRect = sourceView.bounds
        }
        present(alert, animated: true)
    }

    private func loadPlanDetail(_ plan: OpenApiMomentBatchPlan) {
        runPlanAction(title: "获取计划详情") {
            let detail = try await OpenApiMomentAPI.getPlan(id: plan.id)
            await MainActor.run {
                self.upsertPlan(detail)
                self.showSimpleAlert(title: "计划详情", message: self.planSummary(detail))
            }
        }
    }

    private func controlPlan(_ plan: OpenApiMomentBatchPlan, action: String, reason: String) {
        runPlanAction(title: reason) {
            let updated = try await OpenApiMomentAPI.controlPlan(plan, action: action, reason: reason)
            await MainActor.run {
                self.upsertPlan(updated)
                self.showSimpleAlert(title: "计划已更新", message: self.planSummary(updated))
            }
        }
    }

    private func runPlanAction(title: String, operation: @escaping () async throws -> Void) {
        showCircularPageLoading(title: title)
        setActionsEnabled(false)
        Task { [weak self] in
            guard let self else { return }
            do {
                try await operation()
                await MainActor.run {
                    self.setActionsEnabled(true)
                }
            } catch {
                await MainActor.run {
                    self.setActionsEnabled(true)
                    self.showSimpleAlert(title: "\(title)失败", message: error.localizedDescription)
                }
            }
        }
    }

    private func upsertPlan(_ plan: OpenApiMomentBatchPlan) {
        if let index = plans.firstIndex(where: { $0.id == plan.id }) {
            plans[index] = plan
        } else {
            plans.insert(plan, at: 0)
        }
        tableView.reloadData()
        updateEmptyState()
    }

    private func planSummary(_ plan: OpenApiMomentBatchPlan) -> String {
        [
            "ID：\(plan.id)",
            "素材：\(plan.materialId)",
            "状态：\(plan.statusName)",
            "调度：\(plan.scheduleModeName)",
            "目标：\(plan.targetModeName)",
            "进度：\(plan.progressText.isEmpty ? "\(String(format: "%.0f", plan.progressPercent))%" : plan.progressText)",
            "总数：\(plan.itemCount)",
            "待发：\(plan.pendingCount)",
            "已发：\(plan.postedCount)",
            "失败：\(plan.failedCount)"
        ].joined(separator: "\n")
    }

    private func showSimpleAlert(title: String, message: String?) {
        let alert = UIAlertController(title: title, message: message, preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "知道了", style: .default))
        present(alert, animated: true)
    }
}
