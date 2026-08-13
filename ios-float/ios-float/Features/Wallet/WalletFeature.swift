import UIKit

struct CouponWalletItem: Hashable {
    enum Category: String, CaseIterable {
        case membership = "会员卡"
        case transport = "交通卡"
        case voucher = "券和礼品卡"
        case ticket = "票证"

        var symbolName: String {
            switch self {
            case .membership: return "crown"
            case .transport: return "bus"
            case .voucher: return "ticket"
            case .ticket: return "doc.text"
            }
        }

        var tintColor: UIColor {
            switch self {
            case .membership: return UIColor(red: 0.94, green: 0.74, blue: 0.16, alpha: 1)
            case .transport: return UIColor(red: 0.10, green: 0.78, blue: 0.42, alpha: 1)
            case .voucher: return UIColor(red: 0.92, green: 0.55, blue: 0.20, alpha: 1)
            case .ticket: return UIColor(red: 0.14, green: 0.58, blue: 0.82, alpha: 1)
            }
        }
    }

    let id = UUID()
    let category: Category
    let title: String
    let subtitle: String
    let badgeText: String?
    let iconText: String
    let colors: [UIColor]
    let isInvoiceEntry: Bool

    var messageDetail: String {
        [
            "卡包类型：\(category.rawValue)",
            subtitle.isEmpty ? nil : subtitle,
            badgeText.map { "状态：\($0)" },
            "有效期：2026.07.06-2026.12.31",
            "可在卡包页面查看详情或分享"
        ]
            .compactMap { $0 }
            .joined(separator: "\n")
    }

