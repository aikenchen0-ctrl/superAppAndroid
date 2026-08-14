import UIKit

final class GlobalSearchViewController: UIViewController, UITableViewDataSource, UITableViewDelegate, UISearchBarDelegate {
    var onSelectResult: ((GlobalSearchResult) -> Void)?

    private static let categorySelectedBackgroundColor = UIColor(red: 0.08, green: 0.34, blue: 0.27, alpha: 1)
    private static let categorySelectedTextColor = UIColor.white
    private static let categoryNormalBackgroundColor = UIColor.white
    private static let categoryNormalTextColor = UIColor(red: 0.18, green: 0.24, blue: 0.25, alpha: 1)
    private static let categoryNormalBorderColor = UIColor(red: 0.79, green: 0.86, blue: 0.83, alpha: 1)
    private static let categorySelectedBorderColor = UIColor(red: 0.08, green: 0.34, blue: 0.27, alpha: 1)
    private static let allCategoryWidth: CGFloat = 62
    private static let categoryButtonWidth: CGFloat = 78
    private static let categoryButtonHeight: CGFloat = 34

    private var allResults: [GlobalSearchResult]
    private var visibleResults: [GlobalSearchResult] = []
    private var query = ""
    private var selectedCategory: GlobalSearchCategory = .all
    private var isLoadingIndex: Bool
    private let searchBar = UISearchBar()
    private let allCategoryButton = UIButton(type: .system)
    private let categoryScrollView = UIScrollView()
    private let categoryStackView = UIStackView()
    private var categoryButtons: [GlobalSearchCategory: UIButton] = [:]
    private let tableView = UITableView(frame: .zero, style: .insetGrouped)
    private let emptyLabel = UILabel()

