import UIKit

final class ChannelsMediaOverlayView: UIView {
    private let titleLabel = UILabel()
    private let sourceLabel = UILabel()

    override init(frame: CGRect) {
        super.init(frame: frame)
        setup()
    }

    required init?(coder: NSCoder) {
        super.init(coder: coder)
        setup()
    }

    func configure(title: String, source: String) {
        titleLabel.text = title
        sourceLabel.text = source
        isHidden = false
    }

    func reset() {
        titleLabel.text = nil
        sourceLabel.text = nil
        isHidden = true
    }

    private func setup() {
        backgroundColor = UIColor.black.withAlphaComponent(0.42)
        translatesAutoresizingMaskIntoConstraints = false
        titleLabel.font = .systemFont(ofSize: 16, weight: .bold)
        titleLabel.textColor = .white
        titleLabel.numberOfLines = 2
        titleLabel.shadowColor = UIColor.black.withAlphaComponent(0.55)
        titleLabel.shadowOffset = CGSize(width: 0, height: 1)
        titleLabel.translatesAutoresizingMaskIntoConstraints = false
        sourceLabel.font = .systemFont(ofSize: 11.5, weight: .medium)
        sourceLabel.textColor = UIColor.white.withAlphaComponent(0.82)
        sourceLabel.numberOfLines = 1
        sourceLabel.translatesAutoresizingMaskIntoConstraints = false
        addSubview(titleLabel)
        addSubview(sourceLabel)
        NSLayoutConstraint.activate([
            titleLabel.leadingAnchor.constraint(equalTo: leadingAnchor, constant: 12),
            titleLabel.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -12),
            titleLabel.topAnchor.constraint(equalTo: topAnchor, constant: 10),
            sourceLabel.leadingAnchor.constraint(equalTo: titleLabel.leadingAnchor),
            sourceLabel.trailingAnchor.constraint(equalTo: titleLabel.trailingAnchor),
            sourceLabel.topAnchor.constraint(equalTo: titleLabel.bottomAnchor, constant: 6),
            sourceLabel.bottomAnchor.constraint(lessThanOrEqualTo: bottomAnchor, constant: -10)
        ])
        reset()
    }
}
