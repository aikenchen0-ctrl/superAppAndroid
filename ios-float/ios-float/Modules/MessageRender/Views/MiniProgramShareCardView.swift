import UIKit

final class MiniProgramShareCardView: UIView {
    private let titleLabel = UILabel()
    private let heroView = UIView()
    private let heroIconView = UIImageView()
    private let heroTitleLabel = UILabel()
    private let heroSubtitleLabel = UILabel()
    private let heroBadgeLabel = PaddingLabel()
    private let dividerView = UIView()
    private let footerIconView = UIImageView()
    private let footerLabel = UILabel()
    private let chevronView = UIImageView(image: UIImage(systemName: "chevron.right"))

    override init(frame: CGRect) {
        super.init(frame: frame)
        setup()
    }

    required init?(coder: NSCoder) {
        super.init(coder: coder)
        setup()
    }

    func configure(
        title: String,
        source: String,
        page: String,
        heroSymbolName: String,
        isOutgoing: Bool
    ) {
        isHidden = false
        backgroundColor = UIColor.white.withAlphaComponent(isOutgoing ? 0.98 : 0.94)
        titleLabel.text = title
        heroTitleLabel.text = source
        heroSubtitleLabel.text = page
        heroBadgeLabel.text = "可打开"
        footerLabel.text = "\(source) · 小程序"
        footerIconView.image = UIImage(systemName: "app.fill")
        chevronView.image = UIImage(systemName: "chevron.right")
        heroIconView.image = UIImage(systemName: heroSymbolName)
        heroView.backgroundColor = UIColor(red: 0.90, green: 0.97, blue: 0.92, alpha: 1)
        heroBadgeLabel.backgroundColor = UIColor(red: 0.12, green: 0.62, blue: 0.36, alpha: 1)
        heroIconView.tintColor = UIColor(red: 0.12, green: 0.62, blue: 0.36, alpha: 1)
        footerIconView.tintColor = UIColor(red: 0.12, green: 0.62, blue: 0.36, alpha: 1)
    }

    func reset() {
        isHidden = true
        titleLabel.text = nil
        heroTitleLabel.text = nil
        heroSubtitleLabel.text = nil
        heroBadgeLabel.text = nil
        footerLabel.text = nil
    }

    func applyTextShadow(color: UIColor, offset: CGSize) {
        [titleLabel, heroTitleLabel, heroSubtitleLabel, footerLabel].forEach {
            $0.shadowColor = color
            $0.shadowOffset = offset
        }
    }

