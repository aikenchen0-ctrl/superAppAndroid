import UIKit

final class LinkShareCardView: UIView {
    private let titleLabel = UILabel()
    private let subtitleLabel = UILabel()
    private let thumbView = UIView()
    private let thumbIconView = UIImageView()

    override init(frame: CGRect) {
        super.init(frame: frame)
        setup()
    }

    required init?(coder: NSCoder) {
        super.init(coder: coder)
        setup()
    }

    func configure(title: String, summary: String, isOutgoing: Bool) {
        isHidden = false
        backgroundColor = UIColor.white.withAlphaComponent(isOutgoing ? 0.98 : 0.94)
        titleLabel.text = title
        subtitleLabel.text = summary
        thumbIconView.image = UIImage(named: "LinkShareIcon")?.withRenderingMode(.alwaysOriginal)
    }

    func reset() {
        isHidden = true
        titleLabel.text = nil
        subtitleLabel.text = nil
        thumbIconView.image = UIImage(named: "LinkShareIcon")?.withRenderingMode(.alwaysOriginal)
    }

    func applyTextShadow(color: UIColor, offset: CGSize) {
        [titleLabel, subtitleLabel].forEach {
            $0.shadowColor = color
            $0.shadowOffset = offset
        }
    }

    private func setup() {
        isHidden = true
        backgroundColor = UIColor.white.withAlphaComponent(0.96)
        layer.cornerRadius = 6
        layer.cornerCurve = .continuous
        clipsToBounds = true
        translatesAutoresizingMaskIntoConstraints = false

        titleLabel.font = .systemFont(ofSize: 16.5, weight: .regular)
        titleLabel.textColor = UIColor(red: 0.10, green: 0.10, blue: 0.11, alpha: 1)
        titleLabel.numberOfLines = 1
        titleLabel.lineBreakMode = .byTruncatingTail
        titleLabel.translatesAutoresizingMaskIntoConstraints = false

        subtitleLabel.font = .systemFont(ofSize: 13.5, weight: .regular)
        subtitleLabel.textColor = UIColor(red: 0.42, green: 0.42, blue: 0.44, alpha: 1)
        subtitleLabel.numberOfLines = 3
        subtitleLabel.lineBreakMode = .byTruncatingTail
        subtitleLabel.translatesAutoresizingMaskIntoConstraints = false

        thumbView.backgroundColor = UIColor(red: 0.94, green: 0.94, blue: 0.95, alpha: 1)
        thumbView.translatesAutoresizingMaskIntoConstraints = false

        thumbIconView.image = UIImage(named: "LinkShareIcon")?.withRenderingMode(.alwaysOriginal)
        thumbIconView.tintColor = nil
        thumbIconView.contentMode = .scaleAspectFit
        thumbIconView.translatesAutoresizingMaskIntoConstraints = false

        addSubview(titleLabel)
        addSubview(subtitleLabel)
        addSubview(thumbView)
        thumbView.addSubview(thumbIconView)

        NSLayoutConstraint.activate([
            heightAnchor.constraint(greaterThanOrEqualToConstant: 108),
            thumbView.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -10),
            thumbView.centerYAnchor.constraint(equalTo: centerYAnchor),
            thumbView.widthAnchor.constraint(equalToConstant: 44),
            thumbView.heightAnchor.constraint(equalToConstant: 44),
            thumbIconView.centerXAnchor.constraint(equalTo: thumbView.centerXAnchor),
            thumbIconView.centerYAnchor.constraint(equalTo: thumbView.centerYAnchor),
            thumbIconView.widthAnchor.constraint(equalToConstant: 24),
            thumbIconView.heightAnchor.constraint(equalToConstant: 24),
            titleLabel.leadingAnchor.constraint(equalTo: leadingAnchor, constant: 16),
            titleLabel.trailingAnchor.constraint(equalTo: thumbView.leadingAnchor, constant: -10),
            titleLabel.topAnchor.constraint(equalTo: topAnchor, constant: 14),
            subtitleLabel.leadingAnchor.constraint(equalTo: titleLabel.leadingAnchor),
            subtitleLabel.trailingAnchor.constraint(equalTo: titleLabel.trailingAnchor),
            subtitleLabel.topAnchor.constraint(equalTo: titleLabel.bottomAnchor, constant: 6),
            subtitleLabel.bottomAnchor.constraint(lessThanOrEqualTo: bottomAnchor, constant: -12)
        ])
    }
}
