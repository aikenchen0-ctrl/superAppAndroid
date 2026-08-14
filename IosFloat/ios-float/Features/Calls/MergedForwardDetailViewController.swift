import UIKit

struct MergedForwardEntry {
    enum Kind {
        case text
        case file
        case image
        case video
    }

    let senderName: String
    let senderInitials: String
    let senderColor: UIColor
    let timestamp: String
    let kind: Kind
    let title: String
    let detail: String
    let image: UIImage?
    let sourceMessage: ChatMessage?
}

final class MergedForwardDetailViewController: UIViewController {
    var onSelectEntry: ((MergedForwardEntry, String?) -> Void)?
    var onPreviewMediaEntry: ((MergedForwardEntry) -> Void)?

    private let sourceTitle: String
    private let sourceMessage: ChatMessage
    private let entries: [MergedForwardEntry]
    private let tableView = UITableView(frame: .zero, style: .plain)

    init(title: String, sourceMessage: ChatMessage, entries: [MergedForwardEntry]) {
        self.sourceTitle = title
        self.sourceMessage = sourceMessage
        self.entries = entries
        super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        title = sourceTitle
        view.backgroundColor = UIColor(red: 0.96, green: 0.96, blue: 0.96, alpha: 1)
        navigationItem.rightBarButtonItem = UIBarButtonItem(
            image: UIImage(systemName: "ellipsis"),
            style: .plain,
            target: nil,
            action: nil
        )
        configureTableView()
    }

    private func configureTableView() {
        tableView.backgroundColor = .clear
        tableView.separatorStyle = .none
        tableView.dataSource = self
        tableView.delegate = self
        tableView.register(MergedForwardEntryCell.self, forCellReuseIdentifier: MergedForwardEntryCell.reuseIdentifier)
        tableView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(tableView)

        NSLayoutConstraint.activate([
            tableView.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
            tableView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            tableView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            tableView.bottomAnchor.constraint(equalTo: view.bottomAnchor)
        ])
    }
}

extension MergedForwardDetailViewController: UITableViewDataSource, UITableViewDelegate {
    func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        entries.count
    }

    func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = tableView.dequeueReusableCell(
            withIdentifier: MergedForwardEntryCell.reuseIdentifier,
            for: indexPath
        ) as! MergedForwardEntryCell
        cell.configure(with: entries[indexPath.row], showsDivider: indexPath.row < entries.count - 1)
        cell.onTapLink = { [weak self] urlText in
            guard let self else { return }
            self.onSelectEntry?(self.entries[indexPath.row], urlText)
        }
        cell.onTapMedia = { [weak self] in
            guard let self else { return }
            self.onPreviewMediaEntry?(self.entries[indexPath.row])
        }
        return cell
    }

    func tableView(_ tableView: UITableView, estimatedHeightForRowAt indexPath: IndexPath) -> CGFloat {
        168
    }

    func tableView(_ tableView: UITableView, heightForRowAt indexPath: IndexPath) -> CGFloat {
        UITableView.automaticDimension
    }

    func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        tableView.deselectRow(at: indexPath, animated: true)
        let entry = entries[indexPath.row]
        guard entry.kind != .image && entry.kind != .video else { return }
        onSelectEntry?(entry, nil)
    }
}


private final class MergedForwardEntryCell: UITableViewCell {
    static let reuseIdentifier = "MergedForwardEntryCell"
    var onTapLink: ((String) -> Void)?
    var onTapMedia: (() -> Void)?

    private let avatarLabel = UILabel()
    private let nameLabel = UILabel()
    private let timeLabel = UILabel()
    private let contentStack = UIStackView()
    private let titleLabel = UILabel()
    private let detailLabel = UILabel()
    private let fileCardView = UIView()
    private let fileNameLabel = UILabel()
    private let fileMetaLabel = UILabel()
    private let fileIconView = UIImageView(image: UIImage(systemName: "doc.fill"))
    private let imagePreviewView = UIImageView()
    private let mediaPlayIconView = UIImageView(image: UIImage(systemName: "play.circle.fill"))
    private let divider = UIView()
    private var titleLinkRanges: [NSRange] = []
    private var detailLinkRanges: [NSRange] = []

    override init(style: UITableViewCell.CellStyle, reuseIdentifier: String?) {
        super.init(style: style, reuseIdentifier: reuseIdentifier)
        setup()
    }

    required init?(coder: NSCoder) {
        super.init(coder: coder)
        setup()
    }