    static let demoItems: [CouponWalletItem] = [
        CouponWalletItem(category: .membership, title: "麦当劳会员卡", subtitle: "积分0 | 余额¥0.00", badgeText: "附近可用", iconText: "M", colors: [UIColor(red: 0.75, green: 0.58, blue: 0.16, alpha: 1), UIColor(red: 0.91, green: 0.76, blue: 0.30, alpha: 1)], isInvoiceEntry: false),
        CouponWalletItem(category: .membership, title: "天虹集团会员卡", subtitle: "积分236", badgeText: nil, iconText: "天虹", colors: [UIColor(red: 0.69, green: 0.64, blue: 0.55, alpha: 1), UIColor(red: 0.88, green: 0.65, blue: 0.58, alpha: 1)], isInvoiceEntry: false),
        CouponWalletItem(category: .membership, title: "赵一鸣零食会员卡", subtitle: "", badgeText: nil, iconText: "鸣", colors: [UIColor(red: 0.84, green: 0.02, blue: 0.04, alpha: 1), UIColor(red: 0.68, green: 0.00, blue: 0.04, alpha: 1)], isInvoiceEntry: false),
        CouponWalletItem(category: .membership, title: "蛙好厨炭烧牛蛙会员卡", subtitle: "积分0", badgeText: nil, iconText: "蛙", colors: [UIColor(red: 0.68, green: 0.19, blue: 0.18, alpha: 1), UIColor(red: 0.78, green: 0.36, blue: 0.32, alpha: 1)], isInvoiceEntry: false),
        CouponWalletItem(category: .membership, title: "拼多多会员卡", subtitle: "积分0", badgeText: nil, iconText: "拼", colors: [UIColor(red: 0.76, green: 0.02, blue: 0.04, alpha: 1), UIColor(red: 0.88, green: 0.14, blue: 0.08, alpha: 1)], isInvoiceEntry: false),
        CouponWalletItem(category: .membership, title: "美宜佳会员卡", subtitle: "", badgeText: nil, iconText: "MJ", colors: [UIColor(red: 0.75, green: 0.00, blue: 0.04, alpha: 1), UIColor(red: 0.62, green: 0.00, blue: 0.03, alpha: 1)], isInvoiceEntry: false),
        CouponWalletItem(category: .membership, title: "阿迪达斯会员卡", subtitle: "", badgeText: nil, iconText: "ad", colors: [UIColor(red: 0.10, green: 0.10, blue: 0.10, alpha: 1), UIColor(red: 0.18, green: 0.18, blue: 0.18, alpha: 1)], isInvoiceEntry: false),
        CouponWalletItem(category: .ticket, title: "发票", subtitle: "共3张", badgeText: nil, iconText: "票", colors: [.white, .white], isInvoiceEntry: true),
        CouponWalletItem(category: .ticket, title: "人力资源和社会保障部", subtitle: "电子社保卡", badgeText: nil, iconText: "社保", colors: [UIColor(red: 0.30, green: 0.65, blue: 0.74, alpha: 1), UIColor(red: 0.38, green: 0.78, blue: 0.88, alpha: 1)], isInvoiceEntry: false),
        CouponWalletItem(category: .ticket, title: "医保电子凭证", subtitle: "国家医疗保障局监制", badgeText: nil, iconText: "CHS", colors: [UIColor(red: 0.12, green: 0.32, blue: 0.68, alpha: 1), UIColor(red: 0.10, green: 0.48, blue: 0.86, alpha: 1)], isInvoiceEntry: false),
        CouponWalletItem(category: .ticket, title: "深圳信息职业技术学院", subtitle: "学生卡", badgeText: nil, iconText: "SZI", colors: [UIColor(red: 0.62, green: 0.45, blue: 0.00, alpha: 1), UIColor(red: 0.86, green: 0.65, blue: 0.08, alpha: 1)], isInvoiceEntry: false),
        CouponWalletItem(category: .transport, title: "深圳通交通卡", subtitle: "余额¥36.20", badgeText: "可刷码", iconText: "巴士", colors: [UIColor(red: 0.05, green: 0.55, blue: 0.42, alpha: 1), UIColor(red: 0.20, green: 0.78, blue: 0.56, alpha: 1)], isInvoiceEntry: false),
        CouponWalletItem(category: .voucher, title: "饮品兑换券", subtitle: "满30减10 · 来福士B1", badgeText: "今日可用", iconText: "券", colors: [UIColor(red: 0.93, green: 0.50, blue: 0.18, alpha: 1), UIColor(red: 0.97, green: 0.72, blue: 0.28, alpha: 1)], isInvoiceEntry: false)
    ]
}

private func couponInsetWrapper(_ content: UIView, horizontal: CGFloat = 18) -> UIView {
    let wrapper = UIView()
    wrapper.backgroundColor = .clear
    wrapper.translatesAutoresizingMaskIntoConstraints = false
    wrapper.layer.shadowColor = UIColor.black.withAlphaComponent(0.10).cgColor
    wrapper.layer.shadowOpacity = 1
    wrapper.layer.shadowRadius = 7
    wrapper.layer.shadowOffset = CGSize(width: 0, height: 3)
    content.translatesAutoresizingMaskIntoConstraints = false
    wrapper.addSubview(content)
    NSLayoutConstraint.activate([
        content.topAnchor.constraint(equalTo: wrapper.topAnchor),
        content.leadingAnchor.constraint(equalTo: wrapper.leadingAnchor, constant: horizontal),
        content.trailingAnchor.constraint(equalTo: wrapper.trailingAnchor, constant: -horizontal),
        content.bottomAnchor.constraint(equalTo: wrapper.bottomAnchor)
    ])
    return wrapper
}

final class CouponWalletViewController: UIViewController {
    var onSelectItem: ((CouponWalletItem) -> Void)?

    private let items: [CouponWalletItem]
    private let scrollView = UIScrollView()
    private let stackView = UIStackView()