    init(results: [GlobalSearchResult], isLoadingIndex: Bool = false) {
        self.allResults = results
        self.isLoadingIndex = isLoadingIndex
        super.init(nibName: nil, bundle: nil)
        title = "全局搜索"
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
        if isLoadingIndex {
            showCircularPageLoading(title: "加载搜索索引")
        }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.25) {
            self.searchBar.becomeFirstResponder()
        }
    }

    func updateResults(_ results: [GlobalSearchResult]) {
        allResults = results
        isLoadingIndex = false
        applyFilter()
    }

    private func configureHeader() {
        searchBar.placeholder = "搜索联系人、群聊、消息、文件、朋友圈、视频号"
        searchBar.searchBarStyle = .minimal
        searchBar.delegate = self
        searchBar.translatesAutoresizingMaskIntoConstraints = false

        configureCategoryButton(allCategoryButton, category: .all)
        allCategoryButton.translatesAutoresizingMaskIntoConstraints = false
        categoryButtons[.all] = allCategoryButton

        categoryScrollView.showsHorizontalScrollIndicator = false
        categoryScrollView.alwaysBounceHorizontal = true
        categoryScrollView.translatesAutoresizingMaskIntoConstraints = false

        categoryStackView.axis = .horizontal
        categoryStackView.alignment = .fill
        categoryStackView.spacing = 8
        categoryStackView.translatesAutoresizingMaskIntoConstraints = false
        categoryScrollView.addSubview(categoryStackView)

        GlobalSearchCategory.allCases
            .filter { $0 != .all }
            .forEach { category in
                let button = UIButton(type: .system)
                configureCategoryButton(button, category: category)
                categoryStackView.addArrangedSubview(button)
                categoryButtons[category] = button
            }

        let header = UIView()
        header.backgroundColor = UIColor.systemGroupedBackground
        header.addSubview(searchBar)
        header.addSubview(allCategoryButton)
        header.addSubview(categoryScrollView)
        header.frame = CGRect(x: 0, y: 0, width: view.bounds.width, height: 110)
        tableView.tableHeaderView = header

        NSLayoutConstraint.activate([
            searchBar.topAnchor.constraint(equalTo: header.topAnchor, constant: 8),
            searchBar.leadingAnchor.constraint(equalTo: header.leadingAnchor, constant: 8),
            searchBar.trailingAnchor.constraint(equalTo: header.trailingAnchor, constant: -8),
            allCategoryButton.topAnchor.constraint(equalTo: searchBar.bottomAnchor, constant: 7),
            allCategoryButton.leadingAnchor.constraint(equalTo: header.leadingAnchor, constant: 16),
            allCategoryButton.widthAnchor.constraint(equalToConstant: Self.allCategoryWidth),
            allCategoryButton.heightAnchor.constraint(equalToConstant: Self.categoryButtonHeight),

            categoryScrollView.topAnchor.constraint(equalTo: allCategoryButton.topAnchor),
            categoryScrollView.leadingAnchor.constraint(equalTo: allCategoryButton.trailingAnchor, constant: 8),
            categoryScrollView.trailingAnchor.constraint(equalTo: header.trailingAnchor, constant: -16),
            categoryScrollView.heightAnchor.constraint(equalTo: allCategoryButton.heightAnchor),

            categoryStackView.topAnchor.constraint(equalTo: categoryScrollView.contentLayoutGuide.topAnchor),
            categoryStackView.leadingAnchor.constraint(equalTo: categoryScrollView.contentLayoutGuide.leadingAnchor),
            categoryStackView.trailingAnchor.constraint(equalTo: categoryScrollView.contentLayoutGuide.trailingAnchor),
            categoryStackView.bottomAnchor.constraint(equalTo: categoryScrollView.contentLayoutGuide.bottomAnchor),
            categoryStackView.heightAnchor.constraint(equalTo: categoryScrollView.frameLayoutGuide.heightAnchor)
        ])

        updateCategoryButtons()
    }

    private func configureCategoryButton(_ button: UIButton, category: GlobalSearchCategory) {
        button.tag = category.rawValue
        var configuration = UIButton.Configuration.filled()
        configuration.contentInsets = NSDirectionalEdgeInsets(top: 0, leading: 12, bottom: 0, trailing: 12)
        configuration.cornerStyle = .capsule
        button.configuration = configuration
        button.titleLabel?.lineBreakMode = .byClipping
        button.layer.cornerRadius = 17
        button.layer.cornerCurve = .continuous
        button.layer.borderWidth = 1
        button.clipsToBounds = true
        button.addTarget(self, action: #selector(categoryButtonTapped(_:)), for: .touchUpInside)
        button.translatesAutoresizingMaskIntoConstraints = false
        button.heightAnchor.constraint(equalToConstant: Self.categoryButtonHeight).isActive = true
        if category != .all {
            button.widthAnchor.constraint(equalToConstant: Self.categoryButtonWidth).isActive = true
        }
    }

    private func configureTable() {
        tableView.dataSource = self
        tableView.delegate = self
        tableView.keyboardDismissMode = .onDrag
        tableView.register(GlobalSearchResultCell.self, forCellReuseIdentifier: GlobalSearchResultCell.reuseIdentifier)
        tableView.backgroundColor = .clear
        tableView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(tableView)

        emptyLabel.text = "输入关键词开始搜索"
        emptyLabel.textColor = .secondaryLabel
        emptyLabel.font = .systemFont(ofSize: 15, weight: .medium)
        emptyLabel.textAlignment = .center
        emptyLabel.numberOfLines = 2
        emptyLabel.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(emptyLabel)

        NSLayoutConstraint.activate([
            tableView.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
            tableView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            tableView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            tableView.bottomAnchor.constraint(equalTo: view.bottomAnchor),
            emptyLabel.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            emptyLabel.centerYAnchor.constraint(equalTo: view.centerYAnchor),
            emptyLabel.leadingAnchor.constraint(greaterThanOrEqualTo: view.leadingAnchor, constant: 30),
            emptyLabel.trailingAnchor.constraint(lessThanOrEqualTo: view.trailingAnchor, constant: -30)
        ])
    }

    private func applyFilter() {
        if isLoadingIndex {
            visibleResults = []
            emptyLabel.isHidden = false
            emptyLabel.text = "正在加载搜索索引..."
            tableView.reloadData()
            return
        }
        let trimmedQuery = query.trimmingCharacters(in: .whitespacesAndNewlines)
        let lowercasedQuery = trimmedQuery.lowercased()
        visibleResults = allResults.filter { result in
            let matchesCategory = selectedCategory == .all || result.category == selectedCategory
            guard matchesCategory else { return false }
            guard !lowercasedQuery.isEmpty else { return true }
            return [
                result.title,
                result.subtitle,
                result.snippet,
                result.detail,
                result.category.title
            ]
                .joined(separator: " ")
                .lowercased()
                .contains(lowercasedQuery)
        }
        let hasResults = !visibleResults.isEmpty
        emptyLabel.isHidden = hasResults
        emptyLabel.text = trimmedQuery.isEmpty ? "输入关键词开始搜索" : "没有找到相关结果"
        tableView.reloadData()
    }

    @objc private func categoryButtonTapped(_ sender: UIButton) {
        selectedCategory = GlobalSearchCategory(rawValue: sender.tag) ?? .all
        updateCategoryButtons()
        applyFilter()
    }

    private func updateCategoryButtons() {
        for (category, button) in categoryButtons {
            let isSelected = category == selectedCategory
            var configuration = button.configuration ?? .plain()
            var title = AttributedString(category.title)
            title.font = .systemFont(ofSize: 14, weight: .semibold)
            title.foregroundColor = isSelected ? Self.categorySelectedTextColor : Self.categoryNormalTextColor
            configuration.attributedTitle = title
            configuration.baseForegroundColor = isSelected ? Self.categorySelectedTextColor : Self.categoryNormalTextColor
            configuration.baseBackgroundColor = isSelected ? Self.categorySelectedBackgroundColor : Self.categoryNormalBackgroundColor
            configuration.contentInsets = NSDirectionalEdgeInsets(top: 0, leading: 12, bottom: 0, trailing: 12)
            configuration.cornerStyle = .capsule
            button.configuration = configuration
            button.layer.borderWidth = 1
            button.layer.borderColor = (isSelected ? Self.categorySelectedBorderColor : Self.categoryNormalBorderColor).cgColor
        }
    }

    func searchBar(_ searchBar: UISearchBar, textDidChange searchText: String) {
        query = searchText
        applyFilter()
    }

    func searchBarSearchButtonClicked(_ searchBar: UISearchBar) {
        searchBar.resignFirstResponder()
    }

    func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        visibleResults.count
    }

    func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = tableView.dequeueReusableCell(withIdentifier: GlobalSearchResultCell.reuseIdentifier, for: indexPath) as? GlobalSearchResultCell
            ?? GlobalSearchResultCell(style: .default, reuseIdentifier: GlobalSearchResultCell.reuseIdentifier)
        cell.configure(with: visibleResults[indexPath.row], query: query)
        return cell
    }

    func tableView(_ tableView: UITableView, heightForRowAt indexPath: IndexPath) -> CGFloat {
        92
    }

    func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        tableView.deselectRow(at: indexPath, animated: true)
        onSelectResult?(visibleResults[indexPath.row])
    }

    @objc private func close() {
        navigationController?.popViewController(animated: true)
    }
}