    func configure(with entry: MergedForwardEntry, showsDivider: Bool) {
        avatarLabel.text = entry.senderInitials
        avatarLabel.backgroundColor = entry.senderColor
        nameLabel.text = entry.senderName
        timeLabel.text = entry.timestamp
        titleLabel.text = entry.title
        detailLabel.text = entry.detail
        fileNameLabel.text = entry.title
        fileMetaLabel.text = entry.detail
        imagePreviewView.image = entry.image
        mediaPlayIconView.isHidden = entry.kind != .video
        divider.isHidden = !showsDivider
        titleLinkRanges = Self.detectedURLRanges(in: entry.title)
        detailLinkRanges = Self.detectedURLRanges(in: entry.detail)

        titleLabel.isHidden = true
        detailLabel.isHidden = true
        fileCardView.isHidden = true
        imagePreviewView.isHidden = true

        switch entry.kind {
        case .text:
            selectionStyle = .default
            titleLabel.isHidden = false
            detailLabel.isHidden = false
            titleLabel.attributedText = Self.linkifiedText(entry.title, font: titleLabel.font, baseColor: .label)
            detailLabel.attributedText = Self.linkifiedText(entry.detail, font: detailLabel.font, baseColor: .label)
        case .file:
            selectionStyle = .default
            fileCardView.isHidden = false
        case .image, .video:
            selectionStyle = .none
            imagePreviewView.isHidden = false
        }
    }