    init(items: [CouponWalletItem]) {
        self.items = items
        super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        title = "卡包"
        view.backgroundColor = UIColor(red: 0.94, green: 0.94, blue: 0.94, alpha: 1)
        navigationItem.leftBarButtonItem = UIBarButtonItem(title: "返回", style: .plain, target: self, action: #selector(close))
        navigationItem.rightBarButtonItem = UIBarButtonItem(image: UIImage(systemName: "ellipsis"), style: .plain, target: nil, action: nil)
        configureLayout()
        buildContent()
    }

    private func configureLayout() {
        scrollView.alwaysBounceVertical = true
        scrollView.translatesAutoresizingMaskIntoConstraints = false
        stackView.axis = .vertical
        stackView.spacing = 0
        stackView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(scrollView)
        scrollView.addSubview(stackView)
        NSLayoutConstraint.activate([
            scrollView.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
            scrollView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            scrollView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            scrollView.bottomAnchor.constraint(equalTo: view.bottomAnchor),
            stackView.topAnchor.constraint(equalTo: scrollView.contentLayoutGuide.topAnchor),
            stackView.leadingAnchor.constraint(equalTo: scrollView.frameLayoutGuide.leadingAnchor),
            stackView.trailingAnchor.constraint(equalTo: scrollView.frameLayoutGuide.trailingAnchor),
            stackView.bottomAnchor.constraint(equalTo: scrollView.contentLayoutGuide.bottomAnchor)
        ])
    }

    private func buildContent() {
        let firstGroup = makeGroupView(categories: [.membership, .transport])
        let secondGroup = makeGroupView(categories: [.voucher, .ticket])
        stackView.addArrangedSubview(firstGroup)
        stackView.addArrangedSubview(makeSpacer(height: 10))
        stackView.addArrangedSubview(secondGroup)
        stackView.addArrangedSubview(makeSectionLabel("最近使用"))

        let recentItems = Array(items.filter { $0.category == .membership }.prefix(2))
        recentItems.enumerated().forEach { index, item in
            let card = CouponWalletCardView(item: item, compact: true)
            card.addTarget(self, action: #selector(cardTapped(_:)), for: .touchUpInside)
            stackView.addArrangedSubview(couponInsetWrapper(card))
            if index < recentItems.count - 1 {
                stackView.addArrangedSubview(makeSpacer(height: 12))
            }
        }
        stackView.addArrangedSubview(makeSpacer(height: 28))
    }

    private func makeGroupView(categories: [CouponWalletItem.Category]) -> UIView {
        let container = UIStackView()
        container.axis = .vertical
        container.spacing = 0
        container.backgroundColor = .white
        container.translatesAutoresizingMaskIntoConstraints = false
        categories.enumerated().forEach { index, category in
            let row = CouponCategoryRow(category: category)
            row.addTarget(self, action: #selector(categoryTapped(_:)), for: .touchUpInside)
            row.tag = CouponWalletItem.Category.allCases.firstIndex(of: category) ?? 0
            container.addArrangedSubview(row)
            if index < categories.count - 1 {
                let divider = UIView()
                divider.backgroundColor = UIColor.black.withAlphaComponent(0.07)
                divider.heightAnchor.constraint(equalToConstant: 1 / UIScreen.main.scale).isActive = true
                let dividerWrapper = UIView()
                dividerWrapper.backgroundColor = .white
                dividerWrapper.addSubview(divider)
                divider.translatesAutoresizingMaskIntoConstraints = false
                NSLayoutConstraint.activate([
                    divider.leadingAnchor.constraint(equalTo: dividerWrapper.leadingAnchor, constant: 70),
                    divider.trailingAnchor.constraint(equalTo: dividerWrapper.trailingAnchor),
                    divider.topAnchor.constraint(equalTo: dividerWrapper.topAnchor),
                    divider.bottomAnchor.constraint(equalTo: dividerWrapper.bottomAnchor),
                    dividerWrapper.heightAnchor.constraint(equalToConstant: 1 / UIScreen.main.scale)
                ])
                container.addArrangedSubview(dividerWrapper)
            }
        }
        return container
    }

    private func makeSectionLabel(_ text: String) -> UIView {
        let container = UIView()
        container.backgroundColor = .clear
        container.translatesAutoresizingMaskIntoConstraints = false
        container.heightAnchor.constraint(equalToConstant: 52).isActive = true
        let label = UILabel()
        label.text = text
        label.textColor = UIColor(red: 0.62, green: 0.62, blue: 0.64, alpha: 1)
        label.font = .systemFont(ofSize: 15, weight: .regular)
        label.translatesAutoresizingMaskIntoConstraints = false
        container.addSubview(label)
        NSLayoutConstraint.activate([
            label.leadingAnchor.constraint(equalTo: container.leadingAnchor, constant: 20),
            label.trailingAnchor.constraint(equalTo: container.trailingAnchor, constant: -20),
            label.bottomAnchor.constraint(equalTo: container.bottomAnchor, constant: -10)
        ])
        return container
    }

    private func makeSpacer(height: CGFloat) -> UIView {
        let view = UIView()
        view.backgroundColor = .clear
        view.heightAnchor.constraint(equalToConstant: height).isActive = true
        return view
    }

    @objc private func categoryTapped(_ sender: UIControl) {
        let category = CouponWalletItem.Category.allCases[safe: sender.tag] ?? .membership
        let controller = CouponWalletListViewController(
            category: category,
            items: items.filter { $0.category == category }
        )
        controller.onSelectItem = onSelectItem
        navigationController?.pushViewController(controller, animated: true)
    }

    @objc private func cardTapped(_ sender: CouponWalletCardView) {
        onSelectItem?(sender.item)
    }

    @objc private func close() {
        navigationController?.popViewController(animated: true)
    }
}

private final class CouponWalletListViewController: UIViewController {
    var onSelectItem: ((CouponWalletItem) -> Void)?

    private let category: CouponWalletItem.Category
    private let items: [CouponWalletItem]
    private let scrollView = UIScrollView()
    private let stackView = UIStackView()

    init(category: CouponWalletItem.Category, items: [CouponWalletItem]) {
        self.category = category
        self.items = items
        super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        title = category.rawValue
        view.backgroundColor = UIColor(red: 0.94, green: 0.94, blue: 0.94, alpha: 1)
        navigationItem.leftBarButtonItem = UIBarButtonItem(title: "返回", style: .plain, target: self, action: #selector(close))
        navigationItem.rightBarButtonItem = UIBarButtonItem(image: UIImage(systemName: "ellipsis"), style: .plain, target: nil, action: nil)
        configureLayout()
        buildContent()
    }

    private func configureLayout() {
        scrollView.alwaysBounceVertical = true
        scrollView.translatesAutoresizingMaskIntoConstraints = false
        stackView.axis = .vertical
        stackView.spacing = 12
        stackView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(scrollView)
        scrollView.addSubview(stackView)
        NSLayoutConstraint.activate([
            scrollView.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
            scrollView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            scrollView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            scrollView.bottomAnchor.constraint(equalTo: view.bottomAnchor),
            stackView.topAnchor.constraint(equalTo: scrollView.contentLayoutGuide.topAnchor, constant: 16),
            stackView.leadingAnchor.constraint(equalTo: scrollView.frameLayoutGuide.leadingAnchor),
            stackView.trailingAnchor.constraint(equalTo: scrollView.frameLayoutGuide.trailingAnchor),
            stackView.bottomAnchor.constraint(equalTo: scrollView.contentLayoutGuide.bottomAnchor, constant: -28)
        ])
    }

    private func buildContent() {
        if category == .membership {
            let sortLabel = UILabel()
            sortLabel.text = "按距离排序⌄"
            sortLabel.textAlignment = .center
            sortLabel.textColor = UIColor(red: 0.52, green: 0.52, blue: 0.54, alpha: 1)
            sortLabel.font = .systemFont(ofSize: 15, weight: .medium)
            sortLabel.heightAnchor.constraint(equalToConstant: 36).isActive = true
            stackView.addArrangedSubview(sortLabel)
        }
        if category == .ticket {
            addTicketSections()
            return
        }
        items.forEach(addCard)
    }

    private func addTicketSections() {
        let invoiceItems = items.filter(\.isInvoiceEntry)
        if !invoiceItems.isEmpty {
            stackView.addArrangedSubview(sectionLabel("票据"))
            invoiceItems.forEach(addInvoiceRow)
        }
        let documentItems = items.filter { !$0.isInvoiceEntry }
        if !documentItems.isEmpty {
            stackView.addArrangedSubview(sectionLabel("证件"))
            documentItems.forEach(addCard)
        }
    }

    private func sectionLabel(_ text: String) -> UIView {
        let container = UIView()
        container.backgroundColor = .clear
        container.translatesAutoresizingMaskIntoConstraints = false
        container.heightAnchor.constraint(equalToConstant: 34).isActive = true
        let label = UILabel()
        label.text = text
        label.textColor = UIColor(red: 0.42, green: 0.42, blue: 0.44, alpha: 1)
        label.font = .systemFont(ofSize: 15, weight: .medium)
        label.translatesAutoresizingMaskIntoConstraints = false
        container.addSubview(label)
        NSLayoutConstraint.activate([
            label.leadingAnchor.constraint(equalTo: container.leadingAnchor, constant: 20),
            label.trailingAnchor.constraint(equalTo: container.trailingAnchor, constant: -20),
            label.bottomAnchor.constraint(equalTo: container.bottomAnchor, constant: -6)
        ])
        return container
    }

    private func addCard(_ item: CouponWalletItem) {
        let card = CouponWalletCardView(item: item, compact: false)
        card.addTarget(self, action: #selector(cardTapped(_:)), for: .touchUpInside)
        stackView.addArrangedSubview(couponInsetWrapper(card))
    }

    private func addInvoiceRow(_ item: CouponWalletItem) {
        let row = CouponInvoiceRow(item: item)
        row.addTarget(self, action: #selector(invoiceTapped(_:)), for: .touchUpInside)
        stackView.addArrangedSubview(couponInsetWrapper(row))
    }

    @objc private func cardTapped(_ sender: CouponWalletCardView) {
        onSelectItem?(sender.item)
    }

    @objc private func invoiceTapped(_ sender: CouponInvoiceRow) {
        onSelectItem?(sender.item)
    }

    @objc private func close() {
        navigationController?.popViewController(animated: true)
    }
}

private final class CouponCategoryRow: UIControl {
    init(category: CouponWalletItem.Category) {
        super.init(frame: .zero)
        backgroundColor = .white
        translatesAutoresizingMaskIntoConstraints = false
        heightAnchor.constraint(equalToConstant: 72).isActive = true

        let icon = UIImageView(image: UIImage(systemName: category.symbolName))
        icon.tintColor = category.tintColor
        icon.contentMode = .scaleAspectFit
        icon.translatesAutoresizingMaskIntoConstraints = false
        let title = UILabel()
        title.text = category.rawValue
        title.font = .systemFont(ofSize: 22, weight: .regular)
        title.textColor = UIColor(red: 0.10, green: 0.10, blue: 0.11, alpha: 1)
        title.translatesAutoresizingMaskIntoConstraints = false
        let chevron = UIImageView(image: UIImage(systemName: "chevron.right"))
        chevron.tintColor = UIColor(red: 0.78, green: 0.78, blue: 0.80, alpha: 1)
        chevron.contentMode = .scaleAspectFit
        chevron.translatesAutoresizingMaskIntoConstraints = false
        [icon, title, chevron].forEach(addSubview)
        NSLayoutConstraint.activate([
            icon.leadingAnchor.constraint(equalTo: leadingAnchor, constant: 20),
            icon.centerYAnchor.constraint(equalTo: centerYAnchor),
            icon.widthAnchor.constraint(equalToConstant: 28),
            icon.heightAnchor.constraint(equalToConstant: 28),
            title.leadingAnchor.constraint(equalTo: icon.trailingAnchor, constant: 22),
            title.centerYAnchor.constraint(equalTo: centerYAnchor),
            title.trailingAnchor.constraint(lessThanOrEqualTo: chevron.leadingAnchor, constant: -12),
            chevron.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -22),
            chevron.centerYAnchor.constraint(equalTo: centerYAnchor),
            chevron.widthAnchor.constraint(equalToConstant: 16),
            chevron.heightAnchor.constraint(equalToConstant: 20)
        ])
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
}

private final class CouponWalletCardView: UIControl {
    let item: CouponWalletItem
    private let gradientLayer = CAGradientLayer()

    init(item: CouponWalletItem, compact: Bool) {
        self.item = item
        super.init(frame: .zero)
        backgroundColor = .clear
        layer.cornerRadius = 10
        layer.cornerCurve = .continuous
        clipsToBounds = true
        translatesAutoresizingMaskIntoConstraints = false
        heightAnchor.constraint(equalToConstant: compact ? 116 : 122).isActive = true
        layoutMargins = UIEdgeInsets(top: 0, left: 18, bottom: 0, right: 18)

        gradientLayer.colors = item.colors.map(\.cgColor)
        gradientLayer.startPoint = CGPoint(x: 0, y: 0.5)
        gradientLayer.endPoint = CGPoint(x: 1, y: 0.5)
        layer.insertSublayer(gradientLayer, at: 0)

        let logo = UILabel()
        logo.text = item.iconText
        logo.textAlignment = .center
        logo.textColor = item.colors.first == UIColor.white ? item.category.tintColor : .white
        logo.font = .systemFont(ofSize: item.iconText.count > 2 ? 11 : 18, weight: .bold)
        logo.backgroundColor = .white
        logo.layer.cornerRadius = 29
        logo.layer.borderWidth = 1
        logo.layer.borderColor = UIColor.white.withAlphaComponent(0.75).cgColor
        logo.clipsToBounds = true
        logo.translatesAutoresizingMaskIntoConstraints = false

        let title = UILabel()
        title.text = item.title
        title.font = .systemFont(ofSize: compact ? 20 : 21, weight: .bold)
        title.textColor = .white
        title.numberOfLines = 1
        title.adjustsFontSizeToFitWidth = true
        title.minimumScaleFactor = 0.82
        title.translatesAutoresizingMaskIntoConstraints = false

        let subtitle = UILabel()
        subtitle.text = item.subtitle
        subtitle.font = .systemFont(ofSize: 15, weight: .medium)
        subtitle.textColor = UIColor.white.withAlphaComponent(0.84)
        subtitle.numberOfLines = 1
        subtitle.translatesAutoresizingMaskIntoConstraints = false

        addSubview(logo)
        addSubview(title)
        addSubview(subtitle)

        if let badgeText = item.badgeText {
            let badge = UILabel()
            badge.text = " \(badgeText) "
            badge.font = .systemFont(ofSize: 12, weight: .medium)
            badge.textColor = .white
            badge.textAlignment = .center
            badge.backgroundColor = UIColor.black.withAlphaComponent(0.18)
            badge.layer.cornerRadius = 14
            badge.layer.cornerCurve = .continuous
            badge.clipsToBounds = true
            badge.translatesAutoresizingMaskIntoConstraints = false
            addSubview(badge)
            NSLayoutConstraint.activate([
                badge.topAnchor.constraint(equalTo: topAnchor, constant: 18),
                badge.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -16)
            ])
        }

        NSLayoutConstraint.activate([
            logo.leadingAnchor.constraint(equalTo: leadingAnchor, constant: 26),
            logo.centerYAnchor.constraint(equalTo: centerYAnchor),
            logo.widthAnchor.constraint(equalToConstant: 58),
            logo.heightAnchor.constraint(equalToConstant: 58),
            title.leadingAnchor.constraint(equalTo: logo.trailingAnchor, constant: 20),
            title.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -24),
            title.centerYAnchor.constraint(equalTo: centerYAnchor, constant: item.subtitle.isEmpty ? 0 : -13),
            subtitle.leadingAnchor.constraint(equalTo: title.leadingAnchor),
            subtitle.trailingAnchor.constraint(equalTo: title.trailingAnchor),
            subtitle.topAnchor.constraint(equalTo: title.bottomAnchor, constant: 6)
        ])
    }

    override func layoutSubviews() {
        super.layoutSubviews()
        gradientLayer.frame = bounds
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
}

private final class CouponInvoiceRow: UIControl {
    let item: CouponWalletItem

    init(item: CouponWalletItem) {
        self.item = item
        super.init(frame: .zero)
        backgroundColor = .white
        layer.cornerRadius = 10
        layer.cornerCurve = .continuous
        translatesAutoresizingMaskIntoConstraints = false
        heightAnchor.constraint(equalToConstant: 108).isActive = true

        let icon = UIImageView(image: UIImage(systemName: "leaf.fill"))
        icon.tintColor = UIColor(red: 0.05, green: 0.78, blue: 0.34, alpha: 1)
        icon.contentMode = .scaleAspectFit
        icon.translatesAutoresizingMaskIntoConstraints = false
        let title = UILabel()
        title.text = item.title
        title.font = .systemFont(ofSize: 22, weight: .semibold)
        title.textColor = UIColor(red: 0.12, green: 0.12, blue: 0.13, alpha: 1)
        title.translatesAutoresizingMaskIntoConstraints = false
        let count = UILabel()
        count.text = item.subtitle
        count.font = .systemFont(ofSize: 16, weight: .regular)
        count.textColor = UIColor(red: 0.62, green: 0.62, blue: 0.64, alpha: 1)
        count.translatesAutoresizingMaskIntoConstraints = false
        let chevron = UIImageView(image: UIImage(systemName: "chevron.right"))
        chevron.tintColor = UIColor(red: 0.76, green: 0.76, blue: 0.78, alpha: 1)
        chevron.translatesAutoresizingMaskIntoConstraints = false
        [icon, title, count, chevron].forEach(addSubview)
        NSLayoutConstraint.activate([
            icon.leadingAnchor.constraint(equalTo: leadingAnchor, constant: 28),
            icon.centerYAnchor.constraint(equalTo: centerYAnchor),
            icon.widthAnchor.constraint(equalToConstant: 56),
            icon.heightAnchor.constraint(equalToConstant: 56),
            title.leadingAnchor.constraint(equalTo: icon.trailingAnchor, constant: 26),
            title.centerYAnchor.constraint(equalTo: centerYAnchor),
            count.trailingAnchor.constraint(equalTo: chevron.leadingAnchor, constant: -12),
            count.centerYAnchor.constraint(equalTo: centerYAnchor),
            chevron.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -26),
            chevron.centerYAnchor.constraint(equalTo: centerYAnchor),
            chevron.widthAnchor.constraint(equalToConstant: 16),
            chevron.heightAnchor.constraint(equalToConstant: 20)
        ])
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
}

final class CouponDetailViewController: UIViewController {
    var onReceive: (() -> Void)?

    private let titleText: String
    private let detailText: String
    private let actionTitle: String

    init(titleText: String, detailText: String, actionTitle: String = "领取到卡包") {
        self.titleText = titleText
        self.detailText = detailText
        self.actionTitle = actionTitle
        super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        title = "卡券详情"
        view.backgroundColor = UIColor.systemGroupedBackground
        navigationItem.leftBarButtonItem = UIBarButtonItem(title: "返回", style: .plain, target: self, action: #selector(close))
        configure()
    }

    private func configure() {
        let card = UIView()
        card.backgroundColor = UIColor(red: 0.98, green: 0.72, blue: 0.26, alpha: 1)
        card.layer.cornerRadius = 20
        card.layer.cornerCurve = .continuous
        card.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(card)

        let titleLabel = UILabel()
        titleLabel.text = titleText
        titleLabel.font = .systemFont(ofSize: 25, weight: .bold)
        titleLabel.textColor = .white
        titleLabel.numberOfLines = 2
        titleLabel.translatesAutoresizingMaskIntoConstraints = false

        let detailLabel = UILabel()
        detailLabel.text = detailText
        detailLabel.font = .systemFont(ofSize: 15, weight: .medium)
        detailLabel.textColor = UIColor.white.withAlphaComponent(0.88)
        detailLabel.numberOfLines = 0
        detailLabel.translatesAutoresizingMaskIntoConstraints = false

        let codeLabel = UILabel()
        codeLabel.text = "券码 8866 1024 2026"
        codeLabel.font = .monospacedDigitSystemFont(ofSize: 16, weight: .semibold)
        codeLabel.textColor = .white
        codeLabel.textAlignment = .center
        codeLabel.backgroundColor = UIColor.black.withAlphaComponent(0.16)
        codeLabel.layer.cornerRadius = 12
        codeLabel.clipsToBounds = true
        codeLabel.translatesAutoresizingMaskIntoConstraints = false

        let button = UIButton(type: .system)
        button.setTitle(actionTitle, for: .normal)
        button.titleLabel?.font = .systemFont(ofSize: 16, weight: .semibold)
        button.tintColor = UIColor(red: 0.82, green: 0.42, blue: 0.08, alpha: 1)
        button.backgroundColor = .white
        button.layer.cornerRadius = 20
        button.addTarget(self, action: #selector(receive), for: .touchUpInside)
        button.translatesAutoresizingMaskIntoConstraints = false

        [titleLabel, detailLabel, codeLabel, button].forEach { card.addSubview($0) }
        NSLayoutConstraint.activate([
            card.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 30),
            card.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 20),
            card.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -20),
            card.heightAnchor.constraint(greaterThanOrEqualToConstant: 320),
            titleLabel.topAnchor.constraint(equalTo: card.topAnchor, constant: 28),
            titleLabel.leadingAnchor.constraint(equalTo: card.leadingAnchor, constant: 24),
            titleLabel.trailingAnchor.constraint(equalTo: card.trailingAnchor, constant: -24),
            detailLabel.topAnchor.constraint(equalTo: titleLabel.bottomAnchor, constant: 16),
            detailLabel.leadingAnchor.constraint(equalTo: titleLabel.leadingAnchor),
            detailLabel.trailingAnchor.constraint(equalTo: titleLabel.trailingAnchor),
            codeLabel.leadingAnchor.constraint(equalTo: titleLabel.leadingAnchor),
            codeLabel.trailingAnchor.constraint(equalTo: titleLabel.trailingAnchor),
            codeLabel.topAnchor.constraint(equalTo: detailLabel.bottomAnchor, constant: 28),
            codeLabel.heightAnchor.constraint(equalToConstant: 48),
            button.leadingAnchor.constraint(equalTo: titleLabel.leadingAnchor),
            button.trailingAnchor.constraint(equalTo: titleLabel.trailingAnchor),
            button.topAnchor.constraint(equalTo: codeLabel.bottomAnchor, constant: 24),
            button.heightAnchor.constraint(equalToConstant: 44),
            button.bottomAnchor.constraint(lessThanOrEqualTo: card.bottomAnchor, constant: -24)
        ])
    }

    @objc private func receive() {
        onReceive?()
        navigationController?.popViewController(animated: true)
    }

    @objc private func close() {
        navigationController?.popViewController(animated: true)
    }
}

