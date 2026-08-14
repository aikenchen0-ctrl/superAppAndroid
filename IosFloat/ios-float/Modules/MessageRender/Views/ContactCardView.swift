import UIKit

final class ContactCardView: UIView {
    private let accentView = UIView()
    private let avatarLabel = UILabel()
    private let nameLabel = UILabel()
    private let roleLabel = UILabel()
    private let metaLabel = UILabel()
    private let bioLabel = UILabel()
    private let badgeLabel = PaddingLabel()

    override init(frame: CGRect) {
        super.init(frame: frame)
        setup()
    }

    required init?(coder: NSCoder) {
        super.init(coder: coder)
        setup()
    }

    func configure(profile: ContactCardProfile) {
        accentView.backgroundColor = profile.accentColor
        avatarLabel.backgroundColor = profile.accentColor
        avatarLabel.text = String(profile.displayName.prefix(1))
        nameLabel.text = profile.displayName
        roleLabel.text = [profile.role, profile.company].filter { !$0.isEmpty }.joined(separator: " · ")
        let meta = [
            profile.wechatID.isEmpty ? "" : "微信 \(profile.wechatID)",
            profile.phone.isEmpty ? "" : "电话 \(profile.phone)"
        ].filter { !$0.isEmpty }.joined(separator: "   ")
        metaLabel.text = meta.isEmpty ? "点击查看或编辑名片" : meta
        bioLabel.text = profile.bio
        badgeLabel.text = profile.styleName
        badgeLabel.backgroundColor = profile.accentColor
        isHidden = false
    }

    func reset() {
        [avatarLabel, nameLabel, roleLabel, metaLabel, bioLabel, badgeLabel].forEach { $0.text = nil }
        accentView.backgroundColor = .clear
        avatarLabel.backgroundColor = .clear
        badgeLabel.backgroundColor = .clear
        nameLabel.textColor = Self.textColor
        roleLabel.textColor = Self.detailColor
        metaLabel.textColor = Self.mutedColor
        bioLabel.textColor = Self.detailColor
        applyTextShadow(isAI: false)
        isHidden = true
    }

    func applyAITextColor() {
        nameLabel.textColor = Self.aiTextColor
        roleLabel.textColor = Self.aiTextColor.withAlphaComponent(0.84)
        metaLabel.textColor = Self.aiTextColor.withAlphaComponent(0.72)
        bioLabel.textColor = Self.aiTextColor.withAlphaComponent(0.82)
    }

    func applyTextShadow(isAI: Bool) {
        let shadowColor = isAI
            ? UIColor(red: 1.0, green: 0.88, blue: 0.42, alpha: 0.26)
            : UIColor.white.withAlphaComponent(0.34)
        [nameLabel, roleLabel, metaLabel, bioLabel].forEach {
            $0.shadowColor = shadowColor
            $0.shadowOffset = CGSize(width: 0, height: 0.7)
        }
    }