    private func setup() {
        selectionStyle = .default
        backgroundColor = .clear
        contentView.backgroundColor = .clear

        avatarLabel.textAlignment = .center
        avatarLabel.textColor = .white
        avatarLabel.font = .systemFont(ofSize: 15, weight: .semibold)
        avatarLabel.layer.cornerRadius = 8
        avatarLabel.layer.cornerCurve = .continuous
        avatarLabel.clipsToBounds = true
        avatarLabel.translatesAutoresizingMaskIntoConstraints = false
        contentView.addSubview(avatarLabel)

        nameLabel.font = .systemFont(ofSize: 14, weight: .semibold)
        nameLabel.textColor = UIColor.secondaryLabel
        nameLabel.translatesAutoresizingMaskIntoConstraints = false
        contentView.addSubview(nameLabel)

        timeLabel.font = .systemFont(ofSize: 14, weight: .medium)
        timeLabel.textColor = UIColor.secondaryLabel
        timeLabel.textAlignment = .right
        timeLabel.translatesAutoresizingMaskIntoConstraints = false
        contentView.addSubview(timeLabel)

        contentStack.axis = .vertical
        contentStack.spacing = 9
        contentStack.translatesAutoresizingMaskIntoConstraints = false
        contentView.addSubview(contentStack)

        titleLabel.font = .systemFont(ofSize: 17, weight: .regular)
        titleLabel.numberOfLines = 0
        titleLabel.isUserInteractionEnabled = true
        detailLabel.font = .systemFont(ofSize: 17, weight: .regular)
        detailLabel.textColor = .label
        detailLabel.numberOfLines = 0
        detailLabel.isUserInteractionEnabled = true
        titleLabel.addGestureRecognizer(UITapGestureRecognizer(target: self, action: #selector(handleLabelTap(_:))))
        detailLabel.addGestureRecognizer(UITapGestureRecognizer(target: self, action: #selector(handleLabelTap(_:))))
        contentStack.addArrangedSubview(titleLabel)
        contentStack.addArrangedSubview(detailLabel)

        fileCardView.backgroundColor = UIColor(red: 0.91, green: 0.92, blue: 0.94, alpha: 1)
        fileCardView.layer.cornerRadius = 8
        fileCardView.layer.cornerCurve = .continuous
        fileCardView.translatesAutoresizingMaskIntoConstraints = false
        contentStack.addArrangedSubview(fileCardView)

        fileNameLabel.font = .systemFont(ofSize: 17, weight: .regular)
        fileNameLabel.textColor = .label
        fileNameLabel.numberOfLines = 2
        fileNameLabel.translatesAutoresizingMaskIntoConstraints = false
        fileCardView.addSubview(fileNameLabel)

        fileMetaLabel.font = .systemFont(ofSize: 14, weight: .medium)
        fileMetaLabel.textColor = UIColor.secondaryLabel
        fileMetaLabel.numberOfLines = 2
        fileMetaLabel.translatesAutoresizingMaskIntoConstraints = false
        fileCardView.addSubview(fileMetaLabel)

        fileIconView.tintColor = UIColor(red: 0.55, green: 0.60, blue: 0.70, alpha: 1)
        fileIconView.contentMode = .scaleAspectFit
        fileIconView.translatesAutoresizingMaskIntoConstraints = false
        fileCardView.addSubview(fileIconView)

        imagePreviewView.backgroundColor = UIColor(red: 0.86, green: 0.92, blue: 0.93, alpha: 1)
        imagePreviewView.contentMode = .scaleAspectFill
        imagePreviewView.clipsToBounds = true
        imagePreviewView.layer.cornerRadius = 6
        imagePreviewView.isUserInteractionEnabled = true
        imagePreviewView.addGestureRecognizer(UITapGestureRecognizer(target: self, action: #selector(handleMediaTap)))
        imagePreviewView.translatesAutoresizingMaskIntoConstraints = false
        contentStack.addArrangedSubview(imagePreviewView)

        mediaPlayIconView.tintColor = UIColor.white.withAlphaComponent(0.92)
        mediaPlayIconView.contentMode = .scaleAspectFit
        mediaPlayIconView.isHidden = true
        mediaPlayIconView.translatesAutoresizingMaskIntoConstraints = false
        imagePreviewView.addSubview(mediaPlayIconView)

        divider.backgroundColor = UIColor.separator.withAlphaComponent(0.6)
        divider.translatesAutoresizingMaskIntoConstraints = false
        contentView.addSubview(divider)

        NSLayoutConstraint.activate([
            avatarLabel.leadingAnchor.constraint(equalTo: contentView.leadingAnchor, constant: 40),
            avatarLabel.topAnchor.constraint(equalTo: contentView.topAnchor, constant: 24),
            avatarLabel.widthAnchor.constraint(equalToConstant: 36),
            avatarLabel.heightAnchor.constraint(equalToConstant: 36),

            nameLabel.leadingAnchor.constraint(equalTo: avatarLabel.trailingAnchor, constant: 20),
            nameLabel.topAnchor.constraint(equalTo: contentView.topAnchor, constant: 26),
            nameLabel.trailingAnchor.constraint(lessThanOrEqualTo: timeLabel.leadingAnchor, constant: -12),

            timeLabel.trailingAnchor.constraint(equalTo: contentView.trailingAnchor, constant: -40),
            timeLabel.centerYAnchor.constraint(equalTo: nameLabel.centerYAnchor),
            timeLabel.widthAnchor.constraint(greaterThanOrEqualToConstant: 170),

            contentStack.leadingAnchor.constraint(equalTo: nameLabel.leadingAnchor),
            contentStack.trailingAnchor.constraint(equalTo: timeLabel.trailingAnchor),
            contentStack.topAnchor.constraint(equalTo: nameLabel.bottomAnchor, constant: 12),
            contentStack.bottomAnchor.constraint(equalTo: contentView.bottomAnchor, constant: -22),

            fileCardView.widthAnchor.constraint(lessThanOrEqualToConstant: 494),
            fileCardView.heightAnchor.constraint(greaterThanOrEqualToConstant: 116),
            fileNameLabel.leadingAnchor.constraint(equalTo: fileCardView.leadingAnchor, constant: 20),
            fileNameLabel.topAnchor.constraint(equalTo: fileCardView.topAnchor, constant: 20),
            fileNameLabel.trailingAnchor.constraint(equalTo: fileIconView.leadingAnchor, constant: -18),
            fileMetaLabel.leadingAnchor.constraint(equalTo: fileNameLabel.leadingAnchor),
            fileMetaLabel.trailingAnchor.constraint(equalTo: fileNameLabel.trailingAnchor),
            fileMetaLabel.topAnchor.constraint(equalTo: fileNameLabel.bottomAnchor, constant: 12),
            fileMetaLabel.bottomAnchor.constraint(lessThanOrEqualTo: fileCardView.bottomAnchor, constant: -18),
            fileIconView.trailingAnchor.constraint(equalTo: fileCardView.trailingAnchor, constant: -28),
            fileIconView.centerYAnchor.constraint(equalTo: fileCardView.centerYAnchor),
            fileIconView.widthAnchor.constraint(equalToConstant: 54),
            fileIconView.heightAnchor.constraint(equalToConstant: 66),

            imagePreviewView.widthAnchor.constraint(equalToConstant: 258),
            imagePreviewView.heightAnchor.constraint(equalToConstant: 360),
            mediaPlayIconView.centerXAnchor.constraint(equalTo: imagePreviewView.centerXAnchor),
            mediaPlayIconView.centerYAnchor.constraint(equalTo: imagePreviewView.centerYAnchor),
            mediaPlayIconView.widthAnchor.constraint(equalToConstant: 54),
            mediaPlayIconView.heightAnchor.constraint(equalToConstant: 54),

            divider.leadingAnchor.constraint(equalTo: nameLabel.leadingAnchor),
            divider.trailingAnchor.constraint(equalTo: timeLabel.trailingAnchor),
            divider.bottomAnchor.constraint(equalTo: contentView.bottomAnchor),
            divider.heightAnchor.constraint(equalToConstant: 1 / UIScreen.main.scale)
        ])
    }

    @objc private func handleLabelTap(_ gesture: UITapGestureRecognizer) {
        guard let label = gesture.view as? UILabel,
              let attributedText = label.attributedText,
              attributedText.length > 0
        else { return }
        let ranges = label === titleLabel ? titleLinkRanges : detailLinkRanges
        guard !ranges.isEmpty else { return }
        let point = gesture.location(in: label)
        guard let urlText = linkText(in: label, attributedText: attributedText, ranges: ranges, point: point) else { return }
        onTapLink?(urlText)
    }

    @objc private func handleMediaTap() {
        onTapMedia?()
    }

    private func linkText(
        in label: UILabel,
        attributedText: NSAttributedString,
        ranges: [NSRange],
        point: CGPoint
    ) -> String? {
        let textStorage = NSTextStorage(attributedString: attributedText)
        let layoutManager = NSLayoutManager()
        let textContainer = NSTextContainer(size: label.bounds.size)
        textContainer.lineFragmentPadding = 0
        textContainer.maximumNumberOfLines = label.numberOfLines
        textContainer.lineBreakMode = label.lineBreakMode
        layoutManager.addTextContainer(textContainer)
        textStorage.addLayoutManager(layoutManager)

        let glyphRange = layoutManager.glyphRange(for: textContainer)
        let usedRect = layoutManager.usedRect(for: textContainer)
        let textOffset = CGPoint(
            x: (label.bounds.width - usedRect.width) * 0.5 - usedRect.minX,
            y: (label.bounds.height - usedRect.height) * 0.5 - usedRect.minY
        )
        let location = CGPoint(x: point.x - textOffset.x, y: point.y - textOffset.y)
        let glyphIndex = layoutManager.glyphIndex(for: location, in: textContainer)
        guard NSLocationInRange(glyphIndex, glyphRange) else { return nil }

        let characterIndex = layoutManager.characterIndex(for: location, in: textContainer, fractionOfDistanceBetweenInsertionPoints: nil)
        guard let range = ranges.first(where: { NSLocationInRange(characterIndex, $0) }),
              let swiftRange = Range(range, in: attributedText.string)
        else { return nil }
        return String(attributedText.string[swiftRange])
    }

    private static func linkifiedText(_ text: String, font: UIFont, baseColor: UIColor) -> NSAttributedString {
        let attributed = NSMutableAttributedString(
            string: text,
            attributes: [
                .font: font,
                .foregroundColor: baseColor
            ]
        )
        for range in detectedURLRanges(in: text) {
            attributed.addAttributes(
                [
                    .foregroundColor: UIColor.systemBlue,
                    .underlineStyle: NSUnderlineStyle.single.rawValue
                ],
                range: range
            )
        }
        return attributed
    }

    private static func detectedURLRanges(in text: String) -> [NSRange] {
        let pattern = #"(?i)\b((?:https?://|www\.)[A-Za-z0-9\-._~:/?#\[\]@!$&'()*+,;=%]+)"#
        guard let regex = try? NSRegularExpression(pattern: pattern) else { return [] }
        let fullRange = NSRange(text.startIndex..<text.endIndex, in: text)
        return regex.matches(in: text, range: fullRange).compactMap { match in
            guard let range = Range(match.range(at: 1), in: text) else { return nil }
            var end = range.upperBound
            while end > range.lowerBound,
                  trailingURLPunctuation.contains(text[text.index(before: end)]) {
                end = text.index(before: end)
            }
            guard end > range.lowerBound else { return nil }
            return NSRange(range.lowerBound..<end, in: text)
        }
    }

    private static let trailingURLPunctuation: Set<Character> = [
        ".", ",", "!", "?", ":", ";", ")", "]", "}", "。", "，", "！", "？", "：", "；"
    ]
}