private final class GlobalSearchResultCell: UITableViewCell {
    static let reuseIdentifier = "GlobalSearchResultCell"

    private let iconContainer = UIView()
    private let iconView = UIImageView()
    private let titleLabel = UILabel()
    private let subtitleLabel = UILabel()
    private let snippetLabel = UILabel()
    private let badgeLabel = InsetLabel()

    override init(style: UITableViewCell.CellStyle, reuseIdentifier: String?) {
        super.init(style: style, reuseIdentifier: reuseIdentifier)
        configureViews()
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    private func configureViews() {
        selectionStyle = .none
        backgroundColor = .clear
        contentView.backgroundColor = UIColor.secondarySystemGroupedBackground
        contentView.layer.cornerRadius = 14
        contentView.layer.cornerCurve = .continuous

        iconContainer.layer.cornerRadius = 13
        iconContainer.layer.cornerCurve = .continuous
        iconContainer.translatesAutoresizingMaskIntoConstraints = false
        contentView.addSubview(iconContainer)

        iconView.tintColor = .white
        iconView.contentMode = .scaleAspectFit
        iconView.translatesAutoresizingMaskIntoConstraints = false
        iconContainer.addSubview(iconView)

        titleLabel.font = .systemFont(ofSize: 15, weight: .semibold)
        titleLabel.textColor = .label
        titleLabel.lineBreakMode = .byTruncatingTail
        titleLabel.translatesAutoresizingMaskIntoConstraints = false

        badgeLabel.font = .systemFont(ofSize: 11, weight: .semibold)
        badgeLabel.textAlignment = .center
        badgeLabel.textInsets = UIEdgeInsets(top: 3, left: 8, bottom: 3, right: 8)
        badgeLabel.layer.cornerRadius = 9
        badgeLabel.layer.cornerCurve = .continuous
        badgeLabel.clipsToBounds = true
        badgeLabel.translatesAutoresizingMaskIntoConstraints = false

        subtitleLabel.font = .systemFont(ofSize: 12, weight: .medium)
        subtitleLabel.textColor = .secondaryLabel
        subtitleLabel.lineBreakMode = .byTruncatingTail
        subtitleLabel.translatesAutoresizingMaskIntoConstraints = false

        snippetLabel.font = .systemFont(ofSize: 12)
        snippetLabel.textColor = .tertiaryLabel
        snippetLabel.numberOfLines = 1
        snippetLabel.lineBreakMode = .byTruncatingTail
        snippetLabel.translatesAutoresizingMaskIntoConstraints = false

        [titleLabel, badgeLabel, subtitleLabel, snippetLabel].forEach(contentView.addSubview)

        NSLayoutConstraint.activate([
            iconContainer.leadingAnchor.constraint(equalTo: contentView.leadingAnchor, constant: 14),
            iconContainer.centerYAnchor.constraint(equalTo: contentView.centerYAnchor),
            iconContainer.widthAnchor.constraint(equalToConstant: 42),
            iconContainer.heightAnchor.constraint(equalToConstant: 42),

            iconView.centerXAnchor.constraint(equalTo: iconContainer.centerXAnchor),
            iconView.centerYAnchor.constraint(equalTo: iconContainer.centerYAnchor),
            iconView.widthAnchor.constraint(equalToConstant: 21),
            iconView.heightAnchor.constraint(equalToConstant: 21),

            titleLabel.topAnchor.constraint(equalTo: contentView.topAnchor, constant: 13),
            titleLabel.leadingAnchor.constraint(equalTo: iconContainer.trailingAnchor, constant: 12),
            badgeLabel.leadingAnchor.constraint(greaterThanOrEqualTo: titleLabel.trailingAnchor, constant: 8),
            badgeLabel.trailingAnchor.constraint(equalTo: contentView.trailingAnchor, constant: -14),
            badgeLabel.centerYAnchor.constraint(equalTo: titleLabel.centerYAnchor),
            badgeLabel.widthAnchor.constraint(lessThanOrEqualToConstant: 78),

            subtitleLabel.topAnchor.constraint(equalTo: titleLabel.bottomAnchor, constant: 6),
            subtitleLabel.leadingAnchor.constraint(equalTo: titleLabel.leadingAnchor),
            subtitleLabel.trailingAnchor.constraint(equalTo: contentView.trailingAnchor, constant: -14),

            snippetLabel.topAnchor.constraint(equalTo: subtitleLabel.bottomAnchor, constant: 5),
            snippetLabel.leadingAnchor.constraint(equalTo: titleLabel.leadingAnchor),
            snippetLabel.trailingAnchor.constraint(equalTo: contentView.trailingAnchor, constant: -14),
            snippetLabel.bottomAnchor.constraint(lessThanOrEqualTo: contentView.bottomAnchor, constant: -10)
        ])
    }

    func configure(with result: GlobalSearchResult, query: String) {
        iconContainer.backgroundColor = result.category.tintColor
        iconView.image = UIImage(systemName: result.category.symbolName)
        titleLabel.attributedText = highlighted(result.title, query: query, font: .systemFont(ofSize: 15, weight: .semibold), color: .label)
        subtitleLabel.text = result.subtitle
        snippetLabel.attributedText = highlighted(result.snippet, query: query, font: .systemFont(ofSize: 12), color: .tertiaryLabel)
        badgeLabel.text = result.category.title
        badgeLabel.textColor = result.category.tintColor
        badgeLabel.backgroundColor = result.category.tintColor.withAlphaComponent(0.12)
    }

    private func highlighted(_ text: String, query: String, font: UIFont, color: UIColor) -> NSAttributedString {
        let attributed = NSMutableAttributedString(string: text, attributes: [.font: font, .foregroundColor: color])
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty,
              let range = text.range(of: trimmed, options: [.caseInsensitive, .diacriticInsensitive])
        else { return attributed }
        attributed.addAttributes(
            [
                .foregroundColor: UIColor.systemBlue,
                .backgroundColor: UIColor.systemBlue.withAlphaComponent(0.12)
            ],
            range: NSRange(range, in: text)
        )
        return attributed
    }
}

final class GlobalSearchDetailViewController: UIViewController {
    var onOpenLink: ((String) -> Void)?

