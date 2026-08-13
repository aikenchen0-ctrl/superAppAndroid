import UIKit

final class DocumentCardView: UIView {
    private let formatLabel = UILabel()
    private let lineStack = UIStackView()

    override init(frame: CGRect) {
        super.init(frame: frame)
        setup()
    }

    required init?(coder: NSCoder) {
        super.init(coder: coder)
        setup()
    }

    func configure(format: String) {
        formatLabel.text = format
        isHidden = false
    }

    func reset() {
        formatLabel.text = nil
        isHidden = true
    }

    private func setup() {
        backgroundColor = UIColor.white.withAlphaComponent(0.88)
        layer.cornerRadius = 6
        layer.cornerCurve = .continuous
        layer.borderWidth = 1
        layer.borderColor = UIColor.white.withAlphaComponent(0.70).cgColor
        translatesAutoresizingMaskIntoConstraints = false

        formatLabel.font = .systemFont(ofSize: 9, weight: .bold)
        formatLabel.textAlignment = .center
        formatLabel.textColor = UIColor(red: 0.05, green: 0.36, blue: 0.48, alpha: 1)
        formatLabel.translatesAutoresizingMaskIntoConstraints = false
        lineStack.axis = .vertical
        lineStack.spacing = 4
        lineStack.translatesAutoresizingMaskIntoConstraints = false
        (0..<4).forEach { index in
            let line = UIView()
            line.backgroundColor = UIColor(red: 0.05, green: 0.36, blue: 0.48, alpha: index == 0 ? 0.35 : 0.18)
            line.layer.cornerRadius = 1.5
            line.translatesAutoresizingMaskIntoConstraints = false
            line.heightAnchor.constraint(equalToConstant: 3).isActive = true
            lineStack.addArrangedSubview(line)
            if index == 3 {
                line.widthAnchor.constraint(equalTo: lineStack.widthAnchor, multiplier: 0.62).isActive = true
            }
        }

        addSubview(formatLabel)
        addSubview(lineStack)
        NSLayoutConstraint.activate([
            formatLabel.topAnchor.constraint(equalTo: topAnchor, constant: 8),
            formatLabel.leadingAnchor.constraint(equalTo: leadingAnchor, constant: 5),
            formatLabel.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -5),
            lineStack.leadingAnchor.constraint(equalTo: leadingAnchor, constant: 9),
            lineStack.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -9),
            lineStack.topAnchor.constraint(equalTo: formatLabel.bottomAnchor, constant: 8)
        ])
        reset()
    }
}