    private func setup() {
        backgroundColor = UIColor.white.withAlphaComponent(0.28)
        layer.cornerRadius = 12
        layer.cornerCurve = .continuous
        layer.borderWidth = 1
        layer.borderColor = UIColor.white.withAlphaComponent(0.34).cgColor
        translatesAutoresizingMaskIntoConstraints = false
        accentView.layer.cornerRadius = 2
        accentView.translatesAutoresizingMaskIntoConstraints = false
        avatarLabel.textAlignment = .center
        avatarLabel.textColor = .white
        avatarLabel.font = .systemFont(ofSize: 18, weight: .bold)
        avatarLabel.layer.cornerRadius = 20
        avatarLabel.layer.cornerCurve = .continuous
        avatarLabel.clipsToBounds = true
        avatarLabel.translatesAutoresizingMaskIntoConstraints = false
        nameLabel.font = .systemFont(ofSize: 15, weight: .semibold)
        nameLabel.numberOfLines = 1
        nameLabel.translatesAutoresizingMaskIntoConstraints = false
        roleLabel.font = .systemFont(ofSize: 11.5, weight: .medium)
        roleLabel.numberOfLines = 1
        roleLabel.translatesAutoresizingMaskIntoConstraints = false
        metaLabel.font = .systemFont(ofSize: 10.5, weight: .regular)
        metaLabel.numberOfLines = 2
        metaLabel.translatesAutoresizingMaskIntoConstraints = false
        bioLabel.font = .systemFont(ofSize: 11, weight: .regular)
        bioLabel.numberOfLines = 2
        bioLabel.translatesAutoresizingMaskIntoConstraints = false
        badgeLabel.font = .systemFont(ofSize: 9.5, weight: .semibold)
        badgeLabel.textColor = .white
        badgeLabel.layer.cornerRadius = 8
        badgeLabel.layer.cornerCurve = .continuous
        badgeLabel.clipsToBounds = true
        badgeLabel.translatesAutoresizingMaskIntoConstraints = false
        [accentView, avatarLabel, nameLabel, roleLabel, metaLabel, bioLabel, badgeLabel].forEach(addSubview)
        NSLayoutConstraint.activate([
            heightAnchor.constraint(greaterThanOrEqualToConstant: 118),
            accentView.leadingAnchor.constraint(equalTo: leadingAnchor, constant: 10),
            accentView.topAnchor.constraint(equalTo: topAnchor, constant: 12),
            accentView.bottomAnchor.constraint(equalTo: bottomAnchor, constant: -12),
            accentView.widthAnchor.constraint(equalToConstant: 4),
            avatarLabel.leadingAnchor.constraint(equalTo: accentView.trailingAnchor, constant: 12),
            avatarLabel.topAnchor.constraint(equalTo: topAnchor, constant: 14),
            avatarLabel.widthAnchor.constraint(equalToConstant: 40),
            avatarLabel.heightAnchor.constraint(equalToConstant: 40),
            badgeLabel.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -12),
            badgeLabel.topAnchor.constraint(equalTo: topAnchor, constant: 12),
            badgeLabel.heightAnchor.constraint(greaterThanOrEqualToConstant: 18),
            nameLabel.leadingAnchor.constraint(equalTo: avatarLabel.trailingAnchor, constant: 10),
            nameLabel.trailingAnchor.constraint(lessThanOrEqualTo: badgeLabel.leadingAnchor, constant: -8),
            nameLabel.topAnchor.constraint(equalTo: avatarLabel.topAnchor, constant: 1),
            roleLabel.leadingAnchor.constraint(equalTo: nameLabel.leadingAnchor),
            roleLabel.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -12),
            roleLabel.topAnchor.constraint(equalTo: nameLabel.bottomAnchor, constant: 4),
            metaLabel.leadingAnchor.constraint(equalTo: avatarLabel.leadingAnchor),
            metaLabel.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -12),
            metaLabel.topAnchor.constraint(equalTo: avatarLabel.bottomAnchor, constant: 10),
            bioLabel.leadingAnchor.constraint(equalTo: metaLabel.leadingAnchor),
            bioLabel.trailingAnchor.constraint(equalTo: metaLabel.trailingAnchor),
            bioLabel.topAnchor.constraint(equalTo: metaLabel.bottomAnchor, constant: 5),
            bioLabel.bottomAnchor.constraint(lessThanOrEqualTo: bottomAnchor, constant: -12)
        ])
        reset()
    }

    private static let textColor = UIColor(red: 0.03, green: 0.27, blue: 0.34, alpha: 1)
    private static let detailColor = UIColor(red: 0.12, green: 0.31, blue: 0.36, alpha: 0.78)
    private static let mutedColor = UIColor(red: 0.14, green: 0.30, blue: 0.35, alpha: 0.58)
    private static let aiTextColor = UIColor(red: 0.95, green: 0.68, blue: 0.18, alpha: 1)
}