    private let result: GlobalSearchResult

    init(result: GlobalSearchResult) {
        self.result = result
        super.init(nibName: nil, bundle: nil)
        title = result.category.title
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = UIColor.systemGroupedBackground
        navigationItem.leftBarButtonItem = UIBarButtonItem(title: "返回", style: .plain, target: self, action: #selector(close))
        configure()
    }

    private func configure() {
        let scrollView = UIScrollView()
        scrollView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(scrollView)

        let stack = UIStackView()
        stack.axis = .vertical
        stack.spacing = 14
        stack.translatesAutoresizingMaskIntoConstraints = false
        scrollView.addSubview(stack)

        let card = UIView()
        card.backgroundColor = .secondarySystemGroupedBackground
        card.layer.cornerRadius = 18
        card.layer.cornerCurve = .continuous

        let iconContainer = UIView()
        iconContainer.backgroundColor = result.category.tintColor
        iconContainer.layer.cornerRadius = 16
        iconContainer.layer.cornerCurve = .continuous
        iconContainer.translatesAutoresizingMaskIntoConstraints = false

        let icon = UIImageView(image: UIImage(systemName: result.category.symbolName))
        icon.tintColor = .white
        icon.contentMode = .scaleAspectFit
        icon.translatesAutoresizingMaskIntoConstraints = false
        iconContainer.addSubview(icon)

        let titleLabel = UILabel()
        titleLabel.text = result.title
        titleLabel.font = .systemFont(ofSize: 22, weight: .bold)
        titleLabel.textColor = .label
        titleLabel.numberOfLines = 0
        titleLabel.translatesAutoresizingMaskIntoConstraints = false

        let subtitleLabel = UILabel()
        subtitleLabel.text = result.subtitle
        subtitleLabel.font = .systemFont(ofSize: 13, weight: .medium)
        subtitleLabel.textColor = .secondaryLabel
        subtitleLabel.numberOfLines = 0
        subtitleLabel.translatesAutoresizingMaskIntoConstraints = false

        let detailLabel = UILabel()
        detailLabel.text = [result.snippet, result.detail].filter { !$0.isEmpty }.joined(separator: "\n\n")
        detailLabel.font = .systemFont(ofSize: 15)
        detailLabel.textColor = .label
        detailLabel.numberOfLines = 0
        detailLabel.translatesAutoresizingMaskIntoConstraints = false

        let openLinkButton = UIButton(type: .system)
        openLinkButton.setTitle("打开相关链接", for: .normal)
        openLinkButton.titleLabel?.font = .systemFont(ofSize: 15, weight: .semibold)
        openLinkButton.backgroundColor = UIColor.systemBlue.withAlphaComponent(0.12)
        openLinkButton.tintColor = .systemBlue
        openLinkButton.layer.cornerRadius = 13
        openLinkButton.layer.cornerCurve = .continuous
        openLinkButton.addTarget(self, action: #selector(openFirstLink), for: .touchUpInside)
        openLinkButton.isHidden = firstURLText() == nil
        openLinkButton.translatesAutoresizingMaskIntoConstraints = false

        [iconContainer, titleLabel, subtitleLabel, detailLabel, openLinkButton].forEach(card.addSubview)
        card.translatesAutoresizingMaskIntoConstraints = false
        stack.addArrangedSubview(card)

        NSLayoutConstraint.activate([
            scrollView.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
            scrollView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            scrollView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            scrollView.bottomAnchor.constraint(equalTo: view.bottomAnchor),

            stack.topAnchor.constraint(equalTo: scrollView.contentLayoutGuide.topAnchor, constant: 16),
            stack.leadingAnchor.constraint(equalTo: scrollView.frameLayoutGuide.leadingAnchor, constant: 16),
            stack.trailingAnchor.constraint(equalTo: scrollView.frameLayoutGuide.trailingAnchor, constant: -16),
            stack.bottomAnchor.constraint(equalTo: scrollView.contentLayoutGuide.bottomAnchor, constant: -24),

            iconContainer.topAnchor.constraint(equalTo: card.topAnchor, constant: 18),
            iconContainer.leadingAnchor.constraint(equalTo: card.leadingAnchor, constant: 18),
            iconContainer.widthAnchor.constraint(equalToConstant: 52),
            iconContainer.heightAnchor.constraint(equalToConstant: 52),
            icon.centerXAnchor.constraint(equalTo: iconContainer.centerXAnchor),
            icon.centerYAnchor.constraint(equalTo: iconContainer.centerYAnchor),
            icon.widthAnchor.constraint(equalToConstant: 26),
            icon.heightAnchor.constraint(equalToConstant: 26),

            titleLabel.topAnchor.constraint(equalTo: card.topAnchor, constant: 18),
            titleLabel.leadingAnchor.constraint(equalTo: iconContainer.trailingAnchor, constant: 14),
            titleLabel.trailingAnchor.constraint(equalTo: card.trailingAnchor, constant: -18),

            subtitleLabel.topAnchor.constraint(equalTo: titleLabel.bottomAnchor, constant: 7),
            subtitleLabel.leadingAnchor.constraint(equalTo: titleLabel.leadingAnchor),
            subtitleLabel.trailingAnchor.constraint(equalTo: titleLabel.trailingAnchor),

            detailLabel.topAnchor.constraint(equalTo: iconContainer.bottomAnchor, constant: 18),
            detailLabel.leadingAnchor.constraint(equalTo: card.leadingAnchor, constant: 18),
            detailLabel.trailingAnchor.constraint(equalTo: card.trailingAnchor, constant: -18),

            openLinkButton.topAnchor.constraint(equalTo: detailLabel.bottomAnchor, constant: 16),
            openLinkButton.leadingAnchor.constraint(equalTo: card.leadingAnchor, constant: 18),
            openLinkButton.trailingAnchor.constraint(equalTo: card.trailingAnchor, constant: -18),
            openLinkButton.heightAnchor.constraint(equalToConstant: 46),
            openLinkButton.bottomAnchor.constraint(equalTo: card.bottomAnchor, constant: -18)
        ])
    }

    private func firstURLText() -> String? {
        let text = [result.title, result.subtitle, result.snippet, result.detail].joined(separator: " ")
        let pattern = #"(?i)\b((?:https?://|www\.)[A-Za-z0-9\-._~:/?#\[\]@!$&'()*+,;=%]+)"#
        guard let regex = try? NSRegularExpression(pattern: pattern) else { return nil }
        let range = NSRange(text.startIndex..<text.endIndex, in: text)
        guard let match = regex.firstMatch(in: text, range: range),
              let matchRange = Range(match.range(at: 1), in: text)
        else { return nil }
        return String(text[matchRange])
    }

    @objc private func openFirstLink() {
        guard let urlText = firstURLText() else { return }
        onOpenLink?(urlText)
    }

    @objc private func close() {
        navigationController?.popViewController(animated: true)
    }
}

final class HiddenUsersViewController: UIViewController, UITableViewDataSource, UITableViewDelegate {
    var onRestore: ((UUID) -> Void)?

    private var participants: [ChatParticipant]
    private var remarks: [UUID: String]
    private let groupIDs: Set<UUID>
    private let tableView = UITableView(frame: .zero, style: .insetGrouped)
    private let emptyLabel = UILabel()

    init(participants: [ChatParticipant], remarks: [UUID: String], groupIDs: Set<UUID>) {
        self.participants = participants
        self.remarks = remarks
        self.groupIDs = groupIDs
        super.init(nibName: nil, bundle: nil)
        title = "隐藏用户"
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .systemGroupedBackground
        navigationItem.leftBarButtonItem = UIBarButtonItem(title: "返回", style: .plain, target: self, action: #selector(close))

        tableView.dataSource = self
        tableView.delegate = self
        tableView.register(HiddenUserCell.self, forCellReuseIdentifier: HiddenUserCell.reuseIdentifier)
        tableView.backgroundColor = .clear
        tableView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(tableView)

        emptyLabel.text = "暂无隐藏用户"
        emptyLabel.font = .systemFont(ofSize: 15, weight: .medium)
        emptyLabel.textColor = .secondaryLabel
        emptyLabel.textAlignment = .center
        emptyLabel.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(emptyLabel)

        NSLayoutConstraint.activate([
            tableView.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
            tableView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            tableView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            tableView.bottomAnchor.constraint(equalTo: view.bottomAnchor),
            emptyLabel.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            emptyLabel.centerYAnchor.constraint(equalTo: view.centerYAnchor)
        ])
        updateEmptyState()
        showCircularPageLoading(title: "加载隐藏用户")
    }

    func update(participants: [ChatParticipant], remarks: [UUID: String]) {
        self.participants = participants
        self.remarks = remarks
        tableView.reloadData()
        updateEmptyState()
    }

    private func displayName(for participant: ChatParticipant) -> String {
        let remark = remarks[participant.id]?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        return remark.isEmpty ? participant.displayName : remark
    }

    private func updateEmptyState() {
        emptyLabel.isHidden = !participants.isEmpty
        tableView.isHidden = participants.isEmpty
    }

    func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        participants.count
    }

    func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = tableView.dequeueReusableCell(withIdentifier: HiddenUserCell.reuseIdentifier, for: indexPath) as? HiddenUserCell
            ?? HiddenUserCell(style: .default, reuseIdentifier: HiddenUserCell.reuseIdentifier)
        let participant = participants[indexPath.row]
        cell.configure(
            participant: participant,
            displayName: displayName(for: participant),
            kindText: groupIDs.contains(participant.id) ? "群聊" : "好友",
            onRestore: { [weak self] in
                self?.onRestore?(participant.id)
            }
        )
        return cell
    }

    func tableView(_ tableView: UITableView, heightForRowAt indexPath: IndexPath) -> CGFloat {
        76
    }

    @objc private func close() {
        navigationController?.popViewController(animated: true)
    }
}

private final class HiddenUserCell: UITableViewCell {
    static let reuseIdentifier = "HiddenUserCell"

