import UIKit

final class MediaPrivacyOverlayView: UIVisualEffectView {
    private let messageLabel = PaddingLabel()

    init() {
        super.init(effect: UIBlurEffect(style: .systemUltraThinMaterialDark))
        setup()
    }

    required init?(coder: NSCoder) {
        super.init(coder: coder)
        effect = UIBlurEffect(style: .systemUltraThinMaterialDark)
        setup()
    }

    func setMessage(_ message: String) { messageLabel.text = message }

    func setVisible(_ visible: Bool) {
        isHidden = !visible
        alpha = visible ? 1 : 0
    }

    func reset() {
        messageLabel.text = "截图保护"
        setVisible(false)
    }

    private func setup() {
        translatesAutoresizingMaskIntoConstraints = false
        messageLabel.text = "截图保护"
        messageLabel.font = .systemFont(ofSize: 11, weight: .semibold)
        messageLabel.textColor = .white
        messageLabel.backgroundColor = UIColor.black.withAlphaComponent(0.34)
        messageLabel.layer.cornerRadius = 10
        messageLabel.layer.cornerCurve = .continuous
        messageLabel.clipsToBounds = true
        messageLabel.translatesAutoresizingMaskIntoConstraints = false
        contentView.addSubview(messageLabel)
        NSLayoutConstraint.activate([
            messageLabel.centerXAnchor.constraint(equalTo: contentView.centerXAnchor),
            messageLabel.centerYAnchor.constraint(equalTo: contentView.centerYAnchor),
            messageLabel.heightAnchor.constraint(greaterThanOrEqualToConstant: 24)
        ])
        reset()
    }
}
