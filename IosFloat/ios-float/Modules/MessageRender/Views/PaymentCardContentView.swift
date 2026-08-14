import UIKit

final class PaymentCardContentView: UIView {
    private let dividerView = UIView()
    private let footerLabel = UILabel()
    private let envelopeView = RedPacketEnvelopeView()
    private let iconView = UIImageView()
    private let greetingLabel = UILabel()
    private let subtitleLabel = UILabel()
    private var mainCenterYConstraint: NSLayoutConstraint!
    private var iconLeadingConstraint: NSLayoutConstraint!
    private var iconCenterYConstraint: NSLayoutConstraint!

    override init(frame: CGRect) {
        super.init(frame: frame)
        setup()
    }

    required init?(coder: NSCoder) {
        super.init(coder: coder)
        setup()
    }

    func reset() {
        isHidden = true
        dividerView.isHidden = true
        footerLabel.isHidden = true
        footerLabel.text = nil
        envelopeView.isHidden = true
        envelopeView.alpha = 1
        iconView.isHidden = true
        iconView.image = nil
        iconView.alpha = 1
        iconView.transform = .identity
        greetingLabel.isHidden = true
        greetingLabel.text = nil
        greetingLabel.alpha = 1
        greetingLabel.font = .systemFont(ofSize: 16.5, weight: .semibold)
        greetingLabel.numberOfLines = 2
        subtitleLabel.isHidden = true
        subtitleLabel.text = nil
        subtitleLabel.alpha = 1
        subtitleLabel.font = .systemFont(ofSize: 15, weight: .medium)
        mainCenterYConstraint.constant = -4
        iconLeadingConstraint.constant = 10
        iconCenterYConstraint.constant = 0
    }

    func configure(
        type: ChatMessageType,
        title: String,
        subtitle: String?,
        footer: String,
        completed: Bool
    ) {
        reset()
        isHidden = false
        greetingLabel.isHidden = false
        greetingLabel.text = title
        footerLabel.isHidden = false
        footerLabel.text = footer
        footerLabel.textColor = UIColor.white.withAlphaComponent(completed ? 0.54 : 0.68)
        dividerView.backgroundColor = UIColor.white.withAlphaComponent(0.12)

        switch type {
        case .redPacket:
            envelopeView.isHidden = false
            envelopeView.alpha = completed ? 0.58 : 1
            mainCenterYConstraint.constant = -5
            greetingLabel.font = .systemFont(ofSize: 14.5, weight: .semibold)
            greetingLabel.numberOfLines = 1
        case .transfer:
            iconView.isHidden = false
            iconView.image = UIImage(systemName: "arrow.right.circle")
            iconView.transform = CGAffineTransform(scaleX: 1.34, y: 1.34)
            iconLeadingConstraint.constant = 17
            iconCenterYConstraint.constant = -4
            mainCenterYConstraint.constant = -12
            greetingLabel.font = .systemFont(ofSize: 18, weight: .bold)
            greetingLabel.numberOfLines = 1
            subtitleLabel.font = .systemFont(ofSize: 13.5, weight: .medium)
        case .splitBill:
            iconView.isHidden = false
            iconView.image = UIImage(systemName: "person.3.sequence.fill")
            iconView.transform = CGAffineTransform(scaleX: 1.28, y: 1.28)
            iconLeadingConstraint.constant = 13
            iconCenterYConstraint.constant = -3
            mainCenterYConstraint.constant = -12
            greetingLabel.font = .systemFont(ofSize: 17, weight: .bold)
            greetingLabel.numberOfLines = 1
            subtitleLabel.font = .systemFont(ofSize: 13, weight: .medium)
        default:
            break
        }

        subtitleLabel.text = subtitle
        subtitleLabel.isHidden = subtitle == nil
        let alpha: CGFloat = completed ? 0.68 : 1
        iconView.alpha = alpha
        greetingLabel.alpha = completed ? 0.82 : 1
        subtitleLabel.alpha = completed ? 0.78 : 1
        footerLabel.alpha = completed ? 0.72 : 1
    }