    private let avatarLabel = UILabel()
    private let titleLabel = UILabel()
    private let subtitleLabel = UILabel()
    private let restoreButton = UIButton(type: .system)
    private var onRestore: (() -> Void)?

    override init(style: UITableViewCell.CellStyle, reuseIdentifier: String?) {
        super.init(style: style, reuseIdentifier: reuseIdentifier)
        configureViews()
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    private func configureViews() {
        selectionStyle = .none
        contentView.backgroundColor = UIColor.secondarySystemGroupedBackground

        avatarLabel.textAlignment = .center
        avatarLabel.textColor = .white
        avatarLabel.font = .systemFont(ofSize: 15, weight: .semibold)
        avatarLabel.layer.cornerRadius = 10
        avatarLabel.layer.cornerCurve = .continuous
        avatarLabel.clipsToBounds = true
        avatarLabel.translatesAutoresizingMaskIntoConstraints = false

        titleLabel.font = .systemFont(ofSize: 16, weight: .semibold)
        titleLabel.textColor = .label
        titleLabel.translatesAutoresizingMaskIntoConstraints = false

        subtitleLabel.font = .systemFont(ofSize: 12)
        subtitleLabel.textColor = .secondaryLabel
        subtitleLabel.translatesAutoresizingMaskIntoConstraints = false

        restoreButton.setTitle("恢复显示", for: .normal)
        restoreButton.titleLabel?.font = .systemFont(ofSize: 14, weight: .semibold)
        restoreButton.tintColor = .white
        restoreButton.backgroundColor = UIColor(red: 0.15, green: 0.52, blue: 0.36, alpha: 1)
        restoreButton.layer.cornerRadius = 16
        restoreButton.layer.cornerCurve = .continuous
        restoreButton.addTarget(self, action: #selector(restoreTapped), for: .touchUpInside)
        restoreButton.translatesAutoresizingMaskIntoConstraints = false

        [avatarLabel, titleLabel, subtitleLabel, restoreButton].forEach(contentView.addSubview)
        NSLayoutConstraint.activate([
            avatarLabel.leadingAnchor.constraint(equalTo: contentView.leadingAnchor, constant: 14),
            avatarLabel.centerYAnchor.constraint(equalTo: contentView.centerYAnchor),
            avatarLabel.widthAnchor.constraint(equalToConstant: 42),
            avatarLabel.heightAnchor.constraint(equalToConstant: 42),

            titleLabel.leadingAnchor.constraint(equalTo: avatarLabel.trailingAnchor, constant: 12),
            titleLabel.trailingAnchor.constraint(lessThanOrEqualTo: restoreButton.leadingAnchor, constant: -12),
            titleLabel.topAnchor.constraint(equalTo: contentView.topAnchor, constant: 17),

            subtitleLabel.leadingAnchor.constraint(equalTo: titleLabel.leadingAnchor),
            subtitleLabel.trailingAnchor.constraint(equalTo: titleLabel.trailingAnchor),
            subtitleLabel.topAnchor.constraint(equalTo: titleLabel.bottomAnchor, constant: 5),

            restoreButton.trailingAnchor.constraint(equalTo: contentView.trailingAnchor, constant: -14),
            restoreButton.centerYAnchor.constraint(equalTo: contentView.centerYAnchor),
            restoreButton.widthAnchor.constraint(equalToConstant: 82),
            restoreButton.heightAnchor.constraint(equalToConstant: 32)
        ])
    }

    func configure(participant: ChatParticipant, displayName: String, kindText: String, onRestore: @escaping () -> Void) {
        self.onRestore = onRestore
        avatarLabel.text = participant.initials
        avatarLabel.backgroundColor = participant.tintColor.withAlphaComponent(0.92)
        titleLabel.text = displayName
        subtitleLabel.text = displayName == participant.displayName
            ? "\(kindText) · 已隐藏"
            : "\(kindText) · 原名 \(participant.displayName)"
    }

    @objc private func restoreTapped() {
        onRestore?()
    }
}

final class AccessRequestReviewViewController: UIViewController, UITableViewDataSource, UITableViewDelegate {
    var onApprove: ((String) -> Void)?

    private var requests: [MediaAccessRequest]
    private let tableView = UITableView(frame: .zero, style: .insetGrouped)
    private let emptyLabel = UILabel()
    private let dateFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.dateFormat = "MM月dd日 HH:mm"
        return formatter
    }()

    init(requests: [MediaAccessRequest]) {
        self.requests = requests
        super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        title = "申请审核"
        view.backgroundColor = UIColor.systemGroupedBackground
        navigationItem.leftBarButtonItem = UIBarButtonItem(title: "返回", style: .plain, target: self, action: #selector(close))
        configureTable()
        configureEmptyState()
        updateEmptyState()
        showCircularPageLoading(title: "加载申请记录")
    }

    func updateRequests(_ requests: [MediaAccessRequest]) {
        self.requests = requests
        tableView.reloadData()
        updateEmptyState()
    }

    private func configureTable() {
        tableView.translatesAutoresizingMaskIntoConstraints = false
        tableView.dataSource = self
        tableView.delegate = self
        tableView.register(AccessRequestCell.self, forCellReuseIdentifier: AccessRequestCell.reuseIdentifier)
        view.addSubview(tableView)
        NSLayoutConstraint.activate([
            tableView.topAnchor.constraint(equalTo: view.topAnchor),
            tableView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            tableView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            tableView.bottomAnchor.constraint(equalTo: view.bottomAnchor)
        ])
    }

    private func configureEmptyState() {
        emptyLabel.text = "暂无查看申请"
        emptyLabel.font = .systemFont(ofSize: 15, weight: .medium)
        emptyLabel.textColor = .secondaryLabel
        emptyLabel.textAlignment = .center
        emptyLabel.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(emptyLabel)
        NSLayoutConstraint.activate([
            emptyLabel.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            emptyLabel.centerYAnchor.constraint(equalTo: view.centerYAnchor)
        ])
    }

    private func updateEmptyState() {
        emptyLabel.isHidden = !requests.isEmpty
        tableView.isHidden = requests.isEmpty
    }

    func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        requests.count
    }

    func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = tableView.dequeueReusableCell(withIdentifier: AccessRequestCell.reuseIdentifier, for: indexPath) as? AccessRequestCell
            ?? AccessRequestCell(style: .default, reuseIdentifier: AccessRequestCell.reuseIdentifier)
        let request = requests[indexPath.row]
        cell.configure(
            request: request,
            dateText: dateFormatter.string(from: request.createdAt),
            onApprove: { [weak self] in
                self?.onApprove?(request.key)
            }
        )
        return cell
    }