    private func setup() {
        isHidden = true
        backgroundColor = UIColor.white.withAlphaComponent(0.96)
        layer.cornerRadius = 9
        layer.cornerCurve = .continuous
        clipsToBounds = true
        translatesAutoresizingMaskIntoConstraints = false

        titleLabel.font = .systemFont(ofSize: 15, weight: .semibold)
        titleLabel.textColor = UIColor(red: 0.12, green: 0.14, blue: 0.15, alpha: 1)
        titleLabel.numberOfLines = 2
        titleLabel.lineBreakMode = .byTruncatingTail
        titleLabel.translatesAutoresizingMaskIntoConstraints = false

        heroView.backgroundColor = UIColor(red: 0.90, green: 0.97, blue: 0.92, alpha: 1)
        heroView.layer.cornerRadius = 7
        heroView.layer.cornerCurve = .continuous
        heroView.clipsToBounds = true
        heroView.translatesAutoresizingMaskIntoConstraints = false

        heroIconView.contentMode = .scaleAspectFit
        heroIconView.image = UIImage(systemName: "bag.badge.plus")
        heroIconView.tintColor = UIColor(red: 0.12, green: 0.62, blue: 0.36, alpha: 1)
        heroIconView.translatesAutoresizingMaskIntoConstraints = false

        heroTitleLabel.font = .systemFont(ofSize: 16, weight: .bold)
        heroTitleLabel.textColor = UIColor(red: 0.10, green: 0.30, blue: 0.22, alpha: 1)
        heroTitleLabel.numberOfLines = 1
        heroTitleLabel.adjustsFontSizeToFitWidth = true
        heroTitleLabel.minimumScaleFactor = 0.82
        heroTitleLabel.translatesAutoresizingMaskIntoConstraints = false

        heroSubtitleLabel.font = .systemFont(ofSize: 11.5, weight: .medium)
        heroSubtitleLabel.textColor = UIColor(red: 0.25, green: 0.45, blue: 0.36, alpha: 1)
        heroSubtitleLabel.numberOfLines = 1
        heroSubtitleLabel.translatesAutoresizingMaskIntoConstraints = false

        heroBadgeLabel.font = .systemFont(ofSize: 10, weight: .semibold)
        heroBadgeLabel.textColor = .white
        heroBadgeLabel.textInsets = UIEdgeInsets(top: 3, left: 7, bottom: 3, right: 7)
        heroBadgeLabel.backgroundColor = UIColor(red: 0.12, green: 0.62, blue: 0.36, alpha: 1)
        heroBadgeLabel.layer.cornerRadius = 9
        heroBadgeLabel.layer.cornerCurve = .continuous
        heroBadgeLabel.clipsToBounds = true
        heroBadgeLabel.translatesAutoresizingMaskIntoConstraints = false

        dividerView.backgroundColor = UIColor(red: 0.89, green: 0.91, blue: 0.92, alpha: 1)
        dividerView.translatesAutoresizingMaskIntoConstraints = false

        footerIconView.contentMode = .scaleAspectFit
        footerIconView.image = UIImage(systemName: "app.fill")
        footerIconView.tintColor = UIColor(red: 0.12, green: 0.62, blue: 0.36, alpha: 1)
        footerIconView.translatesAutoresizingMaskIntoConstraints = false

        footerLabel.font = .systemFont(ofSize: 11, weight: .medium)
        footerLabel.textColor = UIColor(red: 0.46, green: 0.50, blue: 0.53, alpha: 1)
        footerLabel.numberOfLines = 1
        footerLabel.translatesAutoresizingMaskIntoConstraints = false

        chevronView.tintColor = UIColor(red: 0.62, green: 0.67, blue: 0.70, alpha: 1)
        chevronView.contentMode = .scaleAspectFit
        chevronView.translatesAutoresizingMaskIntoConstraints = false

        addSubview(titleLabel)
        addSubview(heroView)
        heroView.addSubview(heroIconView)
        heroView.addSubview(heroTitleLabel)
        heroView.addSubview(heroSubtitleLabel)
        heroView.addSubview(heroBadgeLabel)
        addSubview(dividerView)
        addSubview(footerIconView)
        addSubview(footerLabel)
        addSubview(chevronView)

        NSLayoutConstraint.activate([
            heightAnchor.constraint(greaterThanOrEqualToConstant: 222),
            titleLabel.leadingAnchor.constraint(equalTo: leadingAnchor, constant: 12),
            titleLabel.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -12),
            titleLabel.topAnchor.constraint(equalTo: topAnchor, constant: 12),
            heroView.leadingAnchor.constraint(equalTo: leadingAnchor, constant: 10),
            heroView.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -10),
            heroView.topAnchor.constraint(equalTo: titleLabel.bottomAnchor, constant: 9),
            heroView.heightAnchor.constraint(equalToConstant: 124),
            heroIconView.leadingAnchor.constraint(equalTo: heroView.leadingAnchor, constant: 18),
            heroIconView.centerYAnchor.constraint(equalTo: heroView.centerYAnchor, constant: -3),
            heroIconView.widthAnchor.constraint(equalToConstant: 42),
            heroIconView.heightAnchor.constraint(equalToConstant: 42),
            heroTitleLabel.leadingAnchor.constraint(equalTo: heroIconView.trailingAnchor, constant: 12),
            heroTitleLabel.trailingAnchor.constraint(equalTo: heroView.trailingAnchor, constant: -14),
            heroTitleLabel.centerYAnchor.constraint(equalTo: heroIconView.centerYAnchor, constant: -10),
            heroSubtitleLabel.leadingAnchor.constraint(equalTo: heroTitleLabel.leadingAnchor),
            heroSubtitleLabel.trailingAnchor.constraint(equalTo: heroTitleLabel.trailingAnchor),
            heroSubtitleLabel.topAnchor.constraint(equalTo: heroTitleLabel.bottomAnchor, constant: 4),
            heroBadgeLabel.leadingAnchor.constraint(equalTo: heroView.leadingAnchor, constant: 12),
            heroBadgeLabel.topAnchor.constraint(equalTo: heroView.topAnchor, constant: 10),
            dividerView.leadingAnchor.constraint(equalTo: leadingAnchor),
            dividerView.trailingAnchor.constraint(equalTo: trailingAnchor),
            dividerView.topAnchor.constraint(equalTo: heroView.bottomAnchor, constant: 10),
            dividerView.heightAnchor.constraint(equalToConstant: 1 / UIScreen.main.scale),
            footerIconView.leadingAnchor.constraint(equalTo: leadingAnchor, constant: 12),
            footerIconView.topAnchor.constraint(equalTo: dividerView.bottomAnchor, constant: 8),
            footerIconView.widthAnchor.constraint(equalToConstant: 14),
            footerIconView.heightAnchor.constraint(equalToConstant: 14),
            footerLabel.leadingAnchor.constraint(equalTo: footerIconView.trailingAnchor, constant: 6),
            footerLabel.centerYAnchor.constraint(equalTo: footerIconView.centerYAnchor),
            footerLabel.trailingAnchor.constraint(lessThanOrEqualTo: chevronView.leadingAnchor, constant: -8),
            chevronView.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -12),
            chevronView.centerYAnchor.constraint(equalTo: footerIconView.centerYAnchor),
            chevronView.widthAnchor.constraint(equalToConstant: 10),
            chevronView.heightAnchor.constraint(equalToConstant: 14),
            footerIconView.bottomAnchor.constraint(lessThanOrEqualTo: bottomAnchor, constant: -9)
        ])
    }
}