    private func setup() {
        backgroundColor = .clear
        translatesAutoresizingMaskIntoConstraints = false

        dividerView.backgroundColor = UIColor.white.withAlphaComponent(0.22)
        dividerView.isHidden = true
        dividerView.translatesAutoresizingMaskIntoConstraints = false
        footerLabel.font = .systemFont(ofSize: 12, weight: .medium)
        footerLabel.textColor = UIColor.white.withAlphaComponent(0.76)
        footerLabel.numberOfLines = 1
        footerLabel.isHidden = true
        footerLabel.translatesAutoresizingMaskIntoConstraints = false
        envelopeView.isHidden = true
        envelopeView.translatesAutoresizingMaskIntoConstraints = false
        iconView.tintColor = .white
        iconView.backgroundColor = .clear
        iconView.contentMode = .scaleAspectFit
        iconView.isHidden = true
        iconView.translatesAutoresizingMaskIntoConstraints = false
        greetingLabel.font = .systemFont(ofSize: 16.5, weight: .semibold)
        greetingLabel.textColor = .white
        greetingLabel.textAlignment = .left
        greetingLabel.numberOfLines = 2
        greetingLabel.lineBreakMode = .byTruncatingTail
        greetingLabel.adjustsFontSizeToFitWidth = true
        greetingLabel.minimumScaleFactor = 0.78
        greetingLabel.shadowColor = UIColor.black.withAlphaComponent(0.10)
        greetingLabel.shadowOffset = CGSize(width: 0, height: 0.6)
        greetingLabel.isHidden = true
        greetingLabel.translatesAutoresizingMaskIntoConstraints = false
        subtitleLabel.font = .systemFont(ofSize: 15, weight: .medium)
        subtitleLabel.textColor = UIColor.white.withAlphaComponent(0.92)
        subtitleLabel.textAlignment = .left
        subtitleLabel.numberOfLines = 1
        subtitleLabel.shadowColor = UIColor.black.withAlphaComponent(0.08)
        subtitleLabel.shadowOffset = CGSize(width: 0, height: 0.5)
        subtitleLabel.isHidden = true
        subtitleLabel.translatesAutoresizingMaskIntoConstraints = false

        [envelopeView, iconView, dividerView, footerLabel, greetingLabel, subtitleLabel].forEach(addSubview)
        mainCenterYConstraint = greetingLabel.centerYAnchor.constraint(equalTo: centerYAnchor, constant: -4)
        iconLeadingConstraint = iconView.leadingAnchor.constraint(equalTo: leadingAnchor, constant: 10)
        iconCenterYConstraint = iconView.centerYAnchor.constraint(equalTo: centerYAnchor)
        NSLayoutConstraint.activate([
            envelopeView.leadingAnchor.constraint(equalTo: leadingAnchor, constant: 16),
            envelopeView.topAnchor.constraint(equalTo: topAnchor, constant: 15),
            envelopeView.widthAnchor.constraint(equalToConstant: 36),
            envelopeView.heightAnchor.constraint(equalToConstant: 48),
            iconLeadingConstraint,
            iconCenterYConstraint,
            iconView.widthAnchor.constraint(equalToConstant: 28),
            iconView.heightAnchor.constraint(equalToConstant: 28),
            greetingLabel.leadingAnchor.constraint(equalTo: envelopeView.trailingAnchor, constant: 14),
            greetingLabel.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -12),
            mainCenterYConstraint,
            subtitleLabel.leadingAnchor.constraint(equalTo: greetingLabel.leadingAnchor),
            subtitleLabel.trailingAnchor.constraint(equalTo: greetingLabel.trailingAnchor),
            subtitleLabel.topAnchor.constraint(equalTo: greetingLabel.bottomAnchor, constant: 2),
            dividerView.leadingAnchor.constraint(equalTo: leadingAnchor, constant: 18),
            dividerView.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -18),
            dividerView.bottomAnchor.constraint(equalTo: footerLabel.topAnchor, constant: -7),
            dividerView.heightAnchor.constraint(equalToConstant: 1 / UIScreen.main.scale),
            footerLabel.leadingAnchor.constraint(equalTo: leadingAnchor, constant: 18),
            footerLabel.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -18),
            footerLabel.bottomAnchor.constraint(equalTo: bottomAnchor, constant: -8),
            footerLabel.heightAnchor.constraint(equalToConstant: 16)
        ])
        reset()
    }
}