    func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        tableView.deselectRow(at: indexPath, animated: true)
        let request = requests[indexPath.row]
        let alert = UIAlertController(
            title: request.title,
            message: """
            \(request.kindTitle) · \(request.scope.rawValue)
            状态：\(request.status.rawValue)
            有效期：\(request.expiryText)
            分享链接：\(request.shareURL)
            原始资源：\(request.url)
            """,
            preferredStyle: .alert
        )
        alert.addAction(UIAlertAction(title: "确定", style: .default))
        present(alert, animated: true)
    }

    @objc private func close() {
        navigationController?.popViewController(animated: true)
    }
}

private final class AccessRequestCell: UITableViewCell {
    static let reuseIdentifier = "AccessRequestCell"

    private let iconView = UIImageView()
    private let titleLabel = UILabel()
    private let subtitleLabel = UILabel()
    private let urlLabel = UILabel()
    private let statusLabel = UILabel()
    private let approveButton = UIButton(type: .system)
    private var onApprove: (() -> Void)?

    override init(style: UITableViewCell.CellStyle, reuseIdentifier: String?) {
        super.init(style: style, reuseIdentifier: reuseIdentifier)
        configure()
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    private func configure() {
        selectionStyle = .none
        contentView.backgroundColor = UIColor.secondarySystemGroupedBackground

        iconView.contentMode = .scaleAspectFit
        iconView.tintColor = UIColor(red: 0.28, green: 0.36, blue: 0.78, alpha: 1)
        iconView.translatesAutoresizingMaskIntoConstraints = false

        titleLabel.font = .systemFont(ofSize: 16, weight: .semibold)
        titleLabel.textColor = .label
        titleLabel.numberOfLines = 2
        titleLabel.translatesAutoresizingMaskIntoConstraints = false

        subtitleLabel.font = .systemFont(ofSize: 13)
        subtitleLabel.textColor = .secondaryLabel
        subtitleLabel.numberOfLines = 2
        subtitleLabel.translatesAutoresizingMaskIntoConstraints = false

        urlLabel.font = .systemFont(ofSize: 12)
        urlLabel.textColor = UIColor(red: 0.12, green: 0.43, blue: 0.88, alpha: 1)
        urlLabel.numberOfLines = 2
        urlLabel.translatesAutoresizingMaskIntoConstraints = false

        statusLabel.font = .systemFont(ofSize: 12, weight: .semibold)
        statusLabel.textAlignment = .center
        statusLabel.layer.cornerRadius = 11
        statusLabel.layer.cornerCurve = .continuous
        statusLabel.clipsToBounds = true
        statusLabel.translatesAutoresizingMaskIntoConstraints = false

        approveButton.setTitle("同意", for: .normal)
        approveButton.titleLabel?.font = .systemFont(ofSize: 14, weight: .semibold)
        approveButton.tintColor = .white
        approveButton.backgroundColor = UIColor(red: 0.17, green: 0.56, blue: 0.34, alpha: 1)
        approveButton.layer.cornerRadius = 16
        approveButton.layer.cornerCurve = .continuous
        approveButton.addTarget(self, action: #selector(approveTapped), for: .touchUpInside)
        approveButton.translatesAutoresizingMaskIntoConstraints = false

        [iconView, titleLabel, subtitleLabel, urlLabel, statusLabel, approveButton].forEach(contentView.addSubview)

        NSLayoutConstraint.activate([
            iconView.leadingAnchor.constraint(equalTo: contentView.leadingAnchor, constant: 14),
            iconView.topAnchor.constraint(equalTo: contentView.topAnchor, constant: 16),
            iconView.widthAnchor.constraint(equalToConstant: 30),
            iconView.heightAnchor.constraint(equalToConstant: 30),

            titleLabel.leadingAnchor.constraint(equalTo: iconView.trailingAnchor, constant: 12),
            titleLabel.trailingAnchor.constraint(equalTo: statusLabel.leadingAnchor, constant: -10),
            titleLabel.topAnchor.constraint(equalTo: contentView.topAnchor, constant: 14),

            statusLabel.trailingAnchor.constraint(equalTo: contentView.trailingAnchor, constant: -14),
            statusLabel.centerYAnchor.constraint(equalTo: titleLabel.centerYAnchor),
            statusLabel.widthAnchor.constraint(equalToConstant: 58),
            statusLabel.heightAnchor.constraint(equalToConstant: 22),

            subtitleLabel.leadingAnchor.constraint(equalTo: titleLabel.leadingAnchor),
            subtitleLabel.trailingAnchor.constraint(equalTo: contentView.trailingAnchor, constant: -14),
            subtitleLabel.topAnchor.constraint(equalTo: titleLabel.bottomAnchor, constant: 6),

            urlLabel.leadingAnchor.constraint(equalTo: titleLabel.leadingAnchor),
            urlLabel.trailingAnchor.constraint(equalTo: approveButton.leadingAnchor, constant: -12),
            urlLabel.topAnchor.constraint(equalTo: subtitleLabel.bottomAnchor, constant: 8),
            urlLabel.bottomAnchor.constraint(equalTo: contentView.bottomAnchor, constant: -14),

            approveButton.trailingAnchor.constraint(equalTo: contentView.trailingAnchor, constant: -14),
            approveButton.centerYAnchor.constraint(equalTo: urlLabel.centerYAnchor),
            approveButton.widthAnchor.constraint(equalToConstant: 64),
            approveButton.heightAnchor.constraint(equalToConstant: 32)
        ])
    }

    func configure(request: MediaAccessRequest, dateText: String, onApprove: @escaping () -> Void) {
        self.onApprove = onApprove
        if request.kindTitle == "链接" {
            iconView.image = UIImage(systemName: "link")
        } else {
            iconView.image = UIImage(systemName: request.kindTitle == "图片" ? "photo" : "doc.text")
        }
        titleLabel.text = request.title
        let actionText = request.kindTitle == "链接" ? "申请访问" : "申请查看"
        subtitleLabel.text = "\(request.requesterName) \(actionText) · \(request.kindTitle) · \(request.scope.rawValue) · 有效期 \(request.expiryText) · \(dateText)"
        urlLabel.text = request.shareURL
        statusLabel.text = request.status.rawValue
        if request.status == .approved {
            statusLabel.textColor = UIColor(red: 0.16, green: 0.50, blue: 0.30, alpha: 1)
            statusLabel.backgroundColor = UIColor(red: 0.16, green: 0.68, blue: 0.36, alpha: 0.14)
            approveButton.isEnabled = false
            approveButton.alpha = 0.45
        } else {
            statusLabel.textColor = UIColor(red: 0.78, green: 0.38, blue: 0.12, alpha: 1)
            statusLabel.backgroundColor = UIColor(red: 0.94, green: 0.52, blue: 0.16, alpha: 0.14)
            approveButton.isEnabled = true
            approveButton.alpha = 1
        }
    }

    @objc private func approveTapped() {
        onApprove?()
    }
}

final class InsetLabel: UILabel {
    var textInsets = UIEdgeInsets.zero {
        didSet { invalidateIntrinsicContentSize() }
    }

    override func drawText(in rect: CGRect) {
        super.drawText(in: rect.inset(by: textInsets))
    }

    override var intrinsicContentSize: CGSize {
        let size = super.intrinsicContentSize
        return CGSize(
            width: size.width + textInsets.left + textInsets.right,
            height: size.height + textInsets.top + textInsets.bottom
        )
    }
}

